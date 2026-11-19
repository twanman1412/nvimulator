#!/usr/bin/env bash
# Runs the end-to-end smoke test in a throwaway jEdit instance (opens a window briefly).
# Uses its own settings directory, so the user's jEdit settings and running instance are untouched.
set -euo pipefail
cd "$(dirname "$0")/.."
J="${JAVA_HOME:+$JAVA_HOME/bin/}"
JEDIT_JAR="${JEDIT_JAR:-/usr/share/java/jedit/jedit.jar}"
scripts/build.sh >/dev/null
SETTINGS="$PWD/build/jedit-smoke"
rm -rf "$SETTINGS" build/smoke-classes
mkdir -p "$SETTINGS/jars" build/smoke-classes
printf 'tip.show=false\nfirstTime=false\nview.docking.framework=Original\n' > "$SETTINGS/properties"
cp install/Nvimulator.jar "$SETTINGS/jars/"
"${J}javac" -Xlint:-options --release 17 -cp "$JEDIT_JAR:install/Nvimulator.jar" -d build/smoke-classes \
    $(find smoke -name '*.java')
"${J}jar" cf "$SETTINGS/jars/nvimulator-smoke.jar" -C build/smoke-classes .
echo 'nvimulator.smoke.Smoke.start();' > build/smoke-run.bsh
export NVIMULATOR_SMOKE_OUT="$PWD/build/smoke-result.txt"
rm -f "$NVIMULATOR_SMOKE_OUT"
timeout 90 "${J}java" -jar "$JEDIT_JAR" -settings="$SETTINGS" -run="$PWD/build/smoke-run.bsh" \
    > build/smoke-jedit.log 2>&1 || true
if [ ! -f "$NVIMULATOR_SMOKE_OUT" ]; then
    echo "smoke: no result (see build/smoke-jedit.log)"; tail -20 build/smoke-jedit.log; exit 1
fi
cat "$NVIMULATOR_SMOKE_OUT"
grep -q ' 0 failed' "$NVIMULATOR_SMOKE_OUT"
