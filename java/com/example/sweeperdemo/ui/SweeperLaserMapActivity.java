package com.example.sweeperdemo.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import com.example.sweeperdemo.SweeperConstants;
import com.example.sweeperdemo.map.SweeperMapBinder;
import com.example.sweeperdemo.map.SweeperMapView;
import com.google.android.material.appbar.MaterialToolbar;
import com.sweeper.demo.set.R;
import com.thingclips.smart.android.sweeper.IThingByteDataListener;
import com.thingclips.smart.android.sweeper.IThingCloudConfigCallback;
import com.thingclips.smart.android.sweeper.IThingSweeperDataListener;
import com.thingclips.smart.android.sweeper.bean.SweeperDataBean;
import com.thingclips.smart.android.sweeper.bean.SweeperHistory;
import com.thingclips.smart.android.sweeper.bean.SweeperHistoryBean;
import com.thingclips.smart.android.sweeper.bean.SweeperPathBean;
import com.thingclips.smart.home.sdk.callback.IThingResultCallback;
import com.thingclips.smart.optimus.sdk.ThingOptimusSdk;
import com.thingclips.smart.optimus.sweeper.api.IThingSweeperKit;
import com.thingclips.smart.optimus.sweeper.api.IThingSweeperKitSdk;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Laser cloud map: init OSS config, pull current files, listen for path updates.
 *
 * @see <a href="https://developer.tuya.com/cn/docs/app-development/lasersweeper?id=Ka6o1iaxtu71o">激光扫地机</a>
 */
public class SweeperLaserMapActivity extends AppCompatActivity {

