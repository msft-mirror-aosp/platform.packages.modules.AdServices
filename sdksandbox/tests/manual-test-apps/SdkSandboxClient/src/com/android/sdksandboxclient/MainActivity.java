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

package com.android.sdksandboxclient;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SURFACE_PACKAGE;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.RequestSurfacePackageException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.interfaces.IActivityStarter;
import android.app.sdksandbox.interfaces.ISdkApi;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.modules.utils.build.SdkLevel;

import java.util.Set;

public class MainActivity extends Activity {
    // TODO(b/253202014): Add toggle button
    private static final Boolean IS_WEBVIEW_TESTING_ENABLED = false;
    private static final String SDK_NAME =
            IS_WEBVIEW_TESTING_ENABLED
                    ? "com.android.sdksandboxcode_webview"
                    : "com.android.sdksandboxcode";
    private static final String MEDIATEE_SDK_NAME = "com.android.sdksandboxcode_mediatee";
    private static final String TAG = "SdkSandboxClientMainActivity";

    private static final String VIEW_TYPE_KEY = "view-type";
    private static final String VIDEO_VIEW_VALUE = "video-view";
    private static final String VIDEO_URL_KEY = "video-url";

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final String EXTRA_SDK_SDK_ENABLED_KEY = "sdkSdkCommEnabled";
    private static final String DROPDOWN_KEY_SDK_SANDBOX = "SDK_IN_SANDBOX";
    private static final String DROPDOWN_KEY_SDK_APP = "SDK_IN_APP";
    private static final String APP_OWNED_SDK_NAME = "app-sdk-1";

    private static String sVideoUrl;

    private boolean mSdksLoaded = false;
    private boolean mSdkToSdkCommEnabled = false;
    private SdkSandboxManager mSdkSandboxManager;

    private Button mLoadButton;
    private Button mRenderButton;
    private Button mCreateFileButton;
    private Button mPlayVideoButton;
    private Button mSyncKeysButton;
    private Button mSdkToSdkCommButton;
    private Button mStartActivity;

    private SurfaceView mRenderedView;

