package com.example.floating.caloriecounter

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.floating.caloriecounter.Model.Food
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.Model.Totals
import com.example.floating.caloriecounter.ui.theme.CalorieCounterTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import createBackupZip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt


data class OffNutrition(
    val name: String = "",
    val kcal100: Float = 0f,
    val protein100: Float = 0f,
    val fat100: Float = 0f,
    val carbs100: Float = 0f
)

private val http by lazy { OkHttpClient() }

suspend fun fetchOpenFoodFacts(barcode: String): OffNutrition? = withContext(Dispatchers.IO) {
    try {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null

            val root = JSONObject(body)
            if (root.optString("status_verbose") != "product found") return@withContext null
            val p = root.optJSONObject("product") ?: return@withContext null
            val nutr = p.optJSONObject("nutriments") ?: JSONObject()

            val name = p.optString("product_name", p.optString("generic_name", ""))

            fun f(key: String): Float =
                nutr.optString(key, "").replace(',', '.').toFloatOrNull() ?: 0f

            val kcal = f("energy-kcal_100g").takeIf { it > 0f }
                ?: (f("energy_100g") / 4.184f) // kJ → kcal fallback

            OffNutrition(
                name = name,
                kcal100 = kcal,
                protein100 = f("proteins_100g"),
                fat100 = f("fat_100g"),
                carbs100 = f("carbohydrates_100g")
            )
        }
    } catch (_: Exception) {
        null
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CalorieCounterTheme {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color(0xFF121212).toArgb() // Match dark background color
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

                // Wrap the whole app surface
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

private enum class MainTab(val label: String) {
    Calories("Calories"),
    Weight("Weight"),
    Workout("Workout")
}

@Composable
fun MainScreen(repository: FoodRepository = FoodRepository()) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Calories) }
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.primary
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Calories,
                    onClick = { selectedTab = MainTab.Calories },
                    icon = { Icon(Icons.Default.Fastfood, contentDescription = "Calories") },
                    label = { Text("Calories") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Weight,
                    onClick = { selectedTab = MainTab.Weight },
                    icon = { Icon(Icons.Default.Scale, contentDescription = "Weight") },
                    label = { Text("Weight") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Workout,
                    onClick = { selectedTab = MainTab.Workout },
                    icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Workout") },
                    label = { Text("Workout") },
                    colors = navItemColors
                )
            }
        }
    ) { padding ->
        val showCalories = selectedTab == MainTab.Calories
        val showWeight = selectedTab == MainTab.Weight
        val showWorkout = selectedTab == MainTab.Workout
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (showCalories) 1f else 0f)
                    .zIndex(if (showCalories) 1f else 0f)
            ) {
                CalorieCounterApp(
                    repository = repository,
                    contentPadding = padding
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (showWeight) 1f else 0f)
                    .zIndex(if (showWeight) 1f else 0f)
            ) {
                WeightTableScreen(
                    repository = repository,
                    showBack = false,
                    contentPadding = padding
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (showWorkout) 1f else 0f)
                    .zIndex(if (showWorkout) 1f else 0f)
            ) {
                WorkoutScreen(
                    repository = repository,
                    contentPadding = padding
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalorieCounterApp(
    repository: FoodRepository = FoodRepository(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val scope = rememberCoroutineScope()
    var totals by remember { mutableStateOf(Totals()) }
    var showDialog by remember { mutableStateOf(false) }
    var allTotals by remember { mutableStateOf(emptyList<Totals>()) }
    var selectedTotal by remember { mutableStateOf<Totals?>(null) }
    var currentDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()

    // Prefill state after OFF lookup
    var offName by remember { mutableStateOf("") }
    var offCalories by remember { mutableStateOf("") }
    var offProteins by remember { mutableStateOf("") }
    var offFat by remember { mutableStateOf("") }
    var offCarbs by remember { mutableStateOf("") }
    var openDialogFromOff by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // NEW: paste dialog
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val pasteProvidedWeight = remember { mutableStateOf("") }

    // Intercept system back (button or edge-swipe). First back clears selection.
    // Only when nothing is selected will back actually leave the screen/app.
    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
        // (optional) Toast/Snackbar to indicate selection cleared
        // Toast.makeText(context, "Selection cleared", Toast.LENGTH_SHORT).show()
    }

    // Scanner configured for retail codes only (faster, fewer false positives)
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            ).build()
    }

// Take a quick preview bitmap and scan it (no file saved)
    val scanPreview = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val scanner = BarcodeScanning.getClient(scannerOptions)
                    val barcodes = scanner.process(image).await()
                    val code = barcodes.firstOrNull()?.rawValue

                    if (!code.isNullOrBlank()) {
                        val off = fetchOpenFoodFacts(code)
                        if (off != null) {
                            offName = off.name
                            offCalories = formatDecimal(off.kcal100)
                            offProteins = formatDecimal(off.protein100)
                            offFat = formatDecimal(off.fat100)
                            offCarbs = formatDecimal(off.carbs100)
                            openDialogFromOff = true
                        } else {
                            // Fallback: open empty dialog but keep name as barcode
                            offName = code
                            offCalories = ""; offProteins = ""; offFat = ""; offCarbs = ""
                            openDialogFromOff = true
                        }
                    } else {
                        Toast.makeText(context, "No barcode detected", Toast.LENGTH_SHORT).show()
                        // No barcode detected -> open empty dialog or show a toast/snackbar
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "Problem parsing barcode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Save-as (SAF) for the ZIP backup
    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val zipFile = createBackupZip(context, repository) // builds the zip in cache
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    zipFile.inputStream().use { it.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup saved.", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup failed: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Fetch totals by summing all rows in the Totals table
    LaunchedEffect(currentDate) {
        totals = repository.getAggregatedTotalsForDate(currentDate)
        allTotals = repository.getAllTotalsForDate(currentDate)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .swipeToNavigate(
                onSwipeLeft = { currentDate = currentDate.plusDays(1) },
                onSwipeRight = { currentDate = currentDate.minusDays(1) }
            )
    ) {
        Scaffold (
            floatingActionButton = {
                if (selectionMode) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                repository.copyTotalsToToday(selectedIds)

                                // Optional: jump to today so user immediately sees the copies
                                // currentDate = java.time.LocalDate.now()

                                // Refresh list/totals (only needed if you're already on today)
                                if (currentDate == java.time.LocalDate.now()) {
                                    allTotals = repository.getAllTotalsForDate(currentDate)
                                    totals = repository.getAggregatedTotalsForDate(currentDate)
                                }

                                // exit selection mode
                                selectedIds = emptySet()
                            }
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Copy to Today") },
                        text = { Text("Copy to Today") }
                    )
                }
            }
        ) { padding ->
            var prevDate by remember { mutableStateOf(currentDate) }
            val forward = currentDate.isAfter(prevDate)
            LaunchedEffect(currentDate) { prevDate = currentDate }

            AnimatedContent(
                targetState = currentDate,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(animationSpec = tween(220)) { it * dir } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(220)) { -it * dir } + fadeOut(tween(220))
                        )
                },
                label = "date-swipe"
            ) { date ->

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp) // Default padding for content
                        .padding(top = 32.dp), // Additional top margin of 26dp
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button
                        IconButton(onClick = { currentDate = currentDate.minusDays(1) }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Previous", tint = Color.White)
                        }
                        // Current date text in the middle
                        Text(text = currentDate.format(dateFormatter), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        // Next button
                        IconButton(onClick = { currentDate = currentDate.plusDays(1) }) {
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Calories: ${formatDecimal(totals.totalCalories)}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Proteins: ${formatDecimal(totals.totalProteins)}g", fontSize = 23.sp)
                    Text("Fat: ${formatDecimal(totals.totalFat)}g", fontSize = 23.sp)
                    Text("Carbs: ${formatDecimal(totals.totalCarbs)}g", fontSize = 23.sp)
                    Text("Cost: ${formatMoney2(totals.totalCost)}€", fontSize = 23.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row for the Delete and Add buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // State to control the visibility of the confirmation dialog
                        var showClearDialog by remember { mutableStateOf(false) }

                        Row {
                            IconButton(onClick = { scanPreview.launch(null) }) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", tint = Color.White)
                            }
                            /*IconButton(onClick = { showClearDialog = true }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear totals", tint = Color.White)
                            }*/

                            IconButton(onClick = {
                                // Propose a nice default name with date-time
                                val stamp = java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
                                saveZipLauncher.launch("CalorieCounter_Backup_$stamp.zip")
                            }) {
                                // You can use Archive or FileDownload—pick your favorite icon
                                Icon(imageVector = Icons.Default.Archive, contentDescription = "Export backup", tint = Color.White)
                            }
                            /*IconButton(onClick = {
                                pasteInput = ""
                                showPasteDialog = true
                            }) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste food JSON", tint = Color.White)
                            }*/
                            IconButton(onClick = {
                                // Try clipboard first
                                val cm = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()

                                fun tryHandleJsonFromClipboard(raw: String?): Boolean {
                                    if (raw.isNullOrBlank()) return false
                                    val t = raw.trim()
                                    return try {
                                        if (t.startsWith("{") && t.endsWith("}")) {
                                            val obj = JSONObject(t)

                                            // Same fields & behavior as your paste dialog "Confirm"
                                            offName = obj.optString("foodName", "")
                                            val weightStr = obj.optString("weightInGrams", "")
                                            offCalories = obj.optString("calories", "")
                                            offFat = obj.optString("fat", "")
                                            offCarbs = obj.optString("carbs", "")
                                            offProteins = obj.optString("protein", "")

                                            pasteProvidedWeight.value = weightStr
                                            openDialogFromOff = true
                                            true
                                        } else {
                                            false
                                        }
                                    } catch (_: Exception) {
                                        false
                                    }
                                }

                                if (!tryHandleJsonFromClipboard(text)) {
                                    // Fallback → open the paste dialog normally
                                    pasteInput = ""
                                    showPasteDialog = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste food JSON",
                                    tint = Color.White
                                )
                            }

                        }

                        /*// Copy Button
                        Button(onClick = {

                        }) {
                            Icon(Icons.Default.Create, contentDescription = "Clear Totals")
                            Spacer(modifier = Modifier.width(8.dp))
                        }*/

                        // Confirmation Dialog
                        if (showClearDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearDialog = false },
                                confirmButton = {
                                    Button(onClick = {
                                        scope.launch {
                                            repository.clearTotals()
                                            totals = repository.getAggregatedTotals() // Refresh totals
                                            allTotals = repository.getAllTotals()
                                        }
                                        showClearDialog = false // Close the dialog
                                    }) {
                                        Text("Yes")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { showClearDialog = false }) {
                                        Text("No")
                                    }
                                },
                                title = { Text("Clear Totals") },
                                text = { Text("Are you sure you want to clear all totals?") }
                            )
                        }

                        if (showPasteDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showPasteDialog = false
                                    pasteInput = ""
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        // Try to parse JSON; if valid, prefill AddFoodDialog fields
                                        val text = pasteInput.trim()
                                        try {
                                            if (text.startsWith("{") && text.endsWith("}")) {
                                                val obj = JSONObject(text)

                                                // All optional — only use if provided
                                                offName = obj.optString("foodName", "")
                                                val weightStr = obj.optString("weightInGrams", "")
                                                offCalories = obj.optString("calories", "")
                                                offFat = obj.optString("fat", "")
                                                offCarbs = obj.optString("carbs", "")
                                                offProteins = obj.optString("protein", "")
                                                pasteProvidedWeight.value = weightStr // (see section 5)

                                                // Open AddFoodDialog prefilled; weight defaults to 100 if blank inside AddFoodDialog
                                                openDialogFromOff = true

                                                // If weight explicitly provided, pass it; otherwise let dialog default logic handle it
                                                // Store it temporarily by reusing offName slots not necessary; instead just keep a local
                                                // We'll pass it directly below when showing AddFoodDialog
                                                // → Save into a temp state:
                                            }
                                        } catch (_: Exception) {
                                            // Not JSON or invalid → do nothing special
                                        } finally {
                                            showPasteDialog = false
                                        }
                                    }) { Text("Confirm") }
                                },
                                dismissButton = {
                                    Row {
                                        // Paste from clipboard → into input
                                        OutlinedButton(onClick = {
                                            val cm = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                            pasteInput = text.orEmpty()
                                        }) { Text("Paste") }
                                        Spacer(Modifier.width(8.dp))
                                        TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
                                    }
                                },
                                title = { Text("Paste food JSON") },
                                text = {
                                    OutlinedTextField(
                                        value = pasteInput,
                                        onValueChange = { pasteInput = it },
                                        label = { Text("Input") },
                                        placeholder = {
                                            Text(
                                                """
                                                        {
                                                          "foodName": "Example Food",
                                                          "weightInGrams": "100",
                                                          "calories": "120",
                                                          "fat": "3",
                                                          "carbs": "15",
                                                          "protein": "8"
                                                        }
                                                                    """.trimIndent(),
                                                color = Color.Gray,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 4
                                    )
                                }
                            )
                        }


                        // Add Button
                        Button(onClick = { showDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Food")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add")
                        }
                    }

                    // Spacer between buttons and totals list
                    Spacer(modifier = Modifier.height(16.dp))

                    // LazyColumn to display the list of totals
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp) // Add vertical padding between items
                    ) {
                        items(allTotals, key = { it.id }) { total ->
                            val isSelected = selectedIds.contains(total.id)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .padding(horizontal = 4.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                // toggle selection
                                                selectedIds = if (isSelected) selectedIds - total.id else selectedIds + total.id
                                                // if empty after toggle, we’re back to default mode automatically
                                            } else {
                                                // default behavior: open edit dialog
                                                selectedTotal = total
                                                showDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            // enter selection mode (or toggle if already in it)
                                            selectedIds = if (isSelected && selectedIds.size == 1) {
                                                // long-press on the only selected item -> unselect -> exit mode
                                                emptySet()
                                            } else if (selectionMode) {
                                                // toggle this one
                                                if (isSelected) selectedIds - total.id else selectedIds + total.id
                                            } else {
                                                // start selection mode with just this item
                                                setOf(total.id)
                                            }
                                        }
                                    ),
                                shape = MaterialTheme.shapes.small,
                                elevation = CardDefaults.cardElevation(4.dp),
                                border = if (isSelected) BorderStroke(2.dp, Color.White) else null
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp, 10.dp, 0.dp, 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // LEFT: your original title + macro text
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "${total.name} ${formatDecimal(total.weight)}g",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val costText = if (total.cost > 0f) " • ${formatMoney2(total.cost)}€" else ""
                                            Text(
                                                text = "${formatDecimal(total.totalProteins)}g protein, " +
                                                        "${formatDecimal(total.totalFat)}g fat, " +
                                                        "${formatDecimal(total.totalCarbs)}g carbs, " +
                                                        "${formatDecimal(total.totalCalories)} kcal$costText",
                                                fontSize = 14.sp
                                            )
                                        }

                                        // RIGHT: checkbox column, centered
                                        Box(
                                            modifier = Modifier
                                                .wrapContentWidth()
                                                .fillMaxHeight(),      // height from row
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.material3.Checkbox(
                                                checked = total.included,
                                                onCheckedChange = { checked ->
                                                    scope.launch {
                                                        repository.setTotalsIncluded(total.id, checked)
                                                        allTotals = repository.getAllTotalsForDate(currentDate)
                                                        totals = repository.getAggregatedTotalsForDate(currentDate)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                }

                            }
                        }
                    }
                }
            }


            // Show AddFoodDialog or EditTotalDialog
            if (showDialog && selectedTotal == null && !openDialogFromOff) {
                AddFoodDialog(
                    onDismiss = { showDialog = false },
                    repository = repository, // Pass the repository instance here
                    onTotalsUpdated = {
                        scope.launch {
                            totals = repository.getAggregatedTotalsForDate(currentDate)
                            allTotals = repository.getAllTotalsForDate(currentDate)
                        }
                    },
                    targetDate = currentDate // ← NEW
                )
            }

            if (openDialogFromOff) {
                AddFoodDialog(
                    onDismiss = {
                        openDialogFromOff = false;
                        pasteProvidedWeight.value = "" // clear temp after use
                    },
                    repository = repository,
                    onTotalsUpdated = {
                        scope.launch {
                            totals = repository.getAggregatedTotalsForDate(currentDate)
                            allTotals = repository.getAllTotalsForDate(currentDate)
                        }
                    },
                    targetDate = currentDate, // ← NEW
                    initialName = offName.ifBlank { "" },
                    initialWeight = pasteProvidedWeight.value.ifBlank { "100" }, // if blank, your dialog defaults to 100
                    initialCalories = offCalories,
                    initialProteins = offProteins,
                    initialFat = offFat,
                    initialCarbs = offCarbs
                )
            }

            if (selectedTotal != null) {
                EditTotalDialog(
                    total = selectedTotal!!,
                    onDismiss = {
                        selectedTotal = null // Dismiss EditTotalDialog
                        showDialog = false // Ensure AddFoodDialog doesn't reappear
                    },
                    repository = repository,
                    onUpdate = { updatedTotal ->
                        scope.launch {
                            repository.updateTotals(updatedTotal)
                            allTotals = repository.getAllTotalsForDate(currentDate)
                            totals = repository.getAggregatedTotalsForDate(currentDate)
                        }
                    },
                    onDelete = { totalToDelete ->
                        scope.launch {
                            repository.deleteTotals(totalToDelete.id) // Delete by ID
                            allTotals = repository.getAllTotalsForDate(currentDate)
                            totals = repository.getAggregatedTotalsForDate(currentDate)
                        }
                    }
                )
            }
        }
    }
}
@Composable
fun DecimalOnlyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = { input ->
            if (input.isBlank() || input.matches(Regex("^\\d*(\\.\\d*)?\$"))) {
                onValueChange(input)
            }
        },
        label = label,
        modifier = modifier
            .focusRequester(focusRequester), // ✅ use passed modifier
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = imeAction,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() })
    )
}

