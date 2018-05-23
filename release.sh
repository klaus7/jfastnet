#!/usr/bin/env bash
# Switch to script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "${DIR}"

set +x # Disable debugging
set -u # Stop on uninitialised variable
set -e # Stop on first non-null return value

echo " * Perform unit tests..."
mvn test

echo " * Prepare the release"
mvn --batch-mode release:prepare -Darguments="-DskipTests"

echo " * Prepare the release"
mvn release:perform -Darguments="-DskipTests"
