#!/usr/bin/env bash
# Compiles the jEdit-independent core plus tests and runs the test suite.
# The core is compiled WITHOUT jedit.jar so any jEdit import in core fails here.
set -euo pipefail
cd "$(dirname "$0")/.."
J="${JAVA_HOME:+$JAVA_HOME/bin/}"
rm -rf build/test
"${J}javac" -Xlint:-options --release 17 -d build/test $(find src/vimulator/core test -name '*.java')
"${J}java" -Djava.awt.headless=true -cp build/test vimulator.TestRunner "$@"
