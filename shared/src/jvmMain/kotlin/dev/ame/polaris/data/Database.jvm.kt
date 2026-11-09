package dev.ame.polaris.data

import androidx.room.Room
import java.io.File

/** Desktop keeps the file in %APPDATA%\Polaris on Windows, or ~/.polaris elsewhere. */
fun createDatabase(): PolarisDatabase {
    val dir = System.getenv("APPDATA")?.let { File(it, "Polaris") }
        ?: File(System.getProperty("user.home"), ".polaris")
    dir.mkdirs()
    return Room.databaseBuilder<PolarisDatabase>(name = File(dir, "polaris.db").absolutePath)
        .buildPolaris()
}
