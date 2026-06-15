package com.qligent.triage.suggested;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZephyrCycleNamingTest {

	@Test
	void shouldPrefixCycleNameWithAiTag() {
		assertThat(ZephyrCycleNaming.formatCycleName("Regression")).isEqualTo("[AI] Regression");
	}

	@Test
	void shouldNotDuplicateAiPrefix() {
		assertThat(ZephyrCycleNaming.formatCycleName("[AI] Regression")).isEqualTo("[AI] Regression");
		assertThat(ZephyrCycleNaming.formatCycleName("[ai] Regression")).isEqualTo("[ai] Regression");
	}
}
