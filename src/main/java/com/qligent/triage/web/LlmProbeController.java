package com.qligent.triage.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qligent.triage.llm.LlmClient;

@RestController
@RequestMapping("/api/llm")
public class LlmProbeController {

	private final LlmClient llmClient;

	public LlmProbeController(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	@GetMapping("/hello")
	public ResponseEntity<Map<String, String>> hello() {
		String llmResponse = llmClient.helloOneWord();
		return ResponseEntity.ok(Map.of(
				"status", "ok",
				"modelResponse", llmResponse
		));
	}
}
