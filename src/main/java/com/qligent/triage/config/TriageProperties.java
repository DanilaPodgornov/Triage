package com.qligent.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage")
public record TriageProperties(
		String suggestedCyclesFile,
		Theme theme
) {

	public TriageProperties {
		if (theme == null) {
			theme = new Theme("system");
		}
	}

	public record Theme(String defaultTheme) {

		public Theme {
			if (defaultTheme == null || defaultTheme.isBlank()) {
				defaultTheme = "system";
			}
		}
	}
}
