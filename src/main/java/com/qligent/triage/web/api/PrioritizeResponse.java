package com.qligent.triage.web.api;

public record PrioritizeResponse(
		String suggestedTestCycleKey,
		String redirectUrl
) {
}
