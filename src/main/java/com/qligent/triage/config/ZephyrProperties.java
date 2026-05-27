package com.qligent.triage.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zephyr")
public record ZephyrProperties(
		String apiUrl,
		String apiToken,
		int pageSize,
		Map<String, String> projects
) {
}
