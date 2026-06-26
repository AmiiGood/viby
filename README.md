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
jaudiotagger · MusicBrainz · Picovoice Porcupine · minSdk 26.

## Compilar

1. Abre el proyecto en Android Studio y deja que sincronice Gradle.
2. Conecta un dispositivo (o usa un emulador) y dale a **Run**.
3. En la app, elige la carpeta donde está tu música (selector del sistema).

### Asistente de voz (opcional)

Para activar el "Hey Viby" necesitas una cuenta gratuita de
[Picovoice](https://console.picovoice.ai):

1. Copia tu **AccessKey** y pégala en la pantalla *Asistente* de la app.
2. Crea la palabra clave **"Viby"** (English, Android), descarga el `.ppn`,
   renómbralo a `Viby.ppn` y colócalo en `app/src/main/assets/`.
3. Recompila y activa el interruptor.

## Licencia

Uso personal.
