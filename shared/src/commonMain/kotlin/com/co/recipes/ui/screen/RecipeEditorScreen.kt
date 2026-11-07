package com.co.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.co.recipes.domain.viewmodel.RecipeEditorViewModel
import com.co.recipes.ui.common.screenContentPadding

/**
 * The description grows from three lines up to this many, then scrolls inside the field.
 *
 * The cap is what keeps the fields *below* it still while typing: with an unbounded `maxLines`
 * every wrapped line re-lays-out the whole `LazyColumn`, so the YouTube/ingredient sections slid
 * up and down under the caret.
 */
private const val DescriptionMaxLines = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    navController: NavHostController,
    viewModel: RecipeEditorViewModel,
) {
    LaunchedEffect(viewModel.savedRecipeId) {
        if (viewModel.savedRecipeId != null) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit recipe" else "New recipe") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            // `screenContentPadding` reserves the keyboard's height (see ui/common/Insets.kt):
            // the list viewport ends where the IME starts, so a focused cell is scrolled above
            // the keyboard once and then stays put, instead of the window panning on every
            // keystroke.
            modifier = Modifier.fillMaxSize().screenContentPadding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextCellActions(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it },
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = DescriptionMaxLines,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = viewModel.youtubeUrl,
                    onValueChange = { viewModel.youtubeUrl = it },
                    label = { Text("YouTube URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextCellActions(),
                    supportingText = { Text("https://www.youtube.com/watch?v=...") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorNumberField(
                        label = "Servings",
                        value = viewModel.servings,
                        onValueChange = { viewModel.servings = it },
                        modifier = Modifier.weight(1f),
                    )
                    EditorNumberField(
                        label = "Prep min",
                        value = viewModel.prepMinutes,
                        onValueChange = { viewModel.prepMinutes = it },
                        modifier = Modifier.weight(1f),
                    )
                    EditorNumberField(
                        label = "Cook min",
                        value = viewModel.cookMinutes,
                        onValueChange = { viewModel.cookMinutes = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { Text("Ingredients", style = MaterialTheme.typography.titleMedium) }

            items(viewModel.ingredients, key = { it.lazyKey }) { row ->
                IngredientEditorRow(
                    row = row,
                    onChange = { viewModel.updateIngredientRow(row.key, it) },
                    onRemove = { viewModel.removeIngredientRow(row.key) },
                )
            }

            item {
                TextButton(onClick = viewModel::addIngredientRow) { Text("+ Add ingredient") }
            }

            item { Text("Method", style = MaterialTheme.typography.titleMedium) }

            items(viewModel.steps, key = { it.lazyKey }) { row ->
                StepEditorRow(
                    row = row,
                    onChange = { viewModel.updateStepRow(row.key, it) },
                    onRemove = { viewModel.removeStepRow(row.key) },
                )
            }

            item {
                TextButton(onClick = viewModel::addStepRow) { Text("+ Add step") }
            }

            item {
                Button(
                    onClick = viewModel::save,
                    enabled = !viewModel.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (viewModel.isEditing) "Save changes" else "Create recipe")
                    }
                }
            }

            viewModel.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
