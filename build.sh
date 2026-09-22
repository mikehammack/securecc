#!/bin/bash
# Secure CC 1.0.0 - Forge 1.12.2 manual build (no Gradle).
#
# Reuses the compiled toolchain artifacts from the Market Blocks 1.12.2 build:
#   ~/workspace/market-blocks/forge1122/build/{minecraft-1.12.2-client-mcp-patched.jar,
#     forge-1.12.2-14.23.5.2860-mcp.jar, mcp2srg.srg}
# plus SpecialSource + deps from ~/workspace/market-blocks/forge1122/libs/.
#
# Pipeline:
#   1. Compile the mod with javac --release 8 against MCP-named jars + CC:Tweaked.
#   2. Package classes + resources.
#   3. Reobfuscate MCP -> SRG names.
#
# Output: build/libs/securecc-1.0.0-forge1122.jar
set -euo pipefail
cd "$(dirname "$0")"

MB="$HOME/workspace/market-blocks/forge1122"
MOD_VER="1.0.0"
OUT_JAR="build/libs/securecc-${MOD_VER}-forge1122.jar"

if [ ! -s "$MB/build/minecraft-1.12.2-client-mcp-patched.jar" ]; then
    echo "ERROR: Market Blocks 1.12.2 toolchain artifacts not found at $MB/build/"
    echo "Build market-blocks/forge1122 first (or point MB elsewhere)."
    exit 1
fi
for f in libs/cc-tweaked-1.12.2-1.89.2.jar libs/cc-tweaked-classes.jar libs/plethora-1.12.2-1.2.3.jar libs/authlib-1.5.25.jar; do
    if [ ! -s "$f" ]; then echo "ERROR: missing $f"; exit 1; fi
done

JB="$HOME/.jdks/jdk-25.0.4.1+1/bin"
JAVA="$JB/java"; JAVAC="$JB/javac"; JAR="$JB/jar"
echo "Using Java: $("$JAVA" -version 2>&1 | head -1)"

SS_CP="$MB/libs/SpecialSource-1.8.5.jar:$MB/libs/jopt-simple-5.0.4.jar:$MB/libs/asm-6.2.jar:$MB/libs/asm-commons-6.2.jar:$MB/libs/asm-tree-6.2.jar:$MB/libs/guava-21.0.jar:$MB/libs/gson-2.8.0.jar"
MC_JAR="$MB/build/minecraft-1.12.2-client-mcp-patched.jar"
FORGE_JAR="$MB/build/forge-1.12.2-14.23.5.2860-mcp.jar"
CC_JAR="libs/cc-tweaked-classes.jar"
PLETHORA_JAR="libs/plethora-1.12.2-1.2.3.jar"
AUTHLIB_JAR="libs/authlib-1.5.25.jar"
GUAVA_JAR="$MB/libs/guava-21.0.jar"
GSON_JAR="$MB/libs/gson-2.8.0.jar"

echo "==> Compiling stub classes (compile-only, never packaged)"
# Stubs (src/stubs) mirror a few real classes with MCP names so javac can
# see them; e.g. BlockGeneric implements ITileEntityProvider.createNewTileEntity
# as final under its SRG name, which javac cannot see. The stub dir comes
# FIRST on the classpath so it wins over the real jar. Stubs are NOT
# packaged into the mod jar.
STUB_CP="$MC_JAR:$FORGE_JAR:$CC_JAR:$PLETHORA_JAR:$AUTHLIB_JAR:$GUAVA_JAR:$MB/libs/jsr305-3.0.2.jar"
rm -rf build/stub-classes
mkdir -p build/stub-classes build/classes build/libs
# shellcheck disable=SC2046
"$JAVAC" --release 8 -nowarn -implicit:none -cp "$STUB_CP" -d build/stub-classes \
    $(find src/stubs/java -name "*.java")
echo "  stub classes: $(find build/stub-classes -name '*.class' | wc -l)"

echo "==> Compiling mod sources (javac --release 8)"
CP="build/stub-classes:$MC_JAR:$FORGE_JAR:$CC_JAR:$PLETHORA_JAR:$AUTHLIB_JAR:$GUAVA_JAR:$GSON_JAR:$MB/libs/jsr305-3.0.2.jar"
rm -rf build/classes
mkdir -p build/classes
# shellcheck disable=SC2046
# -implicit:none: the CC jar embeds API .java sources; use its .class files only.
"$JAVAC" --release 8 -nowarn -implicit:none -cp "$CP" -d build/classes \
    $(find src/main/java -name "*.java")
echo "  compiled $(find build/classes -name '*.class' | wc -l) classes"

echo "==> Packaging and reobfuscating (MCP -> SRG)"
rm -f build/mod-mcp.jar
"$JAR" cf build/mod-mcp.jar -C build/classes . -C src/main/resources .
FULL_CP="$SS_CP:$MC_JAR:$FORGE_JAR:$CC_JAR:$PLETHORA_JAR:$AUTHLIB_JAR:$GUAVA_JAR"
"$JAVA" -cp "$FULL_CP" net.md_5.specialsource.SpecialSource \
    -l \
    -i build/mod-mcp.jar \
    -m "$MB/build/mcp2srg.srg" \
    -o "$OUT_JAR" \
    --kill-lvt -q

echo "==> Done: $OUT_JAR ($(du -h "$OUT_JAR" | cut -f1))"
"$JAR" tf "$OUT_JAR" | grep -c '\.class$' | xargs echo "  classes in jar:"
