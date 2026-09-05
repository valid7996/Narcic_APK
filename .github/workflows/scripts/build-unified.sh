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

// Dependency pins — resolve the MVS conflicts between Xray's and Psiphon's
// dependency trees (verified against each repo's official go.mod):
//
// github.com/quic-go/quic-go v0.52.0 (replaces github.com/apernet/quic-go)
//   XrayLite imports github.com/apernet/quic-go@v0.61.1-0.20260806010916-
//   184d081eef3e which builds against qpack v0.6.0 (new API). Psiphon's fork
//   (Psiphon-Labs/quic-go@79fe45fb83b1, May 2025) builds against the OLD qpack
//   API (NewDecoder with callback = qpack <= v0.5.1). Both engines need
//   github.com/quic-go/qpack in ONE build → impossible with those two
//   versions. Resolution: downgrade the Xray-side quic-go to v0.52.0 (May
//   2025 — same era as the Psiphon fork, qpack v0.5.1 old-callback API which
//   is API-compatible with Psiphon's usage) and REPLACE the apernet import
//   path with the canonical quic-go/quic-go path (the apernet repo is only a
//   mirror; v0.52.0 declares module github.com/quic-go/quic-go). XrayLite
//   only uses the stable quic.DialAddr + quic.Config API — unchanged between
//   v0.52.0 and v0.61.
//
// github.com/quic-go/qpack v0.5.1
//   Shared by both engines at the v0.52.0 alignment (old callback API). No
//   duplicate go.* runtime and no version conflict.
//
// github.com/vishvananda/netlink v1.1.1-0.20211101221916-cabfb018fe85 (Psiphon official pin)
//   tailscale/netlink (2021, required by Psiphon) breaks against
//   vishvananda/netlink v1.3.1 (Quantum type change, ToIPNet signature).
//   netlink is only an INDIRECT dep in XrayLite (via wireguard, unused on
//   Android), so pinning the Psiphon version is safe.
//
// github.com/tailscale/netlink v1.1.1-0.20211101221916-cabfb018fe85 (Psiphon official pin)
//   Kept at the Psiphon-pinned commit exactly as upstream go.mod declares.

replace github.com/apernet/quic-go => github.com/quic-go/quic-go v0.52.0

replace github.com/2dust/AndroidLibXrayLite => $XRAYLITE

replace github.com/Psiphon-Labs/psiphon-tunnel-core => $PSICORE
EOF
# dummy go file so the module resolves:
echo 'package bind' > "$WRAPPER/bind.go"

# --- bindable wrapper packages (REAL upstream API, delegated) ---
# gobind can only bind packages that are part of the module graph AND expose
# bindable exported symbols. The upstream repo roots are outside this module,
# so we wrap them: package "xray" delegates to libv2ray, package "psib"
# delegates to psi. Only functions/types with gobind-supported signatures are
# declared — everything below mirrors the real upstream APIs exactly.

mkdir -p "$WRAPPER/xray" "$WRAPPER/psib"

cat > "$WRAPPER/xray/xray.go" <<'EOF'
package xray

// Bindable facade over AndroidLibXrayLite (package libv2ray).
// Every symbol below delegates 1:1 to the real upstream API
// (github.com/2dust/AndroidLibXrayLite).
import libv2ray "github.com/2dust/AndroidLibXrayLite"

// CoreCallbackHandler mirrors libv2ray.CoreCallbackHandler.
type CoreCallbackHandler interface {
	Startup() int
	Shutdown() int
	OnEmitStatus(int, string) int
}

// ProcessFinder mirrors libv2ray.ProcessFinder.
type ProcessFinder interface {
	FindProcessByConnection(network, srcIP string, srcPort int, destIP string, destPort int) int
}

// CoreController mirrors libv2ray.CoreController (bound by reference).
type CoreController = libv2ray.CoreController

// CheckVersionX mirrors libv2ray.CheckVersionX.
func CheckVersionX() string { return libv2ray.CheckVersionX() }

// InitCoreEnv mirrors libv2ray.InitCoreEnv.
func InitCoreEnv(envPath string, key string) { libv2ray.InitCoreEnv(envPath, key) }

// NewCoreController mirrors libv2ray.NewCoreController.
func NewCoreController(s CoreCallbackHandler) *CoreController {
	return libv2ray.NewCoreController(s)
}

// MeasureOutboundDelay mirrors libv2ray.MeasureOutboundDelay.
func MeasureOutboundDelay(configJSON string, url string) (int64, error) {
	return libv2ray.MeasureOutboundDelay(configJSON, url)
}

// ReconcileBrowserDialer mirrors libv2ray.ReconcileBrowserDialer.
func ReconcileBrowserDialer(dialerAddr string) { libv2ray.ReconcileBrowserDialer(dialerAddr) }

// FetchTlsCertSha256 mirrors libv2ray.FetchTlsCertSha256.
func FetchTlsCertSha256(requestJSON string) string { return libv2ray.FetchTlsCertSha256(requestJSON) }

// FetchQuicCertSha256 mirrors libv2ray.FetchQuicCertSha256.
func FetchQuicCertSha256(requestJSON string) string { return libv2ray.FetchQuicCertSha256(requestJSON) }
EOF

