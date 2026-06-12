package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.llm.LlmClient;
import com.qligent.triage.llm.dto.LlmPrioritizationResult;
import com.qligent.triage.model.Project;
import com.qligent.triage.suggested.SuggestedTestCycleStorage;
import com.qligent.triage.zephyr.ZephyrScaleClient;

@ExtendWith(MockitoExtension.class)
class PrioritizationServiceTest {

	@Mock
	private TestCaseEnrichmentService enrichmentService;

	@Mock
	private LlmClient llmClient;

	@Mock
	private ZephyrScaleClient zephyrScaleClient;

	private PrioritizationService prioritizationService;
	private SuggestedTestCycleStorage storage;

	@BeforeEach
	void setUp(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
		storage = new SuggestedTestCycleStorage(
				new TriageProperties(tempDir.resolve("suggestedTestCycles.json").toString(), null, null),
				new ObjectMapper());
		prioritizationService = new PrioritizationService(
				enrichmentService,
				llmClient,
				storage,
				zephyrScaleClient,
				new ObjectMapper());
	}

	@Test
	void shouldPrioritizeAndPersistSuggestedCycle() {
		when(zephyrScaleClient.resolveProjectKey(Project.B2C)).thenReturn("SQM");
		when(enrichmentService.enrichAll(Project.B2C, List.of("SQM-T1", "SQM-T2"))).thenReturn(List.of(
				TestCaseEnrichment.builder("SQM-T1").name("A").build(),
				TestCaseEnrichment.builder("SQM-T2").name("B").build()));
		when(llmClient.prioritizeTestCases(any(), eq(List.of("SQM-T1", "SQM-T2")))).thenReturn(
				new LlmPrioritizationResult(null, "Regression Sprint", List.of("SQM-T2", "SQM-T1")));

		var cycle = prioritizationService.prioritize(Project.B2C, List.of("SQM-T1", "SQM-T2"));

		assertThat(cycle.suggestedTestCycleKey()).isEqualTo("SQM-SR1");
		assertThat(cycle.testCases()).containsExactly("SQM-T2", "SQM-T1");
		assertThat(storage.findByKey("SQM-SR1")).isPresent();
		verify(enrichmentService).enrichAll(Project.B2C, List.of("SQM-T1", "SQM-T2"));
	}

}
