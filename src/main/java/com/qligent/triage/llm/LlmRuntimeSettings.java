package com.qligent.triage.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Component;

import com.qligent.triage.config.LlmProperties;

import tools.jackson.databind.ObjectMapper;

@Component
public class LlmRuntimeSettings {

	private final Path settingsFile;
	private final LlmProperties properties;
	private final ObjectMapper objectMapper;
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private String activeModel;
	private final Map<String, String> activeVersions = new LinkedHashMap<>();
	private int numCtx;

	public LlmRuntimeSettings(LlmProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.settingsFile = Path.of(properties.settingsFile()).toAbsolutePath().normalize();
		this.numCtx = properties.numCtx();
		loadFromDisk();
	}

	public String activeModel() {
		lock.readLock().lock();
		try {
			return activeModel;
		} finally {
			lock.readLock().unlock();
		}
	}

	public String activeVersion(LlmPromptTemplateType type) {
		lock.readLock().lock();
		try {
			return activeVersions.get(type.fileSuffix());
		} finally {
			lock.readLock().unlock();
		}
	}

	public int numCtx() {
		lock.readLock().lock();
		try {
			return numCtx;
		} finally {
			lock.readLock().unlock();
		}
	}

	public void setActiveModel(String model) {
		lock.writeLock().lock();
		try {
			this.activeModel = (model == null || model.isBlank()) ? null : model;
			persist();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setActiveVersion(LlmPromptTemplateType type, String version) {
		lock.writeLock().lock();
		try {
			activeVersions.put(type.fileSuffix(), version);
			persist();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setNumCtx(int numCtx) {
		lock.writeLock().lock();
		try {
			this.numCtx = numCtx > 0 ? numCtx : properties.numCtx();
			persist();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public String resolveModel() {
		String model = activeModel();
		if (model == null || model.isBlank()) {
			return properties.model();
		}
		return model;
	}

	private void loadFromDisk() {
		if (!Files.isRegularFile(settingsFile)) {
			return;
		}
		try {
			String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
			SettingsSnapshot snapshot = objectMapper.readValue(json, SettingsSnapshot.class);
			if (snapshot.activeModel() != null) {
				this.activeModel = snapshot.activeModel();
			}
			if (snapshot.activeVersions() != null) {
				activeVersions.clear();
				activeVersions.putAll(snapshot.activeVersions());
			}
			if (snapshot.numCtx() != null && snapshot.numCtx() > 0) {
				this.numCtx = snapshot.numCtx();
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to load LLM runtime settings from " + settingsFile, exception);
		}
	}

	private void persist() {
		try {
			Files.createDirectories(settingsFile.getParent());
			SettingsSnapshot snapshot = new SettingsSnapshot(
					activeModel,
					Map.copyOf(activeVersions),
					numCtx);
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), snapshot);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to persist LLM runtime settings to " + settingsFile, exception);
		}
	}

	record SettingsSnapshot(
			String activeModel,
			Map<String, String> activeVersions,
			Integer numCtx) {
	}
}
