package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.toDomain
import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.api.readableMessage
import kotlinx.coroutines.launch

class RecipeListViewModel(private val recipesApi: RecipesApi) : ViewModel() {

    var recipes by mutableStateOf<List<Recipe>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            recipesApi.listRecipes()
                .onSuccess { dtos -> recipes = dtos.map { it.toDomain() } }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }

    fun delete(recipeId: String) {
        viewModelScope.launch {
            recipesApi.deleteRecipe(recipeId)
                .onSuccess { recipes = recipes.filterNot { it.id == recipeId } }
                .onFailure { errorMessage = it.readableMessage() }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
