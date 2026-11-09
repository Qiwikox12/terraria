#!/usr/bin/env bash
set -euo pipefail

if ! test -x /usr/lib/jvm/java-21-openjdk-amd64/bin/javac && ! javac -version 2>&1 | grep -q '^javac 21\.'; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-21-jdk-headless
fi
if test -d /usr/lib/jvm/java-21-openjdk-amd64; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    export PATH="$JAVA_HOME/bin:$PATH"
fi
./gradlew --version
