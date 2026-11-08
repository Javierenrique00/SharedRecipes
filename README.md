# Shared Recipes

A shared recipe collection with two roles:

| | Admin (`admin`) | Client (`client`) |
|---|---|---|
| Read recipes | ✅ | ✅ |
| Create / edit / delete recipes | ✅ | ❌ |
| Read comments | ✅ | ✅ |
| Comment | ✅ | ✅ (own comments only) |
| Moderate comments | ✅ | ❌ |
| Export a recipe to PDF / Markdown | ✅ | ✅ |
| Backup / restore / clear recipes | ✅ | ❌ |

Recipes hold an optional YouTube video, an ordered ingredient ("components") list
and an ordered procedure ("method").

Authorization is enforced **in Postgres** (Row Level Security), not in the app. The
KMP client ships only the publishable key, so a modified client cannot exceed these
rules.

Deleting is undoable. An admin's delete is a **soft** delete — `delete_recipe` stamps
`public.recipes.deleted_at` instead of removing the row — so the list can show an
**Undo** snackbar and `restore_recipe` simply clears the column. The recipe comes back
with the same id, ingredients, steps and comments (including comments written by other
users, which RLS would never let the admin re-create). Reads filter
`deleted_at is null`, and the select policy hides deleted recipes from clients; the
`delete` policy is kept so an admin can still purge a row for real.

## Repository layout

```
supabase/migrations/          SQL migrations (schema, RLS, atomic write RPCs, admin backup)
supabase/seed.sql             Sample recipe (Pad Thai)
branding/                     Icon source artwork + the transparent master it generates
scripts/                      generate-app-icons.sh — every platform icon, from that artwork
shared/                       Kotlin Multiplatform module — UI + data layer
  src/commonMain/             Compose UI, view models, API clients, DTOs
  src/androidMain/            Android Activity bridge
  src/iosMain/                UIViewController bridge
  src/jvmMain/                Desktop entry point (main.kt) + the window icon it loads
  src/wasmJsMain/             Browser entry point (main.kt), localStorage session store and
                              the index.html host page + favicons/web manifest
  icons/                      .icns/.ico/.png handed to jpackage for the desktop builds
androidApp/                   Android application module (AGP 9 requires the split)
gradle/libs.versions.toml     Single source of truth for dependency versions
```

Five packages carry the export/administration features, kept out of the UI on purpose:

| Package (`shared/src/commonMain/...`) | What lives there |
|---|---|
| `domain/backup` | `RecipeBackupCodec` — reads/writes the admin backup file (pure Kotlin) |
| `domain/export` | Markdown renderer, file-name slugs, the `RecipeExporter` contract, export timestamps |
| `infraestructure/files` | `DocumentFileStore` on top of FileKit: open/save dialogs, `expect`ed save on the web |
| `infraestructure/pdf` | The hand-written PDF writer (text layout, pages, xref) — see *Exporting a recipe* |
| `infraestructure/export` | `RecipeExporterImpl`: wires a recipe to Markdown bytes or PDF bytes and saves it |


## Toolchain (verified working together)

| Component | Version | Note |
|---|---|---|
| Kotlin | 2.4.21 | minimum required by supabase-kt 3.9.0 (it is compiled with 2.4.21) |
| supabase-kt | 3.9.0 (`auth-kt`, `postgrest-kt`) | |
| Compose Multiplatform | 1.12.2 | |
| Ktor | 3.6.1 | |
| FileKit | 0.17.0 (`filekit-dialogs`) | Open/save dialogs for the backup and the exports: Android SAF, iOS Files picker, AWT on the desktop, a download in the browser |
| AGP | 9.5.1 | CMP 1.12 depends on `lifecycle 2.11`, which requires AGP ≥ 9.1 |
| Gradle | 9.8.2 | |
| Android | minSdk 26, compileSdk 37 | supabase-kt requires minSdk 26 |

A few structural consequences worth knowing before you change the build:

1. **AGP 9 removed support for `com.android.application` + `org.jetbrains.kotlin.multiplatform`
   in the same module.** That is why `shared` (KMP library, `androidLibrary {}`) and
   `androidApp` (application) are separate modules.
