# FocusTo

Aplicación Android adaptativa de estudio con temporizador Pomodoro, que ajusta automáticamente su comportamiento (modo nocturno, alertas de salud/postura, bloqueo por distracción o agitación) en función de datos leídos en tiempo real de los sensores del dispositivo.

Proyecto desarrollado para el **Taller 1 — Desarrollo de una Aplicación Adaptativa**.

## Equipo
- Rosse Morales
- Yadira Cantorín Lopez
- Jose Diaz

## ¿Qué hace la app?
FocusTo acompaña una sesión de estudio con un temporizador Pomodoro (25 min de estudio / 5 min de descanso) y un visor de PDF con zoom. Mientras el Pomodoro está activo, la app **observa el contexto físico del usuario en tiempo real** (luz ambiental, movimiento, proximidad, inclinación del teléfono) y **adapta automáticamente su comportamiento** sin que el usuario tenga que configurar nada manualmente.

## Comportamiento adaptativo

Pipeline obligatorio implementado: **CONTEXTO → PROCESAMIENTO → DECISIÓN → ADAPTACIÓN**

| Etapa | Archivo |
|---|---|
| Captura del contexto | `SensorService.kt` |
| Procesamiento | `ContextManager.kt` |
| Decisión | `AdaptationEngine.kt` |
| Adaptación | `MainActivity.kt` / `FocusService.kt` |

Ejemplos observables (se pueden probar en vivo):
- **Tapar el sensor de luz** varios segundos → se activa el modo nocturno (colores oscuros + PDF invertido) automáticamente.
- **Agitar el celular** con fuerza → bloqueo de pantalla ("¡Concéntrate!") y pausa del Pomodoro.
- **Poner el celular boca abajo** mientras el Pomodoro corre (sin PDF abierto) → se activa el "Modo Estudio Físico" con vibración.
- **Acercar mucho el celular a la cara** → alerta de salud visual.
- **Sostener el celular con mala postura** (inclinación baja) → alerta de postura.
- **Dejar el celular boca arriba sin moverlo** → pausa automática de la sesión.
- **Abrir una app de la lista negra** (TikTok, Instagram, YouTube, etc.) durante una sesión → la app relanza FocusTo con un aviso de bloqueo.

## Requisitos

- Android Studio reciente (probado con Android Studio 2026.1)
- **JDK 17** para el Gradle del proyecto (AGP 8.2.0 no funciona con versiones más nuevas de Java). Si Android Studio no lo tiene, se descarga solo desde `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM → Download JDK...` (elegir versión 17)
- Android SDK: `compileSdk 34`, `minSdk 24`
- Se recomienda **dispositivo físico** para probar la adaptación por sensores: muchos emuladores no simulan bien el sensor de luz ni el de proximidad.

## Cómo ejecutar la aplicación

1. Abrir la carpeta del proyecto en Android Studio (`File → Open`).
2. Esperar a que termine el **Gradle Sync** (la primera vez puede tardar más porque descarga el JDK 17 y las dependencias).
3. Conectar un dispositivo físico (o usar un emulador) y ejecutar la app (▶).
4. Al presionar **"INICIAR"** por primera vez, la app va a pedir dos permisos que Android no deja otorgar automáticamente:
   - **Acceso a datos de uso** (Usage Access) — necesario para detectar apps distractoras en primer plano.
   - **Mostrar sobre otras apps** — necesario para el bloqueo visual.

   Hay que activarlos manualmente en Ajustes y volver a la app.
5. Para probar la adaptación por sensores: tapar el sensor de luz, agitar el celular, o ponerlo boca abajo/boca arriba durante una sesión activa (ver ejemplos en la sección anterior).

## Dependencias principales

Definidas en [`gradle/libs.versions.toml`](gradle/libs.versions.toml) y [`app/build.gradle.kts`](app/build.gradle.kts):

- Kotlin 1.9.0 / Android Gradle Plugin 8.2.0
- AndroidX: `core-ktx`, `appcompat`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-ktx`
- Kotlin Coroutines (para el renderizado de PDF en segundo plano)
- JUnit + Espresso (testing)

## Estructura del código relevante

```
app/src/main/java/com/example/focusto/
├── MainActivity.kt      → UI y orquestación de la adaptación (Pomodoro, PDF, overlays, alertas)
├── FocusViewModel.kt    → estado de UI (sobrevive a la rotación de pantalla)
├── FocusService.kt      → temporizador Pomodoro en segundo plano + bloqueo de apps distractoras
├── SensorService.kt     → captura del contexto (sensores de luz, acelerómetro, proximidad)
├── ContextManager.kt    → procesamiento del contexto (normalización + confirmación temporal)
└── AdaptationEngine.kt  → decisión (reglas de adaptación puras, sin dependencias de Android)
```

## Tests

```bash
./gradlew testDebugUnitTest
```

O desde Android Studio: click derecho sobre `FocusViewModelTest.kt` → **Run 'FocusViewModelTest'**.
