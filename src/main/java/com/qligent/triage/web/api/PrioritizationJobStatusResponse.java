package com.qligent.triage.web.api;

public record PrioritizationJobStatusResponse(
		String status,
		int completedTests,
		int totalTests,
		long elapsedMillis,
		String redirectUrl,
		String errorMessage
) {
}
