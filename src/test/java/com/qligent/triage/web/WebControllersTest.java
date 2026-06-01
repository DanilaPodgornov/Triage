package com.qligent.triage.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.qligent.triage.model.Project;
import com.qligent.triage.service.SortDirection;
import com.qligent.triage.service.TestCaseListQuery;
import com.qligent.triage.service.TestCaseService;
import com.qligent.triage.service.TestCaseSort;
import com.qligent.triage.suggested.SuggestedTestCycle;
import com.qligent.triage.suggested.SuggestedTestCycleService;
import com.qligent.triage.web.model.TestCaseView;
import com.qligent.triage.web.model.TestCasesPageView;

@WebMvcTest(controllers = {
		HomeController.class,
		TestCaseController.class,
		StubTabController.class,
		SuggestedTestCycleController.class
})
@Import({ WebModelAdvice.class, WebExceptionHandler.class })
class WebControllersTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TestCaseService testCaseService;

	@MockitoBean
	private SuggestedTestCycleService suggestedTestCycleService;

	@Test
	void rootRedirectsToB2CTestCases() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/testcases?project=B2C"));
	}

	@Test
	void testCasesPageRenders() throws Exception {
		var pageView = new TestCasesPageView(
				Project.B2C,
				List.of(),
				List.of(),
				List.of(),
				null,
				null,
				null,
				TestCaseSort.KEY,
				SortDirection.ASC,
				List.of(new TestCaseView("SQM-T1", "Normal", "Approved", "Folder A", "Sample test case")),
				0,
				10,
				1,
				1,
				true
		);
		given(testCaseService.getTestCasesPage(eq(Project.B2C), any(TestCaseListQuery.class))).willReturn(pageView);

		mockMvc.perform(get("/testcases").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("testcases"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("SQM-T1")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Prioritize")));
	}

	@Test
	void testCasesFiltersPassedToService() throws Exception {
		var pageView = new TestCasesPageView(
				Project.B2C,
				List.of(),
				List.of(),
				List.of(),
				1L,
				10L,
				20L,
				TestCaseSort.FOLDER,
				SortDirection.DESC,
				List.of(),
				0,
				10,
				0,
				1,
				true
		);
		var expectedQuery = new TestCaseListQuery(
				Optional.of(1L),
				Optional.of(10L),
				Optional.of(20L),
				TestCaseSort.FOLDER,
				SortDirection.DESC,
				0);
		given(testCaseService.getTestCasesPage(eq(Project.B2C), eq(expectedQuery))).willReturn(pageView);

		mockMvc.perform(get("/testcases")
						.param("project", "B2C")
						.param("priority", "1")
						.param("folder", "10")
						.param("status", "20")
						.param("sort", "folder")
						.param("dir", "desc"))
				.andExpect(status().isOk())
				.andExpect(view().name("testcases"));

		then(testCaseService).should().getTestCasesPage(eq(Project.B2C), eq(expectedQuery));
	}

	@Test
	void testCyclesStubRenders() throws Exception {
		mockMvc.perform(get("/testcycles").param("project", "B2B"))
				.andExpect(status().isOk())
				.andExpect(view().name("stub-tab"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("under development")));
	}

	@Test
	void suggestedCyclesListRenders() throws Exception {
		given(suggestedTestCycleService.listForProject(Project.B2C)).willReturn(List.of(
				SuggestedTestCycle.create("SQM-SR1", "Regression", List.of("SQM-T1"))
		));

		mockMvc.perform(get("/suggestedcycles").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("suggested-cycles"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("SQM-SR1")));
	}

}
