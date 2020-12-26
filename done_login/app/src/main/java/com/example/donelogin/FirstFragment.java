package com.example.donelogin;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.room.Room;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.donelogin.activity.FaceLoginCameraActivity;
import com.example.donelogin.activity.MainActivity;
import com.example.donelogin.adapter.AccessRequestAdapter;
import com.example.donelogin.adapter.AccountAdapter;
import com.example.donelogin.model.AccessRequest;
import com.example.donelogin.model.Account;
import com.example.donelogin.model.AccountDao;
import com.example.donelogin.model.AppDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

public class FirstFragment extends Fragment {
    ListView accountListView;
    ListView accessRequestListView;
    TextView accoutTextView;
    TextView accessRequestTextView;
    ArrayList<Account> accounts;
    AppDatabase db;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    @SuppressLint("StaticFieldLeak")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        accoutTextView = (TextView) getView().findViewById(R.id.account_text_view);
        accessRequestTextView = (TextView) getView().findViewById(R.id.request_text_view);
        accountListView = (ListView) getView().findViewById(R.id.account_list_view);
        accountListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long arg3) {
                Animation animation1 = new AlphaAnimation(0.3f, 1.0f);
                animation1.setDuration(1000);
                v.startAnimation(animation1);

                Account account = (Account) adapter.getItemAtPosition(position);
                Log.d("DEBUG", account.email);
                String msg = account.username + "\n" + account.email;
                new AlertDialog.Builder(getActivity())
                        .setTitle("Account Detail")
                        .setMessage(msg)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Log.d("TEST", "clicked");
                                    }
                                })
                        .show();
            }
        });

        accessRequestListView = (ListView) getView().findViewById(R.id.request_list_view);
        accessRequestListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long arg3) {
                Animation animation1 = new AlphaAnimation(0.3f, 1.0f);
                animation1.setDuration(1000);
                v.startAnimation(animation1);

                AccessRequest request = (AccessRequest) adapter.getItemAtPosition(position);
                String msg = "Name: " + request.getUsername() + "\nEmail: " + request.getEmail() + "\nRequest at: " + request.getRequestTime();
                new AlertDialog.Builder(getActivity())
                        .setTitle("Access request detail")
                        .setMessage(msg)
                        .setPositiveButton("Accept",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Log.d("TEST", "clicked");
                                        Intent faceLoginCameraIntent = new Intent(getActivity(), FaceLoginCameraActivity.class);
                                        faceLoginCameraIntent.putExtra("mfa_code", request.getMfaCode());
                                        faceLoginCameraIntent.putExtra("device_id", Integer.toString(request.getDeviceId()));
                                        startActivity(faceLoginCameraIntent);
                                    }
                                })
                        .show();
            }
        });

        db = Room.databaseBuilder(getActivity().getApplicationContext(),
                AppDatabase.class, AppDatabase.DB_NAME).build();
        AccountDao accountDao = db.accountDao();

        new AsyncTask<Void, Void, ArrayList<Account>>() {
            @SuppressLint("StaticFieldLeak")
            @Override
            protected ArrayList<Account> doInBackground(Void... voids) {
                List<Account> _accounts = accountDao.getAll();
                if (_accounts != null) {
                    if (_accounts.size() > 0) {
                        ArrayList<Account> accounts = new ArrayList<>(_accounts);
                        return accounts;
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(ArrayList<Account> accounts) {
                if (accounts == null) return;
                AccountAdapter adapter = new AccountAdapter(getActivity(), accounts);
                accountListView.setAdapter(adapter);
                accoutTextView.setText(accounts.size() + " accounts associated with this device.");
            }
        }.execute();


        // Send HTTP request
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(getActivity().getApplicationContext());
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("APP_CONFIG", Context.MODE_PRIVATE);
        String url = sharedPreferences.getString("SERVER_URL", "") + "/mfa_requests";
        String fcmToken = sharedPreferences.getString("FCM_TOKEN", "");
        Log.d("LOGIN REQUEST", url);
        Log.d("LOGIN REQUEST", fcmToken);

        JSONObject requestParam = new JSONObject();
        try {
            requestParam.put("fcm_token", fcmToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d("LOGIN REQUEST", requestParam.toString());
        JsonObjectRequest getAllAccessRequests = new JsonObjectRequest
                (Request.Method.POST, url, requestParam, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.d("LOGIN REQUEST", response.toString());
                            String status = response.getString("status");
                            JSONArray _accessRequests = response.getJSONArray("requests");
                            int accessRequestLen = _accessRequests.length();
                            Log.d("LOGIN REQUEST", "Num request " + accessRequestLen);
                            if (accessRequestLen > 0) {
                                new AsyncTask<Void, Void, ArrayList<AccessRequest>>() {
                                    @SuppressLint("StaticFieldLeak")
                                    @Override
                                    protected ArrayList<AccessRequest> doInBackground(Void... voids) {
                                        ArrayList<AccessRequest> accessRequests = new ArrayList<AccessRequest>();
                                        try {
                                            for (int i = 0; i < accessRequestLen; i++) {
                                                JSONObject obj = _accessRequests.getJSONObject(i);
                                                String mfaCode = obj.getString("mfa_code");
                                                int deviceId = Integer.valueOf(obj.getString("device_id"));
                                                String requestAt = obj.getString("request_at");
                                                Account account = accountDao.getAccountByDeviceId(deviceId);
                                                if (account != null) {
                                                    String userName = account.username;
                                                    String email = account.email;
                                                    AccessRequest newAccessRequest = new AccessRequest(mfaCode, deviceId, requestAt, userName, email);
                                                    accessRequests.add(newAccessRequest);
                                                }
                                            }
                                            return accessRequests;
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            return null;
                                        }
                                    }

                                    @Override
                                    protected void onPostExecute(ArrayList<AccessRequest> accessRequests) {
                                        AccessRequestAdapter accessRequestAdapter = new AccessRequestAdapter(getActivity(), accessRequests);
                                        accessRequestListView.setAdapter(accessRequestAdapter);
                                        accessRequestTextView.setText(accessRequests.size() + " pending requests. Tap to response..");
                                        Log.d("LOGIN REQUEST", accessRequests.size() + " access requests");
                                    }
                                }.execute();
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
        queue.add(getAllAccessRequests);


//        view.findViewById(R.id.button_first).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                NavHostFragment.findNavController(FirstFragment.this)
//                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
//            }
//        });
    }
}