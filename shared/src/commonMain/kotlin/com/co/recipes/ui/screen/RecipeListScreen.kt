package com.co.recipes.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.UserSession
import com.co.recipes.domain.viewmodel.RecipeListViewModel
import com.co.recipes.infraestructure.storage.RecipeImages
import com.co.recipes.ui.common.EmptyState
import com.co.recipes.ui.common.ErrorState
import com.co.recipes.ui.common.screenContentPadding
import com.co.recipes.ui.navigation.AdminRecipes
import com.co.recipes.ui.navigation.RecipeDetail
import com.co.recipes.ui.navigation.RecipeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    navController: NavHostController,
    viewModel: RecipeListViewModel,
    session: UserSession,
    onSignOut: () -> Unit,
) {
    // Refresh whenever the screen is resumed, not just on first composition: coming back
    // from the admin console (or from the editor) must never show stale recipes — that is
    // what makes a clear/restore visibly take effect.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val recipes = viewModel.recipes
    // The cards on screen: `recipes` narrowed by the search box. Read as a snapshot value,
    // so typing re-composes the list while the full collection stays in the ViewModel.
    val visible = viewModel.visibleRecipes
    val error = viewModel.errorMessage

    // Undo. The delete is a soft delete server side, so "Undo" only clears a column
    // and the recipe comes back with its ingredients, steps and comments intact.
    // Keyed on the recipe id, so deleting a second recipe re-arms the same snackbar;
    // `finally` covers all the ways out (action taken, timeout, swiped away, or the
    // screen leaving the composition), so navigating back cannot replay a stale bar.
    val pendingDeletion = viewModel.pendingDeletion
    LaunchedEffect(pendingDeletion?.recipe?.id) {
        val pending = pendingDeletion ?: return@LaunchedEffect
        try {
            val result = snackbarHostState.showSnackbar(
                message = "“${pending.recipe.title}” deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        } finally {
            viewModel.dismissUndo(pending.recipe.id)
        }
    }

    // A failed delete with rows on screen would otherwise be silent: the full-screen
    // ErrorState below only renders for an empty list.
    LaunchedEffect(error) {
        if (error == null || recipes.isEmpty()) return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
                actions = {
                    Text(
                        text = "${session.displayName} · ${session.role.name.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Admins get the backup/restore/clear console. Clients never see the
                    // entry point; the calls behind it are admin-checked in Postgres anyway.
                    if (session.isAdmin) {
                        TextButton(onClick = { navController.navigate(AdminRecipes) }) {
                            Text("Admin")
                        }
                    }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
        floatingActionButton = {
            if (session.isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(RecipeEditor()) },
                ) {
                    Text("New recipe")
                }
            }
        },
    ) { padding ->
        // `screenContentPadding` (not `padding`) because this screen now owns a text field:
        // it reserves the keyboard's height, so the results are not hidden behind the IME.
        // See ui/common/Insets.kt and the README's IME section.
        Box(modifier = Modifier.fillMaxSize().screenContentPadding(padding)) {
            when {
                viewModel.isLoading && recipes.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null && recipes.isEmpty() ->
                    ErrorState(
                        message = error,
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = viewModel::refresh,
                    )

                recipes.isEmpty() ->
                    EmptyState("No recipes yet.", Modifier.align(Alignment.Center))

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    RecipeSearchField(
                        query = viewModel.searchQuery,
                        suggestions = viewModel.searchSuggestions,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSuggestionClick = viewModel::completeSearchWith,
                        onClear = viewModel::clearSearch,
                    )

                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize()) {
                            EmptyState(
                                message = "No recipes match “${viewModel.searchQuery.trim()}”.",
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visible, key = { it.id }) { recipe ->
                                RecipeCard(
                                    recipe = recipe,
                                    isAdmin = session.isAdmin,
                                    onClick = { navController.navigate(RecipeDetail(recipe.id)) },
                                    onDelete = { viewModel.delete(recipe) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The list filter: a text field plus a menu of the words the collection already uses.
 *
 * Matching happens in the ViewModel over the recipes already loaded, so there is no debounce
 * and no request per keystroke: the cards narrow as the user types, and the suggestions come
 * from the same strings the filter reads (see [com.co.recipes.domain.dataclass.searchSuggestions]).
 *
 * `ImeAction.Search` closes the keyboard instead of inserting anything — the results are
 * already on screen while typing, so there is nothing to submit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeSearchField(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    // Two pieces of state, and the split is what makes the menu behave: `expanded` is the
    // user's intent (they are in the field), `menuOpen` also requires something to offer.
    // Picking a suggestion leaves a trailing space, so `suggestions` empties and the menu
    // hides while `expanded` stays true — typing the next word re-opens it without the user
    // having to tap the field again. It also means the menu never covers the cards on an
    // empty field or between words.
    var expanded by remember { mutableStateOf(false) }
    val menuOpen = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = menuOpen,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search") },
            placeholder = { Text("Title, description or ingredient") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            trailingIcon = {
                // Only when there is something to clear; the glyph is the one the editor rows
                // use, which keeps the app on text buttons instead of an icon dependency.
                if (query.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("×") }
                }
            },
            // `PrimaryEditable` (not the non-editable anchor) keeps the field focused and the
            // IME up while the menu is open — the rule in the README's keyboard section.
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { word ->
                DropdownMenuItem(
                    text = { Text(word) },
                    onClick = { onSuggestionClick(word) },
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RecipeThumbnail(recipe)

                Column(Modifier.weight(1f)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)

                    recipe.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            RecipeFacts(recipe)

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

/**
 * Time, ingredient count, comment count and the video marker.
 *
 * One `Text` on its own full-width line instead of four siblings in a `Row`: the facts used
 * to overflow the text column next to the thumbnail (Kotlin/Compose does not shrink texts
 * automatically, so the last one was simply cut off). As a single ellipsized line the card
 * uses the whole width it has, and on a narrow phone with a big recipe it ends in "…"
 * instead of running off the card.
 */
@Composable
private fun RecipeFacts(recipe: Recipe) {
    val facts = buildList {
        recipe.totalMinutes?.let { add("$it min") }
        add(countLabel(recipe.ingredients.size, "ingredient"))
        add(countLabel(recipe.comments.size, "comment"))
        if (recipe.youtubeId != null) add("video")
    }

    Text(
        text = facts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** `1 ingredient`, `4 ingredients`. */
private fun countLabel(count: Int, singular: String): String =
    "$count $singular${if (count == 1) "" else "s"}"

/**
 * The card's picture: the recipe's own photo, or its YouTube still when it has no upload
 * yet. Recipes with neither keep the text-only card they had before.
 */
@Composable
private fun RecipeThumbnail(recipe: Recipe) {
    val url = RecipeImages.publicUrl(recipe.imagePath) ?: recipe.youtubeThumbnailUrl ?: return

    AsyncImage(
        model = url,
        contentDescription = "Photo of ${recipe.title}",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}
