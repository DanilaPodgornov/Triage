package com.qligent.triage.suggested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;
import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.CreateTestCycleRequest;
import com.qligent.triage.zephyr.dto.CreateTestExecutionRequest;
import com.qligent.triage.zephyr.dto.TestCycleDto;

@ExtendWith(MockitoExtension.class)
class SuggestedTestCycleServiceTest {

	@TempDir
	Path tempDir;

	@Mock
	private ZephyrScaleClient zephyrScaleClient;

	private SuggestedTestCycleStorage storage;
	private SuggestedTestCycleService service;

	@BeforeEach
	void setUp() {
		storage = new SuggestedTestCycleStorage(
				new TriageProperties(
						tempDir.resolve("llm").toString(),
						tempDir.resolve("suggested_test_cycles").toString(),
						15,
						null,
						null),
				new ObjectMapper());
		service = new SuggestedTestCycleService(storage, zephyrScaleClient);
	}

	@Test
	void shouldSendSuggestedCycleToZephyrWithAiPrefixAndNotExecutedStatus() {
		storage.save(waitingCycle());
		when(zephyrScaleClient.createTestCycle(any())).thenReturn(new TestCycleDto(42L, "SQM-R99"));

		String zephyrKey = service.sendToZephyr("SQM-SR1");

		assertThat(zephyrKey).isEqualTo("SQM-R99");

		ArgumentCaptor<CreateTestCycleRequest> cycleCaptor = ArgumentCaptor.forClass(CreateTestCycleRequest.class);
		verify(zephyrScaleClient).createTestCycle(cycleCaptor.capture());
		assertThat(cycleCaptor.getValue().projectKey()).isEqualTo("SQM");
		assertThat(cycleCaptor.getValue().name()).isEqualTo("[AI] Regression");

		ArgumentCaptor<CreateTestExecutionRequest> executionCaptor = ArgumentCaptor.forClass(CreateTestExecutionRequest.class);
		verify(zephyrScaleClient, org.mockito.Mockito.times(2)).createTestExecution(executionCaptor.capture());
		assertThat(executionCaptor.getAllValues())
				.extracting(CreateTestExecutionRequest::testCaseKey)
				.containsExactly("SQM-T2", "SQM-T1");
		assertThat(executionCaptor.getAllValues())
				.allMatch(request -> "SQM".equals(request.projectKey())
						&& "SQM-R99".equals(request.testCycleKey())
						&& "Not Executed".equals(request.statusName()));

		SuggestedTestCycle loaded = storage.findByKey("SQM-SR1").orElseThrow();
		assertThat(loaded.suggestedTestCycleStatus()).isEqualTo("Sent");
		assertThat(loaded.zephyrTestCycleKey()).isEqualTo("SQM-R99");
	}

	@Test
	void shouldAddTestsInSortOrderWhenSendingToZephyr() {
		storage.save(SuggestedTestCycle.create(
				"SQM-SR2",
				"Ordered cycle",
				"SQM",
				1L,
				"folder",
				List.of(
						entry("SQM-T1", 0.2),
						entry("SQM-T2", 0.8))));
		storage.update(storage.findByKey("SQM-SR2").orElseThrow().withSortState("aiSort", "DESC"));
		when(zephyrScaleClient.createTestCycle(any())).thenReturn(new TestCycleDto(1L, "SQM-R1"));

		service.sendToZephyr("SQM-SR2");

		InOrder inOrder = inOrder(zephyrScaleClient);
		inOrder.verify(zephyrScaleClient).createTestCycle(any());
		inOrder.verify(zephyrScaleClient).createTestExecution(new CreateTestExecutionRequest(
				"SQM", "SQM-T2", "SQM-R1", "Not Executed"));
		inOrder.verify(zephyrScaleClient).createTestExecution(new CreateTestExecutionRequest(
				"SQM", "SQM-T1", "SQM-R1", "Not Executed"));
	}

	@Test
	void shouldRejectSendingAlreadySentCycle() {
		storage.save(SuggestedTestCycle.create(
				"SQM-SR3",
				"Sent cycle",
				"SQM",
				1L,
				"folder",
				List.of(entry("SQM-T1", 1.0)))
				.withZephyrTestCycleKey("SQM-R1")
				.withStatus(SuggestedTestCycleStatus.SENT));

		assertThatThrownBy(() -> service.sendToZephyr("SQM-SR3"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already sent");

		verify(zephyrScaleClient, never()).createTestCycle(any());
	}

	@Test
	void shouldPaginateCyclesForProject() {
		when(zephyrScaleClient.resolveProjectKey(com.qligent.triage.model.Project.B2C)).thenReturn("SQM");
		for (int index = 1; index <= 20; index++) {
			storage.save(SuggestedTestCycle.create(
					"SQM-SR" + index,
					"Cycle " + index,
					"SQM",
					index,
					"folder-" + index,
					List.of(entry("SQM-T" + index, 1.0))));
		}

		var page = service.listPageForProject(com.qligent.triage.model.Project.B2C, 1, 15);

		assertThat(page.cycles()).hasSize(5);
		assertThat(page.currentPage()).isEqualTo(1);
		assertThat(page.pageSize()).isEqualTo(15);
		assertThat(page.rangeFrom()).isEqualTo(16);
		assertThat(page.rangeTo()).isEqualTo(20);
		assertThat(page.totalItems()).isEqualTo(20);
		assertThat(page.totalPages()).isEqualTo(2);
		assertThat(page.lastPage()).isTrue();
	}

	@Test
	void shouldSaveCycleNameWithSortState() {
		storage.save(waitingCycle());

		service.saveSortState("SQM-SR1", "key", "ASC", "Renamed cycle");

		SuggestedTestCycle loaded = storage.findByKey("SQM-SR1").orElseThrow();
		assertThat(loaded.suggestedTestCycleName()).isEqualTo("Renamed cycle");
		assertThat(loaded.sortField()).isEqualTo("key");
		assertThat(loaded.sortDirection()).isEqualTo("ASC");
	}

	private static SuggestedTestCycle waitingCycle() {
		return SuggestedTestCycle.create(
				"SQM-SR1",
				"Regression",
				"SQM",
				1L,
				"folder",
				List.of(
						entry("SQM-T1", 0.4),
						entry("SQM-T2", 0.6)));
	}

	private static SuggestedTestCycle.SuggestedTestCaseEntry entry(String key, double aiSort) {
		return new SuggestedTestCycle.SuggestedTestCaseEntry(
				key,
				"Test " + key,
				Map.of("priority", 1.0),
				new SuggestedTestCycle.TestMetrics(0, 0, 0, 0.0, "-", 0L),
				aiSort);
	}
}
