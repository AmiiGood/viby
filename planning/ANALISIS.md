# Viby — Análisis de mejora

> Documento para decidir qué atacar y qué dejar. Estado a la fecha de la última sesión.

## 1. Estado actual

Lo que funciona bien y estable:
- Reproducción (cola, aleatorio real, repetir, notificación/lockscreen con carátula).
- Now Playing arrastrable, favoritos, pestañas (Canciones/Álbumes/Artistas/Favoritos), búsqueda.
- Ecualizador, reanudar reproducción, recordar aleatorio/repetir.
- Descargas integradas (busca, preview, descarga a Artista/Álbum/Título.m4a con tags + carátula,
  en segundo plano con notificación, sin duplicados).
- Recomendaciones ("Descubrir").
- Asistente de voz "Hey Viby" (funcional pero flojo).

## 2. Hallazgo principal: estás probando una build DEBUG

Todas las pruebas han sido con **build debug** (botón Run / assembleDebug). Una build **debug** de
Jetpack Compose es **bastante más lenta** que una **release**: no pasa por R8 (ofuscación/optimización),
es `debuggable`, y no usa *Baseline Profiles*. **Spotify y otros con los que comparas son builds release.**

➡️ Buena parte del "va lageado" probablemente desaparece compilando en **release**. Es el experimento
de mayor impacto y menor esfuerzo, y debería ser el primer paso antes de optimizar más código.

## 3. Problemas y soluciones

### P1 — Fluidez de la lista (scroll) · Severidad: Media
- **Causa:** (a) build debug; (b) la primera vez se generan las miniaturas (trabajo único);
  (c) algo de recomposición.
- **Soluciones:**
  - **Compilar en release (R8)** — impacto alto, esfuerzo bajo. *(probar primero)*
  - **Baseline Profile** — impacto alto en scroll/arranque, esfuerzo medio.
  - Pre-generar todas las miniaturas en el primer escaneo con barra de progreso — impacto medio.
  - Medir con el profiler de Android Studio para ver el frame exacto que cae.
- **Recomendación:** probar release; si aún no convence, Baseline Profile + pre-generación.

### P2 — Asistente de voz: no siempre escucha / se activa solo · Severidad: Media
- **Causa raíz:** un wake word casero en un teléfono tiene límites reales: no hay cancelación de
  eco para multimedia, y el reconocimiento del comando usa el motor de Google (a veces falla/depende
  de internet o del paquete de voz). La gramática de Vosk fuerza clasificar ruido como la palabra.
- **Soluciones:**
  - **Push-to-talk** (botón de micrófono): muy confiable, deja el manos libres como secundario — esfuerzo bajo.
  - **Capturar el comando con Vosk** en vez de Google (sin traspaso de micro ni dependencia de Google) — esfuerzo medio, menos preciso para nombres de canciones.
  - **Calibración**: modo debug que muestre qué "oye" Vosk para ajustar las variantes de "Viby" a tu voz — esfuerzo bajo.
  - Dejarlo como está / quitarlo — esfuerzo nulo.
- **Recomendación honesta:** el manos-libres perfecto no es alcanzable sin más inversión. Si lo quieres
  usable de verdad, **push-to-talk**. Si te gusta el manos libres aunque imperfecto, déjalo como está.

### P3 — Descargas "Sin audio descargable" en algunos videos · Severidad: Media
- **Causa:** restricción de YouTube en ese video (edad/región o verificación anti-bot / poToken).
  No es bug de Viby; otras canciones sí descargan. NewPipeExtractor ya está en su última versión.
- **Soluciones:**
  - Buscar otra versión de la canción (ya funciona) — esfuerzo nulo.
  - **Auto-reintentar con el siguiente resultado** de la búsqueda si el primero falla — esfuerzo medio.
  - **Fallback a formato video+audio** cuando no hay audio puro — esfuerzo medio, pero ensucia la
    biblioteca (archivo .mp4 más pesado, sin tags/carátula).
  - Mantener NewPipeExtractor actualizado (YouTube cambia seguido).
- **Recomendación:** auto-reintento con el siguiente resultado (mejor relación valor/esfuerzo) o aceptar.

### P4 — Metadatos de descargas (álbum/carátula) · Severidad: Baja
- **Causa:** MusicBrainz a veces devuelve un álbum distinto; la carátula sale de la miniatura (no
  siempre cuadrada perfecta).
- **Soluciones:** mejorar el matching de MusicBrainz; usar Cover Art Archive para portadas reales.
- **Recomendación:** baja prioridad; solo si te molesta.

### P5 — Mantenimiento (YouTube cambia) · Severidad: Baja-continua
- **Causa:** las descargas dependen de NewPipeExtractor, que se rompe con cambios de YouTube.
- **Solución:** actualizar la versión cada tanto.
- **Recomendación:** revisar cada pocos meses.

## 4. Roadmap pendiente (features que faltan)

- Playlists propias.
- Temporizador de apagado (sleep timer).
- Ordenar la biblioteca (nombre, fecha, duración).
- Reordenar la cola arrastrando.

## 5. Orden sugerido

1. **Compilar en release** y volver a juzgar la fluidez (gratis, gran impacto).
2. Decidir el rumbo del **asistente** (push-to-talk vs dejarlo) — es la parte más floja.
3. Descargas: **auto-reintento** con el siguiente resultado.
4. Si la fluidez sigue corta: **Baseline Profile** + pre-generación de miniaturas.
5. Features del roadmap según lo que más uses (playlists / sleep timer).
