package com.qligent.triage.suggested;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestedTestCyclesFile(
		List<SuggestedTestCycle> suggestedTestCycles
) {

	public static SuggestedTestCyclesFile empty() {
		return new SuggestedTestCyclesFile(new ArrayList<>());
	}

}
