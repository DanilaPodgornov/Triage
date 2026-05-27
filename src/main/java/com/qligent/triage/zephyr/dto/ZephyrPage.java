package com.qligent.triage.zephyr.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZephyrPage<T>(
		String next,
		int startAt,
		int maxResults,
		int total,
		boolean isLast,
		List<T> values
) {
}
