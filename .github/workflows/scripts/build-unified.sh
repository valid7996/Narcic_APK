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

# Controlled dependency fork (proved necessary — see go.mod pins comment):
# Psiphon's quic-go fork (79fe45fb83b1) builds against the OLD qpack API
# (NewDecoder(callback) + DecodeFull/Write), while Xray's apernet quic-go
# @184d081 requires qpack v0.6.0 (NewDecoder() + iterator Decode). Go cannot
# carry two versions of github.com/quic-go/qpack in one build, so the fork's
# http3 package is mechanically adapted to qpack v0.6.0 here — a controlled
# patch of THIS dependency only (allowed per review rule #8). The psi engine
# source is untouched.
echo "[1b/6] adapt Psiphon quic-go fork http3 to qpack v0.6.0"
git clone --depth 1 https://github.com/Psiphon-Labs/quic-go.git "$WORK/psiquic"
git -C "$WORK/psiquic" fetch --depth 1 origin 79fe45fb83b1 2>/dev/null || true
git -C "$WORK/psiquic" checkout 79fe45fb83b1 2>/dev/null || true

# 1) append a compat file providing the v0.5-style helpers on top of v0.6:
cat > "$WORK/psiquic/http3/qpack_compat.go" <<'EOF'
package http3

// qpack v0.6 compatibility shims for the Psiphon quic-go fork.
// The v0.5-era API (NewDecoder(callback), Write, DecodeFull, Close) is
// re-implemented on top of the v0.6 iterator API (NewDecoder + Decode).
import (
	"io"

	"github.com/quic-go/qpack"
)

var qpackEmitFunc func(qpack.HeaderField)

func QpackNewDecoderCompat(emitFunc func(qpack.HeaderField)) *qpack.Decoder {
	qpackEmitFunc = emitFunc
	return qpack.NewDecoder()
}

// QpackDecodeFull mirrors qpack v0.5 Decoder.DecodeFull using the v0.6
// iterator API.
func QpackDecodeFull(d *qpack.Decoder, p []byte) ([]qpack.HeaderField, error) {
	if len(p) == 0 {
		return []qpack.HeaderField{}, nil
	}
	var hf []qpack.HeaderField
	df := d.Decode(p)
	for {
		field, err := df()
		if err == io.EOF {
			return hf, nil
		}
		if err != nil {
			return nil, err
		}
		hf = append(hf, field)
		if qpackEmitFunc != nil {
			qpackEmitFunc(field)
		}
	}
}
EOF

# 2) mechanically redirect the v0.5 calls to the compat shims:
# IMPORTANT: longest-match patterns (c.decoder, s.decoder) MUST run BEFORE
# the generic `decoder` pattern — sed processes -e expressions in order, and
# without this ordering the generic pattern matches the `decoder` substring
# inside `c.decoder`/`s.decoder`, producing `c.QpackDecodeFull(decoder, ...)`
# with an undefined `decoder` variable.
sed -i \
  -e 's/qpack\.NewDecoder(func(hf qpack\.HeaderField) {})/QpackNewDecoderCompat(func(hf qpack.HeaderField) {})/g' \
  -e 's/c\.decoder\.DecodeFull(/QpackDecodeFull(c.decoder, /g' \
  -e 's/s\.decoder\.DecodeFull(/QpackDecodeFull(s.decoder, /g' \
  -e 's/decoder\.DecodeFull(/QpackDecodeFull(decoder, /g' \
  "$WORK/psiquic/http3/client.go" \
  "$WORK/psiquic/http3/conn.go" \
  "$WORK/psiquic/http3/http_stream.go" \
  "$WORK/psiquic/http3/server.go"

# 3) the fork's qpack version must now resolve to v0.6.0 (set via replace in
#    the wrapper go.mod below: github.com/quic-go/qpack => v0.6.0)

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
	github.com/Psiphon-Labs/quic-go v0.0.0
	golang.org/x/mobile v0.0.0-20260709172247-6129f5bee9d5
)

