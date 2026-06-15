package com.qligent.triage.llm;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.qligent.triage.config.LlmProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.qligent.triage.llm.dto.AiSortResponse;
import com.qligent.triage.llm.dto.OllamaChatRequest;
import com.qligent.triage.llm.dto.OllamaChatResponse;

@Component
public class LlmClient {

	private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

	private final RestClient restClient;
	private final LlmProperties properties;
	private final LlmRuntimeSettings runtimeSettings;
	private final ObjectMapper objectMapper;
	private final LlmPromptTemplateService promptTemplateService;

	public LlmClient(
			RestClient llmRestClient,
			LlmProperties properties,
			LlmRuntimeSettings runtimeSettings,
			ObjectMapper objectMapper,
			LlmPromptTemplateService promptTemplateService) {
		this.restClient = llmRestClient;
		this.properties = properties;
		this.runtimeSettings = runtimeSettings;
		this.objectMapper = objectMapper;
		this.promptTemplateService = promptTemplateService;
	}

	public String helloOneWord() {
		var request = new OllamaChatRequest(
				runtimeSettings.resolveModel(),
				false,
				null,
				List.of(new OllamaChatRequest.Message("user", "Say hello in one word")),
				new OllamaChatRequest.Options(runtimeSettings.numCtx())
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

	public Map<String, Double> requestAttributeWeights(String testCaseDataMarkdown) {
		String userPrompt = promptTemplateService.formatWeightsPrompt(testCaseDataMarkdown);
		String rawResponse = chatJson(userPrompt, "attribute weights");
		try {
			return parseAttributeWeightsJson(rawResponse);
		} catch (Exception exception) {
			log.error("Failed to parse attribute weights JSON. Raw response:\n{}", rawResponse);
			throw new IllegalStateException("Failed to parse attribute weights JSON.", exception);
		}
	}

	Map<String, Double> parseAttributeWeightsJson(String rawResponse) throws Exception {
		JsonNode root = objectMapper.readTree(rawResponse);
		Map<String, Double> weights = new LinkedHashMap<>();
		for (String key : AttributeWeightKeys.ALL) {
			JsonNode node = root.get(key);
			weights.put(key, node == null || node.isNull() ? 0.0 : node.asDouble());
		}
		return weights;
	}

	public AiSortResponse requestAiSort(String testCasesJson) {
		String userPrompt = promptTemplateService.formatAiSortPrompt(testCasesJson);
		String rawResponse = chatJson(userPrompt, "AI sort");
		try {
			return objectMapper.readValue(rawResponse, AiSortResponse.class);
		} catch (Exception exception) {
			log.error("Failed to parse AI sort JSON. Raw response:\n{}", rawResponse);
			throw new IllegalStateException("Failed to parse AI sort JSON.", exception);
		}
	}

	private String chatJson(String userPrompt, String purpose) {
		log.info(
				"Sending {} prompt to LLM: model={}, numCtx={}, promptChars={}",
				purpose,
				runtimeSettings.resolveModel(),
				runtimeSettings.numCtx(),
				userPrompt.length());
		log.info("LLM prompt:\n{}", userPrompt);

		var request = new OllamaChatRequest(
				runtimeSettings.resolveModel(),
				false,
				"json",
				List.of(new OllamaChatRequest.Message("user", userPrompt)),
				new OllamaChatRequest.Options(runtimeSettings.numCtx())
		);

		var response = restClient.post()
				.uri("/api/chat")
				.body(request)
				.retrieve()
				.body(OllamaChatResponse.class);

		if (response == null || response.message() == null || response.message().content() == null) {
			throw new IllegalStateException("LLM returned empty " + purpose + " response.");
		}

		String rawResponse = response.message().content().trim();
		log.info("LLM raw response:\n{}", rawResponse);
		return rawResponse;
	}
}
