/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.base.service.AppService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.workflow.WorkflowContext;
import com.alibaba.cloud.ai.studio.core.workflow.WorkflowInnerService;
import com.alibaba.cloud.ai.studio.core.workflow.runtime.WorkflowExecuteManager;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.app.ApplicationVersion;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeResult;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeStatusEnum;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeTypeEnum;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.ApiTaskMsg;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.ApiTaskRunRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.TaskRunResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization Test for the workflow/Agent SSE streaming run path
 * (test-plan.md B2 / critical-paths.md #7).
 *
 * <p>与 B1 同风格：不基于"应该是什么"，而是先运行真实的
 * {@link WorkflowController#streamEvents} 编排、观察其经 {@link SseEmitter} 实际推出的
 * 事件序列与结束标记，再把观察到的行为固化为断言（golden master），用于在重构
 * SSE 序列化 / 超时 / 缓冲逻辑前给高危的流式运行链路兜底。
 *
 * <p>外部边界（{@link RedisManager} 取上下文、{@link AppService} 取版本、
 * {@link WorkflowExecuteManager} 提交任务）以 Mockito 在调用边界处打桩；被测对象
 * {@code WorkflowController} 的轮询循环、事件构造、结束标记判定均为真实执行。
 *
 * <p>两个关键技巧：
 * <ul>
 * <li>控制器把异步执行提交到 {@code ThreadPoolUtils.DEFAULT_TASK_EXECUTOR}，该线程池被
 * {@code RequestContextThreadPoolWrapper} 包裹，会把提交线程的 {@code RequestContext}
 * 透传到工作线程；因此测试线程里 {@code set} 真实上下文即可，无需 mock 静态持有者。</li>
 * <li>控制器内部 {@code new SseEmitter(0L)}，用 {@code mockConstruction} 拦截该构造并记录
 * 其上所有 {@code send}/{@code complete} 调用，从而捕获完整事件序列。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowController SSE Characterization (B2 - 工作流流式运行事件序列)")
class WorkflowControllerSseCharacterizationTest {

	@Mock
	private RedisManager redisManager;

	@Mock
	private AppService appService;

	@Mock
	private WorkflowExecuteManager workflowExecuteManager;

	@Mock
	private WorkflowInnerService workflowInnerService;

	private WorkflowController controller;

	private static final String APP_ID = "app-1";

	private static final String TASK_ID = "task-1";

	private static final String CONVERSATION_ID = "conv-1";

	private static final String WORKSPACE_ID = "ws-1";

	@BeforeEach
	void setUp() {
		controller = new WorkflowController(redisManager, appService, workflowExecuteManager, workflowInnerService);

		// 在测试线程设置真实 RequestContext；DEFAULT_TASK_EXECUTOR 的包装器会把它透传到工作线程
		RequestContext requestContext = new RequestContext();
		requestContext.setRequestId("req-1");
		requestContext.setWorkspaceId(WORKSPACE_ID);
		RequestContextHolder.setRequestContext(requestContext);

		// 应用版本（流式路径里仅作为 runTask 的入参透传，不解析其内容）
		when(appService.getAppVersion(anyString(), anyString())).thenReturn(mock(ApplicationVersion.class));

		// 任务提交：回填 taskId / conversationId，后续轮询据此拼 redis key
		TaskRunResponse runResponse = new TaskRunResponse();
		runResponse.setTaskId(TASK_ID);
		runResponse.setConversationId(CONVERSATION_ID);
		when(workflowExecuteManager.runTask(any(), any(), any(), any())).thenReturn(runResponse);
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.clearRequestContext();
	}

	private ApiTaskRunRequest buildRequest() {
		return new ApiTaskRunRequest();
	}

	private NodeResult nodeResult(String id, String name, NodeTypeEnum type, NodeStatusEnum status, String output) {
		NodeResult nodeResult = new NodeResult();
		nodeResult.setNodeId(id);
		nodeResult.setNodeName(name);
		nodeResult.setNodeType(type.getCode());
		nodeResult.setNodeStatus(status.getCode());
		nodeResult.setOutput(output);
		return nodeResult;
	}

	/**
	 * 驱动 streamEvents，并通过拦截内部构造的 SseEmitter 收集事件序列。 阻塞直到 emitter.complete()
	 * 被调用（finally 阶段，晚于所有 send），保证拿到完整序列。
	 */
	private List<ApiTaskMsg> runStreamAndCollect(WorkflowContext latestContext) {
		List<ApiTaskMsg> sent = new CopyOnWriteArrayList<>();
		AtomicBoolean completed = new AtomicBoolean(false);

		when(redisManager.<WorkflowContext>get(anyString())).thenReturn(latestContext);

		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitterMock, ctx) -> {
			doAnswer(invocation -> {
				sent.add((ApiTaskMsg) invocation.getArgument(0));
				return null;
			}).when(emitterMock).send(any(ApiTaskMsg.class));
			doAnswer(invocation -> {
				completed.set(true);
				return null;
			}).when(emitterMock).complete();
		})) {
			SseEmitter returned = controller.streamEvents(APP_ID, buildRequest());

			// 实际行为：streamEvents 同步返回非空 emitter（HTTP 200 长连接已建立）
			assertThat(returned).isNotNull();

			await().atMost(5, SECONDS).untilTrue(completed);

			// 实际行为：以 complete() 正常收尾，绝不以 completeWithError 中断连接
			verify(returned).complete();
			verify(returned, never()).completeWithError(any());
		}
		return sent;
	}

	@Test
	@DisplayName("场景1: 任务成功 → 推送 Message(节点完成) 后以 Finished 事件收尾")
	void runStream_taskSuccess_emitsMessageThenFinished() {
		WorkflowContext context = new WorkflowContext();
		context.setWorkspaceId(WORKSPACE_ID);
		context.getExecuteOrderList().add("end-1");
		context.getNodeResultMap()
			.put("end-1", nodeResult("end-1", "End", NodeTypeEnum.END, NodeStatusEnum.SUCCESS, "final answer"));
		context.setTaskStatus(NodeStatusEnum.SUCCESS.getCode());

		List<ApiTaskMsg> events = runStreamAndCollect(context);

		// 实际行为：事件序列恰为 Message → Finished，结束标记为 Finished
		assertThat(events).extracting(ApiTaskMsg::getEvent)
			.containsExactly(ApiTaskMsg.Event.Message.name(), ApiTaskMsg.Event.Finished.name());

		ApiTaskMsg message = events.get(0);
		assertThat(message.getNodeId()).isEqualTo("end-1");
		assertThat(message.getNodeType()).isEqualTo(NodeTypeEnum.END.getCode());
		assertThat(message.getNodeStatus()).isEqualTo(NodeStatusEnum.SUCCESS.getCode());
		assertThat(message.getNodeIsCompleted()).isTrue();
		assertThat(message.getNodeMsgSeqId()).isEqualTo(1);
		assertThat(message.getTextContent()).isEqualTo("final answer");

		ApiTaskMsg finished = events.get(1);
		assertThat(finished.getTaskId()).isEqualTo(TASK_ID);
		assertThat(finished.getConversationId()).isEqualTo(CONVERSATION_ID);
	}

	@Test
	@DisplayName("场景2: 任务失败 → 以 Error 事件（携带 error_code/error_message）收尾")
	void runStream_taskFail_emitsErrorAsTerminalEvent() {
		WorkflowContext context = new WorkflowContext();
		context.setWorkspaceId(WORKSPACE_ID);
		// 无待推送节点，直接走到任务状态判定
		context.setTaskStatus(NodeStatusEnum.FAIL.getCode());
		context.setErrorCode("ERR_CODE");
		context.setErrorInfo("boom");

		List<ApiTaskMsg> events = runStreamAndCollect(context);

		// 实际行为：仅一个 Error 事件作为结束标记
		assertThat(events).extracting(ApiTaskMsg::getEvent).containsExactly(ApiTaskMsg.Event.Error.name());

		ApiTaskMsg error = events.get(0);
		assertThat(error.getError_code()).isEqualTo("ERR_CODE");
		assertThat(error.getError_message()).isEqualTo("boom");
		assertThat(error.getTaskId()).isEqualTo(TASK_ID);
		assertThat(error.getConversationId()).isEqualTo(CONVERSATION_ID);
	}

	@Test
	@DisplayName("场景3: 任务暂停 → 以 Paused 事件（携带 pause_data）收尾")
	void runStream_taskPause_emitsPausedAsTerminalEvent() {
		WorkflowContext context = new WorkflowContext();
		context.setWorkspaceId(WORKSPACE_ID);
		// INPUT 暂停节点 output 为空 → 不会触发增量 Message，仅产生 Paused 结束事件
		context.getExecuteOrderList().add("input-1");
		context.getNodeResultMap()
			.put("input-1", nodeResult("input-1", "Input", NodeTypeEnum.INPUT, NodeStatusEnum.PAUSE, null));
		context.setTaskStatus(NodeStatusEnum.PAUSE.getCode());

		List<ApiTaskMsg> events = runStreamAndCollect(context);

		// 实际行为：仅一个 Paused 事件作为结束标记
		assertThat(events).extracting(ApiTaskMsg::getEvent).containsExactly(ApiTaskMsg.Event.Paused.name());

		ApiTaskMsg paused = events.get(0);
		assertThat(paused.getPauseType()).isEqualTo(ApiTaskMsg.PauseType.InputNodeInterrupt.name());
		assertThat(paused.getTaskId()).isEqualTo(TASK_ID);
		assertThat(paused.getConversationId()).isEqualTo(CONVERSATION_ID);
		assertThat(paused.getPause_data()).isNotNull();
	}

}