// Dependency pins — resolve the MVS conflict between Xray's and Psiphon's
// dependency trees (verified against each repo's official go.mod):
//
// github.com/quic-go/qpack v0.6.0 (pinned; see below)
//   The ONLY genuine conflict: Xray's apernet quic-go@184d081 requires qpack
//   v0.6.0 (NewDecoder() + iterator Decode), while Psiphon's fork
//   (Psiphon-Labs/quic-go@79fe45fb83b1) builds against the OLD qpack API
//   (NewDecoder(callback) + Write/DecodeFull). Go cannot carry two versions
//   of one module. Resolution (controlled fork per review rule 8): the
//   Psiphon fork's http3 package is mechanically adapted to qpack v0.6.0 in
//   step 1b above (compat shims in qpack_compat.go — only that dependency is
//   touched; psi engine source is unchanged), then qpack is pinned to v0.6.0
//   for BOTH engines.
//
// github.com/vishvananda/netlink v1.2.1-beta.2 (Psiphon official pin)
//   tailscale/netlink (2021, required by Psiphon) breaks against
//   vishvananda/netlink v1.3.1 (Quantum type change, ToIPNet signature).
//   Locked to v1.2.1-beta.2 via replace below — the exact Psiphon version.
//
// github.com/tailscale/netlink v1.1.1-0.20211101221916-cabfb018fe85 (Psiphon official pin)
//   Kept at the Psiphon-pinned commit exactly as upstream go.mod declares.

replace github.com/vishvananda/netlink => github.com/vishvananda/netlink v1.2.1-beta.2

// github.com/Psiphon-Labs/quic-go MUST resolve to the ADAPTED clone
// (step 1b above) — without this replace, gomobile bind compiles the
// upstream unpatched fork and the qpack v0.6 adaptation never applies.
replace github.com/Psiphon-Labs/quic-go => $WORK/psiquic

replace github.com/2dust/AndroidLibXrayLite => $XRAYLITE

	// qpack v0.6.0 — both engines are aligned to this version (the Psiphon
	// fork's http3 was adapted in step 1b above):
	replace github.com/quic-go/qpack => github.com/quic-go/qpack v0.6.0

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

// The four provider interfaces below MUST be declared as independent
// interfaces in THIS package (not `type X = psiapi.X` aliases): gobind only
// generates Java bindings for named types whose package is part of the bind
// set, and type aliases resolve to psiapi types outside the bind set — those
// are silently skipped, so Psi.Start/StartSendFeedback and every provider
// interface would vanish from classes.jar. Go interfaces are structural, so
// any Java-proxied value implementing these satisfies the upstream psiapi
// interfaces at the psiapi.Start call sites below.

// PsiphonProviderNoticeHandler mirrors psi.PsiphonProviderNoticeHandler.
type PsiphonProviderNoticeHandler interface {
	Notice(noticeJSON string)
}

// PsiphonProviderFeedbackHandler mirrors psi.PsiphonProviderFeedbackHandler.
type PsiphonProviderFeedbackHandler interface {
	SendFeedbackCompleted(err error)
}

// PsiphonProviderNetwork mirrors psi.PsiphonProviderNetwork.
type PsiphonProviderNetwork interface {
	HasNetworkConnectivity() int
	GetNetworkID() string
	IPv6Synthesize(IPv4Addr string) string
	HasIPv6Route() int
}

// PsiphonProvider mirrors psi.PsiphonProvider (embedded NoticeHandler and
// Network sub-interfaces, exactly as upstream declares).
type PsiphonProvider interface {
	PsiphonProviderNoticeHandler
	PsiphonProviderNetwork
	BindToDevice(fileDescriptor int) (string, error)

	// GetDNSServersAsString must return a comma-delimited list of DNS server
	// addresses. A single string return value is used since gobind does not
	// support string slice types.
	GetDNSServersAsString() string
}

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
# quic-go: apernet quic-go@184d081 kept (matches XrayLite); Psiphon fork http3
# adapted to qpack v0.6.0 in step 1b. qpack pinned to v0.6.0:
#   (both engines aligned to the same qpack API — no MVS drift):
# qpack pinned to v0.6.0:
go get github.com/quic-go/qpack@v0.6.0

