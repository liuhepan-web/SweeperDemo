package com.example.sweeperdemo.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sweeperdemo.SweeperConstants;
import com.example.sweeperdemo.map.SweeperMapBinder;
import com.example.sweeperdemo.map.SweeperMapView;
import com.google.android.material.appbar.MaterialToolbar;
import com.sweeper.demo.set.R;
import com.thingclips.smart.optimus.sdk.ThingOptimusSdk;
import com.thingclips.smart.optimus.sweeper.api.IThingSweeperKitSdk;
import com.thingclips.smart.optimus.sweeper.api.IThingSweeperP2P;
import com.thingclips.smart.sweepe.p2p.bean.SweeperP2PBean;
import com.thingclips.smart.sweepe.p2p.callback.SweeperP2PCallback;
import com.thingclips.smart.sweepe.p2p.callback.SweeperP2PDataCallback;
import com.thingclips.smart.sweepe.p2p.manager.DownloadType;

/**
 * P2P realtime map: connect, then observe map / path bytes.
 *
 * @see <a href="https://developer.tuya.com/cn/docs/app-development/android-sweeper-p2p?id=Kb2uowd98x17e">扫地机 P2P</a>
 */
public class SweeperP2pMapActivity extends AppCompatActivity {

    private String devId;
    private IThingSweeperP2P sweeperP2P;
    private SweeperMapBinder binder;
    private TextView tvStatus;
    private TextView tvInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sweeper_map);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.sweeper_p2p_title);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnRefresh).setVisibility(View.GONE);
        findViewById(R.id.btnHistory).setVisibility(View.GONE);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        binder = new SweeperMapBinder(this,
                (SweeperMapView) findViewById(R.id.mapView),
                findViewById(R.id.tvEmpty),
                tvInfo);

        devId = getIntent().getStringExtra(SweeperConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            tvStatus.setText(R.string.sweeper_missing_dev);
            return;
        }
        IThingSweeperKitSdk kit = ThingOptimusSdk.getManager(IThingSweeperKitSdk.class);
        if (kit == null) {
            tvStatus.setText(R.string.sweeper_sdk_missing);
            return;
        }
        sweeperP2P = kit.getSweeperP2PInstance(devId);
        findViewById(R.id.btnStart).setOnClickListener(v -> connectAndDownload());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopDownload());
        connectAndDownload();
    }

    @Override
    protected void onDestroy() {
        stopDownload();
        if (sweeperP2P != null) {
            try {
                sweeperP2P.onDestroyP2P();
            } catch (Throwable ignored) {
                // release best-effort
            }
        }
        if (binder != null) {
            binder.shutdown();
        }
        super.onDestroy();
    }

    private void connectAndDownload() {
        if (sweeperP2P == null) {
            return;
        }
        setStatus(getString(R.string.sweeper_status_connecting));
        sweeperP2P.connectDeviceByP2P(new SweeperP2PCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> setStatus(getString(R.string.sweeper_status_connected)));
                startObserver();
            }

            @Override
            public void onFailure(int code) {
                runOnUiThread(() -> setStatus(getString(R.string.sweeper_error,
                        String.valueOf(code), "P2P connect")));
            }
        });
    }

    private void startObserver() {
        if (sweeperP2P == null) {
            return;
        }
        sweeperP2P.startObserverSweeperDataByP2P(DownloadType.P2PDownloadTypeStill,
                new SweeperP2PCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> setStatus(getString(R.string.sweeper_status_downloading)));
                    }

                    @Override
                    public void onFailure(int code) {
                        runOnUiThread(() -> setStatus(getString(R.string.sweeper_error,
                                String.valueOf(code), "start observer")));
                    }
                },
                new SweeperP2PDataCallback() {
                    @Override
                    public void receiveData(int type, SweeperP2PBean bean) {
                        byte[] data = SweeperMapBinder.p2pData(bean);
                        int beanType = bean == null ? type : bean.getType();
                        int resolved = type >= 0 ? type : beanType;
                        if (resolved == 0) {
                            binder.submitMap(data);
                            runOnUiThread(() -> tvInfo.setText(getString(R.string.sweeper_bytes,
                                    data == null ? 0 : data.length)));
                        } else if (resolved == 1) {
                            binder.submitPath(data, false);
                        } else if (resolved == 3) {
                            binder.submitPath(data, true);
                        }
                    }

                    @Override
                    public void onFailure(int code) {
                        runOnUiThread(() -> setStatus(getString(R.string.sweeper_error,
                                String.valueOf(code), "receive")));
                    }
                });
    }

    private void stopDownload() {
        if (sweeperP2P == null) {
            return;
        }
        try {
            sweeperP2P.stopObserverSweeperDataByP2P(new SweeperP2PCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> setStatus(getString(R.string.sweeper_status_idle)));
                }

                @Override
                public void onFailure(int code) {
                    // Do not destroy P2P here (SDK FAQ).
                }
            });
        } catch (Throwable ignored) {
            // already released
        }
    }

    private void setStatus(String text) {
        tvStatus.setText(text);
    }
}
