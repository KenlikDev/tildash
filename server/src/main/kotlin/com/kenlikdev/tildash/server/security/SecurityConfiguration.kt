package com.kenlikdev.tildash.server.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfiguration(
    private val properties: SecurityProperties,
    private val securityHandler: ProblemDetailsSecurityHandler,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.ignoringRequestMatchers("/api/v1/**") }

        http.sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }

        http.exceptionHandling {
            it
                .authenticationEntryPoint(securityHandler)
                .accessDeniedHandler(securityHandler)
        }

        http.authorizeHttpRequests {
            it
                .requestMatchers(
                    "/actuator/health",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                ).permitAll()
                .requestMatchers("/api/v1/**")
                .authenticated()
                .anyRequest()
                .denyAll()
        }

        if (properties.development.enabled) {
            http.addFilterBefore(
                DevelopmentIdentityFilter(properties),
                BearerTokenAuthenticationFilter::class.java,
            )
        }

        http.httpBasic { it.disable() }
        http.formLogin { it.disable() }

        if (properties.resourceServer.enabled) {
            http.oauth2ResourceServer {
                it.authenticationEntryPoint(securityHandler)
                it.accessDeniedHandler(securityHandler)
                it.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
        }

        return http.build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter = createJwtAuthenticationConverter(properties.jwt.rolesClaim)
}
