package dev.ame.polaris.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Checks the real settings table (`key` is a reserved word in SQL, hence the backticks in SettingDao). */
class SettingDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<PolarisDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val dao = db.settingDao()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun putOverwritesAndRemoveDeletes() = runTest {
        dao.put("weather.unit", "Celsius")
        dao.put("weather.unit", "Fahrenheit")
        assertEquals("Fahrenheit", dao.get("weather.unit"))
        assertEquals("Fahrenheit", dao.observe("weather.unit").first())

        dao.remove("weather.unit")
        assertNull(dao.get("weather.unit"))
    }
}
