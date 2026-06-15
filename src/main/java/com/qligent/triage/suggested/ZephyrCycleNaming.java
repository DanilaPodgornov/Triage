package com.qligent.triage.suggested;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class ZephyrCycleNaming {

	private static final String AI_PREFIX = "[AI] ";

	private ZephyrCycleNaming() {
	}

	static String formatCycleName(String suggestedName) {
		String base = suggestedName == null || suggestedName.isBlank()
				? "Suggested cycle"
				: suggestedName.trim();
		if (base.regionMatches(true, 0, AI_PREFIX.trim(), 0, AI_PREFIX.trim().length())) {
			return base;
		}
		return AI_PREFIX + base;
	}

	static String plannedStartDateUtc() {
		return LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC)
				.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	static String plannedEndDateUtc() {
		return LocalDate.now(ZoneOffset.UTC).plusDays(7).atTime(23, 59, 59).atOffset(ZoneOffset.UTC)
				.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}
}
