package com.qligent.triage.suggested;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestedTestCycle(
		String suggestedTestCycleKey,
		String suggestedTestCycleName,
		String suggestedTestCycleStatus,
		List<String> testCases
) {

	public SuggestedTestCycle withTestCases(List<String> testCases) {
		return new SuggestedTestCycle(suggestedTestCycleKey, suggestedTestCycleName, suggestedTestCycleStatus, testCases);
	}

	public SuggestedTestCycle withStatus(SuggestedTestCycleStatus status) {
		return new SuggestedTestCycle(suggestedTestCycleKey, suggestedTestCycleName, status.label(), testCases);
	}

	public static SuggestedTestCycle create(String key, String name, List<String> testCases) {
		return new SuggestedTestCycle(key, name, SuggestedTestCycleStatus.WAITING.label(), new ArrayList<>(testCases));
	}

}
