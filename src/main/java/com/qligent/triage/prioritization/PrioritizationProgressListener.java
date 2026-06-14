package com.qligent.triage.prioritization;

public interface PrioritizationProgressListener {

	void onTestResponseCompleted(int completedTests, int totalTests);

	void onAiSortStarted();
}