2. **AGP 9 has built-in Kotlin.** Do not add `org.jetbrains.kotlin.android`; apply only
   `com.android.application`.
3. **`iosX64` is not a target.** Compose Multiplatform 1.12.x publishes no iosX64
   artifacts. Use `iosSimulatorArm64` for the simulator.
4. **The browser target is `wasmJs`, not `js(IR)`.** Compose Multiplatform only reaches the
   web through Kotlin/Wasm, and every dependency above publishes a `wasm-js` artifact at the
   pinned versions, so the web build shares the same `App()` composable as the native apps.

## Configure

1. Create/choose a Supabase project and copy from **Project Settings → API**:
   - URL (`https://<project-ref>.supabase.co`)
   - publishable key (`sb_publishable_...`, or the legacy anon key)
2. Paste both into
   `shared/src/commonMain/kotlin/com/co/recipes/infraestructure/SupabaseConfig.kt`.
   Until you do, the app shows a "Supabase is not configured" screen instead of crashing.

   > Use **only** the publishable/anon key. The `service_role` key bypasses RLS and must
   > never ship inside a client.

3. Apply the migrations (`supabase/migrations` in filename order) and optionally
   `supabase/seed.sql`.

4. Admins are decided by an **email allowlist**, so a fresh project needs no manual
   role surgery:

   ```sql
   insert into public.admin_emails (email, note)
   values ('you@example.com', 'owner');
   ```

   The `handle_new_user` trigger reads that table at sign-up, so any account created
   with an allowlisted email starts as `admin` (and keeps the normal `client` role
   otherwise). Migration 7 seeds the project owner's address.

   Promoting an account that already exists:

   ```sql
   update public.profiles set role = 'admin' where lower(email) = 'you@example.com';
   ```

   Removing an email from `admin_emails` only affects *future* sign-ups; demoting an
   existing admin is an explicit
   `update public.profiles set role = 'client' where lower(email) = '...'`.

   `public.admin_emails` has RLS enabled with no policies and no grants to
   `anon`/`authenticated`, so the app can never read or enumerate it — manage it from
   the Dashboard SQL editor (or `service_role`).

   The role is cached in the client when the session resolves: restart the app, or
   sign out and back in, to pick up a role change made while it is running.

## Run

```bash
./gradlew :shared:run                 # Desktop (fastest feedback)
./gradlew :androidApp:assembleDebug   # Android APK
./gradlew :shared:compileKotlinIosSimulatorArm64   # iOS framework
./gradlew :shared:wasmJsBrowserDevelopmentRun      # Browser at http://localhost:8080
./gradlew :shared:wasmJsBrowserDistribution        # Static site in shared/build/dist/wasmJs/productionExecutable
```

The web build is the same `App()` rendered with Skiko on a `<canvas>`; `index.html` lives in
`shared/src/wasmJsMain/resources` and loads `composeApp.js`. `wasmJsBrowserDevelopmentRun`
starts webpack-dev-server and opens <http://localhost:8080> in your default browser, which is
the fastest way to see the app. The distribution folder is plain static files — serve it
over HTTP (`python3 -m http.server 8080 --directory shared/build/dist/wasmJs/productionExecutable`),
never through `file://`, because the wasm modules must be delivered as `application/wasm`.

Calls to Supabase are ordinary HTTPS requests and work from the browser as-is (the project
serves CORS headers). Only e-mail flows depend on the origin: if sign-up confirmation is
enabled, add `http://localhost:8080` (plus your production origin) to
**Authentication → URL Configuration** in the dashboard.

### Browser navigation (web only)

The UI is drawn on a canvas, so screens are not paths in the URL. On the browser target the
controller is bound to window history (`bindToBrowserNavigation()` in `wasmJsMain/main.kt`),
which changes that:

- the current screen is appended as a `#fragment`, e.g.
  `…/SharedRecipes/#com.co.recipes.ui.navigation.RecipeDetail/<id>`;
