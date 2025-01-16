/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdksandboxcode_1;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ActivityHandler implements SensorEventListener {
    private static final String DISABLE_BACK_NAVIGATION = "Disable Back Navigation";
    private static final String ENABLE_BACK_NAVIGATION = "Enable Back Navigation";
    private static final String ORIENTATION_PORTRAIT = "Set SCREEN_ORIENTATION_PORTRAIT";
    private static final String ORIENTATION_LANDSCAPE = "Set SCREEN_ORIENTATION_LANDSCAPE";
    private static final String DESTROY_ACTIVITY = "Destroy Activity";
    private static final String OPEN_LANDING_PAGE = "Open Landing Page";
    private final Activity mActivity;
    private final View mView;
    private Button mBackButton;
    private Button mOrientationPortraitButton;
    private Button mOrientationLandscapeButton;
    private Button mDestroyButton;
    private Button mOpenLandingPage;
    private TextView mAxisX;
    private final SensorManager mSensorManager;
    private final Sensor mGyroSensor;
    private final boolean mIsCustomizedSdkContextEnabled;

    public ActivityHandler(
            Activity activity,
            Context sdkContext,
            View view,
            boolean isCustomizedSdkContextEnabled) {
        mActivity = activity;
        mView = view;
        mSensorManager = sdkContext.getSystemService(SensorManager.class);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mIsCustomizedSdkContextEnabled = isCustomizedSdkContextEnabled;
    }

    public void buildLayout() {
        final LinearLayout mediaViewContainer;
        mediaViewContainer = buildLayoutFromResources();

        if (mView.getParent() != null) {
            ((ViewGroup) mView.getParent()).removeView(mView);
        }
        mView.setLayoutParams(
                new ViewGroup.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
        mediaViewContainer.addView(mView);

        // Manually handle window insets post Android V to support edge-to-edge display.
        ViewCompat.setOnApplyWindowInsetsListener(
                mActivity.getWindow().getDecorView(),
                (v, windowInsets) -> {
                    Insets insets =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });

        registerBackEnableButton();
        registerPortraitButton();
        registerLandscapeButton();
        registerDestroyActivityButton();
        registerOpenLandingPageButton();

        registerLifecycleListener();
    }

    /**
     * Using the SDK resources to build the activity layout.
     *
     * @return the container layout for the media view (WebView or media)
     */
    public LinearLayout buildLayoutFromResources() {
        mActivity.setContentView(R.layout.sdk_activity_layout);

        mBackButton = mActivity.findViewById(R.id.back_button);
        mOrientationPortraitButton = mActivity.findViewById(R.id.portrait_orientation_button);
        mOrientationLandscapeButton = mActivity.findViewById(R.id.landscape_orientation_button);
        mDestroyButton = mActivity.findViewById(R.id.destroy_activity_button);
        mOpenLandingPage = mActivity.findViewById(R.id.landing_page_button);
        mAxisX = mActivity.findViewById(R.id.x_axis_button);
        return mActivity.findViewById(R.id.media_view_container);
    }

    private void registerBackEnableButton() {
        final BackNavigationDisabler disabler = new BackNavigationDisabler(mActivity, mBackButton);
        mBackButton.setOnClickListener(v -> disabler.toggle());
    }

    private void registerPortraitButton() {
        mOrientationPortraitButton.setOnClickListener(
                v -> mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }

    private void registerLandscapeButton() {
        mOrientationLandscapeButton.setOnClickListener(
                v -> mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    }

    private void registerDestroyActivityButton() {
        mDestroyButton.setOnClickListener(v -> mActivity.finish());
    }

    private void registerOpenLandingPageButton() {
        mOpenLandingPage.setOnClickListener(
                v -> {
                    Intent visitUrl = new Intent(Intent.ACTION_VIEW);
                    visitUrl.setData(Uri.parse("https://www.google.com"));
                    visitUrl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(visitUrl);
                });
    }

    private void registerLifecycleListener() {
        ActivityHandler activityHandler = this;
        mActivity.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                        makeToast("Activity on create!");
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                        makeToast("Activity on start!");
                    }

                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        makeToast("Activity on resume!");
                        mSensorManager.registerListener(
                                activityHandler, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    }

                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                        makeToast("Activity on pause!");
                        mSensorManager.unregisterListener(activityHandler);
                    }

                    @Override
                    public void onActivityStopped(@NonNull Activity activity) {
                        makeToast("Activity on stop!");
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity, @NonNull Bundle outState) {
                        makeToast("Activity on saveInstanceState!");
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                        makeToast("Activity on destroy!");
                    }
                });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mAxisX.setText("axisX = %s".formatted(event.values[0]));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private class BackNavigationDisabler {
        private final OnBackInvokedDispatcher mDispatcher;

        private final OnBackInvokedCallback mBackNavigationDisablingCallback;

        private boolean mBackNavigationDisabled; // default is back enabled.

        private final Button mBackButton;

        BackNavigationDisabler(Activity activity, Button backButton) {
            mDispatcher = activity.getOnBackInvokedDispatcher();
            mBackNavigationDisablingCallback = () -> makeToast("Can not go back!");
            mBackButton = backButton;
        }

        public synchronized void toggle() {
            if (mBackNavigationDisabled) {
                mDispatcher.unregisterOnBackInvokedCallback(mBackNavigationDisablingCallback);
                mBackButton.setText(DISABLE_BACK_NAVIGATION);
            } else {
                mDispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, mBackNavigationDisablingCallback);
                mBackButton.setText(ENABLE_BACK_NAVIGATION);
            }
            mBackNavigationDisabled = !mBackNavigationDisabled;
        }
    }

    private void makeToast(String message) {
        mActivity.runOnUiThread(
                () -> Toast.makeText(mActivity, message, Toast.LENGTH_SHORT).show());
    }
}
