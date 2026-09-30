package com.kenlikdev.tildash.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "tildash.security.development.enabled=true",
        "tildash.security.development.subject=local-test",
    ],
)
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
class DevelopmentSecurityIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun remoteAddress(address: String): RequestPostProcessor =
        RequestPostProcessor { request ->
            request.remoteAddr = address
            request
        }

    @Test
    fun developmentLearnerRoleCanAccessLearnerCatalogWithoutJwt() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .header("X-Tildash-Development-Role", "learner")
                    .header("Host", "localhost")
                    .with(remoteAddress("127.0.0.1"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
    }

    @Test
    fun developmentModeDoesNotAuthenticateWithoutDevelopmentRoleHeader() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .with(remoteAddress("127.0.0.1")),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun developmentIdentityIsLimitedToLoopbackRequests() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .header("X-Tildash-Development-Role", "learner")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .with(remoteAddress("192.168.1.10")),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun developmentIdentityCanUseExplicitRoleButDoesNotOverrideAuthorization() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .header("X-Tildash-Development-Role", "teacher")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .withRemoteAddress("127.0.0.1"),
            ).andExpect(status().isForbidden)
    }
}
