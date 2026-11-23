# Baby Monitor (Android Mobile & Wear OS)

Aplicación bidireccional de monitor de bebé para teléfonos Android y relojes inteligentes con Wear OS.

Permite alternar qué dispositivo se queda en la habitación del bebé y cuál lleva el padre o la madre, con detección en tiempo real, transmisión de audio/vídeo, controles remotos (linterna, cambio de cámara) y funcionamiento persistente en segundo plano.

---

## 📱 Dos Modos de Funcionamiento Bidireccional

### 1. Reloj en la habitación del bebé y Móvil con el padre
- **Reloj (Transmisor en cuna):**
  - Utiliza el micrófono en segundo plano para muestrear el audio ambiental a 16 kHz.
  - Calcula el nivel de presión sonora en decibelios (dB SPL / RMS).
  - Si el sonido supera el umbral configurado (ej. 65 dB durante un llanto sostenido), envía inmediatamente una alarma háptica/sonora al móvil y abre el canal de transmisión de audio en directo.
  - Sigue funcionando con la pantalla apagada gracias a un `ForegroundService` y `PARTIAL_WAKE_LOCK`.
  - Botón de **Salir** en pantalla y en la notificación para apagar el servicio por completo.
- **Móvil (Receptor del padre):**
  - Muestra una notificación emergente de alta prioridad con sonido y vibración cuando el bebé llora.
  - Botón **"Escuchar en Directo"**: Permite abrir el micrófono del reloj y escuchar la habitación en cualquier momento sin esperar a que salte la alarma.
  - Gráfico y medidor de decibelios en tiempo real.

---

### 2. Móvil en la habitación del bebé y Reloj con el padre
- **Móvil (Transmisor en cuna):**
  - **Detección de Sonido:** Micrófono activo con umbral de decibelios configurable.
  - **Detección de Movimiento:** Cámara (CameraX) analizando variaciones de luminancia en el plano Y entre fotogramas consecutivos.
  - **Opciones flexibles:** Se puede activar *Solo Sonido*, *Solo Movimiento* o *Ambos a la vez*.
  - **Modo Pantalla Negra (OLED):** Permite apagar los píxeles de la pantalla del móvil para no iluminar el dormitorio del bebé ni calentar el teléfono, manteniendo el servicio activo.
  - **Transmisión de Vídeo:** Envía fotogramas MJPEG optimizados a 320x320 px (8–10 fps) diseñados específicamente para pantallas circulares de reloj sin saturar el Bluetooth ni agotar la batería.
  - **Control Remoto:** Responde a órdenes del reloj para encender/apagar la linterna (flash LED) o alternar entre cámara frontal y trasera.
- **Reloj (Receptor del padre en la muñeca):**
  - Vibración háptica en la muñeca ante alertas de llanto o movimiento.
  - **Visor de Vídeo en Directo:** Muestra la imagen de la cuna recortada para la pantalla circular del reloj.
  - **Escucha de Audio en Directo:** Reproduce el sonido de la habitación a través del altavoz del reloj.
  - **Botón Linterna:** Enciende y apaga a distancia el flash del móvil para ver en la oscuridad sin entrar a la habitación.
  - **Botón Cambiar Cámara:** Alterna entre cámara frontal y trasera del móvil desde la muñeca.
  - Botón **Salir** para finalizar la sesión en ambos dispositivos simultáneamente.

---

## 🛠️ Arquitectura Técnica

```
Baby_Monitor/
├── core/                       # Módulo común (modelos, protocolo y algoritmos)
│   ├── detection/
│   │   ├── AudioLevelDetector.kt # Cálculo de RMS, dB y filtro antirruido
│   │   └── MotionDetector.kt     # Algoritmo de diferencia de fotogramas YUV
│   ├── model/Models.kt         # Estados, roles y modos de monitorización
│   └── protocol/ProtocolConstants.kt # Canales y rutas de mensajes
│
├── mobile/                     # Aplicación Android para el teléfono
│   ├── audio/                  # Grabación (AudioRecord) y reproducción (AudioTrack)
│   ├── camera/                 # CameraX ImageAnalysis + streaming MJPEG
│   ├── communication/          # Cliente Google Play Services Wearable Data Layer
│   ├── service/                # BabyMonitorMobileService (Foreground Service)
│   └── ui/                     # Pantallas Jetpack Compose (Material 3)
│
└── wear/                       # Aplicación para el reloj inteligente (Wear OS)
    ├── audio/                  # Grabación y reproducción en el reloj
    ├── communication/          # Conexión directa con el móvil por Wearable API
    ├── service/                # BabyMonitorWearService (Foreground Service)
    └── ui/                     # Interfaz táctil circular en Compose for Wear OS
```

### Protocolo de Comunicación
Utiliza la **Wearable Data Layer API de Google Play Services**:
- **MessageClient (`/cmd/*` y `/alert/*`):** Para envío instantáneo de alertas de sonido/movimiento, cambio de linterna y cámara.
- **ChannelClient (`/channel/audio_stream` y `/channel/video_stream`):** Canales bidireccionales de bajo nivel para transmitir los streams continuos de audio PCM a 16 kHz y vídeo MJPEG tanto por Bluetooth como por Wi-Fi de forma transparente y sin necesidad de configurar direcciones IP ni emparejamientos manuales.

---

## 🚀 Cómo Abrir y Compilar el Proyecto

### Requisitos Previos
1. **Android Studio** (Hedgehog, Iguana, Jellyfish o posterior).
2. **JDK 17** (incluido por defecto en Android Studio).
3. **Dispositivos:**
   - Un teléfono Android (Android 8.0 Oreo / API 26 o superior).
   - Un reloj con Wear OS (Wear OS 2 / Android 9 API 28 o superior; recomendado Wear OS 3/4/5).

### Pasos para Ejecutar
1. Abre Android Studio y selecciona **Open** -> Selecciona la carpeta `d:\OneDrive\Documents\Baby_Monitor`.
2. Deja que Gradle sincronice las dependencias automáticamente (`Sync Project with Gradle Files`).
3. Para instalar en el **Teléfono**:
   - Conecta el móvil por USB con la depuración USB activada.
   - En el desplegable de configuración superior de Android Studio, selecciona `:mobile` y tu teléfono.
   - Pulsa **Run** (botón verde de Play).
4. Para instalar en el **Reloj (Wear OS)**:
   - Activa en el reloj: *Ajustes -> Opciones de desarrollador -> Depuración por Wi-Fi* (o Depuración por Bluetooth).
   - Empareja el reloj con tu ordenador usando `adb connect <IP_DEL_RELOJ>:5555` o mediante el gestor de dispositivos Wear de Android Studio (*Device Manager -> Physical -> Pair using Wi-Fi*).
   - En el desplegable de Android Studio selecciona `:wear` y tu reloj.
   - Pulsa **Run**.

---

## 🔋 Optimizaciones de Batería y Permisos

Para garantizar que el monitor no sea cerrado por el sistema operativo Android cuando la pantalla se apague:
1. **Exención de optimización de batería:** La aplicación solicita automáticamente excluirse de la optimización agresiva de batería (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
2. **Foreground Services tipificados (Android 14+):** Se declaran los tipos oficiales `microphone`, `camera`, `connectedDevice` y `mediaPlayback`.
3. **Modo Noche OLED:** Si el móvil se queda en la habitación, activa el botón de pantalla negra para apagar los píxeles de la pantalla AMOLED mientras la cámara y el micro continúan vigilando.
