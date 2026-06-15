package com.qligent.triage.prioritization;

public class PrioritizationCancelledException extends RuntimeException {

	public PrioritizationCancelledException() {
		super("Prioritization was cancelled.");
	}
}
