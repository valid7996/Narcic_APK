#!/usr/bin/env bash
# Builds ONE unified gomobile AAR carrying BOTH Go engines the app needs:
#
#   1) Xray core        — package libv2ray (github.com/patterniha/AndroidLibXrayLite)
#   2) Psiphon tunnel   — package psi     (psiphon-tunnel-core/MobileLibrary/psi)
#
# Why a single bind: libv2ray.aar and psiphontunnel-2.0.41.aar each ship their
# own gomobile runtime under the SHARED `go.*` Java namespace plus a native
# libgojni.so with different content. An APK can hold only one libgojni.so and
# one definition of each go.* class, and the two runtimes cannot be merged
# post-hoc (the native libs call Java_go_Seq_* symbols and FindClass("go/...")).
# Rebuilding both packages into ONE bind produces:
#   - a single libgojni.so containing both engines
#   - a single go.* runtime
#   - Java classes under a private namespace (com.narcic.ng.bind.*) so the
#     upstream libv2ray.*/psi.*/ca.psiphon.* classes are fully replaced
#
# Outputs (in $1):
#   narcic-unified.aar   — the single bind described above
#   psiphon-java.aar     — the ca.psiphon.PsiphonTunnel java wrapper, compiled
#                          against the relocated psi classes (java only)
set -e -u -o pipefail

UNIFIED_OUT="${1:?usage: build-unified.sh <output-dir> <android-sdk> <ndk-version>}"
ANDROID_SDK="${2:?android sdk path required}"
NDK_VERSION="${3:?ndk version required}"

export GO111MODULE=on
export GOPATH="${GOPATH:-$HOME/go}"
export GOBIN="$GOPATH/bin"
# allow go commands to update go.mod/go.sum as needed (gomobile bind writes
# generated bind packages into the module and may add missing requires):
export GOFLAGS=-mod=mod
export PATH="$PATH:$GOBIN"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_NDK_HOME="$ANDROID_SDK/ndk/$NDK_VERSION"

WORK="$(mktemp -d)"
XRAYLITE="$WORK/AndroidLibXrayLite"
PSICORE="$WORK/psiphon-tunnel-core"
WRAPPER="$WORK/bind-wrapper"

echo "[1/6] clone sources"
git clone --depth 1 https://github.com/patterniha/AndroidLibXrayLite.git "$XRAYLITE"
git clone --depth 1 https://github.com/Psiphon-Labs/psiphon-tunnel-core.git "$PSICORE"

echo "[2/6] install gomobile"
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

echo "[3/6] wrapper module (joins both go.mod trees)"
mkdir -p "$WRAPPER"
cat > "$WRAPPER/go.mod" <<EOF
module narcic/bind

go 1.26

require (
	github.com/2dust/AndroidLibXrayLite v0.0.0
	github.com/Psiphon-Labs/psiphon-tunnel-core v0.0.0
	golang.org/x/mobile v0.0.0-20260709172247-6129f5bee9d5
)

replace github.com/2dust/AndroidLibXrayLite => $XRAYLITE

replace github.com/Psiphon-Labs/psiphon-tunnel-core => $PSICORE
EOF
# dummy go file so the module resolves:
echo 'package bind' > "$WRAPPER/bind.go"

echo "[4/6] resolve dependencies (go mod tidy)"
cd "$WRAPPER"
# gomobile bind REQUIRES golang.org/x/mobile in the target module's graph
# (it generates imports of gobind runtime packages into the bind sources).
# Pin the SAME x/mobile version AndroidLibXrayLite already uses so both
# share one runtime, then let tidy finalize go.sum. The explicit require +
# tidy order keeps x/mobile in the graph (tidy only drops deps nothing
# imports — gobind's generated bind files import it at bind time, so an
# empty-module tidy would remove it; therefore tidy runs BEFORE the bind and
# the require is written into go.mod above, which tidy preserves because the
# module graph carries it via the replace targets that themselves require
# x/mobile).
go mod tidy
# Ensure x/mobile survived tidy (it must — AndroidLibXrayLite requires it);
# if tidy somehow dropped it, re-add explicitly:
if ! grep -q "golang.org/x/mobile" go.mod; then
  go get golang.org/x/mobile@v0.0.0-20260709172247-6129f5bee9d5
fi
go list -m golang.org/x/mobile >/dev/null 2>&1 || {
  echo "FATAL: golang.org/x/mobile missing from module graph"; exit 1;
}

echo "[5/6] gomobile bind (single libgojni, both engines)"
gomobile bind -v \
  -target=android/arm64,android/arm \
  -androidapi 24 \
  -javapkg=com.narcic.ng.bind \
  -o "$WORK/narcic-unified.aar" \
  "$XRAYLITE" "$PSICORE/MobileLibrary/psi"

echo "[6/6] build ca.psiphon.PsiphonTunnel wrapper against relocated classes"
# The upstream wrapper imports psi.*; relocate those imports to the new
# namespace (com.narcic.ng.bind.psi.*) and compile it against the unified
# classes.jar. The class name stays ca.psiphon.PsiphonTunnel so the app code
# (Class.forName) is unchanged.
git -C "$PSICORE" archive HEAD MobileLibrary/Android/PsiphonTunnel | tar -x -C "$WORK"
WRAPPER_JAVA="$WORK/MobileLibrary/Android/PsiphonTunnel/PsiphonTunnel.java"
sed -i 's/^import psi\./import com.narcic.ng.bind.psi./' "$WRAPPER_JAVA"

unzip -o -q "$WORK/narcic-unified.aar" classes.jar -d "$WORK/unified-classes"
ANDROID_JAR="$(ls "$ANDROID_SDK/platforms"/android-3*/android.jar 2>/dev/null | sort -V | tail -1)"
mkdir -p "$WORK/wrapper-out"
javac -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$WORK/unified-classes/classes.jar" \
  -d "$WORK/wrapper-out" \
  "$WRAPPER_JAVA"

# ---- package outputs ----
mkdir -p "$UNIFIED_OUT"
cp "$WORK/narcic-unified.aar" "$UNIFIED_OUT/narcic-unified.aar"

mkdir -p "$WORK/psijava/classes"
cp -r "$WORK/wrapper-out"/* "$WORK/psijava/classes/"
cp "$WORK/MobileLibrary/Android/PsiphonTunnel/AndroidManifest.xml" "$WORK/psijava/" 2>/dev/null || true
echo "-keep class ca.psiphon.** { *; }" > "$WORK/psijava/proguard.txt"
(
  cd "$WORK/psijava"
  rm -f "$UNIFIED_OUT/psiphon-java.aar"
  zip -qr "$UNIFIED_OUT/psiphon-java.aar" AndroidManifest.xml classes proguard.txt
)

echo "=== DONE ==="
ls -la "$UNIFIED_OUT"
echo "--- unified AAR native libs (expect libgojni per ABI) ---"
unzip -l "$UNIFIED_OUT/narcic-unified.aar" | grep -E "\.so" | head -6
echo "--- unified AAR java top-level packages (expect bind + go only) ---"
unzip -p "$UNIFIED_OUT/narcic-unified.aar" classes.jar > "$WORK/u.jar"
unzip -l "$WORK/u.jar" | awk '{print $4}' | grep "\.class" | cut -d/ -f1 | sort -u
echo "--- relocated psi classes present? ---"
unzip -l "$WORK/u.jar" | grep -c "bind/psi"
echo "--- relocated xray classes present? ---"
unzip -l "$WORK/u.jar" | grep -c "Libv2ray"
