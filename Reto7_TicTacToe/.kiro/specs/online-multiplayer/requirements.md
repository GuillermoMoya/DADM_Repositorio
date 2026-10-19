# Requirements Document

_Documento de Requisitos_

## Introduction

Esta funcionalidad extiende la aplicación Android de Triqui (Tic-Tac-Toe) existente —actualmente un juego local de Humano (X) contra la Computadora (O)— para permitir que dos jugadores compitan desde dispositivos Android distintos, con la interacción mediada por un servicio en la nube. Esta especificación corresponde al Reto 7 "Jugando Triqui Online".

El objetivo es incorporar un modo multijugador en línea en tiempo real: un jugador crea una partida, otro la encuentra en una lista y se une, y ambos juegan de forma sincronizada. La aplicación debe conservar íntegramente el modo local de un jugador (Humano contra Computadora) ya existente.

El diseño reutiliza en la medida de lo posible el modelo de juego puro y las costuras (seams) sin dependencias de Android que ya existen en el proyecto (`TicTacToeGame`, `Game`, `TurnOrchestrator`, `MoveGate`, `GameStatus`, `Difficulty`, `BoardGeometry`, `BoardView`), de modo que la validación y el renderizado del tablero del lado del cliente se apoyen en código ya probado.

**Fuera de alcance (explícito):** chat entre jugadores, rankings/clasificaciones, y perfiles de usuario complejos. Estas funcionalidades no forman parte de esta especificación.

## Glossary

- **Aplicación**: La aplicación Android de Triqui (namespace `co.edu.unal.reto4_tictactoe`) que incorpora el modo local existente y el nuevo modo en línea.
- **Servicio_Nube**: El servicio en la nube que media la interacción entre los dos dispositivos, mantiene el estado autoritativo de las partidas y notifica cambios en tiempo real. Técnicamente propuesto como Firebase Realtime Database (ver "Decisiones técnicas propuestas").
- **Lobby**: La vista inicial del modo en línea que muestra la lista de partidas disponibles para unirse y permite crear una partida nueva.
- **Partida**: Una sesión de juego en línea identificada de forma única, con exactamente dos posiciones de jugador (anfitrión e invitado) y un estado de tablero, turno y resultado.
- **Anfitrión**: El Jugador que crea una Partida.
- **Invitado**: El Jugador que se une a una Partida creada por el Anfitrión.
- **Jugador**: Un usuario del modo en línea identificado por el Servicio_Nube. Técnicamente propuesto mediante Firebase Anonymous Authentication (ver "Decisiones técnicas propuestas").
- **Estado_Partida**: El conjunto de datos autoritativo de una Partida: tablero (9 celdas), jugador en turno, estado del ciclo de vida (esperando/en juego/finalizada), asignación X/O y resultado.
- **Ciclo_Vida_Partida**: El conjunto de estados por los que pasa una Partida: ESPERANDO (sin Invitado), EN_JUEGO (dos jugadores presentes), FINALIZADA (victoria o empate), ABANDONADA (un jugador salió o se desconectó).
- **Listener**: Un observador registrado contra el Servicio_Nube que recibe actualizaciones en tiempo real de datos del Lobby o de una Partida.
- **Reglas_Seguridad**: Las reglas de seguridad del Servicio_Nube que controlan el acceso de lectura/escritura a los datos. Técnicamente propuestas como Firebase Security Rules.
- **Escritura_Atómica**: Una operación de escritura (transacción o actualización condicional) sobre el Servicio_Nube que se aplica de forma indivisible para evitar condiciones de carrera. Técnicamente propuesta mediante transacciones de Firebase Realtime Database.
- **TicTacToeGame**: El modelo de juego puro existente, sin dependencias de Android (tablero, `checkForWinner` que retorna 0=continúa, 1=empate, 2=gana X, 3=gana O, dificultad de IA).
- **Modo_Local**: El modo de juego existente de un jugador: Humano (X) contra Computadora (O).
- **Modo_Online**: El nuevo modo multijugador de dos dispositivos mediado por el Servicio_Nube.

---

## Requirements

### Requisitos del profesor

> Esta sección contiene los **7 requisitos obligatorios del profesor** para el Reto 7. Son de cumplimiento estricto.

