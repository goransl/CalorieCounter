package com.example.floating.caloriecounter

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.Model.WorkoutEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private data class WorkoutSetDraft(
    val weight: String,
    val reps: String,
    val rest: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    repository: FoodRepository,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val scope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    var showSearchSuggestions by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<WorkoutEntry>()) }
    var selectedEntry by remember { mutableStateOf<WorkoutEntry?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var visibleCount by remember { mutableStateOf(100) }
    var searchAnchor by remember { mutableStateOf<IntOffset?>(null) }
    var searchAnchorWidthPx by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    val searchSuggestions = remember(searchText, refreshTrigger) {
        if (searchText.trim().isNotEmpty()) {
            repository.getWorkoutNameSuggestions(searchText)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(searchText, refreshTrigger) {
        entries = repository.getWorkoutEntriesNewestFirst(searchText)
        visibleCount = min(100, entries.size)
    }

    LaunchedEffect(listState, entries) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collectLatest { lastVisible ->
                if (lastVisible >= visibleCount - 1 && visibleCount < entries.size) {
                    visibleCount = min(visibleCount + 100, entries.size)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout tracking", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchText,
                            onValueChange = {
                                searchText = it
                                showSearchSuggestions = it.length >= 2
                            },
                            label = { Text("Search exercise") },
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInWindow()
                                    searchAnchor = IntOffset(
                                        pos.x.toInt(),
                                        (pos.y + coords.size.height).toInt()
                                    )
                                    searchAnchorWidthPx = coords.size.width
                                },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                selectedEntry = null
                                showDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add workout")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add")
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(entries.take(visibleCount)) { _, entry ->
                    WorkoutEntryRow(
                        entry = entry,
                        onClick = {
                            selectedEntry = entry
                            showDialog = true
                        }
                    )
                }
            }
        }
    }

    WorkoutNameSuggestionsPopup(
        anchor = searchAnchor,
        widthPx = searchAnchorWidthPx,
        suggestions = if (showSearchSuggestions) searchSuggestions else emptyList(),
        onSelect = { suggestion ->
            searchText = suggestion
            showSearchSuggestions = false
        },
        onDelete = { suggestion ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    repository.deleteWorkoutName(suggestion)
                }
                refreshTrigger++
            }
        }
    )

    if (showDialog) {
        WorkoutEntryDialog(
            repository = repository,
            entry = selectedEntry,
            onDismiss = { showDialog = false },
            onSaved = {
                scope.launch {
                    refreshTrigger++
                    showDialog = false
                }
            },
            onDeleted = {
                scope.launch {
                    refreshTrigger++
                    showDialog = false
                }
            }
        )
    }
}

@Composable
private fun WorkoutEntryRow(
    entry: WorkoutEntry,
    onClick: () -> Unit
) {
    val date = millisToLocalDate(entry.dateMillis)
    val setsText = entry.sets.joinToString(" | ") { set ->
        formatWorkoutSet(set.weightKg, set.reps, set.rest)
    }.trim()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = entry.name, fontWeight = FontWeight.Bold)
            Text(text = formatDate(date))
            if (setsText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = setsText)
            }
            if (entry.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = entry.notes)
            }
        }
    }
}

