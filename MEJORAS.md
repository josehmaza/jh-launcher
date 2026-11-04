# Propuestas y Mejoras de Diseño, Optimización y Ergonomía
## JH LAUNCHER · Hyundai Tucson ix35 2013

Este documento recopila las directrices de diseño, arquitectura técnica y facilidad de manejo para la interfaz de inicio (**Launcher**) de la pantalla Android del **Hyundai Tucson ix35 2013**.

Todas las propuestas parten del hardware real del vehículo:
- **Pantalla:** 1024 × 600 px (160 dpi, 60 Hz).
- **Procesador:** Autochips AC8227L (4 núcleos Cortex-A7 @ 1.3 GHz, 32 bits).
- **Gráficos:** Mali-400 MP2 (gama de entrada).
- **Memoria:** eMMC lenta (~35 MB/s) y RAM ajustada.
- **Conducción:** **Sin botones de control en el volante** (todo se opera tocando la pantalla directamente mientras se maneja).
- **Uso:** 100% **Offline** (sin depender de Wi-Fi ni planes de datos móviles).

---

## 1. Diseños de Alta Gama (Inspiración OEM Luxury)

Las pantallas genéricas chinas saturan la vista con colores primarios chillones (azules planos, fotos genéricas de baja resolución y fuentes estándar de Android). Los sistemas de fabricantes de lujo (**Porsche PCM, Mercedes-Benz MBUX Hyperscreen, Polestar / Android Automotive OS y Tesla OS**) siguen principios opuestos:

### 1.1. Filosofía "Zero Layer" (Capa Cero de Mercedes MBUX)
* **Regla:** Ninguna función crítica debe estar oculta tras un menú o navegación.
* **Aplicación en JH Launcher:**
  - El reproductor de música (**JH Music**), la sintonización de **Radio FM** y el control de **Bluetooth** residen directamente en la pantalla de inicio con controles táctiles vivos.
  - No hace falta abrir la app de música ni la app de radio para pausar, avanzar de pista o saltar de emisora.

### 1.2. Paleta cromática "Obsidian & Cyan Accent"
* **Fondo profundo:** Fondo negro obsidiana (`#06080c`) con gradiente sutil. En pantallas LCD automotrices mejora el contraste percibido, reduce el resplandor nocturno y da la sensación visual de panel OLED.
* **Acento luminoso controlado:** Uso de cian helado (`#00d2ff`) para elementos interactivos y estados activos. Es el color de mayor visibilidad periférica en visión nocturna humana.
* **Jerarquía de texto:**
  - Blanco puro (`#ffffff`) únicamente para datos primarios (título de canción, hora, saludo).
  - Gris pizarra (`#94a3b8`) para artistas y frecuencias.
  - Azul apagado (`#526077`) para etiquetas secundarias que no deben distraer.

### 1.3. Gemelo Digital 3D (Tucson ix35 2013)
* **Visualización de alta fidelidad:** Renderizado de estudio del modelo auténtico **Tucson ix35 generación 2 (LM 2013)** con faros rasgados característicos y parrilla hexagonal de esa época.
* **Plataforma circular con halo de luz:** Base circular estilo sala de exposición que otorga profundidad tridimensional sin sobrecargar la pantalla.
* **Cambio automático de ángulo (aplicado):** cada 3,6 s el auto pasa al ángulo siguiente (frente, perfil y trasera) deslizándose de lado con un fundido, sin que el conductor toque nada. No es un giro continuo y **no se gira con el dedo**: el arrastre se descartó para no chocar con los gestos a ciegas (§3.7).
* **Selector sobrio de 2 colores:** Switch minimalista de un toque para alternar entre **Negro Obsidiana** (color del auto) y **Gris Acero** (Steel Grey).

### 1.4. Tipografía "Zero Jitter" (Sin vibración visual)
* Para reloj digital y frecuencia de radio se utiliza **JetBrains Mono** con números tabulares de ancho idéntico. Esto evita que al cambiar un minuto o un decimal de frecuencia el texto "salte" o tiemble en la pantalla.
* Para el resto de la interfaz se utiliza **Plus Jakarta Sans**, una tipografía geométrica con excelente legibilidad en ángulos de visión oblicuos (posición del conductor).

