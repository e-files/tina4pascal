#!/bin/sh
# setup-fpc-macos.sh — build the whole FPC 3.2.2 toolchain Tina4Pascal needs on an
# Apple Silicon Mac, from nothing: native compiler + Android (arm64, armv7) and
# iOS (arm64) cross compilers, the Android assembler/linker wrappers, and the
# fpc.cfg blocks that tie them together. Run via `tina4pascal setup fpc`.
#
# Idempotent: a finished step is skipped on re-run (native compiler present,
# a target's RTL present, cfg block present). Every pitfall referenced below is
# written up in docs/TOOLCHAIN.md / docs/ANDROID.md.
#
# Env: FPC_PREFIX (default ~/fpc), FPC_DEV (default ~/fpc-dev),
#      ANDROID_NDK (an NDK r23+ root; the CLI passes the one it resolved),
#      TINA4_CROSS ("android ios" by default; add "win64 linux" for the desktop crosses).
set -eu

VER=3.2.2
SHA256=d542e349de246843d4f164829953d1f5b864126c5b62fd17c9b45b33e23d2f44
URL="https://downloads.sourceforge.net/project/freepascal/Source/$VER/fpc-$VER.source.tar.gz"
# GNU ld for aarch64 that runs on arm64 macOS (ld.lld rejects FPC 3.2.2's linker script)
GNULD_TAG=darwin_arm64_crossbins_all; GNULD_ZIP=Linux_AArch64_unknown_V245.zip

PREFIX="${FPC_PREFIX:-$HOME/fpc}"
DEV="${FPC_DEV:-$HOME/fpc-dev}"
SRC="$DEV/fpc-$VER"
CROSS="$PREFIX/cross"
TARGETS="${TINA4_CROSS:-android ios}"
LOGS="$DEV/logs"

if [ -t 1 ]; then B="$(printf '\033[1m')"; R="$(printf '\033[31m')"; Z="$(printf '\033[0m')"; else B=; R=; Z=; fi
say()  { printf '%s\n' "$*"; }
step() { printf '\n%s→ %s%s\n' "$B" "$*" "$Z"; }
die()  { printf '%s✗ %s%s\n' "$R" "$*" "$Z" >&2; exit 1; }

[ "$(uname -s)" = Darwin ] && [ "$(uname -m)" = arm64 ] \
  || die "this formula is for Apple Silicon macOS (see docs/TOOLCHAIN.md for other hosts)"
command -v brew >/dev/null 2>&1 || die "Homebrew is required (https://brew.sh)"

# ---- preflight: fail in seconds, not 10 minutes into make ---------------------
step "preflight"
_sel="$(xcode-select -p 2>/dev/null || true)"
case " $TARGETS " in *" ios "*)
  case "$_sel" in *CommandLineTools*|'')
    die "iOS needs Xcode selected, not the Command Line Tools → sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" ;;
  esac ;;
esac
case "$_sel" in *Xcode*.app*)
  xcodebuild -license check >/dev/null 2>&1 \
    || die "Xcode licence not accepted (the assembler exits 69 mid-build) → sudo xcodebuild -license accept && xcodebuild -runFirstLaunch" ;;
esac
MACSDK="$(xcrun --show-sdk-path)" || die "no macOS SDK (xcode-select --install)"
case " $TARGETS " in *" android "*)
  [ -n "${ANDROID_NDK:-}" ] && [ -d "$ANDROID_NDK/toolchains/llvm/prebuilt" ] \
    || die "no Android NDK found → tina4pascal setup android" ;;
esac
say "  macOS SDK  $MACSDK"
mkdir -p "$DEV" "$LOGS" "$PREFIX/etc"

# ---- 1. bootstrap compiler (Homebrew fpc is native-only: bootstrap use only) --
BOOT=/opt/homebrew/bin/ppca64
if [ ! -x "$PREFIX/bin/fpc" ]; then
  step "bootstrap compiler"
  [ -x "$BOOT" ] || brew install fpc
  [ -x "$BOOT" ] || die "Homebrew fpc did not provide $BOOT"
fi

# ---- 2. source --------------------------------------------------------------
if [ ! -d "$SRC/compiler" ]; then
  step "FPC $VER source"
  tgz="$DEV/fpc-$VER.source.tar.gz"
  [ -f "$tgz" ] || curl -fsSL --retry 3 -o "$tgz" "$URL"
  got="$(shasum -a 256 "$tgz" | cut -d' ' -f1)"
  [ "$got" = "$SHA256" ] || { rm -f "$tgz"; die "checksum mismatch for $tgz ($got)"; }
  tar -xzf "$tgz" -C "$DEV"
fi

