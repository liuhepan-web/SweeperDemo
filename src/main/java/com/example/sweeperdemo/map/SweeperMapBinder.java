package com.example.sweeperdemo.map;

import android.app.Activity;
import android.widget.TextView;

import androidx.annotation.Nullable;


import android.util.Base64;

import com.sweeper.demo.set.R;
import com.thingclips.smart.sweepe.p2p.bean.SweeperP2PBean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Applies parsed map / path payloads onto {@link SweeperMapView} off the main thread.
 */
public final class SweeperMapBinder {

    private final Activity activity;
    private final SweeperMapView mapView;
    private final TextView emptyView;
    private final TextView infoView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SweeperMapModel current;

    /**
     * Bind views that display map state.
     *
     * @param activity  host
     * @param mapView   canvas
     * @param emptyView empty hint
     * @param infoView  size / path summary
     */
    public SweeperMapBinder(Activity activity, SweeperMapView mapView, TextView emptyView, TextView infoView) {
        this.activity = activity;
        this.mapView = mapView;
        this.emptyView = emptyView;
        this.infoView = infoView;
    }

    /**
     * Parse occupancy bytes and merge with the current model.
     *
     * @param data map payload
     */
    public void submitMap(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        executor.execute(() -> {
            SweeperMapModel parsed = SweeperMapParser.parseMap(data);
            if (parsed == null) {
                return;
            }
            SweeperMapModel merged = SweeperMapModel.merge(current, parsed);
            postModel(merged);
        });
    }

    /**
     * Parse path bytes and overlay them on the current occupancy grid.
     *
     * @param data  path payload
     * @param isNav navigation vs cleaning path
     */
    public void submitPath(@Nullable byte[] data, boolean isNav) {
        if (data == null || data.length == 0) {
            return;
        }
        executor.execute(() -> {
            List<float[]> path = SweeperMapParser.parsePath(data);
            if (path.isEmpty()) {
                return;
            }
            SweeperMapModel base = current;
            if (base == null) {
                return;
            }
            List<float[]> aligned = SweeperMapParser.alignPath(path, base);
            postModel(base.withPath(aligned, isNav));
        });
    }

    /**
     * Extract P2P payload bytes.
     *
     * @param bean SDK bean
     * @return data or null
     */
    @Nullable
    public static byte[] p2pData(@Nullable SweeperP2PBean bean) {
        if (bean == null) {
            return null;
        }
        try {
            return decodePayload(bean.getData());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Decode P2P string payload (hex or Base64) into bytes.
     *
     * @param payload SDK string
     * @return bytes or null
     */
    @Nullable
    public static byte[] decodePayload(@Nullable String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String trimmed = payload.trim();
        if (isHex(trimmed)) {
            int len = trimmed.length();
            byte[] out = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                out[i / 2] = (byte) Integer.parseInt(trimmed.substring(i, i + 2), 16);
            }
            return out;
        }
        try {
            return Base64.decode(trimmed, Base64.DEFAULT);
        } catch (IllegalArgumentException ignored) {
            return trimmed.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean isHex(String text) {
        if ((text.length() & 1) == 1 || text.length() < 8) {
            return false;
        }
        for (int i = 0; i < Math.min(text.length(), 64); i++) {
            char c = text.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * Release the parser thread.
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    private void postModel(SweeperMapModel model) {
        activity.runOnUiThread(() -> {
            current = model;
            mapView.setModel(model);
            boolean has = model != null && (model.hasGrid() || !model.path.isEmpty());
            emptyView.setVisibility(has ? android.view.View.GONE : android.view.View.VISIBLE);
            if (model != null && model.hasGrid()) {
                infoView.setText(activity.getString(R.string.sweeper_map_info,
                        model.width, model.height, model.path.size(), model.hint));
            }
        });
    }
}
