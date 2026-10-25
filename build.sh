#!/usr/bin/env bash
# Compila el APK de JH LAUNCHER.
# Gradle (tarea copyApkToRoot) copia solo el APK a jh-launcher/ como JH-LAUNCHER-v<versión>.apk
set -e
export JAVA_HOME="/c/Program Files/Java/jdk-11.0.30"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Usando Java: $(java -version 2>&1 | head -n 1)"
cd android
./gradlew clean assembleDebug
cd ..
echo "BUILD EXITOSO! APK generado en: $(pwd)/$(ls JH-LAUNCHER-v*.apk)"
