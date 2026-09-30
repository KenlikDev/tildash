package com.kenlikdev.tildash

import android.content.Context
import app.cash.sqldelight.android.driver.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.kenlikdev.tildash.storage.TildashDatabase

fun createTildashDatabaseDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(
        schema = TildashDatabase.Schema,
        context = context,
        name = "tildash.db",
    )
