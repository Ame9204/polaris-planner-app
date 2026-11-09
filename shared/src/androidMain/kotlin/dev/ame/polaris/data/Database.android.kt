package dev.ame.polaris.data

import android.content.Context
import androidx.room.Room

/** Android keeps the file in the app's private storage (deleted if the app is uninstalled). */
fun createDatabase(context: Context): PolarisDatabase {
    val app = context.applicationContext
    val file = app.getDatabasePath("polaris.db")
    return Room.databaseBuilder<PolarisDatabase>(context = app, name = file.absolutePath)
        .buildPolaris()
}
