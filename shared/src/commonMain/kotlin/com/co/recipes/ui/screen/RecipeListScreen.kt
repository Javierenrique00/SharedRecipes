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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.co.recipes.domain.dataclass.Recipe
import com.co.recipes.domain.dataclass.UserSession
import com.co.recipes.domain.viewmodel.RecipeListViewModel
import com.co.recipes.ui.common.EmptyState
import com.co.recipes.ui.common.ErrorState
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
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
                actions = {
                    Text(
                        text = "${session.displayName} · ${session.role.name.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val recipes = viewModel.recipes
            val error = viewModel.errorMessage

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

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            isAdmin = session.isAdmin,
                            onClick = { navController.navigate(RecipeDetail(recipe.id)) },
                            onDelete = { viewModel.delete(recipe.id) },
                        )
                    }
                }
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

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                recipe.totalMinutes?.let {
                    Text("$it min", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "${recipe.ingredients.size} ingredients",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "${recipe.comments.size} comments",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (recipe.youtubeId != null) {
                    Text("video", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}
