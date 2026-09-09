# 🎧 Reproductor de Audio

Reproductor de música para Android **centrado en archivos locales**, con descarga de audio integrada y diseño oscuro moderno (grises acero + naranja).

- Escanea **toda tu música local** (almacenamiento interno + carpetas que tú elijas).
- **Descarga audio** desde más de 1000 sitios (YouTube, SoundCloud, TikTok, Instagram…) en MP3, WAV, FLAC, M4A u Opus.
- **Reproducción en segundo plano**, sesión multimedia y controles desde auriculares/Bluetooth.

> ⚠️ Descarga solo contenido que tengas permiso de descargar y respeta los términos de servicio de cada sitio.

---

## ✨ Características

### Reproductor
- Play / pause, siguiente, anterior, **adelantar y retroceder 10 s**.
- Barra de progreso con **tiempo transcurrido y restante**.
- **Bucle** (repetir todo / una / ninguna) y **shuffle**.
- **Adelantar / retroceder 10 s** (con el número "10" visible en los botones).
- Botón de **letras** (añade o consulta la letra de cada canción) y acceso rápido a la **cola de reproducción**.
- **Swipe** lateral sobre la carátula para cambiar de canción; **swipe hacia abajo** para cerrar; toca el mini-reproductor para expandir/colapsar la vista.
- Notificación persistente con play/pause/next/prev y soporte de botones físicos de auriculares/Bluetooth (MediaSession).

### Biblioteca
- **Mi música**: todas las canciones del dispositivo.
- **Favoritos**, **Recientes** y **Playlists** (con nombre e imagen editables).
- **Buscador** de canciones, listas y artistas.
- **Ordenar** por nombre, fecha, tamaño o duración (ascendente / descendente).
- Icono de **carpeta** junto a "Mi música" para añadir carpetas del móvil como fuente de música.
- **Añadir a playlist** desde cualquier canción (lista las creadas o crea una nueva) y **quitar canciones** de una playlist.
- **Reordenar canciones** arrastrando el icono de la playlist.
- **Mini-reproductor** con barra de progreso dentro de la playlist; toca una canción para abrir el reproductor completo o su icono para pausar/reanudar.
- La canción **en reproducción se resalta** en naranja (barra lateral + título).

### Descarga
- Icono de descarga (flechita) que abre un **diálogo** con: barra de enlace, selector de formato, botón de descarga y cancelar.
- Progreso en vivo con opción de **cancelar** (X), y aviso de **completado** con palomita.
- Descargas guardadas en una **ubicación configurable** (por defecto `Música/Reproductor`, no en `Descargas`). Todas las ubicaciones usadas (aunque cambies varias veces) **siempre aparecen en el reproductor**.

---

## 📱 Cómo funciona

- **Escaneo local**: `MediaStore` para el almacenamiento interno + `DocumentFile` para las carpetas seleccionadas (SAF).
- **Descarga**: [yt-dlp](https://github.com/yt-dlp/yt-dlp) + [ffmpeg](https://ffmpeg.org/) empaquetados en el APK mediante [youtubedl-android](https://github.com/JunkFood02/youtubedl-android).
- **Reproducción**: [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3), que soporta todos los formatos y gestiona sesión multimedia y segundo plano.

```
[Enlace] ─► yt-dlp ─► ffmpeg ─► audio (mp3/wav/flac/m4a/opus) ─► carpeta elegida
[Almacenamiento + carpetas] ─► MediaStore / SAF ─► biblioteca ─► ExoPlayer
```

---

## 📥 Instalación

1. Descarga `ReproductorAudio-v1.0.apk` (ver Releases) o compílalo.
2. En Android, permite **"Instalar apps de fuentes desconocidas"**.
3. Abre el APK y concede los permisos de **acceso a música** (y notificaciones).

**Requisitos:** Android 10 (API 29) o superior. El APK incluye las 4 arquitecturas (armeabi-v7a, arm64-v8a, x86, x86_64), por eso pesa ~200 MB.

---

## 🛠️ Compilar

### Requisitos
- JDK 17
- Android SDK (compileSdk 34)
- Gradle (vía `gradlew`)

### Debug
```bash
cd android
./gradlew assembleDebug
# Salida: android/app/build/outputs/apk/debug/app-debug.apk
```

### Release firmado
Crea `android/keystore.properties`:

```properties
storeFile=keystore/release.jks
storePassword=TU_CONTRASEÑA
keyAlias=TU_ALIAS
keyPassword=TU_CONTRASEÑA
```

```bash
cd android
./gradlew assembleRelease
# Salida: android/app/build/outputs/apk/release/app-release.apk
```

> 🔒 El `keystore.properties` y `keystore/` están en `.gitignore`. Guarda tu keystore en un lugar seguro.

---

## 🗂️ Estructura

```
android/app/src/main/java/com/example/audioplayer/
├── MainActivity.kt        # Pantalla principal (buscador, pestañas, biblioteca, mini-reproductor)
├── PlaylistActivity.kt    # Detalle de una playlist
├── PlayerActivity.kt      # Reproductor a pantalla completa
├── DownloadDialog.kt      # Diálogo de descarga (formato + progreso + cancelación)
├── PlaybackService.kt     # Servicio Media3 (segundo plano + notificación)
├── PlayerManager.kt       # Controlador de reproducción (cola, repeat, shuffle)
├── Downloader.kt          # Lógica de descarga (yt-dlp)
├── LocalMusic.kt          # Escaneo de música local (MediaStore + SAF)
├── MusicStore.kt          # Persistencia: favoritos, recientes, playlists, carpetas
├── MediaSaver.kt          # Guardado de descargas (MediaStore / carpeta personalizada)
├── Artwork.kt             # Carga de carátulas
├── SongAdapter.kt         # Lista de canciones
└── PlaylistAdapter.kt     # Lista de playlists
```

## ⚠️ Aviso legal

Esta herramienta está pensada solo para descargar contenido que tengas derecho a descargar. No la uses para infringir derechos de autor ni violar los términos de servicio de los sitios.

## 📄 Licencia

Distribuido bajo la licencia [MIT](LICENSE).

## 🙏 Agradecimientos

- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [youtubedl-android](https://github.com/JunkFood02/youtubedl-android)
- [ffmpeg](https://ffmpeg.org/)
- [AndroidX Media3](https://developer.android.com/media/media3)
