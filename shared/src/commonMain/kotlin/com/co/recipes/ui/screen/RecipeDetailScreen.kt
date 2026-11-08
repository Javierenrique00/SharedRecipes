package com.co.recipes.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.co.recipes.domain.dataclass.UserSession
import com.co.recipes.domain.export.ExportFormat
import com.co.recipes.domain.viewmodel.RecipeDetailViewModel
import com.co.recipes.ui.common.ErrorState
import com.co.recipes.ui.common.screenContentPadding
import com.co.recipes.ui.navigation.RecipeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    navController: NavHostController,
    viewModel: RecipeDetailViewModel,
    session: UserSession,
) {
    val recipe = viewModel.recipe

    LaunchedEffect(Unit) { viewModel.load() }

    val snackbarHostState = remember { SnackbarHostState() }
    var showExportDialog by remember { mutableStateOf(false) }

    // "Saved pad-thai.md" / "Saved to /Users/.../pad-thai.pdf".
    val exportMessage = viewModel.exportMessage
    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            snackbarHostState.showSnackbar(exportMessage)
            viewModel.clearExportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title ?: "Recipe") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                },
                actions = {
                    // Available to every signed-in user, not just admins: it only turns the
                    // recipe on screen into a document.
                    if (recipe != null) {
                        TextButton(
                            onClick = { showExportDialog = true },
                            enabled = !viewModel.isExporting,
                        ) {
                            Text(if (viewModel.isExporting) "Exporting…" else "Export")
                        }
                    }
                    if (session.isAdmin && recipe != null) {
                        TextButton(onClick = { navController.navigate(RecipeEditor(recipe.id)) }) {
                            Text("Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading && recipe == null ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

            recipe == null ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ErrorState(
                        message = viewModel.errorMessage ?: "Recipe not found.",
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = viewModel::load,
                    )
                }

            else -> LazyColumn(
                // Keeps the comment box above the keyboard (and, before it was handled, stopped
                // Android from panning the video thumbnail over it).
                modifier = Modifier.fillMaxSize().screenContentPadding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { VideoHeader(recipe) }
                item { MetaRow(recipe) }

                recipe.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item { Text(description, style = MaterialTheme.typography.bodyMedium) }
                }

                item { SectionTitle("Ingredients") }
                items(recipe.ingredients) { ingredient ->
                    IngredientRow(ingredient.name, ingredient.display)
                }

                item { SectionTitle("Method") }
                items(recipe.steps) { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${step.number}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(step.instruction, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SectionTitle("Comments (${recipe.comments.size})")
                }

                items(recipe.comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        canDelete = session.isAdmin || comment.authorId == session.userId,
                        onDelete = { viewModel.deleteComment(comment.id) },
                    )
                }

                item {
                    CommentComposer(
                        draft = viewModel.commentDraft,
                        onDraftChange = { viewModel.commentDraft = it },
                        isPosting = viewModel.isPostingComment,
                        onPost = { viewModel.postComment(session.userId) },
                    )
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

    if (showExportDialog && recipe != null) {
        ExportFormatDialog(
            hasYoutubeVideo = recipe.youtubeId != null,
            onDismiss = { showExportDialog = false },
            onPick = { format ->
                showExportDialog = false
                viewModel.export(format)
            },
        )
    }

}

/**
 * Format picker for the export action.
 *
 * Two rows, each with a one-line explanation, because "PDF" and "Markdown" mean different
 * things to different people: one is to print or send, the other to keep in a notes app.
 */
@Composable
private fun ExportFormatDialog(
    hasYoutubeVideo: Boolean,
    onDismiss: () -> Unit,
    onPick: (ExportFormat) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export recipe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ExportFormat.entries.forEach { format ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(format) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(format.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = format.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (hasYoutubeVideo) {
                    Text(
                        text = "The video is linked, not embedded, in both formats.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
