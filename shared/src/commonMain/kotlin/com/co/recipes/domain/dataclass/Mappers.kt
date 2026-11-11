package com.co.recipes.domain.dataclass

import com.co.recipes.infraestructure.dataclass.CommentDto
import com.co.recipes.infraestructure.dataclass.IngredientDto
import com.co.recipes.infraestructure.dataclass.ProfileDto
import com.co.recipes.infraestructure.dataclass.RecipeDto
import com.co.recipes.infraestructure.dataclass.StepDto

fun RecipeDto.toDomain(): Recipe = Recipe(
    id = id ?: error("Recipe payload without an id"),
    title = title,
    description = description,
    youtubeId = youtubeId,
    imagePath = imagePath,
    servings = servings,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    // PostgREST does not guarantee child ordering, so sort explicitly.
    ingredients = ingredients.sortedBy { it.position ?: 0 }.map { it.toDomain() },
    steps = steps.sortedBy { it.stepNumber }.map { it.toDomain() },
    comments = comments.sortedBy { it.createdAt ?: "" }.map { it.toDomain() },
)

fun IngredientDto.toDomain(): Ingredient = Ingredient(
    quantity = quantity,
    unit = unit,
    name = name,
)

fun StepDto.toDomain(): Step = Step(
    number = stepNumber,
    instruction = instruction,
    imagePath = imagePath,
)

fun CommentDto.toDomain(): Comment = Comment(
    id = id ?: "",
    authorId = authorId,
    body = body,
    createdAt = createdAt,
)

fun ProfileDto.toSession(): UserSession = UserSession(
    userId = id,
    displayName = displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@'),
    role = UserRole.fromDb(role),
)