@Composable
private fun WorkoutEntryDialog(
    repository: FoodRepository,
    entry: WorkoutEntry?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var date by remember { mutableStateOf(millisToLocalDate(entry?.dateMillis ?: nowMillis())) }
    var showNameSuggestions by remember { mutableStateOf(false) }
    var nameRefreshTrigger by remember { mutableStateOf(0) }
    var nameFieldWidthPx by remember { mutableStateOf(0) }
    val setDrafts = remember { mutableStateListOf<WorkoutSetDraft>() }
    val context = LocalContext.current

    val nameSuggestions = remember(name, nameRefreshTrigger) {
        if (name.trim().isNotEmpty()) repository.getWorkoutNameSuggestions(name) else emptyList()
    }

    LaunchedEffect(entry?.id) {
        setDrafts.clear()
        val existingSets = entry?.sets?.map {
            WorkoutSetDraft(formatWeightInput(it.weightKg), formatRepsInput(it.reps), it.rest)
        } ?: emptyList()
        if (existingSets.isNotEmpty()) {
            setDrafts.addAll(existingSets)
        } else {
            setDrafts.add(WorkoutSetDraft("", "", ""))
        }
    }

    val openDatePicker = {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = LocalDate.of(year, month + 1, dayOfMonth)
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val trimmedName = name.trimEnd()
                val trimmedNotes = notes.trimEnd()
                if (trimmedName.isEmpty()) return@Button

                val sets = setDrafts.mapNotNull { draft ->
                    val weightText = draft.weight.replace(',', '.')
                    val repsText = draft.reps.trim()
                    val restText = draft.rest.trimEnd()
                    val weightValue = weightText.toFloatOrNull()
                    val repsValue = repsText.toIntOrNull()
                    if (weightValue == null && repsValue == null && restText.isEmpty()) {
                        null
                    } else {
                        Triple(weightValue ?: 0f, repsValue ?: 0, restText)
                    }
                }

                val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                scope.launch(Dispatchers.IO) {
                    if (entry == null) {
                        repository.saveWorkoutEntry(
                            name = trimmedName,
                            dateMillis = dateMillis,
                            notes = trimmedNotes,
                            sets = sets
                        )
                    } else {
                        repository.updateWorkoutEntry(
                            id = entry.id,
                            name = trimmedName,
                            dateMillis = dateMillis,
                            notes = trimmedNotes,
                            sets = sets
                        )
                    }
                    launch(Dispatchers.Main) { onSaved() }
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                Button(onClick = onDismiss) { Text("Cancel") }
                if (entry != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            repository.deleteWorkoutEntry(entry.id)
                            launch(Dispatchers.Main) { onDeleted() }
                        }
                    }) { Text("Delete") }
                }
            }
        },
        title = { Text(if (entry == null) "Add workout" else "Edit workout") },
        text = {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showNameSuggestions = it.length >= 2
                        },
                        label = { Text("Exercise name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                nameFieldWidthPx = coords.size.width
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )
                    DropdownMenu(
                        expanded = showNameSuggestions && nameSuggestions.isNotEmpty(),
                        onDismissRequest = { showNameSuggestions = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.width(
                            with(androidx.compose.ui.platform.LocalDensity.current) {
                                nameFieldWidthPx.toDp()
                            }
                        )
                    ) {
                        nameSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        name = suggestion
                                        showNameSuggestions = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = suggestion,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                repository.deleteWorkoutName(suggestion)
                                            }
                                            nameRefreshTrigger++
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Date: ${formatDate(date)}")
                    Button(onClick = openDatePicker) { Text("Change") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                WorkoutSetsEditor(setDrafts)
            }
        }
    )

}

@Composable
private fun WorkoutSetsEditor(setDrafts: SnapshotStateList<WorkoutSetDraft>) {
    Column {
        setDrafts.forEachIndexed { index, set ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = set.weight,
                    onValueChange = { value ->
                        if (value.isBlank() || value.matches(Regex("^\\d*(?:[\\.,]\\d{0,2})?$"))) {
                            setDrafts[index] = set.copy(weight = value)
                        }
                    },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                )
                OutlinedTextField(
                    value = set.reps,
                    onValueChange = { value ->
                        if (value.isBlank() || value.matches(Regex("^\\d+\$"))) {
                            setDrafts[index] = set.copy(reps = value)
                        }
                    },
                    label = { Text("Reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = set.rest,
                    onValueChange = { value ->
                        setDrafts[index] = set.copy(rest = value)
                    },
                    label = { Text("Rest") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { setDrafts.add(WorkoutSetDraft("", "", "")) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Add set")
        }
    }
}

private fun formatWorkoutSet(weightKg: Float, reps: Int, rest: String): String {
    val weightText = if (weightKg > 0f) "${formatWeightDisplay(weightKg)}kg" else ""
    val repsText = if (reps > 0) "x $reps" else ""
    val base = listOf(weightText, repsText).filter { it.isNotBlank() }.joinToString(" ")
    val restText = rest.trim()
    return when {
        base.isNotBlank() && restText.isNotBlank() -> "$base, $restText"
        base.isNotBlank() -> base
        restText.isNotBlank() -> restText
        else -> ""
    }
}

private fun formatWeightDisplay(value: Float): String {
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun formatWeightInput(value: Float): String {
    if (value == 0f) return ""
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun formatRepsInput(value: Int): String {
    return if (value <= 0) "" else value.toString()
}

@Composable
private fun WorkoutNameSuggestionsPopup(
    anchor: IntOffset?,
    widthPx: Int,
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (anchor == null || widthPx <= 0 || suggestions.isEmpty()) return
    Popup(
        alignment = Alignment.TopStart,
        offset = anchor,
        properties = PopupProperties(focusable = false)
    ) {
        val widthDp = with(androidx.compose.ui.platform.LocalDensity.current) { widthPx.toDp() }
        Column(
            modifier = Modifier
                .width(widthDp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 4.dp)
                .defaultMinSize(minHeight = 56.dp)
        ) {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = { onDelete(suggestion) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private fun nowMillis(): Long = System.currentTimeMillis()

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatDate(date: LocalDate): String = date.format(dateFormatter)
