@echo off
title Emulador Radio Tucson ix35
echo Iniciando ADB y Emulador radio-tucson...
set "PATH=D:\JH-PROGRAMAS\android-sdk\emulator;D:\JH-PROGRAMAS\android-sdk\platform-tools;%PATH%"
adb start-server
start "" "D:\JH-PROGRAMAS\android-sdk\emulator\emulator.exe" -avd radio-tucson -gpu swiftshader -no-skin -no-snapshot -no-boot-anim
echo Emulador lanzado en tu escritorio sin fallos de ventana.
exit /b 0