# ---- 3. native compiler (pitfalls 3, 4) ----------------------------------------
PP="$PREFIX/lib/fpc/$VER/ppca64"
if [ ! -x "$PREFIX/bin/fpc" ]; then
  step "native compiler → $PREFIX (several minutes)"
  ( cd "$SRC" && make all PP="$BOOT" OVERRIDEVERSIONCHECK=1 OPT="-XR$MACSDK" > "$LOGS/native-build.log" 2>&1 ) \
    || die "native build failed — tail $LOGS/native-build.log"
  ( cd "$SRC" && make install INSTALL_PREFIX="$PREFIX" PP="$BOOT" OVERRIDEVERSIONCHECK=1 OPT="-XR$MACSDK" > "$LOGS/native-install.log" 2>&1 ) \
    || die "native install failed — tail $LOGS/native-install.log"
fi
ln -sf "../lib/fpc/$VER/ppca64" "$PREFIX/bin/ppca64"
[ -f "$PREFIX/etc/fpc.cfg" ] || "$PREFIX/lib/fpc/$VER/samplecfg" "$PREFIX/lib/fpc/$VER" "$PREFIX/etc" >/dev/null 2>&1
[ -f "$PREFIX/etc/fpc.cfg" ] || die "samplecfg did not write $PREFIX/etc/fpc.cfg"
say "  ✓ $("$PREFIX/bin/fpc" -iV) native"

