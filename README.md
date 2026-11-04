# JH LAUNCHER · Hyundai Tucson ix35 2013

Launcher nativo, 100 % offline, para la pantalla de **1024 × 600** del radio Android (Autochips AC8227L, Android 8.1 aunque Ajustes diga 10) de la **Hyundai Tucson ix35 2013**. Reemplaza la pantalla de inicio de fábrica y reúne música, video, radio y Bluetooth en una sola pantalla, con botones grandes para usar manejando (el auto no tiene mandos en el volante).

Versión actual: **1.6.5** · [Releases](https://github.com/josehmaza/jh-launcher/releases)

Va con [JH MUSIC](https://github.com/josehmaza/jh-music) (2.10.0 o superior), [JH VIDEO](https://github.com/josehmaza/jh-video) y [JH RADIO](https://github.com/josehmaza/jh-radio).

---

## Qué hay en la pantalla

### Barra de arriba
- «Hola, Hernan», hora y fecha del sistema.
- **USB 1 / USB 2:** en verde si hay un pendrive puesto (`udisk1`, `udisk2`).

### Tarjeta del auto
- Fotos del **Tucson 2013** en **Negro Obsidiana** o **Gris Acero**, con un selector de un toque. Cada color tiene 3 ángulos (frente, perfil y trasera).
- **Cambia de ángulo solo cada 3,6 s**, deslizando el auto de lado con un fundido. Tres puntos indican el ángulo actual. **No se gira con el dedo**: se descartó para no chocar con los gestos a ciegas.
- Halo cian fijo bajo el auto (`CarHaloView`).
- **Gestos a ciegas con 1 dedo sobre el auto** (sin mirar la pantalla):
  - deslizar a la derecha / izquierda: siguiente / anterior (de JH MUSIC o del teléfono, según la pestaña);
  - doble toque: play / pausa;
  - arrastrar arriba / abajo: volumen, con un indicador grande (240 dp = 0 a 100 %, igual que en JH MUSIC).
- **CPU, RAM y ALM:** uso del procesador, la memoria y el almacenamiento (`HwMonitor`).

### Tarjeta de música
Dos pestañas:
- **JH MUSIC:** canción, artista, un disco de vinilo (todavía no muestra la carátula de la canción, ver [MEJORAS.md](MEJORAS.md) §6.1), tiempo y ⏮ ▶ ⏭. La **barra de tiempo se toca o se arrastra** para ir a cualquier minuto (orden `SEEK` a JH MUSIC). El botón grande abre JH MUSIC.
- **BLUETOOTH AUDIO:** controla la música del teléfono (YouTube, Spotify, etc.) por el módulo Bluetooth del radio (`com.jancar.btservice`). Muestra la canción si el teléfono la manda. **Cambia sola a esta pestaña cuando el teléfono empieza a sonar.** El botón **MONITOREO** abre la pantalla de pruebas de Bluetooth de JH RADIO.

### Tarjeta de video
Abre **JH VIDEO** (videos del pendrive).

### Tarjeta de radio
- **Carrusel con las emisoras guardadas en JH RADIO**, con desplazamiento horizontal. Tocar una la sintoniza (orden `GOTO` a JH RADIO, que trabaja en segundo plano). La que suena queda marcada.
- Si no hay emisoras guardadas, sale **AGREGAR**, que abre JH RADIO.
- El botón grande abre JH RADIO.

### Barra de abajo (dock)
| Botón | Qué hace |
|---|---|
| **AUDIO / MUTED** | Silencio: baja el volumen a 0 y lo devuelve al de antes. No usa el silencio de Android, porque en este radio dejaba mudo el audio Bluetooth para siempre. |
| **STEALTH** | Apaga la pantalla casi entera y deja la hora y la canción en tono tenue. Se sale tocando cualquier parte o con Atrás. |
| **MÚSICA · VIDEOS · RADIO** | Abren JH MUSIC, JH VIDEO y JH RADIO. |
| **LLAMADAS** | Abre la app de llamadas Bluetooth de fábrica (la busca por nombre: «llamada», «manos libres», «teléfono»). |
| **APPS** | Cajón de apps propio. Arriba a la derecha, **LAUNCHER DE FÁBRICA** abre el launcher original (Launcher3). |
| **AJUSTES** | Ajustes del sistema. |

---

## Arquitectura

App **100 % nativa** (Java y layouts XML, sin WebView): unos 20 MB de RAM, frente a los ~180 MB de la primera versión con WebView.

| Archivo | Qué hace |
|---|---|
| `MainActivity.java` | Pantalla principal: música, Bluetooth, radio, MUTE, Stealth y lanzadores. |
| `CarTurntable.java` | Cambio de ángulo de las fotos del auto (decodificadas una vez, con los bordes fundidos). |
| `CarHaloView.java` | Halo bajo el auto. |
| `BlindGestureListener.java` | Gestos a ciegas sobre el auto. |
| `BtMusicLink.java` | Enlace con el servicio Bluetooth del radio (`com.jancar.btservice`, AIDL en `aidl/`). |
| `AppsActivity.java` | Cajón APPS y botón LAUNCHER DE FÁBRICA. No usa `ACTION_ALL_APPS`, que en este radio no hacía nada. |
| `HwMonitor.java` | CPU, RAM, almacenamiento y pendrives. |
| `VinylArt.java` | Disco de vinilo de la tarjeta de música (se dibuja una sola vez). |
| `JhTextView.java` | Textos con las fuentes propias (`assets/fonts/`). |

Las fotos del auto se toman de `assets/` en la raíz al compilar (tarea `syncCarImages`).

### Cómo habla con las otras apps

- **JH MUSIC:** lee `.jh_music_state.json` y el aviso `com.jh.music.action.TRACK_CHANGED`. Manda `PLAY_PAUSE`, `NEXT`, `PREV`, `SEEK` (`extra_position_sec`) y `REQUEST_STATE`.
- **JH RADIO:** recibe `com.jh.radio.action.STATE` (`extra_khz`, `extra_name`, `extra_list`). Manda `REQUEST_STATE`, `NEXT`, `PREV` y `GOTO` (`extra_khz`).
- **Bluetooth:** se enlaza al servicio `com.jancar.btservice` del radio para los botones y la canción del teléfono.

---

## Compilar

```bash
bash build.sh
```

Genera `JH-LAUNCHER-v<versión>.apk` (release firmado con la clave de debug de la PC) en esta carpeta. Para el emulador `radio-tucson` (1024 × 600) seguir la guía [10-emulador-y-adb.md](../jh-music/Documentacion/10-emulador-y-adb.md) de jh-music, con el paquete `com.jh.launcher`.

## Instalar en el Tucson

1. Copiar las APK a la raíz de un pendrive y conectarlo al radio (o `adb install -r archivo.apk`).
2. Instalar primero **JH MUSIC** (si dice «App no instalada», **no desinstalar** la anterior: se pierde Mi Mix), después **JH RADIO**, **JH VIDEO** y el **launcher**.
3. Abrir JH MUSIC una vez y aceptar el permiso de almacenamiento.
4. Pulsar Home, elegir **JH Launcher** y **Siempre**.

### Volver al launcher de fábrica
- **Un momento:** APPS → **LAUNCHER DE FÁBRICA**. Home vuelve a JH Launcher.
- **Como inicio:** Ajustes → Aplicaciones → JH Launcher → *Abrir de forma predeterminada* → *Borrar valores predeterminados*. Al pulsar Home, Android vuelve a preguntar.
- Sin comprobar en el radio: si su botón Home físico abre directo la pantalla de fábrica, Atrás o recientes llevan de vuelta a JH Launcher.

## Qué revisar en el Tucson

| # | Prueba | Qué debe pasar |
|---|---|---|
| 1 | Barra de arriba | Saludo, hora y fecha correctas; USB 1 y USB 2 en verde si hay pendrives |
| 2 | Auto | Cambia de ángulo cada ~3,6 s; los puntos marcan el ángulo; Negro / Gris Acero cambia el color |
| 3 | Música | Con una canción sonando en JH MUSIC, el launcher muestra título, artista y tiempo; ⏮ ▶ ⏭ la controlan |
| 4 | Barra de tiempo | Tocar la mitad lleva la canción a la mitad |
| 5 | JH MUSIC cerrada | ▶ la abre en segundo plano y suena tras unos segundos (anotar cuánto tarda) |
| 6 | Bluetooth | Con el teléfono sonando, el launcher pasa solo a BLUETOOTH AUDIO y ⏮ ▶ ⏭ controlan el teléfono |
| 7 | Gestos sobre el auto | Deslizar = siguiente / anterior; doble toque = play / pausa; vertical = volumen |
| 8 | MUTE | Silencia y al volver a tocar el sonido regresa, también con música Bluetooth |
| 9 | Radio | El carrusel muestra las emisoras de JH RADIO; tocar una la sintoniza y queda marcada |
| 10 | APPS | Abre el cajón; LAUNCHER DE FÁBRICA abre el original y Home vuelve |
| 11 | LLAMADAS | Abre la app de llamadas Bluetooth de fábrica |
| 12 | CPU | Anotar qué marca (en el emulador siempre sale «--»; ver [MEJORAS.md](MEJORAS.md) §6.3) |

Si el launcher no muestra la canción después de reabrir JH MUSIC, darle el permiso de **Almacenamiento** a JH Launcher en Ajustes → Aplicaciones (el launcher no lo pide por sí solo).

### Medir (con ADB)
```bash
adb shell dumpsys gfxinfo com.jh.launcher reset
# esperar 10 s con el launcher a la vista
adb shell dumpsys gfxinfo com.jh.launcher
adb shell dumpsys meminfo com.jh.launcher
```
Anotar los resultados en «Resultados en el radio» de [ANALISIS-RENDIMIENTO.md](ANALISIS-RENDIMIENTO.md). Referencia del emulador: ~19 cuadros/s en reposo y 6 ms en el percentil 90.

## Panel ABIERTAS (quitado en la 1.6.5)

Entre la 1.4 y la 1.6.4 el dock tenía **ABIERTAS**: un panel para ver y cerrar las apps abiertas, porque JH Launcher oculta la barra de fábrica que traía el botón de recientes. Se quitó en la 1.6.5 (botón, panel, `OpenAppsActivity`, `OpenedApps` y el permiso `KILL_BACKGROUND_PROCESSES`). En el radio (Android 8.1) Android no deja ver las apps de otros, así que el panel solo conocía las que se abrieron desde el launcher. El código está en el historial de git (commit `2024f5b`) por si se quiere volver a poner.

## Maqueta web

`index.html`, `style.css` y `app.js` son la maqueta de diseño original (no van en el APK). Abrir `index.html` en el navegador a 1024 × 600. No tiene todo lo nuevo de la app (Bluetooth, carrusel de radio).

## Propuestas y pendientes

En [MEJORAS.md](MEJORAS.md).
