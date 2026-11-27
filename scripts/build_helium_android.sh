#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HE="$ROOT/helium"
HCR="${HC_ROOT:-$HOME/ai/amni-lang/helium}"
NDK="${ANDROID_NDK:-$(ls -d "$HOME"/Android/Sdk/ndk/* | sort -V | tail -1)}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
STAGE="${XDG_CACHE_HOME:-$HOME/.cache}/amni-drive-helium-ndk"
mkdir -p "$STAGE"
HC_LIB="$HCR/hc/lib" "$HCR/build/hc" build --c "$HE/drive_abi.amni" -o "$STAGE/drive_abi.c"
cp -f "$HCR/hc/hrt.h" "$STAGE/hrt.h"
cp -f "$HE/amni_drive_jni.c" "$STAGE/amni_drive_jni.c"
build_abi() {
    local abi="$1"
    local cc="$2"
    local outdir="$ROOT/app/src/main/jniLibs/$abi"
    mkdir -p "$outdir"
    "$BIN/$cc" -shared -fPIC -O2 -fwrapv -w -Dmain=amni_he_stub_main -Wl,-z,max-page-size=16384 -I"$STAGE" \
        "$STAGE/drive_abi.c" "$STAGE/amni_drive_jni.c" \
        -llog -lm -ldl -o "$outdir/libamni_drive_he.so"
    "$BIN/llvm-strip" "$outdir/libamni_drive_he.so"
    ls -lh "$outdir/libamni_drive_he.so"
}
build_abi "arm64-v8a" "aarch64-linux-android26-clang"
build_abi "x86_64" "x86_64-linux-android26-clang"
echo "Helium Android NDK libraries built OK"
