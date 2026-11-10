package dev.ame.polaris.sync

import dev.ame.polaris.data.SettingDao
import dev.ame.polaris.data.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

/** What one sync round did. */
data class SyncReport(val pushed: Int, val pulled: Int)

/**
 * One sync round = for each table: push local changes up, then pull other devices' changes down.
 *
 * Progress is remembered per table in the settings table:
 *  - "sync.<table>.pushed": the newest local changedAt already uploaded. Starts at -1, so the
 *    very first sync uploads everything (including rows from before sync existed, changedAt 0).
 *  - "sync.<table>.pulled": the server timestamp of the newest change already downloaded.
 *    It's the *server's* clock, so an edit made offline and uploaded hours later still has a
 *    new timestamp, and is never missed.
 *
 * Every step can safely run twice (same rows in, same result), so a sync that fails halfway
 * just picks up where it left off next time.
 */
class SyncEngine(
    private val tables: List<SyncTable<*, *>>,
    private val remote: SyncRemote,
    private val settings: SettingDao,
    private val pageSize: Int = 500,
) {
    suspend fun syncOnce(): SyncReport {
        var pushed = 0
        var pulled = 0
        for (table in tables) {
            pushed += push(table)
            pulled += pull(table)
        }
        return SyncReport(pushed, pulled)
    }

    private suspend fun push(table: SyncTable<*, *>): Int {
        val key = "sync.${table.name}.pushed"
        val since = settings.get(key)?.toLongOrNull() ?: -1L
        val (rows, newest) = table.localChanges(since)
        if (rows.isEmpty() || newest == null) return 0
        rows.chunked(pageSize).forEach { remote.push(table.name, it) }
        settings.put(key, newest.toString())
        return rows.size
    }

    private suspend fun pull(table: SyncTable<*, *>): Int {
        val key = "sync.${table.name}.pulled"
        var since = settings.get(key)
        var stored = 0
        while (true) {
            // "at or after": several rows can share a timestamp, so re-read the boundary row
            // rather than risk skipping one. Re-applying a row is harmless.
            val page = remote.pull(table.name, since, pageSize)
            if (page.isEmpty()) break
            stored += table.applyFromCloud(page) { it.id() }
            // Compare as real times (the server may write "…44.1+00:00" or "…44.123456+00:00")
            val newest = page.maxBy { Instant.parse(it.syncedAt()) }.syncedAt()
            if (newest != since) settings.put(key, newest)
            if (page.size < pageSize || newest == since) break
            since = newest
        }
        return stored
    }

    /** Forget all progress, so the next sync starts from scratch (e.g. after signing out). */
    suspend fun reset() {
        for (table in tables) {
            settings.remove("sync.${table.name}.pushed")
            settings.remove("sync.${table.name}.pulled")
        }
    }
}

private fun JsonObject.id(): String = getValue("id").jsonPrimitive.content
private fun JsonObject.syncedAt(): String = getValue("synced_at").jsonPrimitive.content
