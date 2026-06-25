package com.milodon.vmc.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.milodon.vmc.admin.AdminLoginActivity;

public class FloatService extends Service {
    private WindowManager windowManager;
    private ImageView floatingButton;
    private int clickCount = 0;
    private static final int REQUIRED_CLICKS = 6;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingButton = new ImageView(this);
        floatingButton.setBackgroundColor(Color.TRANSPARENT);
        floatingButton.setImageDrawable(null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                100, 100,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        floatingButton.setOnClickListener(v -> {
            clickCount++;
            Log.d("FloatService", "Click registrado: " + clickCount);

            if (clickCount >= REQUIRED_CLICKS) {
                clickCount = 0;
                Log.d("FloatService", "Límite alcanzado. Iniciando AdminLoginActivity.");
                Intent intent = new Intent(FloatService.this, AdminLoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        windowManager.addView(floatingButton, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingButton != null) windowManager.removeView(floatingButton);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}