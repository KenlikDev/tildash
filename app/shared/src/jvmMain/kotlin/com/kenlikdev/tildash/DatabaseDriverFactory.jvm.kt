package com.kenlikdev.tildash

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kenlikdev.tildash.storage.TildashDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

fun createTildashDatabaseDriver(): SqlDriver {
    val dataDirectory =
        Path.of(
            System.getProperty("user.home"),
            ".tildash",
        )
    Files.createDirectories(dataDirectory)

    val databasePath = dataDirectory.resolve("tildash.db")
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:$databasePath",
        properties =
            Properties().apply {
                put("foreign_keys", "true")
            },
        schema = TildashDatabase.Schema,
    )
}
