package com.example.donelogin.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.donelogin.R;
import com.example.donelogin.model.Account;

import java.util.ArrayList;

/**
 * @author: dangnh
 * Adapter for listview
 */
public class AccountAdapter extends ArrayAdapter<Account> {

    public AccountAdapter(Context context, ArrayList<Account> items) {
        super(context, R.layout.account_list_view_element, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.account_list_view_element, parent, false);
        }
        TextView txtName = (TextView) convertView.findViewById(R.id.txtName);
        TextView txtEmail = (TextView) convertView.findViewById(R.id.txtEmail);
        txtName.setText(getItem(position).getUserName());
        txtEmail.setText(getItem(position).getEmail());
        return convertView;
    }
}
