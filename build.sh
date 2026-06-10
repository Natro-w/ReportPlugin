#!/bin/bash
# ==========================================
#  ReportPlugin Build Script for Linux/Mac
# ==========================================
set -e

LUMI_JAR="../../Servers/lumi-server/Lumi-1.6.0.jar"
LP_JAR="../../Servers/lumi-server/plugins/LuckPerms-Nukkit-5.5.55.jar"
SRC="src/main/java"
OUT="target/classes"
RES="src/main/resources"
JAR_OUT="target/ReportPlugin-1.1.0.jar"

# Allow override via env vars
LUMI_JAR="${LUMI_PATH:-$LUMI_JAR}"
LP_JAR="${LUCKPERMS_PATH:-$LP_JAR}"

# Clean
rm -rf target
mkdir -p "$OUT"

# Compile
javac -cp "${LUMI_JAR}:${LP_JAR}" -d "$OUT" "$SRC"/ru/Natro/reportplugin/*.java

# Package
cd "$OUT"
jar cf "../../$JAR_OUT" -C . .
jar uf "../../$JAR_OUT" -C "../../$RES" plugin.yml
jar uf "../../$JAR_OUT" -C "../../$RES" config.yml
cd ../..

echo ""
echo "Build successful: $JAR_OUT"
