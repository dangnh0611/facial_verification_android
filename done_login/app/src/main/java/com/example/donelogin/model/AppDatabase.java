package com.example.donelogin.model;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {Account.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public static final String DB_NAME = "DOneLoginDB";

    public abstract AccountDao accountDao();
}
