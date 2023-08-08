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
package com.android.adservices.common;

import static android.os.Build.VERSION.SDK_INT;

import android.provider.DeviceConfig;
import android.util.Log;
import android.util.Pair;

import com.android.adservices.common.DeviceConfigHelper.SyncDisabledMode;
import com.android.adservices.service.Flags;
import com.android.adservices.service.PhFlags;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

// TODO(b/294423183): add unit tests
/**
 * Rule used to properly set AdService flags - it will take care of permissions, restoring values at
 * the end, setting {@link android.provider.DeviceConfig} or {@link android.os.SystemProperties},
 * etc...
 *
 * <p>Most notes set {@link android.provider.DeviceConfig} flags, although some sets {@link
 * android.os.SystemProperties} instead - those are typically suffixed with {@code forTests}
 */
public final class AdServicesFlagsSetterRule implements TestRule {

    private static final String TAG = AdServicesFlagsSetterRule.class.getSimpleName();

    private final DeviceConfigHelper mDeviceConfig =
            new DeviceConfigHelper(DeviceConfig.NAMESPACE_ADSERVICES);

    private final SystemPropertiesHelper mSystemProperties =
            new SystemPropertiesHelper(PhFlags.SYSTEM_PROPERTY_PREFIX);

    // Cache flags that were set before the test started, so the rule can be instantiated using a
    // builder-like approach - will be set to null after test starts.
    @Nullable private List<Pair<String, String>> mInitialFlags = new ArrayList<>();

    // Cache system properties that were set before the test started, so the rule can be
    // instantiated using a builder-like approach - will be set to null after test starts.
    @Nullable private List<Pair<String, String>> mInitialSystemProperties = new ArrayList<>();

    // TODO(b/294423183): remove once legacy usage is gone
    private final boolean mUsedByLegacyHelper;

