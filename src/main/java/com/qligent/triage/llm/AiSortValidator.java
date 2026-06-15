package com.qligent.triage.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AiSortValidator {

	private static final double SUM_TOLERANCE = 1e-3;

	public Map<String, Double> validateAndNormalize(List<String> expectedKeys, Map<String, Double> raw) {
		if (raw == null || raw.isEmpty()) {
			throw new IllegalArgumentException("AI sort coefficients must not be empty.");
		}
		Map<String, Double> normalized = new LinkedHashMap<>();
		for (String key : expectedKeys) {
			Double value = raw.get(key);
			if (value == null) {
				throw new IllegalArgumentException("Missing AI sort coefficient for test: " + key);
			}
			if (value < 0.0 || value > 1.0) {
				throw new IllegalArgumentException("AI sort coefficient out of range [0,1]: " + key + "=" + value);
			}
			normalized.put(key, value);
		}
		double sum = normalized.values().stream().mapToDouble(Double::doubleValue).sum();
		if (Math.abs(sum - 1.0) <= SUM_TOLERANCE) {
			return normalized;
		}
		if (sum <= 0.0) {
			throw new IllegalArgumentException("AI sort coefficients sum to zero; cannot normalize.");
		}
		Map<String, Double> scaled = new LinkedHashMap<>();
		for (String key : expectedKeys) {
			scaled.put(key, normalized.get(key) / sum);
		}
		return scaled;
	}
}
