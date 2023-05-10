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
import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;

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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.StrictMode;
import android.text.InputType;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;

import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
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
    private static final String VIEW_TYPE_INFLATED_VIEW = "view-type-inflated-view";

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final String EXTRA_SDK_SDK_ENABLED_KEY = "sdkSdkCommEnabled";
    private static final String DROPDOWN_KEY_SDK_SANDBOX = "SDK_IN_SANDBOX";
    private static final String DROPDOWN_KEY_SDK_APP = "SDK_IN_APP";
    private static final String APP_OWNED_SDK_NAME = "app-sdk-1";

    // Saved instance state keys
    private static final String SDKS_LOADED_KEY = "sdks_loaded";

    private boolean mSdksLoaded = false;
    private boolean mSdkToSdkCommEnabled = false;
    private SdkSandboxManager mSdkSandboxManager;

    private Button mLoadSdksButton;
    private Button mDeathCallbackButton;
    private Button mNewBannerAdButton;
    private ImageButton mBannerAdOptionsButton;
    private Button mCreateFileButton;
    private Button mPlayVideoButton;
    private Button mSyncKeysButton;
    private Button mSdkToSdkCommButton;
    private Button mNewFullScreenAd;

    private SurfaceView mBottomView;

    private SandboxedSdk mSandboxedSdk;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableStrictMode();
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSdksLoaded = savedInstanceState.getBoolean(SDKS_LOADED_KEY);
        }

        Executors.newSingleThreadExecutor()
                .execute(
                        () -> {
                            Looper.prepare();
                            mSharedPreferences =
                                    PreferenceManager.getDefaultSharedPreferences(
                                            MainActivity.this);

                            handleExtras();
                            PreferenceManager.setDefaultValues(
                                    this, R.xml.banner_preferences, false);
                        });

        setContentView(R.layout.activity_main);
        mSdkSandboxManager = getApplicationContext().getSystemService(SdkSandboxManager.class);

        mBottomView = findViewById(R.id.bottom_view);
        mBottomView.setZOrderOnTop(true);
        mBottomView.setVisibility(View.INVISIBLE);

        mLoadSdksButton = findViewById(R.id.load_sdks_button);
        mDeathCallbackButton = findViewById(R.id.register_death_callback_button);

        mNewBannerAdButton = findViewById(R.id.new_banner_ad_button);
        mBannerAdOptionsButton = findViewById(R.id.banner_ad_options_button);
        mNewFullScreenAd = findViewById(R.id.new_fullscreen_ad_button);

        mCreateFileButton = findViewById(R.id.create_file_button);
        mSyncKeysButton = findViewById(R.id.sync_keys_button);
        mSdkToSdkCommButton = findViewById(R.id.enable_sdk_sdk_button);

        registerLoadSdksButton();
        registerDeathCallbackButton();

        registerNewBannerAdButton();
        registerBannerAdOptionsButton();
        registerNewFullscreenAdButton();

        registerCreateFileButton();
        registerSyncKeysButton();
        registerSdkToSdkButton();

        if (savedInstanceState == null) {
            // Register AppOwnedSdkInterface when activity first created
            mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                    new AppOwnedSdkSandboxInterface(
                            APP_OWNED_SDK_NAME, (long) 1.01, new AppOwnedSdkApi()));
        }

        refreshLoadSdksButtonText();
    }

    private void handleExtras() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            final String videoUrl = extras.getString(VIDEO_URL_KEY);
            mSharedPreferences.edit().putString("banner_video_url", videoUrl).apply();
        }
    }

    private void refreshLoadSdksButtonText() {
        if (mSdksLoaded) {
            mLoadSdksButton.setText("Unload SDKs");
        } else {
            mLoadSdksButton.setText("Load SDKs");
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SDKS_LOADED_KEY, mSdksLoaded);
    }

    private void registerDeathCallbackButton() {
        mDeathCallbackButton.setOnClickListener(
                v -> {
                    // Register for sandbox death event.
                    mSdkSandboxManager.addSdkSandboxProcessDeathCallback(
                            Runnable::run, () -> toastAndLog(INFO, "Sdk Sandbox process died"));
                    toastAndLog(INFO, "Registered death callback");
                });
    }

    private void registerLoadSdksButton() {
        mLoadSdksButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        resetStateForLoadSdkButton();
                        return;
                    }

                    Bundle params = new Bundle();
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> receiver =
                            new OutcomeReceiver<SandboxedSdk, LoadSdkException>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    mSandboxedSdk = sandboxedSdk;
                                    toastAndLog(INFO, "First SDK Loaded successfully!");
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    toastAndLog(ERROR, "Failed to load first SDK: %s", error);
                                }
                            };
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> mediateeReceiver =
                            new OutcomeReceiver<SandboxedSdk, LoadSdkException>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    toastAndLog(INFO, "All SDKs Loaded successfully!");
                                    mSdksLoaded = true;
                                    refreshLoadSdksButtonText();
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    toastAndLog(ERROR, "Failed to load all SDKs: %s", error);
                                }
                            };
                    Log.i(TAG, "Loading SDKs " + SDK_NAME + " and " + MEDIATEE_SDK_NAME);
                    mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, receiver);
                    mSdkSandboxManager.loadSdk(
                            MEDIATEE_SDK_NAME, params, Runnable::run, mediateeReceiver);
                });
    }

    private void resetStateForLoadSdkButton() {
        Log.i(TAG, "Unloading SDKs " + SDK_NAME + " and " + MEDIATEE_SDK_NAME);
        mSdkSandboxManager.unloadSdk(SDK_NAME);
        mSdkSandboxManager.unloadSdk(MEDIATEE_SDK_NAME);
        mSdksLoaded = false;
        refreshLoadSdksButtonText();
    }

    private void registerNewBannerAdButton() {
        OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver =
                new RequestSurfacePackageReceiver();
        mNewBannerAdButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        final BannerOptions options =
                                BannerOptions.fromSharedPreferences(mSharedPreferences);
                        Log.i(TAG, options.toString());
                        final Bundle params = getRequestSurfacePackageParams(null);

                        switch (options.getViewType()) {
                            case INFLATED -> {
                                params.putString(VIEW_TYPE_KEY, VIEW_TYPE_INFLATED_VIEW);
                            }
                            case VIDEO -> {
                                params.putString(VIEW_TYPE_KEY, VIDEO_VIEW_VALUE);
                                params.putString(VIDEO_URL_KEY, options.getVideoUrl());
                            }
                        }
                        sHandler.post(
                                () -> {
                                    mSdkSandboxManager.requestSurfacePackage(
                                            SDK_NAME, params, Runnable::run, receiver);
                                });
                    } else {
                        toastAndLog(WARN, "Sdk is not loaded");
                    }
                });
    }

    private void registerBannerAdOptionsButton() {
        mBannerAdOptionsButton.setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, BannerOptionsActivity.class)));
    }

    private void registerCreateFileButton() {
        mCreateFileButton.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        toastAndLog(WARN, "Sdk is not loaded");
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Set size in MB (1-100)");
                    final EditText input = new EditText(this);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);
                    builder.setPositiveButton(
                            "Create",
                            (dialog, which) -> {
                                final int sizeInMb = Integer.parseInt(input.getText().toString());
                                if (sizeInMb <= 0 || sizeInMb > 100) {
                                    toastAndLog(WARN, "Please provide a value between 1 and 100");
                                    return;
                                }
                                IBinder binder = mSandboxedSdk.getInterface();
                                ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);

                                BackgroundThread.getExecutor()
                                        .execute(
                                                () -> {
                                                    try {
                                                        String response =
                                                                sdkApi.createFile(sizeInMb);
                                                        toastAndLog(INFO, response);
                                                    } catch (Exception e) {
                                                        toastAndLog(
                                                                e,
                                                                "Failed to create file with %d Mb",
                                                                sizeInMb);
                                                    }
                                                });
                            });
                    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    builder.show();
                });
    }

    private void registerSdkToSdkButton() {
        mSdkToSdkCommButton.setOnClickListener(
                v -> {
                    mSdkToSdkCommEnabled = !mSdkToSdkCommEnabled;
                    if (mSdkToSdkCommEnabled) {
                        mSdkToSdkCommButton.setText("Disable SDK to SDK comm");
                        toastAndLog(INFO, "Sdk to Sdk Comm Enabled");
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
                        toastAndLog(INFO, "Sdk to Sdk Comm Disabled");
                    }
                });
    }

    private void registerSyncKeysButton() {
        mSyncKeysButton.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        toastAndLog(WARN, "Sdk is not loaded");
                        return;
                    }

                    final AlertDialog.Builder alert = new AlertDialog.Builder(this);

                    alert.setTitle("Set the key and value to sync");
                    LinearLayout linearLayout = new LinearLayout(this);
                    linearLayout.setOrientation(1); // 1 is for vertical orientation
                    final EditText inputKey = new EditText(this);
                    inputKey.setText("key");
                    final EditText inputValue = new EditText(this);
                    inputValue.setText("value");
                    linearLayout.addView(inputKey);
                    linearLayout.addView(inputValue);
                    alert.setView(linearLayout);

                    alert.setPositiveButton(
                            "Sync",
                            (dialog, which) -> {
                                onSyncKeyPressed(inputKey, inputValue);
                            });
                    alert.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                    alert.show();
                });
    }

    private void onSyncKeyPressed(EditText inputKey, EditText inputValue) {
        BackgroundThread.getHandler()
                .post(
                        () -> {
                            final SharedPreferences pref =
                                    PreferenceManager.getDefaultSharedPreferences(
                                            getApplicationContext());
                            String keyToSync = inputKey.getText().toString();
                            String valueToSync = inputValue.getText().toString();
                            pref.edit().putString(keyToSync, valueToSync).commit();
                            mSdkSandboxManager.addSyncedSharedPreferencesKeys(Set.of(keyToSync));
                            IBinder binder = mSandboxedSdk.getInterface();
                            ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                            try {
                                // Allow some time for data to sync
                                Thread.sleep(1000);
                                String syncedKeysValue =
                                        sdkApi.getSyncedSharedPreferencesString(keyToSync);
                                if (syncedKeysValue.equals(valueToSync)) {
                                    toastAndLog(
                                            INFO,
                                            "Key was synced successfully\n"
                                                    + "Key is : %s Value is : %s",
                                            keyToSync,
                                            syncedKeysValue);
                                } else {
                                    toastAndLog(WARN, "Key was not synced");
                                }
                            } catch (Exception e) {
                                toastAndLog(e, "Failed to sync keys (%s)", keyToSync);
                            }
                        });
    }

    private void registerNewFullscreenAdButton() {
        mNewFullScreenAd.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        toastAndLog(WARN, "Sdk is not loaded");
                        return;
                    }
                    if (!SdkLevel.isAtLeastU()) {
                        toastAndLog(WARN, "Device should have Android U or above!");
                        return;
                    }
                    IBinder binder = mSandboxedSdk.getInterface();
                    ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                    ActivityStarter starter = new ActivityStarter(this, mSdkSandboxManager);
                    try {
                        sdkApi.startActivity(starter);
                        toastAndLog(INFO, "Started activity %s", starter);

                    } catch (RemoteException e) {
                        toastAndLog(e, "Failed to startActivity (%s)", starter);
                    }
                });
    }

    private Bundle getRequestSurfacePackageParams(String commType) {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, mBottomView.getWidth());
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, mBottomView.getHeight());
        params.putInt(EXTRA_DISPLAY_ID, getDisplay().getDisplayId());
        params.putBinder(EXTRA_HOST_TOKEN, mBottomView.getHostToken());
        params.putString(EXTRA_SDK_SDK_ENABLED_KEY, commType);
        return params;
    }

    private void toastAndLog(int logLevel, String fmt, Object... args) {
        String message = String.format(fmt, args);
        switch (logLevel) {
            case DEBUG:
                Log.d(TAG, message);
                break;
            case ERROR:
                Log.e(TAG, message);
                break;
            case INFO:
                Log.i(TAG, message);
                break;
            case VERBOSE:
                Log.v(TAG, message);
                break;
            case WARN:
                Log.w(TAG, message);
                break;
            default:
                Log.w(TAG, "Invalid log level " + logLevel + " for message: " + message);
        }
        makeToast(message);
    }

    private void toastAndLog(Exception e, String fmt, Object... args) {
        String message = String.format(fmt, args);
        Log.e(TAG, message, e);
        makeToast(message);
    }

    private void makeToast(CharSequence message) {
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
                        mBottomView.setChildSurfacePackage(surfacePackage);
                        mBottomView.setVisibility(View.VISIBLE);
                    });
            toastAndLog(INFO, "Rendered surface view");
        }

        @Override
        public void onError(@NonNull RequestSurfacePackageException error) {
            toastAndLog(ERROR, "Failed: %s", error.getMessage());
        }
    }

    private static final class ActivityStarter extends IActivityStarter.Stub {
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

        @Override
        public String toString() {
            return mActivity.getComponentName().flattenToShortString();
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
