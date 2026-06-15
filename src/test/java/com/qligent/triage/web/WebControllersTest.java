package com.qligent.triage.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.BDDMockito.then;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.qligent.triage.config.TriageProperties;
import com.qligent.triage.llm.LlmPromptTemplateService;
import com.qligent.triage.model.Project;
import com.qligent.triage.service.SortDirection;
import com.qligent.triage.service.TestCaseListQuery;
import com.qligent.triage.service.TestCaseService;
import com.qligent.triage.service.TestCaseSort;
import com.qligent.triage.suggested.SuggestedTestCycle;
import com.qligent.triage.suggested.SuggestedTestCycleService;
import com.qligent.triage.web.model.SuggestedCyclesPageView;
import com.qligent.triage.web.model.TestCaseView;
import com.qligent.triage.web.model.TestCasesPageView;

@WebMvcTest(controllers = {
		HomeController.class,
		TestCaseController.class,
		SuggestedTestCycleController.class,
		SettingsController.class
})
@Import({ WebModelAdvice.class, WebExceptionHandler.class })
class WebControllersTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TestCaseService testCaseService;

	@MockitoBean
	private SuggestedTestCycleService suggestedTestCycleService;

	@MockitoBean
	private TriageProperties triageProperties;

	@MockitoBean
	private LlmPromptTemplateService llmPromptTemplateService;

	@BeforeEach
	void stubThemeDefaults() {
		given(triageProperties.theme()).willReturn(new TriageProperties.Theme("system"));
		given(triageProperties.locale()).willReturn(new TriageProperties.Locale("en"));
		given(triageProperties.testcasesPageSize()).willReturn(15);
		given(llmPromptTemplateService.getTemplate()).willReturn("Default prompt {testCaseData}");
	}

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
				List.of(new TestCaseView("SQM-T1", "Normal", "#ff0000", "#ffffff", "Approved", "Folder A", "Sample test case")),
				List.of("SQM-T1"),
				0,
				10,
				1,
				1,
				1,
				1,
				true
		);
		given(testCaseService.getTestCasesPage(eq(Project.B2C), any(TestCaseListQuery.class))).willReturn(pageView);

		mockMvc.perform(get("/testcases").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("testcases"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("SQM-T1")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Prioritize")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("weights-prompt")));
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
				List.of(),
				0,
				10,
				0,
				0,
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
				0,
				15);
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
	void settingsPageRenders() throws Exception {
		mockMvc.perform(get("/settings").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("settings"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Dark")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("data-default-theme=\"system\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("data-default-locale=\"en\"")));
	}

	@Test
	void suggestedCyclesListRenders() throws Exception {
		var cycle = SuggestedTestCycle.create(
				"SQM-SR1",
				"Regression",
				"SQM",
				1781453505000L,
				"1781453505000_SQM-SR1",
				List.of(new SuggestedTestCycle.SuggestedTestCaseEntry(
						"SQM-T1",
						"Login",
						Map.of("priority", 0.5, "status", 0.5),
						new SuggestedTestCycle.TestMetrics(1, 0, 0, 0.0, "Pass", 100L),
						1.0)));
		var pageView = new SuggestedCyclesPageView(List.of(cycle), 0, 15, 1, 1, 1, 1, true);
		given(suggestedTestCycleService.listPageForProject(eq(Project.B2C), eq(0), eq(15))).willReturn(pageView);

		mockMvc.perform(get("/suggestedcycles").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("suggested-cycles"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("SQM-SR1")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("rows-per-page")));
	}

	@Test
	void suggestedCycleDetailRendersTestsJsonUnescaped() throws Exception {
		given(suggestedTestCycleService.getByKey("SQM-SR1")).willReturn(
				SuggestedTestCycle.create(
						"SQM-SR1",
						"Regression",
						"SQM",
						1781453505000L,
						"1781453505000_SQM-SR1",
						List.of(new SuggestedTestCycle.SuggestedTestCaseEntry(
								"SQM-T1453",
								"Smoke test",
								Map.of("priority", 0.5, "status", 0.5),
								new SuggestedTestCycle.TestMetrics(1, 0, 0, 0.0, "Pass", 100L),
								0.15))));

		mockMvc.perform(get("/suggestedcycles/SQM-SR1").param("project", "B2C"))
				.andExpect(status().isOk())
				.andExpect(view().name("suggested-cycle-detail"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"key\":\"SQM-T1453\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("/api/suggestedcycles/SQM-SR1/json")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("&quot;"))));
	}

	@Test
	void suggestedCycleJsonDownloadServesFile() throws Exception {
		Path tempFile = Files.createTempFile("sqm-sr1", ".json");
		Files.writeString(tempFile, "{\"suggestedTestCycleKey\":\"SQM-SR1\"}");
		given(suggestedTestCycleService.getCycleJsonFile("SQM-SR1")).willReturn(tempFile);

		mockMvc.perform(get("/api/suggestedcycles/SQM-SR1/json"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"SQM-SR1.json\""))
				.andExpect(content().json("{\"suggestedTestCycleKey\":\"SQM-SR1\"}"));
	}

	@Test
	void suggestedCycleSendReturnsZephyrKey() throws Exception {
		given(suggestedTestCycleService.sendToZephyr("SQM-SR1")).willReturn("SQM-R42");

		mockMvc.perform(post("/api/suggestedcycles/SQM-SR1/send"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"zephyrTestCycleKey\":\"SQM-R42\"}"));
	}

}
