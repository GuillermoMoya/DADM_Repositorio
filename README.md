# Tic-Tac-Toe — Desarrollo de Aplicaciones para Dispositivos Móviles

Repositorio del proyecto de **Tic-Tac-Toe para Android**, desarrollado como parte del curso **Desarrollo de Aplicaciones para Dispositivos Móviles** de la Universidad Nacional de Colombia.

El proyecto se ha construido de manera incremental a través de diferentes retos, pasando de la definición inicial y el prototipado a una aplicación Android funcional con lógica de juego, interfaz gráfica personalizada, sonidos, menús, persistencia de información y manejo del estado de la aplicación.

---

## 📱 Descripción del proyecto

La aplicación implementa el juego clásico **Tic-Tac-Toe (tres en línea)** entre un jugador humano y la computadora.

La lógica principal del juego está separada de la interfaz para facilitar la evolución del proyecto. El tablero utiliza nueve posiciones y los jugadores están representados mediante:

- `X` — jugador humano.
- `O` — computadora.
- Espacio — posición disponible.

La aplicación permite jugar partidas completas, seleccionar diferentes niveles de dificultad, reiniciar partidas y conservar información relevante entre ejecuciones y cambios de configuración.

---

## 🎯 Objetivos

- Desarrollar una aplicación móvil Android funcional.
- Aplicar separación entre lógica del juego e interfaz de usuario.
- Construir progresivamente una interfaz gráfica personalizada.
- Implementar interacción táctil con el tablero.
- Incorporar menús, diálogos y selección de dificultad.
- Incorporar recursos gráficos y efectos de sonido.
- Manejar correctamente cambios de orientación y recreación de la actividad.
- Persistir configuraciones y estadísticas.
- Mantener el estado de una partida durante la recreación de la actividad.
- Aplicar pruebas unitarias e instrumentadas para validar el comportamiento.

---

# 📚 Retos realizados

## Reto 1 — Planteamiento inicial del proyecto

El primer reto corresponde a la etapa inicial de definición del proyecto y sirve como punto de partida para el trabajo posterior.

> **Nota:** el material del Reto 1 no se encuentra entre los documentos disponibles actualmente en este repositorio/documentación de referencia, por lo que esta sección se mantiene deliberadamente general.

Esta etapa permitió establecer la idea y el alcance inicial sobre los cuales posteriormente se desarrolló la aplicación.

---

## Reto 2 — Modelo Canvas y prototipo

**Objetivo:** construir el Modelo Canvas y el primer prototipo de la aplicación.

En este reto se trabajó en:

- Modelo de negocio mediante **Business Model Canvas**.
- Identificación de los principales elementos de la idea de aplicación.
- Elaboración de **wireframes, mockups y flujo de pantallas**.
- Definición visual inicial de la aplicación.
- Carga del Canvas y los Mockups en el repositorio como documentos PDF.

Este reto permitió pasar de la idea inicial a una representación visual de la aplicación antes de implementar la interfaz Android.

---

## Reto 3 — Aplicación Tic-Tac-Toe para Android

**Objetivo:** convertir la implementación del juego en una aplicación Android.

La aplicación parte de una lógica de Tic-Tac-Toe y la integra con una interfaz Android.

### Funcionalidades principales

- Tablero de 3 × 3.
- Jugador humano representado por `X`.
- Computadora representada por `O`.
- Detección de victoria, empate y continuación de la partida.
- Movimiento de la computadora.
- Mensajes para indicar el turno y el resultado.
- Separación entre lógica del juego y lógica de interfaz.
- Opción para comenzar una nueva partida.
- Uso de recursos de texto mediante `strings.xml`.

La clase `TicTacToeGame` concentra la lógica del juego, mientras que la actividad se encarga de la interacción con la interfaz.

También se incorporó el control de fin de partida para impedir movimientos después de terminar el juego.

---

## Reto 4 — Menús y cuadros de diálogo

**Objetivo:** mejorar la interacción de la aplicación mediante menús y diálogos.

Se incorporaron:

- Menú de opciones.
- Opción para iniciar una **Nueva partida**.
- Selección del nivel de dificultad.
- Cuadros de diálogo para las opciones de configuración.
- Niveles de dificultad:
  - **Easy**
  - **Harder**
  - **Expert**

La lógica de selección de movimientos de la computadora se amplió de acuerdo con el nivel de dificultad:

- **Easy:** movimientos aleatorios.
- **Harder:** intenta ganar y, si no puede, realiza un movimiento aleatorio.
- **Expert:** intenta ganar, bloquear al jugador y finalmente realizar un movimiento disponible.

En la evolución posterior del proyecto, la opción **Quit** fue eliminada del menú y el menú actual se concentra en:

- New Game
- Difficulty
- Reset Scores

---

