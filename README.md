# Shelfio

Toda tu colección, en un solo lugar. Biblioteca personal de libros, mangas, cómics y otras publicaciones para Android.

Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose · CameraX + ML Kit. Los datos se guardan solo en el móvil: no hay cuentas ni servidor propio. La app funciona siempre en vertical.

## Qué hace

- **Inicio**: saludo, búsqueda, contadores (Libros, Mangas, Cómics, Otros si hay, Colecciones), botón «Añadir», préstamos activos, últimos añadidos, En lectura y Pendientes de compra (primero los de prioridad alta).
- **Añadir**: el «+» de abajo o «Añadir» en Inicio preguntan **qué quieres añadir** (Libro / novela, Manga / novela ligera, Cómic u Otro) y abren su formulario con un **buscador por título** que rellena la ficha. Todo se puede escribir o corregir a mano. Al crear una serie se crea su primer tomo.
- **Biblioteca**: búsqueda (título, autor, editorial, ISBN, ubicación y a quién se prestó), filtros por tipo y por estado de la colección (Completas, Me faltan, En publicación). Cada tarjeta enseña el estado editorial: «Original: …» y «Tu idioma: …» (solo los que estén rellenados).
- **Ficha de serie**: portada, datos, otros títulos, estado editorial, progreso y lista de tomos (deslizar un tomo a la derecha lo edita y a la izquierda lo borra; mantenerlo pulsado también lo borra). «Lo tengo» y estado de lectura con un toque, «Añadir siguiente tomo», «Añadir los tomos que me faltan», portada (foto o galería) y colecciones. Si solo hay un tomo se ve como un libro normal. El tipo se puede cambiar en Editar (por ejemplo, de Otro a Cómic).
- **Tomo** (en libros se llama «volumen»): número, título, ISBN (con cámara), lo tengo, estado de lectura, ubicación física, prioridad y nota de compra si no lo tengo, préstamo y portada propia.
- **Lista de deseos**: todos los tomos que no tienes, agrupados por serie, con búsqueda, orden por prioridad, fecha o título, y «Lo he comprado» (pregunta dónde lo guardas).
- **Préstamos**: en los dos sentidos. «Prestar» (lo que es tuyo) y «Me lo han prestado» (lo que no es tuyo), con «Marcar como devuelto». Pestañas He prestado, Me han prestado e Historial; retrasados en rojo y búsqueda por persona. Solo se guarda el nombre.
- **Colecciones**: agrupaciones tuyas («Favoritos», «Fantasía»…); una serie puede estar en varias.
- **Perfil**: estadísticas, accesos a Lista de deseos y Préstamos, y ajustes: nombre del saludo, clave propia de Google Books (opcional), **copia de seguridad** (exportar, compartir e importar .zip), **informe PDF** y, solo en la versión de pruebas, **datos de prueba** para cargar y borrar ejemplos.

### Portadas

Si no hay portada real, la app dibuja una con código: azul para libros, violeta para mangas, cian para cómics y gris azulado para «Otro». La portada real viene del resultado elegido en el buscador, de una foto («Hacer foto») o de la galería del móvil («Galería»). Se guarda en el móvil y se ve sin conexión. En una serie con tomos, la app pregunta si la imagen va «Solo portada» o en «Portada y tomos» (cada tomo recibe su propia copia). La portada del buscador se guarda aparte: «Quitar» una foto vuelve a ella, y si se quita también esa, vuelve a la generada. La foto de un tomo nunca pasa a ser la portada de la serie.

## Cómo se buscan los libros

1. Eliges el tipo en «¿Qué quieres añadir?».
2. El **buscador por título** pregunta a las fuentes de ese tipo y junta los resultados, sin repetidos y con su portada:

| Tipo | Fuentes | Clave |
|---|---|---|
| Libro / novela | Google Books + Open Library | Google: de la app (restringida a Android) o del usuario |
| Manga / novela ligera | AniList + MangaUpdates | No hacen falta |
| Cómic | Google Books + Open Library + Comic Vine | Comic Vine: de la app |
| Otro | Google Books + Open Library | Google |

