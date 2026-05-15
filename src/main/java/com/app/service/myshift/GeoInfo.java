package com.app.service.myshift;

public final class GeoInfo {

    public final Double latitude;
    public final Double longitude;
    public final String city;
    public final String country;
    public final String countryCode;
    public final String displayLabel;
    public final String publicIp;

    public GeoInfo(
            Double latitude, Double longitude, String city, String country, String countryCode,
            String displayLabel, String publicIp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = city;
        this.country = country;
        this.countryCode = countryCode;
        this.displayLabel = displayLabel;
        this.publicIp = publicIp;
    }
}
