package com.qligent.triage.prioritization;

import java.util.concurrent.atomic.AtomicBoolean;

public class PrioritizationCancellation {

	private final AtomicBoolean cancelled = new AtomicBoolean();

	public boolean isCancelled() {
		return cancelled.get();
	}

	public void cancel() {
		cancelled.set(true);
	}
}
