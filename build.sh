#!/usr/bin/env bash
# Compiles src/ into jars/xpsmods.jar against the Starsector API.
set -euo pipefail

MOD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GAME="$(cd "$MOD_DIR/../.." && pwd)"
JDK="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"

if [[ ! -x "$JDK/bin/javac" || ! -x "$JDK/bin/jar" ]]; then
    echo "error: no javac/jar in $JDK. Install a JDK 17 (sudo pacman -S jdk17-openjdk) or set JAVA_HOME." >&2
    exit 1
fi

CP=""
for j in starfarer.api.jar json.jar log4j-1.2.9.jar xstream-1.4.10.jar lwjgl.jar lwjgl_util.jar; do
    CP="$CP${CP:+:}$GAME/$j"
done
# Library mods (must match "dependencies" in mod_info.json).
for j in "LunaLib-2.0.5/jars/LunaLib.jar"; do
    CP="$CP:$GAME/mods/$j"
done

cd "$MOD_DIR"
rm -rf build
mkdir -p build/classes jars

mapfile -t SOURCES < <(find src -name '*.java')
"$JDK/bin/javac" --release 17 -encoding UTF-8 -cp "$CP" -d build/classes "${SOURCES[@]}"
"$JDK/bin/jar" cf jars/xpsmods.jar -C build/classes .

echo "built jars/xpsmods.jar (${#SOURCES[@]} source files)"