    @Override
    public Statement apply(Statement base, Description description) {
        setOrCacheSystemProperty("tag.adservices", "VERBOSE");
        setOrCacheSystemProperty("tag.adservices.topics", "VERBOSE");

        String testName = description.getDisplayName();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mDeviceConfig.setSyncDisabledMode(SyncDisabledMode.PERSISTENT);
                setInitialSystemProperties(testName);
                setInitialFlags(testName);
                try {
                    base.evaluate();
                } finally {
                    List<Throwable> errors = new ArrayList<>();
                    runSafely(errors, () -> resetFlags(testName));
                    runSafely(errors, () -> resetSystemProperties(testName));
                    runSafely(
                            errors, () -> mDeviceConfig.setSyncDisabledMode(SyncDisabledMode.NONE));
                    if (!errors.isEmpty()) {
                        throw new RuntimeException(
                                errors.size() + " errors finalizing infra: " + errors);
                    }
                }
            }
        };
    }

    private AdServicesFlagsSetterRule() {
        this(/* usedByLegacyHelper= */ false);
    }

    private AdServicesFlagsSetterRule(boolean usedByLegacyHelper) {
        mUsedByLegacyHelper = usedByLegacyHelper;
    }

    /** Factory method that only disables the global kill switch. */
    public static AdServicesFlagsSetterRule forGlobalKillSwitchDisabledTests() {
        return new AdServicesFlagsSetterRule().setGlobalKillSwitch(false);
    }

    /** Factory method for Topics end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forTopicsE2ETests() {
        return forGlobalKillSwitchDisabledTests()
                .setTopicsKillSwitch(false)
                .setTopicsOnDeviceClassifierKillSwitch(false)
                .setTopicsClassifierForceUseBundleFiles(true)
                .setDisableTopicsEnrollmentCheckForTests(true)
                .setEnableEnrollmentTestSeed(true)
                .setConsentManagerDebugMode(true)
                .setCompatModeFlags();
    }

    /**
     * @deprecated temporary method used only by {@code CompatAdServicesTestUtils} and similar
     *     helpers, it will be remove once such helpers are replaced by this rule.
     * @return
     */
    @Deprecated
    public static AdServicesFlagsSetterRule forLegacyHelpers(Class<?> helperClass) {
        AdServicesFlagsSetterRule rule =
                new AdServicesFlagsSetterRule(/* usedByLegacyHelper= */ true);

        // This object won't be used as a JUnit rule, so we need to explicitly initialize it
        String testName = helperClass.getSimpleName();
        rule.setInitialSystemProperties(testName);
        rule.setInitialFlags(testName);

        return rule;
    }

    // NOTE: add more factory methods as needed

    /**
     * Dumps all flags using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code dumpFlags("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public void dumpFlags(@FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        String message =
                "Logging all flags on " + TAG + ". Reason: " + String.format(reasonFmt, reasonArgs);
        Log.i(TAG, message);
        Log.v(
                TAG,
                ShellUtils.runShellCommand(
                        "device_config list %s", DeviceConfig.NAMESPACE_ADSERVICES));
    }

    // TODO(b/294423183): add dumpProperties (need to filter output as
    // runShellCommand("getprop | grep PREFIX") wouldn't work

    /** Overrides the flag that sets the global AdServices kill switch. */
    public AdServicesFlagsSetterRule setGlobalKillSwitch(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_GLOBAL_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics kill switch. */
    public AdServicesFlagsSetterRule setTopicsKillSwitch(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_TOPICS_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics Device Classifier kill switch. */
    public AdServicesFlagsSetterRule setTopicsOnDeviceClassifierKillSwitch(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the enrollment seed. */
    public AdServicesFlagsSetterRule setEnableEnrollmentTestSeed(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_ENABLE_ENROLLMENT_TEST_SEED, value);
    }

    /**
     * Overrides the system property that sets max time period between each epoch computation job
     * run.
     */
    public AdServicesFlagsSetterRule setTopicsEpochJobPeriodMsForTests(long value) {
        return setOrCacheSystemProperty(PhFlags.KEY_TOPICS_EPOCH_JOB_PERIOD_MS, value);
    }

    /** Overrides the system property that defines the percentage for random topic. */
    public AdServicesFlagsSetterRule setTopicsPercentageForRandomTopicForTests(long value) {
        return setOrCacheSystemProperty(PhFlags.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, value);
    }

    /** Overrides the flag to select the topics classifier type. */
    public AdServicesFlagsSetterRule setTopicsClassifierType(int value) {
        return setOrCacheFlag(PhFlags.KEY_CLASSIFIER_TYPE, value);
    }

    /**
     * Overrides the flag to change the number of top labels returned by on-device topic classifier
     * type.
     */
    public AdServicesFlagsSetterRule setTopicsClassifierNumberOfTopLabels(int value) {
        return setOrCacheFlag(PhFlags.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS, value);
    }

    /** Overrides the flag to change the threshold for the classifier. */
    public AdServicesFlagsSetterRule setTopicsClassifierThreshold(float value) {
        return setOrCacheFlag(PhFlags.KEY_CLASSIFIER_THRESHOLD, value);
    }

    /** Overrides the flag that forces the use of bundle files for the Topics classifier. */
    public AdServicesFlagsSetterRule setTopicsClassifierForceUseBundleFiles(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, value);
    }

    public AdServicesFlagsSetterRule setTopicsClassifierForceUseBundleFilesx(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, value);
    }

    /** Overrides the system property used to disable topics enrollment check. */
    public AdServicesFlagsSetterRule setDisableTopicsEnrollmentCheckForTests(boolean value) {
        return setOrCacheSystemProperty(PhFlags.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, value);
    }

    /** Overrides the system property used to set ConsentManager debug mode keys. */
    public AdServicesFlagsSetterRule setConsentManagerDebugMode(boolean value) {
        return setOrCacheSystemProperty(PhFlags.KEY_CONSENT_MANAGER_DEBUG_MODE, value);
    }

    /** Overrides flag used by {@link PhFlags#getEnableBackCompat()}. */
    public AdServicesFlagsSetterRule setEnableBackCompat(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_ENABLE_BACK_COMPAT, value);
    }

    /** Overrides flag used by {@link PhFlags#getConsentSourceOfTruth()}. */
    public AdServicesFlagsSetterRule setConsentSourceOfTruth(int value) {
        return setOrCacheFlag(PhFlags.KEY_CONSENT_SOURCE_OF_TRUTH, value);
    }

    /** Overrides flag used by {@link PhFlags#getBlockedTopicsSourceOfTruth()}. */
    public AdServicesFlagsSetterRule setBlockedTopicsSourceOfTruth(int value) {
        return setOrCacheFlag(PhFlags.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH, value);
    }

    /** Overrides flag used by {@link PhFlags#getEnableAppsearchConsentData()}. */
    public AdServicesFlagsSetterRule setEnableAppsearchConsentData(boolean value) {
        return setOrCacheFlag(PhFlags.KEY_ENABLE_APPSEARCH_CONSENT_DATA, value);
    }

    /**
     * Overrides flag used by {@link PhFlags#getMeasurementRollbackDeletionAppSearchKillSwitch()}.
     */
    public AdServicesFlagsSetterRule setMeasurementRollbackDeletionAppSearchKillSwitch(
            boolean value) {
        return setOrCacheFlag(
                PhFlags.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH, value);
    }

    /**
     * Sets all flags needed to enable compatibility mode, according to the Android version of the
     * device running the test.
     */
    public AdServicesFlagsSetterRule setCompatModeFlags() {
        if (SdkLevel.isAtLeastT()) {
            Log.d(TAG, "setCompatModeFlags(): ignored on SDK " + SDK_INT);
            // Do nothing; this method is intended to set flags for Android S- only.
            return this;
        }

        if (SdkLevel.isAtLeastS()) {
            Log.d(TAG, "setCompatModeFlags(): setting flags for S+");
            setEnableBackCompat(true);
            setBlockedTopicsSourceOfTruth(Flags.APPSEARCH_ONLY);
            setConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
            setEnableAppsearchConsentData(true);
            setMeasurementRollbackDeletionAppSearchKillSwitch(false);
            return this;
        }
        Log.d(TAG, "setCompatModeFlags(): setting flags for R+");
        setEnableBackCompat(true);
        // TODO (b/285208753): Update flags once AppSearch is supported on R.
        setBlockedTopicsSourceOfTruth(Flags.PPAPI_ONLY);
        setConsentSourceOfTruth(Flags.PPAPI_ONLY);
        setEnableAppsearchConsentData(false);
        setMeasurementRollbackDeletionAppSearchKillSwitch(true);

        return this;
    }

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils.resetFlagsToDefault()} - flags are
     *     automatically reset when used as a JUnit Rule.
     */
    @Deprecated
    public void resetCompatModeFlags() {
        Log.d(TAG, "resetCompatModeFlags()");
        if (!mUsedByLegacyHelper) {
            throw new UnsupportedOperationException("Only available for legacy helpers");
        }
        if (SdkLevel.isAtLeastT()) {
            Log.v(TAG, "resetCompatModeFlags(): ignored on " + SDK_INT);
            // Do nothing; this method is intended to set flags for Android S- only.
            return;
        }
        Log.v(TAG, "resetCompatModeFlags(): setting flags on " + SDK_INT);
        setEnableBackCompat(false);
        // TODO (b/285208753): Set to AppSearch always once it's supported on R.
        setBlockedTopicsSourceOfTruth(
                SdkLevel.isAtLeastS() ? Flags.APPSEARCH_ONLY : Flags.PPAPI_ONLY);
        setConsentSourceOfTruth(SdkLevel.isAtLeastS() ? Flags.APPSEARCH_ONLY : Flags.PPAPI_ONLY);
        setEnableAppsearchConsentData(SdkLevel.isAtLeastS());
        setMeasurementRollbackDeletionAppSearchKillSwitch(!SdkLevel.isAtLeastS());
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, boolean value) {
        return setOrCacheFlag(name, Boolean.toString(value));
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, int value) {
        return setOrCacheFlag(name, Integer.toString(value));
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, float value) {
        return setOrCacheFlag(name, Float.toString(value));
    }

    private void setInitialFlags(String testName) {
        if (mInitialFlags == null) {
            throw new IllegalStateException("already called");
        }
        if (mInitialFlags.isEmpty()) {
            Log.d(TAG, "Not setting any flag before " + testName);
        } else {
            int size = mInitialFlags.size();
            Log.d(TAG, "Setting " + size + " flags before " + testName);
            mInitialFlags.forEach(pair -> setFlag(pair.first, pair.second));
        }
        mInitialFlags = null;
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, String value) {
        if (mInitialFlags != null) {
            // TODO(b/294423183): integrate with custom runner so it's ignored (or throw exception)
            // when called to set a flag that is managed by it
            Log.v(TAG, "Caching flag " + name + "=" + value + " as test is not running yet");
            mInitialFlags.add(new Pair<>(name, value));
            return this;
        }
        return setFlag(name, value);
    }

    private AdServicesFlagsSetterRule setFlag(String name, String value) {
        Log.v(TAG, "Setting flag: " + name + "=" + value);
        mDeviceConfig.set(name, value);
        return this;
    }

    private void resetFlags(String testName) {
        Log.d(TAG, "Resetting flags after " + testName);
        mDeviceConfig.reset();
    }

    private void setInitialSystemProperties(String testName) {
        if (mInitialSystemProperties == null) {
            throw new IllegalStateException("already called");
        }
        if (mInitialSystemProperties.isEmpty()) {
            Log.d(TAG, "Not setting any SystemProperty before " + testName);
        } else {
            int size = mInitialSystemProperties.size();
            Log.d(TAG, "Setting " + size + " SystemProperty before " + testName);
            mInitialSystemProperties.forEach(pair -> setSystemProperty(pair.first, pair.second));
        }
        mInitialSystemProperties = null;
    }

    private AdServicesFlagsSetterRule setOrCacheSystemProperty(String name, boolean value) {
        return setOrCacheSystemProperty(name, Boolean.toString(value));
    }

    private AdServicesFlagsSetterRule setOrCacheSystemProperty(String name, long value) {
        return setOrCacheSystemProperty(name, Long.toString(value));
    }

    private AdServicesFlagsSetterRule setOrCacheSystemProperty(String name, String value) {
        if (mInitialSystemProperties != null) {
            Log.v(
                    TAG,
                    "Caching SystemProperty " + name + "=" + value + " as test is not running yet");
            mInitialSystemProperties.add(new Pair<>(name, value));
            return this;
        }
        return setSystemProperty(name, value);
    }

    private AdServicesFlagsSetterRule setSystemProperty(String name, String value) {
        mSystemProperties.set(name, value);
        return this;
    }

    private void resetSystemProperties(String testName) {
        Log.d(TAG, "Resetting SystemProperties after " + testName);
        mSystemProperties.reset();
    }

    private void runSafely(List<Throwable> errors, Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            errors.add(e);
        }
    }
}
