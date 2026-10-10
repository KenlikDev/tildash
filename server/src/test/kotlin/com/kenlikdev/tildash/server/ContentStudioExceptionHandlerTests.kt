package com.kenlikdev.tildash.server

import com.kenlikdev.tildash.server.api.content.ContentStudioExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals

class ContentStudioExceptionHandlerTests {
    @Test
    fun invalidRequestDoesNotExposeArbitraryExceptionMessage() {
        withRequest {
            val response = ContentStudioExceptionHandler().handleInvalidRequest()

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals("The content request is invalid.", response.body?.detail)
        }
    }

    private fun <T> withRequest(block: () -> T): T {
        val request = MockHttpServletRequest("POST", "/api/v1/content/nodes")
        val previous = RequestContextHolder.getRequestAttributes()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return try {
            block()
        } finally {
            if (previous == null) {
                RequestContextHolder.resetRequestAttributes()
            } else {
                RequestContextHolder.setRequestAttributes(previous)
            }
        }
    }
}