- Back/Forward walk the same screens as the in-app back button;
- pasting such a URL reopens that screen — after signing in, because the navigation graph only
  exists for an authenticated session (`App.kt` gates it), so a link can never skip the login;
- only the fragment changes, so the path stays `/SharedRecipes/` and a refresh always gets
  `index.html` — no rewrite or 404 rules are needed on the host.

The fragment uses the type-safe route name. `bindToBrowserNavigation(getBackStackEntryRoute)`
can shorten those (e.g. `#recipe/<id>`), but then hand-typed URLs have to be parsed and routed
by the app itself. Binding is guarded with a flag so it runs once per page load: the API
installs a `popstate` listener and offers no way to remove it, so a second bind (sign out, sign
in again) would handle every Back/Forward twice.

### Publish the web build

`.github/workflows/publish-web.yml` publishes the same build two ways:

| Trigger | Result |
|---|---|
| push to `main` (or a manual run on `main`) | `deploy-pages` serves the folder at `https://javierenrique00.github.io/SharedRecipes/` |
| push of a `v*` tag | `build` attaches `shared-recipes-web-<tag>.zip` to the Release, with generated notes |

```bash
git push origin main              # ship the current code to the live site
git tag v1.0.0 && git push origin v1.0.0   # cut a downloadable Release
```

The split exists because the auto-created `github-pages` environment restricts deployments to
the `main` **branch**: a run triggered by a tag is rejected before its first step, with no
logs to show for it. Tagging therefore only cuts the Release, while the site follows `main`.
If you would rather deploy from tags, add a tag rule under **Settings → Environments →
github-pages → Deployment branches and tags**, then drop the `if: github.ref_type == 'branch'`
on the `deploy-pages` job.

Two things to know before the first run:

- **A Release cannot host the app.** Release assets are served as
  `application/octet-stream` with `Content-Disposition: attachment`, so a browser downloads
  `index.html` instead of rendering it — that is why the zip is the artefact and Pages is the
  live URL. Unzipped, the folder is still a plain static site (see the `python3 -m http.server`
  note above), and `index.html` sits at the zip root.
- **Pages must be enabled once**, under **Settings → Pages → Source: GitHub Actions**. This
  repository is public, and GitHub Pages is free for public repositories (only *private* ones
  need a paid plan), so the site lands at `https://javierenrique00.github.io/SharedRecipes/`.
  Then add that origin to **Authentication → URL Configuration** in Supabase, exactly like
  `localhost:8080`, or sign-up confirmation links will not come back to the app.

<details>
<summary>Publishing by hand instead (no CI)</summary>

```bash
./gradlew :shared:wasmJsBrowserDistribution
cd shared/build/dist/wasmJs/productionExecutable && zip -r /tmp/shared-recipes-web-v1.0.0.zip .
gh release create v1.0.0 /tmp/shared-recipes-web-v1.0.0.zip --generate-notes
```

</details>

The generated site is ~15 MB, almost all of it the two `.wasm` modules, and it needs no
custom headers: the bundle uses no `SharedArrayBuffer`, so COOP/COEP are not required. It
does need `application/wasm` for the modules (Skiko instantiates them with
`WebAssembly.instantiateStreaming`, which has no MIME fallback) — GitHub Pages serves that
type correctly, and the paths inside the bundle are relative, so the `/SharedRecipes/`
sub-path works without any base-path setting.

### Run the web build from Android Studio

Android Studio has no Kotlin/Wasm debugger, so the browser target gets a **Gradle** run
configuration instead of a native one. The project ships it as
`.idea/runConfigurations/shared__wasmJs__web.xml`, so it appears in the dropdown next to the
Android and iOS configurations:

1. Pick **shared [wasmJs] web** in the run-configuration dropdown and press **Run** (green
   arrow). webpack-dev-server compiles the bundle, serves it on <http://localhost:8080> and
   opens that page in the default browser (`devServer.open` in `shared/build.gradle.kts`).
2. Stop it with the red button in the *Run* tool window. Only one process can hold port
   8080, so a second run fails while the first is still alive.

