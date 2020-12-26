package com.example.donelogin.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.donelogin.R;
import com.example.donelogin.model.AccessRequest;

import java.util.ArrayList;

/**
 * @author: dangnh
 * Adapter for listview
 */
public class AccessRequestAdapter extends ArrayAdapter<AccessRequest> {

    public AccessRequestAdapter(Context context, ArrayList<AccessRequest> items) {
        super(context, R.layout.request_list_view_element, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.request_list_view_element, parent, false);
        }
        TextView txtTime = (TextView) convertView.findViewById(R.id.txtTime2);
        TextView txtName = (TextView) convertView.findViewById(R.id.txtName2);
        TextView txtEmail = (TextView) convertView.findViewById(R.id.txtEmail2);
        txtTime.setText(getItem(position).getRequestTime());
        txtName.setText(getItem(position).getUsername());
        txtEmail.setText(getItem(position).getEmail());
        return convertView;
    }
}
