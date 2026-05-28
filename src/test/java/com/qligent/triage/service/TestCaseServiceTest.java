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
import com.qligent.triage.zephyr.dto.PriorityDto;
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
		when(zephyrScaleClient.getPriorities("SQM")).thenReturn(new ZephyrPage<>(
				null, 0, 100, 2, true,
				List.of(new PriorityDto(1L, "Normal"), new PriorityDto(2L, "High"))
		));

		when(zephyrScaleClient.getTestCases(eq("SQM"), eq(0), eq(1000), eq(Optional.empty()))).thenReturn(new ZephyrPage<>(
				null, 0, 1000, 3, true,
				List.of(
						new TestCaseDto(1L, "SQM-T1", "Case 1", new TestCaseDto.PriorityRef(2L)),
						new TestCaseDto(2L, "SQM-T2", "Case 2", new TestCaseDto.PriorityRef(1L)),
						new TestCaseDto(3L, "SQM-T3", "Case 3", new TestCaseDto.PriorityRef(2L))
				)
		));

		var result = testCaseService.getTestCasesPage(Project.B2C, Optional.of(2L), 0);

		assertThat(result.totalItems()).isEqualTo(2);
		assertThat(result.testCases()).extracting("key").containsExactly("SQM-T1", "SQM-T3");
		verify(zephyrScaleClient).getTestCases("SQM", 0, 1000, Optional.empty());
	}
}
