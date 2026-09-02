package com.example.floating.caloriecounter

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.Model.WorkoutDaySummary
import com.example.floating.caloriecounter.Model.WorkoutEntrySnapshot
import com.example.floating.caloriecounter.Model.WorkoutSetSnapshot
import com.example.floating.caloriecounter.Model.WorkoutSetInput
import com.example.floating.caloriecounter.Model.parseRestSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class WorkoutView(val title: String) {
    Day("Day"),
    History("History")
}

private data class WorkoutSetDraft(
    val weight: String = "",
    val reps: String = "",
    val rest: String = "",
    val notes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    repository: FoodRepository,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    dataRevision: Int = 0,
    isVisible: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedView by remember { mutableStateOf(WorkoutView.Day) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dayEntries by remember { mutableStateOf(emptyList<WorkoutEntrySnapshot>()) }
    var historyEntries by remember { mutableStateOf(emptyList<WorkoutEntrySnapshot>()) }
    var searchText by remember { mutableStateOf("") }
    var showSearchSuggestions by remember { mutableStateOf(false) }
    var historyProgressExerciseName by remember { mutableStateOf<String?>(null) }
    var progressExerciseName by remember { mutableStateOf<String?>(null) }
    var progressEntries by remember { mutableStateOf(emptyList<WorkoutEntrySnapshot>()) }
    var selectedEntry by remember { mutableStateOf<WorkoutEntrySnapshot?>(null) }
    var dialogInitialDate by remember { mutableStateOf(LocalDate.now()) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var recentDays by remember { mutableStateOf(emptyList<WorkoutDaySummary>()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val selectedDateMillis = selectedDate.toStartOfDayMillis()
    val searchSuggestions = remember(searchText, refreshTrigger, dataRevision) {
        if (searchText.trim().length >= 2) {
            repository.getWorkoutNameSuggestions(searchText)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(selectedDateMillis, refreshTrigger, dataRevision) {
        dayEntries = repository.getWorkoutEntriesForDate(selectedDateMillis)
    }

    LaunchedEffect(searchText, refreshTrigger, dataRevision) {
        historyEntries = repository.getWorkoutEntriesNewestFirst(
            searchQuery = searchText,
            completedOnly = true
        )
    }

    LaunchedEffect(progressExerciseName, refreshTrigger, dataRevision) {
        progressEntries = progressExerciseName?.let { exerciseName ->
            repository.getCompletedWorkoutEntriesForExercise(exerciseName)
        }.orEmpty()
    }

    BackHandler(
        enabled = isVisible && progressExerciseName != null &&
            !showEntryDialog && !showCopyDialog
    ) {
        progressExerciseName = null
    }

    val openExerciseProgress: (String) -> Unit = { exerciseName ->
        val normalizedName = exerciseName.trim()
        if (normalizedName.isNotEmpty()) {
            progressEntries = repository.getCompletedWorkoutEntriesForExercise(normalizedName)
            progressExerciseName = normalizedName
        }
    }

    val openDayDatePicker = {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (progressExerciseName == null) "Workout" else "Exercise progress",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (progressExerciseName != null) {
                        IconButton(onClick = { progressExerciseName = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to workout",
                                tint = Color.White
                            )
                        }
                    }
                },
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
            val currentProgressExercise = progressExerciseName
            if (currentProgressExercise != null) {
                WorkoutProgressView(
                    exerciseName = currentProgressExercise,
                    entries = progressEntries,
                    onEdit = { entry ->
                        selectedEntry = entry
                        dialogInitialDate = millisToLocalDate(entry.dateMillis)
                        showEntryDialog = true
                    }
                )
            } else {
                TabRow(selectedTabIndex = selectedView.ordinal) {
                    WorkoutView.entries.forEach { view ->
                        Tab(
                            selected = selectedView == view,
                            onClick = { selectedView = view },
                            text = { Text(view.title) }
                        )
                    }
                }

                when (selectedView) {
                WorkoutView.Day -> WorkoutDayView(
                    selectedDate = selectedDate,
                    entries = dayEntries,
                    onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                    onNextDay = { selectedDate = selectedDate.plusDays(1) },
                    onToday = { selectedDate = LocalDate.now() },
                    onChooseDate = openDayDatePicker,
                    onAddExercise = {
                        selectedEntry = null
                        dialogInitialDate = selectedDate
                        showEntryDialog = true
                    },
                    onCopyWorkout = {
                        recentDays = repository.getRecentWorkoutDays(selectedDateMillis)
                        showCopyDialog = true
                    },
                    onEdit = { entry ->
                        selectedEntry = entry
                        dialogInitialDate = millisToLocalDate(entry.dateMillis)
                        showEntryDialog = true
                    },
                    onViewProgress = { entry -> openExerciseProgress(entry.name) },
                    onToggleCompleted = { entry, completed ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.setWorkoutEntryCompleted(entry.id, completed)
                            }
                            refreshTrigger++
                        }
                    },
                    onReorder = { orderedIds ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.reorderWorkoutEntries(selectedDateMillis, orderedIds)
                            }
                            refreshTrigger++
                        }
                    }
                )

                WorkoutView.History -> WorkoutHistoryView(
                    searchText = searchText,
                    onSearchTextChange = {
                        searchText = it
                        historyProgressExerciseName = null
                        showSearchSuggestions = it.trim().length >= 2
                    },
                    suggestions = if (showSearchSuggestions) searchSuggestions else emptyList(),
                    entries = historyEntries,
                    onSuggestionsDismissed = { showSearchSuggestions = false },
                    onSuggestionSelected = {
                        searchText = it
                        historyProgressExerciseName = it
                        showSearchSuggestions = false
                    },
                    onSuggestionDeleted = { name ->
                        if (historyProgressExerciseName == name) {
                            historyProgressExerciseName = null
                        }
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.deleteWorkoutName(name) }
                            refreshTrigger++
                        }
                    },
                    onEdit = { entry ->
                        selectedEntry = entry
                        dialogInitialDate = millisToLocalDate(entry.dateMillis)
                        showEntryDialog = true
                    },
                    progressExerciseName = historyProgressExerciseName,
                    onViewProgress = openExerciseProgress
                )
            }
            }
        }
    }

    if (showEntryDialog) {
        WorkoutEntryDialog(
            repository = repository,
            entry = selectedEntry,
            initialDate = dialogInitialDate,
            onDismiss = {
                selectedEntry = null
                showEntryDialog = false
            },
            onSaved = { savedName ->
                selectedEntry?.name?.let { previousName ->
                    if (progressExerciseName == previousName) {
                        openExerciseProgress(savedName)
                    }
                    if (historyProgressExerciseName == previousName) {
                        historyProgressExerciseName = savedName
                        searchText = savedName
                    }
                }
                refreshTrigger++
                selectedEntry = null
                showEntryDialog = false
            },
            onDeleted = {
                selectedEntry?.id?.let { deletedId ->
                    dayEntries = dayEntries.filterNot { it.id == deletedId }
                    historyEntries = historyEntries.filterNot { it.id == deletedId }
                    progressEntries = progressEntries.filterNot { it.id == deletedId }
                }
                refreshTrigger++
                selectedEntry = null
                showEntryDialog = false
            },
            onViewProgress = { exerciseName ->
                selectedEntry = null
                showEntryDialog = false
                openExerciseProgress(exerciseName)
            }
        )
    }

    if (showCopyDialog) {
        CopyWorkoutDialog(
            repository = repository,
            targetDate = selectedDate,
            targetHasEntries = dayEntries.isNotEmpty(),
            recentDays = recentDays,
            onDismiss = { showCopyDialog = false },
            onCopied = { count ->
                refreshTrigger++
                showCopyDialog = false
                Toast.makeText(context, "$count exercises copied.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun WorkoutDayView(
    selectedDate: LocalDate,
    entries: List<WorkoutEntrySnapshot>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onChooseDate: () -> Unit,
    onAddExercise: () -> Unit,
    onCopyWorkout: () -> Unit,
    onEdit: (WorkoutEntrySnapshot) -> Unit,
    onViewProgress: (WorkoutEntrySnapshot) -> Unit,
    onToggleCompleted: (WorkoutEntrySnapshot, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    var orderedEntries by remember(entries) { mutableStateOf(entries) }
    var draggingEntryId by remember { mutableStateOf<String?>(null) }
    var initialDraggedOffset by remember { mutableFloatStateOf(0f) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    var movedDuringDrag by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousDay) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
            }
            TextButton(onClick = onChooseDate) {
                Text(
                    text = if (selectedDate == LocalDate.now()) {
                        "${formatDate(selectedDate)} · Today"
                    } else {
                        formatDate(selectedDate)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onNextDay) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onToday) {
                Text("Today")
            }
            OutlinedButton(onClick = onCopyWorkout, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            Button(onClick = onAddExercise) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (orderedEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No exercises for this day.\nAdd one or copy a previous workout.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(orderedEntries, key = { entry -> entry.id }) { entry ->
                    val draggedItemInfo = if (draggingEntryId == entry.id) {
                        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }
                    } else {
                        null
                    }
                    val dragTranslationY = draggedItemInfo?.let { info ->
                        initialDraggedOffset + draggedDistance - info.offset
                    } ?: 0f

                    WorkoutDayEntryCard(
                        entry = entry,
                        isDragging = draggingEntryId == entry.id,
                        dragTranslationY = dragTranslationY,
                        onClick = { onEdit(entry) },
                        onViewProgress = { onViewProgress(entry) },
                        onCompletedChange = { completed ->
                            orderedEntries = orderedEntries.map { item ->
                                if (item.id == entry.id) item.copy(completed = completed) else item
                            }
                            onToggleCompleted(entry, completed)
                        },
                        onDragStart = {
                            listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == entry.id }
                                ?.let { itemInfo ->
                                    draggingEntryId = entry.id
                                    initialDraggedOffset = itemInfo.offset.toFloat()
                                    draggedDistance = 0f
                                    movedDuringDrag = false
                                }
                        },
                        onDrag = { deltaY ->
                            if (draggingEntryId == entry.id) {
                                draggedDistance += deltaY
                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                val currentInfo = visibleItems.firstOrNull { it.key == entry.id }
                                val from = orderedEntries.indexOfFirst { it.id == entry.id }
                                if (currentInfo != null && from >= 0) {
                                    val draggedCenter = initialDraggedOffset + draggedDistance +
                                        currentInfo.size / 2f
                                    val direction = if (deltaY > 0f) 1 else -1
                                    val to = from + direction
                                    val neighbour = orderedEntries.getOrNull(to)
                                    val neighbourInfo = neighbour?.let { candidate ->
                                        visibleItems.firstOrNull { it.key == candidate.id }
                                    }
                                    val crossedNeighbour = neighbourInfo?.let { info ->
                                        val neighbourCenter = info.offset + info.size / 2f
                                        if (direction > 0) {
                                            draggedCenter > neighbourCenter
                                        } else {
                                            draggedCenter < neighbourCenter
                                        }
                                    } ?: false

                                    if (crossedNeighbour) {
                                        orderedEntries = orderedEntries.toMutableList().apply {
                                            val moved = removeAt(from)
                                            add(to, moved)
                                        }
                                        movedDuringDrag = true
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val reorderedIds = orderedEntries.map { it.id }
                            val shouldPersist = movedDuringDrag
                            draggingEntryId = null
                            initialDraggedOffset = 0f
                            draggedDistance = 0f
                            movedDuringDrag = false
                            if (shouldPersist) onReorder(reorderedIds)
                        },
                        onDragCancel = {
                            draggingEntryId = null
                            initialDraggedOffset = 0f
                            draggedDistance = 0f
                            movedDuringDrag = false
                            orderedEntries = entries
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutDayEntryCard(
    entry: WorkoutEntrySnapshot,
    isDragging: Boolean,
    dragTranslationY: Float,
    onClick: () -> Unit,
    onViewProgress: () -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .then(
                if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragTranslationY }
                } else {
                    Modifier
                }
            )
            .pointerInput(entry.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnDragStart()
                    },
                    onDragEnd = currentOnDragEnd,
                    onDragCancel = currentOnDragCancel,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount.y)
                    }
                )
            }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.completed) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = entry.completed,
                onCheckedChange = onCompletedChange
            )
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (entry.supersetGroupId.isNotBlank()) {
                        Text(
                            text = "Superset ${entry.supersetGroupId}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                WorkoutSetsSummary(entry.sets)
                if (entry.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onViewProgress) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = "View ${entry.name} progress",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Long press and drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
internal fun WorkoutSetsSummary(sets: List<WorkoutSetSnapshot>) {
    if (sets.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    sets.forEachIndexed { index, set ->
        val details = formatWorkoutSet(set)
        Text(
            text = buildString {
                append("Set ${index + 1}")
                if (details.isNotBlank()) append(": $details")
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WorkoutHistoryView(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    suggestions: List<String>,
    entries: List<WorkoutEntrySnapshot>,
    onSuggestionsDismissed: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onSuggestionDeleted: (String) -> Unit,
    onEdit: (WorkoutEntrySnapshot) -> Unit,
    progressExerciseName: String?,
    onViewProgress: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            TextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                label = { Text("Search completed exercise") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = suggestions.isNotEmpty(),
                onDismissRequest = onSuggestionsDismissed,
                properties = PopupProperties(focusable = false),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionSelected(suggestion) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(suggestion, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onSuggestionDeleted(suggestion) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete suggestion",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        if (
            progressExerciseName != null &&
            entries.any { it.name == progressExerciseName }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clickable { onViewProgress(progressExerciseName) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "View progress",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            progressExerciseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No completed exercises found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .clickable { onEdit(entry) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entry.name,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(formatDate(millisToLocalDate(entry.dateMillis)))
                            }
                            if (entry.supersetGroupId.isNotBlank()) {
                                Text(
                                    "Superset ${entry.supersetGroupId}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            WorkoutSetsSummary(entry.sets)
                            if (entry.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutEntryDialog(
    repository: FoodRepository,
    entry: WorkoutEntrySnapshot?,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    onViewProgress: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember(entry?.id) { mutableStateOf(entry?.name ?: "") }
    var notes by remember(entry?.id) { mutableStateOf(entry?.notes ?: "") }
    var date by remember(entry?.id, initialDate) {
        mutableStateOf(entry?.let { millisToLocalDate(it.dateMillis) } ?: initialDate)
    }
    var completed by remember(entry?.id) { mutableStateOf(entry?.completed ?: true) }
    var supersetGroupId by remember(entry?.id) {
        mutableStateOf(entry?.supersetGroupId ?: "")
    }
    var showNameSuggestions by remember { mutableStateOf(false) }
    var showMoreOptions by remember(entry?.id) { mutableStateOf(false) }
    var nameRefreshTrigger by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val setDrafts = remember(entry?.id) {
        mutableStateListOf<WorkoutSetDraft>().apply {
            val existing = entry?.sets?.map { set ->
                WorkoutSetDraft(
                    weight = formatWeightInput(set.weightKg),
                    reps = formatRepsInput(set.reps),
                    rest = set.rest.ifBlank {
                        if (set.restSeconds > 0) set.restSeconds.toString() else ""
                    },
                    notes = set.notes
                )
            }.orEmpty()
            if (existing.isEmpty()) add(WorkoutSetDraft()) else addAll(existing)
        }
    }

    val nameSuggestions = remember(name, nameRefreshTrigger) {
        if (name.trim().length >= 2) repository.getWorkoutNameSuggestions(name) else emptyList()
    }
    val canViewProgress = remember(entry?.id) {
        entry?.name?.let(repository::hasCompletedWorkoutHistory) == true
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
        onDismissRequest = { if (!isSaving) onDismiss() },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Exercise name is required."
                        return@Button
                    }
                    val inputs = setDrafts.map { draft ->
                        val rest = draft.rest.trimEnd()
                        WorkoutSetInput(
                            weightKg = draft.weight.replace(',', '.').toFloatOrNull() ?: 0f,
                            reps = draft.reps.toIntOrNull() ?: 0,
                            rest = rest,
                            restSeconds = parseRestSeconds(rest),
                            notes = draft.notes.trimEnd()
                        )
                    }
                    val dateMillis = date.toStartOfDayMillis()
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                if (entry == null) {
                                    repository.saveWorkoutEntry(
                                        name = trimmedName,
                                        dateMillis = dateMillis,
                                        notes = notes,
                                        completed = completed,
                                        supersetGroupId = supersetGroupId,
                                        sets = inputs
                                    )
                                } else {
                                    repository.updateWorkoutEntry(
                                        id = entry.id,
                                        name = trimmedName,
                                        dateMillis = dateMillis,
                                        notes = notes,
                                        completed = completed,
                                        supersetGroupId = supersetGroupId,
                                        sets = inputs
                                    )
                                }
                            }
                            onSaved(trimmedName)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            errorMessage = error.message ?: "Exercise could not be saved."
                            isSaving = false
                        }
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                if (entry != null) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.deleteWorkoutEntry(entry.id)
                                    }
                                    onDeleted()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Throwable) {
                                    errorMessage = error.message ?: "Exercise could not be deleted."
                                    isSaving = false
                                }
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        title = { Text(if (entry == null) "Add exercise" else "Edit exercise") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showNameSuggestions = it.trim().length >= 2
                        },
                        label = { Text("Exercise name") },
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        nameSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        name = suggestion
                                        if (entry == null) {
                                            repository.getLatestWorkoutEntryByName(suggestion)?.let { latest ->
                                                notes = latest.notes
                                                supersetGroupId = latest.supersetGroupId
                                                val previousSets = latest.sets.map { set ->
                                                    WorkoutSetDraft(
                                                        weight = formatWeightInput(set.weightKg),
                                                        reps = formatRepsInput(set.reps),
                                                        rest = set.rest.ifBlank {
                                                            if (set.restSeconds > 0) {
                                                                set.restSeconds.toString()
                                                            } else {
                                                                ""
                                                            }
                                                        },
                                                        notes = set.notes
                                                    )
                                                }
                                                setDrafts.clear()
                                                if (previousSets.isEmpty()) {
                                                    setDrafts.add(WorkoutSetDraft())
                                                } else {
                                                    setDrafts.addAll(previousSets)
                                                }
                                            }
                                        }
                                        showNameSuggestions = false
                                    }
                                    .padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(suggestion, modifier = Modifier.weight(1f))
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
                                        Icons.Default.Delete,
                                        contentDescription = "Delete suggestion",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showMoreOptions = !showMoreOptions },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("More options", modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (showMoreOptions) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (showMoreOptions) {
                            "Collapse more options"
                        } else {
                            "Expand more options"
                        }
                    )
                }

                if (showMoreOptions) {
                    Spacer(Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Date: ${formatDate(date)}")
                            TextButton(onClick = openDatePicker, enabled = !isSaving) {
                                Text("Change")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = completed, onCheckedChange = { completed = it })
                            Text("Completed (include in History)")
                        }

                        OutlinedTextField(
                            value = supersetGroupId,
                            onValueChange = { supersetGroupId = it.take(20) },
                            label = { Text("Superset group (e.g. A)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (entry != null && canViewProgress) {
                            TextButton(
                                onClick = { onViewProgress(entry.name) },
                                enabled = !isSaving,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Progress")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Exercise notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text("Sets", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                WorkoutSetsEditor(setDrafts)
            }
        }
    )
}

@Composable
private fun WorkoutSetsEditor(setDrafts: SnapshotStateList<WorkoutSetDraft>) {
    Column {
        setDrafts.forEachIndexed { index, set ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Set ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { setDrafts.removeAt(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete set",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = set.weight,
                            onValueChange = { value ->
                                if (value.isBlank() || value.matches(Regex("^\\d*(?:[\\.,]\\d{0,2})?$"))) {
                                    setDrafts[index] = setDrafts[index].copy(weight = value)
                                }
                            },
                            label = { Text("Weight kg") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            )
                        )
                        OutlinedTextField(
                            value = set.reps,
                            onValueChange = { value ->
                                if (value.isBlank() || value.matches(Regex("^\\d+$"))) {
                                    setDrafts[index] = setDrafts[index].copy(reps = value)
                                }
                            },
                            label = { Text("Reps") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = setDrafts[index].rest,
                        onValueChange = { value ->
                            setDrafts[index] = setDrafts[index].copy(rest = value)
                        },
                        label = { Text("Rest (90, 1:30 or 2 min)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = setDrafts[index].notes,
                        onValueChange = { value ->
                            setDrafts[index] = setDrafts[index].copy(notes = value)
                        },
                        label = { Text("Set notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                setDrafts.add(setDrafts.lastOrNull()?.copy() ?: WorkoutSetDraft())
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add set")
        }
    }
}

@Composable
private fun CopyWorkoutDialog(
    repository: FoodRepository,
    targetDate: LocalDate,
    targetHasEntries: Boolean,
    recentDays: List<WorkoutDaySummary>,
    onDismiss: () -> Unit,
    onCopied: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var includeDetails by remember { mutableStateOf(true) }
    var replaceExisting by remember { mutableStateOf(false) }
    var copyingDateMillis by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isCopying = copyingDateMillis != null

    AlertDialog(
        onDismissRequest = { if (!isCopying) onDismiss() },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isCopying) { Text("Close") }
        },
        title = { Text("Copy previous workout") },
        text = {
            Column {
                Text("Target: ${formatDate(targetDate)}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeDetails,
                        onCheckedChange = { includeDetails = it },
                        enabled = !isCopying
                    )
                    Text("Copy weights, reps, rest and notes")
                }
                if (targetHasEntries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = replaceExisting,
                            onCheckedChange = { replaceExisting = it },
                            enabled = !isCopying
                        )
                        Text("Replace exercises already on target day")
                    }
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (recentDays.isEmpty()) {
                    Text("No previous workout days found.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(recentDays, key = { it.dateMillis }) { day ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = !isCopying) {
                                        copyingDateMillis = day.dateMillis
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val count = withContext(Dispatchers.IO) {
                                                    repository.copyWorkoutDay(
                                                        sourceDateMillis = day.dateMillis,
                                                        targetDateMillis = targetDate.toStartOfDayMillis(),
                                                        includeDetails = includeDetails,
                                                        replaceExisting = replaceExisting
                                                    )
                                                }
                                                onCopied(count)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Throwable) {
                                                errorMessage = error.message ?: "Workout could not be copied."
                                                copyingDateMillis = null
                                            }
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            formatDate(millisToLocalDate(day.dateMillis)),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (copyingDateMillis == day.dateMillis) {
                                            CircularProgressIndicator(
                                                Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                    Text(
                                        day.exerciseNames.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

internal fun formatWorkoutSet(set: WorkoutSetSnapshot): String {
    val parts = mutableListOf<String>()
    if (set.weightKg > 0f) parts += "${formatWeightDisplay(set.weightKg)} kg"
    if (set.reps > 0) parts += "× ${set.reps}"
    val restText = set.rest.trim().ifBlank {
        if (set.restSeconds > 0) "${set.restSeconds}s" else ""
    }
    if (restText.isNotBlank()) parts += "rest $restText"
    if (set.notes.isNotBlank()) parts += set.notes
    return parts.joinToString(" · ")
}

internal fun formatWeightDisplay(value: Float): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun formatWeightInput(value: Float): String =
    if (value == 0f) "" else formatWeightDisplay(value)

private fun formatRepsInput(value: Int): String = if (value <= 0) "" else value.toString()

private fun LocalDate.toStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private val workoutDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun formatDate(date: LocalDate): String = date.format(workoutDateFormatter)
