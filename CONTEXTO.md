# VOYAGER: contexto del proyecto

## 1. Resumen
Descargador personal de YouTube y Facebook para Android (alternativa sin anuncios a SnapTube) de Redy Mamani, marca DIGITALMINDS, uso privado (no Play Store). Versión 2.12, en uso diario en un Samsung Galaxy A03 Core (2 GB RAM, Android 11 Go).

## 2. Tecnología
- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, Media3 1.5.1 (ExoPlayer + MediaSession), OkHttp 4.12, Coil 2.7, NewPipeExtractor v0.26.5 (JitPack), LAME en Java (vendorizado, LGPL).
- minSdk 30 (Android 11), target/compile 35. AGP 8.7.3, Gradle 8.9, desugaring.
- Compila GitHub Actions (`.github/workflows/build.yml`); un tag `vX.Y` crea un Release con el APK. Firma con `voyager-release.jks` + `keystore.properties` (repo PRIVADO). Versiones en `gradle/libs.versions.toml`.

## 3. Estructura (`app/src/main/java/com/personal/voyager/`)
- `yt/` YtRepo (búsqueda, canales, listas, caché de StreamInfo 10 min, sugerencias), Formats (calidades; etiqueta tipo YouTube para videos panorámicos).
- `fb/` Facebook (scraping con cookie, DASH, caché 10 min), FbSession (cookies del WebView), Photo.
- `dl/` Downloads (cola), Engine (descarga por rangos), Resolver, Mux (MediaMuxer), Mp3 (LAME), Tags (ID3/MP4), Cover, Storage (carpetas), Store (registro JSON), DownloadService (servicio silencioso), Bg (prioridad baja).
- `player/` PlayerHub (ExoPlayer único, calidad, posición), PlaybackService (notificación multimedia).
- `ui/` Splash, AppRoot (navegación, pantalla completa), DownloadScreens (inicio, búsqueda, detalle), PlayScreens, FormatSheet, FacebookScreen (WebView + JS), Components.
- `vm/AppViewModel.kt`, `core/` (Prefs, Util, Net, NewPipeDownloader). `res/values/exo_overrides.xml` = tamaño de controles.

## 4. Funciones actuales
- Splash como COMET con logo DIGITALMINDS; inicio con logo, lupa, ícono de Facebook y links copiados (YT/FB, expiran a 10 min).
- Búsqueda con sugerencias; canales; listas con peso total; vista previa con calidad elegible, pantalla completa con zoom (cubre la cámara), ±10 s, pantalla encendida, continuar videos >10 min.
- Descarga MP3 128/160/320 y M4A con portada y etiquetas; video 360p hasta el máximo (H.264 ≤1080p, VP9 arriba). Facebook SD/HD o DASH; reels y fotos desde la web de Facebook con sesión.
- Máx. 2 DESCARGANDO a la vez, procesado sin límite. Carpetas `Voyager/Voyager video|music|photo`.

## 5. Historial
- 1.0 base; 1.1 carpeta Voyager, sin notificación de descarga, portadas; 1.2 login FB, cola 2; 2.4 sin recarga al volver; 2.6 calidad y reanudar; 2.8 DASH FB; 2.10 modos de login FB; 2.11 FB pantalla completa; 2.12 subcarpetas.

## 6. Bugs resueltos (no repetir)
- PNG y WEBP con el mismo nombre: duplicados al descomprimir encima. Nunca renombrar/borrar archivos entre versiones.
- Import faltante rompió el build: auditar imports antes de entregar.
- Calidades 536p/804p: alto real en videos panorámicos; usar `Formats.labelHeight`.
- Notificación de descarga visible: canal viejo persistente; canal nuevo bloqueado y sin actualizaciones por segundo.
- Sin notificación de reproducción: faltaba `addSession()` en PlaybackService.
- Teléfono lageado: el reproductor externo reintentaba un URI con permiso temporal; se pasa URI de MediaStore y `NEW_TASK`.
- Recarga del video al volver: no tocar pistas en ON_STOP/ON_START.
- FB en blanco: UA personalizado y detector por texto; UA por defecto + detección de campo visible + modos.
- FB bajaba la portada del reel: un solo botón fijo en páginas de video.

## 7. Pendientes
- Selector de calidad para VER videos de Facebook (no hecho).
- Nada se compila ni ejecuta localmente; cada cambio se valida con el build de GitHub y pruebas de Redy.

## 8. Decisiones
- "Acceso a todos los archivos" para la carpeta raíz Voyager; parciales en `Voyager/.tmp`.
- Sin FFmpeg (peso); MediaMuxer + LAME Java. App ligera (~6 MB).
- Login de Facebook solo por WebView (la contraseña nunca pasa por la app).

## 9. Mantenimiento (YouTube/Facebook cambian)
- YouTube falla: subir `newpipe` en `gradle/libs.versions.toml` a la última versión de github.com/TeamNewPipe/NewPipeExtractor/releases.
- Facebook falla al leer videos: ajustar llaves/regex en `fb/Facebook.kt` (`hdKeys`, `sdKeys`, `dash_manifest`); botones de la web: script `INJECT` en `ui/FacebookScreen.kt`.

## 10. Instrucciones para Claude
- Responder en español, sin guiones largos (—). Usuario no experto: pasos claros.
- Entregar SIEMPRE el ZIP completo del proyecto (se descomprime encima), subir `versionName`/`versionCode` y anotar en README.
- Comandos de git en un solo bloque, un comando por línea (add, commit, push, tag, push tag).
- No renombrar ni borrar archivos existentes; no cambiar ni agregar nada que no se pida; honestidad sobre lo no probado.
- Si el build falla, pedir solo las líneas `e:` o `ERROR:`.
