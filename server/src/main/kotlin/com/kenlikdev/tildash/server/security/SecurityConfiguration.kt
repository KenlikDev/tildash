package com.kenlikdev.tildash.server.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.http.HttpStatus

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfiguration(
    private val properties: SecurityProperties,
    private val securityHandler: ProblemDetailsSecurityHandler,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(securityHandler)
                    .accessDeniedHandler(securityHandler)
            }.authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/actuator/health",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                    ).permitAll()
                    .requestMatchers("/api/v1/**").authenticated()
                    .anyRequest().denyAll()
            }.httpBasic { it.disable() }
            .formLogin { it.disable() }

        if (properties.resourceServer.enabled) {
            http.oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter(properties.jwt.rolesClaim),
                    )
                }
            }
        }

        return http.build()
    }
}
