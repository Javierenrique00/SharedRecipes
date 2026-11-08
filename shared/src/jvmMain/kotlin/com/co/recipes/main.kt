package com.co.recipes

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import javax.imageio.ImageIO

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Shared Recipes",
        icon = windowIcon(),
    ) {
        App()
    }
}

/**
 * Window/task-bar icon for `./gradlew :shared:run`, which is not a jpackage bundle and
 * would otherwise show Compose's default icon. For a real distribution the .icns/.ico
 * configured in `compose.desktop.nativeDistributions` wins instead; macOS also keeps the
 * JVM's icon in the Dock until the packaged `.app` is launched.
 *
 * The PNG is committed at `src/jvmMain/resources/app-icon.png` (produced by
 * `scripts/generate-app-icons.sh`) and decoded with the JDK rather than
 * `androidx.compose.ui.res.painterResource(String)`: that overload is deprecated in
 * Compose Multiplatform 1.12 in favour of the Compose resources library, which would
 * package this desktop-only image into the Android and web artifacts as well.
 *
 * Falls back to no icon (rather than crashing) if the resource is ever missing.
 */
private fun windowIcon(): Painter? =
    object {}.javaClass.getResourceAsStream("/app-icon.png")
        ?.use { ImageIO.read(it) }
        ?.let { BitmapPainter(it.toComposeImageBitmap()) }
