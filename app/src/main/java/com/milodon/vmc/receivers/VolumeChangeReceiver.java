package com.milodon.vmc.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

public class VolumeChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
            Log.d("VolumeChangeReceiver", "Volume change detected. Locking volume...");
            // Stub to enforce volume lock logic from Ventor
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // Example logic to keep volume at max
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != maxVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
                }
            }
        }
    }
}
