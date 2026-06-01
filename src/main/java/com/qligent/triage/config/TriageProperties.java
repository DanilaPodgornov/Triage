package com.qligent.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage")
public record TriageProperties(
		String suggestedCyclesFile
) {
}