If the configuration is missing (e.g. `.idea` was deleted), recreate it with
**Run → Edit Configurations → + → Gradle**: *Gradle project* `shared`, *Tasks*
`wasmJsBrowserDevelopmentRun`. Double-clicking that task in the *Gradle* tool window does
the same without a run configuration.

Two limits worth knowing:

- **Debug** does not apply to this target: the IDE debugger attaches to a JVM, not to a
  `.wasm` module. Use Chrome DevTools → *Sources* instead — the dev server emits source
  maps, so Kotlin frames are steppable there.
- The Kotlin code is compiled by the Gradle task, so a source change needs the
  configuration re-run (the page reloads from the new bundle). For a compile-on-save loop,
  keep `./gradlew :shared:wasmJsBrowserDevelopmentRun --continuous` running in a terminal.

## App icons

One artwork drives the launcher icon on all four targets:
`branding/app-icon-source.jpeg`. Nothing is hand-edited — every file below is produced
by

```bash
./scripts/generate-app-icons.sh          # requires ImageMagick 7, plus iconutil (macOS) for the .icns
```

so re-branding the app is "drop in a new JPEG, re-run the script".

**The white background is converted, not cropped.** The source is an opaque JPEG: a
rounded-square cartoon sitting on a flat white canvas, taking up only the middle
663 px of the 1024 px frame. The script flood-fills white *and* near-white to alpha
from the four corners (`-fuzz 12%`, calibrated against the artwork's lightest pixel
`#FBD58C`), which is what lets the same drawing float on the macOS Dock, the Windows
Start menu, a browser tab bar and Android's adaptive foreground layer without a white
square behind it. Verified clean at 500 % zoom on a dark backdrop, so no de-fringe pass
is needed. The crop is padded back to a square so every resize stays exact.

| Target | File(s) | Alpha | Why |
|---|---|---|---|
| iOS | `iosApp/…/AppIcon.appiconset/app-icon-1024.png` | **opaque** | Apple rejects transparency, so the rounded corners are filled with a blurred copy of the artwork (full bleed) |
| Android | `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` + `res/drawable-*dpi/ic_launcher_foreground.png` + `res/drawable/ic_launcher_background.xml` | foreground transparent | adaptive icon, see below |
| Desktop | `shared/icons/app-icon.{icns,ico,png}` | transparent | jpackage icon per OS; the drawing floats on Apple's grid (824 px body) with a soft shadow |
| Desktop (`run`) | `shared/src/jvmMain/resources/app-icon.png` | transparent | window icon, see the note below |
| Web | `favicon.ico`, `favicon-{16,32}.png`, `icon-{192,512}.png` | transparent | legible on light *and* dark tab bars |
| Web (PWA) | `apple-touch-icon.png`, `icon-maskable-512.png`, `site.webmanifest` | **opaque** | iOS composites alpha onto black; a maskable icon needs an opaque 80 % safe zone |

### Android adaptive layers

`minSdk` is 26, which is exactly where adaptive icons start, so the XML is the only form
Android needs — there are no legacy `mipmap-*dpi/ic_launcher.png` fallbacks. (An `anydpi`
resource always wins over a density bucket at runtime, and lint flags the duplication, so
they would be dead weight in the APK.) Two layers:

- **background** — a vertical gradient in the artwork's own edge colours (`#FBD58C` →
  `#FE7257`). It fills whatever the launcher's mask cuts away (circle, squircle, rounded
  square) with something that belongs to the drawing.
- **foreground** — the drawing at **70 %** of the 108 dp layer, centred, corners
  transparent, so the gradient fills the space around it and the two layers read as one
  icon.

The 70 % is not cosmetic: an adaptive icon only ever shows the inner 72 dp of its 108 dp
layer (the outer 18 dp on each side exist for launcher parallax and are masked away), so a
foreground drawn at 100 % lost its outer third to the mask — the artwork's cream border
ring and corner sparkles were never visible and the icon read as zoomed in. At 70 % the
drawing is 75.6 dp, which brings the border back on every launcher shape; the last
millimetre at the edge midpoints still sits under a circular mask, so drop
`ANDROID_FOREGROUND_SCALE` in `scripts/generate-app-icons.sh` to ~61 (the 66 dp safe zone)
if a fully framed icon is preferred over a fuller one, and raise it back towards 100 for
the old full-bleed crop.

