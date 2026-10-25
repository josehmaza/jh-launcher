@echo off
title Emulador Radio Tucson ix35
echo Iniciando Emulador radio-tucson...
set "PATH=D:\JH-PROGRAMAS\android-sdk\emulator;D:\JH-PROGRAMAS\android-sdk\platform-tools;%PATH%"
start "" "D:\JH-PROGRAMAS\android-sdk\emulator\emulator.exe" -avd radio-tucson -gpu host -no-snapshot
echo Emulador lanzado en tu escritorio.
exit /b 0
