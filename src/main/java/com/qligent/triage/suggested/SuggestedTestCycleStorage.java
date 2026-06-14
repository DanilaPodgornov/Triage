package com.qligent.triage.suggested;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;

@Component
public class SuggestedTestCycleStorage {

	private final Path cyclesDir;
	private final ObjectMapper objectMapper;

	public SuggestedTestCycleStorage(TriageProperties properties, ObjectMapper objectMapper) {
		this.cyclesDir = Path.of(properties.suggestedCyclesDir()).toAbsolutePath().normalize();
		this.objectMapper = objectMapper;
	}

	public Path cyclesDirectory() {
		return cyclesDir;
	}

	public void save(SuggestedTestCycle cycle) {
		try {
			Files.createDirectories(cyclesDir);
			Path file = cycleFile(cycle.suggestedTestCycleKey());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), cycle);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to write suggested test cycle: " + cycle.suggestedTestCycleKey(), exception);
		}
	}

	public Optional<SuggestedTestCycle> findByKey(String cycleKey) {
		Path file = cycleFile(cycleKey);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(file.toFile(), SuggestedTestCycle.class));
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to read suggested test cycle: " + cycleKey, exception);
		}
	}

	public SuggestedTestCycle getByKey(String cycleKey) {
		return findByKey(cycleKey)
				.orElseThrow(() -> new IllegalArgumentException("Suggested test cycle not found: " + cycleKey));
	}

	public List<SuggestedTestCycle> findByProjectPrefix(String projectKeyPrefix) {
		if (!Files.isDirectory(cyclesDir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(cyclesDir)) {
			return files
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.map(this::readCycleSafely)
					.flatMap(Optional::stream)
					.filter(cycle -> cycle.projectKey() != null && cycle.projectKey().equals(projectKeyPrefix))
					.sorted(Comparator.comparing(SuggestedTestCycle::createdAt).reversed())
					.toList();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to list suggested test cycles in " + cyclesDir, exception);
		}
	}

	public void update(SuggestedTestCycle updated) {
		if (findByKey(updated.suggestedTestCycleKey()).isEmpty()) {
			throw new IllegalArgumentException("Suggested test cycle not found: " + updated.suggestedTestCycleKey());
		}
		save(updated);
	}

	private Optional<SuggestedTestCycle> readCycleSafely(Path file) {
		try {
			return Optional.of(objectMapper.readValue(file.toFile(), SuggestedTestCycle.class));
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to read suggested test cycle file: " + file, exception);
		}
	}

	private Path cycleFile(String cycleKey) {
		return cyclesDir.resolve(cycleKey + ".json");
	}
}
