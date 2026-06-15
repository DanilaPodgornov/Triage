package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OllamaModelServiceParsingTest {

	@Test
	void shouldExtractOllamaErrorFromJsonLine() {
		String line = "{\"error\":\"pull model manifest: file does not exist\"}";

		assertThatThrownBy(() -> parsePullLine(line))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("pull model manifest: file does not exist");
	}

	@Test
	void shouldFormatPullStatusWithProgress() {
		String line = "{\"status\":\"downloading\",\"completed\":128,\"total\":512}";

		assertThat(formatStatus(line)).isEqualTo("downloading (128 / 512)");
	}

	private static void parsePullLine(String line) {
		String error = extractJsonStringField(line, "error");
		if (error != null && !error.isBlank()) {
			throw new IllegalArgumentException(error);
		}
	}

	private static String formatStatus(String line) {
		String status = extractJsonStringField(line, "status");
		String completed = extractJsonNumberField(line, "completed");
		String total = extractJsonNumberField(line, "total");
		if (completed != null && total != null && !"0".equals(total)) {
			return status + " (" + completed + " / " + total + ")";
		}
		return status;
	}

	private static String extractJsonStringField(String json, String field) {
		var pattern = java.util.regex.Pattern.compile(
				"\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
		var matcher = pattern.matcher(json);
		if (!matcher.find()) {
			return null;
		}
		return matcher.group(1);
	}

	private static String extractJsonNumberField(String json, String field) {
		var pattern = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
		var matcher = pattern.matcher(json);
		return matcher.find() ? matcher.group(1) : null;
	}
}
