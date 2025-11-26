package com.livecaption.translator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class AudioCaptureService extends Service {

    private static final String TAG = "AudioCaptureService";
    private static final String CHANNEL_ID = "AudioCaptureChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private Thread audioThread;
    private boolean isCapturing = false;

    private SpeechRecognitionManager speechRecognitionManager;
    private String sourceLanguage;
    private String targetLanguage;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        speechRecognitionManager = new SpeechRecognitionManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");
            sourceLanguage = intent.getStringExtra("sourceLanguage");
            targetLanguage = intent.getStringExtra("targetLanguage");

            startForeground(NOTIFICATION_ID, createNotification());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startAudioCapture(resultCode, data);
            }
        }

        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startAudioCapture(int resultCode, Intent data) {
        try {
            MediaProjectionManager projectionManager = 
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null");
                stopSelf();
                return;
            }

            AudioPlaybackCaptureConfiguration config = 
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();

            int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, 
                CHANNEL_CONFIG, 
                AUDIO_FORMAT
            );

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            audioRecord = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build();

            audioRecord.startRecording();
            isCapturing = true;

            startAudioProcessing();

            Log.d(TAG, "Audio capture started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
            stopSelf();
        }
    }

    private void startAudioProcessing() {
        audioThread = new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, 
                CHANNEL_CONFIG, 
                AUDIO_FORMAT
            );
            byte[] audioBuffer = new byte[bufferSize];

            while (isCapturing) {
                int bytesRead = audioRecord.read(audioBuffer, 0, bufferSize);
                
                if (bytesRead > 0) {
                    // 오디오 데이터를 Speech Recognition으로 전달
                    processAudioData(audioBuffer, bytesRead);
                }
            }
        });
        audioThread.start();
    }

    private void processAudioData(byte[] audioData, int size) {
        // 음성 인식 처리
        speechRecognitionManager.processAudio(audioData, size, new SpeechRecognitionManager.RecognitionCallback() {
            @Override
            public void onTextRecognized(String text) {
                Log.d(TAG, "Recognized text: " + text);
                
                // 번역 처리
                TranslationManager.getInstance(AudioCaptureService.this)
                    .translate(text, sourceLanguage, targetLanguage, 
                        new TranslationManager.TranslationCallback() {
                            @Override
                            public void onTranslationSuccess(String translatedText) {
                                Log.d(TAG, "Translated text: " + translatedText);
                                
                                // 오버레이에 자막 표시
                                Intent broadcastIntent = new Intent("com.livecaption.translator.UPDATE_SUBTITLE");
                                broadcastIntent.putExtra("originalText", text);
                                broadcastIntent.putExtra("translatedText", translatedText);
                                sendBroadcast(broadcastIntent);
                            }

                            @Override
                            public void onTranslationError(String error) {
                                Log.e(TAG, "Translation error: " + error);
                            }
                        });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Recognition error: " + error);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Live Caption Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("실시간 자막 서비스");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Caption Translator")
            .setContentText("실시간 자막이 실행 중입니다")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAudioCapture();
    }

    private void stopAudioCapture() {
        isCapturing = false;

        if (audioThread != null) {
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping audio thread", e);
            }
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing audio record", e);
            }
            audioRecord = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if (speechRecognitionManager != null) {
            speechRecognitionManager.destroy();
        }

        Log.d(TAG, "Audio capture stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
