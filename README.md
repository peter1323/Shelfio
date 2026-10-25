# Shelfio

Toda tu colección, en un solo lugar. Biblioteca personal de libros, mangas y cómics para Android.

Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose · CameraX + ML Kit. Los datos se guardan solo en el móvil: no hay cuentas ni servidor.

## Qué hace

- **Inicio**: saludo, búsqueda, contadores, últimos añadidos, En lectura, Pendientes de compra y accesos a Añadir / Escanear.
- **Biblioteca**: búsqueda (título, autor, editorial, ISBN, ubicación y a quién se prestó) y filtros por tipo y por estado de la colección.
- **Añadir**: a mano o escaneando el ISBN. Al crear una serie se crea su primer tomo.
- **Ficha de serie**: portada, datos, progreso calculado y lista de tomos. Cambiar «Lo tengo» y estado de lectura con un toque. «Añadir siguiente tomo». Si solo hay un tomo se ve como un libro normal.
- **Colecciones**: crear, renombrar, borrar y añadir/quitar series (desde el icono de colecciones de la ficha).
- **Portadas**: si no hay portada real se dibuja una con código (azul libros, violeta mangas, cian cómics). La real (por ISBN o foto) la sustituye en todas las pantallas; «Quitar» vuelve a la generada.
- **Ubicación física** de cada tomo («Caja 3») y búsqueda por ubicación.
- **Perfil**: estadísticas, accesos a Lista de deseos y Préstamos, y ajustes (nombre del saludo, copia de seguridad e informe PDF).

### Versión 2.0

- **Copia de seguridad** (Perfil › Ajustes): exporta un .zip (datos.json + portadas/) con el selector del sistema o lo comparte (Drive, correo…). Importar valida el archivo, enseña un resumen y solo reemplaza la biblioteca si confirmas; va en una transacción (entra todo o nada). Muestra la fecha de la última copia.
- **Lista de deseos**: prioridad (alta, media, baja) y nota de compra en los tomos que no tengo; pantalla con búsqueda y orden por prioridad, fecha o título; «Lo he comprado» (pregunta dónde lo guardas); «Añadir los tomos que me faltan hasta el N» en la ficha; series que aún no tengo desde Añadir (quitar «Lo tengo» y poner «Me faltan hasta el tomo»).
- **Préstamos**: «Prestar» y «Marcar como devuelto» en la ficha del tomo; etiqueta «Prestado a…» con la ubicación; pantalla con Activos e Historial, retrasados en rojo y búsqueda por persona. Solo se guarda el nombre.
- **Estado editorial** (lápiz de la ficha): estado en mi idioma y en el país de origen, tomos publicados; «Tengo X de Y publicados», «Colección completa» y filtros Completas / Me faltan tomos / En publicación en Biblioteca.
- **Informe PDF** (Perfil › Ajustes): opciones (lista de deseos, ubicaciones, préstamos), progreso y cancelar, guardar o compartir `shelfio-informe-AAAA-MM-DD.pdf`.

## Arquitectura (Modelo · Vista · ViewModel)

Es el MVC de Android: el ViewModel hace de controlador. Flujo de datos en un solo sentido: la Vista muestra estado y avisa de acciones; el ViewModel decide; el Modelo guarda.

```
app/src/main/java/com/shelfio/app/
  ShelfioApplication.kt     crea una sola vez base de datos y repositorios
  MainActivity.kt           punto de entrada (solo pone el tema y la navegación)

  data/                     MODELO
    model/Models.kt         entidades Room: Series, Volume, ShelfCollection, SeriesCollectionCrossRef
    db/                     DAO (consultas SQL) y base de datos Room
    ShelfioRepository.kt    único acceso a los datos; borra portadas que dejan de usarse
    LibraryLogic.kt         funciones puras: búsqueda, ubicación, pendientes, siguiente tomo…
    Isbn.kt                 validar y normalizar ISBN-10/13
    CoverStorage.kt         guardar/borrar portadas en el almacenamiento interno
    books/BookLookup.kt     Google Books y Open Library
    backup/                 copia de seguridad .zip (formato versionado)
    report/                 informe PDF
    SettingsRepository.kt   ajustes con DataStore

  ui/                       VISTA + VIEWMODEL (un paquete por pantalla)
    MainViewModel.kt        estado de las pestañas principales
    AppViewModelProvider.kt fábrica única de ViewModels
    navigation/             rutas, NavHost y barra inferior
    home/ library/ add/ collections/ profile/ series/ volume/ scan/ wishlist/ loans/
    components/             piezas reutilizables (portadas, tarjetas, diálogos…)
    theme/                  colores, tipografía y tema
```