@Composable
fun EditTotalDialog(
    total: Totals,
    onDismiss: () -> Unit,
    repository: FoodRepository,
    onUpdate: (Totals) -> Unit,
    onDelete: (Totals) -> Unit
) {

    //val associatedFoods = repository.getFoodByName(total.name)
    // Maintain local states for the other editable fields
    var weight by remember { mutableStateOf(formatDecimal(total.weight)) }
    /*var proteins by remember { mutableStateOf(formatDecimal(associatedFoods!!.proteins)) }
    var fat by remember { mutableStateOf(formatDecimal(associatedFoods!!.fat)) }
    var carbs by remember { mutableStateOf(formatDecimal(associatedFoods!!.carbs)) }
    var calories by remember { mutableStateOf(formatDecimal(associatedFoods!!.calories)) }*/

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                // Recalculate totals based on associated foods
                val updatedWeight = weight.toFloatOrNull() ?: 0f
                /*val updatedProteins = proteins.toFloatOrNull() ?: 0f
                val updatedFat = fat.toFloatOrNull() ?: 0f
                val updatedCarbs = carbs.toFloatOrNull() ?: 0f
                val updatedCalories = calories.toFloatOrNull() ?: 0f*/

                CoroutineScope(Dispatchers.IO).launch {
                    val associatedFoods = repository.getAllFoods().filter { it.name == total.name }

                    val food = repository.getFoodByName(total.name)
                    val newCost =
                        if (food != null && food.price > 0f && food.priceGrams > 0f) {
                            round2((food.price / food.priceGrams) * updatedWeight)
                        } else 0f


                    val recalculatedTotals = Totals().apply {
                        id = total.id
                        name = total.name // Prevent name editing
                        this.weight = updatedWeight
                        totalProteins = associatedFoods.map { it.proteins * (this.weight / 100f) }.sum()
                        totalFat = associatedFoods.map { it.fat * (this.weight / 100f) }.sum()
                        totalCarbs = associatedFoods.map { it.carbs * (this.weight / 100f) }.sum()
                        totalCalories = associatedFoods.map { it.calories * (this.weight / 100f) }.sum()
                        cost = newCost
                    }

                    onUpdate(recalculatedTotals)
                }
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    onDelete(total) // Delete the total
                    onDismiss()
                }) {
                    Text("Delete")
                }
            }
        },
        title = { Text("Edit " + total.name) },
        text = {
            Column {
                DecimalOnlyTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (g)") },
                    focusRequester = FocusRequester(),
                    onNext = { /* Handle next action if needed */ }
                )
            }
        }
    )
}



