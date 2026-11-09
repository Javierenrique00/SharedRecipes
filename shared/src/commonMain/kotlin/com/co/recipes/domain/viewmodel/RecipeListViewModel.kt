package com.co.recipes.domain.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.filterBySearch
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

    /**
     * What the user typed in the list's search box.
     *
     * The filter is client side, over the recipes [refresh] already loaded: matching a
     * partial word is a `contains` in memory, so typing never fires a request and the
     * results update on the keystroke. See [com.co.recipes.domain.dataclass.filterBySearch].
     */
    var searchQuery by mutableStateOf("")
        private set

    /** What the list renders: [recipes] narrowed by [searchQuery]. */
    val visibleRecipes: List<Recipe> get() = recipes.filterBySearch(searchQuery)

    /**
     * The recipe that was just deleted, kept only so the screen can offer Undo.
     *
     * Nothing is re-inserted from the client: the row is still there with
     * `deleted_at` set, so [undoDelete] restores it in place and the id, the
     * ingredients, the steps and the comments (including other users' comments,
     * which RLS would never let an admin re-create) all come back unchanged.
     */
    var pendingDeletion by mutableStateOf<PendingDeletion?>(null)
        private set

    /** [index] is where the card was in [recipes], so Undo puts it back in order. */
    data class PendingDeletion(val recipe: Recipe, val index: Int)

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

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    /** Drops the filter; the list shows everything [refresh] loaded again. */
    fun clearSearch() {
        searchQuery = ""
    }

    fun delete(recipe: Recipe) {
        // Indexed in [recipes], never in [visibleRecipes]: a search may be hiding the
        // neighbours, and Undo still has to put the card back at its place in the full list.
        val index = recipes.indexOfFirst { it.id == recipe.id }
        if (index < 0) return

        val beforeDelete = recipes
        // Optimistic: the card disappears now and the snackbar offers Undo; on failure
        // the whole list comes back and the message explains why.
        recipes = beforeDelete.filterNot { it.id == recipe.id }

        viewModelScope.launch {
            recipesApi.deleteRecipe(recipe.id)
                .onSuccess { pendingDeletion = PendingDeletion(recipe = recipe, index = index) }
                .onFailure {
                    recipes = beforeDelete
                    errorMessage = it.readableMessage()
                }
        }
    }

    /** Undo for the last [delete]: clears `deleted_at` and puts the card back. */
    fun undoDelete() {
        val pending = pendingDeletion ?: return
        pendingDeletion = null

        viewModelScope.launch {
            recipesApi.restoreRecipe(pending.recipe.id)
                .onSuccess {
                    recipes = recipes.toMutableList().apply {
                        add(pending.index.coerceIn(0, size), pending.recipe)
                    }
                }
                .onFailure { errorMessage = it.readableMessage() }
        }
    }

    /** The Undo snackbar went away without being used; forget that deletion. */
    fun dismissUndo(recipeId: String) {
        if (pendingDeletion?.recipe?.id == recipeId) pendingDeletion = null
    }

    fun clearError() {
        errorMessage = null
    }
}
