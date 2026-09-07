# Plan de Implementación: Control de Zoom para FocusTo

Este plan describe los cambios necesarios para añadir la funcionalidad de aumentar y disminuir el zoom en la visualización de documentos PDF.

## User Review Required

> [!IMPORTANT]
> El zoom se implementará mediante botones (+ y -) que cambiarán el nivel de renderizado del PDF. Esto asegura que el texto se mantenga nítido al ampliar, en lugar de simplemente escalar una imagen de baja resolución.

## Proposed Changes

### Interfaz de Usuario (Recursos)

#### [MODIFY] [strings.xml](file:///C:/Users/Yadira%20C/StudioProjects/Proyecto-Adaptativo/app/src/main/res/values/strings.xml)
- Añadir etiquetas para los botones de zoom: `btn_zoom_in` (+) y `btn_zoom_out` (-).

#### [MODIFY] [activity_main.xml](file:///C:/Users/Yadira%20C/StudioProjects/Proyecto-Adaptativo/app/src/main/res/layout/activity_main.xml)
- Añadir un contenedor flotante o una sección en la barra inferior para los botones de zoom.
- Se colocarán entre los botones de navegación de página o en una fila superior dentro de `layoutPagination`.

### Lógica de la Aplicación

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Yadira%20C/StudioProjects/Proyecto-Adaptativo/app/src/main/java/com/example/focusto/MainActivity.kt)
- **Nueva variable:** `currentZoomLevel` (Float) inicializada en 2.0f.
- **Rango de zoom:** Definir límites (mínimo 1.0f, máximo 6.0f).
- **Actualizar `renderPage`:** Usar `currentZoomLevel` como multiplicador del Bitmap.
- **Listeners:** Configurar los clics de los nuevos botones para incrementar/decrementar el zoom en pasos de 0.5f y volver a renderizar la página actual.

## Verification Plan

### Manual Verification
1. Abrir un PDF.
2. Presionar el botón "+" y verificar que el contenido se ve más grande y nítido.
3. Presionar el botón "-" y verificar que el contenido se reduce.
4. Verificar que el zoom funciona correctamente tanto en modo normal como en modo nocturno.
5. Comprobar que los límites de zoom (mínimo y máximo) funcionan.
