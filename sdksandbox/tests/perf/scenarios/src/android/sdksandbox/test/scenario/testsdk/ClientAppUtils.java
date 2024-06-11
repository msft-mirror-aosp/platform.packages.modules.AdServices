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

package android.sdksandbox.test.scenario.testsdk;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Utility class to help do operations for the client app. */
final class ClientAppUtils {
    private final UiDevice mUiDevice;
    private final String mPackageName;
    private final String mActivityName;
    private final Map<String, Boolean> mClientArgBundle = new HashMap<>();

    private static final String CLIENT_APP_PACKAGE_NAME_KEY = "client-app-package-name";
    private static final String CLIENT_APP_ACTIVITY_NAME_KEY = "client-app-activity-name";
    // Prefix for matching boolean arguments to pass to client app.
    private static final String CLIENT_APP_ARG_KEY_PREFIX = "client-app-arg-";

    // Client app must use these resource ids for their buttons to be identified in test.
    private static final String INITIALIZE_SDK_BUTTON = "initializeSdkButton";
    private static final String LOAD_AD_BUTTON = "loadAdButton";
    private static final String RESIZE_AD_CONTAINER_BUTTON = "resizeAdContainerButton";
    private static final long UI_NAVIGATION_WAIT_MS = 1000;
    private static final int UI_WAIT_INITIALIZE_MS = 1000;
    private static final int UI_WAIT_LOAD_AD_MS = 1000;
    private static final int UI_RETRIES_WAIT_INITIALIZE = 5;
    private static final int UI_RETRIES_WAIT_LOAD_AD = 5;

    public static final String AD_LOADED_BUTTON_TEXT = "Load Ad (Ad loaded)";

    /** Returns the command for stopping the client app. */
    public String getStopAppCommand() {
        return "am force-stop " + mPackageName;
    }

    /** Returns the command for starting the client app with intent extras. */
    public String getStartAppCommand() {
        final ArrayList<String> commandFragments =
                new ArrayList<>(
                        Arrays.asList("am", "start", "-n", mPackageName + "/" + mActivityName));

        for (Map.Entry<String, Boolean> argEntry : mClientArgBundle.entrySet()) {
            commandFragments.add("--ez");
            commandFragments.add(argEntry.getKey());
            commandFragments.add(String.valueOf(argEntry.getValue()));
        }

        return String.join(" ", commandFragments);
    }

    /**
     * Finds the initializeSdk button.
     *
     * @return initialize SDK button.
     */
    public UiObject2 getInitializeSdkButton() {
        return mUiDevice.wait(
                Until.findObject(By.res(mPackageName, INITIALIZE_SDK_BUTTON)),
                UI_NAVIGATION_WAIT_MS);
    }

    /**
     * Finds the loadAd button.
     *
     * @return load ad button.
     */
    public UiObject2 getLoadAdButton() {
        return mUiDevice.wait(
                Until.findObject(By.res(mPackageName, LOAD_AD_BUTTON)), UI_NAVIGATION_WAIT_MS);
    }

    /** Initializes SDK by finding the initializeSdk button and clicking on it. */
    public void initializeSdk() throws InterruptedException {
        UiObject2 initializeSdkButton = getInitializeSdkButton();
        if (initializeSdkButton != null) {
            initializeSdkButton.click();
        } else {
            throw new RuntimeException("Did not find 'Initialize' button.");
        }
        assertInitialized();
    }

    private void assertInitialized() throws InterruptedException {
        int retries = 0;
        // wait until initialized.
        while (retries < UI_RETRIES_WAIT_INITIALIZE) {
            Thread.sleep(UI_WAIT_INITIALIZE_MS);
            if (getLoadAdButton().isEnabled()) {
                return;
            }
            retries++;
        }
        throw new AssertionError("SDK not initialized");
    }

    /**
     * Loads ad by finding the loadAd button and clicking on it.
     *
     * @throws RuntimeException if load ad button is not found.
     */
    public void loadAd() throws InterruptedException {
        UiObject2 loadAdButton = getLoadAdButton();
        if (loadAdButton != null) {
            loadAdButton.click();
        } else {
            throw new RuntimeException("Did not find 'Load Ad' button.");
        }
        assertAdLoaded();
    }

    private void assertAdLoaded() throws InterruptedException {
        int retries = 0;
        // wait until Ad Loaded.
        while (retries < UI_RETRIES_WAIT_LOAD_AD) {
            Thread.sleep(UI_WAIT_LOAD_AD_MS);
            if (getLoadAdButton().getText().equals(ClientAppUtils.AD_LOADED_BUTTON_TEXT)) {
                return;
            }
            retries++;
        }
        throw new AssertionError("Ad not loaded");
    }

    /**
     * Triggers resize of ad container by finding the resizeAdContainer button and clicking on it.
     *
     * @throws RuntimeException if button is not found.
     */
    public void resizeAdContainer() {
        UiObject2 resizeAdContainerButton = getResizeAdContainerButton();
        if (resizeAdContainerButton != null) {
            resizeAdContainerButton.click();
        } else {
            throw new RuntimeException("Did not find 'Resize Ad Container' button.");
        }
    }

    private UiObject2 getResizeAdContainerButton() {
        return mUiDevice.wait(
                Until.findObject(By.res(mPackageName, RESIZE_AD_CONTAINER_BUTTON)),
                UI_NAVIGATION_WAIT_MS);
    }

    /** Gets the client app package name. */
    String getClientPackageName() {
        return mPackageName;
    }

    /**
     * Constructor for {@link ClientAppUtils}.
     *
     * @param uiDevice is the device that is being used.
     * @param argsBundle is the package name for the client app.
     */
    ClientAppUtils(UiDevice uiDevice, Bundle argsBundle) {
        assertThat(uiDevice).isNotNull();
        assertThat(argsBundle).isNotNull();

        mUiDevice = uiDevice;
        mPackageName = argsBundle.getString(CLIENT_APP_PACKAGE_NAME_KEY);
        assertThat(mPackageName).isNotNull();
        mActivityName = argsBundle.getString(CLIENT_APP_ACTIVITY_NAME_KEY);
        assertThat(mActivityName).isNotNull();
        extractExtraClientArguments(argsBundle);
    }

    private void extractExtraClientArguments(Bundle argsBundle) {
        for (String argKey : argsBundle.keySet()) {
            if (argKey.startsWith(CLIENT_APP_ARG_KEY_PREFIX)) {
                mClientArgBundle.put(
                        argKey.substring(CLIENT_APP_ARG_KEY_PREFIX.length()),
                        Boolean.parseBoolean(argsBundle.getString(argKey)));
            }
        }
    }
}
