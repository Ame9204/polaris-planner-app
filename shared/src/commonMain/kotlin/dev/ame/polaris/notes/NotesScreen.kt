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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.ame.polaris.ui.EmptyState
import dev.ame.polaris.ui.ItemGap
import dev.ame.polaris.ui.LocalUndo
import dev.ame.polaris.ui.PageHeader
import dev.ame.polaris.ui.PagePadding
import dev.ame.polaris.ui.PolarisIcons
import dev.ame.polaris.ui.QuietIconButton
import dev.ame.polaris.ui.ReadableWidth
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.mikepenz.markdown.m3.Markdown
import dev.ame.polaris.util.today
import kotlinx.datetime.LocalDate
import dev.ame.polaris.ui.GoldButton
import dev.ame.polaris.ui.PolarisCard

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
    Column(modifier.padding(PagePadding)) {
        PageHeader("Notes", subtitle = if (state.notes.isEmpty()) null else "${state.notes.size} notes") {
            GoldButton(onClick = vm::create) {
                Icon(PolarisIcons.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("New")
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search notes") },
            leadingIcon = { Icon(PolarisIcons.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                { QuietIconButton(PolarisIcons.Close, "Clear search", onClick = { vm.setQuery("") }) }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (state.notes.isEmpty()) {
            if (state.query.isBlank()) {
                EmptyState(PolarisIcons.Notes, "No notes yet", "Tap New to write one. Notes use Markdown: # headings, **bold**, - lists.")
            } else {
                EmptyState(PolarisIcons.Search, "Nothing found", "No notes contain \"${state.query.trim()}\".")
            }
        }

        LazyColumn(verticalArrangement = ItemGap) {
            items(state.notes, key = { it.id }) { note ->
                NoteRow(
                    note = note,
                    today = today,
                    isSelected = note.id == state.selectedId,
                    onClick = { vm.select(note.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, today: LocalDate, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    PolarisCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        highlighted = isSelected,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) {
                    Icon(PolarisIcons.Pin, contentDescription = "Pinned", tint = colors.primary, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                }
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
        val undo = LocalUndo.current

        Column(modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 16.dp).widthIn(max = ReadableWidth)) {
            // Toolbar
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(PolarisIcons.Back, contentDescription = "Back to notes") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { preview = !preview }) {
                    Icon(
                        if (preview) PolarisIcons.Edit else PolarisIcons.Preview,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(if (preview) "Edit" else "Preview")
                }
                IconButton(onClick = { vm.togglePinned(note.id) }) {
                    Icon(
                        PolarisIcons.Pin,
                        contentDescription = if (note.pinned) "Unpin" else "Pin",
                        tint = if (note.pinned) colors.primary else colors.onSurfaceVariant,
                    )
                }
                QuietIconButton(PolarisIcons.Delete, "Delete note", onClick = {
                    // Keep what's typed so far, so Undo brings back the latest text.
                    val deleted = note.copy(title = title, body = body)
                    vm.delete(note.id)
                    undo.offer("Note deleted") { vm.restore(deleted) }
                })
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
