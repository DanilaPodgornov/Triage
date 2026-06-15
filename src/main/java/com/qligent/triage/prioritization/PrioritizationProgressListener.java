package com.qligent.triage.prioritization;

public interface PrioritizationProgressListener {

	void onArtifactFolderCreated(PrioritizationArtifactService.ArtifactFolder folder);

	void onTestStarted(int currentTest, int totalTests, String testCaseKey);

	void onTestResponseCompleted(int completedTests, int totalTests);

	void onAiSortStarted();
}
