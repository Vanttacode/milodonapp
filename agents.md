# AGENTS.MD - Especificaciones Mecatrónicas y de Entorno para el Proyecto Milodón VMC

Este documento contiene las restricciones críticas de hardware, software, dependencias y arquitectura del sistema VMC (Vending Machine Controller) para guiar de manera autónoma las tareas de análisis, refactorización y compilación realizadas por agentes de IA.

---

## 1. ESPECIFICACIONES DEL ENTORNO OBJETIVO (TARGET HARDWARE)
Cualquier modificación de código o sugerencia arquitectónica debe ser compatible con la siguiente plataforma embebida industrial:

- **Placa Base (SBC):** Rockchip RK3288.
- **Sistema Operativo:** Android 5.1.1 (Lollipop / API Level 22).
- **Kernel:** 3.10.0 (Firmware `eng.root` con privilegios Root habilitados de fábrica).
- **Arquitectura de CPU:** ARM 32-bit (`armeabi-v7a`). Las herramientas de simulación x86 o x86_64 deben aislar las llamadas JNI.
- **Lienzo de Pantalla:** Pantalla táctil configurada fijamente en modo Portrait (Vertical) con una relación física rígida de desarrollo de 48x26 cm (Simulado en emulador mediante resoluciones estiradas equivalentes).

---

## 2. SUBSISTEMA MECATRÓNICO A: MATRIZ DE MOTORES (WUYI PROTOCOL)
El control físico de las espirales se realiza mediante una placa de potencia esclava asiática (tipo Wuyi) conectada al puerto serie de la SBC.

### Restricciones Críticas del Driver (`MotorManager.java`):
- **Puerto del Sistema:** `/dev/ttyS3`
- **Velocidad de Comunicación (Baud Rate):** 9600 bps de forma estricta (no alterar a 38400 bps).
- **Enlace Nativo:** Se comunica mediante un puente JNI (`libserial_port.so`).
- **Poka-Yoke Anti-Colisión de Buffer:** La placa Wuyi requiere un retardo imperativo de **111ms** entre ráfagas de datos enviados (`sendGapMs = 111`). Cualquier intento de eliminar o reducir este `Thread.sleep()` o delay asíncrono colapsará el buffer serie de la máquina.
- **Dialecto del Protocolo:** No se deben enviar caracteres de escape clásicos como `\r\n`. Los comandos se encapsulan de forma limpia en texto plano cerrado por paréntesis. El formato del SKU mapeado en la base de datos se traduce de la siguiente manera al comando físico:
  - Formato: `(NOAD:0-|NUM:XY0|ID:HashHex)` donde `X` es la Fila (1-6) e `Y` es la Columna/Espiral (0-9).
- **Manejo de Tiempos de Espera (Timeout):** El ciclo de giro completo de una espiral dura como máximo 6 segundos. El código debe implementar un Watchdog de resguardo de 6000ms (`MOTOR_TIMEOUT_MS = 6000`). Si el microswitch físico de la bandeja falla en reportar el pulso de corte, el sistema debe abortar la corriente por seguridad.

---

## 3. SUBSISTEMA MECATRÓNICO B: INFRAESTRUCTURA DE PAGO (PAYSCAN + NAYAX AMIT 3.0 + PAX IM20)
El cobro digital y la telemetría operan integrados en un único lazo cerrado industrial a través del bus MDB, consolidando los periféricos como un paquete unificado que centraliza su comunicación hacia la tablet.

### Topología física y Telemetría:
- **Ecosistema unificado:** El lector POS físico de cara al usuario es el **PayScan**. Este terminal se interconecta directamente con el router/telemetría **Nayax AMIT 3.0** y el conector/pasarela **PAX (Módulo CM20/IM20)**. 
- **Salida Única MDB:** Toda la lógica de cobro del mazo se concentra en un conector de plástico blanco de 6 pines (estándar MDB).
- **Conversor del Bus:** La salida MDB se conecta a la entrada hembra del adaptador MDB-to-USB (Waferstar / Qibixx).
- **Ruta de Conexión a la Tablet:** El cable USB del adaptador ingresa a la placa Android RK3288, siendo mapeado por el sistema bajo la ruta `/dev/ttyACM0`.

### Configuración del Driver (`MDBManager.java` / `PaymentManager.java`):
- **Parámetros del Puerto Virtual:** Operado a través de la librería `usb-serial-for-android` con la configuración:
  - Baud Rate: 9600 bps.
  - Data bits: 8.
  - Parity: None.
  - Stop bits: 1.