### Requisito 1: Cada partida tiene exactamente dos jugadores

**Historia de usuario:** Como profesor evaluador, quiero que cada partida tenga exactamente dos jugadores, para que el juego de Triqui en línea se desarrolle entre dos personas.

#### Criterios de aceptación

1. THE Servicio_Nube SHALL mantener en cada Partida exactamente dos posiciones de Jugador: una de Anfitrión y una de Invitado.
2. WHEN una Partida tiene ya un Anfitrión y un Invitado, THE Servicio_Nube SHALL rechazar cualquier intento de un tercer Jugador de unirse a esa Partida.
3. **Given** una Partida con una sola posición ocupada (Anfitrión), **When** un segundo Jugador solicita unirse, **Then** THE Servicio_Nube SHALL asignarlo como Invitado y marcar la Partida como completa.

### Requisito 2: Cualquiera puede crear una partida

**Historia de usuario:** Como Jugador, quiero poder crear una partida, para que otro jugador pueda unirse y jugar conmigo.

#### Criterios de aceptación

1. WHEN un Jugador identificado selecciona la opción de crear partida en el Lobby, THE Aplicación SHALL solicitar al Servicio_Nube la creación de una nueva Partida.
2. WHEN se crea una nueva Partida, THE Servicio_Nube SHALL registrar al Jugador solicitante como Anfitrión.
3. THE Aplicación SHALL permitir la creación de Partidas a cualquier Jugador identificado, sin restricción de rol.

### Requisito 3: Al iniciar la aplicación se muestra una lista de partidas disponibles

**Historia de usuario:** Como Jugador, quiero ver una lista de partidas disponibles al abrir el modo en línea, para elegir a cuál unirme.

#### Criterios de aceptación

1. WHEN el Jugador abre el Modo_Online, THE Aplicación SHALL mostrar en el Lobby la lista de Partidas en estado ESPERANDO (con posición de Invitado libre).
2. THE Aplicación SHALL excluir del Lobby las Partidas que estén EN_JUEGO, FINALIZADA o ABANDONADA.
3. IF no existe ninguna Partida disponible, THEN THE Aplicación SHALL mostrar el Lobby con una indicación de que no hay partidas disponibles y la opción de crear una.

### Requisito 4: El usuario puede crear una partida o unirse a una disponible

**Historia de usuario:** Como Jugador, quiero crear una partida o unirme a una existente desde el Lobby, para empezar a jugar.

#### Criterios de aceptación

1. THE Lobby SHALL ofrecer al Jugador una acción para crear una Partida y una acción para unirse a una Partida disponible de la lista.
2. WHEN el Jugador selecciona unirse a una Partida disponible, THE Aplicación SHALL solicitar al Servicio_Nube la incorporación del Jugador como Invitado de esa Partida.
3. WHEN el Jugador crea o se une correctamente a una Partida, THE Aplicación SHALL abrir la vista de juego de esa Partida.

### Requisito 5: El usuario solo puede jugar después de unirse a una partida

**Historia de usuario:** Como profesor evaluador, quiero que un usuario solo pueda jugar tras unirse a una partida, para garantizar que todo movimiento ocurra dentro de una partida válida de dos jugadores.

#### Criterios de aceptación

1. IF un Jugador no está registrado como Anfitrión o Invitado de una Partida, THEN THE Aplicación SHALL impedir que ese Jugador realice movimientos en esa Partida.
2. WHILE una Partida está en estado ESPERANDO (sin Invitado), THE Aplicación SHALL impedir que se realicen movimientos en el tablero.
3. WHEN una Partida pasa a estado EN_JUEGO (dos jugadores presentes), THE Aplicación SHALL habilitar la realización de movimientos conforme al turno asignado.

### Requisito 6: Un servicio en la nube media la interacción

**Historia de usuario:** Como profesor evaluador, quiero que un servicio en la nube medie la interacción entre dispositivos, para que ambos jugadores compartan el mismo estado de partida en tiempo real.

#### Criterios de aceptación

1. THE Servicio_Nube SHALL ser la fuente autoritativa del Estado_Partida (tablero, turno, ciclo de vida y resultado).
2. WHEN un Jugador realiza una acción válida sobre una Partida, THE Aplicación SHALL propagar el cambio a través del Servicio_Nube y no mediante comunicación directa entre dispositivos.
3. WHEN el Estado_Partida cambia en el Servicio_Nube, THE Servicio_Nube SHALL notificar a los Listeners de ambos dispositivos participantes.

