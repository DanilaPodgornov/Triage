package com.qligent.triage.web.api;

public record PrioritizeJobResponse(
		String jobId,
		int totalTests
) {
}
