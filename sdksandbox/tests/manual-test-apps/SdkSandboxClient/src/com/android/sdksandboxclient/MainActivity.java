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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String SDK_NAME = "com.android.sdksandboxcode";
    private static final String MEDIATEE_SDK_NAME = "com.android.sdksandboxcode_mediatee";
    private static final String TAG = "SdkSandboxClientMainActivity";

    private static final String VIEW_TYPE_KEY = "view-type";
    private static final String VIDEO_VIEW_VALUE = "video-view";
    private static final String VIDEO_URL_KEY = "video-url";
    private static final String VIEW_TYPE_INFLATED_VIEW = "view-type-inflated-view";
    private static final String VIEW_TYPE_WEBVIEW = "view-type-webview";
    private static final String VIEW_TYPE_AD_REFRESH = "view-type-ad-refresh";
    private static final String VIEW_TYPE_EDITTEXT = "view-type-edittext";

  private static final String ON_CLICK_BEHAVIOUR_TYPE_KEY = "on-click-behavior";
    private static final String ON_CLICK_OPEN_CHROME = "on-click-open-chrome";
    private static final String ON_CLICK_OPEN_PACKAGE = "on-click-open-package";
    private static final String PACKAGE_TO_OPEN_KEY = "package-to-open";

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final String EXTRA_SDK_SDK_ENABLED_KEY = "sdkSdkCommEnabled";
    private static final String DROPDOWN_KEY_SDK_SANDBOX = "SDK_IN_SANDBOX";
    private static final String DROPDOWN_KEY_SDK_APP = "SDK_IN_APP";
    private static final String APP_OWNED_SDK_NAME = "app-sdk-1";

    // Saved instance state keys
    private static final String SDKS_LOADED_KEY = "sdks_loaded";
    private static final String CUSTOMIZED_SDK_CONTEXT_ENABLED = "customized_sdk_context_enabled";
    private static final String SANDBOXED_SDK_BINDER = "com.android.sdksandboxclient.SANDBOXED_SDK";

    private Bundle mSavedInstanceState = new Bundle();
    private boolean mSdksLoaded = false;
    private boolean mSdkToSdkCommEnabled = false;
    private SdkSandboxManager mSdkSandboxManager;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    private Button mResetPreferencesButton;
    private Button mLoadSdksButton;
    private Button mDeathCallbackButton;
    private Button mNewBannerAdButton;
    private ImageButton mBannerAdOptionsButton;
    private Button mCreateFileButton;
    private Button mSyncKeysButton;
    private Button mSdkToSdkCommButton;
    private Button mDumpSandboxButton;
    private Button mNewFullScreenAd;
    private Button mNewAppWebviewButton;

    private SurfaceView mInScrollBannerView;
    private SurfaceView mBottomBannerView;

    private SandboxedSdk mSandboxedSdk;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableStrictMode();
        super.onCreate(savedInstanceState);

        setAppTitle();

        if (savedInstanceState != null) {
            mSavedInstanceState.putAll(savedInstanceState);
            mSdksLoaded = savedInstanceState.getBoolean(SDKS_LOADED_KEY);
        }

        mExecutor.execute(
                () -> {
                    Looper.prepare();
                    mSharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                    handleExtras();
                    PreferenceManager.setDefaultValues(this, R.xml.banner_preferences, false);
                });

        setContentView(R.layout.activity_main);
        mSdkSandboxManager = getApplicationContext().getSystemService(SdkSandboxManager.class);

        mBottomBannerView = findViewById(R.id.bottom_banner_view);
        mBottomBannerView.setZOrderOnTop(true);
        mBottomBannerView.setVisibility(View.INVISIBLE);

        mInScrollBannerView = findViewById(R.id.in_scroll_banner_view);
        mInScrollBannerView.setZOrderOnTop(true);
        mInScrollBannerView.setVisibility(View.INVISIBLE);

        mResetPreferencesButton = findViewById(R.id.reset_preferences_button);
        mLoadSdksButton = findViewById(R.id.load_sdks_button);
        mDeathCallbackButton = findViewById(R.id.register_death_callback_button);

        mNewBannerAdButton = findViewById(R.id.new_banner_ad_button);
        mBannerAdOptionsButton = findViewById(R.id.banner_ad_options_button);
        mNewFullScreenAd = findViewById(R.id.new_fullscreen_ad_button);

        mCreateFileButton = findViewById(R.id.create_file_button);
        mSyncKeysButton = findViewById(R.id.sync_keys_button);
        mSdkToSdkCommButton = findViewById(R.id.enable_sdk_sdk_button);
        mDumpSandboxButton = findViewById(R.id.dump_sandbox_button);
        mNewAppWebviewButton = findViewById(R.id.new_app_webview_button);

        configureFeatureFlagSection();

        registerResetPreferencesButton();
        registerLoadSdksButton();
        registerDeathCallbackButton();

        registerNewBannerAdButton();
        registerBannerAdOptionsButton();
        registerNewFullscreenAdButton();

        registerGetOrSendFileDescriptorButton();
        registerCreateFileButton();
        registerSyncKeysButton();
        registerSdkToSdkButton();
        registerDumpSandboxButton();
        registerNewAppWebviewButton();

        if (savedInstanceState == null) {
            // Register AppOwnedSdkInterface when activity first created
            mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                    new AppOwnedSdkSandboxInterface(
                            APP_OWNED_SDK_NAME, (long) 1.01, new AppOwnedSdkApi()));
        }

        refreshLoadSdksButtonText();
    }

    private void registerResetPreferencesButton() {
        mResetPreferencesButton.setOnClickListener(
                v ->
                        mExecutor.execute(
                                () -> {
                                    mSharedPreferences.edit().clear().commit();
                                    PreferenceManager.setDefaultValues(
                                            this, R.xml.banner_preferences, true);
                                }));
    }

    private void setAppTitle() {
        try {
            final PackageInfo packageInfo =
                    getPackageManager().getPackageInfo(getPackageName(), /*flags=*/ 0);
            final String versionName = packageInfo.versionName;
            setTitle(
                    String.format(
                            "%s (%s)",
                            getResources().getString(R.string.title_activity_main), versionName));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not find package " + getPackageName());
        }
    }

    private void handleExtras() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            final String videoUrl = extras.getString(VIDEO_URL_KEY);
            mSharedPreferences.edit().putString("banner_video_url", videoUrl).apply();
            final String packageToOpen = extras.getString(PACKAGE_TO_OPEN_KEY);
            mSharedPreferences.edit().putString("package_to_open", packageToOpen).apply();
        }
    }

    private void configureFeatureFlagSection() {
        TextView featureFlagStatus = findViewById(R.id.feature_flags_status);
        if(!mSdksLoaded) {
            featureFlagStatus.setText("Load SDK to fetch status of feature flags");
            return;
        }

        if (!mSavedInstanceState.containsKey(CUSTOMIZED_SDK_CONTEXT_ENABLED)) {
            try {
                IBinder binder = mSandboxedSdk.getInterface();
                ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                boolean result = sdkApi.isCustomizedSdkContextEnabled();
                mSavedInstanceState.putBoolean(CUSTOMIZED_SDK_CONTEXT_ENABLED, result);
            } catch (RemoteException e) {
                toastAndLog(e, "Failed to fetch feature flag status: %s", e);
            }
        }

        boolean result = mSavedInstanceState.getBoolean(CUSTOMIZED_SDK_CONTEXT_ENABLED);
        featureFlagStatus.post(() -> {
            featureFlagStatus.setText("CustomizedSdkContext Enabled: " + result);
        });
    }

    private void refreshLoadSdksButtonText() {
        if (mSdksLoaded) {
            mLoadSdksButton.post(() -> mLoadSdksButton.setText("Unload SDKs"));
        } else {
            mLoadSdksButton.post(() -> mLoadSdksButton.setText("Load SDKs"));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(mSavedInstanceState);
        outState.putBoolean(SDKS_LOADED_KEY, mSdksLoaded);
    }

    private void registerDeathCallbackButton() {
        mDeathCallbackButton.setOnClickListener(
                v -> {
                    // Register for sandbox death event.
                    mSdkSandboxManager.addSdkSandboxProcessDeathCallback(
                            Runnable::run, () -> makeToast("Sdk Sandbox process died"));
                    makeToast("Registered death callback");
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
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> mediateeReceiver =
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    toastAndLog(INFO, "All SDKs Loaded successfully!");
                                    mSdksLoaded = true;
                                    refreshLoadSdksButtonText();
                                    configureFeatureFlagSection();
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    toastAndLog(ERROR, "Failed to load all SDKs: %s", error);
                                }
                            };
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> receiver =
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    mSandboxedSdk = sandboxedSdk;
                                    mSdkSandboxManager.loadSdk(
                                            MEDIATEE_SDK_NAME,
                                            params,
                                            Runnable::run,
                                            mediateeReceiver);
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    toastAndLog(ERROR, "Failed to load first SDK: %s", error);
                                }
                            };
                    Log.i(TAG, "Loading SDKs " + SDK_NAME + " and " + MEDIATEE_SDK_NAME);
                    mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, receiver);
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
        mNewBannerAdButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        final BannerOptions options =
                                BannerOptions.fromSharedPreferences(mSharedPreferences);
                        Log.i(TAG, options.toString());

                        final SurfaceView surfaceView =
                                (options.getPlacement() == BannerOptions.Placement.BOTTOM)
                                        ? mBottomBannerView
                                        : mInScrollBannerView;

                        final OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver =
                                new RequestSurfacePackageReceiver(surfaceView);

                        final Bundle params = getRequestSurfacePackageParams(null, surfaceView);

                        switch (options.getViewType()) {
                            case INFLATED:
                                {
                                    params.putString(VIEW_TYPE_KEY, VIEW_TYPE_INFLATED_VIEW);
                                    break;
                                }
                            case VIDEO:
                                {
                                    params.putString(VIEW_TYPE_KEY, VIDEO_VIEW_VALUE);
                                    params.putString(VIDEO_URL_KEY, options.getVideoUrl());
                                    break;
                                }
                            case WEBVIEW:
                                {
                                    params.putString(VIEW_TYPE_KEY, VIEW_TYPE_WEBVIEW);
                                    break;
                                }
                          case AD_REFRESH:
                                {
                                     params.putString(VIEW_TYPE_KEY, VIEW_TYPE_AD_REFRESH);
                                     break;
                                }
                          case EDITTEXT:
                               {
                                    params.putString(VIEW_TYPE_KEY, VIEW_TYPE_EDITTEXT);
                                    break;
                               }
                        }

                        switch (options.getOnClick()) {
                            case OPEN_CHROME:
                                {
                                    params.putString(
                                            ON_CLICK_BEHAVIOUR_TYPE_KEY, ON_CLICK_OPEN_CHROME);
                                    break;
                                }
                            case OPEN_PACKAGE:
                                {
                                    params.putString(
                                            ON_CLICK_BEHAVIOUR_TYPE_KEY, ON_CLICK_OPEN_PACKAGE);
                                    params.putString(
                                            PACKAGE_TO_OPEN_KEY, options.getmPackageToOpen());
                                    break;
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

    private void registerGetOrSendFileDescriptorButton() {
        final Button mGetFileDescriptorButton = findViewById(R.id.get_filedescriptor_button);
        final Button mSendFileDescriptorButton = findViewById(R.id.send_filedescriptor_button);
        mGetFileDescriptorButton.setOnClickListener(
                v -> {
                    Log.i(TAG, "isGetFileDescriptorCalled = " + String.valueOf(true));
                    onGetOrSendFileDescriptorPressed(/*isGetFileDescriptorCalled=*/ true);
                });
        mSendFileDescriptorButton.setOnClickListener(
                v -> {
                    Log.i(TAG, "isGetFileDescriptorCalled = " + String.valueOf(false));
                    onGetOrSendFileDescriptorPressed(/*isGetFileDescriptorCalled=*/ false);
                });
    }

    private void onGetOrSendFileDescriptorPressed(boolean isGetFileDescriptorCalled) {
        if (!mSdksLoaded) {
            toastAndLog(WARN, "Sdk is not loaded");
            return;
        }
        Log.i(TAG, "Ready to transfer File Descriptor between APP and SDK");

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set the value for FileDescriptor");
        final EditText inputValue = new EditText(this);
        inputValue.setText("default");
        builder.setView(inputValue);

        builder.setPositiveButton(
                "Transfer",
                (dialog, which) -> {
                    BackgroundThread.getExecutor()
                            .execute(
                                    () -> {
                                        final String inputValueString =
                                                inputValue.getText().toString();
                                        if (inputValueString.isEmpty()
                                                || inputValueString.length() > 1000) {
                                            toastAndLog(
                                                    WARN,
                                                    "Input string cannot be empty or"
                                                            + " have more than 1000"
                                                            + " characters. Try again.");
                                            return;
                                        }

                                        String value = "";
                                        if (isGetFileDescriptorCalled) {
                                            value = onGetFileDescriptorPressed(inputValueString);
                                        } else {
                                            value = onSendFileDescriptorPressed(inputValueString);
                                        }

                                        if (inputValueString.equals(value)) {
                                            toastAndLog(
                                                    INFO,
                                                    "FileDescriptor transfer successful, value sent"
                                                            + " ="
                                                            + inputValueString
                                                            + " , value received = "
                                                            + value);
                                        } else {
                                            toastAndLog(
                                                    WARN,
                                                    "FileDescriptor transfer unsuccessful, Value"
                                                            + " sent ="
                                                            + inputValueString
                                                            + " , Value received = "
                                                            + value);
                                        }
                                    });
                });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * This method receives a fileDescriptor from the SDK and opens it using a file inputstream and
     * then reads the characters in the file and stores it in a String to return it.
     */
    private String onGetFileDescriptorPressed(String inputValueString) {
        String value = "";
        try {
            IBinder binder = mSandboxedSdk.getInterface();
            ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
            ParcelFileDescriptor pFd = sdkApi.getFileDescriptor(inputValueString);
            FileInputStream fis = new FileInputStream(pFd.getFileDescriptor());
            // Reading fileInputStream and adding its
            // value to a string
            while (fis.available() != 0) {
                value += (char) fis.read();
            }
            fis.close();
            pFd.close();
            return value;
        } catch (Exception e) {
            toastAndLog(ERROR, "Failed to get FileDescriptor: %s", e);
        }
        return "";
    }

    /**
     * This method generates a file outputstream in the App and sends the generated FileDescriptor
     * to SDK to parse it and then receives the parsed value from the SDK and returns it.
     */
    private String onSendFileDescriptorPressed(String inputValueString) {
        try {
            final String fileName = "testParcelFileDescriptor";
            FileOutputStream fout =
                    getApplicationContext().openFileOutput(fileName, Context.MODE_PRIVATE);
            // Writing inputValue String to a file
            for (int i = 0; i < inputValueString.length(); i++) {
                fout.write((int) inputValueString.charAt(i));
            }
            fout.close();
            File file = new File(getApplicationContext().getFilesDir(), fileName);
            ParcelFileDescriptor pFd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            IBinder binder = mSandboxedSdk.getInterface();
            ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
            String parsedValue = sdkApi.parseFileDescriptor(pFd);
            pFd.close();
            return parsedValue;
        } catch (Exception e) {
            toastAndLog(ERROR, "Failed to send FileDescriptor: %s", e);
        }
        return "";
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
                        toastAndLog(INFO, "Sdk Sdk Comm Enabled");
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
                                    final SurfaceView view = mBottomBannerView;
                                    OutcomeReceiver<Bundle, RequestSurfacePackageException>
                                            receiver = new RequestSurfacePackageReceiver(view);
                                    mSdkSandboxManager.requestSurfacePackage(
                                            SDK_NAME,
                                            getRequestSurfacePackageParams(
                                                    dropdown.getSelectedItem().toString(), view),
                                            Runnable::run,
                                            receiver);
                                });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                        builder.show();
                    } else {
                        mSdkToSdkCommButton.setText("Enable SDK to SDK comm");
                        toastAndLog(INFO, "Sdk Sdk Comm Disabled");
                    }
                });
    }

    private void registerDumpSandboxButton() {
        mDumpSandboxButton.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        toastAndLog(WARN, "Sdk is not loaded");
                        return;
                    }

                    IBinder binder = mSandboxedSdk.getInterface();
                    ISdkApi sdkApi = ISdkApi.Stub.asInterface(binder);
                    String sandboxDump = "";
                    try {
                        sandboxDump = sdkApi.getSandboxDump();
                    } catch (RemoteException e) {
                        // Do nothing, the correct text won't be displayed.
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Information provided by the sandbox")
                            .setMessage(sandboxDump)
                            .setNegativeButton("Cancel", null)
                            .show();
                }
        );
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

                    final BannerOptions options =
                            BannerOptions.fromSharedPreferences(mSharedPreferences);
                    Bundle params = new Bundle();
                    if (options.getViewType() == BannerOptions.ViewType.VIDEO) {
                        params.putString(VIDEO_URL_KEY, options.getVideoUrl());
                    }
                    try {
                        sdkApi.startActivity(starter, params);
                        toastAndLog(INFO, "Started activity %s", starter);
                    } catch (RemoteException e) {
                        toastAndLog(e, "Failed to startActivity (%s)", starter);
                    }
                });
    }

    private void registerNewAppWebviewButton() {
        mNewAppWebviewButton.setOnClickListener(
                v -> {
                    if (!mSdksLoaded) {
                        toastAndLog(WARN, "Sdk is not loaded");
                        return;
                    }
                    IBinder binder = mSandboxedSdk.getInterface();
                    Intent intent = new Intent(this, AppWebViewActivity.class);
                    intent.putExtra(SANDBOXED_SDK_BINDER, binder);
                    startActivity(intent);
                });
    }

    private Bundle getRequestSurfacePackageParams(String commType, SurfaceView surfaceView) {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, surfaceView.getWidth());
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, surfaceView.getHeight());
        params.putInt(EXTRA_DISPLAY_ID, getDisplay().getDisplayId());
        params.putBinder(EXTRA_HOST_TOKEN, surfaceView.getHostToken());
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

        private final SurfaceView mSurfaceView;

        private RequestSurfacePackageReceiver(SurfaceView surfaceView) {
            mSurfaceView = surfaceView;
        }

        @Override
        public void onResult(Bundle result) {
            sHandler.post(
                    () -> {
                        SurfacePackage surfacePackage =
                                result.getParcelable(EXTRA_SURFACE_PACKAGE, SurfacePackage.class);
                        mSurfaceView.setChildSurfacePackage(surfacePackage);
                        mSurfaceView.setVisibility(View.VISIBLE);
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
