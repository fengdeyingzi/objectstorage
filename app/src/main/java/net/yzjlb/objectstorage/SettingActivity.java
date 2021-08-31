package net.yzjlb.objectstorage;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.yzjlb.objectstorage.util.SharedPreferencesUtil;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        EditText edit_url = findViewById(R.id.edit_url);
        EditText edit_bucket = findViewById(R.id.edit_bucket);
        EditText edit_accessKey = findViewById(R.id.edit_accesskey);
        EditText edit_secretKey = findViewById(R.id.edit_secretkey);
        SharedPreferencesUtil util = new SharedPreferencesUtil(this);
        edit_url.setText(util.getString("obj_url", ""));
        edit_bucket.setText(util.getString("obj_bucket",""));
        edit_accessKey.setText(util.getString("obj_accesskey", ""));
        edit_secretKey.setText(util.getString("obj_secretkey", ""));

    }

    @Override
    protected void onDestroy() {

        EditText edit_url = findViewById(R.id.edit_url);
        EditText edit_bucket = findViewById(R.id.edit_bucket);
        EditText edit_accessKey = findViewById(R.id.edit_accesskey);
        EditText edit_secretKey = findViewById(R.id.edit_secretkey);
        SharedPreferencesUtil util = new SharedPreferencesUtil(this);
        util.setString("obj_url",edit_url.getText().toString());
        util.setString("obj_bucket",edit_bucket.getText().toString());
        util.setString("obj_accesskey", edit_accessKey.getText().toString());
        util.setString("obj_secretkey", edit_secretKey.getText().toString());
        util.commit();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
