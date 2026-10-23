# Libro MPAZ — Proyecto Grupo 07 (Sección DSY1105-005, Maipú)

Proyecto Android desarrollado para el caso de Vinculación con el Medio **"Libro Digital de Clases Interactivo 'Libro MPAZ' - Mpazbook"** de la Escuela Escritora Marcela Paz.

## Integrantes del Equipo
*   Katerine Olmedo Mella
*   Matias Nicolas Perez Cañete
*   Cesar Agustin Salvo Urra

## Enlace al Repositorio
*   **GitHub:** https://github.com/fanfisco666/Desarrollo_Movil

---

## Requerimiento Implementado (RF-06)
El foco principal de nuestro equipo fue el **RF-06: Asociar OA y contenidos a cada sesión/bloque lectivo**.

Para que este requerimiento funcione a nivel de producción y cumpla con la rúbrica, implementamos:
1.  **Arquitectura MVVM:** El flujo viaja desde la pantalla (`RegistroClaseScreen`) → `LibroMpazViewModel` → `SesionRepository` → `SesionDao` → **Room**.
2.  **Persistencia Local (Room):** Las clases guardadas sobreviven al cierre de la aplicación.
3.  **Reglas de Negocio Aplicadas:**
    *   **RN-02 (Auditoría):** Cada vez que se guarda una clase, se inserta un registro en la tabla `auditoria` en la misma transacción, indicando quién lo hizo y cuándo.
    *   **RN-03 (Cargas docentes):** El sistema valida que el docente solo pueda guardar clases en las asignaturas/cursos que tiene asignados.
4.  **Validaciones:** El formulario valida que la fecha no sea futura, que el bloque no esté vacío, que se marque al menos un OA y que el contenido esté escrito.

## Recursos Nativos Utilizados (2)
1. **DatePickerDialog (Calendario de Android):** Utilizado en el Registro de Clases para seleccionar la fecha sin errores de tipeo.
2. **Vibración (Vibrator):** Se utiliza para dar feedback háptico al usuario. El teléfono vibra de forma corta cuando la clase se guarda con éxito,
y vibra dos veces (patrón de error) si hay algún problema de validación, mejorando la accesibilidad y experiencia de usuario.

---

## Cómo ejecutar
1. Abrir el proyecto en Android Studio.
2. Sincronizar Gradle (la primera vez necesita internet).
3. Ejecutar ▶ **Run 'app'**.
4. *Nota: Las demás pantallas (Asistencia, Calificaciones, Hoja de Vida) se encuentran en modo de simulación visual para no salir del alcance de nuestro requerimiento asignado, tal como indicó el docente.*

## Usuarios de prueba
Pueden usar el login real implementado o los botones de "Modo desarrollo":

| Perfil | Usuario | Clave |
| :--- | :--- | :--- |
| Docente (Matemática 3°A y 4°A) | docente.mat@libromp.cl | docente123 |
| Docente (Lenguaje 4°A) | docente.len@libromp.cl | docente123 |
| UTP | utp@libromp.cl | utp123 |
| Inspectoría | inspectoria@libromp.cl | inspectoria123 |
| Dirección | direccion@libromp.cl | direccion123 |

## ¿Cómo probar nuestro requerimiento (RF-06)?
1. Ingresar con el perfil de **Docente**.
2. Entrar al curso **3° Básico A - Matemática**.
3. Tocar en **"Registro de clase (OA y contenidos)"**.
4. Probar las validaciones (dejar el bloque vacío y guardar, no marcar OA, etc.).
5. Usar el **calendario nativo** para seleccionar la fecha.
6. Llenar los datos correctamente y guardar. Verán un mensaje de éxito (Snackbar).
7. Cerrar la aplicación por completo, volver a abrirla y entrar al mismo curso. La clase guardada seguirá ahí.
8. Ingresar como **Dirección** → **Auditoría** para ver el registro de la clase guardada.

## Restricciones respetadas
*   No hay acceso para estudiantes ni apoderados.
*   Datos 100% ficticios (Ley 19.628 y 21.719).
*   No se utilizan sensores (GPS, cámara, micrófono).
*   No se envían notificaciones ni mensajes.