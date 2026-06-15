package com.qligent.triage.llm.dto;

public record OllamaPullRequest(String name, boolean stream) {

	public OllamaPullRequest(String name) {
		this(name, false);
	}
}
