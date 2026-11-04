# JH LAUNCHER · Hyundai Tucson ix35 2013

Launcher automotriz de alta gama, 100% offline y ultraligero, diseñado específicamente para la pantalla de **1024 × 600 px** del radio Android (Autochips AC8227L) de la **Hyundai Tucson ix35**.

---

## Características de Diseño (Estilo OEM Luxury)

1. **Gemelo Digital 3D (Turntable 360°)**:
   - Vista del vehículo en **Gris Acero** (Steel Grey) o **Negro Obsidiana** sobre plataforma circular con haz de luz halo.
   - Rotación automática continua o manual por arrastre táctil (Frente diagonal, Frontal, Perfil lateral y Trasera).
2. **100% Offline (Cero lag)**:
   - Sin dependencias de Wi-Fi, sin mapas pesados que congelen la CPU Cortex-A7 ni servicios en segundo plano.
   - Reloj digital y fecha del sistema en tiempo real.
   - Detección de memorias pendrive USB (`udisk1`, `udisk2`).
3. **Integración Directa**:
   - Tarjeta principal con controles rápidos para **JH MUSIC**.
   - Tarjeta para reproducción offline de **JH VIDEO**.
   - Sintonizador directo de **RADIO FM** física y manos libres **BLUETOOTH**.
4. **Ergonomía de Manejo sin volante multifunción**:
   - Zonas táctiles amplias (más de 50 px de altura).
   - Botón directo de **Atenuación Nocturna** (Stealth Mode) para evitar fatiga visual y deslumbramiento.
   - Botón de Silencio (Mute) y acceso a Ajustes del sistema.

---

## Arquitectura

La app Android (`android/`) es **100% nativa** (Java + layouts XML, sin WebView): unos 20 MB de RAM frente a los ~180 MB que consumía la versión con WebView (proceso de la app + renderizador de Chrome).

- `MainActivity.java`: pantalla principal, música, radio, modo Stealth y lanzadores de apps.
- `CarTurntable.java`: rotación de las fotos del Tucson (decodificadas una vez en RGB_565).
- `BlindGestureListener.java`: gestos a ciegas con 1 dedo sobre el auto: swipe ←/→ cambia de pista, doble toque es play/pausa y arrastre ↑/↓ sube/baja el volumen (igual que el gesto ciego de JH Music: 240dp = 0-100%).
- `AppsActivity.java`: cajón de apps propio (botón APPS). No usa `ACTION_ALL_APPS`, que solo atiende un launcher con cajón y dejaba el botón sin efecto.
- `HwMonitor.java`: telemetría real de CPU, RAM y almacenamiento.
- `res/layout/activity_main.xml`: réplica nativa de la maqueta.

Las fotos del auto se toman de `assets/` en la raíz al compilar (tarea `syncCarImages`). Compilar con `build.sh`.

## Compilar y Probar en Emulador

- **Compilar APK**:
  ```bash
  bash build.sh
  ```
- **Probar en el emulador (1024 × 600 px) y medir con ADB:** seguir la guía única [10-emulador-y-adb.md](../jh-music/Documentacion/10-emulador-y-adb.md) de jh-music (mismos pasos; solo cambia el APK a `JH-LAUNCHER-v1.0.0.apk` y el paquete a `com.jh.launcher`).

## Probar en el Tucson

`bash build.sh` genera el APK release (firmado con la clave de debug de la PC) en esta carpeta. JH Music se genera igual en `jh-music/`. Si el radio ya tiene la app instalada con otra clave, hay que desinstalarla antes.

### Instalar
1. Copiar `JH-MUSIC-v2.9.8.apk` y `JH-LAUNCHER-v1.0.0.apk` a la raíz de un pendrive y conectarlo al radio (o `adb install -r -d archivo.apk`).
2. Instalar primero **JH Music** y luego el **launcher**.
3. Abrir JH Music una vez, dejar que escanee y aceptar el permiso de almacenamiento si lo pide.

