package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.llm.AiSortValidator;
import com.qligent.triage.llm.LlmClient;
import com.qligent.triage.llm.LlmPromptTemplateType;
import com.qligent.triage.llm.LlmRuntimeSettings;
import com.qligent.triage.llm.WeightValidator;
import com.qligent.triage.llm.dto.AiSortResponse;
import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycle;
import com.qligent.triage.suggested.SuggestedTestCycleStorage;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.PriorityDto;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDetailDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@ExtendWith(MockitoExtension.class)
class PrioritizationServiceTest {

	@TempDir
	Path tempDir;

	@Mock
	private PrioritizationArtifactService artifactService;

	@Mock
	private TestCasePromptRenderer promptRenderer;

	@Mock
	private LlmClient llmClient;

	@Mock
	private WeightValidator weightValidator;

	@Mock
	private AiSortValidator aiSortValidator;

	@Mock
	private ZephyrScaleClient zephyrScaleClient;

	@Mock
	private LlmRuntimeSettings llmRuntimeSettings;

	private PrioritizationService prioritizationService;
	private SuggestedTestCycleStorage storage;

	@BeforeEach
	void setUp() {
		storage = new SuggestedTestCycleStorage(
				new TriageProperties(
						tempDir.resolve("llm").toString(),
						tempDir.resolve("suggested_test_cycles").toString(),
						15,
						null,
						null),
				new ObjectMapper());
		prioritizationService = new PrioritizationService(
				artifactService,
				promptRenderer,
				llmClient,
				weightValidator,
				aiSortValidator,
				storage,
				zephyrScaleClient,
				new ObjectMapper(),
				llmRuntimeSettings);
	}

	@Test
	void shouldPrioritizeAndPersistSuggestedCycleWithWeights() {
		Path artifactFolder = tempDir.resolve("llm").resolve("1781453505000_SQM-SR1");
		when(zephyrScaleClient.resolveProjectKey(Project.B2C)).thenReturn("SQM");
		when(zephyrScaleClient.getTestCase("SQM-T1")).thenReturn(testCase("SQM-T1"));
		when(zephyrScaleClient.getTestCase("SQM-T2")).thenReturn(testCase("SQM-T2"));
		when(zephyrScaleClient.getStatuses("SQM", 0, 100)).thenReturn(statusPage());
		when(zephyrScaleClient.getPriorities("SQM")).thenReturn(priorityPage());
		when(zephyrScaleClient.getTestExecutions(any(), eq(0), eq(100))).thenReturn(emptyExecutionsPage());
		when(artifactService.resolveNextSequence("SQM")).thenReturn(1);
		when(artifactService.createArtifactFolder("SQM", 1)).thenReturn(
				new PrioritizationArtifactService.ArtifactFolder(
						artifactFolder,
						"1781453505000_SQM-SR1",
						"SQM-SR1",
						1781453505000L));
		when(promptRenderer.render(any(), any(), any(), any())).thenReturn("prompt markdown");
		when(llmClient.requestAttributeWeights("prompt markdown")).thenReturn(sampleWeights());
		when(weightValidator.validateAndNormalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(llmClient.requestAiSort(any())).thenReturn(new AiSortResponse(
				"Weighted Sprint",
				List.of(
						new AiSortResponse.AiSortTestEntry("SQM-T1", 0.6),
						new AiSortResponse.AiSortTestEntry("SQM-T2", 0.4))));
		when(aiSortValidator.validateAndNormalize(eq(List.of("SQM-T1", "SQM-T2")), any())).thenReturn(Map.of(
				"SQM-T1", 0.6,
				"SQM-T2", 0.4));
		when(llmRuntimeSettings.resolveModel()).thenReturn("qwen2.5:3b");
		when(llmRuntimeSettings.numCtx()).thenReturn(8192);
		when(llmRuntimeSettings.activeVersion(LlmPromptTemplateType.AI_SORT)).thenReturn("0.1.0");
		when(llmRuntimeSettings.activeVersion(LlmPromptTemplateType.WEIGHTS)).thenReturn("0.1.0");

		var cycle = prioritizationService.prioritize(Project.B2C, List.of("SQM-T1", "SQM-T2"));

		assertThat(cycle.suggestedTestCycleKey()).isEqualTo("SQM-SR1");
		assertThat(cycle.suggestedTestCycleName()).isEqualTo("Weighted Sprint");
		assertThat(cycle.llm()).isEqualTo("qwen2.5-3b_ctx8192");
		assertThat(cycle.aisortPromptVersion()).isEqualTo("0.1.0");
		assertThat(cycle.weightsPromptVersion()).isEqualTo("0.1.0");
		assertThat(cycle.tests()).hasSize(2);
		assertThat(cycle.tests().getFirst().aiSort()).isEqualTo(0.6);
		assertThat(storage.findByKey("SQM-SR1")).isPresent();
		verify(artifactService).writePromptFile(artifactFolder, "SQM-T1", "prompt markdown");
		verify(artifactService).writeResponseFile(eq(artifactFolder), eq("SQM-T1"), any());
	}

	private static TestCaseDetailDto testCase(String key) {
		return new TestCaseDetailDto(
				1L,
				key,
				"Test " + key,
				null,
				null,
				null,
				List.of("smoke"),
				new TestCaseDetailDto.PriorityRef(1L),
				new TestCaseDetailDto.StatusRef(2L));
	}

	private static ZephyrPage<StatusDto> statusPage() {
		return new ZephyrPage<>(null, 0, 100, 1, true, List.of(new StatusDto(2L, "Approved")));
	}

	private static ZephyrPage<PriorityDto> priorityPage() {
		return new ZephyrPage<>(null, 0, 100, 1, true, List.of(new PriorityDto(1L, "High", "#ff0000")));
	}

	private static ZephyrPage<com.qligent.triage.zephyr.dto.TestExecutionDto> emptyExecutionsPage() {
		return new ZephyrPage<>(null, 0, 100, 0, true, List.of());
	}

	private static Map<String, Double> sampleWeights() {
		return Map.of(
				"priority", 0.2,
				"status", 0.1,
				"labels", 0.1,
				"executionsCount", 0.2,
				"failuresCount", 0.1,
				"failedExecutionsRatio", 0.0,
				"lastExecutionStatus", 0.1,
				"executionTime", 0.2);
	}
}
