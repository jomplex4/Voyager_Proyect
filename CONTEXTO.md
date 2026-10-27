# VOYAGER: contexto del proyecto

## 1. Resumen
Descargador personal de YouTube y Facebook para Android (alternativa sin anuncios a SnapTube) de Redy Mamani, marca DIGITALMINDS, uso privado (no Play Store). Versión 3.5, en uso diario en un Samsung Galaxy A03 Core (2 GB RAM, Android 11 Go).

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
- 1.0 base; 1.1 carpeta Voyager, sin notificación de descarga, portadas; 1.2 login FB, cola 2; 2.4 sin recarga al volver; 2.6 calidad y reanudar; 2.8 DASH FB; 2.10 modos de login FB; 2.11 FB pantalla completa; 2.12 subcarpetas; 2.13 login FB por página simple y sesión caducada.

## 6. Bugs resueltos (no repetir)
- PNG y WEBP con el mismo nombre: duplicados al descomprimir encima. Nunca renombrar/borrar archivos entre versiones.
- Import faltante rompió el build: auditar imports antes de entregar.
- Calidades 536p/804p: alto real en videos panorámicos; usar `Formats.labelHeight`.
- Notificación de descarga visible: canal viejo persistente; canal nuevo bloqueado y sin actualizaciones por segundo.
- Sin notificación de reproducción: faltaba `addSession()` en PlaybackService.
- Teléfono lageado: el reproductor externo reintentaba un URI con permiso temporal; se pasa URI de MediaStore y `NEW_TASK`.
- Recarga del video al volver: no tocar pistas en ON_STOP/ON_START.
- FB en blanco: UA personalizado y detector por texto; UA por defecto + detección de campo visible + modos.
- FB LOGIN (2.16, LEER): solo funcionó el orden m(hardware), m(software), www(escritorio), mbasic(software) con 7 s entre modos, SIN borrar cookies: las primeras páginas dejan cookies que mbasic necesita. No volver a saltar directo a mbasic ni a llamar FbSession.logout() automáticamente (rompió el login en 2.13-2.15). Escape manual: mantener presionada la flecha.
- 3.5 (capturas 27-oct): archivos FB ilegibles = AV1/VP9 (el WebView 154 los anuncia y Facebook los envía). `NO_NEW_CODECS` (document-start con androidx.webkit + onPageStarted) hace que `MediaSource.isTypeSupported/canPlayType/mediaCapabilities` rechacen av01/vp9/hevc; `MediaSniffer` prefiere H.264, marca el códec en la lista y busca el audio por cercanía de tiempo; `Mux.validateVideo` comprueba con `MediaCodecList.findDecoderForFormat`; `Downloads.frameThumb` crea portada.
- 3.5 (auditoría): `friendly()` ya muestra los mensajes propios de IOException; `-keepclassmembers @JavascriptInterface` en proguard; `clearHistory()` tras abrir m.facebook.com; ventana de ajustes oculta barras en pantalla completa; efg se decodifica con base64 normalizado.
- 3.5: MediaSniffer reescrito: cada archivo trae el id del video en `efg` (`xpv_asset_id`); `pick(id)` elige por el id del reel (antes por conteo: bajaba el mismo reel 3 veces) y lista todas las calidades (URLs vistas en peticiones + datos de la página recogidos por el script con hooks de fetch/XHR y scripts, enviados como 6.º parámetro de `Voyager.video`). Fuente "fbsniff": líneas `v\tindice\talto\tmuxed\turl` y `a\turl`; choice `F:Q<indice>`. `Mux.validateVideo` comprueba el MP4 de Facebook. Reanudar: progreso inicial desde el parcial. Engranaje = Calidad/Velocidad/Pantalla.
- 3.4: pantallas guardan su estado en el ViewModel (`vm.keepOf`, se borra al hacer pop) para no recargar al volver; calidad/velocidad en un diálogo abierto por el engranaje (`exo_settings`, `vm.settingsOpen`); NewPipe en es/PE; FB: nombres únicos (`fbName`, antes duplicados por título igual), `Plan.Direct(remux=true)` + `Mux.remuxToMp4`, hoja de formatos inmediata.
- FB 3.3: reproductor de Facebook ELIMINADO a pedido de Redy (solo descarga). `FbPlayScreen.kt` queda vacío a propósito (no se borran archivos). YouTube: pantalla completa = proporción original; `vm.fillScreen` (botón Fill/Original) = zoom que llena la pantalla.
- FB 3.2: la lectura de la página falla (Facebook usa blob/MSE, `currentSrc` vacío). Solución: `fb/MediaSniffer.kt` observa los .mp4 de fbcdn (shouldInterceptRequest + `performance.getEntriesByType('resource')` del script), quita bytestart/byteend y elige el video/audio más pedido y reciente. Fuentes de descarga: "fbsniff" (url = video\naudio), "fbsrc" (archivo directo), "fb" (lectura de página, último recurso). Si deja de funcionar, ver qué .mp4 pide el reproductor (ajustar la clasificación por el parámetro `efg`).
- FB 3.1: "Loading formats"/reproductor sin fin = lectura de página sin límite (Facebook deja la conexión abierta). Ahora: el script pasa `video.currentSrc` (archivo real) y se usa directo (source "fbsrc", choice "F:SRC"); si no hay, lectura con callTimeout 25 s, 15 s por página y tope total 30 s, versión de escritorio en www.
- FB 3.0: video negro = capa de software en WebView; la web logueada usa capa de HARDWARE (no leer píxeles). Datos de usuario crecían por WebStorage de Facebook: se borra al salir. Botones play/descarga/mosaico en el script INJECT. Reproducir FB: Screen.FbPlay + PlayerHub.loadFb (DASH o SD/HD).
- FB 2.18 (capturas de Redy): el formulario aparecía en el modo 2 (m.facebook.com, software) pero se saltaba al 3 (escritorio) a los 7 s mientras escribía, y tras entrar se quedaba en la web de escritorio. Ahora: 10 s por modo, no cambia si se toca la pantalla, y tras login siempre va a m.facebook.com. Botón Mode manual.
- FB 2.17: con cookie guardada y página en blanco (cookie vieja, scheduleCheck no hacía nada) se detecta por píxeles (`looksBlank`) y se hace resetLogin(). Línea de estado abajo.
- (Descartado, 2.15) FB en blanco con cookie vieja: no se confía en la cookie; siempre se abre mbasic.facebook.com (software) y es la página la que decide login/cuenta; la web completa (m.facebook.com) solo tras verlo en mbasic. Si la web completa manda a /login, se borra la sesión.
- FB en blanco con sesión caducada: la app creía estar conectada; si Facebook manda a /login se reinicia el login simple.
- FB bajaba la portada del reel: un solo botón fijo en páginas de video.

