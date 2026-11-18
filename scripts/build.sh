#!/usr/bin/env bash
# Builds install/Vimulator.jar without ant.
set -euo pipefail
cd "$(dirname "$0")/.."
J="${JAVA_HOME:+$JAVA_HOME/bin/}"
JEDIT_JAR="${JEDIT_JAR:-/usr/share/java/jedit/jedit.jar}"
rm -rf build/classes && mkdir -p build/classes install
"${J}javac" -Xlint:-options --release 17 -cp "$JEDIT_JAR" -d build/classes $(find src -name '*.java')
"${J}jar" cf install/Vimulator.jar -C build/classes . actions.xml *.props
echo "built install/Vimulator.jar"
