package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class LlmClientAttributeWeightsParseTest {

	@Test
	void shouldParseAttributeWeightsWithMissingExecutionTime() throws Exception {
		var client = new LlmClient(null, null, null, new ObjectMapper(), null);
		String rawResponse = """
				{
				  "priority": 0.25,
				  "status": 0.25,
				  "labels": 0.1,
				  "executionsCount": 0.2,
				  "failuresCount": 0.1,
				  "lastExecutionStatus": 0.1
				}
				""";

		Map<String, Double> weights = client.parseAttributeWeightsJson(rawResponse);

		assertThat(weights.get("executionTime")).isEqualTo(0.0);
		assertThat(weights.get("failedExecutionsRatio")).isEqualTo(0.0);
		assertThat(AttributeWeightKeys.sum(weights)).isEqualTo(1.0);
	}
}
