package com.example.sweeperdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.sweeperdemo.ui.SweeperPanelActivity;
import com.ipc.demo.base.DeviceNavigator;
import com.sweeper.demo.set.R;

/**
 * Opens the sweeper map hub from the base device list.
 */
public final class SweeperDeviceNavigator implements DeviceNavigator {

    @Override
    public boolean canOpen(@NonNull String devId) {
        return SweeperDeviceHelper.canOpen(devId);
    }

    @Nullable
    @Override
    public String resolveTypeLabel(@NonNull Context context, @NonNull String devId) {
        if (SweeperDeviceHelper.isSweeper(devId)) {
            return context.getString(R.string.sweeper_type_laser);
        }
        return context.getString(R.string.sweeper_type_other);
    }

    @Override
    public void openDevice(@NonNull Activity activity, @NonNull String devId) {
        Intent intent = new Intent(activity, SweeperPanelActivity.class);
        intent.putExtra(SweeperConstants.EXTRA_DEV_ID, devId);
        activity.startActivity(intent);
    }
}
