#!/bin/bash
# Launch z-code coding assistant
# Usage: ./run.sh [--work-dir=/path/to/project] [--api-key=xxx] [--model=xxx]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/target/z-code-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building z-code..."
    cd "$SCRIPT_DIR" && mvn clean package -DskipTests -q
fi

java -jar "$JAR" "$@"
