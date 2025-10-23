package com.example.floating.caloriecounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.floating.caloriecounter.Model.ExpectedPlan
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.Model.WeightEntry
import com.example.floating.caloriecounter.ui.theme.CalorieCounterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class WeightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Match status bar to dark background, same as MainActivity
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color(0xFF121212).toArgb()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContent {
            CalorieCounterTheme {
                val ctx = this
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .swipeToNavigate(
                            onSwipeRight = {
                                ctx.startActivity(Intent(ctx, MainActivity::class.java))
                                (ctx as Activity).finish() // optional: close WeightActivity
                            }
                        )
                ) {
                    WeightTableScreen(repository = FoodRepository())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTableScreen(repository: FoodRepository) {
    val scope = rememberCoroutineScope()
    var weights by remember { mutableStateOf(emptyList<WeightEntry>()) }
    var plan by remember { mutableStateOf<ExpectedPlan?>(null) }

    var showWeightDialogForDate by remember { mutableStateOf<LocalDate?>(null) }
    var showExpectedDialogForDate by remember { mutableStateOf<LocalDate?>(null) }

    // Load data
    LaunchedEffect(Unit) {
        weights = repository.getAllWeightsAscending()
        plan = repository.getExpectedPlan()
    }

    // Map date -> weight
    val weightMap: Map<LocalDate, WeightEntry> = weights.associateBy { millisToDate(it.timestamp) }


    val firstDate = (weightMap.keys.minOrNull() ?: LocalDate.now())
    val today = LocalDate.now()
    val endDate = today.plusMonths(12)
    val allDates = generateSequence(firstDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .toList()

    val listState = rememberLazyListState()
    LaunchedEffect(allDates) {
        val idx = allDates.indexOf(today)
        if (idx >= 0) listState.scrollToItem(idx)
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = { Text("Weight tracking", color = Color.White) },
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
        ) {
            // Header row: 3 columns
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Date", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("Weight (kg)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Expected (kg)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Adj. expected (kg)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            Divider()

            LazyColumn(state = listState) {
                items(allDates.size) { index ->
                    val date = allDates[index]
                    val entry = weightMap[date]
                    val planStartDate = plan?.startDateMillis?.let { millisToDate(it) }
                    val expected = expectedForDate(date, plan, planStartDate?.let { weightMap[it]?.weightKg })
                    val adjusted = adjustedExpectedForDate(date, plan, weightMap)

                    // Highlight today's date
                    val isToday = date == LocalDate.now()
                    val bgColor = if (isToday) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date column
                        Text(formatDate(date), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)

                        // Weight column
                        Text(
                            entry?.weightKg?.toString() ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showWeightDialogForDate = date }, textAlign = TextAlign.Center
                        )

                        // Expected column
                        Text(
                            expected?.let { formatDecimal(it) } ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showExpectedDialogForDate = date }, textAlign = TextAlign.Center
                        )

                        Text(
                            adjusted?.let { formatDecimal(it) } ?: "",
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End
                        )
                    }
                    Divider()
                }
            }
        }

        // Weight add/replace dialog (existing from previous step)
        if (showWeightDialogForDate != null) {
            val date = showWeightDialogForDate!!
            val existing = weightMap[date]?.weightKg
            AddWeightDialog(
                date = date,
                existingWeight = existing,
                onDismiss = { showWeightDialogForDate = null },
                onSave = { kg, millis ->
                    scope.launch(Dispatchers.IO) {
                        repository.addOrUpdateWeight(kg, millis)
                        launch {
                            weights = repository.getAllWeightsAscending()
                            showWeightDialogForDate = null
                        }
                    }
                },
                onDelete = { millis ->
                    scope.launch(Dispatchers.IO) {
                        repository.deleteWeight(millis)
                        // If deleting the baseline date, also clear plan
                        val baselineDate = plan?.startDateMillis
                        if (baselineDate != null && baselineDate == millis) {
                            repository.clearExpectedPlan()
                            plan = null
                        }
                        launch {
                            weights = repository.getAllWeightsAscending()
                            showWeightDialogForDate = null
                        }
                    }
                }
            )
        }

        // Expected plan dialog
        if (showExpectedDialogForDate != null) {
            val date = showExpectedDialogForDate!!
            val weightOnDate = weightMap[date]?.weightKg

            ExpectedDeltaDialog(
                date = date,
                existingWeightOnDate = weightOnDate,
                onDismiss = { showExpectedDialogForDate = null },
                onSavePlan = { startMillis, baseline, dailyDelta ->
                    // Enforce: must have weight on selected date
                    if (weightOnDate == null) {
                        // You can show a snackbar/toast if you want
                        showExpectedDialogForDate = null
                        return@ExpectedDeltaDialog
                    }
                    scope.launch(Dispatchers.IO) {
                        repository.setExpectedPlan(startMillis, baseline, dailyDelta) // replaces any previous plan
                        launch {
                            plan = repository.getExpectedPlan()
                            showExpectedDialogForDate = null
                        }
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWeightDialog(
    date: LocalDate,
    existingWeight: Float?,
    onDismiss: () -> Unit,
    onSave: (Float, Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var weightText by remember { mutableStateOf(existingWeight?.toString() ?: "") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus() // auto-focus input → shows keyboard
    }

    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val kg = weightText.replace(',', '.').toFloatOrNull() ?: 0f
                onSave(kg, millis) // replace if exists, add if new
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                Button(onClick = onDismiss) { Text("Cancel") }
                if (existingWeight != null) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onDelete(millis) }) { Text("Delete") }
                }
            }
        },
        title = { Text("Weight for ${formatDate(date)}") },
        text = {
            OutlinedTextField(
                value = weightText,
                onValueChange = { v ->
                    if (v.isBlank() || v.matches(Regex("^\\d*(?:[\\.,]\\d*)?$"))) {
                        weightText = v
                    }
                },
                label = { Text("Weight (kg)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number
                )
            )
        }
    )
}

@Composable
fun ExpectedDeltaDialog(
    date: LocalDate,
    existingWeightOnDate: Float?,
    onDismiss: () -> Unit,
    onSavePlan: (startMillis: Long, baseline: Float, dailyDelta: Float) -> Unit
) {
    // Must have a baseline weight
    var deltaText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val dailyDelta = deltaText.replace(',', '.').toFloatOrNull() ?: 0f
                val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val baseline = existingWeightOnDate ?: 0f
                onSavePlan(millis, baseline, dailyDelta)
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Expected weight from ${formatDate(date)}") },
        text = {
            Column {
                if (existingWeightOnDate == null) {
                    Text(
                        "No tracked weight on this date. Add a weight first (tap the weight column for this date).",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { v ->
                        if (v.isBlank() || v.matches(Regex("^[-+]?\\d*(?:[\\.,]\\d*)?$"))) deltaText = v
                    },
                    label = { Text("Daily change (kg/day, e.g. 0.05 or -0.05)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }
    )
}


// --- Helpers ---
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun millisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatDate(date: LocalDate): String = date.format(dateFormatter)

private fun daysBetweenInclusive(start: LocalDate, end: LocalDate): Long =
    ChronoUnit.DAYS.between(start, end)

private fun expectedForDate(
    date: LocalDate,
    plan: ExpectedPlan?,
    baselineWeight: Float?
): Float? {
    if (plan == null) return null
    val startDate = millisToDate(plan.startDateMillis)
    if (date.isBefore(startDate)) return null
    val baseline = baselineWeight ?: plan.baselineWeightKg
    val days = ChronoUnit.DAYS.between(startDate, date).toFloat()
    return baseline + plan.dailyDeltaKg * days
}

private fun latestTrackedOnOrBefore(
    date: LocalDate,
    weightMap: Map<LocalDate, com.example.floating.caloriecounter.Model.WeightEntry>
): Pair<LocalDate, Float>? {
    val key = weightMap.keys.filter { !it.isAfter(date) }.maxOrNull() ?: return null
    return key to (weightMap[key]?.weightKg ?: return null)
}

// Show adjusted only from the LATEST tracked date onward.
// Baseline = latest tracked weight; for future days: baseline + delta * days.
private fun adjustedExpectedForDate(
    date: LocalDate,
    plan: ExpectedPlan?,
    weightMap: Map<LocalDate, WeightEntry>
): Float? {
    if (plan == null) return null
    // find latest tracked date overall
    val latestTrackedDate = weightMap.keys.maxOrNull() ?: return null
    if (date.isBefore(latestTrackedDate)) return null

    val baseline = weightMap[latestTrackedDate]?.weightKg ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(latestTrackedDate, date).toFloat()
    return baseline + plan.dailyDeltaKg * days
}
