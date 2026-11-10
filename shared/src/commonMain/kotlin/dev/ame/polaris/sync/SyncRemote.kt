package dev.ame.polaris.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * The cloud side of sync, as plain JSON rows. An interface so tests can use an
 * in-memory fake server instead of the real Supabase.
 */
interface SyncRemote {
    /** Uploads rows. The server keeps whichever version of each row changed last. */
    suspend fun push(table: String, rows: List<JsonObject>)

    /**
     * Rows the server accepted at or after [since] (the server's own timestamp; null = all),
     * oldest first, at most [limit]. Each row has a "synced_at" with that timestamp.
     */
    suspend fun pull(table: String, since: String?, limit: Int): List<JsonObject>
}

/** The real thing: Supabase's database API (PostgREST). */
class SupabaseRemote(private val client: SupabaseClient) : SyncRemote {

    override suspend fun push(table: String, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        // "upsert": insert new rows, update existing ones (matched by id)
        client.from(table).upsert(JsonArray(rows)) { onConflict = "id" }
    }

    override suspend fun pull(table: String, since: String?, limit: Int): List<JsonObject> =
        client.from(table).select {
            if (since != null) filter { gte("synced_at", since) }
            order("synced_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<JsonObject>()
}