There is deliberately no `monochrome` layer: Android 13+ themed icons want a
single-colour silhouette, and the only silhouette this artwork has is the rounded square
itself — tinting that produces a blob. Without the layer, Android 16 QPR 2 and above tint
the colour version instead, which looks better than a meaningless shape, so
`MonochromeLauncherIcon` is suppressed with `tools:ignore` in both XMLs (lint stays
clean and the decision is recorded where the layer would live).

### Desktop and web notes

- jpackage takes one image per OS, wired in `compose.desktop.nativeDistributions`
  (`macOS`/`windows`/`linux { iconFile … }`). They live in `shared/icons/` rather than
  `src/jvmMain/resources` on purpose: only the window icon has to be on the classpath,
  and `.icns`/`.ico` would just be dead weight inside the jar.
- `./gradlew :shared:run` is not a jpackage bundle and would show Compose's default
  icon, so `main.kt` loads `app-icon.png` and passes it to `Window`. It is decoded with
  `ImageIO`/`toComposeImageBitmap` rather than `painterResource(String)` because that
  overload is deprecated in Compose Multiplatform 1.12 in favour of the Compose
  resources library — which would package this desktop-only image into the Android and
  web artifacts too. On macOS the *Dock* keeps the JVM icon until the packaged `.app`
  is launched; that is a platform limitation, not a bug.
- The web files are authored in `shared/src/wasmJsMain/resources`, which
  `wasmJsBrowserDistribution` copies verbatim, so the existing Pages workflow ships them
  with no extra step. Every `href` in `index.html` is **relative** (no leading slash)
  because the site is served under `/SharedRecipes/`.
- The icons are committed binaries (~8 MB together, the 2 MB transparent master and the
  2 MB `.icns` being most of it) because they are build inputs no tool regenerates on its
  own. Deleting them and re-running the script reproduces them.

Two caveats worth knowing: the artwork occupies 664 px of the 1024 px source, so the
1024 px icons are a ~1.54× Lanczos upscale (invisible on flat cartoon art at icon sizes,
but a higher-resolution source would be sharper if one exists); and an already-installed
app keeps its cached launcher icon until it is reinstalled or the launcher cache clears.

## Light / dark theme

The UI follows the operating system's appearance on **all four targets**; there is no in-app
switch and nothing to configure. `App()` wraps the whole tree in `SharedRecipesTheme`
(`shared/src/commonMain/.../ui/theme/Theme.kt`), which picks `lightColorScheme()` or
`darkColorScheme()` from `isSystemInDarkTheme()`. No screen hardcodes a color — every
`Scaffold`, `Card`, `OutlinedTextField` and `MaterialTheme.colorScheme.*` read is role-based —
so adding the two schemes is what makes the entire UI dark-mode aware.

`isSystemInDarkTheme()` is common-code API in Compose Multiplatform 1.12.2, implemented per
platform, and it tracks the setting **while the app is running**:

| Target | Source of truth | Live switch |
|---|---|---|
| Android | `Configuration.uiMode` | ✅ — `MainActivity` lists `uiMode` in `configChanges`, so the Activity is not recreated |
| iOS | `UITraitCollection.userInterfaceStyle` — Compose's `ComposeContainer` re-provides it on every trait change | ✅ |
| Desktop (JVM) | skiko's system theme, polled once per second | ✅ macOS/Windows. Upstream disables the polling on **Linux**, where the app stays light unless `ComposeUiFlags.pollSystemTheme = true` is set in `jvmMain/main.kt` |
| Web (`wasmJs`) | `window.matchMedia("(prefers-color-scheme: dark)")` plus a `change` listener | ✅ |

Only the *pre-paint* window is styled outside Compose, so booting never flashes the wrong
color. These two pairs must stay equal to the Material3 baseline `background` role:

