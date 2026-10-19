# VOYAGER

Descargador personal y ligero de **YouTube** y **Facebook** para Android (11 a 17). Sin anuncios, sin cuentas, sin seguimiento. Solo para tu uso personal (no se publica ni se distribuye).

Interfaz de la app en inglés. Este README y mis explicaciones, en español.

## Versión 3.2

- **Descarga y reproducción de Facebook por captura directa:** al tocar el botón, la app toma las direcciones del video y del audio que el propio reproductor de Facebook está pidiendo (como las extensiones de descarga), sin depender de cómo Facebook arme su página. Si el video estaba pausado, la app lo reproduce un momento para que el reproductor las pida.
- Con esas direcciones se hace la lista de formatos (video, M4A, MP3) y la reproducción con el reproductor de YouTube. Los cuadros de la lista de reels de un canal siguen leyendo la página.

## Versión 3.1

- **"Loading formats" y el reproductor de Facebook ya no se quedan cargando:** si la página ya tiene el archivo del video (lo normal en reels), la app lo usa directo, sin descargar la página de Facebook (casi instantáneo). Si no, lee la página con límites de tiempo (como máximo unos 30 s) y avisa si Facebook no responde.
- **Actualización automática de YouTube:** cada lunes GitHub revisa si salió una versión nueva del extractor de YouTube; si salió, compila sola y publica un Release "VOYAGER auto" en la pestaña Releases. Solo tienes que instalar ese APK encima.
- Antes de subir cambios tuyos, usa `git pull` (por si el robot actualizó algo).

## Versión 3.0

- **Facebook, video en negro:** la web de Facebook se dibuja con aceleración normal (por software el video no se ve, solo la portada).
- **Botones:** descargar (rojo) y reproducir (oscuro) en cada video y reel; botón pequeño de descarga en cada cuadro de la lista de reels de un canal.
- **Reproducir con calidad:** el botón de reproducir abre el mismo reproductor de YouTube con selector de calidad, pantalla completa y +/-10 s.
- **Modo ligero:** los videos no arrancan solos en el inicio y se apagan las animaciones de la página.
- **Datos de usuario y caché:** Facebook guarda su almacenamiento interno en el teléfono; ahora se borra al salir (el inicio de sesión se conserva) y el caché se vacía si pasa de 40 MB.
- **Inicio de sesión:** automático (modos cada 8 s, sin cambiar si tocas la pantalla) y siempre termina en la web de celular.

## Versión 2.18

- Facebook login: orden de modos 1 normal, 2 móvil por software (el que muestra el formulario moderno), 3 página simple, 4 escritorio (último recurso). Espera 10 s por modo y deja de cambiar solo en cuanto tocas la pantalla. Botón "Mode" (junto a la flecha) para cambiar a mano.
- Tras iniciar sesión en cualquier modo, siempre continúa en la web móvil (m.facebook.com), nunca en la de escritorio.
- El caché del navegador ya no se borra al salir (cargaba todo desde cero cada vez); solo si pasa de 60 MB.

## Versión 2.17

- Facebook: si hay una sesión guardada pero la página sale vacía, ahora se detecta mirando la imagen real (no el texto de la página) y se reinicia el inicio de sesión como en una instalación nueva. Con sesión guardada la página se dibuja por software. Una línea pequeña abajo muestra el estado (modo, sesión, página vacía sí/no).

## Versión 2.16

- Facebook: se restaura el orden de modos de la 2.10, con la que sí se pudo iniciar sesión (las primeras páginas dejan las cookies que la página simple necesita). Se deja de borrar cookies automáticamente. La primera vez puede tardar hasta 25 segundos en mostrar el formulario. Mantener presionada la flecha de atrás borra el inicio de sesión y empieza de nuevo.

## Versión 2.15

- Facebook: ya no se fía de la cookie guardada. Siempre abre la página simple (que sí se dibuja en este teléfono): si muestra el login, escribes tus datos ahí; si muestra tu cuenta, abre la web completa con los botones de descarga. Si la web completa manda al login (sesión inválida), borra la sesión y vuelve a la página simple.

## Versión 2.14

- Facebook: si la sesión guardada está caducada y la página sale vacía, la app la borra sola (una vez) y empieza el login desde la página simple.

## Versión 2.13

