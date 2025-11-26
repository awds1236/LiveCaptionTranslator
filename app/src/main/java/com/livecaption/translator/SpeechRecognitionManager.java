package com.livecaption.translator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechRecognitionManager {

    private static final String TAG = "SpeechRecognitionMgr";

    private Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    private RecognitionCallback callback;

    public interface RecognitionCallback {
        void onTextRecognized(String text);
        void onError(String error);
    }

    public SpeechRecognitionManager(Context context) {
        this.context = context;
        initializeSpeechRecognizer();
    }

    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Ready for speech");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // 오디오 레벨 변화 (선택적 구현)
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // 오디오 버퍼 수신 (선택적 구현)
                }

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "End of speech");
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorText(error);
                    Log.e(TAG, "Speech recognition error: " + errorMessage);
                    
                    if (callback != null) {
                        callback.onError(errorMessage);
                    }
                    
                    isListening = false;
                    
                    // 에러 발생 시 재시작
                    restartListening();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    );
                    
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        Log.d(TAG, "Recognized: " + recognizedText);
                        
                        if (callback != null) {
                            callback.onTextRecognized(recognizedText);
                        }
                    }
                    
                    isListening = false;
                    
                    // 계속 듣기 위해 재시작
                    restartListening();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    );
                    
                    if (matches != null && !matches.isEmpty()) {
                        String partialText = matches.get(0);
                        Log.d(TAG, "Partial result: " + partialText);
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // 이벤트 처리 (선택적 구현)
                }
            });

            setupRecognizerIntent();
            Log.d(TAG, "SpeechRecognizer initialized");
        } else {
            Log.e(TAG, "Speech recognition not available on this device");
        }
    }

    private void setupRecognizerIntent() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    public void processAudio(byte[] audioData, int size, RecognitionCallback callback) {
        this.callback = callback;
        
        if (!isListening && speechRecognizer != null) {
            startListening();
        }
    }

    public void startListening() {
        if (speechRecognizer != null && !isListening) {
            try {
                speechRecognizer.startListening(recognizerIntent);
                isListening = true;
                Log.d(TAG, "Started listening");
            } catch (Exception e) {
                Log.e(TAG, "Error starting speech recognition", e);
            }
        }
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            Log.d(TAG, "Stopped listening");
        }
    }

    private void restartListening() {
        // 짧은 지연 후 재시작 (너무 빠르게 재시작하면 에러 발생 가능)
        new android.os.Handler().postDelayed(() -> {
            if (!isListening) {
                startListening();
            }
        }, 300);
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.d(TAG, "SpeechRecognizer destroyed");
        }
        isListening = false;
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "오디오 녹음 오류";
            case SpeechRecognizer.ERROR_CLIENT:
                return "클라이언트 오류";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "권한 부족";
            case SpeechRecognizer.ERROR_NETWORK:
                return "네트워크 오류";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "네트워크 타임아웃";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "일치하는 결과 없음";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "인식기 사용 중";
            case SpeechRecognizer.ERROR_SERVER:
                return "서버 오류";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "음성 입력 타임아웃";
            default:
                return "알 수 없는 오류 (" + errorCode + ")";
        }
    }
}
