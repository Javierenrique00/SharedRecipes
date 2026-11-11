package com.co.recipes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.co.recipes.domain.dataclass.UserSession
import com.co.recipes.domain.viewmodel.AdminRecipesViewModel
import com.co.recipes.domain.viewmodel.RecipeDetailViewModel
import com.co.recipes.domain.viewmodel.RecipeEditorViewModel
import com.co.recipes.domain.viewmodel.RecipeListViewModel
import com.co.recipes.infraestructure.api.RecipesApi
import com.co.recipes.infraestructure.export.RecipeExporterImpl
import com.co.recipes.infraestructure.storage.RecipeImageStore
import com.co.recipes.ui.screen.AdminRecipesScreen
import com.co.recipes.ui.screen.RecipeDetailScreen
import com.co.recipes.ui.screen.RecipeEditorScreen
import com.co.recipes.ui.screen.RecipeListScreen

/**
 * Signed-in navigation graph. Login is handled one level up in [com.co.recipes.App],
 * so there is no auth route and no way to deep-link past authentication.
 *
 * The controller is hoisted in rather than created here so the browser target can bind it
 * to the window history through [onNavHostReady]: that is what makes Back/Forward work and
 * turns the current screen into a shareable `#fragment`. Native targets leave the callback
 * at its default no-op.
 */
@Composable
fun RecipesNavigation(
    navController: NavHostController,
    recipesApi: RecipesApi,
    imageStore: RecipeImageStore,
    session: UserSession,
    onSignOut: () -> Unit,
    onNavHostReady: suspend (NavHostController) -> Unit = {},
) {
    // One exporter for the whole signed-in session: it holds no state, but keeping it
    // hoisted means the detail screen does not build a new one per navigation.
    val exporter = remember { RecipeExporterImpl(imageStore) }

    NavHost(navController = navController, startDestination = RecipeList) {
        composable<RecipeList> {
            val viewModel = viewModel { RecipeListViewModel(recipesApi) }
            RecipeListScreen(
                navController = navController,
                viewModel = viewModel,
                session = session,
                onSignOut = onSignOut,
            )
        }

        composable<RecipeDetail> { entry ->
            val route: RecipeDetail = entry.toRoute()
            val viewModel = viewModel(key = "detail-${route.recipeId}") {
                RecipeDetailViewModel(recipesApi, route.recipeId, exporter)
            }
            RecipeDetailScreen(
                navController = navController,
                viewModel = viewModel,
                session = session,
            )
        }

        // Admin console (backup / restore / clear). The route is only reachable from the
        // list screen's admin action, and every call behind it is admin-checked in
        // Postgres, so a hand-typed #fragment cannot do anything a client could not.
        composable<AdminRecipes> {
            val viewModel = viewModel { AdminRecipesViewModel(recipesApi, imageStore) }
            AdminRecipesScreen(navController = navController, viewModel = viewModel)
        }

        composable<RecipeEditor> { entry ->
            val route: RecipeEditor = entry.toRoute()
            val viewModel = viewModel(key = "editor-${route.recipeId ?: "new"}") {
                RecipeEditorViewModel(recipesApi, imageStore, recipeId = route.recipeId)
            }
            RecipeEditorScreen(
                navController = navController,
                viewModel = viewModel,
            )
        }
    }

    // Fires once the graph is attached to the controller. The browser entry point uses this
    // hook to bind the controller to window history (see wasmJsMain/main.kt).
    LaunchedEffect(navController) { onNavHostReady(navController) }
}
