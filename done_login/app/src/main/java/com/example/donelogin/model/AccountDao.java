package com.example.donelogin.model;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.ArrayList;
import java.util.List;

@Dao
public interface AccountDao {
    @Insert
    void insertAll(Account... users);

    @Delete
    void delete(Account user);

    @Query("SELECT * FROM accounts")
    List<Account> getAll();

    @Query("SELECT * FROM accounts WHERE device_id = :deviceId")
    Account getAccountByDeviceId(int deviceId);
}