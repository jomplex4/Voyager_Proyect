# VOYAGER: contexto del proyecto

## 1. Resumen
Descargador personal de YouTube y Facebook para Android, alternativa sin anuncios a SnapTube. Autor: Redy Mamani (marca DIGITALMINDS). Uso privado, nunca se publica. Versión 4.6. En uso en un Samsung Galaxy A03 Core (2 GB de RAM, Android 13), internet lento en muchos momentos. YouTube funciona (calidades hasta 1080p y audio). Facebook funciona: iniciar sesión, botón de descarga en videos y reels, descarga con sonido (confirmado por Redy tras la 4.2).

## 2. Tecnología
- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, Media3 1.5.1, OkHttp 4.12, Coil 2.7, androidx.webkit 1.12.1, LAME en Java (vendorizado).
- Extractor de YouTube: NewPipeExtractor por JitPack, versión fijada en `gradle/libs.versions.toml` (clave `newpipe`). Ahora un commit de la rama dev (`6faf40681e`, 26-oct-2026).
- minSdk 30, target/compile 35, AGP 8.7.3, Gradle 8.9, JDK 17. R8 activo (`app/proguard-rules.pro`).
- Compila GitHub Actions (`.github/workflows/build.yml`). Un tag `vX.Y` crea un Release con `VOYAGER-vX.Y.apk`. Firma con `voyager-release.jks` + `keystore.properties` (repo PRIVADO). Cada lunes el flujo revisa si salió una versión nueva del extractor y, si sí, compila y publica "VOYAGER auto".
- `versionName` está en `app/build.gradle.kts`; `versionCode = 100 + número de ejecución de GitHub`.

## 3. Estructura (`app/src/main/java/com/personal/voyager/`)
- `yt/`: YtRepo (búsqueda, canales, listas, caché de StreamInfo 10 min, errores), Formats (calidades, audio, etiqueta tipo YouTube), YtReport (escribe `Voyager/youtube-report.txt`).
- `fb/`: Facebook (lectura de página con cookie, último recurso), FbSession (cookies del WebView), MediaSniffer (archivos de video/audio que pide el reproductor de Facebook), MediaProbe (sondeo de archivos, sin uso normal; escribe `Voyager/facebook-report.txt`), Photo.
- `dl/`: Downloads (cola), Engine (descarga por rangos con reanudación), Resolver (elige qué bajar), Mux (une audio y video, remux, validación), Mp3 (LAME), Tags, Cover, Storage (carpetas), Store (registro JSON), DownloadService, Bg.
- `player/`: PlayerHub (ExoPlayer único), PlaybackService (notificación del reproductor).
- `ui/`: AppRoot, DownloadScreens (inicio, resultados, canal, lista, detalle y reproductor de vista previa), FacebookScreen (WebView, script de botones, modos de inicio de sesión), PlayScreens, FormatSheet, Components, Theme. `FbPlayScreen.kt` queda vacío a propósito.
- `vm/AppViewModel.kt`, `core/` (Prefs, Net, Util, NewPipeDownloader).

## 4. Funciones actuales
- YouTube: búsqueda con sugerencias, canales, listas, vista previa con engranaje (calidad, velocidad, Original/Llenar pantalla), ±10 s, pantalla completa, continuar videos de más de 10 min, las pantallas conservan su posición al volver, títulos en español.
- Descarga: MP3 128/160/320 y M4A con portada y etiquetas, video hasta la calidad máxima, máximo 2 descargas a la vez, pausa y reanudar sin reiniciar. Carpetas `Voyager/Voyager video`, `Voyager/Voyager music`, `Voyager/Voyager photo`. Notificación de descarga silenciosa; notificación del reproductor al salir de la app.
- Facebook: pantalla completa con flecha, botón rojo en videos, reels y cada cuadro de reels, fotos a `Voyager photo`, enlaces copiados también.

## 5. Bugs resueltos (no repetir)
- Archivos con el mismo nombre en distintas versiones (PNG y WEBP): duplicados al descomprimir encima. No renombrar ni borrar archivos entre versiones.
- Un import faltante rompe el build: revisar imports antes de entregar.
- Facebook en blanco al iniciar sesión: solo funcionó el orden de modos `m` normal, `m` por software, `www` escritorio, `mbasic` por software, 8 s entre modos, sin borrar cookies (las primeras páginas dejan cookies que `mbasic` necesita). Después de iniciar sesión siempre se va a `m.facebook.com`.
- Video negro en Facebook: la capa de software del WebView no muestra video; la web con sesión usa capa de hardware.
- Facebook entrega AV1/VP9 si el WebView lo declara: `NO_NEW_CODECS` (script al inicio de la página) lo oculta para obtener H.264.
- Facebook sin sonido: el audio empieza con tiempo negativo; `Mux.mergeVideoAudio` lo tomaba como fin del archivo. El fin es `sampleTrackIndex < 0` y los tiempos negativos se llevan a 0.
- Facebook mismo video varias veces: el elegido se determina por el número del video (`xpv_asset_id` o `video_id` dentro de `efg`), no por conteo de peticiones.
- Facebook solo una descarga visible: todos tenían el mismo nombre y se tomaban como duplicados; `fbName` agrega fecha y hora.
- Reproductor en segundo plano sin notificación: se pedía el permiso una sola vez; ahora se pide hasta 3 veces y luego se abre la pantalla de notificaciones de la app.
- Reanudar descarga: la barra volvía a 0; ahora cuenta lo ya descargado. El motor reanuda por tamaño del archivo parcial.
- YouTube solo 360p con velocidad limitada: ver sección 8.

