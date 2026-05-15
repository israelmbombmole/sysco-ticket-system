package com.app.service.myshift;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Resolves approximate client location from public IP using https://ipapi.co/json/ (no key for basic use).
 */
public final class GeoLocationClient {

    private static final String DEFAULT_URL = "https://ipapi.co/json/";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private GeoLocationClient() {}

    public static GeoInfo fetch() {
        return fetch(DEFAULT_URL);
    }

    public static GeoInfo fetch(String url) {
        String u = (url == null || url.isBlank()) ? DEFAULT_URL : url.trim();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(u))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "SYSCO-MyShift/1.0")
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return new GeoInfo(null, null, null, null, null, null, null);
            }
            JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
            if (j.has("error") && j.get("error").getAsBoolean()) {
                return new GeoInfo(null, null, null, null, null, null, null);
            }
            String ip = j.has("ip") ? j.get("ip").getAsString() : null;
            Double lat = null;
            Double lon = null;
            if (j.has("latitude") && j.get("latitude").isJsonPrimitive()) {
                lat = j.get("latitude").getAsDouble();
            }
            if (j.has("longitude") && j.get("longitude").isJsonPrimitive()) {
                lon = j.get("longitude").getAsDouble();
            }
            String city = j.has("city") ? j.get("city").getAsString() : null;
            String country = j.has("country_name")
                    ? j.get("country_name").getAsString()
                    : (j.has("country") ? j.get("country").getAsString() : null);
            String ccode = j.has("country_code") ? j.get("country_code").getAsString() : null;
            StringBuilder label = new StringBuilder();
            if (city != null) {
                label.append(city);
            }
            if (country != null) {
                if (label.length() > 0) {
                    label.append(", ");
                }
                label.append(country);
            }
            return new GeoInfo(
                    lat, lon, city, country, ccode, label.length() > 0 ? label.toString() : null, ip);
        } catch (Exception e) {
            return new GeoInfo(null, null, null, null, null, null, null);
        }
    }
}
