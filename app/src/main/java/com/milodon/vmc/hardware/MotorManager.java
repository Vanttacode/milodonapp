package com.milodon.vmc.hardware;

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import android_serialport_api.SerialPort;

public class MotorManager {
    private static final String TAG = "MotorManager";
    private static final String SERIAL_PORT = "/dev/ttyS3";
    private static final int DELAY_MS = 111;
    private static final int MOTOR_TIMEOUT_MS = 6000;

    private static MotorManager instance;

    private ExecutorService commandExecutor;

    private final Semaphore commandSemaphore = new Semaphore(1);
    private boolean isRunning = false;
    private boolean isDispensing = false;

    private OutputStream serialOutputStream;
    private InputStream serialInputStream;
    private SerialPort serialPort;
    private long lastCommandTime = 0;

    public interface MotorListener {
        void onMotorSuccess();
        void onMotorError(String reason);
    }

    private MotorManager() {}

    public static synchronized MotorManager getInstance() {
        if (instance == null) {
            instance = new MotorManager();
        }
        return instance;
    }

    public void init() {
        if (isRunning) return;
        commandExecutor = Executors.newSingleThreadExecutor();
        isRunning = true;

        commandExecutor.execute(this::initSerialPort);
    }

    private void initSerialPort() {
        try {
            // Forzar permisos root sobre el nodo serial en la placa RK3288
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod 666 " + SERIAL_PORT});
            process.waitFor();
            Thread.sleep(500);

            File deviceFile = new File(SERIAL_PORT);
            if (!deviceFile.exists()) {
                Log.e(TAG, "MODO TALLER: El puerto " + SERIAL_PORT + " no está disponible físicamente. Suspendiendo inicialización JNI.");
                return;
            }

            // CORRECCIÓN VELOCIDAD: Configuración mandatoria a 9600 baudios para placa controladora Wuyi
            serialPort = new SerialPort(deviceFile, 9600, 0);
            serialOutputStream = serialPort.getOutputStream();
            serialInputStream = serialPort.getInputStream();
            Log.d(TAG, "Puerto serial abierto con éxito a 9600 bps.");

        } catch (Throwable t) {
            // BLINDAJE DE ENTORNO: Evita el Force Close capturando LinkageErrors por falta del .so en desarrollo
            Log.e(TAG, "AVISO: Librería JNI ausente o error de permisos root protegido en taller.", t);
        }
    }

    private void sendCommand(String command) throws IOException {
        try {
            commandSemaphore.acquire();

            // Poka-Yoke Anti-Colisión: Es obligatorio un delay exacto de 111ms entre cada escritura
            Thread.sleep(111);

            if (serialOutputStream != null) {
                serialOutputStream.write(command.getBytes());
                serialOutputStream.flush();
                lastCommandTime = System.currentTimeMillis();
                Log.d(TAG, "TX Motor --> " + command);
            } else {
                throw new IOException("Stream de salida serial inaccesible.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            commandSemaphore.release();
        }
    }

    public void rotateMotor(final String sku, final MotorListener listener) {
        commandExecutor.execute(() -> {
            isDispensing = true;
            try {
                String shipId = Long.toHexString(System.currentTimeMillis());
                if (shipId.length() > 8) shipId = shipId.substring(shipId.length() - 8);

                // Normalización de SKUs: Rellena automáticamente el cero para coincidir con la matriz física
                String formattedNum = sku;
                if (formattedNum != null) {
                    if (formattedNum.length() == 2) {
                        formattedNum = formattedNum + "0";
                    }
                } else {
                    formattedNum = "110";
                }

                String command = String.format("(NOAD:0-|NUM:%s|ID:%s)", formattedNum, shipId);

                // Limpieza de datos residuales o basura en el buffer de entrada antes de ordenar el giro
                if (serialInputStream != null) {
                    int availableBytes = serialInputStream.available();
                    if (availableBytes > 0) {
                        serialInputStream.skip(availableBytes);
                    }
                }

                sendCommand(command);

                if (serialInputStream == null) {
                    throw new IOException("Descriptor de lectura inaccesible.");
                }

                long startTime = System.currentTimeMillis();
                boolean isSuccess = false;
                boolean operationFinished = false;
                byte[] buffer = new byte[32];
                StringBuilder responseBuffer = new StringBuilder();

                // Bucle de reconstrucción analítica de tramas asíncronas
                while ((System.currentTimeMillis() - startTime < MOTOR_TIMEOUT_MS) && !operationFinished) {
                    if (serialInputStream.available() > 0) {
                        int size = serialInputStream.read(buffer);
                        if (size > 0) {
                            responseBuffer.append(new String(buffer, 0, size));
                            String currentData = responseBuffer.toString();
                            Log.d(TAG, "RX Fragmento Motor: " + currentData);

                            // CORRECCIÓN PROTOCOLO: Filtrar ecos iniciales y buscar confirmaciones del microswitch final de carrera
                            if (currentData.contains(")")) {
                                if (currentData.contains("OK:" + shipId) || (currentData.contains("OK") && !currentData.contains("NOAD"))) {
                                    isSuccess = true;
                                    operationFinished = true;
                                } else if (currentData.contains("ERR") || currentData.contains("FAIL")) {
                                    isSuccess = false;
                                    operationFinished = true;
                                }
                            }
                        }
                    }
                    Thread.sleep(50); // Muestreo óptimo sin saturar la CPU
                }

                if (listener != null) {
                    if (isSuccess) {
                        Log.d(TAG, "Confirmación de entrega validada por hardware.");
                        listener.onMotorSuccess();
                    } else {
                        Log.e(TAG, "Falla en despacho: Tiempo de espera agotado o reporte de atasco mecánico.");
                        listener.onMotorError("Atasco Físico o Ausencia de pulso de microswitch en espiral");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Falla crítica en el despachador de motores", e);
                if (listener != null) {
                    listener.onMotorError(e.getMessage());
                }
            } finally {
                lastCommandTime = System.currentTimeMillis();
                isDispensing = false;
            }
        });
    }

    public void release() {
        isRunning = false;
        if (commandExecutor != null) commandExecutor.shutdownNow();

        try {
            if (serialInputStream != null) serialInputStream.close();
            if (serialOutputStream != null) serialOutputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error al cerrar descriptores de comunicación", e);
        }

        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        instance = null;
    }
}