    private String devId;
    private String bucket;
    private IThingSweeperKit sweeperKit;
    private SweeperMapBinder binder;
    private TextView tvStatus;
    private boolean channelStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sweeper_map);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.sweeper_laser_title);
        toolbar.setNavigationOnClickListener(v -> finish());
        tvStatus = findViewById(R.id.tvStatus);
        binder = new SweeperMapBinder(this,
                (SweeperMapView) findViewById(R.id.mapView),
                findViewById(R.id.tvEmpty),
                findViewById(R.id.tvInfo));

        devId = getIntent().getStringExtra(SweeperConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            tvStatus.setText(R.string.sweeper_missing_dev);
            return;
        }
        IThingSweeperKitSdk kitSdk = ThingOptimusSdk.getManager(IThingSweeperKitSdk.class);
        if (kitSdk == null) {
            tvStatus.setText(R.string.sweeper_sdk_missing);
            return;
        }
        sweeperKit = kitSdk.getSweeperInstance();
        findViewById(R.id.btnStart).setOnClickListener(v -> initCloudAndListen());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopChannel());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> loadCurrentPath());
        findViewById(R.id.btnHistory).setOnClickListener(v -> loadHistory());
        initCloudAndListen();
    }

    @Override
    protected void onDestroy() {
        stopChannel();
        if (binder != null) {
            binder.shutdown();
        }
        super.onDestroy();
    }

    private void initCloudAndListen() {
        if (sweeperKit == null) {
            return;
        }
        tvStatus.setText(R.string.sweeper_status_connecting);
        sweeperKit.initCloudConfig(devId, new IThingCloudConfigCallback() {
            @Override
            public void onConfigSuccess(String result) {
                bucket = result;
                runOnUiThread(() -> tvStatus.setText(R.string.sweeper_status_cloud_ok));
                loadCurrentPath();
                startChannel();
            }

            @Override
            public void onConfigError(String errorCode, String errorMessage) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.sweeper_error,
                        errorCode, errorMessage)));
            }
        });
    }

    private void loadCurrentPath() {
        if (sweeperKit == null || TextUtils.isEmpty(bucket)) {
            return;
        }
        sweeperKit.getSweeperCurrentPath(devId, new IThingResultCallback<SweeperPathBean>() {
            @Override
            public void onSuccess(SweeperPathBean result) {
                if (result == null) {
                    return;
                }
                downloadFile(result.getMapPath(), true);
                downloadFile(result.getRoutePath(), false);
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.sweeper_error,
                        errorCode, errorMessage)));
            }
        });
    }

    private void startChannel() {
        if (sweeperKit == null || channelStarted) {
            return;
        }
        channelStarted = true;
        sweeperKit.startConnectSweeperDataChannel(new IThingSweeperDataListener() {
            @Override
            public void onSweeperDataReceived(SweeperDataBean bean) {
                if (bean == null || TextUtils.isEmpty(bean.getMapPath())) {
                    return;
                }
                boolean isMap = bean.getMapType() == 0;
                downloadFile(bean.getMapPath(), isMap);
            }
        });
        runOnUiThread(() -> tvStatus.setText(R.string.sweeper_status_downloading));
    }

    private void stopChannel() {
        if (sweeperKit == null) {
            return;
        }
        try {
            sweeperKit.stopConnectSweeperDataChannel();
        } catch (Throwable ignored) {
            // already stopped
        }
        channelStarted = false;
        tvStatus.setText(R.string.sweeper_status_idle);
    }

    private void downloadFile(String path, boolean isMap) {
        if (sweeperKit == null || TextUtils.isEmpty(bucket) || TextUtils.isEmpty(path)) {
            return;
        }
        sweeperKit.getSweeperByteData(bucket, path, new IThingByteDataListener() {
            @Override
            public void onSweeperByteData(byte[] data) {
                if (isMap) {
                    binder.submitMap(data);
                } else {
                    binder.submitPath(data, false);
                }
            }

            @Override
            public void onFailure(int code, String msg) {
                if (!TextUtils.isEmpty(bucket)) {
                    sweeperKit.updateCloudConfig(devId, new IThingCloudConfigCallback() {
                        @Override
                        public void onConfigSuccess(String result) {
                            bucket = result;
                        }

                        @Override
                        public void onConfigError(String errorCode, String errorMessage) {
                            runOnUiThread(() -> tvStatus.setText(getString(R.string.sweeper_error,
                                    errorCode, errorMessage)));
                        }
                    });
                }
                runOnUiThread(() -> tvStatus.setText(getString(R.string.sweeper_error,
                        String.valueOf(code), msg)));
            }
        });
    }

    private void loadHistory() {
        if (sweeperKit == null) {
            return;
        }
        sweeperKit.getSweeperHistoryData(devId, 20, 0, new IThingResultCallback<SweeperHistory>() {
            @Override
            public void onSuccess(SweeperHistory result) {
                List<SweeperHistoryBean> datas = result == null ? null : result.getDatas();
                if (datas == null || datas.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(SweeperLaserMapActivity.this,
                            R.string.sweeper_history_empty, Toast.LENGTH_SHORT).show());
                    return;
                }
                runOnUiThread(() -> showHistoryDialog(datas));
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.sweeper_error,
                        errorCode, errorMessage)));
            }
        });
    }

    private void showHistoryDialog(List<SweeperHistoryBean> datas) {
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        CharSequence[] labels = new CharSequence[datas.size()];
        for (int i = 0; i < datas.size(); i++) {
            SweeperHistoryBean item = datas.get(i);
            labels[i] = fmt.format(new Date(item.getTime())) + "  " + item.getId();
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.sweeper_history_title)
                .setItems(labels, (dialog, which) -> openHistory(datas.get(which)))
                .show();
    }

    private void openHistory(SweeperHistoryBean item) {
        if (item == null || sweeperKit == null) {
            return;
        }
        String useBucket = TextUtils.isEmpty(item.getBucket()) ? bucket : item.getBucket();
        sweeperKit.getSweeperByteData(useBucket, item.getFile(), new IThingByteDataListener() {
            @Override
            public void onSweeperByteData(byte[] data) {
                applyHistoryFile(data, item.getExtend());
            }

            @Override
            public void onFailure(int code, String msg) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.sweeper_error,
                        String.valueOf(code), msg)));
            }
        });
    }

    private void applyHistoryFile(byte[] data, String extend) {
        if (data == null) {
            return;
        }
        int layoutSize = 0;
        int routeSize = 0;
        try {
            JSONObject json = JSON.parseObject(extend);
            if (json != null) {
                layoutSize = json.getIntValue("layout_size");
                routeSize = json.getIntValue("route_size");
            }
        } catch (Throwable ignored) {
            layoutSize = 0;
        }
        if (layoutSize > 0 && layoutSize <= data.length) {
            byte[] map = new byte[layoutSize];
            System.arraycopy(data, 0, map, 0, layoutSize);
            binder.submitMap(map);
            int end = Math.min(data.length, layoutSize + routeSize);
            if (routeSize > 0 && end > layoutSize) {
                byte[] route = new byte[end - layoutSize];
                System.arraycopy(data, layoutSize, route, 0, route.length);
                binder.submitPath(route, false);
            }
            return;
        }
        binder.submitMap(data);
    }
}