| Target | Light | Dark | Where |
|---|---|---|---|
| Android | `#FEF7FF` | `#141218` | `androidApp/src/main/res/values{,-night}/colors.xml`, referenced as `window_background` by `values{,-night}/themes.xml` (`Theme.SharedRecipes`, also flips `windowLightStatusBar` so the edge-to-edge status-bar icons stay readable) |
| Web | `#FEF7FF` | `#141218` | `shared/src/wasmJsMain/resources/index.html` (`color-scheme`, `theme-color`, `@media (prefers-color-scheme: dark)`) |

iOS needs nothing: `Info.plist` has no `UIUserInterfaceStyle`, so the app follows the system and
`ComposeContainer` handles trait changes on its own. Desktop window decorations are drawn by the
OS and already match.

Two notes for whoever changes the palette later:

- If `SharedRecipesTheme` ever moves off the Material3 baseline colors, update the `#FEF7FF` /
  `#141218` values above too — they are the only duplicated colors in the project.
- `SharedRecipesTheme(darkTheme = true)` is the escape hatch for a preview or a test that needs a
  fixed appearance; production code always uses the default (system) detection.

## Keyboard (IME) on Android

The recipe editor is where this matters most, but the rule applies to every form: **the keyboard
must never move the layout**. Two pieces make that true, and both are needed:

| Piece | Where | What it does |
|---|---|---|
| `android:windowSoftInputMode="adjustResize"` | `androidApp/src/main/AndroidManifest.xml` (`MainActivity`) | Stops the system from *panning* the window to keep the caret visible. |
| `Modifier.screenContentPadding(padding)` | `shared/src/commonMain/.../ui/common/Insets.kt`, used by the scrollable body of every `Scaffold` that contains a text field (recipe editor, recipe detail comment box, login) | Applies the Scaffold padding, consumes it (so the keyboard inset is not stacked on top of the nav-bar inset), then adds `imePadding()`. |

Why both: the app targets SDK 37, where edge-to-edge is enforced, so the window is **not** resized
for the keyboard. Without `adjustResize` the framework pans the whole window — and recomputes that
pan every time typing changes the layout, which is what made content slide over the editor's cells.
Without `imePadding()` the focused field stays underneath the keyboard, and Compose's
`bringIntoView` keeps re-scrolling to chase the caret. Together they produce a viewport that ends
where the keyboard starts: a tapped cell scrolls up once, smoothly, and then stays still.

`enableEdgeToEdge()` in `MainActivity` makes API 26–34 report the same insets as Android 15+, so
this one rule covers the whole supported range. `imePadding()` is common-code API in Compose
Multiplatform 1.12.2 (real on Android/iOS, a no-op on desktop and the browser), so no screen needs
a platform check.

Two smaller rules keep the editor from twitching on its own:

- Every single-line cell uses `imeAction = ImeAction.Next` together with `nextCellActions()`
  (`RecipeEditorSections.kt`), which moves focus instead of closing the keyboard; closing it
  re-runs the IME animation between cells.
- Multi-line fields are capped (`Description` `maxLines = 6`, step fields `maxLines = 4`) so the
  rows below them stop re-flowing on every wrapped line.

## Sessions and local storage

Signing in persists an encrypted session, so the app re-opens on the recipe list
without asking for the password again. "Remember my email" only pre-fills the email
field — **the password is never stored anywhere**, because the Supabase refresh token
already keeps the user signed in and a plaintext password on disk would add breach
surface for no benefit.

| Platform | Where the session lives | Encrypted |
|---|---|---|
| Android | `secure_session` SharedPreferences file, AES-256-GCM under a non-exportable Android Keystore key | ✅ |
| iOS | Keychain, `kSecClassGenericPassword`, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (no iCloud sync) | ✅ |
| Desktop (dev harness) | `java.util.prefs` node `com/co/recipes/session` | ❌ the JDK has no keychain API |
| Web (browser) | `localStorage`, key `sb-<project-ref>-session` | ❌ browsers expose no keychain API (see the web note below) |