- Facebook: el login empieza en la página simple (la que funciona en este teléfono); si la sesión guardada caducó, vuelve a ese login en vez de quedarse en blanco. Tras entrar, la web normal.
- La página termina en el borde de la barra de navegación del teléfono.
- Al salir se borran el caché y los datos web de Facebook (no la sesión), para que "datos de usuario" no crezca.

## Versión 2.12

- Carpetas: `Voyager/Voyager video`, `Voyager/Voyager music` y `Voyager/Voyager photo`. Lo descargado antes queda donde estaba.
- Facebook sin conexión ahora dice "No connection" en vez de pedir iniciar sesión.

## Versión 2.11

- **Facebook a pantalla completa:** sin barra superior, sin textos ni barra inferior; solo una flecha redonda arriba a la izquierda para salir. La pantalla no se apaga mientras estés en esa página.
- **Botón de descarga:** en la página de un reel o video hay un solo botón a la derecha (lejos del aviso "Abrir aplicación") que baja el video que se ve en pantalla, nunca la foto de portada. En el inicio, cada video tiene su botón y las fotos normales el suyo. Después de cada toque se ignoran los toques durante 2,5 segundos, y una foto no se guarda dos veces.
- **Velocidad:** la lista de formatos y el arranque de la descarga de Facebook ya no leen la página dos veces; las dos versiones de la página se piden a la vez y el resultado se guarda 10 minutos.
- **Caché:** al salir de Facebook, si el caché del teléfono pasa de 20 MB se vacía el caché web (la sesión se conserva).

## Versión 2.10

- **Facebook, pantalla en blanco:** el WebView del teléfono (versión 154) es moderno; el problema era mi identidad personalizada del navegador y un detector que contaba texto escondido. Ahora usa la identidad normal del WebView y detecta si hay un campo de inicio de sesión realmente visible. Si no lo hay a los 9 segundos, prueba el siguiente modo: dibujo por software, página de escritorio, página simple. El botón "Mode" (arriba) cambia de modo a mano. La línea de estado muestra el modo, la versión de WebView y el último error de la página.

## Versión 2.9

- **Facebook, pantalla en blanco al iniciar sesión:** si la página se queda en blanco, la app prueba otras 3 páginas de acceso de Facebook (simple, gratuita y táctil, y la de escritorio) una tras otra. La línea de estado muestra la versión del WebView del teléfono. Si ninguna se dibuja, aparece el botón "Update WebView in Play Store": en teléfonos con WebView antiguo es la única forma de que Facebook se vea dentro de una app.

## Versión 2.8

- **Facebook, calidades reales:** el archivo "HD" que entrega Facebook está limitado a 720p. Las calidades altas (1080p, 1440p, etc.) solo vienen en sus streams DASH (video y audio por separado). Ahora la lista muestra todas esas calidades con su peso estimado y las descarga mezclando video y audio. Si la página no trae esos streams, queda la lista anterior (SD y HD).
- El título ya no incluye el "31K views · 334 reactions |" del inicio.

## Versión 2.7

- **Facebook:** el acceso empieza en la página simple (HTML sin scripts modernos), que funciona en cualquier WebView, y después pasa a la versión completa. Una línea de estado bajo el título muestra "Loading %", o el motivo exacto si algo falla.
- **Pantalla completa:** el video se amplía para llenar toda la pantalla (recorta lo que sobra, sin franjas negras) y también cubre la zona de la cámara frontal.
- **Reproductor:** botones 15% más pequeños; en lugar de anterior/siguiente hay retroceder y adelantar 10 segundos.
- **Continuar:** el punto guardado se borra solo cuando terminas el video (o llegas a sus últimos 10 segundos); mientras lo ves por partes se conserva.

## Versión 2.6

- **Calidad de la vista previa de YouTube:** botón arriba a la derecha del video (por ejemplo "360p") para elegir cualquier calidad disponible; cambia sin perder el punto donde vas.
- **Pantalla completa:** botón del reproductor (también muestra el selector de calidad).
- **Continuar donde lo dejaste:** en videos de más de 10 minutos que no terminaste, se guarda el punto cada pocos segundos y, al abrirlos otra vez, continúan desde ahí. Al terminarlos se borra el punto. Se recuerdan los últimos 60.

## Versión 2.5

