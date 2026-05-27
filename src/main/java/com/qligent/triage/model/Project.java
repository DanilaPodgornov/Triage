package com.qligent.triage.model;

import java.util.Arrays;

public enum Project {

	B2C("B2C"),
	B2B("B2B");

	private final String label;

	Project(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public static Project fromLabel(String label) {
		return Arrays.stream(values())
				.filter(project -> project.label.equalsIgnoreCase(label))
				.findFirst()
				.orElse(B2C);
	}

}
