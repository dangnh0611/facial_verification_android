package com.example.donelogin.model;

import androidx.room.TypeConverter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Converters {
    @TypeConverter
    public static float[][] fromJSONArrayStr(String jsonStr)  {
        try{
            JSONArray jArr = new JSONArray(jsonStr);
            int jArrLen = jArr.length();
            int subArrLen = ((JSONArray) jArr.get(0) ).length();
            float[][] dest = new float[jArrLen][subArrLen];
            for(int i =0; i< jArrLen; i++){
                JSONArray subArr = jArr.getJSONArray(i);
                for(int j=0; j< subArrLen; j++){
                    dest[i][j] = (float) subArr.getDouble(j);
                }
            }
            return dest;
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    @TypeConverter
    public static String fromFloatArray(float[][] floatArray) {
        try{
            int arrLen = floatArray.length;
            int subArrLen = floatArray[0].length;
            JSONArray globalJSON = new JSONArray();
            for (int i=0; i<arrLen; i++){
                JSONArray subJSON = new JSONArray();
                for(int j=0; j<subArrLen; j++){
                    subJSON.put(floatArray[i][j]);
                }
                globalJSON.put(subJSON);
            }
            return globalJSON.toString();
        }
        catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
