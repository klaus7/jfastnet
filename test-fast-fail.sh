#!/usr/bin/env bash
# Switch to script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

mvn clean test -DskipAfterFailureCount=false