Las pantallas nunca tocan el DAO ni la base de datos: hablan con su ViewModel y este con el repositorio.

## Versiones usadas

| Herramienta | Versión | Motivo |
|---|---|---|
| Gradle | 9.3.1 | Ya instalada en este equipo por Android Studio |
| Android Gradle Plugin | 9.1.0 | Estable, compatible con Gradle 9.3 (Kotlin integrado en AGP 9) |
| Kotlin / plugin Compose | 2.3.21 | Estable, compatible con AGP 9.1 y KSP 2.3 |
| KSP | 2.3.12 | Generador de código de Room |
| Compose BOM | 2026.03.01 (Compose 1.10) | Último BOM que compila con compileSdk 36; BOM más recientes (p. ej. 2026.09.00) exigen compileSdk 37 |
| Room | 2.8.4 | Base de datos local |
| CameraX | 1.5.3 | Vista de cámara del escáner |
| ML Kit barcode-scanning | 17.3.0 | Lectura del código de barras |
| Coil | 3.3.0 | Mostrar las portadas guardadas |
| compileSdk / targetSdk | 36 | Plataforma instalada en el SDK local |
| minSdk | 26 | Android 8.0 o superior |
| JDK | el de Android Studio (jbr) | |

### Por qué ML Kit para el escáner

- **Mantenimiento**: Google lo mantiene activamente. La alternativa libre más usada, ZXing, está en modo mantenimiento (sin novedades).
- **Licencia**: uso gratuito según los términos de ML Kit, sin claves ni cuenta. Se usa la versión con el modelo **incluido en la app** (`barcode-scanning`), así que no depende de Google Play Services y funciona sin conexión.
- **Integración**: CameraX tiene un adaptador oficial (`camera-mlkit-vision`), lo que deja el código del escáner en una sola función.
- Coste: la APK crece unos megas por el modelo.

### Búsqueda por ISBN

Se consultan Google Books y Open Library a la vez, sin claves. Si una falla se usa la otra; si fallan las dos, se avisa y la entrada manual sigue funcionando con el ISBN ya escrito. Nada se guarda sin pasar por la pantalla de revisión. Los campos que no vienen se quedan vacíos.

**Limitación conocida**: Google Books sin clave comparte una cuota diaria entre todas las apps; a veces responde «429 cuota excedida» (pasó durante las pruebas). Entonces solo se usa Open Library. Ambas tienen poca cobertura de mangas y cómics editados en España.

## Abrir y compilar

1. Abre Android Studio → **File › Open** → elige la carpeta `Shelfio`.
2. Espera a que termine la sincronización de Gradle (la primera vez descarga dependencias).
3. Desde terminal, en la carpeta del proyecto:

```bash
./gradlew assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Pruebas automáticas

```bash
./gradlew testDebugUnitTest
```

50 pruebas: búsqueda y ubicación, progreso, pendientes, «añadir siguiente tomo», ISBN, lista de deseos, préstamos, estado editorial, contenido del informe PDF, el repositorio contra Room en memoria (Robolectric), migraciones de la base de datos desde la versión 1 con datos reales y copias de seguridad (exportar → importar, archivos inválidos y copias de los formatos 1 a 4).

## Probar en tu móvil Android por USB

### 1. Activar las opciones de desarrollador
1. En el móvil: **Ajustes › Información del teléfono** (en Realme: *Ajustes › Acerca del teléfono › Versión*).
2. Toca **7 veces seguidas** sobre **Número de compilación**. Si pide el PIN, ponlo.
3. Aparece el mensaje «Ya eres desarrollador».

### 2. Activar la depuración USB
1. **Ajustes › Ajustes adicionales › Opciones de desarrollador** (en otros móviles: *Ajustes › Sistema › Opciones de desarrollador*).
2. Activa **Depuración USB**.
3. (Xiaomi) activa también **Instalar vía USB**.

### 3. Conectar el móvil
1. Conecta el móvil al PC con un cable USB **de datos** (algunos cables solo cargan).
2. En el móvil aparece «¿Permitir depuración USB?» → marca **Permitir siempre desde este ordenador** → **Aceptar**.
3. Si el móvil pregunta el modo USB, elige **Transferencia de archivos**.

### 4. Ejecutar desde Android Studio
1. Arriba, en el selector de dispositivos, debe aparecer el nombre de tu móvil.
2. Pulsa el botón verde **Run ▶** (o `Mayús + F10`).
3. La app se instala y se abre sola en el móvil.

Alternativa por terminal (con el móvil conectado):

```bash
./gradlew installDebug
```

Si el móvil no aparece: cambia de cable o de puerto, desconecta y vuelve a aceptar el aviso de depuración, o en *Opciones de desarrollador* pulsa **Revocar autorizaciones de depuración USB** y vuelve a conectar.

### Instalar la APK sin cable

Copia `app/build/outputs/apk/debug/app-debug.apk` al móvil (por ejemplo, por Drive), ábrela y acepta «Instalar apps de origen desconocido» para esa app. Es una APK de depuración: sirve para probar, no para publicar en Google Play.

## Ubicación física («Dónde lo tengo»)

Cada tomo que tienes puede llevar una nota libre con su sitio: «Caja 3», «Estante de arriba, segunda fila»…

- Se escribe al crear el libro o en la ficha del tomo (*Editar ISBN, ubicación y notas*). Solo aparece si el tomo está marcado como «Lo tengo»; al desmarcarlo se borra.
- Se ve en la lista de tomos de la ficha (icono de chincheta).
- La búsqueda de Biblioteca la incluye: escribe «caja 3» y verás arriba «Guardado en «caja 3»» con los tomos que están ahí.
- No tiene nada que ver con las colecciones: la colección agrupa series; la ubicación es el sitio físico.

## Lista de comprobación en el móvil

1. **Añadir**: pestaña + → elige Manga → título → Guardar. Se abre la ficha.
2. **Siguiente tomo**: en la ficha, «Añadir siguiente tomo» → sale el número 2 → «Añadir tomo».
3. **Cambios rápidos**: en la lista de tomos toca el estado («Sin leer») y elige «Leyendo»; desmarca «Lo tengo» en otro → aparece en Inicio › Pendientes de compra.
4. **Ubicación**: toca un tomo → «Dónde lo tengo» = «Caja 3» → Guardar. En Biblioteca busca «caja» → aparece arriba.
5. **Portada por foto**: en la ficha, «Hacer foto de la portada» → acepta el permiso → haz la foto. Se ve en Inicio, Biblioteca y la ficha. «Quitar» vuelve a la generada.
6. **Escáner**: Inicio › Escanear ISBN → «Usar la cámara» → apunta al código de barras de un libro → revisa → Guardar. Prueba también con el modo avión: debe avisar y ofrecer «Escribir los datos a mano».
7. **Colecciones**: en la ficha, icono de colecciones → «Nueva colección» → Favoritos. En la pestaña Colecciones aparece con 1 serie.
8. **Editar / borrar**: en la ficha, lápiz → cambia el autor → Guardar; luego la papelera → confirmar.
9. **Persistencia**: cierra la app del todo y vuelve a abrirla: todo sigue ahí.
10. **Girar el móvil** en un formulario a medio escribir: no se pierde lo escrito.

11. **Copia de seguridad**: Perfil › Ajustes › «Exportar copia de seguridad» → Guardar. Luego «Importar copia de seguridad», elige el .zip → sale el resumen → «Cancelar» no cambia nada.
12. **Lista de deseos**: en un tomo, desmarca «Lo tengo», ponle prioridad alta y una nota → Inicio › «Lista de deseos» → «Lo he comprado» → escribe la ubicación.
13. **Préstamos**: abre un tomo que tengas → «Prestar» → nombre y fecha → aparece «Prestado a…» en la ficha y en Perfil › Préstamos; «Marcar como devuelto» lo pasa a Historial.
14. **Estado editorial**: lápiz de la ficha → «En publicación» y «Tomos publicados» = 10 → la ficha muestra «Tengo X de 10 publicados»; prueba los filtros nuevos de Biblioteca.
15. **Informe PDF**: Perfil › Ajustes › «Crear informe PDF» → elige opciones → Guardar → ábrelo con el visor de PDF.

## Base de datos y migraciones

| Versión | Qué añade | Migración |
|---|---|---|
| 1 | series, tomos, colecciones | — |
| 2 | lista de deseos en los tomos | `MIGRATION_1_2` |
| 3 | tabla de préstamos | `MIGRATION_2_3` |
| 4 | estado editorial de las series | `MIGRATION_3_4` |

Nunca se usa migración destructiva. `MigrationTest` crea una base de datos real de la versión 1 (con el esquema de `app/schemas/…/1.json`), la rellena y la abre con la app actual: Room aplica 1→2→3→4 y comprueba el esquema final.

**Actualizar del 1 al 2.0 sin perder datos**: instala encima, sin desinstalar (Run ▶ en Android Studio o `./gradlew installDebug`). Antes, por si acaso, haz una copia en Perfil › Ajustes › «Exportar copia de seguridad».

## Qué se ha probado y qué no

**Probado**
- Compila (`assembleDebug`, versión 2.0) y pasan las 50 pruebas automáticas.
- En el móvil del usuario (Realme RMX3370), instalando encima de la versión 1 sin desinstalar: migraciones 1→2→3→4 sin errores; exportar copia con el selector del sistema; serie que no tengo con «Me faltan hasta el tomo 4»; lista de deseos y «Lo he comprado» con ubicación; prestar y etiqueta «Prestado a…» con ubicación; «Tengo 1 de 6 publicados»; informe PDF guardado y revisado (portada con logo y totales, listado, lista de deseos y préstamos); cancelar el selector al importar no cambia nada.
- Antes de instalar se sacó una copia de los datos del móvil al PC (`Desktop/Shelfio-copia-movil-…`).
- En emulador (versión 1): crear, editar, borrar, persistencia, ficha, siguiente tomo, foto de portada, colecciones, búsqueda por ISBN y ubicación.

**Pendiente de probar en el móvil**
- Importar una copia de verdad desde el móvil (está probado con pruebas automáticas, no a mano en el móvil).
- Leer un código de barras real con la cámara.
- Retrasados (hace falta un préstamo con fecha pasada), rotación y letra grande en las pantallas nuevas.

**Limitaciones conocidas**
- Google Books sin clave a veces responde «cuota excedida»; entonces solo se usa Open Library.
- El informe PDF pesa unos 600 KB aunque la biblioteca sea pequeña: la mayor parte es la fuente que Android incrusta en el PDF.

## Recursos gráficos

- `res/drawable/logo_shelfio.png`: logotipo aprobado (copia de `docs/diseno/logo_aprobado.png`, renombrado para cumplir las reglas de nombres de Android). Se muestra en Perfil › Acerca de y en la portada del informe PDF.
- `res/drawable/ic_launcher_foreground.xml`: versión vectorial del logotipo aprobado (libros en la balda, sin círculo) para el icono adaptativo del launcher y la cabecera de Inicio.
- `docs/diseno/`: lámina de concepto, identidad visual y variantes descartadas. Solo referencia; no se usan dentro de la app. La app no incluye portadas reales de obras con copyright ni cuentas de usuario.

## Fuera de alcance

Varias ediciones de una misma serie, sincronización en la nube (la copia de seguridad cubre el cambio de móvil) e iOS.
