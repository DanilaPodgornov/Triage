package com.qligent.triage.llm;

import java.util.List;
import java.util.Map;

public final class AttributeWeightKeys {

	public static final String PRIORITY = "priority";
	public static final String STATUS = "status";
	public static final String LABELS = "labels";
	public static final String EXECUTIONS_COUNT = "executionsCount";
	public static final String FAILURES_COUNT = "failuresCount";
	public static final String FAILED_EXECUTIONS_RATIO = "failedExecutionsRatio";
	public static final String LAST_EXECUTION_STATUS = "lastExecutionStatus";
	public static final String EXECUTION_TIME = "executionTime";

	public static final List<String> ALL = List.of(
			PRIORITY,
			STATUS,
			LABELS,
			EXECUTIONS_COUNT,
			FAILURES_COUNT,
			FAILED_EXECUTIONS_RATIO,
			LAST_EXECUTION_STATUS,
			EXECUTION_TIME);

	private AttributeWeightKeys() {
	}

	public static double sum(Map<String, Double> weights) {
		return ALL.stream().mapToDouble(key -> weights.getOrDefault(key, 0.0)).sum();
	}
}
