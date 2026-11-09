package dev.ame.polaris.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.mikepenz.markdown.m3.Markdown
import dev.ame.polaris.util.today
import kotlinx.datetime.LocalDate

@Composable
fun NotesScreen(
    dao: NoteDao,
    modifier: Modifier = Modifier,
    vm: NotesViewModel = viewModel { NotesViewModel(dao) },
) {
    val state by vm.state.collectAsState()
    val today = remember { today() }
    val selected = state.selected

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp

        // On a phone with a note open, the Back gesture closes the note instead of leaving the app.
        // (Always called; `isBackEnabled` switches it on and off.)
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = !wide && selected != null,
            onBackCompleted = vm::close,
        )

        if (wide) {
            // Wide: list on the left, the open note on the right
            Row(Modifier.fillMaxSize()) {
                NotesList(state, today, vm, Modifier.width(320.dp).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.surface)
                if (selected != null) {
                    NoteEditor(selected, vm, onBack = null, modifier = Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text("Select a note, or make a new one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (selected != null) {
            // Narrow, note open: the editor takes the whole screen.
            NoteEditor(selected, vm, onBack = vm::close, modifier = Modifier.fillMaxSize())
        } else {
            NotesList(state, today, vm, Modifier.fillMaxSize())
        }
    }
}

// ---------- List ----------

@Composable
private fun NotesList(state: NotesUiState, today: LocalDate, vm: NotesViewModel, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notes",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = vm::create) { Text("New note") }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (state.notes.isEmpty()) {
            Text(
                if (state.query.isBlank()) "No notes yet." else "No notes match \"${state.query.trim()}\".",
                color = colors.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.notes, key = { it.id }) { note ->
                NoteRow(
                    note = note,
                    today = today,
                    isSelected = note.id == state.selectedId,
                    onClick = { vm.select(note.id) },
                )
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, today: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (isSelected) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) Text("★ ", color = colors.primary)
                Text(
                    displayTitle(note),
                    modifier = Modifier.weight(1f),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    updatedLabel(note.updatedAt, today),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            // Only show a second line when the title isn't already the first line of text
            val preview = previewText(note.body)
            if (note.title.isNotBlank() && preview.isNotEmpty()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------- Editor ----------

@Composable
private fun NoteEditor(note: Note, vm: NotesViewModel, onBack: (() -> Unit)?, modifier: Modifier) {
    // key(note.id): switching notes throws away this note's local text state and starts fresh.
    // Inside, the text lives here while you type; the database only gets autosaved copies,
    // so saves never move your cursor.
    key(note.id) {
        val colors = MaterialTheme.colorScheme
        var title by remember { mutableStateOf(note.title) }
        var body by remember { mutableStateOf(note.body) }
        // Existing notes open as rendered Markdown; new ones open ready to type.
        var preview by remember { mutableStateOf(note.body.isNotBlank()) }
        var confirmDelete by remember { mutableStateOf(false) }

        Column(modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 16.dp)) {
            // Toolbar
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TextButton(onClick = onBack) { Text("‹ Notes") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { preview = !preview }) { Text(if (preview) "Edit" else "Preview") }
                TextButton(onClick = { vm.togglePinned(note.id) }) { Text(if (note.pinned) "★ Pinned" else "☆ Pin") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = colors.error) }
            }

            TextField(
                value = title,
                onValueChange = {
                    title = it
                    vm.edit(note.id, title, body)
                },
                placeholder = { Text("Title") },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = plainFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (preview) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (body.isBlank()) {
                        Text("Nothing written yet. Tap Edit to start.", color = colors.onSurfaceVariant)
                    } else {
                        Markdown(content = body)
                    }
                }
            } else {
                TextField(
                    value = body,
                    onValueChange = {
                        body = it
                        vm.edit(note.id, title, body)
                    },
                    placeholder = {
                        Text("Write in Markdown:\n# Heading\n**bold**, *italic*\n- list item\n- [ ] checkbox")
                    },
                    colors = plainFieldColors(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this note?") },
                text = { Text("\"${displayTitle(note.copy(title = title, body = body))}\" will be gone for good.") },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; vm.delete(note.id) }) {
                        Text("Delete", color = colors.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

/** A text field with no box or underline, so the note reads like a page. */
@Composable
private fun plainFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
