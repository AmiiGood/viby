# Viby

Reproductor de música para Android, sin anuncios y con estética cuidada. Pensado para
escuchar tu biblioteca local (por ejemplo, la música descargada con
[y1-music-downloader](https://github.com/AmiiGood/y1-music-downloader)) y, además,
descubrir y descargar canciones nuevas desde la propia app.

> Proyecto personal. Uso sideload (no Play Store).

## Qué hace

- **Reproductor** con cola, aleatorio real, repetir, búsqueda y favoritos.
- **Pantalla "Reproduciendo"** a pantalla completa, arrastrable (sube/baja siguiendo el dedo),
  con carátula grande y fondo desenfocado.
- **Navegación por pestañas**: Canciones · Álbumes · Artistas · Favoritos.
- **Notificación y pantalla bloqueada** con carátula (Media3 / ExoPlayer).
- **Ecualizador** integrado.
- **Reanuda** donde te quedaste y recuerda aleatorio/repetir.
- **Descargas** integradas (NewPipeExtractor): busca, escucha un preview, descarga a
  `Artista/Álbum/Título.m4a` con tags ID3 + carátula (enriquecido con MusicBrainz),
  en segundo plano con notificación de progreso y sin duplicados.
- **Descubrir**: recomendaciones de música nueva basadas en tu biblioteca.
- **Radio**: miles de emisoras de internet vía [Radio Browser](https://www.radio-browser.info/)
  (catálogo abierto, sin cuenta ni API key). Busca por nombre, filtra por género,
  guarda favoritas y reconecta sola cuando el stream se corta.
- **Widget de inicio**: carátula, título, artista y controles anterior/pausa/siguiente,
  con el color tomado de la portada que suena.

## Stack

Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · Coil · Glance (widget) ·
NewPipeExtractor · jaudiotagger · MusicBrainz · Radio Browser · minSdk 26 · targetSdk 36.

## Compilar

1. Abre el proyecto en Android Studio y deja que sincronice Gradle.
2. Conecta un dispositivo (o usa un emulador) y dale a **Run**.
3. En la app, elige la carpeta donde está tu música (selector del sistema).

## Licencia

Uso personal.
