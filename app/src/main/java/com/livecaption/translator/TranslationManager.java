package com.livecaption.translator;

import android.content.Context;
import android.util.Log;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.Map;

public class TranslationManager {

    private static final String TAG = "TranslationManager";
    private static TranslationManager instance;

    private Context context;
    private Translator currentTranslator;
    private Map<String, String> languageCodeMap;

    public interface TranslationCallback {
        void onTranslationSuccess(String translatedText);
        void onTranslationError(String error);
    }

    private TranslationManager(Context context) {
        this.context = context.getApplicationContext();
        initializeLanguageMap();
    }

    public static synchronized TranslationManager getInstance(Context context) {
        if (instance == null) {
            instance = new TranslationManager(context);
        }
        return instance;
    }

    private void initializeLanguageMap() {
        languageCodeMap = new HashMap<>();
        languageCodeMap.put("한국어", TranslateLanguage.KOREAN);
        languageCodeMap.put("English", TranslateLanguage.ENGLISH);
        languageCodeMap.put("日本語", TranslateLanguage.JAPANESE);
        languageCodeMap.put("中文", TranslateLanguage.CHINESE);
        languageCodeMap.put("Español", TranslateLanguage.SPANISH);
        languageCodeMap.put("Français", TranslateLanguage.FRENCH);
        languageCodeMap.put("Deutsch", TranslateLanguage.GERMAN);
    }

    public void translate(String text, String sourceLanguage, String targetLanguage, 
                         TranslationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onTranslationError("빈 텍스트");
            return;
        }

        String sourceCode = getLanguageCode(sourceLanguage);
        String targetCode = getLanguageCode(targetLanguage);

        if (sourceCode == null || targetCode == null) {
            callback.onTranslationError("지원하지 않는 언어");
            return;
        }

        // 같은 언어면 번역 건너뛰기
        if (sourceCode.equals(targetCode)) {
            callback.onTranslationSuccess(text);
            return;
        }

        setupTranslator(sourceCode, targetCode, new TranslatorSetupCallback() {
            @Override
            public void onSetupComplete() {
                performTranslation(text, callback);
            }

            @Override
            public void onSetupError(String error) {
                callback.onTranslationError(error);
            }
        });
    }

    private void setupTranslator(String sourceCode, String targetCode, 
                                 TranslatorSetupCallback callback) {
        // 이미 설정된 번역기가 있으면 재사용
        if (currentTranslator != null) {
            callback.onSetupComplete();
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(targetCode)
            .build();

        currentTranslator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
            .requireWifi()
            .build();

        currentTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener(unused -> {
                Log.d(TAG, "Translation model downloaded successfully");
                callback.onSetupComplete();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error downloading translation model", e);
                callback.onSetupError("번역 모델 다운로드 실패: " + e.getMessage());
            });
    }

    private void performTranslation(String text, TranslationCallback callback) {
        if (currentTranslator == null) {
            callback.onTranslationError("번역기가 초기화되지 않음");
            return;
        }

        currentTranslator.translate(text)
            .addOnSuccessListener(translatedText -> {
                Log.d(TAG, "Translation successful: " + text + " -> " + translatedText);
                callback.onTranslationSuccess(translatedText);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Translation error", e);
                callback.onTranslationError("번역 실패: " + e.getMessage());
            });
    }

    private String getLanguageCode(String language) {
        return languageCodeMap.get(language);
    }

    public void closeTranslator() {
        if (currentTranslator != null) {
            currentTranslator.close();
            currentTranslator = null;
            Log.d(TAG, "Translator closed");
        }
    }

    private interface TranslatorSetupCallback {
        void onSetupComplete();
        void onSetupError(String error);
    }
}
