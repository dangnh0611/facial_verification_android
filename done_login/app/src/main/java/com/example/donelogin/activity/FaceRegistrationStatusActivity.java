package com.example.donelogin.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.donelogin.R;

import org.json.JSONException;
import org.json.JSONObject;

public class FaceRegistrationSuccessActivity extends AppCompatActivity {

    private boolean isSuccess =false;
    private String msg = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_registration_success);
        Button nextBtn = findViewById(R.id.face_registration_success_next);

        Bundle extras = getIntent().getExtras();
        String deviceName = android.os.Build.MODEL;
        String deviceBrand = android.os.Build.MANUFACTURER;
        int deviceAPILevel = Build.VERSION.SDK_INT;
        String deviceOS = Build.VERSION.RELEASE;
        Log.d("DETAIL", deviceName + "__" + deviceBrand + "__" + deviceOS + "__" + deviceAPILevel );

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        SharedPreferences sharedPreferences = getSharedPreferences("APP_CONFIG", Context.MODE_PRIVATE);
        String url = sharedPreferences.getString("SERVER_URL", "") + "/device_registration";
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            String status = response.getString("status");
                            if(status.equals("success")){
                                isSuccess = true;
                                msg = "";
                            }
                            else{
                                isSuccess = false;
                                msg = response.getString("msg");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO: Handle error

                    }
                });

// Add the request to the RequestQueue.
        queue.add(stringRequest);


        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mainActivityIntent = new Intent(FaceRegistrationSuccessActivity.this, MainActivity.class);
                startActivity(mainActivityIntent);
            }
        });
    }
}

