# Shared Recipes

A shared recipe collection with two roles:

| | Admin (`admin`) | Client (`client`) |
|---|---|---|
| Read recipes | ✅ | ✅ |
| Create / edit / delete recipes | ✅ | ❌ |
| Read comments | ✅ | ✅ |
| Comment | ✅ | ✅ (own comments only) |
| Moderate comments | ✅ | ❌ |

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
supabase/migrations/          SQL migrations (schema, RLS, atomic write RPCs)
supabase/seed.sql             Sample recipe (Pad Thai)
shared/                       Kotlin Multiplatform module — UI + data layer
  src/commonMain/             Compose UI, view models, API clients, DTOs
  src/androidMain/            Android Activity bridge
  src/iosMain/                UIViewController bridge
  src/jvmMain/                Desktop entry point (main.kt)
  src/wasmJsMain/             Browser entry point (main.kt), localStorage session store and
                              the index.html host page
androidApp/                   Android application module (AGP 9 requires the split)
gradle/libs.versions.toml     Single source of truth for dependency versions
```

## Toolchain (verified working together)

| Component | Version | Note |
|---|---|---|
| Kotlin | 2.4.21 | minimum required by supabase-kt 3.9.0 (it is compiled with 2.4.21) |
| supabase-kt | 3.9.0 (`auth-kt`, `postgrest-kt`) | |
| Compose Multiplatform | 1.12.2 | |
| Ktor | 3.6.1 | |
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
before the Compose tree starts; calling `App()` without it fails fast with a clear
message rather than silently losing the session.

Web note: browsers give scripts no keychain and `SecureStore` is synchronous, so the web
build keeps the session in plaintext `localStorage` — the same trade-off as Desktop, but
with a real XSS surface because any script on the app's origin can read it. Nothing else is
persisted (still no password), `localStorage` is per-origin, and every access is guarded so
private-mode or quota failures fall back to "sign in again" instead of crashing.

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
- `create_recipe` / `update_recipe` / `delete_recipe` / `restore_recipe` run as
  `SECURITY INVOKER`, so RLS still applies to the caller; a non-admin calling them
  directly is rejected by the policies on `recipes` (for the two `update`-based ones
  the call simply touches zero rows, i.e. the recipe stays in place and stays visible).
