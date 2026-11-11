package com.co.recipes.domain.dataclass

enum class UserRole {
    ADMIN,
    CLIENT;

    val isAdmin: Boolean get() = this == ADMIN

    companion object {
        fun fromDb(value: String?): UserRole =
            if (value.equals("admin", ignoreCase = true)) ADMIN else CLIENT
    }
}

data class Ingredient(
    val quantity: Double?,
    val unit: String?,
    val name: String,
) {
    val display: String
        get() = listOfNotNull(
            quantity?.let { formatQuantity(it) },
            unit?.takeIf { it.isNotBlank() },
            name,
        ).joinToString(" ")
}

data class Step(
    val number: Int,
    val instruction: String,
    /** Storage object path of this step's photo, or null when it has none. */
    val imagePath: String? = null,
)

data class Comment(
    val id: String,
    val authorId: String,
    val body: String,
    val createdAt: String?,
)

data class Recipe(
    val id: String,
    val title: String,
    val description: String?,
    val youtubeId: String?,
    /** Storage object path of the cover photo (list thumbnail + detail header). */
    val imagePath: String? = null,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    val comments: List<Comment>,
) {
    val youtubeThumbnailUrl: String?
        get() = youtubeId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }

    val youtubeWatchUrl: String?
        get() = youtubeId?.let { "https://www.youtube.com/watch?v=$it" }

    val totalMinutes: Int?
        get() = listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum()
}

/** Draft used by the admin editor before it hits the database. */
data class RecipeDraft(
    val title: String,
    val description: String?,
    val youtubeUrl: String?,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
)

/** "2.5" -> "2.5", "2.0" -> "2" — avoids "2.0 eggs" in the ingredient list. */
fun formatQuantity(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}
