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
- `create_recipe` / `update_recipe` run as `SECURITY INVOKER`, so RLS still applies to the
  caller; a non-admin calling them directly is rejected by the policy on `recipes`.