- **Facebook:** al iniciar sesión se abre la interfaz normal de Facebook (inicio, páginas, canales, búsqueda) con un botón rojo de descarga sobre cada video, reel y foto. Los videos pasan por la lista de formatos; las fotos se guardan en la carpeta Voyager. Si la pantalla se queda en blanco, a los 12 segundos cambia sola a la página de acceso ligera.
- **Pantalla:** durante la vista previa de YouTube en primer plano no se apaga; en segundo plano sí se apaga con el tiempo normal del teléfono.
- **Reproductor:** botones 40% más grandes que en la 2.4.

## Versión 2.4

- La vista previa de YouTube ya no reinicia la carga al salir de la app y volver: antes, al volver, la app reconfiguraba el reproductor y este descartaba lo ya cargado.

## Versión 1.2.2: abrir archivos en tu reproductor

- Al tocar un archivo descargado, VOYAGER lo abre en tu reproductor como una app aparte (su propia ventana en recientes), no dentro de VOYAGER.
- El reproductor recibe la dirección permanente del archivo (la misma que usan las galerías y los reproductores de música), en vez de un permiso temporal. Antes, al salir del reproductor el permiso se cancelaba mientras el reproductor seguía activo, y al terminar la canción intentaba reabrir el archivo sin permiso, fallaba y reintentaba sin parar, lo que calentaba y laggeaba el teléfono.
- Nota: cortar la reproducción y borrar la notificación al salir con la flecha es una decisión del reproductor, no de VOYAGER.

## Versión 1.2.1: correcciones urgentes

- **Notificación de reproducción:** ahora sí aparece la notificación estándar de Android al reproducir una vista previa de YouTube en segundo plano. Antes la sesión de reproducción no se registraba y Android no la mostraba.
- **Sin notificación de descarga:** el canal visible de la versión 1.0.0 seguía instalado en el teléfono (Android no deja cambiar un canal ya creado). Ahora se borra y se usa uno nuevo y bloqueado. Además la notificación interna ya no se redibuja varias veces por segundo, que era lo que más cargaba al sistema.
- **Teléfono lento y caliente:** el mezclado de video y la conversión a MP3 corren con prioridad baja para no competir con la pantalla y los botones del sistema. Se agregaron guardas contra descargas o conversiones que se queden "pegadas" y contra un indicador de carga que no terminaba.
- **Reproductor:** botones exactamente un 20% más grandes.

## Versión 1.2.0: lo nuevo

- **Facebook con tu cuenta:** el ícono de persona (arriba, junto a la lupa) abre el inicio de sesión de Facebook dentro de la app. Es la página normal de Facebook: VOYAGER no ve tu contraseña, la sesión queda guardada solo en el teléfono y se usa únicamente para abrir videos que tu cuenta ya puede ver (privados, de amigos, de grupos). Si intentas descargar un video privado sin sesión, la app te lo pide sola. Se cierra con "Log out". El enlace al video solo se envía a facebook.com y a nadie más.
- **Notificación:** solo la de reproducción de la vista previa de YouTube en segundo plano (la notificación multimedia estándar de Android). Las descargas siguen sin mostrar nada. En Android 13 o más la app pide el permiso de notificaciones una sola vez, la primera vez que reproduces una vista previa.
- **Descargas:** el límite de 2 aplica solo a las que están DESCARGANDO. Las que se están procesando (mezclando o convirtiendo) no tienen límite y no bloquean la fila.
- **Reproductor:** botones más pequeños, controles más discretos y menos capa oscura sobre el video.
- **Portada:** el mensaje vacío está centrado; los links copiados desaparecen a los 10 minutos y solo se detectan links de YouTube y Facebook.
- **Archivos enormes:** no hay ningún límite de tamaño. Videos de 6, 10 o más GB funcionan si el teléfono tiene espacio libre: se necesita aproximadamente el doble del tamaño del video mientras se mezcla.

## Versión 1.1.x: qué cambió