3. Al tocar un resultado se rellena la ficha. Con AniList y MangaUpdates puedes **elegir el título principal** entre todos los que tiene la obra y **guardar otros** (inglés, original japonés o coreano…) en «Otros títulos»; también se pueden escribir a mano y la búsqueda los encuentra. MangaUpdates da autor, sinopsis y estado al elegirlo, pero no la editorial española. Comic Vine da la serie original (editorial, año, número de entregas), no la edición española ni el autor.
4. Lo que la fuente no trae se queda vacío: la app nunca inventa datos.
5. El campo **ISBN (Opcional)** tiene un botón de **cámara**: lee el código de barras (ML Kit) y lo escribe. Ya no se buscan datos por ISBN, porque Google Books fallaba demasiado con ediciones españolas.

**Claves**: las de Google Books y Comic Vine van en `local.properties`, que **no se sube a Git**:

```
GOOGLE_BOOKS_API_KEY=...
COMIC_VINE_API_KEY=...
```

Si clonas el proyecto en otro ordenador, añádelas ahí. La de Google está restringida en Google Cloud a `com.shelfio.app` y a la huella SHA-1 del certificado de depuración de este PC. Para una versión de publicación habrá que añadir la huella de su firma.

**Descartado y por qué**: Gemini con búsqueda de Google (para cuentas nuevas no tiene capa gratuita), leer webs como IberLibro o Listado Manga (sus normas para robots lo prohíben), ISBNdb (de pago) y Shortboxed (pensado para tiendas de EE. UU.).

## Arquitectura (Modelo · Vista · ViewModel)

Es el MVC de Android: el ViewModel hace de controlador. Flujo de datos en un solo sentido: la Vista muestra estado y avisa de acciones; el ViewModel decide; el Modelo guarda.

