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


import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** Utility class to help do operations for the client app. */
final class ClientAppUtils {
    private final UiDevice mUiDevice;
    private final String mPackageName;
    private final String mActivityName;

    // Client app must use these resource ids for their buttons to be identified in test.
    private static final String INITIALIZE_SDK_BUTTON = "initializeSdkButton";
    private static final String LOAD_AD_BUTTON = "loadAdButton";
    private static final long UI_NAVIGATION_WAIT_MS = 1000;
    private static final int UI_WAIT_INITIALIZE_MS = 1000;
    private static final int UI_WAIT_LOAD_AD_MS = 1000;
    private static final int UI_RETRIES_WAIT_INITIALIZE = 5;
    private static final int UI_RETRIES_WAIT_LOAD_AD = 5;

    public static final String AD_LOADED_BUTTON_TEXT = "Load Ad (Ad loaded)";

    /** Returns the command for stopping the client app with {@code packageName}. */
    public static String getStopAppCommand(String packageName) {
        return "am force-stop " + packageName;
    }

    /**
     * Returns the command for starting the client app with intent extras.
     *
     * @param clientArgs to pass to the client activity as intent extras.
     */
    public String getStartAppCommand(Map<String, Boolean> clientArgs) {
        final ArrayList<String> commandFragments =
                new ArrayList<>(
                        Arrays.asList("am", "start", "-n", mPackageName + "/" + mActivityName));

        for (Map.Entry<String, Boolean> argEntry : clientArgs.entrySet()) {
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

    // TODO: b/330389288 - make button required after updating client apps.
    /** Initializes SDK by finding the initializeSdk button and clicking on it, if it exists. */
    public void initializeSdk() throws InterruptedException {
        UiObject2 initializeSdkButton = getInitializeSdkButton();
        if (initializeSdkButton != null) {
            initializeSdkButton.click();
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
     * Constructor for {@link ClientAppUtils}.
     *
     * @param uiDevice is the device that is being used.
     * @param packageName is the package name for the client app.
     * @param activityName is the activity name to start for the test.
     */
    ClientAppUtils(UiDevice uiDevice, String packageName, String activityName) {
        this.mUiDevice = uiDevice;
        this.mPackageName = packageName;
        this.mActivityName = activityName;
    }
}