- **Carpeta propia:** todo se guarda en `Voyager` (almacenamiento interno, raíz). La primera vez la app muestra una pantalla pidiendo "Acceso a todos los archivos"; es el único permiso que existe en Android para crear una carpeta en la raíz. Los archivos parciales van a `Voyager/.tmp` (oculta), así que los datos de la app ya no crecen.
- **Sin notificaciones de descarga:** Android obliga a tener un servicio para que las descargas sigan con la pantalla apagada, pero ahora usa un canal bloqueado y no muestra nada. Solo queda la notificación multimedia cuando reproduces una vista previa.
- **Calidades de video corregidas:** los videos panorámicos (cine) aparecían como 536p, 804p, 1072p. Ahora se etiquetan como YouTube (360p, 480p, 720p, 1080p, 1440p, 2160p).
- **Portada y datos en el audio:** MP3 y M4A llevan portada, título, artista (el canal) y álbum (el canal).
- **Más rápido:** video y audio se descargan a la vez, se mueve el archivo sin copiarlo, la extracción de YouTube se reutiliza, Facebook deja de leer la página en cuanto encuentra el video.
- **Sugerencias de búsqueda** mientras escribes.
- **Listas y cursos:** muestra el peso total de la selección (exacto hasta 6 videos, estimado si son más).
- **Descargas:** 2 a la vez, el resto en espera. Al procesar ya no se puede pausar (así no se repite el trabajo). Botones más pequeños y mejor ubicados; "Pause all" arriba. Los audios muestran el disco de vinilo desde que empiezan.
- **Portada limpia:** arriba el logo de DIGITALMINDS y la lupa; la barra de búsqueda aparece al tocar la lupa. Todo el espacio de abajo es la lista de links copiados de YouTube y Facebook (hasta 30, con su botón Download y una X para quitarlos).
- **Ícono** con más negro, conservando los difuminados.

Las descargas de la versión 1.0.0 siguen apareciendo en Play (están en Música/VOYAGER y Películas/VOYAGER). Se instala encima sin perder nada.

## 1. Descomprimir y abrir en VS Code

1. Crea una carpeta nueva, por ejemplo `C:\Proyectos\VOYAGER`.
2. Extrae aquí el contenido de `VOYAGER-project.zip`. Debes ver directamente las carpetas `app`, `gradle`, `.github` y los archivos `settings.gradle.kts`, `README.md`, etc. (no una carpeta dentro de otra).
3. Abre VS Code, menú **File > Open Folder** y elige esa carpeta.
4. Abre la terminal de VS Code: menú **Terminal > New Terminal**.

## 2. Crear el repositorio en GitHub (una sola vez)

1. Entra a github.com e inicia sesión. Arriba a la derecha pulsa **+ > New repository**.
2. Nombre: `voyager`. Marca **Private** (IMPORTANTE, mira la nota de seguridad abajo). No marques README ni .gitignore. Pulsa **Create repository**.
3. Copia la dirección que te muestra, algo como `https://github.com/TU_USUARIO/voyager.git`.

## 3. Primer envío (push)

En la terminal de VS Code, uno por uno:

```
git init
git add .
git commit -m "VOYAGER v1.0.0"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/voyager.git
git push -u origin main
```

(Cambia `TU_USUARIO` por tu usuario real. Si Git te pide iniciar sesión, hazlo en la ventana que se abre.)

Al terminar el push, GitHub empieza a compilar solo. Tarda entre 6 y 15 minutos la primera vez.

## 4. Descargar el APK

**Opción A (cada push):** en tu repo, pestaña **Actions**, entra al último "Build VOYAGER" con check verde, baja hasta **Artifacts** y descarga `VOYAGER-apk` (es un .zip con el .apk dentro).

**Opción B (versión oficial con tag):**

```
git tag v3.2
git push origin v3.2
```

El APK aparece en la pestaña **Releases** del repo, ya listo para bajar.

## 5. Instalar en el teléfono

1. Pasa el .apk al teléfono (abre GitHub desde el navegador del teléfono y bájalo, o por cable).
2. Ábrelo. Si Android lo bloquea, pulsa **Ajustes** y activa "Permitir desde esta fuente" para tu navegador o administrador de archivos.
3. Instala. Como todas las versiones van firmadas con la misma clave, cada versión nueva se instala encima de la anterior sin perder nada.
4. La primera vez acepta el permiso de notificaciones (la app funciona igual si lo niegas, pero no verás el progreso en la barra).

## 6. Si el build falla

