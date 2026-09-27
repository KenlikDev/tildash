package com.kenlikdev.tildash.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@Import(PostgresTestConfiguration::class)
@AutoConfigureMockMvc
class ApiFoundationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun openApiDocumentIsAvailable() {
        mockMvc
            .perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.openapi").isNotEmpty)
            .andExpect(jsonPath("$.info.title").value("Tildash API"))
            .andExpect(jsonPath("$.info.version").value("1.0.0"))
    }

    @Test
    fun apiErrorsUseProblemDetails() {
        mockMvc
            .perform(
                get("/api/v1/missing")
                    .with(user("api-test-user"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.instance").value("/api/v1/missing"))
    }
}
