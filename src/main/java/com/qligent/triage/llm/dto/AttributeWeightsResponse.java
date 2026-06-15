package com.qligent.triage.llm.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeWeightsResponse(
		double priority,
		double status,
		double labels,
		double executionsCount,
		double failuresCount,
		double failedExecutionsRatio,
		double lastExecutionStatus,
		double executionTime
) {

	public Map<String, Double> asMap() {
		return Map.of(
				"priority", priority,
				"status", status,
				"labels", labels,
				"executionsCount", executionsCount,
				"failuresCount", failuresCount,
				"failedExecutionsRatio", failedExecutionsRatio,
				"lastExecutionStatus", lastExecutionStatus,
				"executionTime", executionTime);
	}
}
