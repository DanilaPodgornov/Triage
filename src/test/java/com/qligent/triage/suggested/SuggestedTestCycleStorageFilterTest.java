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
	void shouldFindCycleByTestCasePrefixWhenLlmReturnedPlaceholderKey() {
		var storage = new SuggestedTestCycleStorage(
				new TriageProperties(tempDir.resolve("cycles.json").toString(), null),
				new ObjectMapper());
		storage.add(SuggestedTestCycle.create(
				"PROJECT-SR4",
				"High Priority Tests",
				List.of("SQM-T7", "SQM-T8")));

		var result = storage.findByProjectPrefix("SQM");

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().suggestedTestCycleKey()).isEqualTo("PROJECT-SR4");
	}

}
