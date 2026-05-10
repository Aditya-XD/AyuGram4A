/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

 --- AI Translate patch ---
 Google Translate replaced with local Ollama (qwen2.5:3b).
 Ollama must be running in Termux: `ollama serve &`
 Model must be pulled: `ollama pull qwen2.5:3b`
 --------------------------

*/

package com.exteragram.messenger.utils;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.MessageObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TranslatorUtils {

    // ── Config ─────────────────────────────────────────────────────────────────
    //  Change OLLAMA_HOST if Ollama runs on a different machine on your LAN.
    //  Change OLLAMA_MODEL to "qwen2.5:1.5b" if 3b feels slow.
    private static final String OLLAMA_HOST  = "http://127.0.0.1:11434";
    private static final String OLLAMA_MODEL = "qwen2.5:3b";

    public static final DispatchQueue translateQueue = new DispatchQueue("translateQueue", false);

    public interface OnTranslationSuccess {
        void run(CharSequence translated);
    }

    public interface OnTranslationFail {
        void run();
    }

    // todo
    public static void translate(MessageObject messageObject) {
    }

    // ── Public entry point (auto-detect source language) ───────────────────────
    public static void translate(CharSequence text, String toLang,
                                 OnTranslationSuccess onSuccess, OnTranslationFail onFail) {
        if (TextUtils.isEmpty(text)) return;

        if (LanguageDetector.hasSupport()) {
            LanguageDetector.detectLanguage(text.toString(), lng -> {
                String fromLang = (lng != null && !lng.equals("und")) ? lng : "auto";
                translate(text, fromLang, toLang, onSuccess, onFail);
            }, e -> {
                FileLog.e(e);
                translate(text, "auto", toLang, onSuccess, onFail);
            });
        } else {
            translate(text, "auto", toLang, onSuccess, onFail);
        }
    }

    // ── Core translate — calls local Ollama instead of Google Translate ─────────
    public static void translate(CharSequence text, String fromLang, String toLang,
                                 OnTranslationSuccess onSuccess, OnTranslationFail onFail) {
        if (TextUtils.isEmpty(text)) return;

        if (!translateQueue.isAlive()) translateQueue.start();

        translateQueue.postRunnable(() -> {
            try {
                String result = callOllama(text.toString(), fromLang, toLang);
                if (onSuccess != null)
                    AndroidUtilities.runOnUIThread(() -> onSuccess.run(result));
            } catch (Exception e) {
                FileLog.e(e);
                if (onFail != null)
                    AndroidUtilities.runOnUIThread(onFail::run);
            }
        });
    }

    // ── Ollama HTTP call ───────────────────────────────────────────────────────
    private static String callOllama(String text, String fromLang, String toLang)
            throws Exception {

        String targetLangName = resolveLanguageName(toLang);

        // Extra hints to stop qwen2.5 mixing up Tagalog / Malay / Indonesian —
        // they look nearly identical to models that don't know SEA linguistics.
        String hint = "";
        switch (fromLang) {
            case "tl": hint = "The source is Tagalog (marker words: ang, ng, sa, mga, ay, ito, nang)."; break;
            case "ms":  hint = "The source is Bahasa Melayu (words: saya, boleh, awak, tidak, anda)."; break;
            case "id":  hint = "The source is Bahasa Indonesia (words: saya, bisa, sudah, aku, anda)."; break;
        }

        String prompt = "Translate the following message to " + targetLangName + ". "
                + "Output ONLY the translated text — no explanation, no quotes, no preamble. "
                + hint + "\n\nMessage: " + text;

        // Build JSON body
        JSONObject options = new JSONObject();
        options.put("temperature", 0.15);  // low = deterministic, good for translation
        options.put("num_predict", 512);
        options.put("num_ctx", 1024);       // smaller context = faster on-device

        JSONObject body = new JSONObject();
        body.put("model", OLLAMA_MODEL);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("options", options);

        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        // POST to local Ollama
        URL url = new URL(OLLAMA_HOST + "/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5_000);   // fail fast if Ollama isn't running
        conn.setReadTimeout(60_000);     // generous read timeout for on-device inference

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        if (conn.getResponseCode() != 200) {
            throw new Exception("Ollama returned HTTP " + conn.getResponseCode()
                    + " — is it running? Run in Termux: ollama serve &");
        }

        // Read response
        Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
        String raw = scanner.useDelimiter("\\A").next();
        scanner.close();

        JSONObject json = new JSONObject(raw);
        return json.getString("response").trim();
    }

    // ── Language code → full name ──────────────────────────────────────────────
    //  Ollama understands full names much better than ISO codes.
    private static String resolveLanguageName(String code) {
        switch (code) {
            case "en":    return "English";
            case "tl":
            case "fil":   return "Tagalog";
            case "ms":    return "Bahasa Melayu";
            case "id":    return "Bahasa Indonesia";
            case "zh-cn":
            case "zh":    return "Simplified Chinese";
            case "zh-tw": return "Traditional Chinese";
            case "ja":    return "Japanese";
            case "ko":    return "Korean";
            case "ar":    return "Arabic";
            case "ru":    return "Russian";
            case "es":    return "Spanish";
            case "fr":    return "French";
            case "de":    return "German";
            case "pt":    return "Portuguese";
            case "hi":    return "Hindi";
            case "vi":    return "Vietnamese";
            case "th":    return "Thai";
            case "tr":    return "Turkish";
            case "uk":    return "Ukrainian";
            case "pl":    return "Polish";
            case "it":    return "Italian";
            case "nl":    return "Dutch";
            case "sv":    return "Swedish";
            case "fa":    return "Persian";
            case "he":
            case "iw":    return "Hebrew";
            default:      return code; // fallback — pass the code as-is
        }
    }
}