### Requisito 7: El profesor debe poder jugar con el estudiante durante la evaluación

**Historia de usuario:** Como profesor evaluador, quiero poder jugar una partida con el estudiante durante la evaluación, para verificar el funcionamiento del modo en línea entre dos dispositivos físicos.

#### Criterios de aceptación

1. WHEN el estudiante y el profesor abren el Modo_Online desde dispositivos distintos conectados al mismo Servicio_Nube, THE Aplicación SHALL mostrar en el Lobby de cada dispositivo las Partidas disponibles creadas desde cualquiera de los dos dispositivos.
2. **Given** que el estudiante ha creado una Partida, **When** el profesor abre el Lobby, **Then** THE Aplicación del profesor SHALL mostrar esa Partida como disponible para unirse.
3. WHEN el profesor se une a la Partida creada por el estudiante, THE Aplicación SHALL permitir que ambos jueguen una partida completa sincronizada entre los dos dispositivos.

> **Nota de evaluación:** El mecanismo concreto para que el profesor encuentre la partida del estudiante (lista compartida global vs. unirse por código de partida) es una **Decisión pendiente** (ver sección correspondiente), ya que puede afectar la arquitectura y las Reglas_Seguridad.

---

## Decisiones técnicas propuestas

> Esta sección documenta las **decisiones técnicas propuestas** por el equipo. **No son requisitos del profesor** y pueden ajustarse en la fase de diseño. Las que afectan a Gradle/configuración se tratan como **pendientes** (ver "Decisiones pendientes") porque modificar Gradle está fuera de alcance en esta fase.

- **Servicio_Nube = Firebase Realtime Database**: se propone usar Firebase Realtime Database por su sincronización en tiempo real y su soporte de transacciones/escrituras atómicas, adecuadas para el Estado_Partida compartido.
- **Identificación de Jugador = Firebase Anonymous Authentication**: se propone identificar a cada Jugador mediante autenticación anónima de Firebase, evitando perfiles complejos (fuera de alcance) pero obteniendo un identificador estable por dispositivo para asignar roles (Anfitrión/Invitado) y aplicar Reglas_Seguridad.
- **Integridad por Escrituras_Atómicas**: se propone usar transacciones de Firebase para unirse a una Partida y para aplicar movimientos/turnos, de modo que las condiciones de carrera (dos escrituras concurrentes) no corrompan el Estado_Partida.
- **Reutilización del modelo existente**: se propone reutilizar `TicTacToeGame` para el modelo de tablero y la detección de ganador/empate (`checkForWinner`), y las costuras `Game`, `TurnOrchestrator`, `MoveGate`, `GameStatus`, `BoardGeometry` y `BoardView` para la validación y el renderizado del lado del cliente, manteniendo al Servicio_Nube como fuente autoritativa.
- **Compatibilidad pendiente de evaluar**: el proyecto actual **no** incluye Firebase ni el plugin `google-services`, ni `google-services.json`, ni el Firebase BoM en `gradle/libs.versions.toml` ni en `app/build.gradle.kts`. Añadir Firebase requiere tocar Gradle/configuración, lo cual está **fuera de alcance en esta fase**. Por lo tanto, la adopción de Firebase se documenta como **pendiente** y debe evaluarse su compatibilidad (minSdk 24, compileSdk/targetSdk 37, AGP 9.3.3, Java 11) antes de comprometerla en diseño.

---

### Requisitos funcionales detallados

### Requisito 8: Lobby con actualización automática en tiempo real

**Historia de usuario:** Como Jugador, quiero que la lista de partidas del Lobby se actualice automáticamente, para ver nuevas partidas o la desaparición de partidas sin recargar manualmente.

#### Criterios de aceptación

1. WHILE el Lobby está visible, THE Aplicación SHALL mantener un Listener activo sobre la lista de Partidas disponibles del Servicio_Nube.
2. WHEN una Partida disponible nueva se crea en el Servicio_Nube, THE Aplicación SHALL añadirla a la lista del Lobby en un lapso no mayor a 3 segundos sin intervención del Jugador.
3. WHEN una Partida deja de estar disponible (se completa, finaliza o se abandona), THE Aplicación SHALL retirarla de la lista del Lobby en un lapso no mayor a 3 segundos.

