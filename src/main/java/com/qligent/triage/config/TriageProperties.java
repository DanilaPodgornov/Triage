package com.qligent.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triage")
public record TriageProperties(
		String suggestedCyclesFile,
		Theme theme,
		Locale locale
) {

	public TriageProperties {
		if (theme == null) {
			theme = new Theme("system");
		}
		if (locale == null) {
			locale = new Locale("en");
		}
	}

	public record Theme(String defaultTheme) {

		public Theme {
			if (defaultTheme == null || defaultTheme.isBlank()) {
				defaultTheme = "system";
			}
		}
	}

	public record Locale(String defaultLocale) {

		public Locale {
			if (defaultLocale == null || defaultLocale.isBlank()) {
				defaultLocale = "en";
			}
		}
	}
}
