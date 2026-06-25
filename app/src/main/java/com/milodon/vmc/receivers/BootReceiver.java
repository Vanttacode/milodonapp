package com.milodon.vmc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.milodon.vmc.MainActivity;

public class BootReceiver extends BroadcastReceiver {

    // 5 segundos de retraso para garantizar el montaje de hardware físico (/dev/tty...) y Root
    private static final int BOOT_DELAY_MS = 5000;

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();

        // Atrapa tanto el arranque estándar como el inicio rápido de algunas placas
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d("BootReceiver", "Reinicio detectado. Esperando inicialización de hardware físico...");

            // Inyección de retraso protegido
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d("BootReceiver", "Iniciando Milodon VMC Master...");

                Intent i = new Intent(context, MainActivity.class);
                // FLAG_ACTIVITY_NEW_TASK: Obligatorio desde un Receiver
                // FLAG_ACTIVITY_CLEAR_TOP: Destruye instancias previas fantasmas si hubo un doble-disparo
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                context.startActivity(i);

            }, BOOT_DELAY_MS);
        }
    }
}