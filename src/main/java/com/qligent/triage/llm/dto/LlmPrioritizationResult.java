package com.qligent.triage.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmPrioritizationResult(
		String suggestedTestCycleKey,
		String suggestedTestCycleName,
		List<String> testCases
) {
}
