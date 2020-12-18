package com.example.donelogin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.donelogin.activity.MainActivity;
import com.example.donelogin.adapter.AccountAdapter;
import com.example.donelogin.model.Account;

import java.util.ArrayList;

public class FirstFragment extends Fragment {
    ListView accountListView;
    ArrayList<Account> accountList;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        accountListView = (ListView) getView().findViewById(R.id.account_list_view);
        accountList = new ArrayList<Account>();
        accountList.add(new Account("Nguyễn Hồng Đăng", "dangnh0611@gmail.com"));
        accountList.add(new Account("Dương Thị Minh Thương", "thuongdmt2210@gmail.com"));
        AccountAdapter adapter = new AccountAdapter(getActivity(), accountList);
        accountListView.setAdapter(adapter);

//        view.findViewById(R.id.button_first).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                NavHostFragment.findNavController(FirstFragment.this)
//                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
//            }
//        });
    }
}