1. En GitHub, pestaña **Actions**, abre el run con X roja.
2. Abre el paso **Build release APK** y copia las **últimas 60 líneas** (busca las líneas con `error:` o `Could not resolve`).
3. Pégamelas aquí en el chat y te doy el arreglo exacto.

Causa más probable: una versión de librería. Todas están en un solo archivo, `gradle/libs.versions.toml`. Las que NO pude comprobar desde mi entorno (AGP, Compose BOM, lifecycle, activity, coil, okhttp, desugar) vienen de mi conocimiento. Las que sí verifiqué en GitHub: NewPipeExtractor v0.26.5, Media3 1.5.1, Kotlin 2.0.21, coroutines 1.9.0.

## 7. Cuando YouTube deje de funcionar

YouTube cambia seguido y la librería de extracción se actualiza para seguirlo.

1. Mira la última versión en https://github.com/TeamNewPipe/NewPipeExtractor/releases
2. Abre `gradle/libs.versions.toml` y cambia la línea `newpipe = "v0.26.5"` por la nueva (con la `v`).
3. Terminal: `git add .`, `git commit -m "Actualizar extractor"`, `git push`. Luego saca una versión nueva (`git tag v3.2`, `git push origin v3.2`) y reinstala.

## 8. Cambiar el ícono o el splash

- Ícono: reemplaza `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` (432 x 432 px, logo con fondo transparente, dentro del 66% central). El fondo gris es `ic_launcher_background` en `values/colors.xml`.
- Splash: el logo es `drawable-nodpi/voyager_logo.png` y el de DIGITALMINDS `dm_logo.png`. Duración mínima: constante `SPLASH_MIN_MS` en `app/src/main/java/com/personal/voyager/ui/AppRoot.kt`.

## 9. Qué hace la app

- **Download:** buscar (Todo, Canales, Listas), pegar un enlace de YouTube o Facebook, tarjeta del portapapeles, detalle con vista previa ligera (360p) y reproducción en segundo plano con la notificación multimedia de Android.
- **Calidades de video:** todas las que el video realmente tiene, de 360p hasta su máximo y nunca más. Hasta 1080p usa H.264 en MP4. Para 1440p y 2160p usa VP9 en WebM; si tu teléfono no puede mezclarlo, cae solo a H.264 en 1080p o menos.
- **Audio:** M4A 128K (sin recodificar) y MP3 128K, 160K y 320K (el MP3 320K se recodifica desde el AAC de YouTube, es normal).
- **Listas (cursos):** marcas videos, eliges un formato una vez y se descargan en orden con prefijo `01 - `. Máximo 200 por lista.
- **Play:** cola de descargas (máx. 2 a la vez, pausa y reanudación, sobrevive si se cierra la app), lista de descargados con Abrir, Compartir y Eliminar. Los archivos van a `Música/VOYAGER` y `Películas/VOYAGER`.
- **Facebook:** solo videos públicos, sin iniciar sesión. Si Facebook cambia su página, esa parte (archivo `fb/Facebook.kt`) es la que habría que ajustar.

## 10. Lo que no pude verificar (sé transparente conmigo si algo falla)

- La app no pudo compilarse completa en mi entorno (no tengo acceso a Maven, Google ni JitPack). Sí compilé aparte las partes de Android puro (mezcla de video, MP3, guardado) y probé el codificador MP3 con audio real. Las pantallas (Compose) y la unión con NewPipeExtractor se revisaron leyendo el código fuente real de cada librería, pero no se ejecutaron.
- El MP3 se codifica con un port de LAME en Java puro (rápido en PC; en tu A03 Core puede tardar cerca de 1x la duración de la canción). Calidad configurable en `dl/Mp3.kt` (constante `QUALITY`).
- Facebook y el mezclado WebM de 1440p/2160p son lo más frágil.
- No se recortó ninguna función del prompt original.

## 11. Nota de seguridad

`voyager-release.jks` y `keystore.properties` van incluidos para que no tengas que configurar nada. Eso es aceptable **solo mientras el repositorio sea privado**. Si algún día lo haces público, crea otra clave y bórrala del historial.

## 12. Licencias

NewPipeExtractor es GPL-3.0 y LAME (port Java) es LGPL: por eso la app es solo para uso personal y no debe distribuirse. Space Grotesk usa licencia OFL. Los textos están en `licenses/`.