### Requisito 9: Creación de partida con identificador único

**Historia de usuario:** Como Jugador, quiero que cada partida que creo tenga un identificador único, para que pueda ser referenciada y unida sin ambigüedad.

#### Criterios de aceptación

1. WHEN se crea una Partida, THE Servicio_Nube SHALL asignarle un identificador único que no colisione con el de ninguna otra Partida existente.
2. THE Servicio_Nube SHALL inicializar una Partida recién creada con el tablero vacío (9 celdas sin marca) y en estado ESPERANDO.
3. THE Aplicación SHALL referenciar la Partida por su identificador único en todas las operaciones de lectura y escritura.

### Requisito 10: Unión controlada de un segundo jugador

**Historia de usuario:** Como Jugador, quiero unirme a una partida solo si hay espacio, para no interferir en partidas ya completas.

#### Criterios de aceptación

1. **Given** una Partida en estado ESPERANDO con posición de Invitado libre, **When** un Jugador solicita unirse, **Then** THE Servicio_Nube SHALL asignarlo como Invitado mediante una Escritura_Atómica y cambiar el estado de la Partida a EN_JUEGO.
2. IF dos Jugadores solicitan unirse de forma concurrente a la misma Partida con una sola posición libre, THEN THE Servicio_Nube SHALL aceptar exactamente a uno como Invitado y rechazar al otro.
3. IF un Jugador solicita unirse a una Partida que ya está EN_JUEGO, FINALIZADA o ABANDONADA, THEN THE Servicio_Nube SHALL rechazar la solicitud y THE Aplicación SHALL informar que la Partida no está disponible.

### Requisito 11: Asignación consistente de X y O

**Historia de usuario:** Como Jugador, quiero que las marcas X y O se asignen de forma consistente entre los dos jugadores, para que no haya ambigüedad sobre quién juega cada marca.

#### Criterios de aceptación

1. WHEN una Partida pasa a estado EN_JUEGO, THE Servicio_Nube SHALL asignar una marca distinta (X u O) a cada uno de los dos Jugadores, de modo que ningún par de Jugadores comparta la misma marca.
2. THE Aplicación SHALL mostrar en cada dispositivo la marca asignada a su propio Jugador y la del oponente de forma coherente con el Estado_Partida.
3. THE Servicio_Nube SHALL definir que el Jugador con la marca X realiza el primer movimiento.

> **Nota:** La regla concreta de asignación (por ejemplo, Anfitrión=X e Invitado=O, o asignación aleatoria) es una **Decisión pendiente**.

### Requisito 12: Estado de espera hasta la llegada del segundo jugador

**Historia de usuario:** Como Anfitrión, quiero ver un estado de espera hasta que llegue el segundo jugador, para saber que la partida aún no ha comenzado.

#### Criterios de aceptación

1. WHILE una Partida está en estado ESPERANDO, THE Aplicación del Anfitrión SHALL mostrar una indicación de espera del segundo Jugador.
2. WHEN el Invitado se une y la Partida pasa a EN_JUEGO, THE Aplicación de ambos dispositivos SHALL salir del estado de espera y mostrar el tablero jugable.
3. WHILE una Partida está en estado ESPERANDO, THE Aplicación SHALL impedir la realización de movimientos.

### Requisito 13: Sincronización de tablero, turnos y resultado entre dispositivos

**Historia de usuario:** Como Jugador, quiero que el tablero, el turno y el resultado se sincronicen entre los dos dispositivos, para que ambos vean el mismo estado del juego.

#### Criterios de aceptación

1. WHEN un Jugador realiza un movimiento válido, THE Servicio_Nube SHALL actualizar el tablero y el turno, y THE Aplicación del otro dispositivo SHALL reflejar el cambio en un lapso no mayor a 3 segundos.
2. WHEN el Estado_Partida indica victoria o empate, THE Aplicación de ambos dispositivos SHALL mostrar el mismo resultado.
3. THE Aplicación SHALL renderizar el tablero a partir del Estado_Partida autoritativo del Servicio_Nube y no a partir de estado local no confirmado.

