package com.milodon.vmc.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class CameraService extends Service {
    private static final String TAG = "CameraService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.app.android.common.ACTION_SHIPPING".equals(intent.getAction())) {
            Log.d(TAG, "Taking silent photo on dispense...");
            // Camera capture stub
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
