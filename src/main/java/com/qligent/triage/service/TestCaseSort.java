package com.qligent.triage.service;

public enum TestCaseSort {

	KEY,
	PRIORITY,
	FOLDER,
	STATUS;

	public static TestCaseSort fromParam(String value) {
		if (value == null || value.isBlank()) {
			return KEY;
		}
		try {
			return TestCaseSort.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException exception) {
			return KEY;
		}
	}

}
