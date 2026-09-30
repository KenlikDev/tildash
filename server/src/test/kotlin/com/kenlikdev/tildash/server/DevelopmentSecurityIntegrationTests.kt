package com.kenlikdev.tildash.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "tildash.security.development.enabled=true",
        "tildash.security.development.subject=local-test",
    ],
)
@AutoConfigureMockMvc
class DevelopmentSecurityIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun developmentLearnerRoleCanAccessLearnerCatalogWithoutJwt() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .header("X-Tildash-Development-Role", "learner")
                    .header("Host", "localhost")
                    .with { request -> request.remoteAddr = "127.0.0.1"; request }
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
    }

    @Test
    fun developmentModeDoesNotAuthenticateWithoutDevelopmentRoleHeader() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .withRemoteAddress("127.0.0.1"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun developmentIdentityIsLimitedToLoopbackRequests() {
        mockMvc
            .perform(
                get("/api/v1/learning/catalog")
                    .header("X-Tildash-Development-Role", "learner")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .with { request -> request.remoteAddr = "192.168.1.10"; request },
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
