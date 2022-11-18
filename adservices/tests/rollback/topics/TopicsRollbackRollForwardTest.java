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

import static com.google.common.truth.Truth.assertThat;

import com.android.module_rollback_test.host.ModuleRollbackBaseHostTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
@SuppressWarnings("DefaultPackage")
public class TopicsRollbackRollForwardTest extends ModuleRollbackBaseHostTest {
    public static final String SDK_1 = "sdk1";
    public static final String SDK_2 = "sdk2";

    private static final String TOPICS_TEST_APP =
            "com.android.adservices.tests.rollback.topicsrollbackrollforwardtestapp";

    private static final String TEST_APP1_CLASS =
            "com.android.adservices.tests.apps.topics.TopicsRollbackRollForwardTestApp";
    private static final String TEST_TOPICS_API_ONLY = "testTopics";
    private static final String TEST_TOPICS_AND_PREVIEW = "testTopicsAndPreviewAPI";

    public void runTestApp(boolean isTopicsApiOnly) {
        ITestDevice device = getDevice();
        try {
            if (!getDevice().isAdbRoot()) {
                getDevice().enableAdbRoot();
            }
            // Disable kill switches
            device.executeShellCommand("device_config put adservices global_kill_switch false");
            device.executeShellCommand("device_config put adservices topics_kill_switch false");
            device.executeShellCommand(
                    "setprop debug.adservices.disable_topics_enrollment_check true");
            // Temporarily disable Device Config sync
            device.executeShellCommand("device_config set_sync_disabled_for_tests persistent");
            device.executeShellCommand("setprop debug.adservices.ppapi_app_signature_allow_list *");
            // Override Consent Manager to debug mode to grant user consent
            device.executeShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
            // Use bundled files
            device.executeShellCommand(
                    "device_config put adservices classifier_force_use_bundled_files true");
            // Set classifier_type to just On device classifier.
            device.executeShellCommand("device_config put adservices classifier_type 1");
            // Set up number of classifications returned by the classifier to 5
            device.executeShellCommand(
                    "device_config put adservices classifier_number_of_top_labels 5");
            // Lower threshold to 0.
            // If we do not do this there is still chance we do not get 5 values.
            device.executeShellCommand("device_config put adservices classifier_threshold 0");

            installPackage(TOPICS_TEST_APP + ".apk");
            runDeviceTests(
                    TOPICS_TEST_APP,
                    TEST_APP1_CLASS,
                    isTopicsApiOnly ? TEST_TOPICS_API_ONLY : TEST_TOPICS_AND_PREVIEW);
        } catch (Exception e) {
            throw new IllegalStateException("Failed on running device test", e);
        }
    }

    /** Customized checks/actions before installing the modules. */
    @Override
    public void onPreInstallModule() {}

    /**
     * Customized checks/actions before installing higher version module. Current module version v =
     * v1.
     */
    @Override
    public void onPreInstallHigherVersionModule() {
        // Test on a lower version build assuming there is no Preview API.
        runTestApp(/* isRunTopicsApiOnly */ true);

        // Sdk1 calls Topics API, there is usage from Sdk1.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_1);
        // Sdk2 calls Topics API, there is usage from Sdk2.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_2);
    }

    /** Customized checks/actions before rolling back a module. Current module version v = v2. */
    @Override
    public void onPreRollbackSetting() {
        // Topics API usage preserved from version 1 stage.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_1);
        assertThat(getTopicsApiUsageHistory()).contains(SDK_2);

        // Test Topics and Preview API on higher version module.
        runTestApp(/* isRunTopicsApiOnly */ false);

        // Sdk1 calls Topics API,there is usage from Sdk1.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_1);
        // Sdk2 calls Preview API,there is no usage from Sdk2.
        assertThat(getTopicsApiUsageHistory()).doesNotContain(SDK_2);
    }

    /** Customized checks/actions after rolling back a module. Current module version v = v1. */
    @Override
    public void onPostRollbackValidation() {
        // In version 2, sdk1 has usage and sdk2 does not.
        // In version 1, sdk1 and sdk2 both have usage.
        // After rollback, app data is rolled back to earlier stage. Usage history restored to
        // version 1.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_1);
        assertThat(getTopicsApiUsageHistory()).contains(SDK_2);

        // Module version rolled back to lower version build. Assuming there is no Preview API.
        runTestApp(/* isRunTopicsApiOnly */ true);

        // Sdk1 calls Topics API,there is usage from Sdk1.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_1);
        // Sdk2 calls Topics API,there is usage from Sdk2.
        assertThat(getTopicsApiUsageHistory()).contains(SDK_2);
    }

    /** Check Topics API usage */
    private String getTopicsApiUsageHistory() {
        try {
            return getDevice()
                    .executeShellCommand(
                            "sqlite3"
                        + " /data/data/com.google.android.adservices.api/databases/adservices.db"
                                + " 'SELECT * FROM topics_usage_history'");
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException("Failed to execute get usage history command", e);
        }
    }
}
