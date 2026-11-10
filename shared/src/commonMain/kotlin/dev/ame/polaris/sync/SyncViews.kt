package dev.ame.polaris.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.ame.polaris.Palette
import dev.ame.polaris.ui.GoldButton
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.shortDate
import dev.ame.polaris.util.today
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The app's SyncManager, for any screen that wants it. Null in tests and previews. */
val LocalSync = staticCompositionLocalOf<SyncManager?> { null }

/**
 * A small status line, e.g. "● SYNCED 12:03". Tap it to open the account panel.
 * Green dot = all good, tangerine = working on it, terracotta = signed out or a problem.
 */
@Composable
fun SyncBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sync = LocalSync.current ?: return
    val status by sync.status.collectAsState()
    val (dot, text) = when {
        !status.signedIn -> Palette.Terracotta to "SIGN IN TO SYNC"
        status.syncing -> Palette.Tangerine to "SYNCING…"
        status.problem != null -> Palette.Terracotta to status.problem!!.uppercase()
        status.lastSynced != null -> Palette.Sage to "SYNCED ${timeLabel(status.lastSynced!!)}"
        else -> Palette.Tangerine to "NOT SYNCED YET"
    }
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Sign in / create account, or (when signed in) your account, last sync, Sync now and Sign out. */
@Composable
fun AccountDialog(onDismiss: () -> Unit) {
    val sync = LocalSync.current ?: return
    val status by sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(action: suspend () -> String?) {
        busy = true
        message = null
        scope.launch {
            message = action()
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status.signedIn) {
                    Text("Signed in as", style = MaterialTheme.typography.labelMedium)
                    Text(status.email ?: "your account", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            status.syncing -> "Syncing…"
                            status.lastSynced != null -> "Last synced ${timeLabel(status.lastSynced!!)}"
                            else -> "Not synced yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    status.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Tasks, events, schedule and notes sync between your devices. " +
                            "Changes sync a few seconds after you make them, and every few minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Sign in with the same account on your phone and computer to keep them in sync.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton(
                            onClick = { run { sync.signIn(email, password) } },
                            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        ) { Text("Sign in") }
                        TextButton(
                            onClick = { run { sync.signUp(email, password) } },
                            enabled = !busy && email.isNotBlank() && password.length >= 6,
                        ) { Text("Create account") }
                    }
                }
            }
        },
        confirmButton = {
            if (status.signedIn) {
                TextButton(onClick = sync::syncNow, enabled = !status.syncing) { Text("Sync now") }
            }
        },
        dismissButton = {
            Row {
                if (status.signedIn) {
                    TextButton(onClick = { run { sync.signOut(); null } }) {
                        Text("Sign out", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/** "12:03" today, otherwise "9 Nov 12:03". */
private fun timeLabel(millis: Long): String {
    val at = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return if (at.date == today()) hhmm(at.time) else "${shortDate(at.date)} ${hhmm(at.time)}"
}
