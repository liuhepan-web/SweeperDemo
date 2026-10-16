package com.example.sweeperdemo;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.sdk.bean.DeviceBean;
import com.thingclips.smart.sdk.bean.ProductBean;

/**
 * Identifies robot vacuum devices from product category.
 * Sweeper products typically use category {@code sd}.
 */
public final class SweeperDeviceHelper {

    private SweeperDeviceHelper() {
    }

    /**
     * Whether the device can open the sweeper map panel.
     *
     * @param devId device id
     * @return true if device exists
     */
    public static boolean canOpen(@Nullable String devId) {
        return getDevice(devId) != null;
    }

    /**
     * Whether the product looks like a vacuum (category contains {@code sd}).
     *
     * @param devId device id
     * @return true if category suggests sweeper
     */
    public static boolean isSweeper(@Nullable String devId) {
        DeviceBean bean = getDevice(devId);
        if (bean == null) {
            return false;
        }
        String category = firstNonEmpty(bean.getCategory(), categoryOf(bean.getProductBean()));
        String code = bean.getCategoryCode();
        String merged = ((category == null ? "" : category) + " " + (code == null ? "" : code))
                .toLowerCase();
        return merged.contains("sd")
                || merged.contains("sweep")
                || merged.contains("vacuum")
                || merged.contains("robot");
    }

    /**
     * Load cached device bean.
     *
     * @param devId device id
     * @return bean or null
     */
    @Nullable
    public static DeviceBean getDevice(@Nullable String devId) {
        if (TextUtils.isEmpty(devId)) {
            return null;
        }
        try {
            return ThingHomeSdk.getDataInstance().getDeviceBean(devId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String categoryOf(@Nullable ProductBean product) {
        if (product == null) {
            return null;
        }
        try {
            return product.getCategory();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String firstNonEmpty(String a, String b) {
        if (!TextUtils.isEmpty(a)) {
            return a;
        }
        return TextUtils.isEmpty(b) ? null : b;
    }
}