### Sin quedar atrapado
El launcher también tiene ícono normal: **abrirlo desde la lista de apps** antes de dejarlo como inicio. Al pulsar HOME, si Android pregunta, elegir **"Solo una vez"**, no "Siempre". Para volver a lo de fábrica: botón **AJUSTES** (abajo a la derecha, abre los ajustes del sistema), **APPS**, o desinstalar el launcher. Sin comprobar en el radio: si su botón HOME de fábrica abre su propia interfaz sin preguntar, probar solo desde la lista de apps.

### Qué revisar

| # | Prueba | Qué debe pasar |
|---|---|---|
| 1 | Pantalla de inicio | Saludo, hora y fecha correctas; USB 1 y USB 2 en verde si hay pendrives |
| 2 | Auto | Cambia de ángulo cada ~3,6 s deslizándose; los puntos indican el ángulo; Negro / Gris Acero cambia el color |
| 3 | Música con JH Music abierta | Reproducir una canción y volver al launcher: muestra título, artista y duración |
| 4 | Botones ⏮ ▶ ⏭ | Controlan JH Music |
| 5 | Con JH Music **cerrada** | ▶ en el launcher abre la app en segundo plano y suena tras unos segundos (anotar cuánto tarda) |
| 6 | Gestos sobre el auto (**1 dedo**) | Deslizar derecha/izquierda = siguiente/anterior; doble toque = play/pausa; arrastrar vertical = volumen con indicador grande |
| 7 | Audio y Stealth | Mute y modo Stealth |
| 8 | Radio FM | ◄ ► cambian la emisora de fábrica y el número del launcher coincide con lo que suena (sale de una lista fija de 8; ver [MEJORAS.md](MEJORAS.md) §6.2) |
| 9 | Indicador CPU | Anotar qué marca (en el emulador siempre sale "--"; ver [MEJORAS.md](MEJORAS.md) §6.3) |
| 10 | Otro pendrive | Con uno que no tenga la última canción, abrir JH Music: abre Librería y el launcher dice "Selecciona una canción" |
| 11 | Botón APPS | Abre el cajón de apps propio del launcher (cuadrícula con todas las apps instaladas, ordenadas por nombre); tocar una la abre, Atrás vuelve al cajón y la flecha ← al launcher |

Si el launcher no muestra la canción después de reabrir JH Music, darle el permiso de **Almacenamiento** a JH Launcher en Ajustes → Aplicaciones: el launcher no lo pide por sí solo (en el emulador lo tenía concedido).

### Medir (con ADB)
```bash
adb shell dumpsys gfxinfo com.jh.launcher reset
# esperar 10 s con el launcher a la vista
adb shell dumpsys gfxinfo com.jh.launcher
adb shell dumpsys meminfo com.jh.launcher
```
Anotar los resultados en la tabla "Resultados en el radio" de [ANALISIS-RENDIMIENTO.md](ANALISIS-RENDIMIENTO.md). Referencia del emulador: ~19 cuadros/s en reposo y 6 ms en el percentil 90.

## Maqueta Web (referencia de diseño)

`index.html`, `style.css` y `app.js` son la maqueta de diseño original. Ya no van dentro del APK; abre `index.html` en cualquier navegador para verla a 1024 × 600 px.

## Apps abiertas (botón ABIERTAS del dock, v1.4.x)

JH Launcher oculta la barra del launcher de fábrica, que traía el botón de «recientes». El botón **ABIERTAS** abre un panel propio (`OpenAppsActivity`) con las apps que están corriendo, un botón CERRAR por app y CERRAR TODAS; tocar una fila abre esa app. Cierra con `killBackgroundProcesses` (permiso normal) y nunca toca apps de fábrica (`com.jancar.*`, `com.autochips.*`, `com.mediatek.*`, Android) ni el launcher de fábrica.

Cómo sabe qué corre: en el radio (Android 6) lee `ps` y `ActivityManager`; en Android 7 en adelante esos datos se ocultan, y entonces usa la lista de apps que se abrieron desde este launcher desde que se encendió el equipo (`OpenedApps`, se vacía al reiniciar). El pie del panel dice cuál de los dos modos está usando. Un servicio con audio en primer plano (por ejemplo JH Music sonando) puede no cerrarse.