@Composable
fun AddFoodDialog(
    onDismiss: () -> Unit,
    repository: FoodRepository,
    onTotalsUpdated: () -> Unit, // Callback to update totals
    targetDate: java.time.LocalDate,
    // ---- NEW optional prefill params ----
    initialName: String = "",
    initialWeight: String = "",
    initialCalories: String = "",
    initialProteins: String = "",
    initialFat: String = "",
    initialCarbs: String = ""
) {
    val focusManager = LocalFocusManager.current
    val focusSink = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val weightFocusRequester = FocusRequester()
    val caloriesFocusRequester = FocusRequester()
    val proteinsFocusRequester = FocusRequester()
    val fatFocusRequester = FocusRequester()
    val carbsFocusRequester = FocusRequester()

    var name by remember { mutableStateOf(initialName) }
    var weight by remember { mutableStateOf(initialWeight) }
    var calories by remember { mutableStateOf(initialCalories) }
    var proteins by remember { mutableStateOf(initialProteins) }
    var fat by remember { mutableStateOf(initialFat) }
    var carbs by remember { mutableStateOf(initialCarbs) }
    var price by remember { mutableStateOf("") }
    var priceGrams by remember { mutableStateOf("1000") } // default


    var showSuggestions by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Dynamically recompute suggestions
    val suggestions = remember(name, refreshTrigger) {
        if (name.length >= 2) repository.getSuggestions(name) else emptyList()
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val weightValue = weight.toFloatOrNull() ?: 0f

                val caloriesValue = (calories.toFloatOrNull() ?: 0f)
                val proteinsValue = (proteins.toFloatOrNull() ?: 0f)
                val fatValue = (fat.toFloatOrNull() ?: 0f)
                val carbsValue = (carbs.toFloatOrNull() ?: 0f)

                val caloriesValueCalc = caloriesValue * weightValue / 100
                val proteinsValueCalc = proteinsValue * weightValue / 100
                val fatValueCalc = fatValue * weightValue / 100
                val carbsValueCalc = carbsValue * weightValue / 100

                val priceValueRaw = price.toFloatOrNull()
                val priceGramsValue = priceGrams.toFloatOrNull()

                val priceValue = priceValueRaw?.let { round2(it) }  // ✅ round to 2 decimals

                val costValue: Float =
                    if (priceValue != null && priceGramsValue != null && priceGramsValue > 0f) {
                        round2((priceValue / priceGramsValue) * weightValue)   // ✅ cost also 2 decimals
                    } else 0f

                val food = Food().apply {
                    id = name
                    this.name = name
                    this.weight = weightValue
                    this.calories = caloriesValue
                    this.proteins = proteinsValue
                    this.fat = fatValue
                    this.carbs = carbsValue
                    this.price = priceValue ?: 0f
                    this.priceGrams = priceGramsValue ?: 0f
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val existingFood = repository.getFoodByName(food.name)
                    if (existingFood != null) {
                        repository.updateFood(existingFood, food)
                    } else {
                        repository.saveFood(food)
                    }

                    // NEW: touch for sorting recency
                    repository.touchFood(name)


                    val dateMillis = targetDate
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()

                    repository.saveToTotals(name, caloriesValueCalc, proteinsValueCalc, fatValueCalc, carbsValueCalc, weightValue, dateMillis, cost = costValue)

                    // Update totals after saving food
                    onTotalsUpdated()
                }

                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add Food") },
        text = {
            Column {
                // 👇 hidden focus sink (used to grab focus away from TextFields)
                Box(
                    Modifier
                        .size(1.dp)
                        .focusRequester(focusSink)
                        .focusable()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // TextField for Name
                        TextField(
                            value = name,
                            onValueChange = {
                                name = it
                                showSuggestions = it.length >= 2 && suggestions.isNotEmpty()
                            },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                weightFocusRequester.requestFocus()
                            })
                        )

                        // Display suggestions below the TextField
                        if (showSuggestions) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 4.dp)
                                    .defaultMinSize(minHeight = 56.dp)
                            ) {
                                suggestions.forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Fill fields and hide suggestions on click
                                                name = suggestion
                                                showSuggestions = false
                                                val existingFood = repository.getFoodByName(suggestion)
                                                if (existingFood != null) {
                                                    weight = formatDecimal(existingFood.weight)
                                                    calories = formatDecimal(existingFood.calories)
                                                    proteins = formatDecimal(existingFood.proteins)
                                                    fat = formatDecimal(existingFood.fat)
                                                    carbs = formatDecimal(existingFood.carbs)
                                                    price = if (existingFood.price > 0f) formatMoney2(existingFood.price) else ""
                                                    priceGrams = if (existingFood.priceGrams > 0f) formatDecimal(existingFood.priceGrams) else "1000"

                                                }

                                                // 👇 FIX: move focus away, then hide IME
                                                focusSink.requestFocus()
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()

                                                /*// NEW: mark as used for sorting
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    repository.touchFood(suggestion)
                                                    refreshTrigger++    // keep your refresh mechanism
                                                }*/
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
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    repository.deleteFood(suggestion) // Delete from database
                                                    refreshTrigger++ // Refresh suggestions
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
                    }
                }

                // Inputs with 8dp margin between them
                Spacer(modifier = Modifier.height(8.dp))
                DecimalOnlyTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (g)") },
                    focusRequester = weightFocusRequester,
                    onNext = { caloriesFocusRequester.requestFocus() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DecimalOnlyTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calories") },
                    focusRequester = caloriesFocusRequester,
                    onNext = { fatFocusRequester.requestFocus() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DecimalOnlyTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("Fat") },
                    focusRequester = fatFocusRequester,
                    onNext = { carbsFocusRequester.requestFocus() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DecimalOnlyTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("Carbs") },
                    focusRequester = carbsFocusRequester,
                    onNext = { proteinsFocusRequester.requestFocus() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DecimalOnlyTextField(
                    value = proteins,
                    onValueChange = { proteins = it },
                    label = { Text("Proteins") },
                    focusRequester = proteinsFocusRequester,
                    onNext = { focusManager.clearFocus() } // Hide keyboard
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DecimalOnlyTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price") },
                        focusRequester = remember { FocusRequester() },
                        onNext = { /* optional */ },
                        modifier = Modifier.weight(1f)       // ✅ IMPORTANT
                    )

                    DecimalOnlyTextField(
                        value = priceGrams,
                        onValueChange = { priceGrams = it },
                        label = { Text("Grams") },
                        focusRequester = remember { FocusRequester() },
                        onNext = { /* optional */ },
                        modifier = Modifier.weight(1f),      // ✅ IMPORTANT
                        imeAction = ImeAction.Done
                    )
                }
            }
        }
    )
}

fun formatDecimal(value: Float): String {
    return if (value % 1.0 != 0.0) {
        String.format(Locale.US, "%.1f", value) // Keep one decimal place if not an integer
    } else {
        value.toInt().toString() // Convert to an integer string if .0
    }
}

fun formatMoney(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

fun round2(value: Float): Float = (value * 100f).roundToInt() / 100f

fun formatMoney2(value: Float): String =
    String.format(Locale.US, "%.2f", value)     // always 2 decimals (7.99, 8.00)

fun formatMoney2Trim(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else formatMoney2(value) // optional





