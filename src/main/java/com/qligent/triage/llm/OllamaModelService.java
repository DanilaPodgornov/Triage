package com.qligent.triage.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.qligent.triage.llm.dto.OllamaPullRequest;
import com.qligent.triage.llm.dto.OllamaPullResult;
import com.qligent.triage.llm.dto.OllamaTagsResponse;

@Service
public class OllamaModelService {


	private final RestClient restClient;

	public OllamaModelService(@Qualifier("llmRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	public List<OllamaTagsResponse.ModelTag> listInstalled() {
		OllamaTagsResponse response = restClient.get()
				.uri("/api/tags")
				.retrieve()
				.body(OllamaTagsResponse.class);
		if (response == null || response.models() == null) {
			return List.of();
		}
		return response.models().stream()
				.sorted(Comparator.comparing(
						tag -> tag.name() == null ? "" : tag.name(),
						String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public List<String> listInstalledNames() {
		return listInstalled().stream()
				.map(OllamaTagsResponse.ModelTag::name)
				.filter(name -> name != null && !name.isBlank())
				.toList();
	}

	public boolean isInstalled(String model) {
		if (model == null || model.isBlank()) {
			return false;
		}
		return listInstalledNames().stream().anyMatch(name -> name.equalsIgnoreCase(model));
	}

	public OllamaPullResult pull(String model) {
		List<String> statusLog = new ArrayList<>();
		String[] lastStatus = { "" };
		try {
			restClient.post()
					.uri("/api/pull")
					.body(new OllamaPullRequest(model, true))
					.exchange((request, response) -> {
						readPullResponse(model, response, statusLog, lastStatus);
						return null;
					});
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new IllegalArgumentException(formatPullFailure(model, exception.getMessage()), exception);
		}
		return new OllamaPullResult(lastStatus[0], List.copyOf(statusLog));
	}

	public List<OllamaTagsResponse.ModelTag> listInstalledOrEmptyOnFailure() {
		try {
			return listInstalled();
		} catch (RuntimeException exception) {
			return Collections.emptyList();
		}
	}

	private void readPullResponse(
			String model,
			ClientHttpResponse response,
			List<String> statusLog,
			String[] lastStatus) throws IOException {
		if (response.getStatusCode().isError()) {
			String body = readResponseBody(response);
			throw new IllegalArgumentException(formatPullFailure(model, body));
		}
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String error = extractJsonStringField(line, "error");
				if (error != null && !error.isBlank()) {
					throw new IllegalArgumentException(error);
				}
				String status = extractJsonStringField(line, "status");
				if (status == null || status.isBlank()) {
					continue;
				}
				String formatted = formatPullStatus(line, status);
				lastStatus[0] = formatted;
				statusLog.add(formatted);
			}
		}
	}

	private static String readResponseBody(ClientHttpResponse response) throws IOException {
		if (response.getBody() == null) {
			return response.getStatusCode() + " " + response.getStatusText();
		}
		return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private static String formatPullStatus(String line, String status) {
		String completed = extractJsonNumberField(line, "completed");
		String total = extractJsonNumberField(line, "total");
		if (completed != null && total != null && !"0".equals(total)) {
			return status + " (" + completed + " / " + total + ")";
		}
		return status;
	}

	private static String formatPullFailure(String model, String details) {
		String trimmed = details == null ? "" : details.trim();
		if (trimmed.isBlank()) {
			return "Failed to pull model \"" + model + "\".";
		}
		String error = extractJsonStringField(trimmed, "error");
		if (error != null && !error.isBlank()) {
			return error;
		}
		return trimmed;
	}

	private static String extractJsonStringField(String json, String field) {
		Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
		Matcher matcher = pattern.matcher(json);
		if (!matcher.find()) {
			return null;
		}
		return unescapeJson(matcher.group(1));
	}

	private static String extractJsonNumberField(String json, String field) {
		Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
		Matcher matcher = pattern.matcher(json);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String unescapeJson(String value) {
		return value
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\t", "\t")
				.replace("\\\"", "\"")
				.replace("\\\\", "\\");
	}
}
