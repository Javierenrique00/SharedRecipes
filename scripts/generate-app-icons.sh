#!/usr/bin/env bash
#
# Regenerates every app icon Shared Recipes ships, from one source image:
#
#     branding/app-icon-source.jpeg
#
# That file is a 1024x1024 JPEG: a rounded-square cartoon drawn on a flat white
# canvas, the drawing itself taking up only the middle ~663 pixels. Two properties
# of that source drive the whole pipeline:
#
#   * It is opaque (no alpha channel), so the white background is *converted* to
#     transparency with a flood fill rather than merely cropped away. That is what
#     lets the same artwork float on any surface it lands on: the macOS Dock, the
#     Windows Start menu, a browser tab bar, and Android's adaptive foreground
#     layer (whose background layer supplies the fill).
#   * Its lightest pixel is #FBD58C, so `-fuzz 12%` clears white *and* near-white
#     without ever creeping into the drawing. The edge is verified clean (no white
#     fringe) on a dark backdrop, so no erosion pass is needed.
#
# Everything here is derived from that one transparent master, which means
# re-branding the app is: drop in a new JPEG, re-run this script.
#
# Outputs (committed to the repo):
#   branding/app-icon-1024.png                            transparent master
#   iosApp/.../AppIcon.appiconset/app-icon-1024.png       opaque full-bleed (Apple rejects alpha)
#   androidApp/src/main/res/drawable-*dpi/ic_launcher_foreground.png
#       (the adaptive XML in res/mipmap-anydpi/ and the gradient in res/drawable/ are
#        hand-written, not generated — only the bitmap layer comes from here)
#   shared/icons/app-icon.icns | .ico | .png              jpackage icons (macOS/Windows/Linux)
#   shared/src/jvmMain/resources/app-icon.png             window icon for `./gradlew :shared:run`
#   shared/src/wasmJsMain/resources/favicon*,icon-*,apple-touch-icon.png,site.webmanifest
#
# Requirements: ImageMagick 7 (`magick`). The .icns step also needs `sips` and
# `iconutil`, which ship with macOS, so run this on macOS.
#
# Usage: ./scripts/generate-app-icons.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/branding/app-icon-source.jpeg"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Edge colours of the drawing, reused as the Android adaptive background so the
# foreground layer blends into it. Keep in sync with
# androidApp/src/main/res/drawable/ic_launcher_background.xml.
WARM_YELLOW='#FBD58C'
CORAL='#FE7257'

command -v magick >/dev/null 2>&1 || { echo "error: ImageMagick 7 ('magick') is required" >&2; exit 1; }
[ -f "$SRC" ] || { echo "error: missing source artwork: $SRC" >&2; exit 1; }

log() { printf '  %s\n' "$*"; }

# Resize a square PNG and strip metadata, which is a large share of the bytes.
# `-depth 8` is explicit because some ImageMagick operators (gradient: among them)
# otherwise hand back 16-bit channels, doubling the file for no visual gain.
png() { magick "$1" -filter Lanczos -resize "${2}x${2}" -depth 8 -strip -define png:compression-level=9 "$3"; }

# ---------------------------------------------------------------------------
# 1. White -> transparent, then crop to the drawing.
# ---------------------------------------------------------------------------
read -r W H <<EOF
$(magick identify -format '%w %h' "$SRC")
EOF
log "source ${W}x${H}, converting white background to alpha"

magick "$SRC" -alpha set -fuzz 12% -fill none \
    -draw "color 0,0 floodfill" \
    -draw "color $((W - 1)),0 floodfill" \
    -draw "color 0,$((H - 1)) floodfill" \
    -draw "color $((W - 1)),$((H - 1)) floodfill" \
    "$WORK/transparent.png"

magick "$WORK/transparent.png" -trim +repage "$WORK/art.png"

# The crop lands 1px short on one axis (663x662) because the JPEG edge falls
# mid-pixel; pad it back to a square so every resize below stays exactly square.
read -r AW AH <<EOF
$(magick identify -format '%w %h' "$WORK/art.png")
EOF
SIDE=$((AW > AH ? AW : AH))
magick "$WORK/art.png" -background none -gravity center -extent "${SIDE}x${SIDE}" \
    "$WORK/art-square.png"
ART="$WORK/art-square.png"
log "artwork cropped to ${SIDE}x${SIDE}, corners transparent"

# ---------------------------------------------------------------------------
# 2. Derive the three forms every platform is built from.
# ---------------------------------------------------------------------------
# (a) Master: the drawing filling a 1024 canvas, corners transparent.
magick "$ART" -filter Lanczos -resize 1024x1024 \
    -background none -gravity center -extent 1024x1024 \
    -strip -define png:compression-level=9 "$ROOT/branding/app-icon-1024.png"

# (b) Opaque and edge-to-edge, for platforms that reject or ignore alpha (iOS).
# The four rounded-corner slivers are filled with a heavily blurred, stretched
# copy of the drawing, so the seam is invisible but every pixel stays opaque.
magick "$ART" -background "${CORAL}" -alpha remove -alpha off \
    -resize 1024x1024! -blur 0x80 "$WORK/backdrop.png"
magick "$ART" -filter Lanczos -resize 1024x1024 \
    -background none -gravity center -extent 1024x1024 "$WORK/art-1024.png"
magick "$WORK/backdrop.png" "$WORK/art-1024.png" -compose over -composite -alpha off \
    "$WORK/fullbleed.png"

# (c) Floating rounded icon on a transparent canvas, sized to Apple's icon grid
# (824 px body in a 1024 canvas) with a soft shadow. This is what desktop OSes
# expect: a hard square looks wrong in the Dock and the Start menu.
magick "$ART" -filter Lanczos -resize 824x824 \
    \( +clone -background black -shadow 35x12+0+12 \) \
    +swap -background none -layers merge +repage \
    -gravity center -background none -extent 1024x1024 "$WORK/padded.png"

