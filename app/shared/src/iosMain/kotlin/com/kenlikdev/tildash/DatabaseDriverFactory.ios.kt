@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.kenlikdev.tildash

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.DatabaseConfiguration
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.kenlikdev.tildash.storage.TildashDatabase
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

fun createTildashDatabaseDriver(): SqlDriver {
    val directory = "${NSHomeDirectory()}/Library/Application Support/Tildash"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )

    return NativeSqliteDriver(
        schema = TildashDatabase.Schema,
        name = "$directory/tildash.db",
        onConfiguration = { config: DatabaseConfiguration ->
            config.copy(
                extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true),
            )
        },
    )
}