- **Protocolo Lógico:** Ejecución asíncrona en hilos de fondo de comandos secuenciales de `RESET`, `SETUP` y un bucle continuo de `POLLING` (emitido estrictamente cada 800ms) para mantener despierta y habilitada la sesión Cashless (Nivel MDB 3).
- **Restricción de Voltaje Operativo:** El bus MDB requiere obligatoriamente una fuente de **24V DC (2A a 3A)** inyectada al adaptador para energizar correctamente el procesador y los módems de telemetría. La placa Android RK3288 toma energía en paralelo mediante una derivación hacia un **regulador step-down plateado ajustado estrictamente a 12V DC** (inyectar 24V directos causará destrucción catastrófica de la tablet).

---

## 4. CAPA DE PRESENTACIÓN (UI 100% NATIVA ANDROID)
El Proyecto Milodón descarta cualquier uso de contenedores híbridos o WebViews. La interfaz gráfica se ejecuta de manera local y pura mediante vistas nativas de Android optimizadas para rendimiento en hardware embebido antiguo.

- **Estructura Base:** `MainActivity.java` maneja la interfaz principal y los ciclos de navegación mediante componentes nativos como `GridView`.
- **Diseño Visual (Estética Milodón):** Interfaz basada en un tema oscuro absoluto "Premium Dark / Pitch Black" (`#000000`) aplicado mediante recursos en `res/values/styles.xml` (heredando de variantes oscuras sin barra de acciones) para mitigar la fatiga lumínica del panel y evitar parpadeos blancos durante el arranque.
- **Carga Dinámica de Assets:** El logotipo (`logo.png`) se aloja localmente en `res/drawable/`. Para evitar desbordamientos de memoria (OutOfMemory) en el hardware RK3288, se prohíbe el escalado libre en layouts; las imágenes y loaders profesionales deben tener dimensiones delimitadas explícitamente (`android:layout_height` controlado).
- **Poka-Yoke Gráfico Táctil:** Todo elemento interactivo o botón en pantalla debe poseer un padding extendido (mínimo de 24dp) y una separación mínima inter-botón de 16dp para evitar colisiones dactilares. 
- **Feedback Industrial:** Queda prohibido el uso de estados flotantes (`hover`). Es mandatorio el uso de archivos selectores drawables nativos (`<selector>`) que escuchen de forma reactiva el estado presionado (`android:state_pressed="true"`) para inyectar un cambio de color inmediato ante la pulsación del dedo.

---

## 5. CAPA DE DATA (SUPABASE INTEGRACIÓN DIRECTA)
- **Proveedor de Backend:** Supabase (PostgreSQL).
- **Aislamiento Multi-Tenant (PGRST106):** No utilizar el esquema `public`. Toda la lógica de inventarios, máquinas y transacciones reside estrictamente en el esquema personalizado `milodon`.
- **Regla para Clientes HTTP:** Al interactuar con la API REST de Supabase mediante el cliente HTTP de la app (Retrofit/OkHttp), es mandatorio inyectar mediante un Interceptor el Header HTTP `Accept-Profile: milodon`. De lo contrario, el motor PostgREST devolverá un error de tabla no encontrada.

---

## 6. SCRIPT DE CONFIGURACIÓN DEL ENTORNO PARA JULES (CI/CD VM SETUP)
Para validar la compilación, ejecutar análisis estáticos del código (linters) o correr pruebas unitarias dentro de la máquina virtual de Jules, se requiere preparar las dependencias de Android y inyectar las constantes en Gradle.

Utilizar los siguientes comandos de inicialización:

```bash
# ==============================================================================
# SECCIÓN DE CONFIGURACIÓN INICIAL PARA EL AGENTE JULES
# ==============================================================================

# 1. Inyectar propiedades locales falsas para evitar que Gradle falle por falta de variables productivas
echo "SUPABASE_URL=[https://mock-milodon.supabase.co](https://mock-milodon.supabase.co)" > local.properties
echo "SUPABASE_KEY=mock-api-key-value-for-agent-testing-12345" >> local.properties

# 2. Conceder permisos de ejecución al Wrapper de Gradle
chmod +x ./gradlew

# 3. Descargar el SDK correspondiente, sincronizar librerías y compilar fuentes de depuración
./gradlew compileDebugSources

# 4. Validar que el empaquetador de recursos de Android (AAPT2) pase limpio sin errores de sintaxis XML
./gradlew lintDebug

7. POLÍTICA DE COMPILACIÓN Y OFUSCACIÓN
ProGuard / R8: Debe mantenerse configurado en minifyEnabled false en el archivo build.gradle. Al utilizar llamadas JNI nativas a través de archivos .so para abrir los periféricos serie de los motores, la ofuscación de ProGuard destruye las firmas de métodos que los drivers de C++ necesitan encontrar en el entorno runtime de Android.

Estructuración del Empaquetado: Desactivar esquemas de división de APKs (splits) en Gradle para forzar la compilación de un archivo binario único y universal que facilite el despliegue directo en la placa.