# ---- 4. Android wrappers + stripped NDK stubs (pitfalls 7-9, 14, 18) ----------
if case " $TARGETS " in *" android "*) true ;; *) false ;; esac; then
  step "Android assembler/linker wrappers"
  TC="$ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64"
  [ -x "$TC/bin/clang" ] || die "no clang in $TC"
  A64="$CROSS/bin/aarch64-android"; ARM="$CROSS/bin/arm-android"; GNU="$CROSS/bin/aarch64-linux-gnu"
  mkdir -p "$A64" "$ARM" "$GNU" "$CROSS/lib/aarch64-android-ndk21" "$CROSS/lib/arm-android-ndk21"

  if [ ! -x "$GNU/aarch64-unknown-linux-gnu-ld" ]; then
    zip="$DEV/$GNULD_ZIP"
    [ -f "$zip" ] || curl -fsSL --retry 3 -o "$zip" \
      "https://github.com/LongDirtyAnimAlf/fpcupdeluxe/releases/download/$GNULD_TAG/$GNULD_ZIP"
    unzip -o -q "$zip" -d "$GNU" && chmod +x "$GNU"/*
  fi
  command -v arm-linux-gnueabihf-as >/dev/null 2>&1 || brew install arm-linux-gnueabihf-binutils
  GNUARM="$(brew --prefix)/bin"

  # arm64: clang's integrated assembler, with .as forced to assembler and GNU
  # --defsym translated; link with GNU ld.
  cat > "$A64/aarch64-linux-android-as" <<EOF
#!/bin/bash
args=()
while [ \$# -gt 0 ]; do
  case "\$1" in
    --defsym) args+=("-Wa,--defsym,\$2"); shift 2 ;;
    --defsym=*) args+=("-Wa,--defsym,\${1#--defsym=}"); shift ;;
    *) args+=("\$1"); shift ;;
  esac
done
exec "$TC/bin/clang" -target aarch64-linux-android21 -c -x assembler "\${args[@]}"
EOF
  printf '#!/bin/sh\nexec "%s" "$@"\n' "$GNU/aarch64-unknown-linux-gnu-ld" > "$A64/aarch64-linux-android-ld"
  # armv7: GNU as (clang rejects FPC's pre-UAL syntax) + GNU ld
  printf '#!/bin/sh\nexec "%s" -march=armv7-a -mfpu=vfpv3-d16 -mfloat-abi=softfp "$@"\n' "$GNUARM/arm-linux-gnueabihf-as" > "$ARM/arm-linux-androideabi-as"
  printf '#!/bin/sh\nexec "%s" "$@"\n' "$GNUARM/arm-linux-gnueabihf-ld" > "$ARM/arm-linux-androideabi-ld"
  for t in ar strip objdump; do
    printf '#!/bin/sh\nexec "%s" "$@"\n' "$TC/bin/llvm-$t" > "$A64/aarch64-linux-android-$t"
    printf '#!/bin/sh\nexec "%s" "$@"\n' "$TC/bin/llvm-$t" > "$ARM/arm-linux-androideabi-$t"
  done
  chmod +x "$A64"/* "$ARM"/*

  # BFD ld can't read the NDK's zstd-compressed debug sections: strip copies
  for pair in aarch64-linux-android:aarch64-android-ndk21 arm-linux-androideabi:arm-android-ndk21; do
    from="$TC/sysroot/usr/lib/${pair%%:*}/21"; to="$CROSS/lib/${pair##*:}"
    for f in "$from"/*; do "$TC/bin/llvm-strip" -g "$f" -o "$to/$(basename "$f")" 2>/dev/null || cp "$f" "$to/"; done
  done

  # smoke test the wrapper on a .as file with --defsym (pitfall 14)
  t="$(mktemp -d)"; printf '.text\n.globl f\nf: ret\n' > "$t/t.as"
  "$A64/aarch64-linux-android-as" --defsym X=1 -o "$t/t.o" "$t/t.as" && [ -s "$t/t.o" ] \
    || die "arm64 assembler wrapper produced no object"
  rm -rf "$t"; say "  ✓ wrappers"
fi

# ---- 5. fpc.cfg: per-CPU android blocks + iOS SDK (ANDROID.md learning 3) -----
step "fpc.cfg"
MARK="# ---- Tina4Pascal cross targets"
if ! grep -q "$MARK" "$PREFIX/etc/fpc.cfg"; then
  IOSSDK="$(xcrun --sdk iphoneos --show-sdk-path 2>/dev/null || true)"
  {
    printf '\n%s (toolchain/setup-fpc-macos.sh) ----\n' "$MARK"
    printf '#ifdef android\n#ifdef cpuaarch64\n-XPaarch64-linux-android-\n-FD%s\n-Fl%s\n#endif\n' "$CROSS/bin/aarch64-android" "$CROSS/lib/aarch64-android-ndk21"
    printf '#ifdef cpuarm\n-XParm-linux-androideabi-\n-FD%s\n-Fl%s\n#endif\n#endif\n' "$CROSS/bin/arm-android" "$CROSS/lib/arm-android-ndk21"
    [ -n "$IOSSDK" ] && printf '#ifdef ios\n-XR%s\n#endif\n' "$IOSSDK"
  } >> "$PREFIX/etc/fpc.cfg"
  say "  ✓ added cross blocks"
else
  say "  ✓ already present"
fi

# ---- 6. cross compilers (pitfalls 10, 16) ------------------------------------
cross() {   # name os cpu bindir binprefix crossopt suffix
  name=$1; os=$2; cpu=$3; bindir=$4; bpre=$5; copt=$6; sfx=$7
  if [ -f "$PREFIX/lib/fpc/$VER/units/$cpu-$os/rtl/system.ppu" ]; then say "  ✓ $name (already built)"; return; fi
  say "  … $name (a few minutes)"
  ( cd "$SRC" && make clean OS_TARGET="$os" CPU_TARGET="$cpu" >/dev/null 2>&1 || true
    make crossall crossinstall OS_TARGET="$os" CPU_TARGET="$cpu" PP="$PP" \
      INSTALL_PREFIX="$PREFIX" OVERRIDEVERSIONCHECK=1 OPT="-XR$MACSDK" \
      ${bindir:+CROSSBINDIR=$bindir} ${bpre:+BINUTILSPREFIX=$bpre} \
      ${copt:+CROSSOPT="$copt"} > "$LOGS/cross-$name.log" 2>&1 ) \
    || die "$name cross build failed — tail $LOGS/cross-$name.log"
  x="$PREFIX/lib/fpc/$VER/ppcross$sfx"
  [ -f "$x" ] && [ ! -e "$PREFIX/bin/ppc$sfx" ] && ln -sf "../lib/fpc/$VER/ppcross$sfx" "$PREFIX/bin/ppc$sfx"
  say "  ✓ $name"
}
step "cross compilers"
export PATH="$PREFIX/bin:$PATH"
for t in $TARGETS; do
  case "$t" in
    android) cross android-a64 android aarch64 "$CROSS/bin/aarch64-android" aarch64-linux-android- "" a64
             cross android-arm android arm     "$CROSS/bin/arm-android"     arm-linux-androideabi- "-CpARMV7A -CfVFPV3" arm ;;
    ios)     cross ios-a64 ios aarch64 "" "" "-XR$(xcrun --sdk iphoneos --show-sdk-path)" a64 ;;
    win64)   cross win64 win64 x86_64 "" "" "" x64 ;;
    linux)   say "  linux crosses need the binutils in toolchain/build-crosses.sh — run that script" ;;
    *) die "unknown cross target '$t' (android | ios | win64 | linux)" ;;
  esac
done

# ---- 7. prove each target links ----------------------------------------------
step "link check"
export PPC_CONFIG_PATH="$PREFIX/etc"
t="$(mktemp -d)"; printf "program h; begin writeln('hi'); end.\n" > "$t/h.pas"
check() {   # label, fpc flags...
  l=$1; shift; d="$t/$l"; mkdir -p "$d"
  "$PREFIX/bin/fpc" "$@" -FE"$d" -FU"$d" "$t/h.pas" >"$d/log" 2>&1 && [ -f "$d/h" ] \
    && say "  ✓ $l  $(file -b "$d/h" | cut -d, -f1-2)" || die "$l hello world did not link — see $d/log"
}
check macos
case " $TARGETS " in *" android "*) check android-arm64 -Tandroid -Paarch64; check android-armv7 -Tandroid -Parm -CpARMV7A -CfVFPV3 ;; esac
case " $TARGETS " in *" ios "*) check ios-arm64 -Tios -Paarch64 ;; esac
rm -rf "$t"

say ""
say "FPC $VER ready in $PREFIX. Add to your shell profile:"
say "  export PATH=\"$PREFIX/bin:\$PATH\" PPC_CONFIG_PATH=\"$PREFIX/etc\""
