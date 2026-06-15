package com.qligent.triage.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.qligent.triage.config.LlmProperties;

@Service
public class PromptTemplateStore {

	private static final String DEFAULT_VERSION = "0.1.0";

	private final Path templatesRoot;

	public PromptTemplateStore(LlmProperties properties) {
		this.templatesRoot = Path.of(properties.promptTemplatesDir()).toAbsolutePath().normalize();
	}

	public List<String> listVersions(String model, int numCtx, LlmPromptTemplateType type) {
		String prefix = filePrefix(model, numCtx, type);
		if (!Files.isDirectory(templatesRoot)) {
			return List.of();
		}
		Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "-(.+)\\.md$");
		List<String> versions = new ArrayList<>();
		try (var stream = Files.list(templatesRoot)) {
			stream.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.forEach(name -> {
						Matcher matcher = pattern.matcher(name);
						if (matcher.matches()) {
							versions.add(matcher.group(1));
						}
					});
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to list prompt template versions in " + templatesRoot, exception);
		}
		versions.sort(Comparator.comparing(PromptTemplateStore::parseVersion));
		return versions;
	}

	public String read(String model, int numCtx, LlmPromptTemplateType type, String version) {
		Path path = resolvePath(model, numCtx, type, version);
		try {
			if (!Files.isRegularFile(path)) {
				throw new IllegalStateException("Prompt template not found: " + path);
			}
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to read prompt template from " + path, exception);
		}
	}

	public String readActive(String model, int numCtx, LlmPromptTemplateType type, String activeVersion) {
		String version = resolveActiveVersion(model, numCtx, type, activeVersion);
		return read(model, numCtx, type, version).trim();
	}

	public String resolveActiveVersion(String model, int numCtx, LlmPromptTemplateType type, String activeVersion) {
		List<String> versions = listVersions(model, numCtx, type);
		if (versions.isEmpty()) {
			return activeVersion == null || activeVersion.isBlank() ? DEFAULT_VERSION : activeVersion;
		}
		if (activeVersion == null || activeVersion.isBlank() || !versions.contains(activeVersion)) {
			return versions.getLast();
		}
		return activeVersion;
	}

	public String latestVersion(String model, int numCtx, LlmPromptTemplateType type) {
		List<String> versions = listVersions(model, numCtx, type);
		return versions.isEmpty() ? DEFAULT_VERSION : versions.getLast();
	}

	public String saveNewVersion(String model, int numCtx, LlmPromptTemplateType type, String content) {
		List<String> versions = listVersions(model, numCtx, type);
		String newVersion = versions.isEmpty()
				? DEFAULT_VERSION
				: bumpPatchVersion(versions.getLast());
		Path path = resolvePath(model, numCtx, type, newVersion);
		try {
			Files.createDirectories(templatesRoot);
			Files.writeString(path, content, StandardCharsets.UTF_8);
			return newVersion;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to save prompt template to " + path, exception);
		}
	}

	public void seedForModel(String newModel, int numCtx, String sourceModel, String weightsVersion, String aisortVersion) {
		for (LlmPromptTemplateType type : LlmPromptTemplateType.values()) {
			if (!listVersions(newModel, numCtx, type).isEmpty()) {
				continue;
			}
			String sourceVersion = type == LlmPromptTemplateType.WEIGHTS ? weightsVersion : aisortVersion;
			if (sourceModel == null || sourceModel.isBlank()) {
				continue;
			}
			List<String> sourceVersions = listVersions(sourceModel, numCtx, type);
			if (sourceVersions.isEmpty()) {
				continue;
			}
			String versionToCopy = sourceVersion != null && !sourceVersion.isBlank()
					? sourceVersion
					: sourceVersions.getLast();
			if (!sourceVersions.contains(versionToCopy)) {
				versionToCopy = sourceVersions.getLast();
			}
			String content = read(sourceModel, numCtx, type, versionToCopy);
			Path target = resolvePath(newModel, numCtx, type, DEFAULT_VERSION);
			try {
				Files.createDirectories(templatesRoot);
				Files.writeString(target, content, StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new IllegalStateException("Failed to seed prompt template at " + target, exception);
			}
		}
	}

	public void seedForModelWithFallback(
			String newModel,
			int numCtx,
			String primarySource,
			String fallbackSource,
			String weightsVersion,
			String aisortVersion) {
		seedForModel(newModel, numCtx, primarySource, weightsVersion, aisortVersion);
		if (fallbackSource == null || fallbackSource.isBlank()
				|| fallbackSource.equalsIgnoreCase(primarySource)) {
			return;
		}
		seedForModel(newModel, numCtx, fallbackSource, weightsVersion, aisortVersion);
	}

	public boolean hasVersions(String model, int numCtx, LlmPromptTemplateType type) {
		return !listVersions(model, numCtx, type).isEmpty();
	}

	public String templateFileName(String model, int numCtx, LlmPromptTemplateType type, String version) {
		return filePrefix(model, numCtx, type) + "-" + version + ".md";
	}

	public Path resolvePath(String model, int numCtx, LlmPromptTemplateType type, String version) {
		return templatesRoot.resolve(templateFileName(model, numCtx, type, version));
	}

	private static String filePrefix(String model, int numCtx, LlmPromptTemplateType type) {
		return sanitizeModel(model) + "_ctx" + numCtx + "_" + type.fileSuffix();
	}

	static String sanitizeModel(String model) {
		return model.replace(':', '-').replace('/', '-');
	}

	public static String llmKey(String model, int numCtx) {
		return sanitizeModel(model) + "_ctx" + numCtx;
	}

	static String bumpPatchVersion(String version) {
		String[] parts = version.split("\\.");
		if (parts.length != 3) {
			return DEFAULT_VERSION;
		}
		try {
			int patch = Integer.parseInt(parts[2]) + 1;
			return parts[0] + "." + parts[1] + "." + patch;
		} catch (NumberFormatException exception) {
			return DEFAULT_VERSION;
		}
	}

	private static long parseVersion(String version) {
		String[] parts = version.split("\\.");
		if (parts.length != 3) {
			return 0L;
		}
		try {
			long major = Long.parseLong(parts[0]);
			long minor = Long.parseLong(parts[1]);
			long patch = Long.parseLong(parts[2]);
			return major * 1_000_000L + minor * 1_000L + patch;
		} catch (NumberFormatException exception) {
			return 0L;
		}
	}
}