    private SandboxedSdk mSandboxedSdk;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableStrictMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSdkSandboxManager = getApplicationContext().getSystemService(SdkSandboxManager.class);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            sVideoUrl = extras.getString(VIDEO_URL_KEY);
        }

        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setZOrderOnTop(true);
        mRenderedView.setVisibility(View.INVISIBLE);

        mLoadButton = findViewById(R.id.load_code_button);
        mRenderButton = findViewById(R.id.request_surface_button);
        mCreateFileButton = findViewById(R.id.create_file_button);
        mPlayVideoButton = findViewById(R.id.play_video_button);
        mSyncKeysButton = findViewById(R.id.sync_keys_button);
        mSdkToSdkCommButton = findViewById(R.id.enable_sdk_sdk_button);
        mStartActivity = findViewById(R.id.start_activity);

        registerLoadSdkProviderButton();
        registerLoadSurfacePackageButton();
        registerCreateFileButton();
        registerPlayVideoButton();
        registerSyncKeysButton();
        registerSdkToSdkButton();
        registerStartActivityButton();
        // Register AppOwnedSdkInterface
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_NAME, (long) 1.01, new AppOwnedSdkApi()));
    }

    private void registerLoadSdkProviderButton() {
        mLoadButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        resetStateForLoadSdkButton();
                        return;
                    }
                    // Register for sandbox death event.
                    mSdkSandboxManager.addSdkSandboxProcessDeathCallback(
                            Runnable::run, () -> makeToast("Sdk Sandbox process died"));

                    Bundle params = new Bundle();
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> receiver =
                            new OutcomeReceiver<SandboxedSdk, LoadSdkException>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    mSdksLoaded = true;
                                    mSandboxedSdk = sandboxedSdk;
                                    makeToast("First SDK Loaded successfully!");
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    makeToast("Failed: " + error);
                                }
                            };
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> mediateeReceiver =
                            new OutcomeReceiver<SandboxedSdk, LoadSdkException>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    makeToast("All SDKs Loaded successfully!");
                                    Log.d(TAG, "All SDKs Loaded successfully!");
                                    mLoadButton.setText("Unload SDKs");
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    makeToast("Failed: " + error);
                                    resetStateForLoadSdkButton();
                                }
                            };
                    mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, receiver);
                    mSdkSandboxManager.loadSdk(
                            MEDIATEE_SDK_NAME, params, Runnable::run, mediateeReceiver);
                });
    }

    private void resetStateForLoadSdkButton() {
        mSdkSandboxManager.unloadSdk(SDK_NAME);
        mSdkSandboxManager.unloadSdk(MEDIATEE_SDK_NAME);
        mLoadButton.setText("Load SDKs");
        mSdksLoaded = false;
    }

    private void registerLoadSurfacePackageButton() {
        OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver =
                new RequestSurfacePackageReceiver();
        mRenderButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        sHandler.post(
                                () -> {
                                    mSdkSandboxManager.requestSurfacePackage(
                                            SDK_NAME,
                                            getRequestSurfacePackageParams(null),
                                            Runnable::run,
                                            receiver);
                                });
                    } else {
                        makeToast("Sdk is not loaded");
                    }
                });
    }

    private void registerCreateFileButton() {
        mCreateFileButton.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        makeToast("Sdk is not loaded");
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Set size in MB");
                    final EditText input = new EditText(this);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);
                    builder.setPositiveButton(
                            "Create",
                            (dialog, which) -> {
                                int sizeInMb = -1;
                                try {
                                    sizeInMb = Integer.parseInt(input.getText().toString());
                                } catch (Exception ignore) {
                                }
                                if (sizeInMb <= 0 || sizeInMb > 100) {
                                    makeToast("Please provide a value between 1 and 100");
                                    return;
                                }
                                IBinder binder = mSandboxedSdk.getInterface();
                                ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                                try {
                                    String response = sdkApi.createFile(sizeInMb);
                                    makeToast(response);
                                } catch (RemoteException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    builder.show();
                });
    }

    private void registerPlayVideoButton() {
        if (sVideoUrl == null) {
            mPlayVideoButton.setVisibility(View.GONE);
            return;
        }

        OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver =
                new RequestSurfacePackageReceiver();
        mPlayVideoButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        sHandler.post(
                                () -> {
                                    Bundle params = getRequestSurfacePackageParams(null);
                                    params.putString(VIEW_TYPE_KEY, VIDEO_VIEW_VALUE);
                                    params.putString(VIDEO_URL_KEY, sVideoUrl);
                                    mSdkSandboxManager.requestSurfacePackage(
                                            SDK_NAME, params, Runnable::run, receiver);
                                });
                    } else {
                        makeToast("Sdk is not loaded");
                    }
                });
    }

    private void registerSdkToSdkButton() {
        mSdkToSdkCommButton.setOnClickListener(
                v -> {
                    mSdkToSdkCommEnabled = !mSdkToSdkCommEnabled;
                    if (mSdkToSdkCommEnabled) {
                        mSdkToSdkCommButton.setText("Disable SDK to SDK comm");
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Choose winning SDK");

                        String[] items =
                                new String[] {DROPDOWN_KEY_SDK_SANDBOX, DROPDOWN_KEY_SDK_APP};
                        ArrayAdapter<String> adapter =
                                new ArrayAdapter<>(
                                        this, android.R.layout.simple_spinner_dropdown_item, items);
                        final Spinner dropdown = new Spinner(this);
                        dropdown.setAdapter(adapter);

                        LinearLayout linearLayout = new LinearLayout(this);
                        linearLayout.setOrientation(1); // 1 is for vertical orientation
                        linearLayout.addView(dropdown);
                        builder.setView(linearLayout);

                        builder.setPositiveButton(
                                "Request SP",
                                (dialog, which) -> {
                                    OutcomeReceiver<Bundle, RequestSurfacePackageException>
                                            receiver = new RequestSurfacePackageReceiver();
                                    mSdkSandboxManager.requestSurfacePackage(
                                            SDK_NAME,
                                            getRequestSurfacePackageParams(
                                                    dropdown.getSelectedItem().toString()),
                                            Runnable::run,
                                            receiver);
                                });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                        builder.show();
                    } else {
                        mSdkToSdkCommButton.setText("Enable SDK to SDK comm");
                        makeToast("Sdk to Sdk Comm Disabled");
                    }
                });
    }

    private void registerSyncKeysButton() {
        mSyncKeysButton.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        makeToast("Sdk is not loaded");
                        return;
                    }

                    final AlertDialog.Builder alert = new AlertDialog.Builder(this);

                    alert.setTitle("Set the key and value to sync");
                    LinearLayout linearLayout = new LinearLayout(this);
                    linearLayout.setOrientation(1); // 1 is for vertical orientation
                    final EditText inputKey = new EditText(this);
                    final EditText inputValue = new EditText(this);
                    linearLayout.addView(inputKey);
                    linearLayout.addView(inputValue);
                    alert.setView(linearLayout);

                    alert.setPositiveButton(
                            "Sync",
                            (dialog, which) -> {
                                sHandler.post(
                                        () -> {
                                            final SharedPreferences pref =
                                                    PreferenceManager.getDefaultSharedPreferences(
                                                            getApplicationContext());
                                            String keyToSync = inputKey.getText().toString();
                                            String valueToSync = inputValue.getText().toString();
                                            pref.edit().putString(keyToSync, valueToSync).commit();
                                            mSdkSandboxManager.addSyncedSharedPreferencesKeys(
                                                    Set.of(keyToSync));
                                            IBinder binder = mSandboxedSdk.getInterface();
                                            ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                                            try {
                                                // Allow some time for data to sync
                                                Thread.sleep(1000);
                                                String syncedKeysValue =
                                                        sdkApi.getSyncedSharedPreferencesString(
                                                                keyToSync);
                                                if (syncedKeysValue.equals(valueToSync)) {
                                                    makeToast(
                                                            "Key was synced successfully\n"
                                                                    + "Key is : "
                                                                    + keyToSync
                                                                    + " Value is : "
                                                                    + syncedKeysValue);
                                                } else {
                                                    makeToast("Key was not synced");
                                                }
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        });
                            });
                    alert.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    alert.show();
                });
    }

    private void registerStartActivityButton() {
        mStartActivity.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        makeToast("Sdk is not loaded");
                        return;
                    }
                    if (!SdkLevel.isAtLeastU()) {
                        makeToast("Device should have Android U or above!");
                        return;
                    }
                    IBinder binder = mSandboxedSdk.getInterface();
                    ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                    try {
                        sdkApi.startActivity(new ActivityStarter(this, mSdkSandboxManager));
                    } catch (RemoteException e) {
                        makeToast("Failed to startActivity: " + e.getMessage());
                        Log.e(TAG, "Failed to startActivity: " + e.getMessage(), e);
                    }
                });
    }

    private Bundle getRequestSurfacePackageParams(String commType) {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, mRenderedView.getWidth());
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, mRenderedView.getHeight());
        params.putInt(EXTRA_DISPLAY_ID, getDisplay().getDisplayId());
        params.putBinder(EXTRA_HOST_TOKEN, mRenderedView.getHostToken());
        params.putString(EXTRA_SDK_SDK_ENABLED_KEY, commType);
        return params;
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private class RequestSurfacePackageReceiver
            implements OutcomeReceiver<Bundle, RequestSurfacePackageException> {

        @Override
        public void onResult(Bundle result) {
            sHandler.post(
                    () -> {
                        SurfacePackage surfacePackage =
                                result.getParcelable(EXTRA_SURFACE_PACKAGE, SurfacePackage.class);
                        mRenderedView.setChildSurfacePackage(surfacePackage);
                        mRenderedView.setVisibility(View.VISIBLE);
                    });
            makeToast("Rendered surface view");
        }

        @Override
        public void onError(@NonNull RequestSurfacePackageException error) {
            makeToast("Failed: " + error.getMessage());
            Log.e(TAG, error.getMessage(), error);
        }
    }

    private static class ActivityStarter extends IActivityStarter.Stub {
        private final Activity mActivity;
        private final SdkSandboxManager mSdkSandboxManager;

        ActivityStarter(Activity activity, SdkSandboxManager manager) {
            this.mActivity = activity;
            this.mSdkSandboxManager = manager;
        }

        @Override
        public void startActivity(IBinder token) throws RemoteException {
            mSdkSandboxManager.startSdkSandboxActivity(mActivity, token);
        }
    }

    private void enableStrictMode() {
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder()
                        .detectAll()
                        .penaltyLog()
                        .penaltyDeath()
                        .build());
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().penaltyDeath().build());
    }
}
