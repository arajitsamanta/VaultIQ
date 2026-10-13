#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Start Up Batch script
# ----------------------------------------------------------------------------

set -e

if [ -z "$MAVEN_PROJECTBASEDIR" ]; then
  MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
fi

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
  if command -v curl >/dev/null 2>&1; then
    echo "Downloading Maven Wrapper..."
    curl -sLo "$WRAPPER_JAR" https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar || true
  fi
fi

if [ -f "$WRAPPER_JAR" ]; then
  exec java -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
fi

echo "Maven or Maven wrapper jar not found. Please install Maven or run via Docker Compose: docker compose up --build" >&2
exit 1