Implementation lives in `shared/src/commonMain/.../infraestructure/security`
(`SecureSessionManager` + a 3-method `SecureStore`), with one small actual per target:
`AndroidSecureStore` (Keystore), `KeychainSecureStore` (Security framework),
`DesktopSecureStore` and `WebSecureStore`. Remembered, non-secret values (`auth.remember_me`,
`auth.last_email`, `auth.last_display_name`) are plain preferences via
`UserPreferences` — deliberately a separate store from the tokens.

Upgrading from a build that used supabase-kt's `SettingsSessionManager` migrates the
old plaintext `session` entry into the secure store once and then deletes it, so
nobody is signed out by the switch.

Android note: `startSharedRecipes()` calls `SecureStoreInitializer.init(applicationContext)`
before the Compose tree starts, and `FileKit.init(this)` so the file dialogs used by the
admin backup and the exports can reach the Activity's `ActivityResultRegistry`. Calling
`App()` without the first one fails fast with a clear message rather than silently losing
the session; without the second, opening a dialog throws `FileKitNotInitializedException`.

Web note: browsers give scripts no keychain and `SecureStore` is synchronous, so the web
build keeps the session in plaintext `localStorage` — the same trade-off as Desktop, but
with a real XSS surface because any script on the app's origin can read it. Nothing else is
persisted (still no password), `localStorage` is per-origin, and every access is guarded so
private-mode or quota failures fall back to "sign in again" instead of crashing.
## Recipe administration (admin)

The **Admin** action in the recipe list's top bar opens a console with four cards. It is
only rendered for admins, and every call behind it is admin-checked in Postgres
(`public.is_admin()`), so hiding the button is convenience, not security.

| Action | What happens |
|---|---|
| **Backup** | `admin_export_recipes()` returns the whole collection as one `jsonb` array (soft-deleted recipes, ingredients, steps and comments included) and the app writes it to `recipes-backup-<date>.json` through the save dialog. |
| **Restore** | Pick a backup file → every recipe in it is listed with a checkbox and a `new` / `update` badge → only the ticked ones are imported (`admin_import_recipes`). |
| **Deleted recipes** | Soft-deleted recipes are listed with checkboxes; `admin_restore_recipes` clears `deleted_at` on the selected ones (or all of them). |
| **Clear** | `admin_clear_recipes`: *soft* by default (recoverable, shows up in the deleted list), or permanent after typing `DELETE`. |

Why restoring is not "insert the rows again": `admin_import_recipes` upserts **on the
recipe id** and clears `deleted_at`, so importing the same file twice updates instead of
duplicating, and "clear everything, then restore the two I want" works — the recipes come
back with their original ids, so links and comment authorship keep pointing at the same
rows. Comments are re-attached only when the author profile still exists, and are never
overwritten (words written after the backup survive a restore). A row that cannot be
imported (bad number, over-long text) is skipped and counted instead of aborting the file.

The file is deliberately boring: the keys are the column names, so it is a transparent dump
of the tables that a DBA can read, diff and hand-edit.

```json
{
  "formatVersion": 1,
  "app": "Shared Recipes",
  "exportedAt": "2026-11-08T09:41:12Z",
  "recipes": [
    {
      "id": "3f7c…",
      "title": "Pad Thai",
      "description": "Rice noodles with tamarind.",
      "youtube_url": "https://www.youtube.com/watch?v=BWk1C0dMj8o",
      "servings": 2, "prep_minutes": 20, "cook_minutes": 10,
      "created_at": "2026-11-01T10:00:00Z",
      "deleted_at": null,
      "ingredients": [{ "position": 0, "quantity": 200, "unit": "g", "name": "flat rice noodles" }],
      "steps": [{ "step_number": 1, "instruction": "Soak the noodles." }],
      "comments": [{ "id": "9a1…", "author_id": "b02…", "body": "Extra lime.", "created_at": "…" }]
    }
  ]
}
```

`formatVersion` is checked on read: a file from a newer build is refused with an
explanation instead of being half-restored. Unknown keys are ignored, so adding fields
later does not break older builds.