## Reto 5 — Gráficos y sonido

**Objetivo:** reemplazar el tablero construido con botones por una vista gráfica personalizada e incorporar efectos de sonido.

### `BoardView`

Se desarrolló una vista personalizada basada en `android.view.View`.

La clase:

- Dibuja el tablero directamente sobre un `Canvas`.
- Representa el tablero como una cuadrícula 3 × 3.
- Calcula las dimensiones de las celdas a partir del tamaño actual de la vista.
- Dibuja las marcas `X` y `O` mediante recursos gráficos.
- Se conecta con `TicTacToeGame`.
- Permite detectar la posición tocada por el usuario.
- Rechaza interacciones inválidas.

### Sonido

Se incorporaron efectos de sonido mediante `MediaPlayer`:

- Sonido para el movimiento del jugador.
- Sonido para el movimiento de la computadora.
- Gestión del ciclo de vida de los reproductores.

### Movimiento retardado de la computadora

Como mejora adicional, se implementó un retraso de aproximadamente **1 segundo** antes del movimiento de la computadora.

El retraso se realiza mediante `Handler.postDelayed()` sin bloquear el hilo de interfaz.

Esto permite mostrar claramente el estado de la partida y diferenciar el turno del jugador del turno de la computadora.

---

# 🚀 Reto 6 — Persistencia, orientación y manejo del estado

El Reto 6 extiende la aplicación desarrollada en los retos anteriores para hacerla más robusta frente a recreaciones de la actividad y para conservar información importante.

## Principales funcionalidades

### 🔄 Cambio de orientación

Se incorporó un diseño específico para orientación horizontal:

```text
res/layout/activity_main.xml
res/layout-land/activity_main.xml
```

La interfaz se adapta a orientación vertical y horizontal manteniendo la funcionalidad del juego.

### 💾 Persistencia de configuración

Se utiliza `SharedPreferences` para conservar información entre ejecuciones.

Se persisten, entre otros:

- Nivel de dificultad.
- Marcadores/estadísticas de partidas.
- Resultados acumulados.

### 🏆 Marcadores

La aplicación conserva estadísticas de:

- Victorias del jugador.
- Victorias de la computadora.
- Empates.

También se incorporó la opción:

**Reset Scores**

para reiniciar las estadísticas.

### 🔁 Restauración del estado de la partida

Se implementó `onSaveInstanceState()` para conservar el estado cuando Android recrea la actividad.

Se restaura información como:

- Estado completo del tablero.
- Estado de la partida.
- Resultado/estado del juego.
- Marcadores.
- Quién comienza.
- Turno actual.
- Estado de movimiento pendiente de la computadora.

### ⏱️ Movimiento retardado y recreación

Se mejoró el manejo del movimiento retardado de la computadora.

La aplicación:

- Evita aceptar movimientos del jugador mientras corresponde el turno de la computadora.
- Cancela correctamente el `Runnable` cuando la actividad entra en pausa.
- Reprograma el movimiento pendiente cuando la actividad se recrea.
- Evita ejecutar el mismo movimiento de la computadora más de una vez.
- Evita que la computadora realice un movimiento después de que una jugada humana haya terminado la partida.

### 🧩 API del estado del tablero

`TicTacToeGame` incorpora una API para obtener y restaurar el tablero completo:

```java
getBoardState()
setBoardState(...)
```

La implementación utiliza copias defensivas para evitar modificaciones externas accidentales del estado interno.

### 🔀 Separación de turnos

`TurnOrchestrator` fue reorganizado para separar las operaciones:

- Aplicar movimiento humano.
- Ejecutar movimiento de la computadora.

Esto facilita el control del turno y el manejo del retraso de la computadora.

---

# 🏗️ Estructura general del proyecto

El repositorio contiene las diferentes versiones del proyecto organizadas por reto.

```text
DADM_Repositorio/
│
├── Reto5_TicTacToe/
│   └── Proyecto correspondiente al Reto 5
│
├── Reto6_TicTacToe/
│   └── Proyecto correspondiente al Reto 6
│
└── README.md
```

El proyecto del Reto 6 se conserva como una evolución independiente del Reto 5.

---

# 🛠️ Tecnologías utilizadas

- **Java**
- **Android**
- **Android Studio**
- **Gradle**
- **XML** para layouts y recursos
- `android.view.View`
- `Canvas`
- `Bitmap`
- `MediaPlayer`
- `Handler`
- `SharedPreferences`
- `Bundle`
- JUnit para pruebas

---

# 🧠 Arquitectura y componentes principales

Algunos de los componentes principales de la aplicación son:

### `MainActivity`

Responsable de:

- Controlar la interfaz.
- Gestionar el ciclo de vida de la actividad.
- Procesar las opciones del menú.
- Coordinar los turnos.
- Restaurar y guardar el estado.
- Gestionar la persistencia de configuración y estadísticas.