# ---------------------------------------------------------------------------
# 3. iOS: one universal 1024 slot, opaque.
# ---------------------------------------------------------------------------
log "iosApp/.../AppIcon.appiconset/app-icon-1024.png"
magick "$WORK/fullbleed.png" -strip -define png:compression-level=9 \
    "$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"

# ---------------------------------------------------------------------------
# 4. Android.
#
# Adaptive icons (API 26+, which is this app's minSdk) are drawn on a 108dp
# canvas that the launcher masks into a circle/squircle/rounded square. Only the
# inner 72dp of that canvas is visible, so a foreground drawn at 100% gets its
# outer third cropped by every mask — which is exactly what made the icon look
# zoomed in, with the artwork's cream border ring and corner sparkles cut off.
# The drawing is therefore scaled to ANDROID_FOREGROUND_SCALE of the layer and
# centred on it, so the whole rounded square survives the mask and only the
# gradient background reaches the edge. Corners stay transparent, so the two
# layers still read as one icon.
#
# Only the layers are generated. No legacy mipmap-*dpi/ic_launcher.png fallbacks:
# with minSdk 26 the anydpi XML always wins over a density bucket, so those PNGs
# would be dead weight that lint flags (IconXmlAndPng).
# ---------------------------------------------------------------------------
RES="$ROOT/androidApp/src/main/res"

# How much of the 108dp layer the drawing fills, in percent. 70 leaves the
# artwork inside the 72dp visible area (70% = 75.6dp, so only the outermost
# sliver of the border is shaved, at the edge midpoints of a circular mask),
# which is what makes the border ring and the sparkles next to it visible on
# every launcher shape. Raise it towards 100 for a tighter, full-bleed crop;
# drop it to ~61 to sit inside the 66dp safe zone instead.
ANDROID_FOREGROUND_SCALE=70

android_foreground_size() {
    # <density> -> px of the 108dp adaptive layer at that scale
    case "$1" in
        mdpi)    echo 108 ;;
        hdpi)    echo 162 ;;
        xhdpi)   echo 216 ;;
        xxhdpi)  echo 324 ;;
        xxxhdpi) echo 432 ;;
    esac
}

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    read -r fg_size <<EOF
$(android_foreground_size "$density")
EOF
    art_size=$((fg_size * ANDROID_FOREGROUND_SCALE / 100))
    log "res/drawable-$density/ic_launcher_foreground.png (${fg_size}px layer, ${art_size}px drawing)"
    magick "$ART" -filter Lanczos -resize "${art_size}x${art_size}" \
        -background none -gravity center -extent "${fg_size}x${fg_size}" \
        -depth 8 -strip -define png:compression-level=9 \
        "$RES/drawable-$density/ic_launcher_foreground.png"
done

# ---------------------------------------------------------------------------
# 5. Desktop (Compose Multiplatform jvm / jpackage).
# ---------------------------------------------------------------------------
log "shared/icons/app-icon.icns | .ico | .png (desktop packaging)"
mkdir -p "$ROOT/shared/icons"
png "$WORK/padded.png" 512 "$ROOT/shared/icons/app-icon.png"
magick "$WORK/padded.png" -define icon:auto-resize=256,128,64,48,32,16 \
    "$ROOT/shared/icons/app-icon.ico"

mkdir -p "$WORK/AppIcon.iconset"
for size in 16 32 128 256 512; do
    magick "$WORK/padded.png" -filter Lanczos -resize "${size}x${size}" \
        -strip "$WORK/AppIcon.iconset/icon_${size}x${size}.png"
    magick "$WORK/padded.png" -filter Lanczos -resize "$((size * 2))x$((size * 2))" \
        -strip "$WORK/AppIcon.iconset/icon_${size}x${size}@2x.png"
done
iconutil -c icns "$WORK/AppIcon.iconset" -o "$ROOT/shared/icons/app-icon.icns"

# Window icon for `./gradlew :shared:run`; loaded from the classpath by main.kt.
log "shared/src/jvmMain/resources/app-icon.png (window icon)"
png "$WORK/padded.png" 512 "$ROOT/shared/src/jvmMain/resources/app-icon.png"

# ---------------------------------------------------------------------------
# 6. Web (Kotlin/Wasm build served by GitHub Pages).
#
# Transparent PNGs, so the tab icon works on both a light and a dark tab bar.
# apple-touch-icon has to be opaque (iOS composites alpha onto black), and the
# maskable icon keeps the drawing inside the 80% safe circle over the gradient.
# ---------------------------------------------------------------------------
WEB="$ROOT/shared/src/wasmJsMain/resources"
log "shared/src/wasmJsMain/resources (favicons, PWA icons)"
png "$ART" 16 "$WEB/favicon-16x16.png"
png "$ART" 32 "$WEB/favicon-32x32.png"
png "$ART" 192 "$WEB/icon-192.png"
png "$ART" 512 "$WEB/icon-512.png"
magick "$ART" -define icon:auto-resize=48,32,16 "$WEB/favicon.ico"
magick "$WORK/fullbleed.png" -filter Lanczos -resize 180x180 \
    -strip -define png:compression-level=9 "$WEB/apple-touch-icon.png"
magick -size 512x512 "gradient:${WARM_YELLOW}-${CORAL}" \
    \( "$ART" -filter Lanczos -resize 410x410 \) \
    -gravity center -compose over -composite -alpha off -depth 8 \
    -strip -define png:compression-level=9 "$WEB/icon-maskable-512.png"

echo "done. Re-run the Android/iOS builds to pick the new icons up."
