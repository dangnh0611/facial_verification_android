package com.example.donelogin.model;


public class AccessRequest {
    private String username;
    private String email;
    private String mfaCode;
    private int deviceId;
    private String requestTime;

    public AccessRequest(String mfaCode, int deviceId, String requestTime, String username, String email){
        this.username = username;
        this.email = email;
        this.mfaCode = mfaCode;
        this.deviceId = deviceId;
        this.requestTime = requestTime;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail(){
        return email;
    }

    public String getMfaCode(){
        return this.mfaCode;
    }

    public int getDeviceId(){
        return this.deviceId;
    }
    public String getRequestTime(){
        return this.requestTime;
    }

}

