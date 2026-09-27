package com.kenlikdev.tildash.server

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@Import(SecurityFoundationTests.TestSecurityConfiguration::class)
class SecurityFoundationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun apiRequiresAuthentication() {
        mockMvc
            .perform(
                get("/api/v1/auth/me")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            )
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:unauthorized"))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.instance").value("/api/v1/auth/me"))
    }

    @Test
    @WithMockUser(username = "teacher-123", roles = ["TEACHER", "REVIEWER"])
    fun authenticatedIdentityUsesApplicationContract() {
        mockMvc
            .perform(
                get("/api/v1/auth/me")
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.subject").value("teacher-123"))
            .andExpect(jsonPath("$.roles[0]").value("reviewer"))
            .andExpect(jsonPath("$.roles[1]").value("teacher"))
    }

    @Test
    @WithMockUser(roles = ["LEARNER"])
    fun learnerCannotInvokeTeacherOnlyUseCase() {
        mockMvc
            .perform(
                get("/api/v1/security-test/teacher")
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            )
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:forbidden"))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.instance").value("/api/v1/security-test/teacher"))
    }

    @Test
    @WithMockUser(roles = ["TEACHER"])
    fun teacherCanInvokeTeacherOnlyUseCase() {
        mockMvc
            .perform(
                get("/api/v1/security-test/teacher")
                    .accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(status().isOk)
            .andExpect(content().string("teacher-access"))
    }

    @Test
    fun jwtRolesAreMappedToKnownAuthorities() {
        val jwt =
            Jwt
                .withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("roles", listOf("teacher", "reviewer", "administrator", "unknown"))
                .build()

        val authorities = JwtRoleAuthenticationConverter("roles").convert(jwt)

        assertEquals(
            setOf("ROLE_ADMINISTRATOR", "ROLE_REVIEWER", "ROLE_TEACHER"),
            authorities
                .mapNotNull { it.getAuthority() }
                .toSet(),
        )
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestSecurityConfiguration {
        @Bean
        fun teacherProtectedService(): TeacherProtectedService =
            TeacherProtectedServiceImpl()

        @Bean
        fun securityProbeController(service: TeacherProtectedService): SecurityProbeController =
            SecurityProbeController(service)
    }

    interface TeacherProtectedService {
        @PreAuthorize("hasRole('TEACHER')")
        fun access(): String
    }

    class TeacherProtectedServiceImpl : TeacherProtectedService {
        override fun access(): String = "teacher-access"
    }

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/v1/security-test")
    class SecurityProbeController(
        private val service: TeacherProtectedService,
    ) {
        @org.springframework.web.bind.annotation.GetMapping("/teacher")
        fun teacherAccess(): String = service.access()
    }
}
