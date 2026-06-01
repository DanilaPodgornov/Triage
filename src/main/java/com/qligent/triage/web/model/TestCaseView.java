package com.qligent.triage.web.model;

public record TestCaseView(
		String key,
		String priority,
		String priorityColor,
		String priorityTextColor,
		String status,
		String folder,
		String name
) {
}
