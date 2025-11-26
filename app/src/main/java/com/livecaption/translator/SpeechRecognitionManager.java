package com.livecaption.translator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpeechRecognitionManager {

    private static final String TAG = "SpeechRecognitionMgr";
    private static final int SAMPLE_RATE = 16000;

    private Context context;
    private Model model;
    private Recognizer recognizer;
    private RecognitionCallback callback;
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isInitialized = false;

    public interface RecognitionCallback {
        void onTextRecognized(String text);
        void onError(String error);
    }

    public interface ModelInitCallback {
        void onInitialized();
        void onError(String error);
    }

    public SpeechRecognitionManager(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 음성 인식 모델 초기화
     */
    public void initializeModel(String language, final ModelInitCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Starting model initialization for language: " + language);

                // 모델 이름 가져오기
                String modelName = getModelName(language);
                Log.d(TAG, "Model name: " + modelName);

                // assets에서 내부 저장소로 모델 복사
                File modelDir = new File(context.getFilesDir(), modelName);

                if (!modelDir.exists()) {
                    Log.d(TAG, "Copying model from assets...");
                    if (!copyModelFromAssets(modelName, modelDir)) {
                        String error = "모델 복사 실패: " + modelName;
                        Log.e(TAG, error);
                        mainHandler.post(() -> callback.onError(error));
                        return;
                    }
                    Log.d(TAG, "Model copied successfully");
                }

                // 모델 로드
                Log.d(TAG, "Loading model from: " + modelDir.getAbsolutePath());
                model = new Model(modelDir.getAbsolutePath());

                // Recognizer 생성
                recognizer = new Recognizer(model, SAMPLE_RATE);
                recognizer.setMaxAlternatives(1);
                recognizer.setWords(false);

                isInitialized = true;
                Log.d(TAG, "Model initialized successfully");

                mainHandler.post(() -> callback.onInitialized());

            } catch (Exception e) {
                Log.e(TAG, "Error initializing model", e);
                String error = "모델 초기화 실패: " + e.getMessage();
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    /**
     * assets에서 모델 복사
     */
    private boolean copyModelFromAssets(String modelName, File targetDir) {
        try {
            String assetPath = "models/" + modelName;

            // 대상 디렉토리 생성
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            // assets 폴더의 모든 파일 복사
            copyAssetFolder(assetPath, targetDir);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error copying model from assets", e);
            return false;
        }
    }

    /**
     * assets 폴더 재귀적으로 복사
     */
    private void copyAssetFolder(String assetPath, File targetDir) throws IOException {
        String[] files = context.getAssets().list(assetPath);

        if (files == null || files.length == 0) {
            // 파일인 경우
            copyAssetFile(assetPath, new File(targetDir.getParent(), targetDir.getName()));
        } else {
            // 폴더인 경우
            targetDir.mkdirs();

            for (String filename : files) {
                String assetFilePath = assetPath + "/" + filename;
                File targetFile = new File(targetDir, filename);

                String[] subFiles = context.getAssets().list(assetFilePath);
                if (subFiles != null && subFiles.length > 0) {
                    // 하위 폴더
                    copyAssetFolder(assetFilePath, targetFile);
                } else {
                    // 파일
                    copyAssetFile(assetFilePath, targetFile);
                }
            }
        }
    }

    /**
     * assets 파일 복사
     */
    private void copyAssetFile(String assetPath, File targetFile) throws IOException {
        InputStream in = context.getAssets().open(assetPath);
        FileOutputStream out = new FileOutputStream(targetFile);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        in.close();
        out.close();
    }

    /**
     * 오디오 데이터 처리
     */
    public void processAudio(byte[] audioData, int size, RecognitionCallback callback) {
        if (!isInitialized || recognizer == null) {
            if (callback != null) {
                callback.onError("음성 인식이 초기화되지 않았습니다");
            }
            return;
        }

        this.callback = callback;

        if (audioData == null || size <= 0) {
            return;
        }

        executorService.execute(() -> {
            try {
                if (recognizer.acceptWaveForm(audioData, size)) {
                    // 최종 인식 결과
                    String result = recognizer.getResult();
                    processResult(result, true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing audio", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("오디오 처리 실패: " + e.getMessage()));
                }
            }
        });
    }

    private void processResult(String result, boolean isFinal) {
        try {
            JSONObject jsonResult = new JSONObject(result);
            String text = "";

            if (isFinal && jsonResult.has("text")) {
                text = jsonResult.getString("text");
            }

            if (!text.isEmpty() && isFinal) {
                final String finalText = text;
                Log.d(TAG, "Final recognized text: " + finalText);

                if (callback != null) {
                    mainHandler.post(() -> callback.onTextRecognized(finalText));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing result", e);
        }
    }

    /**
     * 언어 코드에 따른 모델 이름 반환
     */
    private String getModelName(String language) {
        switch (language) {
            case "ko-KR":
            case "한국어":
                return "vosk-model-small-ko-0.22";
            case "en-US":
            case "English":
                return "vosk-model-small-en-us-0.15";
            case "ja-JP":
            case "日本語":
                return "vosk-model-small-ja-0.22";
            case "zh-CN":
            case "中文":
                return "vosk-model-small-cn-0.22";
            default:
                return "vosk-model-small-ko-0.22";
        }
    }

    /**
     * 리소스 해제
     */
    public void destroy() {
        isInitialized = false;

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }

        if (model != null) {
            model.close();
            model = null;
        }

        if (executorService != null) {
            executorService.shutdown();
        }

        Log.d(TAG, "SpeechRecognitionManager destroyed");
    }
}