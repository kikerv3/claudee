# DirSpace

Analizador de almacenamiento para Android al estilo **WinDirStat**: dibuja un
mapa de bloques (*treemap*) donde cada archivo es un rectángulo con área
proporcional a lo que ocupa y color según su tipo, de modo que los devoradores
de espacio saltan a la vista sin tener que abrir carpeta por carpeta.

App nativa en Kotlin + Jetpack Compose, sin dependencias de terceros ni acceso a
red: los nombres y tamaños de tus archivos nunca salen del teléfono.

## Qué trae

- **Mapa** — treemap *squarified* con sombreado tipo *cushion*, el mismo que
  hace legible el de WinDirStat. Toca un bloque para ver el archivo, mantén
  pulsado para entrar en la carpeta.
- **Carpetas** — listado ordenable por tamaño, nombre, fecha o número de
  archivos, con barra de proporción y navegación por migas de pan.
- **Tipos** — reparto del espacio por categoría (vídeo, imagen, audio…) y el
  desglose completo extensión por extensión.
- **Limpieza** — encuentra lo que sobra y lo borra en bloque: temporales,
  miniaturas, papeleras, registros, carpetas vacías, restos de apps
  desinstaladas, instaladores APK, archivos grandes que llevan medio año sin
  tocarse y duplicados idénticos byte a byte. Cada categoría lleva su nivel de
  riesgo: sólo se marcan solas las que el sistema regenera, y el botón de
  limpieza rápida borra exactamente esas.
- **Grandes** — los 250 archivos más pesados del volumen, estén donde estén.
- **Apps** — cuánto ocupa cada aplicación (APK + datos + caché). El escaneo de
  archivos no puede ver `/data/data`, así que este dato viene de
  `StorageStatsManager` y requiere conceder "Acceso a datos de uso".
- Borrar, abrir y compartir desde la propia app, con el árbol actualizándose en
  memoria para no tener que reescanear.
- Interno y tarjeta SD, tema claro/oscuro con colores dinámicos (Android 12+),
  español e inglés.

## Instalar

Descarga el APK desde la [última release](https://github.com/kikerv3/claudee/releases/latest)
y ábrelo en el teléfono. Requiere Android 8.0 o superior.

Los APK publicados van firmados con la clave de depuración de Android: sirven
para instalar de forma manual, no para publicar en Google Play.

## Permisos

| Permiso | Para qué | Cómo se concede |
|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Recorrer todo el almacenamiento (Android 11+) | Pantalla de Ajustes "Acceso a todos los archivos" |
| `READ_EXTERNAL_STORAGE` | Lo mismo en Android 10 y anteriores | Diálogo de permisos normal |
| `PACKAGE_USAGE_STATS` | Tamaño de cada app instalada | Ajustes → Acceso a datos de uso (opcional) |

Sin el permiso de datos de uso todo funciona salvo la pestaña *Apps*.

## Compilar

Requiere JDK 17 y el SDK de Android (API 35).

```bash
./gradlew assembleDebug      # APK en app/build/outputs/apk/debug/
./gradlew installDebug       # instala en el dispositivo conectado
./gradlew testDebugUnitTest  # tests del algoritmo de treemap y los formateadores
```

Hay dos workflows de GitHub Actions: `android.yml` compila el APK de debug y
corre los tests en cada push, y `release.yml` publica los APK como release
cuando cambia el fichero `VERSION`, que es también de donde sale el
`versionName` de la app.

- `minSdk` 26 (Android 8.0) · `targetSdk` 35 (Android 15)

## Cómo está organizado

```
model/      FsNode (árbol de archivos) y categorías por extensión
scan/       recorrido del volumen, detección de volúmenes, estadísticas
clean/      reglas de limpieza y detección de duplicados
apps/       tamaño por aplicación vía StorageStatsManager
ui/treemap/ algoritmo squarified + rasterizado del mapa
ui/screens/ permisos, volúmenes, escaneo y las cinco pestañas
util/       formateo de tamaños y operaciones de archivo
```

Dos decisiones que explican casi todo el código:

**El árbol se guarda en memoria sin rutas.** Un teléfono lleno tiene cientos de
miles de archivos; guardar la ruta completa en cada nodo cuesta decenas de MB.
`FsNode` guarda sólo el nombre y el padre, y reconstruye la ruta subiendo por la
cadena cuando hace falta.

**Lo que se borra en bloque tiene que ser regenerable.** Las categorías de
limpieza se dividen en dos niveles de riesgo. Sólo llegan marcadas las que el
sistema o las apps vuelven a crear cuando hacen falta, y la limpieza rápida
borra exactamente ese conjunto. Por eso `.bak` y `.old` no cuentan como
temporales, y una carpeta sólo se trata como papelera si su nombre no puede
haberlo elegido una persona para guardar sus cosas. Los duplicados se comparan
leyendo el contenido completo, no sólo el tamaño y la cabecera, y de cada grupo
idéntico nunca se ofrece la última copia.

**El mapa se rasteriza una vez, no en cada frame.** Un volumen grande genera
miles de rectángulos; pintarlos en cada frame haría el scroll inservible. El
`TreemapRenderer` los vuelca sobre un `ImageBitmap` fuera del hilo principal y
la pantalla sólo dibuja esa imagen más el recuadro de selección.
