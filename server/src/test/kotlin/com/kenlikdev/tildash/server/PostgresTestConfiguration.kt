package com.kenlikdev.tildash.server

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer(
            DockerImageName.parse("postgres:18.6-alpine3.24"),
        ).withDatabaseName("tildash")
            .withUsername("tildash")
            .withPassword("tildash-test")
}
