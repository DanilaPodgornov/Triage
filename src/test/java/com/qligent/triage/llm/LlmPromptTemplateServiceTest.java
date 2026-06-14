package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.qligent.triage.config.LlmProperties;

class LlmPromptTemplateServiceTest {

	@Test
	void shouldLoadAndFormatWeightsTemplate() {
		var service = new LlmPromptTemplateService(
				new LlmProperties("http://localhost:11434", "qwen2.5:3b", 8192, null),
				new DefaultResourceLoader());

		assertThat(service.getTemplate()).contains("{testCaseData}");

		String formatted = service.formatWeightsPrompt("## Test case: SQM-T1");
		assertThat(formatted).contains("SQM-T1");
		assertThat(formatted).doesNotContain("{testCaseData}");
	}

	@Test
	void shouldResolveTemplateResourceNameFromModelAndContext() {
		var service = new LlmPromptTemplateService(
				new LlmProperties("http://localhost:11434", "qwen2.5:3b", 8192, null),
				new DefaultResourceLoader());

		assertThat(service.templateResourceName(LlmPromptTemplateType.WEIGHTS))
				.isEqualTo("qwen2.5-3b_ctx8192_weights.md");
		assertThat(service.templateResourceName(LlmPromptTemplateType.AI_SORT))
				.isEqualTo("qwen2.5-3b_ctx8192_aisort.md");
	}
}
