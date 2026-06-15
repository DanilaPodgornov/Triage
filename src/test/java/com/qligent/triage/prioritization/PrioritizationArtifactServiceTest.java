package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.qligent.triage.config.TriageProperties;

class PrioritizationArtifactServiceTest {

	@TempDir
	Path tempDir;

	private PrioritizationArtifactService service;

	@BeforeEach
	void setUp() {
		service = new PrioritizationArtifactService(
				new TriageProperties(tempDir.resolve("llm").toString(), null, 15, null, null));
	}

	@Test
	void shouldDeleteArtifactFolderRecursively() throws Exception {
		Path folder = tempDir.resolve("llm").resolve("1781542979267_SQM-SR39");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("1781542980783_prompt_SQM-T1267.md"), "prompt");

		service.deleteArtifactFolder(folder);

		assertThat(Files.exists(folder)).isFalse();
	}

	@Test
	void shouldRejectArtifactFolderOutsideRoot() {
		assertThatThrownBy(() -> service.deleteArtifactFolder(tempDir.resolve("outside")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("outside artifacts root");
	}
}
