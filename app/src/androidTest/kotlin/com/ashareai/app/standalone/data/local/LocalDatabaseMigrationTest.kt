package com.ashareai.app.standalone.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class LocalDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LocalDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesV1DatabaseToLocalStandaloneSchema() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            LocalDatabase.MIGRATION_1_2,
            LocalDatabase.MIGRATION_2_3,
        ).close()
    }

    @Test
    fun migratesV6DatabaseToMonitoringEventsAndSignalColumns() {
        helper.createDatabase("standalone-migration-v6", 6).close()
        helper.runMigrationsAndValidate(
            "standalone-migration-v6",
            8,
            true,
            LocalDatabase.MIGRATION_6_7,
            LocalDatabase.MIGRATION_7_8,
        ).close()
    }

    private companion object {
        const val TEST_DB = "standalone-migration-test"
    }
}
