package com.example.sweeperdemo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


import com.example.sweeperdemo.SweeperConstants;
import com.example.sweeperdemo.SweeperDeviceHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.sweeper.demo.set.R;
import com.thingclips.smart.sdk.bean.DeviceBean;

/**
 * Hub with P2P and laser-cloud map entries.
 */
public class SweeperPanelActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sweeper_panel);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String devId = getIntent().getStringExtra(SweeperConstants.EXTRA_DEV_ID);
        DeviceBean bean = SweeperDeviceHelper.getDevice(devId);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvOnline = findViewById(R.id.tvOnline);
        TextView tvMeta = findViewById(R.id.tvMeta);
        if (bean == null) {
            tvName.setText(devId == null ? getString(R.string.sweeper_missing_dev) : devId);
            tvOnline.setText(R.string.sweeper_offline);
            tvOnline.setTextColor(ContextCompat.getColor(this, R.color.sweeper_offline));
        } else {
            tvName.setText(TextUtils.isEmpty(bean.getName()) ? bean.getDevId() : bean.getName());
            boolean online = Boolean.TRUE.equals(bean.getIsOnline());
            tvOnline.setText(online ? R.string.sweeper_online : R.string.sweeper_offline);
            tvOnline.setTextColor(ContextCompat.getColor(this,
                    online ? R.color.sweeper_online : R.color.sweeper_offline));
            String category = bean.getCategoryCode();
            if (TextUtils.isEmpty(category)) {
                category = bean.getCategory();
            }
            tvMeta.setText("devId: " + bean.getDevId() + "\ncategory: " + category);
        }

        findViewById(R.id.cardP2p).setOnClickListener(v -> open(SweeperP2pMapActivity.class, devId));
        findViewById(R.id.cardLaser).setOnClickListener(v -> open(SweeperLaserMapActivity.class, devId));
    }

    private void open(Class<?> clazz, String devId) {
        Intent intent = new Intent(this, clazz);
        intent.putExtra(SweeperConstants.EXTRA_DEV_ID, devId);
        startActivity(intent);
    }
}
