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
- **Asistente de voz "Hey Viby"** (opcional): wake word + comandos por voz + respuesta hablada.

## Stack

Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · Coil · NewPipeExtractor ·
jaudiotagger · MusicBrainz · Vosk (wake word offline) · minSdk 26.

## Compilar

1. Abre el proyecto en Android Studio y deja que sincronice Gradle.
2. Conecta un dispositivo (o usa un emulador) y dale a **Run**.
3. En la app, elige la carpeta donde está tu música (selector del sistema).

### Asistente de voz (opcional)

El "Hey Viby" usa **Vosk** (offline, sin cuenta ni keys). En la pantalla *Asistente*
de la app, activa el interruptor: la primera vez descarga un modelo de voz en español
(~40 MB) y pide permiso de micrófono. Luego di "Viby" y, tras el beep, tu orden.

## Licencia

Uso personal.
