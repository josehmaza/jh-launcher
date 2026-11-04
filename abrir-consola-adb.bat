@echo off
title Consola ADB - JH Launcher
cd /d "D:\workspaces\general\jh-launcher"
set "PATH=D:\JH-PROGRAMAS\android-sdk\platform-tools;%PATH%"
cls
echo ====================================================
echo         CONSOLA ADB LISTA - JH LAUNCHER
echo ====================================================
echo.
adb devices
echo.
echo Comandos recomendados:
echo   adb shell
echo   adb logcat -s JH-LAUNCHER:V
echo   adb shell input keyevent 85    (Play / Pausa)
echo   adb shell input keyevent 87    (Siguiente cancion)
echo   adb shell input keyevent 24    (Subir volumen)
echo   adb shell input keyevent 25    (Bajar volumen)
echo ====================================================
echo.
cmd.exe /k

