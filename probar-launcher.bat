@echo off
title PROBAR JH LAUNCHER EN EMULADOR TUCSON
echo ========================================================
echo   INICIANDO EMULADOR TUCSON (1024x600 px)
echo ========================================================
echo.
set "PATH=D:\JH-PROGRAMAS\android-sdk\emulator;D:\JH-PROGRAMAS\android-sdk\platform-tools;%PATH%"
set "MSYS_NO_PATHCONV=1"

echo Iniciando servidor ADB...
adb start-server

echo Abriendo emulador visible en tu pantalla...
start "" "D:\JH-PROGRAMAS\android-sdk\emulator\emulator.exe" -avd radio-tucson -gpu swiftshader -no-skin -no-snapshot

echo.
echo Esperando a que el emulador responda por ADB...
adb wait-for-device

echo Enlazando pendrive USB para JH Music...
adb root >nul 2>&1
adb wait-for-device
adb shell ln -s /storage/emulated/0/USB /mnt/udisk1 >nul 2>&1
adb shell ln -s /storage/emulated/0/USB /storage/udisk1 >nul 2>&1

echo.
echo Buscando e instalando ultimo APK de JH Launcher...
for /f "delims=" %%F in ('dir /b /o:-d "%~dp0JH-LAUNCHER-v*.apk" 2^>nul') do (
    set "APK_NAME=%%F"
    goto :install_apk
)

:install_apk
if not defined APK_NAME (
    echo [ERROR] No se encontro ningun archivo JH-LAUNCHER-v*.apk en %~dp0
    pause
    exit /b 1
)

echo Instalando %APK_NAME%...
adb install -r "%~dp0%APK_NAME%"

echo.
echo Abriendo JH Launcher en pantalla...
adb shell am start -n com.jh.launcher/.MainActivity

echo.
echo ========================================================
echo   LISTO! JH LAUNCHER Y PENDRIVE LISTOS EN TU PANTALLA
echo ========================================================
pause
