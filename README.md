# Shelfio

Toda tu colección, en un solo lugar. Biblioteca personal de libros, mangas y cómics para Android.

Kotlin · Jetpack Compose · Material 3 · Navigation Compose. Los datos se guardan solo en el móvil.

## Versiones usadas

| Herramienta | Versión | Motivo |
|---|---|---|
| Gradle | 9.3.1 | Ya instalada en este equipo por Android Studio |
| Android Gradle Plugin | 9.1.0 | Estable, compatible con Gradle 9.3 (Kotlin integrado en AGP 9) |
| Kotlin / plugin Compose | 2.3.0 | Estable, compatible con AGP 9.1 |
| Compose BOM | 2026.03.01 (Compose 1.10) | Último BOM que compila con compileSdk 36; BOM más recientes (p. ej. 2026.09.00) exigen compileSdk 37 |
| compileSdk / targetSdk | 36 | Plataforma instalada en el SDK local |
| minSdk | 26 | Android 8.0 o superior |
| JDK | el de Android Studio (jbr) | |

## Abrir y compilar

1. Abre Android Studio → **File › Open** → elige la carpeta `Shelfio`.
2. Espera a que termine la sincronización de Gradle (la primera vez descarga dependencias).
3. Desde terminal, en la carpeta del proyecto:

```bash
./gradlew assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Probar en tu móvil Android por USB

### 1. Activar las opciones de desarrollador
1. En el móvil: **Ajustes › Información del teléfono** (en algunos móviles: *Acerca del teléfono › Información de software*).
2. Toca **7 veces seguidas** sobre **Número de compilación**. Si pide el PIN, ponlo.
3. Aparece el mensaje «Ya eres desarrollador».

### 2. Activar la depuración USB
1. **Ajustes › Sistema › Opciones de desarrollador** (en Samsung/Xiaomi aparece al final de Ajustes).
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

## Estructura

```
app/src/main/java/com/shelfio/app/
  MainActivity.kt        punto de entrada
  ui/theme/              colores, tipografía y tema
  ui/navigation/         rutas y NavHost
  ui/screens/            pantallas
  data/                  datos (a partir de la Fase 2)
docs/diseno/             imágenes del anexo de diseño (referencia)
```

## Recursos gráficos

- `res/drawable/logo_shelfio.png`: logotipo aprobado (copia de `docs/diseno/logo_aprobado.png`, renombrado para cumplir las reglas de nombres de Android).
- `res/drawable/ic_launcher_foreground.xml`: versión vectorial del logotipo aprobado (libros en la balda, sin círculo) para el icono adaptativo del launcher.
- `docs/diseno/`: lámina de concepto, identidad visual y variantes descartadas. Solo referencia; no se usan dentro de la app.

## Ubicación física («Dónde lo tengo»)

Cada tomo que tienes puede llevar una nota libre con su sitio: «Caja 3», «Estante de arriba, segunda fila»…

- Se escribe al crear el libro o en la ficha del tomo (*Editar ISBN, ubicación y notas*). Solo aparece si el tomo está marcado como «Lo tengo»; al desmarcarlo se borra.
- Se ve en la lista de tomos de la ficha (icono de chincheta).
- La búsqueda de Biblioteca la incluye: escribe «caja 3» y verás arriba «Guardado en «caja 3»» con los tomos que están ahí.
- No tiene nada que ver con las colecciones: la colección agrupa series; la ubicación es el sitio físico.
