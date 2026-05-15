package com.app.model;

/**
 * View row for MyShift present list and range reports.
 */
public class MyShiftRow {

    private int signinId;
    private int userId;
    private String username;
    /** Display for "Noms & prénoms" (same as {@link #username} until the user table has first/last name). */
    private String namesAndPostnoms;
    private String matricule;
    /** App role as "Fonctions" in registries. */
    private String fonction;
    /** User's assigned attendance registry / signature code. */
    private String attendanceSignature;
    private String signinDay;
    private String signInTime;
    private String signOutTime;
    private String location;
    private String city;
    private String country;
    private String ip;
    private boolean faceVerified;
    private Double faceConfidence;
    private String faceMethod;

    public int getSigninId() {
        return signinId;
    }

    public void setSigninId(int signinId) {
        this.signinId = signinId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNamesAndPostnoms() {
        return namesAndPostnoms != null && !namesAndPostnoms.isBlank() ? namesAndPostnoms : username;
    }

    public void setNamesAndPostnoms(String namesAndPostnoms) {
        this.namesAndPostnoms = namesAndPostnoms;
    }

    public String getFonction() {
        return fonction;
    }

    public void setFonction(String fonction) {
        this.fonction = fonction;
    }

    public String getAttendanceSignature() {
        return attendanceSignature;
    }

    public void setAttendanceSignature(String attendanceSignature) {
        this.attendanceSignature = attendanceSignature;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    public String getSigninDay() {
        return signinDay;
    }

    public void setSigninDay(String signinDay) {
        this.signinDay = signinDay;
    }

    public String getSignInTime() {
        return signInTime;
    }

    public void setSignInTime(String signInTime) {
        this.signInTime = signInTime;
    }

    public String getSignOutTime() {
        return signOutTime;
    }

    public void setSignOutTime(String signOutTime) {
        this.signOutTime = signOutTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public boolean isFaceVerified() {
        return faceVerified;
    }

    public void setFaceVerified(boolean faceVerified) {
        this.faceVerified = faceVerified;
    }

    public Double getFaceConfidence() {
        return faceConfidence;
    }

    public void setFaceConfidence(Double faceConfidence) {
        this.faceConfidence = faceConfidence;
    }

    public String getFaceMethod() {
        return faceMethod;
    }

    public void setFaceMethod(String faceMethod) {
        this.faceMethod = faceMethod;
    }
}
