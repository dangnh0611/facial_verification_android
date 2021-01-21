package com.example.donelogin.model;


public class AccessRequest {
    private final String username;
    private final String email;
    private final String mfaCode;
    private final int deviceId;
    private final String requestTime;
    private final String ipAddress;
    private final String location;

    public AccessRequest(String mfaCode, int deviceId, String requestTime, String username,
                         String email, String ipAddress, String location) {
        this.username = username;
        this.email = email;
        this.mfaCode = mfaCode;
        this.deviceId = deviceId;
        this.requestTime = requestTime;
        this.ipAddress = ipAddress;
        this.location = location;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getMfaCode() {
        return this.mfaCode;
    }

    public int getDeviceId() {
        return this.deviceId;
    }

    public String getRequestTime() {
        return this.requestTime;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public String getLocation() {
        return this.location;
    }

}