cat > "$WRAPPER/psib/psib.go" <<'EOF'
package psib

// Bindable facade over psiphon-tunnel-core MobileLibrary/psi (package psi).
// Every symbol delegates 1:1 to the real upstream API.
import psiapi "github.com/Psiphon-Labs/psiphon-tunnel-core/MobileLibrary/psi"

// PsiphonProvider mirrors psi.PsiphonProvider.
type PsiphonProvider = psiapi.PsiphonProvider

// PsiphonProviderFeedbackHandler mirrors psi.PsiphonProviderFeedbackHandler.
type PsiphonProviderFeedbackHandler = psiapi.PsiphonProviderFeedbackHandler

// PsiphonProviderNetwork mirrors psi.PsiphonProviderNetwork.
type PsiphonProviderNetwork = psiapi.PsiphonProviderNetwork

// PsiphonProviderNoticeHandler mirrors psi.PsiphonProviderNoticeHandler.
type PsiphonProviderNoticeHandler = psiapi.PsiphonProviderNoticeHandler

// Start mirrors psi.Start.
func Start(
	configJSON string,
	embeddedServerEntryList string,
	embeddedServerEntryListFilename string,
	provider PsiphonProvider,
	useDeviceBinder bool,
	useIPv6Synthesizer bool,
	useHasIPv6RouteGetter bool,
) error {
	return psiapi.Start(
		configJSON,
		embeddedServerEntryList,
		embeddedServerEntryListFilename,
		provider,
		useDeviceBinder,
		useIPv6Synthesizer,
		useHasIPv6RouteGetter,
	)
}

// Stop mirrors psi.Stop.
func Stop() { psiapi.Stop() }

// NoticeUserLog mirrors psi.NoticeUserLog.
func NoticeUserLog(message string) { psiapi.NoticeUserLog(message) }

// HomepageFilePath mirrors psi.HomepageFilePath.
func HomepageFilePath(rootDataDirectoryPath string) string {
	return psiapi.HomepageFilePath(rootDataDirectoryPath)
}

// NoticesFilePath mirrors psi.NoticesFilePath.
func NoticesFilePath(rootDataDirectoryPath string) string {
	return psiapi.NoticesFilePath(rootDataDirectoryPath)
}

// UpgradeDownloadFilePath mirrors psi.UpgradeDownloadFilePath.
func UpgradeDownloadFilePath(rootDataDirectoryPath string) string {
	return psiapi.UpgradeDownloadFilePath(rootDataDirectoryPath)
}

// ReconnectTunnel mirrors psi.ReconnectTunnel.
func ReconnectTunnel() { psiapi.ReconnectTunnel() }

// NetworkChanged mirrors psi.NetworkChanged.
func NetworkChanged() { psiapi.NetworkChanged() }

// AppResumed mirrors psi.AppResumed.
func AppResumed() { psiapi.AppResumed() }

// DropPacketTunnelTraffic mirrors psi.DropPacketTunnelTraffic.
func DropPacketTunnelTraffic(drop bool) { psiapi.DropPacketTunnelTraffic(drop) }

// ExportExchangePayload mirrors psi.ExportExchangePayload.
func ExportExchangePayload() string { return psiapi.ExportExchangePayload() }

// ImportExchangePayload mirrors psi.ImportExchangePayload.
func ImportExchangePayload(payload string) bool { return psiapi.ImportExchangePayload(payload) }

// ImportPushPayload mirrors psi.ImportPushPayload.
func ImportPushPayload(payload []byte) bool { return psiapi.ImportPushPayload(payload) }

// GetDSLAccessToken mirrors psi.GetDSLAccessToken.
func GetDSLAccessToken() string { return psiapi.GetDSLAccessToken() }

// StartSendFeedback mirrors psi.StartSendFeedback.
func StartSendFeedback(
	configJSON string,
	diagnosticsJSON string,
	uploadPath string,
	feedbackHandler PsiphonProviderFeedbackHandler,
	networkInfoProvider PsiphonProviderNetwork,
	noticeHandler PsiphonProviderNoticeHandler,
	useIPv6Synthesizer bool,
	useHasIPv6RouteGetter bool,
) error {
	return psiapi.StartSendFeedback(
		configJSON,
		diagnosticsJSON,
		uploadPath,
		feedbackHandler,
		networkInfoProvider,
		noticeHandler,
		useIPv6Synthesizer,
		useHasIPv6RouteGetter,
	)
}

// StopSendFeedback mirrors psi.StopSendFeedback.
func StopSendFeedback() { psiapi.StopSendFeedback() }

// WriteRuntimeProfiles mirrors psi.WriteRuntimeProfiles.
func WriteRuntimeProfiles(outputDirectory string, cpuSampleDurationSeconds, blockSampleDurationSeconds int) {
	psiapi.WriteRuntimeProfiles(outputDirectory, cpuSampleDurationSeconds, blockSampleDurationSeconds)
}
EOF

echo "[4/6] resolve dependencies (go mod tidy + MVS conflict pins)"
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

