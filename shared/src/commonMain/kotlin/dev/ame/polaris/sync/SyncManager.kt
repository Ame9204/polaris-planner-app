package dev.ame.polaris.sync

import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.data.put
import dev.ame.polaris.util.nowMillis
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Your Supabase project. The publishable key is meant to be in apps; the database's security rules protect the data. */
object SupabaseConfig {
    const val URL = "https://mbvgigrbslznnhrxptbz.supabase.co"
    const val KEY = "sb_publishable_ZwXXW0W17zsmPtRI2eV97g_S3MO3iZ5"
}

/** What the account panel shows. */
data class SyncStatus(
    val signedIn: Boolean = false,
    val email: String? = null,
    val syncing: Boolean = false,
    /** When the last sync finished successfully (ms), or null if never. */
    val lastSynced: Long? = null,
    /** A short message when the last try failed (e.g. offline). Cleared by the next success. */
    val problem: String? = null,
)

/**
 * Runs cloud sync for the whole app: sign-in, and syncing automatically
 *  - a few seconds after you change something,
 *  - every few minutes while the app is open,
 *  - and when you tap "Sync now".
 * One instance per app (created by each platform, see PolarisApp / main.kt).
 */
class SyncManager(private val db: PolarisDatabase, private val scope: CoroutineScope) {

    private val client = createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.KEY) {
        install(Auth)      // sign-in; the session is remembered on the device
        install(Postgrest) // the database
    }
    private val settings = db.settingDao()
    private val engine = SyncEngine(syncTables(db), SupabaseRemote(client), settings)
    private val lock = Mutex()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Call once when the app starts. */
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            _status.update { it.copy(lastSynced = settings.get(LAST_SYNCED)?.toLongOrNull()) }
            client.auth.sessionStatus.collectLatest { session ->
                if (session is SessionStatus.Authenticated) {
                    _status.update { it.copy(signedIn = true, email = session.session.user?.email) }
                    coroutineScope {
                        // Local changes -> sync a few seconds later (also fires once right away)
                        launch {
                            db.invalidationTracker.createFlow(*SYNCED_TABLES)
                                .debounce(3_000)
                                .collect { runSync() }
                        }
                        // And every few minutes, to pick up changes from your other device
                        launch {
                            while (true) {
                                delay(5 * 60 * 1000L)
                                runSync()
                            }
                        }
                    }
                } else {
                    _status.update { it.copy(signedIn = false, email = null, syncing = false) }
                }
            }
        }
    }

    /** "Sync now". */
    fun syncNow() {
        scope.launch { runSync() }
    }

    /** Returns an error message to show, or null if it worked. */
    suspend fun signIn(email: String, password: String): String? = attempt {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Creates the account. Returns a message to show: either an error, or (if your project
     * asks new users to confirm their email) a reminder to check the inbox.
     */
    suspend fun signUp(email: String, password: String): String? {
        attempt {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }?.let { return it }
        return if (client.auth.currentSessionOrNull() == null) {
            "Account created. Check your email to confirm it, then sign in."
        } else {
            null
        }
    }

    /** Signs out on this device. Your data stays here and in the cloud. */
    suspend fun signOut() {
        attempt { client.auth.signOut() }
        engine.reset() // a different account would start fresh
        settings.remove(LAST_SYNCED)
        _status.update { SyncStatus() }
    }

    private suspend fun runSync() {
        if (!_status.value.signedIn) return
        lock.withLock {
            _status.update { it.copy(syncing = true) }
            try {
                engine.syncOnce()
                val now = nowMillis()
                settings.put(LAST_SYNCED, now.toString())
                _status.update { it.copy(syncing = false, lastSynced = now, problem = null) }
            } catch (e: CancellationException) {
                _status.update { it.copy(syncing = false) }
                throw e
            } catch (e: Exception) {
                _status.update { it.copy(syncing = false, problem = friendly(e)) }
            }
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        friendly(e)
    }

    private fun friendly(e: Exception): String {
        val text = (e.message ?: "").lowercase()
        return when {
            "invalid login" in text || "invalid_credentials" in text -> "Wrong email or password."
            "email not confirmed" in text -> "Confirm your email first (check your inbox)."
            "already registered" in text || "already exists" in text -> "That email already has an account. Sign in instead."
            "password" in text && ("short" in text || "least" in text) -> "Password is too short (at least 6 characters)."
            "relation" in text && "does not exist" in text -> "The cloud tables aren't set up yet (run supabase/schema.sql)."
            "unable to resolve" in text || "timeout" in text || "connect" in text || "network" in text -> "Offline. Will retry."
            else -> e.message?.lineSequence()?.firstOrNull()?.take(120) ?: "Sync failed."
        }
    }

    companion object {
        private const val LAST_SYNCED = "sync.lastSynced"
        private val SYNCED_TABLES = arrayOf("tasks", "events", "schedule_blocks", "notes")
    }
}
