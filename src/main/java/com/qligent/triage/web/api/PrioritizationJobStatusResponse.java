package com.qligent.triage.web.api;

public record PrioritizationJobStatusResponse(
		String status,
		int completedTests,
		int totalTests,
		int currentTest,
		String currentTestCaseKey,
		long elapsedMillis,
		String cycleKey,
		String redirectUrl,
		String errorMessage
) {
}
