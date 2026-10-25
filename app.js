/* ==========================================================================
   JH LAUNCHER · HYUNDAI TUCSON ix35 2013 (app.js)
   Rotación automática 360°, Selector de Color, Modo Stealth y Zero Layer
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {

  // --------------------------------------------------------------------------
  // 1. ROTACIÓN AUTOMÁTICA SUAVE 360° DEL HYUNDAI TUCSON 2013
  // --------------------------------------------------------------------------
  const carImage = document.getElementById('carImage');
  const btnColorBlack = document.getElementById('btnColorBlack');
  const btnColorGrey = document.getElementById('btnColorGrey');
  const btnQuickColor = document.getElementById('btnQuickColor');

  // Renders del Tucson 2013
  const vehicleAngles = {
    black: [
      'assets/tucson2013_black_front.jpg', // Frontal 3/4
      'assets/tucson2013_black_side.jpg',  // Lateral perfil
      'assets/tucson2013_black_rear.jpg'   // Trasera 3/4
    ],
    grey: [
      'assets/tucson_angle_front_right.jpg',
      'assets/tucson_angle_side.jpg',
      'assets/tucson_angle_rear.jpg'
    ]
  };

  let currentColor = 'black'; // Por defecto: Negro
  let currentAngleIndex = 0;
  let autoSpinTimer = null;

  function updateCarView(fade = true) {
    const angles = vehicleAngles[currentColor];
    const targetSrc = angles[currentAngleIndex];

    if (fade) {
      carImage.style.opacity = '0.4';
      carImage.style.transform = 'scale(0.98)';
      setTimeout(() => {
        carImage.src = targetSrc;
        carImage.style.opacity = '1';
        carImage.style.transform = 'scale(1)';
      }, 150);
    } else {
      carImage.src = targetSrc;
      carImage.style.opacity = '1';
    }
  }

  // Rotación suave automática continua
  function startAutoSpin() {
    clearInterval(autoSpinTimer);
    autoSpinTimer = setInterval(() => {
      const angles = vehicleAngles[currentColor];
      currentAngleIndex = (currentAngleIndex + 1) % angles.length;
      updateCarView(true);
    }, 3600);
  }
  startAutoSpin();

  // Cambio de color entre los únicos 2 colores solicitados: Negro y Gris Acero
  function setColor(color) {
    if (currentColor === color) return;
    currentColor = color;

    btnColorBlack.classList.toggle('active', color === 'black');
    btnColorGrey.classList.toggle('active', color === 'grey');

    if (btnQuickColor) {
      btnQuickColor.textContent = color === 'black' ? '🎨 Tucson: Negro' : '🎨 Tucson: Gris Acero';
    }

    updateCarView(true);
    showToast(`Color Tucson: ${color === 'black' ? 'Negro Obsidiana' : 'Gris Acero'}`);
  }

  btnColorBlack.addEventListener('click', () => setColor('black'));
  btnColorGrey.addEventListener('click', () => setColor('grey'));

  if (btnQuickColor) {
    btnQuickColor.addEventListener('click', () => {
      setColor(currentColor === 'black' ? 'grey' : 'black');
    });
  }

  // --------------------------------------------------------------------------
  // 2. RELOJ Y FECHA OFFLINE (Reloj interno RTC de la radio)
  // --------------------------------------------------------------------------
  const statusClock = document.getElementById('statusClock');
  const statusDate = document.getElementById('statusDate');
  const stealthClock = document.getElementById('stealthClock');
  const stealthDate = document.getElementById('stealthDate');

  function updateSystemClock() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const timeStr = `${hours}:${minutes}`;

    statusClock.textContent = timeStr;
    if (stealthClock) stealthClock.textContent = timeStr;

    const days = ['DOMINGO', 'LUNES', 'MARTES', 'MIÉRCOLES', 'JUEVES', 'VIERNES', 'SÁBADO'];
    const months = ['ENERO', 'FEBRERO', 'MARZO', 'ABRIL', 'MAYO', 'JUNIO', 'JULIO', 'AGOSTO', 'SEPTIEMBRE', 'OCTUBRE', 'NOVIEMBRE', 'DICIEMBRE'];

    const dateStr = `${days[now.getDay()]}, ${now.getDate()} DE ${months[now.getMonth()]}`;
    statusDate.textContent = dateStr;
    if (stealthDate) stealthDate.textContent = dateStr;
  }
  updateSystemClock();
  setInterval(updateSystemClock, 1000);

  // --------------------------------------------------------------------------
  // 3. ZERO LAYER: CONTROL DE JH MUSIC EN PANTALLA PRINCIPAL
  // --------------------------------------------------------------------------
  // --------------------------------------------------------------------------
  // 3. ZERO LAYER: SELECTOR DE FUENTE (USB JH MUSIC vs BLUETOOTH TELÉFONO)
  // --------------------------------------------------------------------------
  const btnSourceUsb = document.getElementById('btnSourceUsb');
  const btnSourceBt = document.getElementById('btnSourceBt');
  const sourceExtraBadge = document.getElementById('sourceExtraBadge');
  const sourceStatusChip = document.getElementById('sourceStatusChip');
  const musicArtCover = document.getElementById('musicArtCover');
  const btnLaunchJhMusic = document.getElementById('btnLaunchJhMusic');

  const btnPlayPause = document.getElementById('btnPlayPause');
  const btnPrevTrack = document.getElementById('btnPrevTrack');
  const btnNextTrack = document.getElementById('btnNextTrack');
  const trackTitle = document.getElementById('trackTitle');
  const trackArtist = document.getElementById('trackArtist');
  const musicProgressFill = document.getElementById('musicProgressFill');
  const timeReadout = document.getElementById('timeReadout');
  const vinylDisc = document.getElementById('vinylDisc');
  const stealthTrackName = document.getElementById('stealthTrackName');

  let currentAudioSource = 'usb'; // 'usb' | 'bt'

  // Lista de canciones locales USB (JH Music)
  const usbTracks = [
    { title: "I'm Sorry", artist: "Aventura · The Last", duration: "04:36", totalSec: 276 },
    { title: "Ella y Yo", artist: "Don Omar ft. Aventura", duration: "03:27", totalSec: 207 },
    { title: "Dile al Amor", artist: "Aventura · The Last", duration: "03:49", totalSec: 229 },
    { title: "Obsesión", artist: "Aventura · We Broke The Rules", duration: "04:12", totalSec: 252 }
  ];

  // Lista de audio/videos en streaming vía Bluetooth desde el teléfono
  const btTracks = [
    { title: "Video en YouTube (Teléfono)", artist: "YouTube Music / App · Transmisión Bluetooth", duration: "03:45", totalSec: 225 },
    { title: "Canción en Spotify", artist: "Spotify Móvil · Audio A2DP / AVRCP", duration: "04:10", totalSec: 250 },
    { title: "Podcast / Audio en Vivo", artist: "Celular Conectado · Manos Libres", duration: "12:00", totalSec: 720 }
  ];

  let currentUsbIdx = 0;
  let currentBtIdx = 0;
  let isPlaying = false;
  let progressSec = 134;

  function updateTrackDisplay() {
    const list = currentAudioSource === 'usb' ? usbTracks : btTracks;
    const idx = currentAudioSource === 'usb' ? currentUsbIdx : currentBtIdx;
    const track = list[idx];

    trackTitle.textContent = track.title;
    trackArtist.textContent = track.artist;
    if (stealthTrackName) {
      stealthTrackName.textContent = `${track.title} — ${track.artist}`;
    }
  }

  function setAudioSource(source) {
    currentAudioSource = source;
    btnSourceUsb.classList.toggle('active', source === 'usb');
    btnSourceBt.classList.toggle('active', source === 'bt');

    if (source === 'usb') {
      sourceExtraBadge.textContent = "MI MIX (109)";
      sourceStatusChip.textContent = "USB 1";
      sourceStatusChip.style.color = "var(--accent-cyan)";
      sourceStatusChip.style.borderColor = "rgba(0, 210, 255, 0.35)";
      
      // Carátula de vinilo para música local
      musicArtCover.innerHTML = `
        <svg viewBox="0 0 24 24" fill="currentColor" class="art-placeholder-icon">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 0-4.5-2.01-4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5zm0-5.5c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1z"/>
        </svg>
      `;

      btnLaunchJhMusic.querySelector('.open-title').textContent = "ABRIR JH MUSIC";
      btnLaunchJhMusic.querySelector('.open-sub').textContent = "Ver carpetas y listas de reproducción";
      showToast("Fuente: JH MUSIC (Pendrive USB 1)");
    } else {
      sourceExtraBadge.textContent = "AVRCP REMOTO";
      sourceStatusChip.textContent = "BT AUDIO";
      sourceStatusChip.style.color = "var(--accent-cyan)";
      sourceStatusChip.style.borderColor = "rgba(0, 210, 255, 0.4)";

      // Icono de Bluetooth con onda de transmisión
      musicArtCover.innerHTML = `
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" class="art-placeholder-icon" style="color: var(--accent-cyan);">
          <path d="m7 7 10 10-5 5V2l5 5L7 17"/>
        </svg>
      `;

      btnLaunchJhMusic.querySelector('.open-title').textContent = "ABRIR MANOS LIBRES";
      btnLaunchJhMusic.querySelector('.open-sub').textContent = "Ver llamadas y teléfono conectado";
      showToast("Fuente: BLUETOOTH (Controles para YouTube / Spotify en el teléfono)");
    }

    updateTrackDisplay();
  }

  btnSourceUsb.addEventListener('click', () => setAudioSource('usb'));
  btnSourceBt.addEventListener('click', () => setAudioSource('bt'));

  // Play / Pausa (En USB pausa la app local; en BT envía AVRCP Play/Pause al teléfono)
  btnPlayPause.addEventListener('click', () => {
    isPlaying = !isPlaying;
    btnPlayPause.innerHTML = isPlaying
      ? `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`
      : `<svg class="icon-play" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>`;
    
    if (vinylDisc) {
      vinylDisc.classList.toggle('spinning', isPlaying);
    }

    if (currentAudioSource === 'usb') {
      showToast(isPlaying ? "JH Music: Reproduciendo" : "JH Music: En Pausa");
    } else {
      showToast(isPlaying ? "Bluetooth AVRCP: Play en Teléfono (YouTube)" : "Bluetooth AVRCP: Pausa en Teléfono");
    }
  });

  // Anterior canción / video
  btnPrevTrack.addEventListener('click', () => {
    if (currentAudioSource === 'usb') {
      currentUsbIdx = (currentUsbIdx - 1 + usbTracks.length) % usbTracks.length;
      showToast(`USB: ${usbTracks[currentUsbIdx].title}`);
    } else {
      currentBtIdx = (currentBtIdx - 1 + btTracks.length) % btTracks.length;
      showToast(`Bluetooth AVRCP: Video/canción anterior en el teléfono`);
    }
    updateTrackDisplay();
    progressSec = 0;
  });

  // Siguiente canción / video
  btnNextTrack.addEventListener('click', () => {
    if (currentAudioSource === 'usb') {
      currentUsbIdx = (currentUsbIdx + 1) % usbTracks.length;
      showToast(`USB: ${usbTracks[currentUsbIdx].title}`);
    } else {
      currentBtIdx = (currentBtIdx + 1) % btTracks.length;
      showToast(`Bluetooth AVRCP: Siguiente video/canción en el teléfono`);
    }
    updateTrackDisplay();
    progressSec = 0;
  });

  // Avance del tiempo de reproducción
  setInterval(() => {
    if (isPlaying) {
      progressSec += 1;
      const list = currentAudioSource === 'usb' ? usbTracks : btTracks;
      const idx = currentAudioSource === 'usb' ? currentUsbIdx : currentBtIdx;
      const totalSec = list[idx].totalSec;
      const pct = Math.min((progressSec / totalSec) * 100, 100);
      musicProgressFill.style.width = `${pct}%`;
      const mins = String(Math.floor(progressSec / 60)).padStart(2, '0');
      const secs = String(progressSec % 60).padStart(2, '0');
      timeReadout.textContent = `${mins}:${secs} / ${list[idx].duration}`;
    }
  }, 1000);

  // --------------------------------------------------------------------------
  // 4. MODO STEALTH (SIGILO NOCTURNO PORSCHE / TESLA)
  // --------------------------------------------------------------------------
  const btnStealthMode = document.getElementById('btnStealthMode');
  const btnStealthPreview = document.getElementById('btnStealthPreview');
  const stealthOverlay = document.getElementById('stealthOverlay');

  function enterStealthMode() {
    stealthOverlay.classList.add('active');
  }

  function exitStealthMode() {
    stealthOverlay.classList.remove('active');
    showToast("Pantalla restaurada");
  }

  if (btnStealthMode) btnStealthMode.addEventListener('click', enterStealthMode);
  if (btnStealthPreview) btnStealthPreview.addEventListener('click', enterStealthMode);
  if (stealthOverlay) stealthOverlay.addEventListener('click', exitStealthMode);

  // Mute Audio
  const btnMuteToggle = document.getElementById('btnMuteToggle');
  const muteLabel = document.getElementById('muteLabel');
  let isMuted = false;
  btnMuteToggle.addEventListener('click', () => {
    isMuted = !isMuted;
    btnMuteToggle.style.color = isMuted ? 'var(--accent-crimson)' : 'var(--text-secondary)';
    muteLabel.textContent = isMuted ? "MUTED" : "AUDIO";
    showToast(isMuted ? "Audio Silenciado (MUTE)" : "Audio Activado");
  });

  // --------------------------------------------------------------------------
  // 5. ACCIONES DE LANZAMIENTO DE APPS (NATIVAS EN ANDROID)
  // --------------------------------------------------------------------------
  const toast = document.getElementById('launchToast');
  const toastMsg = document.getElementById('toastMsg');
  let toastTimeout = null;

  function showToast(msg) {
    toastMsg.textContent = msg;
    toast.classList.add('show');
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => {
      toast.classList.remove('show');
    }, 2200);
  }

  // En Android nativo, estos clicks llaman a AndroidBridge o simulan el toast
  const launchMusicAction = () => {
    if (window.AndroidBridge && window.AndroidBridge.launchJhMusic) {
      window.AndroidBridge.launchJhMusic();
    } else {
      showToast("Iniciando app JH Music (com.jh.caraudio)...");
    }
  };
  document.getElementById('btnLaunchJhMusic').addEventListener('click', launchMusicAction);
  document.getElementById('dockBtnMusic').addEventListener('click', launchMusicAction);
  
  const launchVideoAction = () => {
    if (window.AndroidBridge && window.AndroidBridge.launchJhVideo) {
      window.AndroidBridge.launchJhVideo();
    } else {
      showToast("Iniciando app JH Video (com.jh.carvideo)...");
    }
  };
  document.getElementById('btnLaunchJhVideo').addEventListener('click', launchVideoAction);
  document.getElementById('dockBtnVideo').addEventListener('click', launchVideoAction);

  // Radio FM nativa y Sintonizador Rápido
  const radioFreqDisplay = document.getElementById('radioFreqDisplay');
  const btnRadioPrev = document.getElementById('btnRadioPrev');
  const btnRadioNext = document.getElementById('btnRadioNext');
  const radioFreqBadge = document.getElementById('radioFreqBadge');

  const fmPresets = [
    { freq: 88.5, name: "FM 88.5" },
    { freq: 91.3, name: "FM 91.3" },
    { freq: 93.7, name: "FM 93.7" },
    { freq: 96.1, name: "FM 96.1" },
    { freq: 98.9, name: "FM 98.9" },
    { freq: 101.7, name: "FM 101.7" },
    { freq: 104.5, name: "FM 104.5" },
    { freq: 107.9, name: "FM 107.9" }
  ];
  let currentFmIdx = 3; // 96.1 MHz por defecto

  function updateFmStation(index) {
    currentFmIdx = (index + fmPresets.length) % fmPresets.length;
    const station = fmPresets[currentFmIdx];
    if (radioFreqDisplay) {
      radioFreqDisplay.textContent = station.freq.toFixed(1);
    }
    showToast(`Radio FM: ${station.freq.toFixed(1)} MHz`);
  }

  if (btnRadioPrev) {
    btnRadioPrev.addEventListener('click', (e) => {
      e.stopPropagation();
      updateFmStation(currentFmIdx - 1);
      if (window.AndroidBridge && window.AndroidBridge.tuneRadioPrev) {
        window.AndroidBridge.tuneRadioPrev();
      }
    });
  }

  if (btnRadioNext) {
    btnRadioNext.addEventListener('click', (e) => {
      e.stopPropagation();
      updateFmStation(currentFmIdx + 1);
      if (window.AndroidBridge && window.AndroidBridge.tuneRadioNext) {
        window.AndroidBridge.tuneRadioNext();
      }
    });
  }

  const launchRadioAction = () => {
    if (window.AndroidBridge && window.AndroidBridge.launchRadio) {
      window.AndroidBridge.launchRadio();
    } else {
      showToast("Abriendo Radio FM nativa (com.autochips.radio)...");
    }
  };
  document.getElementById('btnLaunchRadio').addEventListener('click', launchRadioAction);
  document.getElementById('dockBtnRadio').addEventListener('click', launchRadioAction);
  if (radioFreqBadge) {
    radioFreqBadge.addEventListener('click', launchRadioAction);
  }

  // Bluetooth Manos Libres
  document.getElementById('dockBtnBt').addEventListener('click', () => {
    if (window.AndroidBridge && window.AndroidBridge.launchBluetooth) {
      window.AndroidBridge.launchBluetooth();
    } else {
      showToast("Abriendo Manos Libres Bluetooth (com.autochips.bluetooth)...");
    }
  });
  
  document.getElementById('btnAllApps').addEventListener('click', () => {
    if (window.AndroidBridge && window.AndroidBridge.launchAllApps) {
      window.AndroidBridge.launchAllApps();
    } else {
      showToast("Abriendo Cajón de Todas las Aplicaciones...");
    }
  });

  document.getElementById('btnSystemSettings').addEventListener('click', () => {
    if (window.AndroidBridge && window.AndroidBridge.launchSettings) {
      window.AndroidBridge.launchSettings();
    } else {
      showToast("Abriendo Configuración del Radio...");
    }
  });

  // --------------------------------------------------------------------------
  // 6. MONITOR DE HARDWARE PERMANENTE (BAJO EL AUTO)
  // --------------------------------------------------------------------------
  const vehicleHwBar = document.getElementById('vehicleHwBar');
  const subCpuVal = document.getElementById('subCpuVal');
  const subRamVal = document.getElementById('subRamVal');
  const subStorageVal = document.getElementById('subStorageVal');
  const subCpuFill = document.getElementById('subCpuFill');
  const subRamFill = document.getElementById('subRamFill');

  // Simulación dinámica realista del chip AC8227L
  setInterval(() => {
    // CPU fluctúa entre 18% y 32%
    const cpu = Math.floor(18 + Math.random() * 14);
    // RAM se mantiene entre 52% y 56% (1.05 a 1.12 GB)
    const ram = Math.floor(52 + Math.random() * 5);
    const storage = 38; // 38% eMMC usado (estable)

    if (subCpuVal) subCpuVal.textContent = `${cpu}%`;
    if (subRamVal) subRamVal.textContent = `${ram}%`;
    if (subStorageVal) subStorageVal.textContent = `${storage}%`;

    if (subCpuFill) subCpuFill.style.width = `${cpu}%`;
    if (subRamFill) subRamFill.style.width = `${ram}%`;
  }, 2400);

  // Clic en el monitor abre JH Monitor
  if (vehicleHwBar) {
    vehicleHwBar.addEventListener('click', () => {
      showToast("Abriendo JH Monitor (com.jh.monitor) · Diagnóstico completo");
    });
  }

  // --------------------------------------------------------------------------
  // 7. CONTROLES DE LA BARRA DE PRUEBA EN ESCRITORIO
  // --------------------------------------------------------------------------
  const btnToggleFull = document.getElementById('btnToggleFull');
  if (btnToggleFull) {
    btnToggleFull.addEventListener('click', () => {
      document.body.classList.toggle('fullscreen-mode');
      btnToggleFull.textContent = document.body.classList.contains('fullscreen-mode') 
        ? "❐ Modo Ventana 1024x600" 
        : "⛶ Pantalla Completa";
    });
  }

});
