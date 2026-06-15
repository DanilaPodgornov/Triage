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
						new SuggestedTestCycle.TestMetrics(2, 1, 1, 0.5, "Fail", 1000L),
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

	@Test
	void shouldPersistLlmMetadataInJson() throws Exception {
		var storage = storage(tempDir);
		var cycle = SuggestedTestCycle.create(
				"SQM-SR1",
				"Cycle 1",
				"SQM",
				1781453505000L,
				"1781453505000_SQM-SR1",
				"qwen3.5-2b_ctx4096",
				"0.2.0",
				"0.3.0",
				List.of());

		storage.save(cycle);

		var loaded = storage.findByKey("SQM-SR1").orElseThrow();
		assertThat(loaded.llm()).isEqualTo("qwen3.5-2b_ctx4096");
		assertThat(loaded.aisortPromptVersion()).isEqualTo("0.2.0");
		assertThat(loaded.weightsPromptVersion()).isEqualTo("0.3.0");

		String json = Files.readString(tempDir.resolve("suggested_test_cycles").resolve("SQM-SR1.json"));
		assertThat(json).contains("\"llm\" : \"qwen3.5-2b_ctx4096\"");
		assertThat(json).contains("\"aisortPromptVersion\" : \"0.2.0\"");
		assertThat(json).contains("\"weightsPromptVersion\" : \"0.3.0\"");
	}

	@Test
	void shouldPersistSortStateInJson() throws Exception {
		var storage = storage(tempDir);
		var cycle = SuggestedTestCycle.create(
				"SQM-SR1",
				"Cycle 1",
				"SQM",
				1781453505000L,
				"1781453505000_SQM-SR1",
				List.of());
		storage.save(cycle);

		storage.update(cycle.withSortState("priority", "ASC"));

		var loaded = storage.findByKey("SQM-SR1").orElseThrow();
		assertThat(loaded.sortField()).isEqualTo("priority");
		assertThat(loaded.sortDirection()).isEqualTo("ASC");

		String json = Files.readString(tempDir.resolve("suggested_test_cycles").resolve("SQM-SR1.json"));
		assertThat(json).contains("\"sortField\" : \"priority\"");
		assertThat(json).contains("\"sortDirection\" : \"ASC\"");
	}

	@Test
	void shouldResolveCycleJsonPathUnderCyclesDirectory() {
		var storage = storage(tempDir);
		Path path = storage.cycleJsonPath("SQM-SR1");
		assertThat(path).isEqualTo(tempDir.resolve("suggested_test_cycles").resolve("SQM-SR1.json"));
	}

	@Test
	void shouldRejectInvalidCycleKeyForJsonPath() {
		var storage = storage(tempDir);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.cycleJsonPath("../secrets"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static SuggestedTestCycleStorage storage(Path tempDir) {
		return new SuggestedTestCycleStorage(
				new TriageProperties(
						tempDir.resolve("llm").toString(),
						tempDir.resolve("suggested_test_cycles").toString(),
						15,
						null,
						null),
				new ObjectMapper());
	}
}
