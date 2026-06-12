package com.qligent.triage.llm;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.qligent.triage.config.LlmProperties;
import tools.jackson.databind.ObjectMapper;

import com.qligent.triage.llm.dto.LlmPrioritizationResult;
import com.qligent.triage.llm.dto.OllamaChatRequest;
import com.qligent.triage.llm.dto.OllamaChatResponse;

@Component
public class LlmClient {

	private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

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
		log.info(
				"Sending prioritization prompt to LLM: model={}, promptChars={}",
				properties.model(),
				userPrompt.length());
		log.info("LLM prompt:\n{}", userPrompt);

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

		String rawResponse = response.message().content().trim();
		log.info("LLM raw response:\n{}", rawResponse);

		try {
			LlmPrioritizationResult result = objectMapper.readValue(rawResponse, LlmPrioritizationResult.class);
			log.info(
					"LLM prioritization response: suggestedTestCycleName={}, testCases={}",
					result.suggestedTestCycleName(),
					result.testCases());
			return result;
		} catch (Exception exception) {
			log.error("Failed to parse LLM prioritization JSON. Raw response:\n{}", rawResponse);
			throw new IllegalStateException("Failed to parse LLM prioritization JSON.", exception);
		}
	}
}