### Requisito 14: Validación de movimientos, turnos, celdas y resultado

**Historia de usuario:** Como Jugador, quiero que los movimientos se validen (turno correcto, celda libre, fin de juego), para que el juego siga las reglas del Triqui.

#### Criterios de aceptación

1. IF un Jugador intenta mover cuando no es su turno, THEN THE Servicio_Nube SHALL rechazar el movimiento y THE Aplicación SHALL no aplicarlo al Estado_Partida.
2. IF un Jugador intenta marcar una celda ya ocupada, THEN THE Servicio_Nube SHALL rechazar el movimiento.
3. WHEN se aplica un movimiento válido, THE Aplicación SHALL usar `TicTacToeGame.checkForWinner` para determinar si el resultado es 0 (continúa), 1 (empate), 2 (gana X) o 3 (gana O), y THE Servicio_Nube SHALL registrar el resultado correspondiente.
4. WHEN el resultado es victoria o empate, THE Servicio_Nube SHALL cambiar el estado de la Partida a FINALIZADA e impedir movimientos posteriores.

### Requisito 15: Integridad concurrente mediante escrituras atómicas

**Historia de usuario:** Como profesor evaluador, quiero que las escrituras concurrentes no corrompan la partida, para que el estado sea siempre consistente entre dispositivos.

#### Criterios de aceptación

1. WHEN dos Jugadores intentan escribir en el Estado_Partida de forma concurrente, THE Servicio_Nube SHALL aplicar las escrituras mediante Escrituras_Atómicas de modo que el estado resultante sea válido y determinista.
2. IF dos movimientos concurrentes apuntan a la misma celda o al mismo turno, THEN THE Servicio_Nube SHALL aceptar exactamente uno y rechazar el otro.
3. THE Servicio_Nube SHALL impedir que un tercer Jugador escriba en una Partida ya completa (dos jugadores) mediante Escrituras_Atómicas y Reglas_Seguridad.

### Requisito 16: Manejo de errores de conexión y de partidas no disponibles

**Historia de usuario:** Como Jugador, quiero que la aplicación maneje los errores de conexión y las partidas no disponibles, para no quedar en un estado inconsistente ante fallos.

#### Criterios de aceptación

1. IF ocurre un error de conexión con el Servicio_Nube durante una operación, THEN THE Aplicación SHALL informar al Jugador del error y mantener la Aplicación en un estado consistente y operable.
2. IF un Jugador intenta unirse a una Partida que ya no está disponible, THEN THE Aplicación SHALL informar que la Partida no está disponible y regresar al Lobby.
3. WHEN se restablece la conexión tras un error, THE Aplicación SHALL volver a sincronizar el Estado_Partida vigente desde el Servicio_Nube.

### Requisito 17: Ciclo de vida correcto de los listeners

**Historia de usuario:** Como desarrollador, quiero que los listeners se registren y liberen según el ciclo de vida de Android, para evitar fugas de memoria y escrituras tras cerrar la vista.

#### Criterios de aceptación

1. WHEN una vista con Listeners pasa a estado activo (por ejemplo `onResume`/`onStart`), THE Aplicación SHALL registrar los Listeners correspondientes del Lobby o de la Partida.
2. WHEN una vista con Listeners pasa a estado inactivo o se destruye (por ejemplo `onPause`/`onStop`/`onDestroy`), THE Aplicación SHALL desregistrar los Listeners correspondientes.
3. WHILE no haya ninguna vista activa que requiera actualizaciones, THE Aplicación SHALL no mantener Listeners activos sobre el Servicio_Nube.

### Requisito 18: Comportamiento ante salida o desconexión de un jugador

**Historia de usuario:** Como Jugador, quiero que la partida reaccione cuando el otro jugador sale o se desconecta, para no quedar esperando indefinidamente.

#### Criterios de aceptación

1. WHEN un Jugador abandona voluntariamente una Partida EN_JUEGO, THE Servicio_Nube SHALL marcar la Partida como ABANDONADA y THE Aplicación del Jugador restante SHALL informar que el oponente salió.
2. IF un Jugador se desconecta durante una Partida EN_JUEGO, THEN THE Servicio_Nube SHALL reflejar la ausencia del Jugador y THE Aplicación del Jugador restante SHALL informar la desconexión.
3. WHEN el Anfitrión abandona una Partida en estado ESPERANDO antes de que llegue el Invitado, THE Servicio_Nube SHALL retirar la Partida de la lista de Partidas disponibles del Lobby.

