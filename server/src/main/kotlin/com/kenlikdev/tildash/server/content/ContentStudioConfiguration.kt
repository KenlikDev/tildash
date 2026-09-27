package com.kenlikdev.tildash.server.content

import com.kenlikdev.tildash.content.validation.ContentValidator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ContentStudioConfiguration {
    @Bean
    fun contentValidator(): ContentValidator = ContentValidator()
}
