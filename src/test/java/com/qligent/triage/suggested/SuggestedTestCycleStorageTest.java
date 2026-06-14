package com.qligent.triage.suggested;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;

class SuggestedTestCycleStorageTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldPersistAndLoadSuggestedCycleJson() {
		var storage = storage(tempDir);
		var cycle = SuggestedTestCycle.create(
				"SQM-SR1",
				"Cycle 1",
				"SQM",
				1781453505000L,
				"1781453505000_SQM-SR1",
				List.of(new SuggestedTestCycle.SuggestedTestCaseEntry(
						"SQM-T1",
						"Login test",
						Map.of("priority", 0.5, "status", 0.5),
						new SuggestedTestCycle.TestMetrics(2, 1, 1, "Fail", 1000L),
						0.6)));

		storage.save(cycle);

		var loaded = storage.findByKey("SQM-SR1").orElseThrow();
		assertThat(loaded.suggestedTestCycleName()).isEqualTo("Cycle 1");
		assertThat(loaded.tests()).hasSize(1);
		assertThat(loaded.tests().getFirst().key()).isEqualTo("SQM-T1");
		assertThat(Files.exists(tempDir.resolve("suggested_test_cycles").resolve("SQM-SR1.json"))).isTrue();
	}

	@Test
	void shouldListCyclesByProject() {
		var storage = storage(tempDir);
		storage.save(SuggestedTestCycle.create(
				"SQM-SR1", "A", "SQM", 1L, "folder-a", List.of()));
		storage.save(SuggestedTestCycle.create(
				"SB2B-SR1", "B", "SB2B", 2L, "folder-b", List.of()));

		assertThat(storage.findByProjectPrefix("SQM")).hasSize(1);
		assertThat(storage.findByProjectPrefix("SB2B")).hasSize(1);
	}

	private static SuggestedTestCycleStorage storage(Path tempDir) {
		return new SuggestedTestCycleStorage(
				new TriageProperties(
						tempDir.resolve("llm").toString(),
						tempDir.resolve("suggested_test_cycles").toString(),
						null,
						null),
				new ObjectMapper());
	}
}
