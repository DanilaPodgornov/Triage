package com.qligent.triage.jira;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.qligent.triage.config.JiraProperties;
import com.qligent.triage.jira.dto.JiraIssueSummary;

@Component
public class JiraClient {

	private static final int DESCRIPTION_MAX_LENGTH = 2000;
	private static final String ISSUE_FIELDS = "summary,status,priority,description";

	private final RestClient restClient;
	private final JiraProperties properties;

	public JiraClient(
			@Autowired(required = false) @Qualifier("jiraRestClient") RestClient restClient,
			JiraProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public boolean isAvailable() {
		return properties.isConfigured() && restClient != null;
	}

	public Optional<JiraIssueSummary> getIssue(long issueId) {
		return getIssue(String.valueOf(issueId));
	}

	public Optional<JiraIssueSummary> getIssue(String issueIdOrKey) {
		if (!isAvailable()) {
			return Optional.empty();
		}
		try {
			var response = restClient.get()
					.uri("/rest/api/3/issue/{issueIdOrKey}?fields={fields}", issueIdOrKey, ISSUE_FIELDS)
					.retrieve()
					.body(JiraIssueResponse.class);
			if (response == null || response.fields() == null) {
				return Optional.empty();
			}
			return Optional.of(toSummary(response));
		} catch (RestClientException exception) {
			return Optional.empty();
		}
	}

	private JiraIssueSummary toSummary(JiraIssueResponse response) {
		var fields = response.fields();
		String status = fields.status() != null ? fields.status().name() : null;
		String priority = fields.priority() != null ? fields.priority().name() : null;
		String description = extractDescription(fields.description());
		return new JiraIssueSummary(response.key(), fields.summary(), status, priority, description);
	}

	private String extractDescription(Object descriptionField) {
		if (descriptionField == null) {
			return null;
		}
		if (descriptionField instanceof String text) {
			return truncate(text);
		}
		if (descriptionField instanceof Map<?, ?> document) {
			return truncate(adfToPlainText(document));
		}
		return truncate(descriptionField.toString());
	}

	@SuppressWarnings("unchecked")
	private String adfToPlainText(Map<?, ?> document) {
		Object content = document.get("content");
		if (!(content instanceof List<?> blocks)) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		for (Object block : blocks) {
			if (block instanceof Map<?, ?> blockMap) {
				appendAdfNode(builder, blockMap);
			}
		}
		return builder.toString().trim();
	}

	@SuppressWarnings("unchecked")
	private void appendAdfNode(StringBuilder builder, Map<?, ?> node) {
		Object text = node.get("text");
		if (text instanceof String stringValue) {
			builder.append(stringValue);
		}
		Object content = node.get("content");
		if (content instanceof List<?> children) {
			for (Object child : children) {
				if (child instanceof Map<?, ?> childMap) {
					appendAdfNode(builder, childMap);
				}
			}
		}
		String type = node.get("type") instanceof String stringType ? stringType : "";
		if ("paragraph".equals(type) || "heading".equals(type)) {
			builder.append('\n');
		}
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() <= DESCRIPTION_MAX_LENGTH) {
			return trimmed;
		}
		return trimmed.substring(0, DESCRIPTION_MAX_LENGTH) + "...";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record JiraIssueResponse(String key, JiraIssueFields fields) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record JiraIssueFields(
			String summary,
			JiraNamedField status,
			JiraNamedField priority,
			Object description
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record JiraNamedField(String name) {
	}

}
