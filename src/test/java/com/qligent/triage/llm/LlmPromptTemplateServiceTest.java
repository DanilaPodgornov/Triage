package com.qligent.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.qligent.triage.config.LlmProperties;

class LlmPromptTemplateServiceTest {

	@Test
	void shouldLoadAndFormatDefaultPromptTemplate() {
		var service = new LlmPromptTemplateService(
				new LlmProperties("http://localhost:11434", "test-model", "classpath:llm/default-prioritization-prompt.txt"),
				new DefaultResourceLoader());

		assertThat(service.getTemplate()).contains("{inputKeys}").contains("{testCasesJson}");

		String formatted = service.format("SQM-T1, SQM-T2", "[{\"key\":\"SQM-T1\"}]");
		assertThat(formatted).contains("SQM-T1, SQM-T2");
		assertThat(formatted).contains("[{\"key\":\"SQM-T1\"}]");
		assertThat(formatted).doesNotContain("{inputKeys}");
	}

}
