package com.app.service.myshift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Calls a configurable HTTP face-matching API. If {@link MyShiftConfig#faceApiUrl()} is empty, runs in
 * dev mode (image must be non-empty; no real verification).
 */
public final class FaceAuthClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private FaceAuthClient() {}

    public static FaceAuthResult verify(int userId, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 32) {
            return new FaceAuthResult(false, 0, "none", "image_too_small");
        }
        String url = MyShiftConfig.faceApiUrl();
        if (url == null || url.isBlank()) {
            return new FaceAuthResult(true, 0.8, "dev-skip", "api_url_not_set");
        }
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            JsonObject body = new JsonObject();
            body.addProperty("userId", userId);
            body.addProperty("imageBase64", b64);
            body.addProperty("imageContentType", "image/jpeg");
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            String key = MyShiftConfig.faceApiKey();
            if (key != null && !key.isBlank()) {
                b.header("Authorization", "Bearer " + key.trim());
            }
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return new FaceAuthResult(false, 0, "api", "http_" + res.statusCode());
            }
            JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
            boolean ok = j.has("ok") && j.get("ok").getAsBoolean()
                    || j.has("verified") && j.get("verified").getAsBoolean();
            double score = 0;
            if (j.has("score")) {
                if (j.get("score").isJsonPrimitive() && j.get("score").getAsJsonPrimitive().isNumber()) {
                    score = j.get("score").getAsDouble();
                }
            } else if (j.has("confidence")) {
                score = j.get("confidence").getAsDouble();
            }
            if (score > 1.0) {
                score = score / 100.0;
            }
            int minPct = MyShiftConfig.faceMinConfidencePercent();
            if (j.has("score") || j.has("confidence")) {
                if (score * 100.0 < minPct) {
                    ok = false;
                }
            }
            return new FaceAuthResult(ok, score, "api", null);
        } catch (Exception e) {
            return new FaceAuthResult(false, 0, "api", e.getClass().getSimpleName());
        }
    }
}
