package com.qligent.triage.prioritization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.qligent.triage.config.TriageProperties;

@Service
public class PrioritizationArtifactService {

	private static final Logger log = LoggerFactory.getLogger(PrioritizationArtifactService.class);

	private static final Pattern FOLDER_PATTERN = Pattern.compile("^\\d+_(.+)-SR(\\d+)$");

	private final Path artifactsRoot;

	public PrioritizationArtifactService(TriageProperties properties) {
		this.artifactsRoot = Path.of(properties.llmArtifactsDir()).toAbsolutePath().normalize();
	}

	public Path artifactsRoot() {
		return artifactsRoot;
	}

	public int resolveNextSequence(String projectKey) {
		if (!Files.isDirectory(artifactsRoot)) {
			return 1;
		}
		try (Stream<Path> paths = Files.list(artifactsRoot)) {
			return paths
					.filter(Files::isDirectory)
					.map(path -> path.getFileName().toString())
					.map(FOLDER_PATTERN::matcher)
					.filter(Matcher::matches)
					.filter(matcher -> matcher.group(1).equals(projectKey))
					.mapToInt(matcher -> Integer.parseInt(matcher.group(2)))
					.max()
					.orElse(0) + 1;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to scan LLM artifact folders in " + artifactsRoot, exception);
		}
	}

	public String buildCycleKey(String projectKey, int sequence) {
		return projectKey + "-SR" + sequence;
	}

	public ArtifactFolder createArtifactFolder(String projectKey, int sequence) {
		long createdAt = System.currentTimeMillis();
		String cycleKey = buildCycleKey(projectKey, sequence);
		String folderName = createdAt + "_" + cycleKey;
		Path folder = artifactsRoot.resolve(folderName);
		try {
			Files.createDirectories(folder);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create LLM artifact folder: " + folder, exception);
		}
		log.info("Created LLM artifact folder: {}", folder);
		return new ArtifactFolder(folder, folderName, cycleKey, createdAt);
	}

	public Path writePromptFile(Path folder, String testCaseKey, String content) {
		return writeMarkdownFile(folder, testCaseKey, "prompt", content);
	}

	public Path writeResponseFile(Path folder, String testCaseKey, String content) {
		return writeMarkdownFile(folder, testCaseKey, "response", content);
	}

	private Path writeMarkdownFile(Path folder, String testCaseKey, String kind, String content) {
		long timestamp = System.currentTimeMillis();
		String fileName = timestamp + "_" + kind + "_" + testCaseKey + ".md";
		Path file = folder.resolve(fileName);
		try {
			Files.writeString(file, content);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to write LLM artifact file: " + file, exception);
		}
		log.info("Wrote LLM artifact file: {}", file);
		return file;
	}

	public record ArtifactFolder(
			Path path,
			String folderName,
			String cycleKey,
			long createdAtMillis
	) {
	}

	public void deleteArtifactFolder(Path folder) {
		if (folder == null) {
			return;
		}
		Path normalizedFolder = folder.toAbsolutePath().normalize();
		if (!normalizedFolder.startsWith(artifactsRoot)) {
			throw new IllegalArgumentException("Artifact folder is outside artifacts root: " + folder);
		}
		if (!Files.exists(normalizedFolder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(normalizedFolder)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException exception) {
					throw new IllegalStateException("Failed to delete artifact path: " + path, exception);
				}
			});
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to delete LLM artifact folder: " + normalizedFolder, exception);
		}
		log.info("Deleted LLM artifact folder: {}", normalizedFolder);
	}
}
