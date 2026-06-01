package com.qligent.triage.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColorContrastTest {

	@Test
	void shouldReturnWhiteTextOnDarkBackground() {
		assertThat(ColorContrast.textColorForBackground("#ff0000")).isEqualTo("#ffffff");
	}

	@Test
	void shouldReturnDarkTextOnLightBackground() {
		assertThat(ColorContrast.textColorForBackground("#ffff00")).isEqualTo("#111827");
	}

}
