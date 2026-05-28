package com.qligent.triage.web;

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
import com.qligent.triage.service.TestCaseService;
import com.qligent.triage.web.model.TestCaseView;
import com.qligent.triage.web.model.TestCasesPageView;

@WebMvcTest(controllers = { HomeController.class, TestCaseController.class, StubTabController.class })
@Import({ WebModelAdvice.class, WebExceptionHandler.class })
class WebControllersTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TestCaseService testCaseService;

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
				null,
				List.of(new TestCaseView("SQM-T1", "Normal", "Sample test case")),
				0,
				10,
				1,
				1,
				true
		);
		given(testCaseService.getTestCasesPage(eq(Project.B2C), eq(Optional.empty()), eq(0))).willReturn(pageView);

		mockMvc.perform(get("/testcases").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("testcases"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("SQM-T1")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Prioritize")));
	}

	@Test
	void testCasesPriorityFilterPassedToService() throws Exception {
		var pageView = new TestCasesPageView(
				Project.B2C,
				List.of(),
				1140558L,
				List.of(),
				0,
				10,
				0,
				1,
				true
		);
		given(testCaseService.getTestCasesPage(eq(Project.B2C), eq(Optional.of(1140558L)), eq(0))).willReturn(pageView);

		mockMvc.perform(get("/testcases")
						.param("project", "B2C")
						.param("priority", "1140558"))
				.andExpect(status().isOk())
				.andExpect(view().name("testcases"));

		then(testCaseService).should().getTestCasesPage(eq(Project.B2C), eq(Optional.of(1140558L)), eq(0));
	}

	@Test
	void testCyclesStubRenders() throws Exception {
		mockMvc.perform(get("/testcycles").param("project", "B2B"))
				.andExpect(status().isOk())
				.andExpect(view().name("stub-tab"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("under development")));
	}

	@Test
	void suggestedCyclesStubRenders() throws Exception {
		mockMvc.perform(get("/suggestedcycles").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("stub-tab"));
	}

}
