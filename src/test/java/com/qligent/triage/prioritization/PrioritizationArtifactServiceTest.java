package com.qligent.triage.prioritization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.qligent.triage.config.TriageProperties;

class PrioritizationArtifactServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldResolveNextSequenceFromExistingFolders() throws Exception {
		Path llmDir = tempDir.resolve("llm");
		Files.createDirectories(llmDir.resolve("1000_SQM-SR1"));
		Files.createDirectories(llmDir.resolve("2000_SQM-SR3"));

		var service = new PrioritizationArtifactService(
				new TriageProperties(llmDir.toString(), tempDir.resolve("cycles").toString(), null, null));

		assertThat(service.resolveNextSequence("SQM")).isEqualTo(4);
		assertThat(service.buildCycleKey("SQM", 4)).isEqualTo("SQM-SR4");
	}

	@Test
	void shouldCreateArtifactFolderWithTimestampPrefix() throws Exception {
		Path llmDir = tempDir.resolve("llm");
		var service = new PrioritizationArtifactService(
				new TriageProperties(llmDir.toString(), tempDir.resolve("cycles").toString(), null, null));

		var folder = service.createArtifactFolder("SQM", 2);

		assertThat(folder.cycleKey()).isEqualTo("SQM-SR2");
		assertThat(folder.folderName()).startsWith(String.valueOf(folder.createdAtMillis()));
		assertThat(folder.folderName()).endsWith("_SQM-SR2");
		assertThat(Files.isDirectory(folder.path())).isTrue();
	}
}
