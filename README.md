# Nexo Maps

Primera base de la aplicación Android Nexo Maps.

## Incluye
- Proyecto Android nativo en Kotlin.
- Mapa interactivo mediante MapLibre.
- Estilo de mapa de demostración de MapLibre.
- Flujo de GitHub Actions para generar un APK de depuración.

## Generar el APK
1. Abre la pestaña **Actions** del repositorio.
2. Selecciona **Construir APK de Nexo Maps**.
3. Pulsa **Run workflow** si no se inició automáticamente.
4. Espera a que termine la compilación.
5. Abre la ejecución y descarga el artefacto **Nexo-Maps-debug**.

## Próximas funciones
Búsqueda geográfica, ubicación GPS con permiso del usuario, rutas y navegación, y después visualización 3D de avatares y vehículos.

El mapa de demostración requiere conexión a Internet. Para una publicación real se deberán revisar el proveedor de mapas, atribuciones, límites de uso y servicios de geocodificación/rutas.
