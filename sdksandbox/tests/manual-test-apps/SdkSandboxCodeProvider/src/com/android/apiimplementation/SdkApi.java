/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.apiimplementation;

import android.app.Activity;
import android.app.Application;
import android.app.sdksandbox.interfaces.IActivityStarter;
import android.app.sdksandbox.interfaces.ISdkApi;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.modules.utils.build.SdkLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SdkApi extends ISdkApi.Stub {
    private final Context mContext;

    public SdkApi(Context sdkContext) {
        mContext = sdkContext;
    }

    @Override
    public ParcelFileDescriptor getFileDescriptor(String inputValue) {
        try {
            final String fileName = "testParcelFileDescriptor";
            FileOutputStream fout = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            // Writing inputValue String to a file
            for (int i = 0; i < inputValue.length(); i++) {
                fout.write((int) inputValue.charAt(i));
            }
            fout.close();
            File file = new File(mContext.getFilesDir(), fileName);
            ParcelFileDescriptor pFd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            return pFd;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String createFile(int sizeInMb) throws RemoteException {
        Path path;
        if (SdkLevel.isAtLeastU()) {
            // U device should be have customized sdk context that allows all storage APIs on
            // context to utlize per-sdk storage
            path = Paths.get(mContext.getFilesDir().getPath(), "file.txt");
            // Verify per-sdk storage is being used
            if (!path.startsWith(mContext.getDataDir().getPath())) {
                throw new IllegalStateException("Customized Sdk Context is not being used");
            }
        } else {
            path = Paths.get(mContext.getDataDir().getPath(), "file.txt");
        }

        try {
            Files.deleteIfExists(path);
            Files.createFile(path);
            final byte[] buffer = new byte[sizeInMb * 1024 * 1024];
            Files.write(path, buffer);

            final File file = new File(path.toString());
            final long actualFilzeSize = file.length() / (1024 * 1024);
            return "Created " + actualFilzeSize + " MB file successfully";
        } catch (IOException e) {
            throw new RemoteException(e);
        }
    }

    @Override
    public String getMessage() {
        return "Message from sdk in the sandbox process";
    }

    @Override
    public String getSyncedSharedPreferencesString(String key) {
        return getClientSharedPreferences().getString(key, "");
    }

    @Override
    public void startActivity(IActivityStarter iActivityStarter) throws RemoteException {
        if (!SdkLevel.isAtLeastU()) {
            throw new IllegalStateException("Starting activity requires Android U or above!");
        }
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);
        IBinder token =
                controller.registerSdkSandboxActivityHandler(activity -> populateView(activity));
        iActivityStarter.startActivity(token);
    }

    private void populateView(Activity activity) {
        // creating LinearLayout
        LinearLayout linLayout = new LinearLayout(activity);
        // specifying vertical orientation
        linLayout.setOrientation(LinearLayout.VERTICAL);
        // creating LayoutParams
        LinearLayout.LayoutParams linLayoutParam =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
        TextView textView = new TextView(activity);
        textView.setText("This is an Activity running inside the sandbox process!");
        linLayout.addView(textView);

        final Button toggleBackNavigationButton = createToggleBackNavigationButton(activity);
        linLayout.addView(toggleBackNavigationButton);

        final Button portraitOrientationButton = createRequestPortraitOrientationButton(activity);
        final Button landscapeOrientationButton = createRequestLandscapeOrientationButton(activity);
        linLayout.addView(portraitOrientationButton);
        linLayout.addView(landscapeOrientationButton);

        final Button finishActivityButton = createFinishActivityButton(activity);
        linLayout.addView(finishActivityButton);

        final TextView statesChangeLog = createStatesChangeLog(activity);
        linLayout.addView(statesChangeLog);

        // set LinearLayout as a root element of the screen
        activity.setContentView(linLayout, linLayoutParam);
    }

    private Button createToggleBackNavigationButton(Activity activity) {
        final Button button = new Button(activity);
        final BackNavigationDisabler disabler = new BackNavigationDisabler(activity);
        button.setOnClickListener(
                v -> {
                    disabler.toggle();
                    button.setText(
                            toggleBackNavigationButtonText(disabler.isBackNavigationDisabled()));
                });
        button.setText(toggleBackNavigationButtonText(disabler.isBackNavigationDisabled()));
        return button;
    }

    private Button createRequestLandscapeOrientationButton(Activity activity) {
        final Button button = new Button(activity);
        button.setText("Set SCREEN_ORIENTATION_LANDSCAPE");

        button.setOnClickListener(
                v -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));

        return button;
    }

    private Button createRequestPortraitOrientationButton(Activity activity) {
        final Button button = new Button(activity);
        button.setText("Set SCREEN_ORIENTATION_PORTRAIT");

        button.setOnClickListener(
                v -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));

        return button;
    }

    private Button createFinishActivityButton(Activity activity) {
        final Button button = new Button(activity);
        button.setText("Finish Activity");

        button.setOnClickListener(v -> activity.finish());

        return button;
    }

    private TextView createStatesChangeLog(Activity activity) {
        final TextView textView = new TextView(activity);

        activity.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivityCreated");
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivityStarted");
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivityResumed");
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivityPaused");
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivityStopped");
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivitySaveInstanceState");
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        textView.append(System.lineSeparator());
                        textView.append("onActivityDestroyed");
                    }
                });

        return textView;
    }

    private String toggleBackNavigationButtonText(boolean isBackNavigationDisabled) {
        if (isBackNavigationDisabled) {
            return "Enable Back Navigation";
        } else {
            return "Disable Back Navigation";
        }
    }

    private SharedPreferences getClientSharedPreferences() {
        return mContext.getSystemService(SdkSandboxController.class).getClientSharedPreferences();
    }

    private static class BackNavigationDisabler {

        private final OnBackInvokedDispatcher mDispatcher;

        private final OnBackInvokedCallback mBackNavigationDisablingCallback;

        private boolean mBackNavigationDisabled;

        BackNavigationDisabler(Activity activity) {
            mDispatcher = activity.getOnBackInvokedDispatcher();
            mBackNavigationDisablingCallback =
                    () -> {
                        // do nothing
                    };
        }

        public void toggle() {
            if (mBackNavigationDisabled) {
                mDispatcher.unregisterOnBackInvokedCallback(mBackNavigationDisablingCallback);
            } else {
                mDispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, mBackNavigationDisablingCallback);
            }
            mBackNavigationDisabled = !mBackNavigationDisabled;
        }

        public boolean isBackNavigationDisabled() {
            return mBackNavigationDisabled;
        }
    }
}
