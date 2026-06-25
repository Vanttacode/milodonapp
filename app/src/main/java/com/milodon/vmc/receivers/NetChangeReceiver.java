package com.milodon.vmc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

public class NetChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "NET_CHANGE_RECEIVER";
    private NetChangeListener listener;

    // Interfaz para enganchar este evento con la MainActivity o el Kiosko Web
    public interface NetChangeListener {
        void onNetworkRestored();
        void onNetworkLost();
    }

    public NetChangeReceiver() {}

    public NetChangeReceiver(NetChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null
                || !intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            return;
        }

        boolean hasInternet = isInternetAvailable(context);
        Log.w(TAG, "🔄 Alerta de red: ¿Conexión real con internet establecida? -> " + hasInternet);

        if (listener != null) {
            if (hasInternet) {
                // Notificar al sistema que puede reintentar la sincronización con Supabase
                listener.onNetworkRestored();
            } else {
                listener.onNetworkLost();
            }
        }
    }

    /**
     * Algoritmo de doble comprobación industrial para asegurar tráfico de datos real
     */
    private boolean isInternetAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        // Soporte para arquitecturas actualizadas (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED); // Valida acceso real a internet
        } else {
            // Fallback seguro sin warnings para el Android 5.1.1 nativo de la RK3288
            try {
                android.net.NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            } catch (Exception e) {
                return false;
            }
        }
    }
}