package com.qligent.triage.suggested;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;

class SuggestedTestCycleStorageTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldPersistAndLoadSuggestedCycles() {
		Path file = tempDir.resolve("suggestedTestCycles.json");
		var storage = new SuggestedTestCycleStorage(new TriageProperties(file.toString(), null), new ObjectMapper());

		storage.add(SuggestedTestCycle.create("SQM-SR1", "Cycle 1", java.util.List.of("SQM-T1", "SQM-T2")));

		var loaded = storage.findByKey("SQM-SR1").orElseThrow();
		assertThat(loaded.suggestedTestCycleName()).isEqualTo("Cycle 1");
		assertThat(loaded.testCases()).containsExactly("SQM-T1", "SQM-T2");
		assertThat(Files.exists(file)).isTrue();
	}

	@Test
	void shouldGenerateNextSuggestedCycleKey() {
		Path file = tempDir.resolve("suggestedTestCycles.json");
		var storage = new SuggestedTestCycleStorage(new TriageProperties(file.toString(), null), new ObjectMapper());
		storage.add(SuggestedTestCycle.create("SQM-SR1", "Cycle 1", java.util.List.of("SQM-T1")));

		assertThat(storage.nextSuggestedCycleKey("SQM")).isEqualTo("SQM-SR2");
	}

}
