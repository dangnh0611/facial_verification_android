package com.example.donelogin.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;

@Entity(tableName = "accounts")
public class Account {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String username;
    public String email;
    public int device_id;
    public String keyAlias;
    public float[][] embeddings;
}
