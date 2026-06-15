package com.qligent.triage.llm;

import org.springframework.stereotype.Service;

import com.qligent.triage.config.LlmProperties;

@Service
public class LlmPromptTemplateService {

	private final LlmRuntimeSettings runtimeSettings;
	private final PromptTemplateStore promptTemplateStore;

	public LlmPromptTemplateService(
			LlmRuntimeSettings runtimeSettings,
			PromptTemplateStore promptTemplateStore) {
		this.runtimeSettings = runtimeSettings;
		this.promptTemplateStore = promptTemplateStore;
	}

	public String getTemplate() {
		return loadTemplate(LlmPromptTemplateType.WEIGHTS);
	}

	public String format(LlmPromptTemplateType type, String placeholderKey, String placeholderValue) {
		String template = loadTemplate(type);
		return template.replace("{" + placeholderKey + "}", placeholderValue);
	}

	public String formatWeightsPrompt(String testCaseData) {
		return format(LlmPromptTemplateType.WEIGHTS, "testCaseData", testCaseData);
	}

	public String formatAiSortPrompt(String testCasesJson) {
		return format(LlmPromptTemplateType.AI_SORT, "testCasesJson", testCasesJson);
	}

	public String templateResourceName(LlmPromptTemplateType type) {
		String version = promptTemplateStore.resolveActiveVersion(
				runtimeSettings.resolveModel(),
				runtimeSettings.numCtx(),
				type,
				runtimeSettings.activeVersion(type));
		return promptTemplateStore.templateFileName(
				runtimeSettings.resolveModel(),
				runtimeSettings.numCtx(),
				type,
				version);
	}

	private String loadTemplate(LlmPromptTemplateType type) {
		return promptTemplateStore.readActive(
				runtimeSettings.resolveModel(),
				runtimeSettings.numCtx(),
				type,
				runtimeSettings.activeVersion(type));
	}
}
