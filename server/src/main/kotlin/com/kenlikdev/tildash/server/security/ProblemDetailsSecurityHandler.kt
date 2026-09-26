package com.kenlikdev.tildash.server.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Component
class ProblemDetailsSecurityHandler(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        writeProblem(
            request = request,
            response = response,
            status = HttpStatus.UNAUTHORIZED,
            type = "urn:tildash:problem:unauthorized",
            title = "Unauthorized",
            detail = "Authentication is required to access this resource.",
        )
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        writeProblem(
            request = request,
            response = response,
            status = HttpStatus.FORBIDDEN,
            type = "urn:tildash:problem:forbidden",
            title = "Forbidden",
            detail = "You are not authorized to access this resource.",
        )
    }

    private fun writeProblem(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        type: String,
        title: String,
        detail: String,
    ) {
        val problem =
            ProblemDetail.forStatusAndDetail(status, detail).apply {
                this.type = URI.create(type)
                this.title = title
                instance = URI.create(request.requestURI)
            }

        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.outputStream, problem)
    }

    private object HttpHeaders {
        const val WWW_AUTHENTICATE = "WWW-Authenticate"
    }
}
