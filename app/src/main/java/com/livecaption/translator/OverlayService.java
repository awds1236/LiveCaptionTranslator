package com.livecaption.translator;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {

    private static final String TAG = "OverlayService";

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvOriginalText;
    private TextView tvTranslatedText;

    private BroadcastReceiver subtitleReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createOverlayView();
        registerSubtitleReceiver();
    }

    private void createOverlayView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.overlay_subtitle, null);

        tvOriginalText = overlayView.findViewById(R.id.tv_original_text);
        tvTranslatedText = overlayView.findViewById(R.id.tv_translated_text);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 100; // 하단에서 100px 위

        try {
            windowManager.addView(overlayView, params);
            Log.d(TAG, "Overlay view created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating overlay view", e);
        }
    }

    private void registerSubtitleReceiver() {
        subtitleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String originalText = intent.getStringExtra("originalText");
                String translatedText = intent.getStringExtra("translatedText");

                updateSubtitle(originalText, translatedText);
            }
        };

        IntentFilter filter = new IntentFilter("com.livecaption.translator.UPDATE_SUBTITLE");

        // Android 13(API 33) 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(subtitleReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(subtitleReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    private void updateSubtitle(final String originalText, final String translatedText) {
        if (overlayView != null) {
            overlayView.post(() -> {
                if (originalText != null && !originalText.isEmpty()) {
                    tvOriginalText.setText(originalText);
                    tvOriginalText.setVisibility(View.VISIBLE);
                } else {
                    tvOriginalText.setVisibility(View.GONE);
                }

                if (translatedText != null && !translatedText.isEmpty()) {
                    tvTranslatedText.setText(translatedText);
                    tvTranslatedText.setVisibility(View.VISIBLE);
                } else {
                    tvTranslatedText.setVisibility(View.GONE);
                }

                Log.d(TAG, "Subtitle updated: " + translatedText);
            });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                Log.d(TAG, "Overlay view removed");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay view", e);
            }
        }

        if (subtitleReceiver != null) {
            try {
                unregisterReceiver(subtitleReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}