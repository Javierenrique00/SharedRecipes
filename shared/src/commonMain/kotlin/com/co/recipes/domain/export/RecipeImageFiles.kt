package com.co.recipes.domain.export

import com.co.recipes.domain.dataclass.Recipe

/**
 * Relative names for a recipe's photos inside an exported `.zip`.
 *
 * Deterministic and human-friendly (`images/cover.jpg`, `images/step-2.jpg`) instead of the
 * random storage names: the Markdown links to these, and a person unzipping the document
 * should see which photo belongs to which step.
 */
fun recipeImageFileNames(recipe: Recipe): Map<String, String> = buildMap {
    recipe.imagePath?.takeIf { it.isNotBlank() }?.let { put(it, "$IMAGES_FOLDER/cover.jpg") }
    recipe.steps.sortedBy { it.number }.forEach { step ->
        step.imagePath?.takeIf { it.isNotBlank() }
            ?.let { put(it, "$IMAGES_FOLDER/step-${step.number}.jpg") }
    }
}

/** Folder inside the exported zip; also the prefix used in the Markdown links. */
const val IMAGES_FOLDER = "images"
