package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WeightValidatorTest {

	private final WeightValidator validator = new WeightValidator();

	@Test
	void shouldNormalizeWeightsWhenSumIsCloseToOne() {
		Map<String, Double> normalized = validator.validateAndNormalize(Map.of(
				"priority", 0.2,
				"status", 0.1,
				"labels", 0.1,
				"executionsCount", 0.2,
				"failuresCount", 0.1,
				"lastExecutionStatus", 0.1,
				"executionTime", 0.19));

		assertThat(AttributeWeightKeys.sum(normalized)).isEqualTo(1.0, within(1e-6));
	}

	@Test
	void shouldDefaultMissingWeightKeyToZero() {
		Map<String, Double> normalized = validator.validateAndNormalize(Map.of(
				"priority", 0.25,
				"status", 0.25,
				"labels", 0.1,
				"executionsCount", 0.2,
				"failuresCount", 0.1,
				"lastExecutionStatus", 0.1));

		assertThat(normalized.get("executionTime")).isEqualTo(0.0);
		assertThat(AttributeWeightKeys.sum(normalized)).isEqualTo(1.0, within(1e-6));
	}

	private static org.assertj.core.data.Offset<Double> within(double offset) {
		return org.assertj.core.data.Offset.offset(offset);
	}
}
