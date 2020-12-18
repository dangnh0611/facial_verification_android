package com.example.donelogin.model;

public class Account {
    private String userName;
    private String email;

    public Account(String userName, String email){
        this.userName= userName;
        this.email = email;
    }

    public String getUserName(){
        return this.userName;
    }

    public String getEmail(){
        return this.email;
    }
}
