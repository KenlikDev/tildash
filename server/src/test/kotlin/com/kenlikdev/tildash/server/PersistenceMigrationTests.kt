package com.kenlikdev.tildash.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

@SpringBootTest
@Import(PostgresTestConfiguration::class)
class PersistenceMigrationTests {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun applicationSchemaIsCreatedByMigration() {
        val schemaCount =
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.schemata
                where schema_name = 'tildash'
                """.trimIndent(),
                Int::class.java,
            )

        assertEquals(1, schemaCount)
    }

    @Test
    fun firstMigrationIsRecordedAsSuccessful() {
        val migrationCount =
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from flyway_schema_history
                where version = '1'
                  and script = 'V1__create_application_schema.sql'
                  and success = true
                """.trimIndent(),
                Int::class.java,
            )

        assertEquals(1, migrationCount)
    }
}
