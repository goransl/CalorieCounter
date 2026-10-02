package com.example.floating.caloriecounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.floating.caloriecounter.Model.ExpectedPlan
import com.example.floating.caloriecounter.Model.ExpectedPlanMode
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.Model.WeightEntry
import com.example.floating.caloriecounter.ui.theme.CalorieCounterTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow

private const val WEIGHT_DATE_PAGE_SIZE = 50L

class WeightActivity : ComponentActivity() {
    private lateinit var repository: FoodRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FoodRepository()

        // Match status bar to dark background, same as MainActivity
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color(0xFF121212).toArgb()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContent {
            CalorieCounterTheme {
                val ctx = this
                var showChart by rememberSaveable { mutableStateOf(false) }

                if (showChart) {
                    BackHandler { showChart = false }
                    WeightChartScreen(
                        repository = repository,
                        onBack = { showChart = false }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .swipeToNavigate(
                                onSwipeRight = {
                                    ctx.startActivity(Intent(ctx, MainActivity::class.java))
                                    (ctx as Activity).finish()
                                }
                            )
                    ) {
                        WeightTableScreen(
                            repository = repository,
                            showBack = true,
                            onBack = { (ctx as Activity).finish() },
                            onShowChart = { showChart = true }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTableScreen(
    repository: FoodRepository,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
    onShowChart: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    dataRevision: Int = 0
) {
    var showEmbeddedChart by rememberSaveable { mutableStateOf(false) }
    if (showEmbeddedChart) {
        BackHandler { showEmbeddedChart = false }
        WeightChartScreen(
            repository = repository,
            onBack = { showEmbeddedChart = false },
            contentPadding = contentPadding,
            dataRevision = dataRevision
        )
        return
    }

    val openChart = onShowChart ?: { showEmbeddedChart = true }
    val scope = rememberCoroutineScope()
    var weights by remember { mutableStateOf(emptyList<WeightEntry>()) }
    var plan by remember { mutableStateOf<ExpectedPlan?>(null) }
    val today = remember { LocalDate.now() }
    val initialStartDate = remember(today) { today.minusDays(WEIGHT_DATE_PAGE_SIZE) }
    val initialEndDate = remember(today) { today.plusDays(WEIGHT_DATE_PAGE_SIZE) }
    var loadedStartDate by remember { mutableStateOf(initialStartDate) }
    var loadedEndDate by remember { mutableStateOf(initialEndDate) }
    var oldestTrackedDate by remember { mutableStateOf<LocalDate?>(null) }
    var newestTrackedDate by remember { mutableStateOf<LocalDate?>(null) }
    var newestTrackedWeight by remember { mutableStateOf<Float?>(null) }
    var initialScrollPending by remember { mutableStateOf(false) }

    var showWeightDialogForDate by remember { mutableStateOf<LocalDate?>(null) }
    var showExpectedDialogForDate by remember { mutableStateOf<LocalDate?>(null) }

    suspend fun refreshWeightMetadata() {
        val oldest = repository.getOldestWeight()
        val newest = repository.getNewestWeight()
        oldestTrackedDate = oldest?.let { millisToDate(it.timestamp) }
        newestTrackedDate = newest?.let { millisToDate(it.timestamp) }
        newestTrackedWeight = newest?.weightKg
    }

    suspend fun refreshLoadedWeights() {
        weights = repository.getWeightsForDateRange(loadedStartDate, loadedEndDate)
        refreshWeightMetadata()
    }

    LaunchedEffect(dataRevision) {
        loadedStartDate = initialStartDate
        loadedEndDate = initialEndDate
        weights = repository.getWeightsForDateRange(initialStartDate, initialEndDate)
        refreshWeightMetadata()
        plan = repository.getExpectedPlan()
        initialScrollPending = true
    }

    // Map date -> weight
    val weightMap: Map<LocalDate, WeightEntry> = weights.associateBy { millisToDate(it.timestamp) }


    val allDates = remember(loadedStartDate, loadedEndDate) {
        generateSequence(loadedStartDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(loadedEndDate) }
            .toList()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(initialScrollPending, allDates) {
        if (!initialScrollPending) return@LaunchedEffect
        val idx = allDates.indexOf(today)
        if (idx >= 0) {
            listState.scrollToItem(idx)
            initialScrollPending = false
        }
    }

    val nearStart by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 5 }
    }
    val nearEnd by remember(allDates) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= allDates.lastIndex - 5
        }
    }

    LaunchedEffect(nearStart, initialScrollPending, loadedStartDate, oldestTrackedDate) {
        val oldest = oldestTrackedDate ?: return@LaunchedEffect
        if (initialScrollPending || !nearStart || !oldest.isBefore(loadedStartDate)) {
            return@LaunchedEffect
        }
        val candidate = loadedStartDate.minusDays(WEIGHT_DATE_PAGE_SIZE)
        val newStart = if (candidate.isBefore(oldest)) oldest else candidate
        val additional = repository.getWeightsForDateRange(
            newStart,
            loadedStartDate.minusDays(1)
        )
        weights = (additional + weights).distinctBy { it.id }.sortedBy { it.timestamp }
        loadedStartDate = newStart
    }

    LaunchedEffect(nearEnd, initialScrollPending, loadedEndDate) {
        if (initialScrollPending || !nearEnd) {
            return@LaunchedEffect
        }
        val newEnd = loadedEndDate.plusDays(WEIGHT_DATE_PAGE_SIZE)
        val additional = repository.getWeightsForDateRange(loadedEndDate.plusDays(1), newEnd)
        weights = (weights + additional).distinctBy { it.id }.sortedBy { it.timestamp }
        loadedEndDate = newEnd
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { onBack?.invoke() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                title = { Text("Weight tracking", color = Color.White) },
                actions = {
                    TextButton(onClick = openChart) {
                        Text("Chart", color = Color.White)
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
                items(allDates, key = { date -> date.toEpochDay() }) { date ->
                    val entry = weightMap[date]
                    val planStartDate = plan?.startDateMillis?.let { millisToDate(it) }
                    val expected = expectedForDate(date, plan, planStartDate?.let { weightMap[it]?.weightKg })
                    val adjusted = adjustedExpectedForDate(
                        date = date,
                        plan = plan,
                        latestTrackedDate = newestTrackedDate,
                        latestTrackedWeight = newestTrackedWeight
                    )

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
                    scope.launch {
                        repository.addOrUpdateWeight(kg, millis)
                        refreshLoadedWeights()
                        showWeightDialogForDate = null
                    }
                },
                onDelete = { millis ->
                    scope.launch {
                        repository.deleteWeight(millis)
                        // If deleting the baseline date, also clear plan
                        val baselineDate = plan?.startDateMillis
                        if (baselineDate != null && baselineDate == millis) {
                            repository.clearExpectedPlan()
                            plan = null
                        }
                        refreshLoadedWeights()
                        showWeightDialogForDate = null
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
                existingPlan = plan,
                onDismiss = { showExpectedDialogForDate = null },
                onSavePlan = { startMillis, baseline, mode, dailyDelta, weeklyLossPercent ->
                    // Enforce: must have weight on selected date
                    if (weightOnDate == null) {
                        // You can show a snackbar/toast if you want
                        showExpectedDialogForDate = null
                        return@ExpectedDeltaDialog
                    }
                    scope.launch {
                        repository.setExpectedPlan(
                            startMillis = startMillis,
                            baseline = baseline,
                            dailyDelta = dailyDelta,
                            calculationMode = mode,
                            weeklyLossPercent = weeklyLossPercent
                        )
                        plan = repository.getExpectedPlan()
                        showExpectedDialogForDate = null
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
    existingPlan: ExpectedPlan?,
    onDismiss: () -> Unit,
    onSavePlan: (
        startMillis: Long,
        baseline: Float,
        calculationMode: String,
        dailyDelta: Float,
        weeklyLossPercent: Float
    ) -> Unit
) {
    var selectedMode by remember {
        mutableStateOf(existingPlan?.calculationMode ?: ExpectedPlanMode.DAILY_CHANGE)
    }
    var deltaText by remember {
        mutableStateOf(existingPlan?.dailyDeltaKg?.toString().orEmpty())
    }
    var weeklyLossText by remember {
        mutableStateOf(existingPlan?.weeklyLossPercent?.toString().orEmpty())
    }
    val focusRequester = remember { FocusRequester() }
    val dailyDelta = deltaText.replace(',', '.').toFloatOrNull()
    val weeklyLossPercent = weeklyLossText.replace(',', '.').toFloatOrNull()
    val validInput = when (selectedMode) {
        ExpectedPlanMode.WEEKLY_LOSS_PERCENT ->
            weeklyLossPercent != null && weeklyLossPercent > -100f
        else -> dailyDelta != null
    }

    LaunchedEffect(selectedMode) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = existingWeightOnDate != null && validInput,
                onClick = {
                    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onSavePlan(
                        millis,
                        existingWeightOnDate ?: 0f,
                        selectedMode,
                        dailyDelta ?: 0f,
                        weeklyLossPercent ?: 0f
                    )
                }
            ) { Text("Save") }
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
                TabRow(
                    selectedTabIndex = if (selectedMode == ExpectedPlanMode.DAILY_CHANGE) 0 else 1
                ) {
                    Tab(
                        selected = selectedMode == ExpectedPlanMode.DAILY_CHANGE,
                        onClick = { selectedMode = ExpectedPlanMode.DAILY_CHANGE },
                        text = { Text("Daily change") }
                    )
                    Tab(
                        selected = selectedMode == ExpectedPlanMode.WEEKLY_LOSS_PERCENT,
                        onClick = { selectedMode = ExpectedPlanMode.WEEKLY_LOSS_PERCENT },
                        text = { Text("% per week") }
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (selectedMode == ExpectedPlanMode.DAILY_CHANGE) {
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { value ->
                            if (value.isBlank() || value.matches(Regex("^[-+]?\\d*(?:[\\.,]\\d*)?$"))) {
                                deltaText = value
                            }
                        },
                        label = { Text("Daily change (kg/day)") },
                        supportingText = { Text("Example: -0.05 for a daily loss of 0.05 kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    OutlinedTextField(
                        value = weeklyLossText,
                        onValueChange = { value ->
                            if (value.isBlank() || value.matches(Regex("^[-+]?\\d*(?:[\\.,]\\d*)?$"))) {
                                weeklyLossText = value
                            }
                        },
                        label = { Text("Weekly weight change (%)") },
                        supportingText = { Text("Negative = cutting, positive = gaining. Example: -0.5") },
                        isError = weeklyLossPercent != null &&
                            weeklyLossPercent <= -100f,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }
        }
    )
}


// --- Helpers ---
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun millisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatDate(date: LocalDate): String = date.format(dateFormatter)

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
    return projectExpectedWeight(baseline, days, plan)
}

// Show adjusted only from the LATEST tracked date onward.
// Baseline = latest tracked weight; future values use the plan's selected formula.
private fun adjustedExpectedForDate(
    date: LocalDate,
    plan: ExpectedPlan?,
    latestTrackedDate: LocalDate?,
    latestTrackedWeight: Float?
): Float? {
    if (plan == null) return null
    latestTrackedDate ?: return null
    if (date.isBefore(latestTrackedDate)) return null

    val baseline = latestTrackedWeight ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(latestTrackedDate, date).toFloat()
    return projectExpectedWeight(baseline, days, plan)
}

private fun projectExpectedWeight(
    baselineWeight: Float,
    days: Float,
    plan: ExpectedPlan
): Float = when (plan.calculationMode) {
    ExpectedPlanMode.WEEKLY_LOSS_PERCENT -> {
        val weeklyMultiplier = 1.0 + plan.weeklyLossPercent.toDouble() / 100.0
        (baselineWeight.toDouble() * weeklyMultiplier.pow(days.toDouble() / 7.0)).toFloat()
    }
    else -> baselineWeight + plan.dailyDeltaKg * days
}
