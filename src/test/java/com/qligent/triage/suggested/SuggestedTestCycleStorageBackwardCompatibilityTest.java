package com.qligent.triage.suggested;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;

class SuggestedTestCycleStorageBackwardCompatibilityTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldLoadCycleJsonWithoutFailedExecutionsCountField() throws Exception {
		Path cyclesDir = tempDir.resolve("suggested_test_cycles");
		Files.createDirectories(cyclesDir);
		Files.writeString(cyclesDir.resolve("SQM-SR1.json"), """
				{
				  "suggestedTestCycleKey": "SQM-SR1",
				  "suggestedTestCycleName": "Legacy cycle",
				  "suggestedTestCycleStatus": "Waiting",
				  "projectKey": "SQM",
				  "createdAt": 1,
				  "artifactFolder": "folder",
				  "sortField": "aiSort",
				  "sortDirection": "DESC",
				  "tests": [{
				    "key": "SQM-T1",
				    "name": "Test",
				    "weights": { "priority": 1.0, "status": 0.0, "labels": 0.0, "executionsCount": 0.0, "failuresCount": 0.0, "lastExecutionStatus": 0.0, "executionTime": 0.0 },
				    "metrics": {
				      "executionsCount": 3,
				      "failuresCount": 1,
				      "lastExecutionStatus": "Pass",
				      "executionTimeMs": 1000
				    },
				    "aiSort": 1.0
				  }]
				}
				""");

		var storage = new SuggestedTestCycleStorage(
				new TriageProperties(null, cyclesDir.toString(), 15, null, null),
				new ObjectMapper());

		var cycles = storage.findByProjectPrefix("SQM");

		assertThat(cycles).hasSize(1);
		assertThat(cycles.getFirst().tests().getFirst().metrics().failedExecutionsCount()).isZero();
		assertThat(cycles.getFirst().tests().getFirst().metrics().failedExecutionsRatio()).isZero();
		assertThat(cycles.getFirst().zephyrTestCycleKey()).isNull();
		assertThat(cycles.getFirst().llm()).isEqualTo(SuggestedTestCycle.LEGACY_LLM);
		assertThat(cycles.getFirst().aisortPromptVersion()).isEqualTo(SuggestedTestCycle.LEGACY_PROMPT_VERSION);
		assertThat(cycles.getFirst().weightsPromptVersion()).isEqualTo(SuggestedTestCycle.LEGACY_PROMPT_VERSION);
	}

	@Test
	void shouldComputeFailedExecutionsRatioFromLegacyFailedCountField() throws Exception {
		Path cyclesDir = tempDir.resolve("suggested_test_cycles");
		Files.createDirectories(cyclesDir);
		Files.writeString(cyclesDir.resolve("SQM-SR2.json"), """
				{
				  "suggestedTestCycleKey": "SQM-SR2",
				  "suggestedTestCycleName": "Legacy cycle",
				  "suggestedTestCycleStatus": "Waiting",
				  "projectKey": "SQM",
				  "createdAt": 1,
				  "artifactFolder": "folder",
				  "sortField": "aiSort",
				  "sortDirection": "DESC",
				  "tests": [{
				    "key": "SQM-T1",
				    "name": "Test",
				    "weights": { "priority": 1.0, "status": 0.0, "labels": 0.0, "executionsCount": 0.0, "failuresCount": 0.0, "failedExecutionsRatio": 0.0, "lastExecutionStatus": 0.0, "executionTime": 0.0 },
				    "metrics": {
				      "executionsCount": 4,
				      "failuresCount": 1,
				      "failedExecutionsCount": 2,
				      "lastExecutionStatus": "Fail",
				      "executionTimeMs": 1000
				    },
				    "aiSort": 1.0
				  }]
				}
				""");

		var storage = new SuggestedTestCycleStorage(
				new TriageProperties(null, cyclesDir.toString(), 15, null, null),
				new ObjectMapper());

		var metrics = storage.findByKey("SQM-SR2").orElseThrow().tests().getFirst().metrics();

		assertThat(metrics.failedExecutionsRatio()).isEqualTo(0.5);
	}
}
