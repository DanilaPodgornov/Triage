package com.qligent.triage.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueSummary(
		String key,
		String summary,
		String status,
		String priority,
		String description
) {
}