### Requisito 19: Finalización de la partida y regreso al lobby

**Historia de usuario:** Como Jugador, quiero volver al lobby cuando la partida termina, para poder iniciar o unirme a otra partida.

#### Criterios de aceptación

1. WHEN una Partida llega a estado FINALIZADA (victoria o empate), THE Aplicación SHALL mostrar el resultado y ofrecer una acción para regresar al Lobby.
2. WHEN el Jugador selecciona regresar al Lobby, THE Aplicación SHALL desregistrar los Listeners de la Partida y mostrar el Lobby con la lista de Partidas disponibles actualizada.
3. WHEN una Partida queda en estado FINALIZADA o ABANDONADA, THE Servicio_Nube SHALL excluirla de la lista de Partidas disponibles del Lobby.

### Requisito 20: Reutilización del modelo de juego existente

**Historia de usuario:** Como desarrollador, quiero reutilizar el modelo de juego puro y las costuras existentes, para apoyar la validación y el renderizado del cliente en código ya probado.

#### Criterios de aceptación

1. THE Aplicación SHALL usar `TicTacToeGame` como modelo del tablero y de la detección de ganador/empate en el lado del cliente del Modo_Online.
2. THE Aplicación SHALL reutilizar las costuras `Game`, `TurnOrchestrator`, `MoveGate` y `GameStatus` para la validación de turnos/movimientos y el mapeo de estado del lado del cliente, donde resulte aplicable.
3. THE BoardView SHALL seguir sin mantener estado autoritativo y renderizar a partir del modelo, reportando la celda candidata mediante su `OnMoveListener`.
4. THE Servicio_Nube SHALL permanecer como fuente autoritativa del Estado_Partida, aun cuando el cliente use `TicTacToeGame` para validación/renderizado local.

### Requisito 21: Preservación del modo local de un jugador

**Historia de usuario:** Como Jugador, quiero conservar el modo local Humano vs. Computadora, para seguir jugando sin conexión como hasta ahora.

#### Criterios de aceptación

1. THE Aplicación SHALL conservar el Modo_Local (Humano X vs. Computadora O) con el mismo comportamiento existente.
2. WHERE no hay conexión al Servicio_Nube, THE Aplicación SHALL permitir jugar el Modo_Local.
3. THE Aplicación SHALL conservar la persistencia local existente del Modo_Local (puntajes y dificultad en SharedPreferences, estado transitorio en Bundle) y el ciclo de vida de sonido existente (MediaPlayer en `onResume`/`onPause`/`onStop`).

---

### Requisito de seguridad

### Requisito 22: Seguridad del acceso al servicio en la nube

**Historia de usuario:** Como profesor evaluador, quiero que la seguridad no dependa solo del cliente, para que el estado de las partidas no pueda manipularse ni leerse/escribirse de forma indiscriminada.

#### Criterios de aceptación

1. THE Aplicación SHALL tratar la validación del lado del cliente (incluido deshabilitar botones) como insuficiente por sí sola, y THE Servicio_Nube SHALL hacer cumplir la integridad del Estado_Partida mediante Reglas_Seguridad.
2. THE Reglas_Seguridad SHALL restringir las lecturas y escrituras a Jugadores identificados y, para una Partida dada, permitir escrituras de movimiento únicamente a los dos Jugadores asignados a esa Partida.
3. THE Reglas_Seguridad SHALL impedir que un tercer Jugador escriba en una Partida ya completa.
4. THE Aplicación SHALL aplicar movimientos y cambios de turno mediante Escrituras_Atómicas (transacciones u operaciones atómicas) para preservar la integridad frente a concurrencia.
5. THE Reglas_Seguridad SHALL evitar el acceso indiscriminado de lectura/escritura a la base de datos en producción (no reglas abiertas tipo "cualquiera lee/escribe todo").
6. THE documentación de la funcionalidad SHALL describir cómo reemplazar la configuración temporal de pruebas (reglas en modo de prueba) por Reglas_Seguridad bloqueadas de producción antes de la entrega/evaluación.
7. THE equipo SHALL no incluir credenciales ni configuración real de Firebase (por ejemplo `google-services.json` con claves reales) en esta especificación.

