package com.milodon.vmc.hardware;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PaymentManager {
    private static final String TAG = "MDB_MANAGER";
    private static PaymentManager instance;

    private UsbSerialPort usbSerialPort;
    private PaymentListener listener;
    private ExecutorService commandExecutor;
    private Thread readThread;

    private enum State { IDLE, WAITING_VEND, DISPENSING, SESSION_COMPLETE }
    private State currentState = State.IDLE;
    private boolean isReading = false;

    private final StringBuilder readBuffer = new StringBuilder();

    public interface PaymentListener {
        void onPaymentSuccess();
        void onPaymentFailed(String reason);
    }

    private PaymentManager() {}

    public static synchronized PaymentManager getInstance() {
        if (instance == null) instance = new PaymentManager();
        return instance;
    }

    public void setListener(PaymentListener listener) { this.listener = listener; }

    public void init(Context context) {
        if (usbSerialPort != null && usbSerialPort.isOpen()) return;

        commandExecutor = Executors.newSingleThreadExecutor();
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "Hardware MDB-USB Waferstar no encontrado.");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

        if (connection != null) {
            usbSerialPort = driver.getPorts().get(0);
            try {
                usbSerialPort.open(connection);
                // Configuración estricta según manual: 9600, 8, 1, None
                usbSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                Log.d(TAG, "Interfaz MDB-USB enlazada con éxito.");

                // Iniciar hilo de escucha asíncrona permanente (El adaptador reporta solo cuando hay actividad)
                startReadThread();

                // Secuencia Mandatoria de Inicialización de lector Cashless MDB
                commandExecutor.execute(() -> {
                    try {
                        enviarComandoString("M,1"); // Forzar modo Maestro VMC
                        Thread.sleep(200);
                        enviarComandoString("110001000000"); // 1. Configurar Lector
                        Thread.sleep(200);
                        enviarComandoString("1101FFFF0000"); // 2. Definir Precios Máx/Mín
                        Thread.sleep(200);
                        enviarComandoString("1401"); // 3. Habilitar Lector Card Reader
                        Log.i(TAG, "Secuencia de inicialización PayScan completada de forma limpia.");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Fallo crítico al abrir descriptores USB MDB", e);
            }
        }
    }

    private void startReadThread() {
        isReading = true;
        readThread = new Thread(() -> {
            byte[] buffer = new byte[64];
            while (isReading && usbSerialPort != null && usbSerialPort.isOpen()) {
                try {
                    int n = usbSerialPort.read(buffer, 200);
                    if (n > 0) {
                        // El lector Waferstar responde en formato de texto ASCII terminado en \n
                        String chunk = new String(buffer, 0, n);
                        readBuffer.append(chunk);

                        int lineEndIndex;
                        while ((lineEndIndex = readBuffer.indexOf("\n")) != -1) {
                            String rawLine = readBuffer.substring(0, lineEndIndex).trim();
                            readBuffer.delete(0, lineEndIndex + 1);

                            if (!rawLine.isEmpty()) {
                                parsearLineaWaferstar(rawLine);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Fallo en hilo de lectura serial", e);
                }
            }
        });
        readThread.start();
    }

    public void payment(final String amount, final String sku) {
        if (commandExecutor == null) return;
        commandExecutor.execute(() -> {
            currentState = State.WAITING_VEND;
            try {
                int price = Integer.parseInt(amount);
                int itemNumber = 1;
                try {
                    itemNumber = Integer.parseInt(sku.replaceAll("[^0-9]", ""));
                    if (itemNumber <= 0) itemNumber = 1;
                } catch (Exception e) {
                    itemNumber = 1;
                }

                // Formatear a 4 dígitos HEX rellenados con ceros a la izquierda (Formato MDB 2-bytes)
                String hexPrice = String.format("%04X", price);
                String hexItem = String.format("%04X", itemNumber);

                // Solicitud de Venta: 1300 + MontoHEX + ItemHEX
                enviarComandoString("1300" + hexPrice + hexItem);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Monto inválido para procesamiento MDB");
            }
        });
    }

    public void cancel() {
        if (commandExecutor == null) return;
        commandExecutor.execute(() -> {
            enviarComandoString("1301"); // VEND CANCEL oficial del manual Waferstar
        });
    }

    public void vendSuccess(final String sku) {
        if (commandExecutor == null) return;
        commandExecutor.execute(() -> {
            currentState = State.SESSION_COMPLETE;
            int itemNumber = 1;
            try {
                itemNumber = Integer.parseInt(sku.replaceAll("[^0-9]", ""));
                if (itemNumber <= 0) itemNumber = 1;
            } catch (Exception e) {
                itemNumber = 1;
            }
            String hexItem = String.format("%04X", itemNumber);
            enviarComandoString("1302" + hexItem); // VEND SUCCESS + ItemHEX
        });
    }

    public void vendFailure() {
        if (commandExecutor == null) return;
        commandExecutor.execute(() -> {
            currentState = State.SESSION_COMPLETE;
            enviarComandoString("1301"); // Retorno seguro mediante orden de Cancelación de Venta
        });
    }

    public void sessionComplete() {
        if (commandExecutor == null) return;
        commandExecutor.execute(() -> {
            enviarComandoString("1304"); // Fin de sesión definitivo para liberar el terminal
        });
    }

    private void enviarComandoString(String cmd) {
        try {
            if (usbSerialPort != null && usbSerialPort.isOpen()) {
                String formattedCmd = cmd + "\n";
                usbSerialPort.write(formattedCmd.getBytes(), 100);
                Log.d(TAG, "TX MDB-USB --> " + cmd);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error de escritura en bus USB-MDB", e);
        }
    }

    private void parsearLineaWaferstar(String line) {
        Log.i(TAG, "RX MDB-USB <-- " + line);

        // Intercepción de Cancelación o Rechazo de tarjeta física (Trama 07)
        if (line.startsWith("07") || line.contains("REJECT") || line.contains("CANCEL")) {
            currentState = State.IDLE;
            if (listener != null) listener.onPaymentFailed("Operación denegada o cancelada por el usuario");
            return;
        }

        if (currentState == State.WAITING_VEND) {
            // Trama 05 = Aprobación de crédito por el servidor central de cobros
            if (line.startsWith("05")) {
                currentState = State.DISPENSING;
                if (listener != null) listener.onPaymentSuccess();
            }
        } else if (currentState == State.SESSION_COMPLETE) {
            currentState = State.IDLE;
            Log.d(TAG, "Sesión finalizada de manera conforme. Estatus IDLE restituido.");
        }
    }

    public void release() {
        isReading = false;
        if (commandExecutor != null) commandExecutor.shutdownNow();
        try {
            if (usbSerialPort != null) {
                usbSerialPort.close();
                usbSerialPort = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error de cierre en puerto MDB", e);
        }
        instance = null;
    }
}