### `TicTacToeGame`

Contiene la lógica principal del juego:

- Estado del tablero.
- Movimientos.
- Validación.
- Detección de ganador.
- Empate.
- Nivel de dificultad.
- Selección del movimiento de la computadora.

### `BoardView`

Responsable de la representación gráfica del tablero:

- Dibujo del tablero.
- Dibujo de las marcas.
- Cálculo de posiciones.
- Procesamiento de interacción táctil.

### `TurnOrchestrator`

Coordina la aplicación de los movimientos y la transición entre el turno humano y el turno de la computadora.

### `MoveGate`

Ayuda a controlar cuándo la interacción del usuario está permitida, especialmente durante el turno de la computadora o cuando la partida ya terminó.

### `SoundPlayer` / `SoundLifecycle`

Gestionan la reproducción de sonidos y su ciclo de vida.

### `Difficulty`

Representa los diferentes niveles de dificultad disponibles para la computadora.

---

# 🧪 Pruebas

El proyecto del Reto 6 fue validado mediante pruebas automáticas y pruebas manuales.

## Pruebas automáticas

Resultado de la suite de pruebas unitarias:

```text
44 tests
0 failures
```

La compilación de la aplicación también fue verificada correctamente mediante:

```bash
./gradlew assembleDebug
```

Resultado:

```text
BUILD SUCCESSFUL
```

## Pruebas manuales realizadas

Se verificaron, entre otros, los siguientes escenarios:

1. Cambio de orientación y funcionamiento del menú en landscape.
2. Restauración del turno después de recrear la actividad.
3. Restauración de un movimiento retardado de la computadora.
4. Ejecución del movimiento de la computadora exactamente una vez.
5. Prevención de movimientos de la computadora después de terminar la partida.
6. Persistencia de los marcadores.
7. Persistencia del nivel de dificultad.
8. Funcionamiento de **Reset Scores**.
9. Regresión de funcionalidades desarrolladas en el Reto 5.

---

# 📌 Estado actual

Hasta el **Reto 6**, la aplicación cuenta con:

- Juego completo de Tic-Tac-Toe.
- Inteligencia de computadora con diferentes dificultades.
- Tablero gráfico personalizado.
- Interacción táctil.
- Recursos gráficos para X y O.
- Efectos de sonido.
- Movimiento retardado de la computadora.
- Menú de opciones.
- Nueva partida.
- Selección de dificultad.
- Marcadores persistentes.
- Reinicio de marcadores.
- Persistencia de configuración.
- Restauración del estado de la partida.
- Soporte para orientación vertical y horizontal.
- Pruebas unitarias.
- Validación manual de escenarios de recreación y ciclo de vida.

---

# ▶️ Ejecución del proyecto

1. Clonar el repositorio:

```bash
git clone https://github.com/GuillermoMoya/DADM_Repositorio.git
```

2. Abrir el proyecto correspondiente en **Android Studio**.

3. Seleccionar el proyecto:

```text
Reto6_TicTacToe
```

4. Sincronizar Gradle.

5. Ejecutar la aplicación en un dispositivo Android o emulador.

---

# 📂 Organización de los retos

| Reto | Tema | Resultado principal |
|---|---|---|
| Reto 1 | Planteamiento inicial | Definición inicial del proyecto |
| Reto 2 | Canvas y prototipado | Modelo Canvas y prototipo/mockups |
| Reto 3 | Tic-Tac-Toe para Android | Primera versión funcional de la aplicación |
| Reto 4 | Menús y diálogos | Menú, nueva partida y niveles de dificultad |
| Reto 5 | Gráficos y sonido | `BoardView`, gráficos, sonidos y movimiento retardado |
| Reto 6 | Estado y persistencia | Orientación, persistencia, restauración y pruebas |

---

# 👨‍💻 Autores

**Guillermo Moya Romero**

Universidad Nacional de Colombia  
Facultad de Ingeniería  
Curso: Desarrollo de Aplicaciones para Dispositivos Móviles

---

## 📖 Referencias de los retos

Los retos de implementación de Tic-Tac-Toe se basan en el material proporcionado para el curso y en los tutoriales de Android utilizados como guía durante el desarrollo.

- Reto 2 — Modelo Canvas y Prototipo.
- Reto 3 — Tic-Tac-Toe for Android.
- Reto 4 — Menus and Dialog Boxes.
- Reto 5 — Graphics and Sound.

El desarrollo de los retos posteriores adapta y extiende la implementación inicial para cumplir los requerimientos del proyecto y las pruebas realizadas.

---

## 📄 Licencia

Este repositorio corresponde a un proyecto académico desarrollado para el curso **Desarrollo de Aplicaciones para Dispositivos Móviles**.
