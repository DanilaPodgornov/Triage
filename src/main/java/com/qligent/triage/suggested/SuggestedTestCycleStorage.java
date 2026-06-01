package com.qligent.triage.suggested;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;

@Component
public class SuggestedTestCycleStorage {

	private static final Pattern SR_KEY_PATTERN = Pattern.compile("^(.+)-SR(\\d+)$");

	private final Path filePath;
	private final ObjectMapper objectMapper;

	public SuggestedTestCycleStorage(TriageProperties properties, ObjectMapper objectMapper) {
		this.filePath = Path.of(properties.suggestedCyclesFile()).toAbsolutePath().normalize();
		this.objectMapper = objectMapper;
	}

	public SuggestedTestCyclesFile load() {
		if (!Files.exists(filePath)) {
			return SuggestedTestCyclesFile.empty();
		}
		try {
			return objectMapper.readValue(filePath.toFile(), SuggestedTestCyclesFile.class);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to read suggested test cycles file: " + filePath, exception);
		}
	}

	public void save(SuggestedTestCyclesFile file) {
		try {
			if (filePath.getParent() != null) {
				Files.createDirectories(filePath.getParent());
			}
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), file);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to write suggested test cycles file: " + filePath, exception);
		}
	}

	public void add(SuggestedTestCycle cycle) {
		var file = load();
		var cycles = new ArrayList<>(file.suggestedTestCycles() == null ? List.of() : file.suggestedTestCycles());
		cycles.add(cycle);
		save(new SuggestedTestCyclesFile(cycles));
	}

	public Optional<SuggestedTestCycle> findByKey(String key) {
		var cycles = load().suggestedTestCycles();
		if (cycles == null) {
			return Optional.empty();
		}
		return cycles.stream()
				.filter(cycle -> cycle.suggestedTestCycleKey().equals(key))
				.findFirst();
	}

	public void update(SuggestedTestCycle updated) {
		var file = load();
		var cycles = new ArrayList<>(file.suggestedTestCycles() == null ? List.of() : file.suggestedTestCycles());
		for (int i = 0; i < cycles.size(); i++) {
			if (cycles.get(i).suggestedTestCycleKey().equals(updated.suggestedTestCycleKey())) {
				cycles.set(i, updated);
				save(new SuggestedTestCyclesFile(cycles));
				return;
			}
		}
		throw new IllegalArgumentException("Suggested test cycle not found: " + updated.suggestedTestCycleKey());
	}

	public List<SuggestedTestCycle> findByProjectPrefix(String projectKeyPrefix) {
		var cycles = load().suggestedTestCycles();
		if (cycles == null) {
			return List.of();
		}
		return cycles.stream()
				.filter(cycle -> belongsToProject(cycle, projectKeyPrefix))
				.sorted(Comparator.comparing(SuggestedTestCycle::suggestedTestCycleKey))
				.toList();
	}

	private boolean belongsToProject(SuggestedTestCycle cycle, String projectKeyPrefix) {
		String prefix = projectKeyPrefix + "-";
		if (cycle.suggestedTestCycleKey() != null && cycle.suggestedTestCycleKey().startsWith(prefix)) {
			return true;
		}
		if (cycle.testCases() == null) {
			return false;
		}
		return cycle.testCases().stream()
				.anyMatch(testCaseKey -> testCaseKey != null && testCaseKey.startsWith(prefix));
	}

	public String nextSuggestedCycleKey(String projectKeyPrefix) {
		var cycles = load().suggestedTestCycles();
		if (cycles == null) {
			return projectKeyPrefix + "-SR1";
		}
		int max = cycles.stream()
				.map(SuggestedTestCycle::suggestedTestCycleKey)
				.map(SR_KEY_PATTERN::matcher)
				.filter(Matcher::matches)
				.filter(matcher -> matcher.group(1).equals(projectKeyPrefix))
				.mapToInt(matcher -> Integer.parseInt(matcher.group(2)))
				.max()
				.orElse(0);
		return projectKeyPrefix + "-SR" + (max + 1);
	}

}