# vishvananda/netlink is locked to v1.2.1-beta.2 via the `replace` directive
# in go.mod above (exact Psiphon version — resolves the tailscale/netlink
# v1.2.1 vs v1.3.1 API incompatibility structurally, no go get needed).
# tailscale/netlink stays at the Psiphon-pinned pseudo-version:
go get github.com/tailscale/netlink@v1.1.1-0.20211101221916-cabfb018fe85
go mod tidy
# Final verification:
go list -m github.com/tailscale/netlink github.com/vishvananda/netlink || {
  echo "FATAL: netlink modules missing from module graph"; exit 1;
}

echo "[5/6] gomobile bind (single libgojni, both engines)"
# Bind against the WRAPPER packages that live inside this module (relative
# paths) — the upstream repo dirs are outside the module graph and gobind
# fails on them with "no exported names" (it cannot resolve their packages
# from another module). The wrapper packages delegate to the real engines.
# Class names emitted (gobind convention <Pkg><Type> with -javapkg):
#   com.narcic.ng.bind.Xray*            (package xray)
#   com.narcic.ng.bind.Psi*             (package psib)
# -checklinkname=0: anet (Psiphon dep) uses //go:linkname to net.zoneCache,
#   which the Go 1.23+ linker rejects by default — same flag Psiphon's own
#   MobileLibrary/Android/make.bash passes.
gomobile bind -v \
  -target=android/arm64,android/arm \
  -androidapi 24 \
  -javapkg=com.narcic.ng.bind \
  -ldflags="-checklinkname=0 -s -w" \
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
  -e 's/^import psi\.Psi;/import com.narcic.ng.bind.psib.Psib;/' \
  -e 's/^import psi\.PsiphonProvider;/import com.narcic.ng.bind.psib.PsiphonProvider;/' \
  -e 's/^import psi\.PsiphonProviderFeedbackHandler;/import com.narcic.ng.bind.psib.PsiphonProviderFeedbackHandler;/' \
  -e 's/^import psi\.PsiphonProviderNetwork;/import com.narcic.ng.bind.psib.PsiphonProviderNetwork;/' \
  -e 's/^import psi\.PsiphonProviderNoticeHandler;/import com.narcic.ng.bind.psib.PsiphonProviderNoticeHandler;/' \
  -e 's/\bPsi\./Psib./g' \
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
jar cf "$WORK/psijava/classes.jar" -C "$WORK/wrapper-out" .
cp "$WORK/MobileLibrary/Android/PsiphonTunnel/AndroidManifest.xml" "$WORK/psijava/" 2>/dev/null || true
# The Psiphon manifest references @xml/ca_psiphon_psiphontunnel_backup_rules
# (android:fullBackupContent) — ship the upstream resource, exactly as the
# original psiphontunnel AAR did (res/xml/ + an empty R.txt), or AAPT fails
# with "resource xml/ca_psiphon_psiphontunnel_backup_rules not found".
mkdir -p "$WORK/psijava/res/xml"
cp "$WORK/MobileLibrary/Android/PsiphonTunnel/ca_psiphon_psiphontunnel_backup_rules.xml" "$WORK/psijava/res/xml/" 2>/dev/null || true
touch "$WORK/psijava/R.txt"
echo "-keep class ca.psiphon.** { *; }" > "$WORK/psijava/proguard.txt"
(
  cd "$WORK/psijava"
  rm -f "$UNIFIED_OUT/psiphon-java.aar"
  zip -qr "$UNIFIED_OUT/psiphon-java.aar" AndroidManifest.xml classes.jar classes res R.txt proguard.txt
)

echo "=== DONE ==="
ls -la "$UNIFIED_OUT"
echo "--- unified AAR native libs (expect libgojni per ABI) ---"
unzip -l "$UNIFIED_OUT/narcic-unified.aar" | grep -E "\.so" | head -6
echo "--- unified AAR java top-level packages (expect bind + go only) ---"
unzip -p "$UNIFIED_OUT/narcic-unified.aar" classes.jar > "$WORK/u.jar"
unzip -l "$WORK/u.jar" | awk '{print $4}' | grep "\.class" | cut -d/ -f1 | sort -u
echo "--- relocated psi classes present? ---"
unzip -l "$WORK/u.jar" | grep -c "bind/psi" || true
echo "--- relocated xray classes present? ---"
unzip -l "$WORK/u.jar" | grep -c "bind/xray" || true
