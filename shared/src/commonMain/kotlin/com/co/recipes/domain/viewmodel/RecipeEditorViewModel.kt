package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.api.readableMessage
import com.co.recipes.infraestructure.dataclass.CreateRecipeParams
import com.co.recipes.infraestructure.dataclass.IngredientParam
import com.co.recipes.infraestructure.dataclass.StepParam
import kotlinx.coroutines.launch

class RecipeEditorViewModel(
    private val recipesApi: RecipesApi,
    private val recipeId: String? = null,
) : ViewModel() {

    data class IngredientRow(
        val key: Int,
        val quantity: String = "",
        val unit: String = "",
        val name: String = "",
    ) {
        /**
         * Stable key for the editor's single `LazyColumn`, which renders ingredient
         * *and* step rows. Both lists start at [key] 0, and Compose requires keys to be
         * unique across the whole lazy list, so the type prefix is mandatory — without
         * it the editor crashes with `Key "0" was already used`.
         */
        val lazyKey: String get() = "ingredient-$key"
    }

    data class StepRow(
        val key: Int,
        val instruction: String = "",
    ) {
        /** See [IngredientRow.lazyKey]: the prefix keeps both lists' keys distinct. */
        val lazyKey: String get() = "step-$key"
    }

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var youtubeUrl by mutableStateOf("")
    var servings by mutableStateOf("")
    var prepMinutes by mutableStateOf("")
    var cookMinutes by mutableStateOf("")

    var ingredients by mutableStateOf(listOf(IngredientRow(key = 0)))
        private set

    var steps by mutableStateOf(listOf(StepRow(key = 0)))
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Set once a save succeeds, so the screen can navigate away. */
    var savedRecipeId by mutableStateOf<String?>(null)
        private set

    val isEditing: Boolean get() = recipeId != null

    private var nextKey = 1

    init {
        if (recipeId != null) loadExisting()
    }

    fun clearError() {
        errorMessage = null
    }

    fun addIngredientRow() {
        ingredients = ingredients + IngredientRow(key = nextKey++)
    }

    fun updateIngredientRow(key: Int, row: IngredientRow) {
        ingredients = ingredients.map { if (it.key == key) row else it }
    }

    fun removeIngredientRow(key: Int) {
        if (ingredients.size > 1) ingredients = ingredients.filterNot { it.key == key }
    }

    fun addStepRow() {
        steps = steps + StepRow(key = nextKey++)
    }

    fun updateStepRow(key: Int, instruction: String) {
        steps = steps.map { if (it.key == key) it.copy(instruction = instruction) else it }
    }

    fun removeStepRow(key: Int) {
        if (steps.size > 1) steps = steps.filterNot { it.key == key }
    }

    fun save() {
        if (isSaving) return

        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            errorMessage = "A title is required."
            return
        }

        val params = CreateRecipeParams(
            title = trimmedTitle,
            description = description.trim().ifBlank { null },
            youtubeUrl = youtubeUrl.trim().ifBlank { null },
            servings = servings.toIntOrNull(),
            prepMinutes = prepMinutes.toIntOrNull(),
            cookMinutes = cookMinutes.toIntOrNull(),
            ingredients = ingredients
                .filter { it.name.isNotBlank() }
                .mapIndexed { index, row ->
                    IngredientParam(
                        position = index,
                        quantity = row.quantity.replace(',', '.').toDoubleOrNull(),
                        unit = row.unit.trim().ifBlank { null },
                        name = row.name.trim(),
                    )
                },
            steps = steps
                .filter { it.instruction.isNotBlank() }
                .mapIndexed { index, row ->
                    StepParam(
                        stepNumber = index + 1,
                        instruction = row.instruction.trim(),
                    )
                },
        )

        viewModelScope.launch {
            isSaving = true
            errorMessage = null

            val result = if (recipeId == null) {
                recipesApi.createRecipe(params)
            } else {
                recipesApi.updateRecipe(recipeId, params).map { recipeId }
            }

            result
                .onSuccess { savedRecipeId = it }
                .onFailure { errorMessage = it.readableMessage() }

            isSaving = false
        }
    }

    private fun loadExisting() {
        val id = recipeId ?: return
        viewModelScope.launch {
            isLoading = true
            recipesApi.getRecipe(id)
                .onSuccess { dto ->
                    title = dto.title
                    description = dto.description.orEmpty()
                    youtubeUrl = dto.youtubeUrl.orEmpty()
                    servings = dto.servings?.toString().orEmpty()
                    prepMinutes = dto.prepMinutes?.toString().orEmpty()
                    cookMinutes = dto.cookMinutes?.toString().orEmpty()

                    val loadedIngredients = dto.ingredients
                        .sortedBy { it.position ?: 0 }
                        .map { ingredient ->
                            IngredientRow(
                                key = nextKey++,
                                quantity = ingredient.quantity?.toString().orEmpty(),
                                unit = ingredient.unit.orEmpty(),
                                name = ingredient.name,
                            )
                        }
                    if (loadedIngredients.isNotEmpty()) ingredients = loadedIngredients

                    val loadedSteps = dto.steps
                        .sortedBy { it.stepNumber }
                        .map { step -> StepRow(key = nextKey++, instruction = step.instruction) }
                    if (loadedSteps.isNotEmpty()) steps = loadedSteps
                }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }
}