---

## 2. Optimización Extrema de Rendimiento (GPU & CPU Friendly)

El procesador Cortex-A7 y la GPU Mali-400 no soportan efectos gráficos modernos de escritorio sin congelarse. La interfaz debe correr a **60 cuadros por segundo sólidos** siguiendo estas reglas:

### 2.1. Cero desenfoques Gaussianos (`backdrop-filter: blur`)
* Los efectos tipo *glassmorphism* con desenfoque de fondo dinámico consumen hasta el 80% de la GPU Mali-400 al redibujarse.
* **Solución:** Se sustituyen por fondos sólidos oscuros con opacidad directa (`rgba(14, 19, 27, 0.98)`) y micro-bordes nítidos (`1px solid rgba(255, 255, 255, 0.08)`). El aspecto visual es igualmente premium pero con **0% de impacto en la GPU**.

### 2.2. Aceleración por Hardware (`transform: translate3d`)
* Todas las transiciones de ángulo del auto y las microinteracciones de botones utilizan transformaciones 3D compuestas por la GPU (`translate3d(0,0,0)` / `scale`), evitando que el procesador recalcule el diseño (*reflow / repaint*).

### 2.3. Cero consumo de red / Wi-Fi en segundo plano
* Eliminación de APIs de clima online o mapas en streaming que intenten buscar conexión al encender el auto.
* El Launcher lee exclusivamente el **reloj interno de hardware (RTC)** y el estado local de los puertos USB.
* **Tiempo de arranque objetivo:** Menos de 1.2 segundos desde que el sistema Android despierta.