---

### Casos borde para evaluación con dos dispositivos físicos

> Casos relevantes a verificar durante la evaluación con dos dispositivos Android reales. Se expresan como criterios verificables.

### Requisito 23: Casos borde de concurrencia y desconexión en dos dispositivos

**Historia de usuario:** Como profesor evaluador, quiero verificar los casos borde en dos dispositivos reales, para confirmar la robustez del modo en línea.

#### Criterios de aceptación

1. **Given** una Partida EN_JUEGO, **When** ambos Jugadores tocan una celda de forma prácticamente simultánea, **Then** THE Servicio_Nube SHALL aceptar únicamente el movimiento del Jugador cuyo turno corresponde y rechazar el otro.
2. **Given** una Partida EN_JUEGO, **When** un Jugador pierde la red en medio de un movimiento, **Then** THE Aplicación SHALL no dejar el Estado_Partida en un estado inconsistente y, al restablecerse la red, SHALL reflejar el Estado_Partida autoritativo vigente.
3. **Given** una Partida con una sola posición libre que acaba de ser ocupada por un Invitado, **When** un segundo Jugador intenta unirse a esa misma Partida, **Then** THE Servicio_Nube SHALL rechazar la unión e THE Aplicación SHALL informar que la Partida ya no está disponible.
4. **Given** una Partida en estado ESPERANDO, **When** el Anfitrión abandona antes de que llegue el Invitado, **Then** THE Servicio_Nube SHALL retirar la Partida del Lobby y un Jugador que intente unirse SHALL recibir aviso de no disponibilidad.
5. **Given** una Partida EN_JUEGO, **When** un Jugador se desconecta en medio de la partida, **Then** THE Aplicación del Jugador restante SHALL informar la desconexión y ofrecer regresar al Lobby.
6. **Given** dos Jugadores que tocan simultáneamente la misma celda libre en turnos ambiguos, **When** llegan dos escrituras concurrentes, **Then** THE Servicio_Nube SHALL aplicar Escrituras_Atómicas de modo que la celda quede marcada por un solo Jugador.

---

## Decisiones pendientes

> Decisiones abiertas que pueden afectar la arquitectura y deben resolverse en la fase de diseño. No son requisitos; se listan para seguimiento.

1. **Configuración de Firebase / credenciales**: adopción del plugin `google-services`, inclusión de `google-services.json` y del Firebase BoM. Afecta a Gradle/configuración, que está fuera de alcance en esta fase. Requiere evaluar compatibilidad con minSdk 24, compileSdk/targetSdk 37, AGP 9.3.3 y Java 11.
2. **Arquitectura de pantallas**: mantener una sola Activity (`MainActivity`) y conmutar vistas vs. añadir una Activity/Fragment de Lobby separada y una Activity/Fragment de Partida en línea.
3. **Estrategia de reconexión**: cómo detectar desconexión/reconexión (por ejemplo, presencia/`onDisconnect` de Firebase) y cómo reanudar la sincronización tras pérdida de red.
4. **Limpieza de partidas / TTL**: cómo y cuándo eliminar Partidas FINALIZADA/ABANDONADA o ESPERANDO huérfanas (limpieza en cliente, marcas de tiempo, o lógica del servicio).
5. **Regla de asignación de X/O**: Anfitrión=X e Invitado=O, asignación aleatoria, u otra; e implicación sobre quién inicia.
6. **Mecanismo para "el profesor juega con el estudiante"**: lista global compartida de partidas disponibles vs. unión por código de partida. Afecta a las Reglas_Seguridad y al diseño del Lobby.
7. **Estrategia de pruebas del Modo_Online**: qué validar con pruebas de propiedades (lógica pura reutilizada de `TicTacToeGame`) vs. pruebas de integración/humo con 1–3 ejemplos para la interacción con el Servicio_Nube (dado que probar servicios externos con 100 iteraciones no aporta valor).
8. **Reglas de prueba vs. producción**: definición concreta de las Reglas_Seguridad de producción y procedimiento documentado para sustituir las reglas de modo de prueba.
