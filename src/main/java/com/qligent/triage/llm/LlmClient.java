package com.qligent.triage.llm;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.qligent.triage.config.LlmProperties;
import com.qligent.triage.llm.dto.OllamaChatRequest;
import com.qligent.triage.llm.dto.OllamaChatResponse;

@Component
public class LlmClient {

	private final RestClient restClient;
	private final LlmProperties properties;

	public LlmClient(RestClient llmRestClient, LlmProperties properties) {
		this.restClient = llmRestClient;
		this.properties = properties;
	}

	public String helloOneWord() {
		var request = new OllamaChatRequest(
				properties.model(),
				false,
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
}
