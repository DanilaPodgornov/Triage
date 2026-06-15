package com.qligent.triage.llm;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class WeightValidator {

	private static final double SUM_TOLERANCE = 1e-3;

	public Map<String, Double> validateAndNormalize(Map<String, Double> raw) {
		if (raw == null || raw.isEmpty()) {
			throw new IllegalArgumentException("Attribute weights must not be empty.");
		}
		Map<String, Double> normalized = new LinkedHashMap<>();
		for (String key : AttributeWeightKeys.ALL) {
			Double value = raw.get(key);
			if (value == null) {
				value = 0.0;
			}
			if (value < 0.0 || value > 1.0) {
				throw new IllegalArgumentException("Attribute weight out of range [0,1]: " + key + "=" + value);
			}
			normalized.put(key, value);
		}
		double sum = AttributeWeightKeys.sum(normalized);
		if (Math.abs(sum - 1.0) <= SUM_TOLERANCE) {
			return normalized;
		}
		if (sum <= 0.0) {
			throw new IllegalArgumentException("Attribute weights sum to zero; cannot normalize.");
		}
		Map<String, Double> scaled = new LinkedHashMap<>();
		for (String key : AttributeWeightKeys.ALL) {
			scaled.put(key, normalized.get(key) / sum);
		}
		return scaled;
	}
}
