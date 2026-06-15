package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.qligent.triage.config.LlmProperties;

import tools.jackson.databind.ObjectMapper;

class LlmPromptTemplateServiceTest {

	@TempDir
	Path tempDir;

	private LlmPromptTemplateService service;

	@BeforeEach
	void setUp() throws Exception {
		Path templatesDir = tempDir.resolve("prompt_templates");
		Files.createDirectories(templatesDir);
		Files.writeString(
				templatesDir.resolve("qwen2.5-3b_ctx8192_weights-0.1.0.md"),
				"Weights template {testCaseData}");
		Files.writeString(
				templatesDir.resolve("qwen2.5-3b_ctx8192_aisort-0.1.0.md"),
				"AI sort template {testCasesJson}");

		var properties = new LlmProperties(
				"http://localhost:11434",
				"qwen2.5:3b",
				8192,
				null,
				templatesDir.toString(),
				tempDir.resolve("settings.json").toString());
		var runtimeSettings = new LlmRuntimeSettings(properties, new ObjectMapper());
		var store = new PromptTemplateStore(properties);
		service = new LlmPromptTemplateService(runtimeSettings, store);
	}

	@Test
	void shouldLoadAndFormatWeightsTemplate() {
		assertThat(service.getTemplate()).contains("{testCaseData}");

		String formatted = service.formatWeightsPrompt("## Test case: SQM-T1");
		assertThat(formatted).contains("SQM-T1");
		assertThat(formatted).doesNotContain("{testCaseData}");
	}

	@Test
	void shouldResolveTemplateResourceNameFromModelAndContext() {
		assertThat(service.templateResourceName(LlmPromptTemplateType.WEIGHTS))
				.isEqualTo("qwen2.5-3b_ctx8192_weights-0.1.0.md");
		assertThat(service.templateResourceName(LlmPromptTemplateType.AI_SORT))
				.isEqualTo("qwen2.5-3b_ctx8192_aisort-0.1.0.md");
	}
}
