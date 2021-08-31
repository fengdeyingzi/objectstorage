package net.yzjlb.objectstorage;

import android.app.AlertDialog;
import android.app.Dialog;

import androidx.appcompat.app.AppCompatActivity;

import net.yzjlb.objectstorage.util.ProgressDialog;

public class BaseActivity extends AppCompatActivity {

    private Dialog progressDialog = null;

    void showProgressDialog(String info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            return;
        }

        progressDialog = ProgressDialog.show(this, null, info);


    }

    void dismissDialog() {

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }


    }

    void showInfoDialog(String text){
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage(text)
                .setNegativeButton("确定",null)
                .create().show();
    }
}
