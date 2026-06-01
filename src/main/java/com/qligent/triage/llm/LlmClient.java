package com.qligent.triage.llm;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.qligent.triage.config.LlmProperties;
import tools.jackson.databind.ObjectMapper;

import com.qligent.triage.llm.dto.LlmPrioritizationResult;
import com.qligent.triage.llm.dto.OllamaChatRequest;
import com.qligent.triage.llm.dto.OllamaChatResponse;

@Component
public class LlmClient {

	private final RestClient restClient;
	private final LlmProperties properties;
	private final ObjectMapper objectMapper;
	private final LlmPromptTemplateService promptTemplateService;

	public LlmClient(
			RestClient llmRestClient,
			LlmProperties properties,
			ObjectMapper objectMapper,
			LlmPromptTemplateService promptTemplateService) {
		this.restClient = llmRestClient;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.promptTemplateService = promptTemplateService;
	}

	public String helloOneWord() {
		var request = new OllamaChatRequest(
				properties.model(),
				false,
				null,
				List.of(new OllamaChatRequest.Message("user", "Say hello in one word"))
		);

		var response = restClient.post()
				.uri("/api/chat")
				.body(request)
				.retrieve()
				.body(OllamaChatResponse.class);

		if (response == null || response.message() == null || response.message().content() == null) {
			throw new IllegalStateException("LLM returned empty response.");
		}
		return response.message().content().trim();
	}

	public LlmPrioritizationResult prioritizeTestCases(String testCasesJson, List<String> inputKeys) {
		String userPrompt = promptTemplateService.format(String.join(", ", inputKeys), testCasesJson);

		var request = new OllamaChatRequest(
				properties.model(),
				false,
				"json",
				List.of(new OllamaChatRequest.Message("user", userPrompt))
		);

		var response = restClient.post()
				.uri("/api/chat")
				.body(request)
				.retrieve()
				.body(OllamaChatResponse.class);

		if (response == null || response.message() == null || response.message().content() == null) {
			throw new IllegalStateException("LLM returned empty prioritization response.");
		}

		try {
			return objectMapper.readValue(response.message().content().trim(), LlmPrioritizationResult.class);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to parse LLM prioritization JSON.", exception);
		}
	}
}