## Exporting a recipe (PDF or Markdown)

Every signed-in user gets an **Export** action on the recipe screen, with two formats.
Both produce the same bytes on all four targets and both are saved through the platform's
own "save as" flow (Android SAF, iOS Files picker, AWT dialog) or the browser's download —
no permissions, no FileProvider for these two, nothing to install.

**Markdown** (`domain/export/RecipeMarkdown.kt`) is a plain CommonMark document: `#` title,
a meta list, `## Ingredients` as bullets, `## Method` as a numbered list, and a footer. It
opens correctly in Obsidian, Notion, GitHub and VS Code, and markdown-special characters in
a title are escaped so a recipe called `*Best* noodles` cannot corrupt the document.

**PDF** (`infraestructure/pdf`) is written by hand — about 300 lines producing a PDF 1.4
document with A4 pages, Helvetica base-14 fonts, word wrapping, hanging indents for numbered
steps, automatic page breaks and a per-page footer (`Shared Recipes · <date>` / `Page n
of m`). Text is therefore **selectable and searchable**, files are a few KB, and every
platform emits identical output.

Two deliberate choices there:

- **The base-14 fonts are not embedded.** That is what keeps the files tiny, but it limits
  the text to the WinAnsi (Latin-1) repertoire — Spanish, French, German and Portuguese all
  fit; emoji and CJK characters are replaced by `?` rather than mangling the file.
- **No PDF library.** The one Kotlin Multiplatform option (KmPDF 1.2.0) is compiled against
  an older Skiko and dies on Compose 1.12 with `NoSuchMethodError: Image.encodeToData`, so
  it was dropped after `RecipePdfTest` caught it. The hand-written writer has no dependency
  to drift and its output is covered by tests (header, page objects, xref offsets, trailer,
  and that every ingredient and step is really in the stream).

Recipe PDFs are *not* shared through the Android share sheet (they would need a
`FileProvider`); the user picks the destination once in the system save dialog, which is
also the only flow that works unchanged on the other three targets.



## Security model

- Admin is granted by the `public.admin_emails` allowlist, read by the sign-up trigger;
  that table has RLS enabled with no policies and no grants to `anon`/`authenticated`,
  so the app can neither read nor edit it, and clients still cannot touch
  `profiles.role` (column-level grant on `display_name` only).
- No credential is persisted: the encrypted session (see above) is what keeps a user
  signed in, and the password only lives in memory while it is typed.
- The browser build is the one exception to "encrypted at rest": `localStorage` has no
  keychain to hide behind (see *Sessions and local storage*). RLS still enforces every
  rule server-side, so a stolen session cannot do more than that user could.
- `public.is_admin()` is a `SECURITY DEFINER` helper with an empty `search_path`, used by
  policies so that rules on `profiles` can be evaluated without recursing.
- Clients cannot change their own `role`: `update` on `profiles` is granted **per column**
  (`display_name` only), so the column is unwritable regardless of any policy.
- The role is read from `profiles`, never from `raw_user_meta_data` (which users can edit).
- `create_recipe` / `update_recipe` / `delete_recipe` / `restore_recipe` /
  `admin_export_recipes` / `admin_restore_recipes` / `admin_clear_recipes` run as
  `SECURITY INVOKER`, so RLS still applies to the caller; a non-admin calling them
  directly is rejected by the policies on `recipes` (for the `update`-based ones the
  call simply touches zero rows, i.e. the recipe stays in place and stays visible).
- `admin_import_recipes` is the single `SECURITY DEFINER` write path, and it earns the
  exception: restoring a backup has to clear `deleted_at`, put back `created_at` and
  re-attach comments other users wrote — none of which RLS would let an invoker do (the
  comment insert policy requires `author_id = auth.uid()`). Its **first statement** is
  `if not public.is_admin() then raise exception … using errcode = '42501'`, and it pins
  `search_path = ''` like every other helper, so the elevation is gated by exactly the
  same rule the policies use. It is only reachable by `authenticated` (`anon` and `public`
  are revoked, as in migrations 5, 6 and 8).
