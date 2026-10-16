package com.fit2x.app.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fit2x.app.data.local.FoodEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the Diet screen. Protein is the hero metric. */
data class DietUiState(
    val entries: List<FoodEntry> = emptyList(),
    val proteinGrams: Double = 0.0,
    val calories: Int = 0,
    val proteinGoal: Int = 70,
    val calorieGoal: Int = 2_200
) {
    val proteinProgress: Float
        get() = if (proteinGoal > 0) (proteinGrams / proteinGoal).toFloat().coerceIn(0f, 1f) else 0f
    val proteinLeft: Double
        get() = (proteinGoal - proteinGrams).coerceAtLeast(0.0)
    val calorieProgress: Float
        get() = if (calorieGoal > 0) (calories.toFloat() / calorieGoal).coerceIn(0f, 1f) else 0f
}

@OptIn(ExperimentalCoroutinesApi::class)
class DietViewModel(
    private val repository: FoodRepository
) : ViewModel() {

    private val date = MutableStateFlow(repository.today())

    init {
        // Warm the bundled food database off the Main thread, so the first
        // search keystroke never does asset I/O + JSON parsing on the UI thread.
        viewModelScope.launch(Dispatchers.IO) {
            repository.allFoods()
        }
    }

    /** Combined hero state for the screen. */
    val uiState: StateFlow<DietUiState> = date
        .flatMapLatest { d ->
            combine(
                repository.getEntries(d),
                repository.getTodayProtein(d),
                repository.getTodayCalories(d),
                repository.observeProteinGoal(),
                repository.observeCalorieGoal()
            ) { entries, protein, calories, proteinGoal, calorieGoal ->
                DietUiState(entries, protein, calories, proteinGoal, calorieGoal)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState())

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    val searchQuery = MutableStateFlow("")

    /** Live search results from the bundled offline database. */
    val searchResults: StateFlow<List<Food>> = searchQuery
        .flatMapLatest { q -> flow { emit(repository.searchFoods(q)) }.flowOn(Dispatchers.Default) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    // ------------------------------------------------------------------
    // Barcode
    // ------------------------------------------------------------------

    private val _barcodeState = MutableStateFlow<BarcodeUiState>(BarcodeUiState.Idle)
    val barcodeState: StateFlow<BarcodeUiState> = _barcodeState

    fun onBarcodeScanned(code: String) {
        viewModelScope.launch {
            _barcodeState.value = BarcodeUiState.LookingUp
            _barcodeState.value = when (val r = repository.lookupBarcode(code)) {
                is FoodRepository.BarcodeLookupResult.Found -> BarcodeUiState.Found(r.food)
                FoodRepository.BarcodeLookupResult.NotFound ->
                    BarcodeUiState.Error("No nutrition data for this barcode.")
                FoodRepository.BarcodeLookupResult.Offline ->
                    BarcodeUiState.Error("You're offline — search the food list instead.")
            }
        }
    }

    fun clearBarcodeState() {
        _barcodeState.value = BarcodeUiState.Idle
    }

    // ------------------------------------------------------------------
    // Logging
    // ------------------------------------------------------------------

    fun logFood(food: Food, mealType: MealType = MealType.defaultForNow(), quantity: Double = 1.0) {
        viewModelScope.launch {
            repository.logFood(food, mealType, quantity, date.value)
        }
    }

    /** Manual entry for foods not in the database. */
    fun logManualFood(
        name: String,
        proteinGrams: Double,
        calories: Int,
        mealType: MealType = MealType.defaultForNow()
    ) {
        viewModelScope.launch {
            repository.logManualFood(name, proteinGrams, calories, mealType, date.value)
        }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
        }
    }

    fun refreshToday() {
        date.value = repository.today()
    }
}

sealed interface BarcodeUiState {
    data object Idle : BarcodeUiState
    data object LookingUp : BarcodeUiState
    data class Found(val food: Food) : BarcodeUiState
    data class Error(val message: String) : BarcodeUiState
}

/** Manual-DI factory (no Hilt in this project). */
class DietViewModelFactory(
    private val repository: FoodRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DietViewModel::class.java))
        return DietViewModel(repository) as T
    }
}
