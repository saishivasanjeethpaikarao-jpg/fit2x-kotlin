package com.fit2x.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fit2x.app.R
import com.fit2x.app.appGraph
import com.fit2x.app.data.local.FoodEntry
import com.fit2x.app.diet.DietViewModel
import com.fit2x.app.diet.Food
import com.fit2x.app.diet.MealType
import com.fit2x.app.diet.BarcodeUiState
import com.fit2x.app.diet.rememberBarcodeScanLauncher
import com.fit2x.app.ui.components.ProgressRing

/**
 * Diet tracker — backed by the real [DietViewModel] (Room + bundled food DB).
 *
 * - Twin protein/calorie rings, today's log, quick-add from the bundled
 *   Indian food database, manual entry, per-item delete.
 */
@Composable
fun DietScreen(
    dietViewModel: DietViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val vm = dietViewModel
    val state by vm.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshToday() }

    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_food))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_diet).uppercase(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = scheme.onBackground
                )
            }

            // Twin progress rings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProgressRing(
                                progress = state.proteinProgress,
                                centerText = "${state.proteinGrams.toInt()}g",
                                centerSubText = stringResource(R.string.diet_protein),
                                size = 110.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.of_goal, "${state.proteinGoal}g"),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProgressRing(
                                progress = state.calorieProgress,
                                centerText = "${state.calories}",
                                centerSubText = stringResource(R.string.diet_calories),
                                size = 110.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.of_goal, "${state.calorieGoal}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.food_log).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = scheme.onSurfaceVariant
                )
            }

            if (state.entries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = scheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.no_foods_logged),
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    FoodEntryCard(
                        entry = entry,
                        onDelete = { vm.deleteEntry(entry) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddFoodDialog(
            viewModel = vm,
            onDismiss = {
                vm.clearBarcodeState()
                showAddDialog = false
            },
            onAdded = {
                vm.clearBarcodeState()
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FoodEntryCard(entry: FoodEntry, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scheme.onSurface
                )
                Text(
                    text = "${entry.quantityLabel} · ${entry.mealType.lowercase()
                        .replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.proteinGrams.toInt()}g protein",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = scheme.primary
                )
                Text(
                    text = "${entry.calories} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Add-food dialog: live search over the bundled food database, tap to
 * quick-fill, then adjust quantity/meal and save. Manual entry supported.
 */
@Composable
private fun AddFoodDialog(
    viewModel: DietViewModel,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<Food?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    var meal by remember { mutableStateOf(MealType.defaultForNow()) }
    // Manual fallback fields
    var manualName by remember { mutableStateOf("") }
    var manualProtein by remember { mutableStateOf("") }
    var manualCalories by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsState()
    val barcodeState by viewModel.barcodeState.collectAsState()

    // Barcode scanner launcher — delivers the scanned code or null if cancelled.
    val barcodeLauncher = rememberBarcodeScanLauncher { code ->
        if (code != null) viewModel.onBarcodeScanned(code)
    }

    // When a barcode lookup finds a food, auto-select it.
    LaunchedEffect(barcodeState) {
        if (barcodeState is BarcodeUiState.Found) {
            selectedFood = (barcodeState as BarcodeUiState.Found).food
            manualMode = false
        }
    }

    LaunchedEffect(query) { viewModel.onSearchQueryChange(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_food)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                manualMode = false
                                selectedFood = null
                            },
                            label = { Text(stringResource(R.string.search_foods)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { barcodeLauncher.launch(Unit) },
                            modifier = Modifier
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = "Scan barcode",
                                tint = scheme.primary
                            )
                        }
                    }
                }

                // Barcode lookup status
                when (val bs = barcodeState) {
                    is BarcodeUiState.LookingUp -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Looking up barcode…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is BarcodeUiState.Error -> {
                        item {
                            Text(
                                text = bs.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    else -> { /* Idle or Found (auto-selected above) */ }
                }

                if (!manualMode) {
                    item {
                        Text(
                            text = stringResource(R.string.quick_add),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurfaceVariant
                        )
                    }
                    items(searchResults.take(6)) { food ->
                        val selected = selectedFood?.id == food.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFood = food },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) scheme.primaryContainer
                                else scheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = food.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = food.serving.ifBlank { "1 serving" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${food.proteinGrams.toInt()}g · ${food.calories}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = scheme.primary
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Can't find it? Add manually",
                            modifier = Modifier
                                .clickable { manualMode = true }
                                .padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = scheme.primary
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = { Text(stringResource(R.string.food_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualProtein,
                                onValueChange = {
                                    if (it.all { c -> c.isDigit() || c == '.' }) manualProtein = it
                                },
                                label = { Text("Protein (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = manualCalories,
                                onValueChange = { if (it.all(Char::isDigit)) manualCalories = it },
                                label = { Text("Kcal") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (selectedFood != null && !manualMode) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Quantity:", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = {
                                    if (it.all { c -> c.isDigit() || c == '.' }) quantityText = it
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Meal",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurfaceVariant
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealType.entries.forEach { m ->
                            val isSelected = meal == m
                            Card(
                                modifier = Modifier.clickable { meal = m },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) scheme.primary else scheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = m.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (manualMode) {
                        val protein = manualProtein.toDoubleOrNull() ?: 0.0
                        val cals = manualCalories.toIntOrNull() ?: 0
                        if (manualName.isNotBlank()) {
                            // Log as a one-off Food via the repository path.
                            viewModel.logManualFood(
                                name = manualName,
                                proteinGrams = protein,
                                calories = cals,
                                mealType = meal
                            )
                            onAdded()
                        }
                    } else {
                        val food = selectedFood ?: return@TextButton
                        val qty = quantityText.toDoubleOrNull()?.coerceAtLeast(0.25) ?: 1.0
                        viewModel.logFood(food, meal, qty)
                        onAdded()
                    }
                }
            ) { Text(stringResource(R.string.add_food)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
