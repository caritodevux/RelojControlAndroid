# Reloj Control IoT - Credencial Digital Móvil

Un sistema de control de asistencia de personal moderno, seguro y conforme a la normativa vigente de **Protección de Datos Personales (Ley N° 21.713)**, diseñado como alternativa a los sistemas biométricos tradicionales (huella o reconocimiento facial).

---

## Descripción del Proyecto

El proyecto reemplaza las antiguas tarjetas de papel y el registro biométrico por una **credencial digital cifrada en el smartphone del trabajador**. La aplicación móvil se conecta mediante **Bluetooth Clásico (SPP)** a un **tótem físico IoT impulsado por Arduino**, registrando la asistencia de forma inmediata y destrabando un torniquete de acceso.

---

## Arquitectura del Sistema

### 1. Capa de Gestión Móvil (Android / Kotlin)
* **Interfaz Dinámica (XML):** Maquetada en Figma e integrada en Android Studio mediante `ConstraintLayout`.
* **Navegación y Estados:** Transición de pantallas con `Intents` (`MainActivity` ➔ `ResultadoMarcajeActivity`) y gestión segura de estados runtime (`Bundle`).
* **Conectividad Bluetooth SPP:** Socket de comunicación en bytes hacia el módulo HC-05 con manejo de *timeouts* y permisos de Android 12+ (`BLUETOOTH_CONNECT`).
* **Ciberseguridad:** Confirmación previa de marcaje (`AlertDialog`) bajo el principio de mínimo privilegio.

### 2. Capa Física y Lógica (Arduino / C++)
* **Unidad Central:** Arduino Uno con lectura de tramas seriales a 9600 baudios.
* **Pantalla LCD 16x2:** Retroalimentación visual inmediata al usuario (*MARCAJE OK / ERROR*).
* **Indicadores Físicos:** LED Verde, LED Rojo y Buzzer con frecuencias de tono diferenciadas.
* **Actuador:** Servomotor a 90° simulando el destrabe de puerta/torniquete por 3 segundos.
* **Redundancia Fail-Safe:** Teclado matricial 4x4 integrado para ingreso manual de PIN de respaldo en caso de agotamiento de batería o fallas móviles.

---

## Estándares y Cumplimiento
* **Protección de Datos:** Cero almacenamiento de datos biométricos sensibles.
* **Ciberseguridad OT/IIoT:** Diseñado bajo los lineamientos de la norma **ISO/IEC 27400** (control de conexiones no autorizadas, gestión de recursos en `onDestroy()` y continuidad operacional).
