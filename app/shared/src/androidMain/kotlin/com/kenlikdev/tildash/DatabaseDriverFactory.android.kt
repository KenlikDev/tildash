package com.kenlikdev.tildash

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.kenlikdev.tildash.storage.TildashDatabase

fun createTildashDatabaseDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(
        schema = TildashDatabase.Schema,
        context = context,
        name = "tildash.db",
        callback =
            object : AndroidSqliteDriver.Callback(TildashDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
    )