```
app/src/main/java/com/shelfio/app/
  ShelfioApplication.kt     crea una sola vez base de datos, repositorios y buscador
  MainActivity.kt           punto de entrada (solo pone el tema y la navegación)

  data/                     MODELO
    model/Models.kt         entidades Room: Series, Volume, ShelfCollection, SeriesCollectionCrossRef, Loan
    db/                     DAO (consultas SQL), base de datos Room y migraciones
    ShelfioRepository.kt    único acceso a los datos; borra portadas que dejan de usarse
    LibraryLogic.kt         funciones puras: búsqueda, ubicación, lista de deseos, préstamos, siguiente tomo…
    Isbn.kt                 validar y normalizar ISBN-10/13
    CoverStorage.kt         guardar/borrar portadas en el almacenamiento interno
    DemoData.kt             biblioteca de ejemplo (solo versión de pruebas)
    books/                  buscador por título (Google Books, Open Library, AniList, MangaUpdates, Comic Vine)
    backup/                 copia de seguridad .zip (formato versionado)
    report/                 informe PDF
    SettingsRepository.kt   ajustes con DataStore

  ui/                       VISTA + VIEWMODEL (un paquete por pantalla)
    MainViewModel.kt        estado de las pestañas principales
    AppViewModelProvider.kt fábrica única de ViewModels
    navigation/             rutas, NavHost y barra inferior
    add/                    ventana «¿Qué quieres añadir?» y formulario con buscador
    home/ library/ collections/ profile/ series/ volume/ wishlist/ loans/
    scan/                   lector de código de barras y campo ISBN con cámara
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
| Compose BOM | 2026.03.01 (Compose 1.10) | Último BOM que compila con compileSdk 36; los más recientes (p. ej. 2026.09.00) exigen compileSdk 37 |
| Room | 2.8.4 | Base de datos local |
| DataStore | 1.1.7 | Ajustes |
| CameraX | 1.5.3 | Cámara del lector de código de barras |
| ML Kit barcode-scanning | 17.3.0 | Lectura del código de barras (modelo incluido, sin conexión) |
| Coil (+ red OkHttp) | 3.3.0 | Mostrar portadas guardadas y miniaturas de los resultados |
| compileSdk / targetSdk | 36 | Plataforma instalada en el SDK local |
| minSdk | 26 | Android 8.0 o superior |
| JDK | el de Android Studio (jbr) | |

**Por qué ML Kit para el código de barras**: Google lo mantiene activamente (ZXing está en modo mantenimiento), es gratis, va con el modelo dentro de la app (sin Google Play Services y sin conexión) y CameraX tiene un adaptador oficial.

## Base de datos y migraciones

| Versión | Qué añade | Migración |
|---|---|---|
| 1 | series, tomos, colecciones | — |
| 2 | lista de deseos en los tomos | `MIGRATION_1_2` |
| 3 | tabla de préstamos | `MIGRATION_2_3` |
| 4 | estado editorial de las series | `MIGRATION_3_4` |
| 5 | otros títulos de la serie y préstamos «me lo han prestado» | `MIGRATION_4_5` |
| 6 | portada original del buscador (`originalCoverPath`) | `MIGRATION_5_6` |

Nunca se usa migración destructiva. `MigrationTest` crea una base de datos real de la versión 1 (con el esquema de `app/schemas/…/1.json`), la rellena y la abre con la app actual: Room aplica 1→2→3→4→5→6 y comprueba el esquema final. El tipo «Otro» no necesitó migración: el tipo se guarda como texto.

La copia de seguridad lleva `formatVersion` (ahora 6, que incluye la portada original del buscador) y sigue importando las copias de versiones anteriores.

## Abrir y compilar

1. Abre Android Studio → **File › Open** → elige la carpeta `Shelfio`.
2. Comprueba que `local.properties` tiene las claves (ver arriba).
3. Espera a que termine la sincronización de Gradle.
4. Desde terminal, en la carpeta del proyecto:

```bash
./gradlew assembleDebug
```

La APK (unos 46 MB en depuración) queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Pruebas automáticas

```bash
./gradlew testDebugUnitTest
```

52 pruebas: búsqueda y ubicación, progreso, pendientes, «añadir siguiente tomo», ISBN, lista de deseos, préstamos, estado editorial, informe PDF, elección de título y resultados repetidos del buscador, el repositorio contra Room en memoria (Robolectric), migraciones desde la versión 1 con datos reales y copias de seguridad (exportar → importar, archivos inválidos y copias de los formatos 1 a 4).

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

Copia la APK al móvil (por ejemplo, por Drive), ábrela y elige **Actualizar** (no desinstales: perderías tus datos). Acepta «Instalar apps de origen desconocido» para la app desde la que la abras. Es una APK de depuración: sirve para probar, no para publicar en Google Play.

## Lista de comprobación en el móvil

1. **Añadir**: «+» → Manga / novela ligera → busca «La caja azul» → toca un resultado → elige el título → Guardar. Se abre la ficha.
2. **ISBN con cámara**: en el formulario, icono de cámara del campo ISBN → apunta al código de barras → se escribe solo.
3. **Otro**: «+» → Otro → escribe un título → Guardar. Después, lápiz de la ficha → cambia el tipo a Cómic.
4. **Siguiente tomo**: en la ficha, «Añadir siguiente tomo» → sale el número 2 → «Añadir tomo».
5. **Cambios rápidos**: en la lista de tomos toca el estado y elige «Leyendo»; desmarca «Lo tengo» en otro → aparece en Inicio › Pendientes de compra.
6. **Ubicación**: abre un tomo → «Dónde lo tengo» = «Caja 3» → Guardar. En Biblioteca busca «caja».
7. **Portada**: en la ficha de un manga, «Galería» → elige una imagen → «Portada y tomos» → todos los tomos la tienen. En una serie añadida con el buscador, pon una foto y pulsa «Quitar»: vuelve la portada del buscador.
8. **Lista de deseos**: en un tomo que no tengas, prioridad alta y una nota → Inicio › «Lista de deseos» → «Lo he comprado» → escribe la ubicación.
9. **Préstamos**: abre un tomo que tengas → «Prestar» → nombre y fecha → aparece «Prestado a…»; «Marcar como devuelto» lo pasa a Historial.
10. **Estado editorial**: lápiz de la ficha → «En publicación» y «Tomos publicados» = 10 → «Tengo X de 10 publicados»; prueba los filtros de Biblioteca.
11. **Colecciones**: en la ficha, icono de colecciones → «Nueva colección».
12. **Copia de seguridad**: Perfil › Ajustes › «Exportar copia de seguridad» → Guardar; «Importar» → elige el .zip → sale el resumen.
13. **Informe PDF**: Perfil › Ajustes › «Crear informe PDF» → elige opciones → Guardar → ábrelo con el visor de PDF.
14. **Persistencia**: cierra la app del todo y vuelve a abrirla: todo sigue ahí.
15. **Datos de prueba** (solo versión de pruebas): Perfil › «Cargar datos de prueba» → 14 ejemplos (4 libros, 4 mangas, 3 cómics, 3 otros) con préstamos al día, vencido y «me lo han prestado», lista de deseos, ubicaciones y estados editoriales. Van a la colección «Datos de prueba»; «Borrar datos de prueba» los quita sin tocar lo demás.

## Qué se ha probado y qué no

**Probado en el móvil del usuario (Realme RMX3370)**
- Migraciones 1→2→3→4→5→6 instalando encima, sin perder datos (antes se sacó una copia de los datos al PC).
- Exportar copia, lista de deseos, «Lo he comprado», préstamos, «Tengo X de Y publicados» e informe PDF (revisado en el PC).
- Ventana «¿Qué quieres añadir?», buscador de manga (AniList y MangaUpdates, eligiendo título) y de cómic (Google Books y Comic Vine).
- Botón «Quitar» de la portada con letra grande y app fija en vertical.
- Deslizar tomos (editar/borrar), estado editorial en las tarjetas, datos de prueba, portada desde la galería en serie y tomos, y «Quitar» volviendo a la portada del buscador.

**Pendiente de probar a mano**
- Leer un código de barras real con el botón de cámara del campo ISBN.
- Importar una copia desde el móvil (probado con pruebas automáticas).
- Un préstamo retrasado y el tipo «Otro» de principio a fin.

**Limitaciones conocidas**
- Google Books falla a ratos (errores 503); la app reintenta y, si no responde, enseña lo que hayan dado las demás fuentes.
- Muchos mangas y cómics editados en España no están en ninguna fuente con sus datos españoles (editorial, tomos en España): esos campos se escriben a mano.
- Con MangaUpdates salen muchos títulos para elegir, en todos los idiomas.
- El informe PDF pesa unos 600 KB aunque la biblioteca sea pequeña: es la fuente que Android incrusta en el PDF.

## Recursos gráficos

- `res/drawable/logo_shelfio.png`: logotipo aprobado (copia de `docs/diseno/logo_aprobado.png`, renombrado para cumplir las reglas de nombres de Android). Se muestra en Perfil › Acerca de y en la portada del informe PDF.
- `res/drawable/ic_launcher_foreground.xml`: versión vectorial del logotipo aprobado (libros en la balda, sin círculo) para el icono adaptativo del launcher y la cabecera de Inicio.
- `docs/diseno/`: lámina de concepto, identidad visual y variantes descartadas. Solo referencia; no se usan dentro de la app. La app no incluye portadas reales de obras con copyright ni cuentas de usuario.

## Pendiente y fuera de alcance

- **Más adelante**: campos distintos en el formulario según el tipo.
- **Fuera de alcance**: varias ediciones de una misma serie, sincronización en la nube (la copia de seguridad cubre el cambio de móvil) e iOS.
