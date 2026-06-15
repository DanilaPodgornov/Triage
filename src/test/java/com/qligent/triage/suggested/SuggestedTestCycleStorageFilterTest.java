package com.qligent.triage.suggested;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;

class SuggestedTestCycleStorageFilterTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldFindCyclesByProjectKeyField() {
		var storage = new SuggestedTestCycleStorage(
				new TriageProperties(
						tempDir.resolve("llm").toString(),
						tempDir.resolve("suggested_test_cycles").toString(),
						15,
						null,
						null),
				new ObjectMapper());
		storage.save(SuggestedTestCycle.create(
				"SQM-SR1",
				"High Priority Tests",
				"SQM",
				1781453505000L,
				"1781453505000_SQM-SR1",
				List.of()));
		storage.save(SuggestedTestCycle.create(
				"SB2B-SR1",
				"B2B Cycle",
				"SB2B",
				1781453505001L,
				"1781453505001_SB2B-SR1",
				List.of()));

		assertThat(storage.findByProjectPrefix("SQM")).hasSize(1);
		assertThat(storage.findByProjectPrefix("SQM").getFirst().suggestedTestCycleKey()).isEqualTo("SQM-SR1");
	}
}
