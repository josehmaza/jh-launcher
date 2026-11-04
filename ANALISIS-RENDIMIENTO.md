# Análisis de rendimiento · JH Launcher nativo

**Fecha:** 2026-10-25
**Equipo objetivo:** radio de la Tucson ix35 · Autochips AC8227L (4× Cortex-A7 @ 1.3 GHz) · Mali-400 MP2 · 1024×600 @ 160 dpi
**Dónde se midió:** emulador `radio-tucson` (Android 8.1 / API 27, 1024×600, 160 dpi)

> ⚠️ El emulador corre en el procesador x86 del PC, mucho más rápido que el AC8227L.
> **Los números absolutos en el radio serán peores**; lo que sí vale son las **comparaciones**
> entre estados y entre versiones. Ver [Cómo medir en el radio](#cómo-medir-en-el-radio).

---

## Resumen

| Estado | Cuadros dibujados | CPU (1 núcleo) | Veredicto |
|---|---|---|---|
| Segundo plano (JH Music al frente) | 0 | 0 % | ✅ No consume nada |
| Modo Stealth | ~1 por segundo | ~0,4 % | ✅ Casi nada |
| Visible, música en pausa | ~18 por segundo | ~8–11 % | 🟠 Mejorable (punto 3 y 5) |
| Visible, música sonando | ~19 por segundo (antes ~56) | ~11 % (antes 25–33 %) | ✅ Resuelto con la opción B (punto 1) |

| Recurso | Valor | Veredicto |
|---|---|---|
| Memoria RAM (PSS total) | 19,5 MB | ✅ Excelente (la versión WebView usaba ~184 MB) |
| Sobredibujado de la GPU | 1x–2x en casi toda la pantalla | ✅ Bien |
| Vistas en pantalla | 170 | ✅ Razonable |
| Armar la pantalla (`setContentView`) | 222–568 ms debug / 44–53 ms release | ✅ Ya se compila release (punto 2) |

**Revisión 2026-10-31** (release, con el nuevo diseño del auto): en reposo, ~19 cuadros por segundo. Son ~10 del punto que late (punto 3) y ~10 del cambio de ángulo del auto (punto 5). Con los dos puntos aplicados bajaría a ~6 por segundo (estimado).

---

## Problemas, ordenados por impacto

Estado: `[ ]` pendiente · `[x]` aplicado · `[-]` descartado

### 1. 🔴 El vinilo girando es el mayor gasto mientras suena música

- [x] Decidir y aplicar · **Aplicada la opción B** (ver resultado al final de este punto)

**Qué pasa:** con música sonando la pantalla se redibuja 56 veces por segundo y gasta 25–33 % de un núcleo.

**Por qué es caro:** en este Android el sistema **no puede redibujar solo la parte que cambia**; el log lo confirma:

```
W/OpenGLRenderer: Failed to choose config with EGL_SWAP_BEHAVIOR_PRESERVED, retrying without...
D/OpenGLRenderer: Swap behavior 0
```

Así que para girar un disco de 78 dp se repintan los 170 elementos de **toda la pantalla** en cada cuadro.
En la Mali-400 ese repintado completo puede acercarse a los 16 ms que dura un cuadro, con riesgo de que el giro se vea a tirones.

**Opciones (decisión de diseño):**

| Opción | Costo estimado | Cómo se ve |
|---|---|---|
| A. Dejar como está (60 cuadros/s) | 100 % | Fluido en el emulador; en el radio, por confirmar |
| B. Girar a ~20 cuadros/s | ~1/3 | Algo menos fluido |
| C. Vinilo quieto, asomado fuera de la carátula | ~0 | Sin giro |

Se compararon 6 variantes en `demos-reproduccion.html` (raíz del repo).

**Decisión (2026-10-25):** opción **B · Latido con el segundero**. Mientras suena:
- El vinilo sale de la carátula y queda **quieto** (ya no gira).
- La carátula se ilumina con borde cian.
- Debajo del artista aparece **"● REPRODUCIENDO"**; el punto late una vez por segundo **en el mismo
  repintado que ya hace el contador de tiempo**, así que no agrega ninguno. En pausa dice "● EN PAUSA" en gris.

**Resultado medido (emulador, música sonando, 10 s):**

| | Antes (vinilo girando) | Después (opción B) |
|---|---|---|
| Cuadros dibujados | 552–558 (~56/s) | 191 (~19/s) |
| CPU | 25–33 % de un núcleo | 11 % de un núcleo |

Con música ahora cuesta lo mismo que en reposo. Lo que queda de esos ~19 cuadros/s es el punto
de "360° ACTIVO" (punto 3) y el fundido del auto (punto 5).

---

### 2. 🔴 Al radio se le instala el APK de *debug*

- [x] Aplicado (2026-10-31): release firmado con la clave de debug, aviso `ExpiredTargetSdkVersion` desactivado y `build.sh` con `assembleRelease`. Además `build.sh` hace `unset MSYS_NO_PATHCONV`, que rompía `gradlew` en Git Bash.

**Qué pasa:** `build.sh` compila `assembleDebug`. Android ejecuta las apps *debug* sin optimizar para poder depurarlas.

**Medido (mismo código, mismo emulador):**

| | Debug (actual) | Release |
|---|---|---|
| Armar la pantalla (`setContentView`) | 222–568 ms | 44–53 ms |
| CPU con música sonando | 33 % | 25 % |

**Solución (no cambia nada visible):**
- Compilar *release* firmado con la misma clave de debug, para no tener que desinstalar en el radio.
- Desactivar el aviso de lint `ExpiredTargetSdkVersion` (exigencia de Google Play que hoy bloquea el build release y no aplica a una app instalada a mano).
- Que `build.sh` genere el APK release.

---

### 3. 🟠 El punto que late en "360° ACTIVO"

- [ ] Decidir y aplicar

**Qué pasa:** aunque late a 10 cuadros/s (no a 60), en reposo genera **más de la mitad** del trabajo:
100 de los 179 cuadros cada 10 s, porque cada latido repinta toda la pantalla (ver punto 1).

**Opción:** dejar el punto fijo. El trabajo en reposo bajaría a menos de la mitad.

**Otra opción (revisión 2026-10-31):** que lata una vez por segundo dentro de `progressTick`, el repintado que ya hace el contador, igual que "● REPRODUCIENDO". Así sigue viéndose vivo sin agregar repintados. Hoy sigue a 100 ms (`PULSE_STEP_MS`).

**Decisión:** _pendiente_

---

### 4. 🟠 Detección de pendrives USB

- [ ] Aplicar

**Dónde:** `HwMonitor.java` (`isUsb1Connected` / `isUsb2Connected`) y `hwPoll` en `MainActivity.java`.

**Qué pasa:**
- Revisa **9 rutas cada 3 s**.
- **Lista el contenido completo de la raíz del USB** (`File.list()`) en cada revisión.
- **Escribe 9–12 líneas en el log** cada vez (`Log.d("JH-USB", ...)`): medidas 24–36 líneas cada 10 s.
- Vuelve a poner el fondo y los colores de las etiquetas USB **aunque no cambien**, lo que fuerza un repintado.
- Es **redundante**: ya hay un receptor (`usbReceiver`) que avisa cuando se monta o desmonta un almacenamiento.

**Solución (no cambia nada visible):**
- Revisar solo al volver al launcher (`onResume`) y cuando llegue el aviso de montaje.
- Sin escribir en el log.
- Comprobar solo que la carpeta exista y se pueda leer (sin listar su contenido).
- Actualizar las etiquetas solo si el estado cambió.

**Revisión 2026-10-31:** el log ya se quitó; lo demás sigue igual (`checkUsbPaths` sigue llamando a `list()` y `setUsbIndicatorState` vuelve a poner fondo y colores en cada ciclo).

---

### 5. 🟡 Giro del auto

- [ ] Decidir y aplicar

**Qué pasa:** cada 3,6 s hay un fundido de ~0,5 s (~30 cuadros).

**Opción:** cambiar cada 6 s → ~40 % menos de ese trabajo. Es una decisión de diseño.

**Revisión 2026-10-31:** con el nuevo diseño, cada cambio es un deslizamiento de ~0,6 s (200 ms de salida + 380 ms de entrada) cada 3,6 s: ~10 cuadros por segundo de promedio, la mitad del gasto en reposo. Cada 7 s bajaría a ~5.

**Decisión:** _pendiente_

---

### 6. 🟡 Menores

- [ ] El % de almacenamiento (ALM) se lee cada 3 s; cambia muy poco, podría leerse cada minuto.
- [x] Las fuentes `.ttf` van comprimidas dentro del APK y se descomprimen al arrancar (15–100 ms). **Aplicado 2026-10-31** con `aaptOptions { noCompress 'ttf' }`.
- [ ] La barra superior y el dock pintan un fondo casi igual al de la ventana (un poco de sobredibujado evitable).
- [ ] Las fotos del auto pasaron de RGB_565 (~400 KB) a ARGB_8888 (~800 KB) por el fundido de bordes: ~4,8 MB las 6. Se pueden escalar al tamaño real de la vista al cargarlas (se muestran mucho más chicas que los 600 × 335 px de la foto).

---

## Lo que ya está optimizado

Para no perderlo de vista al hacer cambios:

- **Nada se anima ni se consulta si el launcher no se ve** (segundo plano o modo Stealth): auto, vinilo, punto y telemetría se detienen. En Stealth además se oculta la interfaz de abajo.
- **Reloj** actualizado por el aviso por minuto del sistema (`ACTION_TIME_TICK`), sin timer propio.
- **Fotos del auto** decodificadas una sola vez en un hilo aparte, con los bordes fundidos horneados en ese momento: dibujarlas no cuesta nada extra. El halo bajo el auto (`CarHaloView`) es estático.
- **Vinilo** dibujado una sola vez como imagen y **quieto**: "está sonando" se muestra con la carátula iluminada y el latido de "● REPRODUCIENDO", sincronizado con el contador para no agregar repintados (punto 1).
- **Barras** de progreso y telemetría movidas con `scaleX` (sin recalcular la pantalla).
- **Textos que cambian seguido** (tiempo de la canción, porcentajes, volumen) con ancho fijo, para no recalcular la pantalla entera.
- **Lecturas de archivos, CPU y RAM** en un hilo aparte (`jh-io`).
- **Volumen por gesto**: solo se llama al sistema cuando cambia el nivel real.

---

## Cómo medir en el radio

Con el radio conectado por ADB y el launcher a la vista:

```bash
# Memoria (mirar TOTAL)
adb shell dumpsys meminfo com.jh.launcher

# Fluidez: reiniciar contadores, dejar sonar música ~10 s y leer
adb shell dumpsys gfxinfo com.jh.launcher reset
adb shell dumpsys gfxinfo com.jh.launcher
#   "90th percentile" > 16 ms  → el vinilo/animaciones se ven a tirones
#   "Janky frames" alto        → cuadros que no llegaron a tiempo

# Arranque en frío (TotalTime en ms)
adb shell am force-stop com.jh.launcher
adb shell am start -W -n com.jh.launcher/.MainActivity
```

### Resultados en el radio

| Medición | Valor | Fecha |
|---|---|---|
| Memoria TOTAL | | |
| 90th percentile con música | | |
| Janky frames con música | | |
| Arranque en frío (TotalTime) | | |
