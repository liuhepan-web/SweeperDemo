package com.example.sweeperdemo;

import android.app.Application;
import android.util.Log;

import com.ipc.demo.base.BaseHub;
import com.ipc.demo.base.BaseSdk;
import com.thingclips.smart.optimus.sdk.ThingOptimusSdk;

/**
 * Application entry. Reuses base login / home / pairing, then inits Sweeper SDK.
 *
 * @see <a href="https://developer.tuya.com/cn/docs/app-development/Smart-Sweep-SDK-integrated?id=Kdjvz86ribf9l">SDK 集成</a>
 */
public class SweeperDemoApplication extends Application {

    private static final String TAG = SweeperConstants.TAG;

    @Override
    public void onCreate() {
        super.onCreate();
        BaseHub.setDeviceNavigator(new SweeperDeviceNavigator());
        BaseSdk.init(this, true);
        try {
            ThingOptimusSdk.init(this);
            Log.i(TAG, "ThingOptimusSdk init ok");
        } catch (Throwable t) {
            Log.e(TAG, "ThingOptimusSdk init failed", t);
        }
    }
}
