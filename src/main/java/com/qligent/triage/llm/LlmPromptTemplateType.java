package com.qligent.triage.llm;

public enum LlmPromptTemplateType {
	WEIGHTS("weights"),
	AI_SORT("aisort");

	private final String fileSuffix;

	LlmPromptTemplateType(String fileSuffix) {
		this.fileSuffix = fileSuffix;
	}

	public String fileSuffix() {
		return fileSuffix;
	}
}