# --- MVS alignment pins (see go.mod comments for full rationale) ---
# quic-go: replaced to quic-go/quic-go@v0.52.0 in go.mod above — the era the
# Psiphon fork was cut from (qpack v0.5.1 old-callback API used by BOTH).
# qpack pinned to v0.5.1 to prevent MVS drift to v0.6.0 (breaking API):
go get github.com/quic-go/qpack@v0.5.1
go get github.com/vishvananda/netlink@v1.1.1-0.20211101221916-cabfb018fe85
go get github.com/tailscale/netlink@v1.1.1-0.20211101221916-cabfb018fe85
go mod tidy

echo "[5/6] gomobile bind (single libgojni, both engines)"
# Bind against the WRAPPER packages that live inside this module (relative
# paths) — the upstream repo dirs are outside the module graph and gobind
# fails on them with "no exported names" (it cannot resolve their packages
# from another module). The wrapper packages delegate to the real engines.
# Class names emitted (gobind convention <Pkg><Type> with -javapkg):
#   com.narcic.ng.bind.Xray*            (package xray)
#   com.narcic.ng.bind.Psi*             (package psib)
gomobile bind -v \
  -target=android/arm64,android/arm \
  -androidapi 24 \
  -javapkg=com.narcic.ng.bind \
  -o "$WORK/narcic-unified.aar" \
  ./xray ./psib

echo "[6/6] build ca.psiphon.PsiphonTunnel wrapper against relocated classes"
# The upstream wrapper imports psi.*; relocate those imports to the gobind
# output of the unified bind. gobind names classes <Pkg><Type> under -javapkg:
#   psi.Psi                                -> com.narcic.ng.bind.psib.Psi
#   psi.PsiphonProvider*                   -> com.narcic.ng.bind.Psib*
# (package "psib" + -javapkg=com.narcic.ng.bind). The wrapper class name
# stays ca.psiphon.PsiphonTunnel so the app code (Class.forName) is unchanged.
git -C "$PSICORE" archive HEAD MobileLibrary/Android/PsiphonTunnel | tar -x -C "$WORK"
WRAPPER_JAVA="$WORK/MobileLibrary/Android/PsiphonTunnel/PsiphonTunnel.java"
sed -i \
  -e 's/^import psi\.Psi;/import com.narcic.ng.bind.psib.Psi;/' \
  -e 's/^import psi\.PsiphonProvider;/import com.narcic.ng.bind.PsibPsiphonProvider;/' \
  -e 's/^import psi\.PsiphonProviderFeedbackHandler;/import com.narcic.ng.bind.PsibPsiphonProviderFeedbackHandler;/' \
  -e 's/^import psi\.PsiphonProviderNetwork;/import com.narcic.ng.bind.PsibPsiphonProviderNetwork;/' \
  -e 's/^import psi\.PsiphonProviderNoticeHandler;/import com.narcic.ng.bind.PsibPsiphonProviderNoticeHandler;/' \
# gobind lowers the first letter of Go exported funcs for java methods
# (verified via javap on libv2ray.aar: CheckVersionX -> checkVersionX), so
# every Psi.Xxx( static call maps to bind.psib.Psi.xxx(:
sed -i \
  -e 's/\bPsi\.Start(/com.narcic.ng.bind.psib.Psi.start(/g' \
  -e 's/\bPsi\.Stop(/com.narcic.ng.bind.psib.Psi.stop(/g' \
  -e 's/\bPsi\.AppResumed(/com.narcic.ng.bind.psib.Psi.appResumed(/g' \
  -e 's/\bPsi\.NetworkChanged(/com.narcic.ng.bind.psib.Psi.networkChanged(/g' \
  -e 's/\bPsi\.ReconnectTunnel(/com.narcic.ng.bind.psib.Psi.reconnectTunnel(/g' \
  -e 's/\bPsi\.DropPacketTunnelTraffic(/com.narcic.ng.bind.psib.Psi.dropPacketTunnelTraffic(/g' \
  -e 's/\bPsi\.UpgradeDownloadFilePath(/com.narcic.ng.bind.psib.Psi.upgradeDownloadFilePath(/g' \
  -e 's/\bPsi\.ExportExchangePayload(/com.narcic.ng.bind.psib.Psi.exportExchangePayload(/g' \
  -e 's/\bPsi\.ImportExchangePayload(/com.narcic.ng.bind.psib.Psi.importExchangePayload(/g' \
  -e 's/\bPsi\.ImportPushPayload(/com.narcic.ng.bind.psib.Psi.importPushPayload(/g' \
  -e 's/\bPsi\.GetDSLAccessToken(/com.narcic.ng.bind.psib.Psi.getDSLAccessToken(/g' \
  -e 's/\bPsi\.StartSendFeedback(/com.narcic.ng.bind.psib.Psi.startSendFeedback(/g' \
  -e 's/\bPsi\.StopSendFeedback(/com.narcic.ng.bind.psib.Psi.stopSendFeedback(/g' \
  -e 's/\bPsi\.WriteRuntimeProfiles(/com.narcic.ng.bind.psib.Psi.writeRuntimeProfiles(/g' \
  "$WRAPPER_JAVA"

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