### 2.4. Huella de memoria (RAM) ultra reducida
* Mantener el consumo total del Launcher por debajo de **35 MB de RAM**, dejando el resto de los escasos 1.8 GB libres para que [JH Music](file:///d:/workspaces/general/jh-music) y [JH Video](file:///d:/workspaces/general/jh-video) decodifiquen audio y video sin microcortes (*stuttering*).

### 2.5. Micro-Monitor de Rendimiento en Tiempo Real (CPU, RAM y Almacenamiento)
* **Objetivo:** Tener visibilidad continua del estado del hardware modesto (Autochips AC8227L) sin recargar la pantalla con paneles pesados.
* **Variantes de visualización maquetadas:**
  - **Demo 1 (Cápsula en la Barra Superior):** `[ CPU 24% · RAM 54% · ALM 38% ]`. Ubicada junto a los indicadores USB; discreta, elegante, siempre visible y no le resta ni un píxel a las tarjetas de música o video.
  - **Demo 2 (Mini Telemetría bajo el Auto):** 3 chips horizontales con barras de progreso micro (`CPU`, `RAM 1.1/2GB`, `ALMACENAMIENTO eMMC`) situados debajo del Tucson 2013.
* **Integración con [JH Monitor](file:///d:/workspaces/general/jh-car/jh-monitor/README.md):** Al tocar cualquiera de los dos monitores, se lanza automáticamente la app nativa `com.jh.monitor` para ver el informe completo de hardware, frecuencias y temperaturas.

---

## 3. Facilidad de Manejo y Seguridad al Conducir (Ergonomía sin volante)

Al no disponer de botones en el volante del Tucson, **la pantalla es el único mando**. Un diseño para conducir debe regirse por la **Ley de Fitts** (objetivos grandes son más rápidos y seguros de presionar):

### 3.1. Objetivos táctiles masivos (Zonas de toque de 56 a 70 dp)
* Ningún botón de uso frecuente mide menos de 50 dp de alto:
  - Botones de **Play / Pausa, Anterior y Siguiente**: altura de 56 dp con espaciado generoso entre sí para no tocar el botón equivocado por vibraciones del asfalto.
  - Botón **ABRIR JH MUSIC**: barra completa horizontal de más de 200 px de ancho.
  - Botones del Dock inferior: 82 px de ancho y 50 px de alto.

### 3.2. Selector inteligente de Fuente de Audio (`USB` vs `Bluetooth`)
* **Problema común:** Si estás escuchando música de un pendrive y luego quieres reproducir un video de YouTube o un podcast desde el teléfono por Bluetooth, en los radios chinos debes salirte a otra app fea y buscar botones pequeños.
* **Mejora implementada:**
  - Pestaña **`JH MUSIC (USB)`**: control total de las canciones locales en los pendrives.
  - Pestaña **`BLUETOOTH AUDIO`**: controla **YouTube / Spotify en el teléfono** con los mismos botones de Play/Pausa, Siguiente y Anterior, a través del servicio Bluetooth del radio (`com.jancar.btservice`), y muestra la canción si el teléfono la manda. El launcher cambia solo a esta pestaña cuando el teléfono empieza a sonar. Pendiente de confirmar en el Tucson.

### 3.3. Emisoras de Radio FM en el inicio (aplicado con JH RADIO)
* La tarjeta muestra un **carrusel con las emisoras guardadas en [JH RADIO](https://github.com/josehmaza/jh-radio)** (desplazamiento horizontal). Tocar una la sintoniza sin salir del launcher (orden `GOTO`); la que suena queda marcada. Ya no hay botones `◄` / `►`.
* Para escanear la banda o guardar emisoras, el botón de la tarjeta abre JH RADIO.

### 3.4. Modo "Stealth" (Sigilo Nocturno tipo Porsche)
* **Problema:** En carretera oscura o autopista sin iluminación, la pantalla de 10" encandila y cansa la vista.
* **Mejora:** Un botón directo **STEALTH** en el dock apaga al 90% todos los elementos gráficos, dejando únicamente un reloj digital tenue y el nombre de la canción en tono gris oscuro.
* **Despertar instantáneo:** Con tocar cualquier punto de la pantalla, la interfaz completa se reactiva de inmediato.

### 3.5. Botón de MUTE / Silencio de pánico
* Ubicado en la esquina inferior izquierda (zona de más fácil acceso para la mano derecha del conductor).
* Silencia instantáneamente el audio al llegar a un peaje, una llamada o una maniobra de estacionamiento difícil.
* **Cómo silencia (aplicado):** baja el volumen a 0 y lo devuelve al de antes. El silencio de Android (`ADJUST_MUTE`) dejaba mudo el audio Bluetooth de este radio para siempre.

### 3.6. Botón de Ajustes y Rescate del Sistema
* Siempre visible en el extremo inferior derecho. Si alguna vez necesitas entrar a los ajustes del auto, emparejar un nuevo Bluetooth o volver al launcher de fábrica, se accede con 1 toque sin riesgo de quedar atrapado.

### 3.7. Gestos Táctiles a Ciegas (Blind Touch Gestures en el área del auto)
* **Inspiración:** Audi MMI Touch y Polestar.
* **Problema:** Apuntar con el dedo a un botón distrae la vista del tráfico.
* **Aplicado (con 1 dedo):** la tarjeta del auto funciona como *touchpad* para gestos a ciegas:
  - **Deslizar a la derecha ➔** siguiente canción (de JH MUSIC o del teléfono, según la pestaña).
  - **Deslizar a la izquierda ➔** canción anterior.
  - **Doble toque ➔** Play / Pausa.
  - **Arrastrar arriba / abajo ➔** volumen (§3.8).
  - La propuesta original era con 2 dedos y también para cambiar de emisora; quedó con 1 dedo y solo para música.
* **Beneficio:** Controlar la música y videos sin desviar los ojos de la carretera.

### 3.8. Control Rápido de Volumen Gigante (HUD Volume)
* **Inspiración:** Tesla Model Y / Mercedes MBUX.
* **Problema:** El marco plástico izquierdo de la pantalla tiene botones `+` y `-` diminutos difíciles de acertar con vibraciones.
* **Propuesta:** Botones amplios de volumen de 55 px en el dock inferior o gesto de deslizamiento vertical en el borde derecho para regular el volumen de 0 a 100 con indicador numérico grande.
* **Aplicado:** el gesto vertical va sobre la tarjeta del auto (no en el borde), con un indicador grande; 240 dp de recorrido = 0 a 100 %, igual que en JH MUSIC. No hay botones de volumen en el dock.

### 3.9. Conmutación Automática Día / Noche por Horario (Smart Ambience)
* **Inspiración:** Porsche PCM.
* **Propuesta:** A las **18:30 h**, el Launcher atenúa suavemente el brillo y aplica tonos ámbar/cálidos a los textos para evitar fatiga visual nocturna; a las **06:30 h**, restaura automáticamente el contraste diurno alto.

### 3.10. Botón de Acceso Directo a Cámara Trasera / Auxiliar
* **Inspiración:** BMW iDrive / Range Rover.
* **Propuesta:** Botón directo en pantalla para forzar la visualización de la cámara de reversa sin necesidad de engranar la marcha atrás con la palanca de cambios (útil al estacionar muy pegado o antes de iniciar la marcha).

### 3.11. Presets Rápidos de Sonido (Ecualizador al Vuelo)
* **Inspiración:** Sistemas Burmester / Harman Kardon.
* **Propuesta:** Selector de 4 presets en la tarjeta de música (`[ Rock ]`, `[ Bass Boost ]`, `[ Voces ]`, `[ Plano ]`) que comunican con el chip DSP de la radio con un solo toque.

### 3.12. Temporizador Inteligente de Reposo (Auto-Stealth en Autopista)
* **Propuesta:** Si el vehículo está en marcha y no se toca la pantalla en 2 minutos mientras suena audio, el sistema entra suavemente en modo Stealth para reducir distracciones, despertando al tocar cualquier parte.

### 3.13. Alerta Térmica Preventiva (CPU AC8227L > 78 °C)
* **Inspiración:** Diagnóstico térmico de [JH Monitor](file:///d:/workspaces/general/jh-car/jh-monitor/README.md).
* **Propuesta:** Un punto LED discreto en la barra superior que permanece apagado y **solo si la CPU supera los 78 °C** se ilumina en ámbar sutil para avisar al conductor antes de que el radio sufra ralentización térmica.

---

## 4. Matriz Comparativa: Interfaz Actual vs. JH Launcher

| Característica | Interfaz de Fábrica Actual (Foto) | JH Launcher 1.6.5 |
|---|---|---|
| **Vehículo mostrado** | Sedán genérico ajeno al auto | **Fotos del Hyundai Tucson ix35 2013 en 3 ángulos** |
| **Saludo** | *"Hola, Jhon"* (genérico de fábrica) | **"Hola, Hernan"** |
| **Control de Música** | Obliga a abrir la app de fábrica | **Control Zero Layer directo (Play/Pausa/Skip y barra de tiempo)** |
| **Soporte Bluetooth** | App separada y tosca | **Pestaña BLUETOOTH AUDIO para YouTube/Spotify del teléfono** |
| **Sintonizador Radio** | Solo abre la app | **Carrusel de emisoras guardadas en el inicio** |
| **Integración local** | Apps viejas de fábrica | **Acceso directo a JH Music y JH Video** |
| **Uso Nocturno** | Brillo fijo que encandila | **Modo Stealth (Sigilo antirreflejo)** |
| **Ergonomía sin volante** | Botones pequeños y rígidos | **Zonas táctiles gigantes (> 56 dp)** |
| **Conexión requerida** | Ninguna / Intenta cargar cosas | **100% Offline, cero llamadas Wi-Fi** |

---

## 5. Hoja de Ruta de Implementación Técnica (Android Nativo)

1. **Fase 1: Validación visual de maqueta web**
   - Probar en navegador la maqueta en [D:\workspaces\general\jh-launcher\index.html](file:///d:/workspaces/general/jh-launcher/index.html) a resolución 1024 × 600 px.
   - Ajustar últimos detalles de gusto personal (colores, espaciados).

2. **Fase 2: Estructura del proyecto Android nativo**
   - Proyecto en `android/` con Gradle Wrapper (Java 8, `minSdkVersion 21`, `targetSdkVersion 27` / Android 8.1 real).
   - Declarar en `AndroidManifest.xml` la categoría `android.intent.category.HOME` y `DEFAULT`.

3. **Fase 3: Integración de Intents nativos de hardware**
   - Lanzamiento directo de `com.jh.music` y `com.jh.video`.
   - Control de Radio FM a través de JH RADIO (que maneja la radio de fábrica `com.jancar.radio`).
   - Control del audio Bluetooth con el servicio del radio `com.jancar.btservice` (antes se pensó en las teclas multimedia estándar).
   - Detección de montaje/desmontaje de USB mediante `Intent.ACTION_MEDIA_MOUNTED`.

4. **Fase 4: Validación en emulador `radio-tucson` y auto**
   - Compilación con `build.sh`.
   - Prueba en el emulador de PC configurado para 1024 × 600 px (Android 8.1 API 27).
   - Instalación vía pendrive USB en el Tucson ix35 2013 y activación como Launcher predeterminado.

---

## 6. Revisión de diseño e interacción (31-oct-2026)

Revisión de la app nativa (código y capturas del emulador). Lo de rendimiento está en [ANALISIS-RENDIMIENTO.md](ANALISIS-RENDIMIENTO.md).

### Ya aplicado

- **Tarjeta del auto, variante D** (elegida entre 6 en [demos-auto-360.html](demos-auto-360.html)):
  - bordes de la foto fundidos con la tarjeta, horneados una vez al cargarla;
  - halo cian estático bajo el auto (`CarHaloView`);
  - cambio de ángulo deslizando el auto de lado;
  - indicador de 3 puntos del ángulo actual.
- **Estado "sin canción".** Si JH Music abre sin canción cargada (su última canción era de un pendrive que no está puesto), la tarjeta de música dice "Selecciona una canción" con el progreso en cero, en vez de seguir mostrando la canción vieja. Con ese estado, ▶ y saltar muestran el aviso "Elige una canción en JH Music" en vez de aparentar que funcionaron.
- **Sin arrastre para girar el auto.** Se descartó para no chocar con los gestos a ciegas (deslizar en el auto = siguiente/anterior).
- **Frecuencia de radio real (era el 6.2).** La tarjeta ya no usa la lista fija `FM_PRESETS`: muestra las emisoras y la frecuencia que le manda JH RADIO, que la lee del servicio de la radio de fábrica.

### Pendiente

| # | Hallazgo | Propuesta | Esfuerzo |
|---|---|---|---|
| 6.1 | **La tarjeta de música no muestra la carátula.** Usa un ícono de disco aunque la canción tenga carátula: JH Music solo comparte título, artista, ruta y duración. La tarjeta además tiene un hueco vacío arriba de la canción. | JH Music guarda una miniatura de ~200 px junto a `.jh_music_state.json` (propuesta #21 de JH Music) y el launcher la muestra en ese hueco, más grande. Es el cambio más visible de la pantalla principal. | Medio (las dos apps) |
| 6.3 | **El indicador de CPU puede no servir.** En el emulador siempre marca "--". En el radio, si `/proc/stat` no se puede leer (Android 8 lo restringe a las apps), `HwMonitor` cae a calcularlo con la frecuencia de los núcleos, que no es el uso real: según el gobernador del radio podría marcar siempre lo mismo. Sin comprobar en el radio. | Comprobarlo en el radio. Si no sirve, reemplazarlo por un dato útil o quitarlo y dar más ancho a RAM y ALM. | Bajo |
| 6.4 | **Los gestos a ciegas no se descubren.** Nada indica que el auto funciona como touchpad (deslizar = cambiar canción, doble toque = play/pausa, vertical = volumen). | Una leyenda tenue bajo el auto las primeras veces, o al tocarlo una vez sin hacer gesto. | Bajo |
