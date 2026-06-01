package com.qligent.triage.suggested;

public enum SuggestedTestCycleStatus {

	WAITING("Waiting"),
	SENT("Sent");

	private final String label;

	SuggestedTestCycleStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public static SuggestedTestCycleStatus fromLabel(String label) {
		for (var status : values()) {
			if (status.label.equalsIgnoreCase(label)) {
				return status;
			}
		}
		return WAITING;
	}

}