## 6. Pendientes y límites conocidos
- Algunos videos de YouTube solo entregan el formato de 360p "todo en uno" al extractor (caso "KULLAWADA RAIMONDI": sin formatos adaptativos, sin audio separado, sin HLS). Con la versión vieja salía solo 360p; con el commit nuevo puede salir un error de "no disponible". Probable causa: restricciones de YouTube para los clientes que usa NewPipe. SnapTube sí los lee porque usa otro método. No hay solución en la app hasta que el extractor lo soporte.
- No se soportan formatos DASH/OTF ni grabaciones de directos (`Formats.progressive` solo acepta `PROGRESSIVE_HTTP`).
- Facebook depende de su página: puede cambiar y romper los botones o la lectura.

## 7. Decisiones importantes
- Acceso a todos los archivos (carpeta `Voyager` en el almacenamiento); parciales en `Voyager/.tmp`.
- Sin FFmpeg para mantener la app ligera: `MediaMuxer` + LAME en Java.
- El inicio de sesión de Facebook solo por WebView; la contraseña nunca pasa por la app.
- Se eliminó el reproductor propio de Facebook a pedido de Redy; solo descarga.
- Interfaz de la app en inglés; README, comandos y este archivo en español.

## 8. Guía de actualización (YouTube y Facebook cambian; leer completo)
### Qué debe adjuntar Redy al pedir una actualización
1. `VOYAGER-project.zip` más reciente y este `CONTEXTO.md`.
2. Según el problema, los informes de la carpeta `Voyager` del teléfono (se suben tal cual, sin datos personales): `youtube-report.txt` (qué formatos entregó YouTube y los errores reales) y/o `facebook-report.txt` (qué archivos vio Facebook y por qué falló una descarga).
3. Una captura del error y, si falla la compilación, las líneas que empiezan con `e:` o `ERROR:` del paso "Build release APK" en la pestaña Actions de GitHub.
4. El nombre o enlace del video que falla.
### YouTube se queda atrás (solo 360p, errores, descargas lentas de 10 a 50 KB/s)
1. Leer `youtube-report.txt`: `videoOnly: none` y `audio: none` indican que el extractor no recibió formatos adaptativos. Una línea `ERROR` trae el mensaje real de YouTube.
2. Ver qué hay nuevo: `https://github.com/TeamNewPipe/NewPipeExtractor/releases.atom` (versiones) y `https://github.com/TeamNewPipe/NewPipeExtractor/commits/dev.atom` (cambios con "[YouTube]" posteriores al commit fijado).
3. Comprobar compatibilidad antes de subir: descargar desde `raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/dev/extractor/src/main/java/...` y verificar que existen las 39 clases que el proyecto importa y estas funciones: `YoutubeParsingHelper.getVisionOsUserAgent`, `isVisionOsStreamingUrl`, `isWebStreamingUrl`, y `NewPipe.init(Downloader, Localization, ContentCountry)`.
4. Cambiar `newpipe` en `gradle/libs.versions.toml` (tag nuevo o los primeros 10 caracteres de un commit). Subir `versionName`.
5. La primera compilación con un commit nuevo puede fallar porque JitPack debe construirlo: en Actions usar "Re-run failed jobs" (el flujo ya reintenta 6 veces).
6. Retroceso seguro: poner `newpipe = "v0.26.5"`. Esa versión funciona con la mayoría de videos.
7. Si el extractor no tiene arreglo, la app no puede hacerlo; no inventar soluciones sin poder probarlas.
### Facebook cambia (sin botones, lista vacía, descarga sin audio, no inicia sesión)
1. Leer `facebook-report.txt`. Cada bloque lista los archivos vistos con `kind` (0 video, 1 audio, 2 completo), `h` (alto), `asset` y el `tag` decodificado de `efg` (`vencode_tag`: `dash_h264-basic-gen2_720p`, `dash_ln_heaac_vbr3_audio`). Si el `tag` ya no trae esas palabras, ajustar `MediaSniffer.parse` (clasificación) y `codecOf`.
2. Botones y datos de la página: constante `INJECT` en `ui/FacebookScreen.kt` (JavaScript). Los campos que busca para el MP4 completo: `browser_native_hd_url`, `browser_native_sd_url`, `playable_url`, `playable_url_quality_hd`, `hd_src`, `sd_src`.
3. Inicio de sesión: lista `MODES` en el mismo archivo; ver la regla de la sección 5 antes de tocarla.
4. Unión de audio y video: `Mux.mergeVideoAudio`; validación: `Mux.validateVideo`.
5. Formato AV1/VP9: `NO_NEW_CODECS` en `FacebookScreen.kt`.
### Cómo debe trabajar Claude con este proyecto
- Responder en español, sin guiones largos, con pasos claros. Redy no es programador.
- Entregar siempre el ZIP completo (se descomprime encima de la carpeta), subir `versionName`, anotar en `README.md` y en este archivo.
- Comandos de git en un solo bloque, un comando por línea: `git pull`, `git add .`, `git commit -m "VOYAGER X.Y"`, `git push`, `git tag vX.Y`, `git push origin vX.Y`. Cada tag nuevo debe ser distinto.
- No renombrar ni borrar archivos existentes; no cambiar lo que funciona; no agregar funciones no pedidas.
- Antes de decir que algo funciona, decir con honestidad que no se pudo probar en el teléfono. Revisar llaves e imports antes de entregar.
- Pedir solo datos que Redy tenga a mano (capturas, informes de la carpeta `Voyager`, líneas de error de GitHub).
