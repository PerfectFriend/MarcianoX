#!/usr/bin/env bash
# ==============================================================================
# MarcianoX — Ubuntu Host Build, Rust NDK, Unit/Robolectric Test & Log Collector
# Usage (from ~/Projects/MX):
#   chmod +x mx_verify_and_log.sh && ./mx_verify_and_log.sh
# ==============================================================================
set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

LOG_DIR="$PROJECT_DIR/build_logs"
XML_DIR="$LOG_DIR/test_results_xml"
rm -rf "$LOG_DIR"
mkdir -p "$XML_DIR"

SUMMARY_FILE="$LOG_DIR/SUMMARY.md"
ALL_LOGS_FILE="$LOG_DIR/ALL_LOGS.txt"
TIMESTAMP_UTC="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"

# --- Toolchain Auto-Detection -------------------------------------------------
if [[ -f "$HOME/.cargo/env" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.cargo/env"
fi
export PATH="$HOME/.cargo/bin:/usr/local/cargo/bin:$PATH"

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  for candidate in "${ANDROID_HOME:-}" "$HOME/Android/Sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
    if [[ -n "$candidate" && -d "$candidate" ]]; then
      export ANDROID_SDK_ROOT="$candidate"
      break
    fi
  done
fi
if [[ -n "${ANDROID_SDK_ROOT:-}" && -z "${ANDROID_HOME:-}" ]]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  if [[ -n "${ANDROID_NDK_ROOT:-}" && -d "$ANDROID_NDK_ROOT" ]]; then
    export ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT/ndk" ]]; then
    latest_ndk="$(ls -1 "$ANDROID_SDK_ROOT/ndk" 2>/dev/null | sort -V | tail -n 1 || true)"
    if [[ -n "$latest_ndk" && -d "$ANDROID_SDK_ROOT/ndk/$latest_ndk" ]]; then
      export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/$latest_ndk"
    fi
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT/ndk-bundle" ]]; then
    export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk-bundle"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  for jcandidate in \
    "/opt/android-studio/jbr" \
    "$HOME/android-studio/jbr" \
    /usr/lib/jvm/java-21-openjdk-* \
    /usr/lib/jvm/java-17-openjdk-*; do
    if [[ -d "$jcandidate" && -x "$jcandidate/bin/java" ]]; then
      export JAVA_HOME="$jcandidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi

GRADLE_CMD=""
if [[ -x "$PROJECT_DIR/gradlew" ]]; then
  GRADLE_CMD="$PROJECT_DIR/gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="$(command -v gradle)"
else
  for gcandidate in \
    /snap/bin/gradle \
    /opt/gradle/*/bin/gradle \
    "$HOME/.sdkman/candidates/gradle/current/bin/gradle" \
    "$HOME"/.gradle/wrapper/dists/gradle-*/*/gradle-*/bin/gradle; do
    if [[ -x "$gcandidate" ]]; then
      GRADLE_CMD="$gcandidate"
      break
    fi
  done
fi

# Ensure debug.keystore exists if archive shipped debug.keystore.base64
if [[ ! -f "$PROJECT_DIR/debug.keystore" && -f "$PROJECT_DIR/debug.keystore.base64" ]]; then
  base64 -d "$PROJECT_DIR/debug.keystore.base64" > "$PROJECT_DIR/debug.keystore" 2>/dev/null || true
fi

# --- Helper Functions ---------------------------------------------------------
STATUS_ENV=0
STATUS_RUST_HOST=0
STATUS_RUST_NDK=0
STATUS_GRADLE_TEST=0
STATUS_GRADLE_APK=0
STATUS_GRADLE_LINT=0

run_stage() {
  local name="$1"
  local logfile="$2"
  local cmd="$3"
  echo ""
  echo "================================================================================"
  echo "[$name] Starting..."
  echo "================================================================================"
  {
    echo "### STAGE: $name"
    echo "### STARTED: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo "### CMD: $cmd"
    echo ""
  } > "$logfile"
  bash -c "$cmd" >> "$logfile" 2>&1
  local rc=$?
  {
    echo ""
    echo "### FINISHED: $(date -u '+%Y-%m-%d %H:%M:%S UTC') | EXIT_CODE=$rc"
  } >> "$logfile"
  tail -n 30 "$logfile"
  echo "[$name] Exit code: $rc (log: $logfile)"
  return $rc
}

# --- Stage 1: Environment -----------------------------------------------------
run_stage "01_ENV" "$LOG_DIR/01_env.log" '
  echo "PROJECT_DIR=$PWD"
  echo "UNAME=$(uname -a)"
  if [[ -f /etc/os-release ]]; then cat /etc/os-release; fi
  echo "CPU_CORES=$(nproc 2>/dev/null || echo unknown)"
  echo "MEM=$(free -h 2>/dev/null | head -n 2 || true)"
  echo "JAVA_HOME=${JAVA_HOME:-UNSET}"
  java -version 2>&1 || echo "java: NOT FOUND"
  echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-UNSET}"
  echo "ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-UNSET}"
  echo "GRADLE_CMD='"${GRADLE_CMD:-NOT_FOUND}"'"
  if [[ -n "'"${GRADLE_CMD:-}"'" ]]; then "'"$GRADLE_CMD"'" --version 2>&1 || true; fi
  echo "RUSTC=$(rustc --version 2>&1 || echo NOT_FOUND)"
  echo "CARGO=$(cargo --version 2>&1 || echo NOT_FOUND)"
  echo "CARGO_NDK=$(cargo ndk --version 2>&1 || echo NOT_FOUND)"
  echo "RUSTUP_TARGETS:"
  rustup target list --installed 2>&1 || true
  echo "GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo N/A)"
  echo "GIT_STATUS:"
  git status -s 2>/dev/null || true
'
STATUS_ENV=$?

# --- Stage 2: Rust Host Tests + Host Release Build ----------------------------
if command -v cargo >/dev/null 2>&1; then
  run_stage "02_RUST_HOST" "$LOG_DIR/02_rust_test_and_host_build.log" '
    set -e
    cargo test --manifest-path rust/marcianox-core/Cargo.toml -- --nocapture
    cargo build --release --manifest-path rust/marcianox-core/Cargo.toml
    ls -lh rust/marcianox-core/target/release/libmarcianox_core.so
    sha256sum rust/marcianox-core/target/release/libmarcianox_core.so
  '
  STATUS_RUST_HOST=$?
else
  echo "ERROR: cargo not found in PATH or ~/.cargo/bin" > "$LOG_DIR/02_rust_test_and_host_build.log"
  STATUS_RUST_HOST=127
fi

# --- Stage 3: Rust Android NDK Cross-Compile ----------------------------------
if command -v cargo >/dev/null 2>&1 && [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  run_stage "03_RUST_NDK" "$LOG_DIR/03_rust_android_ndk_build.log" '
    set -e
    if ! cargo ndk --version >/dev/null 2>&1; then
      echo "cargo-ndk not found; installing via cargo install cargo-ndk..."
      cargo install cargo-ndk
    fi
    rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
    env -C rust/marcianox-core cargo ndk \
      -t arm64-v8a -t armeabi-v7a -t x86_64 \
      -P 24 \
      -o ../../app/src/main/jniLibs \
      build --release
    ls -lh app/src/main/jniLibs/*/libmarcianox_core.so
    sha256sum app/src/main/jniLibs/*/libmarcianox_core.so
    file app/src/main/jniLibs/*/libmarcianox_core.so || true
  '
  STATUS_RUST_NDK=$?
else
  {
    echo "ERROR: Skipping Android NDK Rust build."
    echo "cargo=$(command -v cargo || echo MISSING)"
    echo "ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-MISSING}"
  } > "$LOG_DIR/03_rust_android_ndk_build.log"
  STATUS_RUST_NDK=127
fi

# --- Stage 4: Gradle Unit & Robolectric Tests ---------------------------------
if [[ -n "$GRADLE_CMD" ]]; then
  run_stage "04_GRADLE_TESTS" "$LOG_DIR/04_gradle_testDebugUnitTest.log" "
    \"$GRADLE_CMD\" :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain --stacktrace
  "
  STATUS_GRADLE_TEST=$?
  if [[ -d "$PROJECT_DIR/app/build/test-results/testDebugUnitTest" ]]; then
    cp -f "$PROJECT_DIR"/app/build/test-results/testDebugUnitTest/*.xml "$XML_DIR/" 2>/dev/null || true
  fi
else
  echo "ERROR: Gradle binary not found." > "$LOG_DIR/04_gradle_testDebugUnitTest.log"
  STATUS_GRADLE_TEST=127
fi

# --- Stage 5: Gradle Assemble Debug APK & Inspect Native Packaging ------------
if [[ -n "$GRADLE_CMD" ]]; then
  run_stage "05_GRADLE_APK" "$LOG_DIR/05_gradle_assembleDebug.log" "
    set -e
    \"$GRADLE_CMD\" :app:assembleDebug --no-daemon --console=plain --stacktrace
    ls -lh app/build/outputs/apk/debug/app-debug.apk
    sha256sum app/build/outputs/apk/debug/app-debug.apk
    echo '--- Native .so entries inside APK ---'
    unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E '\.so$' || true
  "
  STATUS_GRADLE_APK=$?
else
  echo "ERROR: Gradle binary not found." > "$LOG_DIR/05_gradle_assembleDebug.log"
  STATUS_GRADLE_APK=127
fi

# --- Stage 6: Android Lint ----------------------------------------------------
if [[ -n "$GRADLE_CMD" ]]; then
  run_stage "06_GRADLE_LINT" "$LOG_DIR/06_gradle_lintDebug.log" "
    \"$GRADLE_CMD\" :app:lintDebug --no-daemon --console=plain
  "
  STATUS_GRADLE_LINT=$?
  if [[ -f "$PROJECT_DIR/app/build/reports/lint-results-debug.txt" ]]; then
    cp -f "$PROJECT_DIR/app/build/reports/lint-results-debug.txt" "$LOG_DIR/" 2>/dev/null || true
  fi
else
  echo "ERROR: Gradle binary not found." > "$LOG_DIR/06_gradle_lintDebug.log"
  STATUS_GRADLE_LINT=127
fi

# --- Generate Summary & Combined Log ------------------------------------------
badge() {
  if [[ "$1" -eq 0 ]]; then echo "PASS (0)"; else echo "FAIL ($1)"; fi
}

{
  echo "# MarcianoX Ubuntu Verification Summary"
  echo ""
  echo "- **Timestamp:** $TIMESTAMP_UTC"
  echo "- **Host:** $(hostname) ($(uname -m))"
  echo "- **ANDROID_SDK_ROOT:** \`${ANDROID_SDK_ROOT:-UNSET}\`"
  echo "- **ANDROID_NDK_HOME:** \`${ANDROID_NDK_HOME:-UNSET}\`"
  echo "- **GRADLE:** \`${GRADLE_CMD:-NOT_FOUND}\`"
  echo ""
  echo "## Stage Results"
  echo "| Stage | Result | Log File |"
  echo "|---|---|---|"
  echo "| 01 Environment | $(badge $STATUS_ENV) | \`build_logs/01_env.log\` |"
  echo "| 02 Rust Host Tests & Release .so | $(badge $STATUS_RUST_HOST) | \`build_logs/02_rust_test_and_host_build.log\` |"
  echo "| 03 Rust Android NDK (.so x3 ABIs) | $(badge $STATUS_RUST_NDK) | \`build_logs/03_rust_android_ndk_build.log\` |"
  echo "| 04 Gradle :app:testDebugUnitTest | $(badge $STATUS_GRADLE_TEST) | \`build_logs/04_gradle_testDebugUnitTest.log\` |"
  echo "| 05 Gradle :app:assembleDebug | $(badge $STATUS_GRADLE_APK) | \`build_logs/05_gradle_assembleDebug.log\` |"
  echo "| 06 Gradle :app:lintDebug | $(badge $STATUS_GRADLE_LINT) | \`build_logs/06_gradle_lintDebug.log\` |"
  echo ""
  echo "## Test XML Breakdown"
  if ls "$XML_DIR"/*.xml >/dev/null 2>&1; then
    python3 - <<'PYEOF' "$XML_DIR"
import glob, os, sys, xml.etree.ElementTree as ET
xml_dir = sys.argv[1]
total_t = total_f = total_e = total_s = 0
print("| Test Suite | Tests | Failures | Errors | Skipped |")
print("|---|---|---|---|---|")
for path in sorted(glob.glob(os.path.join(xml_dir, "*.xml"))):
    root = ET.parse(path).getroot()
    name = root.attrib.get("name", os.path.basename(path))
    t = int(root.attrib.get("tests", 0))
    f = int(root.attrib.get("failures", 0))
    e = int(root.attrib.get("errors", 0))
    s = int(root.attrib.get("skipped", 0))
    total_t += t; total_f += f; total_e += e; total_s += s
    print(f"| `{name}` | {t} | {f} | {e} | {s} |")
print(f"| **TOTAL** | **{total_t}** | **{total_f}** | **{total_e}** | **{total_s}** |")

failures = []
for path in sorted(glob.glob(os.path.join(xml_dir, "*.xml"))):
    root = ET.parse(path).getroot()
    for tc in root.findall("testcase"):
        for tag in ("failure", "error"):
            node = tc.find(tag)
            if node is not None:
                failures.append((tc.attrib.get("classname"), tc.attrib.get("name"), node.attrib.get("message", ""), (node.text or "")[:1200]))
if failures:
    print("\n### Failed Test Details")
    for cls, name, msg, trace in failures:
        print(f"- **{cls}.{name}**: `{msg}`")
        print("```")
        print(trace.strip())
        print("```")
PYEOF
  else
    echo "_No JUnit XML reports generated._"
  fi
} > "$SUMMARY_FILE"

{
  cat "$SUMMARY_FILE"
  echo ""
  for f in "$LOG_DIR"/0*.log; do
    echo "================================================================================"
    echo "FILE: $(basename "$f")"
    echo "================================================================================"
    cat "$f"
    echo ""
  done
} > "$ALL_LOGS_FILE"

echo ""
echo "================================================================================"
cat "$SUMMARY_FILE"
echo "================================================================================"
echo "All logs saved to: $LOG_DIR/"
echo "Push to GitHub with:"
echo "  git add -A && git commit -m \"chore: ubuntu build and test logs\" && git push"

if [[ $STATUS_RUST_HOST -ne 0 || $STATUS_RUST_NDK -ne 0 || $STATUS_GRADLE_TEST -ne 0 || $STATUS_GRADLE_APK -ne 0 ]]; then
  exit 1
fi
exit 0
