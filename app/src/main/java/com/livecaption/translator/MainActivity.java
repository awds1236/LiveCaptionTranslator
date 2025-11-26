package com.livecaption.translator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1002;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1003;

    private Switch switchService;
    private Button btnSettings;
    private Spinner spinnerSourceLanguage;
    private Spinner spinnerTargetLanguage;
    private Button btnStartService;
    private Button btnStopService;

    private MediaProjectionManager mediaProjectionManager;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupLanguageSpinners();
        setupListeners();
        checkPermissions();

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    private void initializeViews() {
        switchService = findViewById(R.id.switch_service);
        btnSettings = findViewById(R.id.btn_settings);
        spinnerSourceLanguage = findViewById(R.id.spinner_source_language);
        spinnerTargetLanguage = findViewById(R.id.spinner_target_language);
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
    }

    private void setupLanguageSpinners() {
        String[] languages = {"한국어", "English", "日本語", "中文", "Español", "Français", "Deutsch"};
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spinnerSourceLanguage.setAdapter(adapter);
        spinnerTargetLanguage.setAdapter(adapter);
        
        spinnerTargetLanguage.setSelection(1); // 기본값: 영어
    }

    private void setupListeners() {
        btnStartService.setOnClickListener(v -> startCaptionService());
        btnStopService.setOnClickListener(v -> stopCaptionService());
        btnSettings.setOnClickListener(v -> openSettings());
        
        switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startCaptionService();
            } else {
                stopCaptionService();
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 오버레이 권한 체크
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
                return;
            }

            // 오디오 녹음 권한 체크
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    private void startCaptionService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        // MediaProjection 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_CODE_MEDIA_PROJECTION
            );
        }
    }

    private void stopCaptionService() {
        Intent serviceIntent = new Intent(this, AudioCaptureService.class);
        stopService(serviceIntent);
        
        Intent overlayIntent = new Intent(this, OverlayService.class);
        stopService(overlayIntent);
        
        isServiceRunning = false;
        switchService.setChecked(false);
        Toast.makeText(this, "서비스가 중지되었습니다", Toast.LENGTH_SHORT).show();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                // AudioCaptureService 시작
                Intent serviceIntent = new Intent(this, AudioCaptureService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                serviceIntent.putExtra("sourceLanguage", spinnerSourceLanguage.getSelectedItem().toString());
                serviceIntent.putExtra("targetLanguage", spinnerTargetLanguage.getSelectedItem().toString());
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                // OverlayService 시작
                Intent overlayIntent = new Intent(this, OverlayService.class);
                startService(overlayIntent);

                isServiceRunning = true;
                switchService.setChecked(true);
                Toast.makeText(this, "서비스가 시작되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "오디오 캡처 권한이 거부되었습니다", Toast.LENGTH_SHORT).show();
                switchService.setChecked(false);
            }
        } else if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한이 허용되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "오디오 녹음 권한이 필요합니다", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceRunning) {
            stopCaptionService();
        }
    }
}
