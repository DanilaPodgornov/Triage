package com.qligent.triage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.qligent.triage.config.ZephyrProperties;
import com.qligent.triage.model.Project;
import com.qligent.triage.zephyr.ZephyrScaleClient;
import com.qligent.triage.zephyr.dto.FolderDto;
import com.qligent.triage.zephyr.dto.PriorityDto;
import com.qligent.triage.zephyr.dto.StatusDto;
import com.qligent.triage.zephyr.dto.TestCaseDto;
import com.qligent.triage.zephyr.dto.ZephyrPage;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

	@Mock
	private ZephyrScaleClient zephyrScaleClient;

	private TestCaseService testCaseService;

	@BeforeEach
	void setUp() {
		var properties = new ZephyrProperties("https://example.com", "token", 10, Map.of("B2C", "SQM", "B2B", "SB2B"));
		testCaseService = new TestCaseService(zephyrScaleClient, properties);
	}

	@Test
	void shouldFilterByPriorityLocallyWhenPrioritySelected() {
		when(zephyrScaleClient.resolveProjectKey(Project.B2C)).thenReturn("SQM");
		when(zephyrScaleClient.getPriorities("SQM")).thenReturn(prioritiesPage());
		when(zephyrScaleClient.getFolders("SQM", 0, 100)).thenReturn(emptyFoldersPage());
		when(zephyrScaleClient.getStatuses("SQM", 0, 100)).thenReturn(emptyStatusesPage());
		when(zephyrScaleClient.getTestCases(eq("SQM"), eq(0), eq(1000), eq(Optional.empty()))).thenReturn(testCasesPage());

		var query = new TestCaseListQuery(Optional.of(2L), Optional.empty(), Optional.empty(), TestCaseSort.KEY, SortDirection.ASC, 0, 10);
		var result = testCaseService.getTestCasesPage(Project.B2C, query);

		assertThat(result.totalItems()).isEqualTo(2);
		assertThat(result.rangeFrom()).isEqualTo(1);
		assertThat(result.rangeTo()).isEqualTo(2);
		assertThat(result.allMatchingTestCaseKeys()).containsExactlyInAnyOrder("SQM-T1", "SQM-T3");
		assertThat(result.testCases()).extracting("key").containsExactly("SQM-T1", "SQM-T3");
		verify(zephyrScaleClient).getTestCases("SQM", 0, 1000, Optional.empty());
	}

	@Test
	void shouldFilterByFolderAndSortByStatusName() {
		when(zephyrScaleClient.resolveProjectKey(Project.B2C)).thenReturn("SQM");
		when(zephyrScaleClient.getPriorities("SQM")).thenReturn(prioritiesPage());
		when(zephyrScaleClient.getFolders("SQM", 0, 100)).thenReturn(new ZephyrPage<>(null, 0, 100, 1, true,
				List.of(new FolderDto(10L, null, "Folder A", "TEST_CASE"))));
		when(zephyrScaleClient.getStatuses("SQM", 0, 100)).thenReturn(new ZephyrPage<>(null, 0, 100, 2, true,
				List.of(new StatusDto(20L, "Approved"), new StatusDto(21L, "Draft"))));
		when(zephyrScaleClient.getTestCases(eq("SQM"), eq(0), eq(1000), eq(Optional.empty()))).thenReturn(
				new ZephyrPage<>(null, 0, 1000, 3, true, List.of(
						new TestCaseDto(1L, "SQM-T1", "Case 1",
								new TestCaseDto.PriorityRef(1L),
								new TestCaseDto.StatusRef(21L),
								new TestCaseDto.FolderRef(10L)),
						new TestCaseDto(2L, "SQM-T2", "Case 2",
								new TestCaseDto.PriorityRef(2L),
								new TestCaseDto.StatusRef(20L),
								new TestCaseDto.FolderRef(10L)),
						new TestCaseDto(3L, "SQM-T3", "Case 3",
								new TestCaseDto.PriorityRef(2L),
								new TestCaseDto.StatusRef(20L),
								new TestCaseDto.FolderRef(99L)))));

		var query = new TestCaseListQuery(
				Optional.empty(),
				Optional.of(10L),
				Optional.empty(),
				TestCaseSort.STATUS,
				SortDirection.ASC,
				0,
				10);
		var result = testCaseService.getTestCasesPage(Project.B2C, query);

		assertThat(result.totalItems()).isEqualTo(2);
		assertThat(result.allMatchingTestCaseKeys()).containsExactly("SQM-T2", "SQM-T1");
		assertThat(result.testCases()).extracting("key").containsExactly("SQM-T2", "SQM-T1");
		assertThat(result.testCases()).extracting("folder").containsOnly("Folder A");
	}

	private ZephyrPage<PriorityDto> prioritiesPage() {
		return new ZephyrPage<>(null, 0, 100, 2, true,
				List.of(new PriorityDto(1L, "Normal", "#808080"), new PriorityDto(2L, "High", "#ff0000")));
	}

	private ZephyrPage<FolderDto> emptyFoldersPage() {
		return new ZephyrPage<>(null, 0, 100, 0, true, List.of());
	}

	private ZephyrPage<StatusDto> emptyStatusesPage() {
		return new ZephyrPage<>(null, 0, 100, 0, true, List.of());
	}

	private ZephyrPage<TestCaseDto> testCasesPage() {
		return new ZephyrPage<>(null, 0, 1000, 3, true, List.of(
				new TestCaseDto(1L, "SQM-T1", "Case 1",
						new TestCaseDto.PriorityRef(2L), null, null),
				new TestCaseDto(2L, "SQM-T2", "Case 2",
						new TestCaseDto.PriorityRef(1L), null, null),
				new TestCaseDto(3L, "SQM-T3", "Case 3",
						new TestCaseDto.PriorityRef(2L), null, null)));
	}

}