## 7. Pendientes
- YouTube: si al abrir un video se pausa solo, probable estrangulamiento de YouTube (subir `newpipe`, ver sección 9); ajustar buffers en `PlayerHub.get`.
- Facebook: no se pudo comprobar en un teléfono que los archivos descargados se reproduzcan; si no, revisar `Mux.validateVideo` y el mensaje que muestre.
- Facebook cambia su página seguido: si fallan botones o login, ajustar INJECT y MODES en FacebookScreen.kt.
- Nada se compila ni ejecuta localmente; cada cambio se valida con el build de GitHub y pruebas de Redy.

## 8. Decisiones
- "Acceso a todos los archivos" para la carpeta raíz Voyager; parciales en `Voyager/.tmp`.
- Sin FFmpeg (peso); MediaMuxer + LAME Java. App ligera (~6 MB).
- Login de Facebook solo por WebView (la contraseña nunca pasa por la app).

## 9. Mantenimiento (YouTube/Facebook cambian)
- AUTOMÁTICO: cada lunes Actions compara NewPipeExtractor con la última versión; si cambió, edita el toml, compila y publica el Release "auto-N". versionCode = 100 + número de ejecución (siempre crece). Hacer `git pull` antes de subir cambios.
- YouTube falla: subir `newpipe` en `gradle/libs.versions.toml` a la última versión de github.com/TeamNewPipe/NewPipeExtractor/releases.
- Facebook falla al leer videos: ajustar llaves/regex en `fb/Facebook.kt` (`hdKeys`, `sdKeys`, `dash_manifest`); botones de la web: script `INJECT` en `ui/FacebookScreen.kt`.

## 10. Instrucciones para Claude
- Responder en español, sin guiones largos (—). Usuario no experto: pasos claros.
- Entregar SIEMPRE el ZIP completo del proyecto (se descomprime encima), subir `versionName`/`versionCode` y anotar en README.
- Comandos de git en un solo bloque, un comando por línea (add, commit, push, tag, push tag).
- No renombrar ni borrar archivos existentes; no cambiar ni agregar nada que no se pida; honestidad sobre lo no probado.
- Si el build falla, pedir solo las líneas `e:` o `ERROR:`.
