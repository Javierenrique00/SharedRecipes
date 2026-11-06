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

class RecipeDetailViewModel(
    private val recipesApi: RecipesApi,
    private val recipeId: String,
) : ViewModel() {

    var recipe by mutableStateOf<Recipe?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isPostingComment by mutableStateOf(false)
        private set

    var commentDraft by mutableStateOf("")

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            recipesApi.getRecipe(recipeId)
                .onSuccess { recipe = it.toDomain() }
                .onFailure { errorMessage = it.readableMessage() }
            isLoading = false
        }
    }

    fun postComment(authorId: String) {
        val body = commentDraft.trim()
        if (body.isEmpty() || isPostingComment) return

        viewModelScope.launch {
            isPostingComment = true
            recipesApi.addComment(recipeId = recipeId, authorId = authorId, body = body)
                .onSuccess {
                    commentDraft = ""
                    load()
                }
                .onFailure { errorMessage = it.readableMessage() }
            isPostingComment = false
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            recipesApi.deleteComment(commentId)
                .onSuccess { load() }
                .onFailure { errorMessage = it.readableMessage() }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
