#!/bin/bash
# Launch zclaw coding assistant
# Usage: ./run.sh [--work-dir=/path/to/project] [--api-key=xxx] [--model=xxx]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/target/zclaw-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building zclaw..."
    cd "$SCRIPT_DIR" && mvn clean package -DskipTests -q
fi

java -jar "$JAR" "$@"
