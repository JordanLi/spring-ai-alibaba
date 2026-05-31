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
package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.dto.ChatSession;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorDebugResult;
import com.alibaba.cloud.ai.studio.admin.dto.Experiment;
import com.alibaba.cloud.ai.studio.admin.dto.PromptVersionDetail;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentCreateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.DatasetItemDO;
import com.alibaba.cloud.ai.studio.admin.entity.DatasetVersionDO;
import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorVersionDO;
import com.alibaba.cloud.ai.studio.admin.entity.ExperimentDO;
import com.alibaba.cloud.ai.studio.admin.entity.ExperimentResultDO;
import com.alibaba.cloud.ai.studio.admin.mapper.DatasetItemMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.DatasetVersionMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.EvaluatorMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.EvaluatorVersionMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.ExperimentMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.ExperimentResultMapper;
import com.alibaba.cloud.ai.studio.admin.service.ChatSessionService;
import com.alibaba.cloud.ai.studio.admin.service.PromptVersionService;
import com.alibaba.cloud.ai.studio.admin.utils.ModelConfigParser;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization Test for the experiment async-evaluation critical path
 * (test-plan.md B1 / critical-paths.md #2).
 *
 * <p>本测试不基于"应该是什么"，而是先运行真实的 {@link ExperimentServiceImpl} 代码、
 * 观察其实际行为，再把观察到的实际行为固化为断言（golden master），用于在重构前给
 * 高危的异步评估链路兜底。外部边界（LLM ChatClient、评估器、Prompt 版本服务、各 Mapper）
 * 以 Mockito 在其调用边界处打桩，被测对象 {@code ExperimentServiceImpl} 的编排/状态机
 * /落库逻辑均为真实执行。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExperimentServiceImpl Characterization (B1 - 实验异步评估成功链路)")
class ExperimentServiceImplCharacterizationTest {

	@Mock
	private ExperimentMapper experimentMapper;

	@Mock
	private ExperimentResultMapper experimentResultMapper;

	@Mock
	private DatasetVersionMapper datasetVersionMapper;

	@Mock
	private EvaluatorMapper evaluatorMapper;

	@Mock
	private EvaluatorVersionMapper evaluatorVersionMapper;

	@Mock
	private DatasetItemMapper datasetItemMapper;

	@Mock
	private ModelConfigParser modelConfigParser;

	@Mock
	private PromptVersionService promptVersionService;

	@Mock
	private ChatSessionService chatSessionService;

	@Mock
	private EvaluatorServiceImpl evaluatorServiceImpl;

	@InjectMocks
	private ExperimentServiceImpl experimentService;

	private static final long EXPERIMENT_ID = 100L;

	private static final long DATASET_ID = 1L;

	private static final long DATASET_VERSION_ID = 10L;

	@BeforeEach
	void setUp() {
		// ExperimentServiceImpl 的 final mapper 字段走 @RequiredArgsConstructor 构造注入，
		// 而 promptVersionService/chatSessionService/evaluatorServiceImpl 是 @Autowired 字段。
		// Mockito @InjectMocks 一旦选择构造注入便不再做字段注入，这三个协作者会保持 null，
		// 因此手动用反射注入 mock，确保真实的异步评估编排逻辑被完整执行。
		ReflectionTestUtils.setField(experimentService, "promptVersionService", promptVersionService);
		ReflectionTestUtils.setField(experimentService, "chatSessionService", chatSessionService);
		ReflectionTestUtils.setField(experimentService, "evaluatorServiceImpl", evaluatorServiceImpl);

		// 模拟 DB 自增主键：insert 后回填 id（贴近真实 MyBatis 行为）
		when(experimentMapper.insert(any(ExperimentDO.class))).thenAnswer(invocation -> {
			ExperimentDO entity = invocation.getArgument(0);
			entity.setId(EXPERIMENT_ID);
			return 1;
		});
		when(experimentMapper.updateById(any(ExperimentDO.class))).thenReturn(1);
	}

	private ExperimentCreateRequest buildRequest(String evaluationObjectConfig, String evaluatorConfig) {
		ExperimentCreateRequest request = new ExperimentCreateRequest();
		request.setName("characterization-exp");
		request.setDescription("characterization");
		request.setDatasetId(DATASET_ID);
		request.setDatasetVersionId(DATASET_VERSION_ID);
		request.setDatasetVersion("1.0");
		request.setEvaluationObjectConfig(evaluationObjectConfig);
		request.setEvaluatorConfig(evaluatorConfig);
		return request;
	}

	/** {"type":"prompt","config":"{...EvaluationPromptConfig...}"} —— config 是被转义的 JSON 字符串。 */
	private String promptEvaluationObjectConfig() {
		String promptConfig = "{\"promptKey\":\"k\",\"version\":\"1.0\",\"variableMap\":[]}";
		return "{\"type\":\"prompt\",\"config\":" + JSON.toJSONString(promptConfig) + "}";
	}

	@Test
	@DisplayName("场景1: create() 同步返回的实验初始状态为 RUNNING、progress=0，并写入数据库")
	void create_returnsRunningExperimentWithZeroProgress() {
		// 空数据集，让异步任务能干净结束，不干扰对同步返回值的断言
		when(datasetVersionMapper.selectById(DATASET_VERSION_ID)).thenReturn(
				DatasetVersionDO.builder().datasetId(DATASET_ID).datasetItems("[]").dataCount(0).build());
		when(datasetItemMapper.selectByDatasetIdAndItemIds(anyLong(), anyList()))
				.thenReturn(Collections.emptyList());

		Experiment created = experimentService.create(buildRequest(promptEvaluationObjectConfig(), "[]"));

		// 实际行为：返回的 DTO 状态为字符串 "RUNNING"，progress=0，并已回填自增 id
		assertThat(created).isNotNull();
		assertThat(created.getId()).isEqualTo(EXPERIMENT_ID);
		assertThat(created.getStatus()).isEqualTo("RUNNING");
		assertThat(created.getProgress()).isEqualTo(0);

		// 实际行为：insert 收到的实体初始状态 RUNNING / progress 0 / 带时间戳
		ArgumentCaptor<ExperimentDO> insertCaptor = ArgumentCaptor.forClass(ExperimentDO.class);
		verify(experimentMapper).insert(insertCaptor.capture());
		ExperimentDO inserted = insertCaptor.getValue();
		assertThat(inserted.getStatus()).isEqualTo("RUNNING");
		assertThat(inserted.getProgress()).isEqualTo(0);
		assertThat(inserted.getCreateTime()).isNotNull();
		assertThat(inserted.getUpdateTime()).isNotNull();
	}

	@Test
	@DisplayName("场景2: 数据集为空时，异步执行短路为 COMPLETED/progress=100/带完成时间，且不落任何结果")
	void asyncExecution_emptyDataset_completesWithoutPersistingResults() {
		when(datasetVersionMapper.selectById(DATASET_VERSION_ID)).thenReturn(
				DatasetVersionDO.builder().datasetId(DATASET_ID).datasetItems("[]").dataCount(0).build());
		when(datasetItemMapper.selectByDatasetIdAndItemIds(anyLong(), anyList()))
				.thenReturn(Collections.emptyList());

		experimentService.create(buildRequest(promptEvaluationObjectConfig(), "[]"));

		// 异步线程池执行，等待最终状态被写回
		ArgumentCaptor<ExperimentDO> updateCaptor = ArgumentCaptor.forClass(ExperimentDO.class);
		await().atMost(5, SECONDS).untilAsserted(() -> {
			verify(experimentMapper, atLeastOnce()).updateById(updateCaptor.capture());
			assertThat(updateCaptor.getAllValues())
					.anyMatch(entity -> "COMPLETED".equals(entity.getStatus()));
		});

		ExperimentDO completed = updateCaptor.getAllValues().stream()
				.filter(entity -> "COMPLETED".equals(entity.getStatus()))
				.findFirst().orElseThrow();
		assertThat(completed.getProgress()).isEqualTo(100);
		assertThat(completed.getCompleteTime()).isNotNull();

		// 实际行为：空数据集不会进入打分循环，因此不持久化任何实验结果
		verify(experimentResultMapper, never()).batchInsert(anyList());
	}

	@Test
	@DisplayName("场景3: 单数据项时，异步执行逐项打分并落库 experiment_result，最终 COMPLETED/progress=100")
	void asyncExecution_singleItem_persistsResultAndCompletes() throws Exception {
		// 数据集版本含 1 个数据项 id=1
		when(datasetVersionMapper.selectById(DATASET_VERSION_ID)).thenReturn(
				DatasetVersionDO.builder().datasetId(DATASET_ID).datasetItems("[1]").dataCount(1).build());
		when(datasetItemMapper.selectByDatasetIdAndItemIds(anyLong(), anyList())).thenReturn(List.of(
				DatasetItemDO.builder().id(1L).datasetId(DATASET_ID)
						.dataContent("{\"input\":\"hello\",\"reference_output\":\"ref\"}").build()));

		// 运行中状态（供 isExperimentStopped 检查，避免被判为已停止）
		when(experimentMapper.selectById(EXPERIMENT_ID))
				.thenReturn(ExperimentDO.builder().id(EXPERIMENT_ID).status("RUNNING").build());

		// Prompt 版本
		when(promptVersionService.getByPromptKeyAndVersion(anyString(), anyString()))
				.thenReturn(PromptVersionDetail.builder().promptKey("k").version("1.0")
						.template("t {{input}}").variables("{}").modelConfig("{}").build());
		when(modelConfigParser.replaceVariables(anyString(), anyString())).thenReturn("USER_PROMPT");

		// 会话与 LLM 调用边界打桩
		when(chatSessionService.createSession(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(ChatSession.builder().sessionId("s1").promptKey("k").version("1.0").build());
		ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
		when(chatClient.prompt(anyString()).messages(anyList()).call().content()).thenReturn("MOCK_OUTPUT");
		when(chatSessionService.getOrCreateSessionChatClient(anyString(), anyMap())).thenReturn(chatClient);

		// 评估器边界打桩
		when(evaluatorVersionMapper.selectById(11L)).thenReturn(
				EvaluatorVersionDO.builder().id(11L).variables("{}").modelConfig("{}").prompt("judge").build());
		EvaluatorDebugResult debugResult = new EvaluatorDebugResult();
		debugResult.setScore("0.9");
		debugResult.setReason("good");
		when(evaluatorServiceImpl.evaluatorTest(any())).thenReturn(debugResult);
		when(experimentResultMapper.batchInsert(anyList())).thenReturn(1);

		String evaluatorConfig = "[{\"evaluatorId\":1,\"evaluatorVersionId\":11,\"variableMap\":[]}]";
		experimentService.create(buildRequest(promptEvaluationObjectConfig(), evaluatorConfig));

		// 等待异步完成（写入 COMPLETED）
		ArgumentCaptor<ExperimentDO> updateCaptor = ArgumentCaptor.forClass(ExperimentDO.class);
		await().atMost(5, SECONDS).untilAsserted(() -> {
			verify(experimentMapper, atLeastOnce()).updateById(updateCaptor.capture());
			assertThat(updateCaptor.getAllValues())
					.anyMatch(entity -> "COMPLETED".equals(entity.getStatus()));
		});
		ExperimentDO completed = updateCaptor.getAllValues().stream()
				.filter(entity -> "COMPLETED".equals(entity.getStatus()))
				.findFirst().orElseThrow();
		assertThat(completed.getProgress()).isEqualTo(100);

		// 实际行为：每个数据项 * 每个评估器落一条结果，字段来自 LLM 输出与评估器打分
		@SuppressWarnings({ "unchecked", "rawtypes" })
		ArgumentCaptor<List<ExperimentResultDO>> resultCaptor = ArgumentCaptor.forClass((Class) List.class);
		verify(experimentResultMapper, atLeastOnce()).batchInsert(resultCaptor.capture());
		ExperimentResultDO saved = resultCaptor.getValue().get(0);
		assertThat(saved.getExperimentId()).isEqualTo(EXPERIMENT_ID);
		assertThat(saved.getInput()).isEqualTo("hello");
		assertThat(saved.getActualOutput()).isEqualTo("MOCK_OUTPUT");
		assertThat(saved.getReferenceOutput()).isEqualTo("ref");
		assertThat(saved.getScore()).isEqualByComparingTo(new BigDecimal("0.9"));
		assertThat(saved.getReason()).isEqualTo("good");
		assertThat(saved.getEvaluatorVersionId()).isEqualTo(11L);
	}

}
