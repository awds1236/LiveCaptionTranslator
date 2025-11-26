package com.livecaption.translator;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "CaptionSettings";

    private Spinner spinnerSubtitlePosition;
    private SeekBar seekBarFontSize;
    private TextView tvFontSizeValue;
    private Switch switchShowOriginal;
    private Switch switchAutoDetect;
    private Button btnSave;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        loadSettings();
        setupListeners();
    }

    private void initializeViews() {
        spinnerSubtitlePosition = findViewById(R.id.spinner_subtitle_position);
        seekBarFontSize = findViewById(R.id.seekbar_font_size);
        tvFontSizeValue = findViewById(R.id.tv_font_size_value);
        switchShowOriginal = findViewById(R.id.switch_show_original);
        switchAutoDetect = findViewById(R.id.switch_auto_detect);
        btnSave = findViewById(R.id.btn_save);

        // 자막 위치 스피너 설정
        String[] positions = {"상단", "중앙", "하단"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, positions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubtitlePosition.setAdapter(adapter);
    }

    private void loadSettings() {
        // 저장된 설정 불러오기
        int position = sharedPreferences.getInt("subtitle_position", 2); // 기본값: 하단
        int fontSize = sharedPreferences.getInt("font_size", 18); // 기본값: 18sp
        boolean showOriginal = sharedPreferences.getBoolean("show_original", true);
        boolean autoDetect = sharedPreferences.getBoolean("auto_detect", false);

        spinnerSubtitlePosition.setSelection(position);
        seekBarFontSize.setProgress(fontSize);
        tvFontSizeValue.setText(fontSize + "sp");
        switchShowOriginal.setChecked(showOriginal);
        switchAutoDetect.setChecked(autoDetect);
    }

    private void setupListeners() {
        seekBarFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int fontSize = Math.max(12, progress); // 최소 12sp
                tvFontSizeValue.setText(fontSize + "sp");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putInt("subtitle_position", spinnerSubtitlePosition.getSelectedItemPosition());
        editor.putInt("font_size", seekBarFontSize.getProgress());
        editor.putBoolean("show_original", switchShowOriginal.isChecked());
        editor.putBoolean("auto_detect", switchAutoDetect.isChecked());

        editor.apply();

        // 설정 저장 완료 메시지
        android.widget.Toast.makeText(this, "설정이 저장되었습니다", 
            android.widget.Toast.LENGTH_SHORT).show();

        finish();
    }

    @Override
    public void onBackPressed() {
        saveSettings();
        super.onBackPressed();
    }
}
