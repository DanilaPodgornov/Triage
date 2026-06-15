package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.qligent.triage.config.LlmProperties;

class PromptTemplateStoreSeedTest {

	@TempDir
	Path tempDir;

	private PromptTemplateStore store;

	@BeforeEach
	void setUp() throws Exception {
		Path templatesDir = tempDir.resolve("prompt_templates");
		Files.createDirectories(templatesDir);
		Files.writeString(
				templatesDir.resolve("qwen2.5-3b_ctx8192_weights-0.1.0.md"),
				"weights {testCaseData}");
		Files.writeString(
				templatesDir.resolve("qwen2.5-3b_ctx8192_aisort-0.1.0.md"),
				"aisort {testCasesJson}");
		store = new PromptTemplateStore(new LlmProperties(
				"http://localhost:11434",
				"qwen2.5:3b",
				8192,
				null,
				templatesDir.toString(),
				tempDir.resolve("settings.json").toString()));
	}

	@Test
	void shouldSeedNewModelFromFallbackWhenPrimaryHasNoPrompts() {
		store.seedForModelWithFallback(
				"qwen3.5:2b",
				8192,
				"unknown:model",
				"qwen2.5:3b",
				"0.1.0",
				"0.1.0");

		assertThat(store.hasVersions("qwen3.5:2b", 8192, LlmPromptTemplateType.WEIGHTS)).isTrue();
		assertThat(store.hasVersions("qwen3.5:2b", 8192, LlmPromptTemplateType.AI_SORT)).isTrue();
		assertThat(store.read("qwen3.5:2b", 8192, LlmPromptTemplateType.WEIGHTS, "0.1.0"))
				.contains("{testCaseData}");
	}
}
