package com.example.floating.caloriecounter

// Food-entry and total-edit dialogs shared by the calorie screen.

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.floating.caloriecounter.Model.Food
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.Model.Totals
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
            .focusRequester(focusRequester),
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
    val scope = rememberCoroutineScope()

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

                scope.launch {
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
    val scope = rememberCoroutineScope()
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
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }


    var showSuggestions by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Dynamically recompute suggestions
    val suggestions = remember(name, refreshTrigger) {
        if (name.length >= 2) repository.getSuggestions(name) else emptyList()
    }


    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (isSaving) return@Button

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

                    val priceValue = priceValueRaw?.let { round2(it) }

                    val costValue: Float =
                        if (priceValue != null && priceGramsValue != null && priceGramsValue > 0f) {
                            round2((priceValue / priceGramsValue) * weightValue)
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

                    isSaving = true
                    saveError = null

                    scope.launch {
                        try {
                            val existingFood = repository.getFoodByName(food.name)
                            if (existingFood != null) {
                                repository.updateFood(existingFood, food)
                            } else {
                                repository.saveFood(food)
                            }

                            repository.touchFood(name)

                            val dateMillis = targetDate
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()

                            repository.saveToTotals(
                                name = name,
                                calories = caloriesValueCalc,
                                proteins = proteinsValueCalc,
                                fat = fatValueCalc,
                                carbs = carbsValueCalc,
                                weight = weightValue,
                                dateMillis = dateMillis,
                                cost = costValue
                            )

                            onTotalsUpdated()
                            // A rememberCoroutineScope is cancelled when this dialog leaves
                            // composition, so dismiss only after every database write completes.
                            onDismiss()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            saveError = error.message ?: "Food could not be saved."
                            isSaving = false
                        }
                    }
                }
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        },
        title = { Text("Add Food") },
        text = {
            Column {
                // Hidden focus sink used to move focus away from text fields.
                Box(
                    Modifier
                        .size(1.dp)
                        .focusRequester(focusSink)
                        .focusable()
                )

                saveError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

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

                                                // Move focus away, then hide the keyboard.
                                                focusSink.requestFocus()
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()

                                                /*// NEW: mark as used for sorting
                                                scope.launch {
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
                                                scope.launch {
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
                        modifier = Modifier.weight(1f)
                    )

                    DecimalOnlyTextField(
                        value = priceGrams,
                        onValueChange = { priceGrams = it },
                        label = { Text("Grams") },
                        focusRequester = remember { FocusRequester() },
                        onNext = { /* optional */ },
                        modifier = Modifier.weight(1f),
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
