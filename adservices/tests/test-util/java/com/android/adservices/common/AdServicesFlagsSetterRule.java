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

import android.util.Log;
import android.util.Pair;

import com.android.adservices.common.AbstractFlagsRouletteRunner.FlagsRouletteState;
import com.android.adservices.common.DeviceConfigHelper.SyncDisabledMode;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.PhFlags;
import com.android.modules.utils.build.SdkLevel;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO(b/294423183): add unit tests for the most relevant / less repetitive stuff (don't need to
// test all setters / getters, for example)
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

    private static final String ALLOWLIST_SEPARATOR = FlagsConstants.ARRAY_SPLITTER_COMMA;

    private final DeviceConfigHelper mDeviceConfig =
            new DeviceConfigHelper(FlagsConstants.NAMESPACE_ADSERVICES);

    private final SystemPropertiesHelper mSystemProperties =
            new SystemPropertiesHelper(FlagsConstants.SYSTEM_PROPERTY_PREFIX);

    // Cache flags that were set before the test started, so the rule can be instantiated using a
    // builder-like approach - will be set to null after test starts.
    @Nullable private List<Flag> mInitialFlags = new ArrayList<>();

    // Cache system properties that were set before the test started, so the rule can be
    // instantiated using a builder-like approach - will be set to null after test starts.
    @Nullable private List<Pair<String, String>> mInitialSystemProperties = new ArrayList<>();

    // TODO(b/294423183): remove once legacy usage is gone
    private final boolean mUsedByLegacyHelper;

    private AdServicesFlagsSetterRule() {
        this(/* usedByLegacyHelper= */ false);
    }

    private AdServicesFlagsSetterRule(boolean usedByLegacyHelper) {
        mUsedByLegacyHelper = usedByLegacyHelper;
    }

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
                List<Throwable> cleanUpErrors = new ArrayList<>();
                Throwable testError = null;
                StringBuilder dump = new StringBuilder("*** Flags before:\n");
                dumpFlagsSafely(dump).append("\n\n*** SystemProperties before:\n");
                dumpSystemPropertiesSafely(dump);
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    testError = t;
                } finally {
                    dump.append("\n*** Flags after:\n");
                    dumpFlagsSafely(dump).append("\n\n***SystemProperties after:\n");
                    dumpSystemPropertiesSafely(dump);
                    runSafely(cleanUpErrors, () -> resetFlags(testName));
                    runSafely(cleanUpErrors, () -> resetSystemProperties(testName));
                    runSafely(
                            cleanUpErrors,
                            () -> mDeviceConfig.setSyncDisabledMode(SyncDisabledMode.NONE));
                }
                // TODO(b/294423183): ideally it should throw an exception if cleanUpErrors is not
                // empty, but it's better to wait until this class is unit tested to do so (for now,
                // it's just logging it)
                throwIfNecessary(testName, dump, testError);
            }
        };
    }

    private void throwIfNecessary(
            String testName, StringBuilder dump, @Nullable Throwable testError) throws Throwable {
        if (testError == null) {
            Log.v(TAG, "Good News, Everyone! " + testName + " passed.");
            return;
        }
        if (testError instanceof AssumptionViolatedException) {
            Log.i(TAG, testName + " is being ignored: " + testError);
            throw testError;
        }
        Log.e(TAG, testName + " failed with " + testError + ".\n" + dump);
        throw new TestFailure(testError, dump);
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

    /** Factory method for AdId end-to-end CTS tests. */
    public static AdServicesFlagsSetterRule forAdidE2ETests(String packageName) {
        return forGlobalKillSwitchDisabledTests()
                .setAdIdKillSwitchForTests(false)
                .setAdIdRequestPermitsPerSecond(25.0)
                .setPpapiAppAllowList(packageName)
                .setCompatModeFlag();
    }

    /**
     * @deprecated temporary method used only by {@code CompatAdServicesTestUtils} and similar
     *     helpers, it will be remove once such helpers are replaced by this rule.
     */
    @Deprecated
    static AdServicesFlagsSetterRule forLegacyHelpers(Class<?> helperClass) {
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
        StringBuilder message =
                new StringBuilder("Logging all flags on ")
                        .append(TAG)
                        .append(". Reason: ")
                        .append(String.format(reasonFmt, reasonArgs))
                        .append(". Flags: \n");
        dumpFlagsSafely(message);
        Log.i(TAG, message.toString());
    }

    private StringBuilder dumpFlagsSafely(StringBuilder dump) {
        try {
            mDeviceConfig.dumpFlags(dump);
        } catch (Throwable t) {
            dump.append("Failed to dump flags: ").append(t);
        }
        return dump;
    }

    /**
     * Dumps all system properties using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code
     * dumpSystemProperties("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public void dumpSystemProperties(
            @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        StringBuilder message =
                new StringBuilder("Logging all SystemProperties on ")
                        .append(TAG)
                        .append(". Reason: ")
                        .append(String.format(reasonFmt, reasonArgs))
                        .append(". SystemProperties: \n");
        dumpSystemPropertiesSafely(message);
        Log.i(TAG, message.toString());
    }

    private StringBuilder dumpSystemPropertiesSafely(StringBuilder dump) {
        try {
            mSystemProperties.dump(dump);
        } catch (Throwable t) {
            dump.append("Failed to dump SystemProperties: ").append(t);
        }
        return dump;
    }

    /** Overrides the flag that sets the global AdServices kill switch. */
    public AdServicesFlagsSetterRule setGlobalKillSwitch(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_GLOBAL_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics kill switch. */
    public AdServicesFlagsSetterRule setTopicsKillSwitch(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_TOPICS_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics Device Classifier kill switch. */
    public AdServicesFlagsSetterRule setTopicsOnDeviceClassifierKillSwitch(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the enrollment seed. */
    public AdServicesFlagsSetterRule setEnableEnrollmentTestSeed(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED, value);
    }

    /**
     * Overrides the system property that sets max time period between each epoch computation job
     * run.
     */
    public AdServicesFlagsSetterRule setTopicsEpochJobPeriodMsForTests(long value) {
        return setOrCacheSystemProperty(FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS, value);
    }

    /** Overrides the system property that defines the percentage for random topic. */
    public AdServicesFlagsSetterRule setTopicsPercentageForRandomTopicForTests(long value) {
        return setOrCacheSystemProperty(
                FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, value);
    }

    /** Overrides the flag to select the topics classifier type. */
    public AdServicesFlagsSetterRule setTopicsClassifierType(int value) {
        return setOrCacheFlag(FlagsConstants.KEY_CLASSIFIER_TYPE, value);
    }

    /**
     * Overrides the flag to change the number of top labels returned by on-device topic classifier
     * type.
     */
    public AdServicesFlagsSetterRule setTopicsClassifierNumberOfTopLabels(int value) {
        return setOrCacheFlag(FlagsConstants.KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS, value);
    }

    /** Overrides the flag to change the threshold for the classifier. */
    public AdServicesFlagsSetterRule setTopicsClassifierThreshold(float value) {
        return setOrCacheFlag(FlagsConstants.KEY_CLASSIFIER_THRESHOLD, value);
    }

    /** Overrides the flag that forces the use of bundle files for the Topics classifier. */
    public AdServicesFlagsSetterRule setTopicsClassifierForceUseBundleFiles(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, value);
    }

    public AdServicesFlagsSetterRule setTopicsClassifierForceUseBundleFilesx(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, value);
    }

    /** Overrides the system property used to disable topics enrollment check. */
    public AdServicesFlagsSetterRule setDisableTopicsEnrollmentCheckForTests(boolean value) {
        return setOrCacheSystemProperty(FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, value);
    }

    /** Overrides the system property used to set ConsentManager debug mode keys. */
    public AdServicesFlagsSetterRule setConsentManagerDebugMode(boolean value) {
        return setOrCacheSystemProperty(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, value);
    }

    /** Overrides flag used by {@link PhFlags#getEnableBackCompat()}. */
    public AdServicesFlagsSetterRule setEnableBackCompat(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, value);
    }

    /** Overrides flag used by {@link PhFlags#getConsentSourceOfTruth()}. */
    public AdServicesFlagsSetterRule setConsentSourceOfTruth(int value) {
        return setOrCacheFlag(FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH, value);
    }

    /** Overrides flag used by {@link PhFlags#getBlockedTopicsSourceOfTruth()}. */
    public AdServicesFlagsSetterRule setBlockedTopicsSourceOfTruth(int value) {
        return setOrCacheFlag(FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH, value);
    }

    /** Overrides flag used by {@link PhFlags#getEnableAppsearchConsentData()}. */
    public AdServicesFlagsSetterRule setEnableAppsearchConsentData(boolean value) {
        return setOrCacheFlag(FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA, value);
    }

    /**
     * Overrides flag used by {@link PhFlags#getMeasurementRollbackDeletionAppSearchKillSwitch()}.
     */
    public AdServicesFlagsSetterRule setMeasurementRollbackDeletionAppSearchKillSwitch(
            boolean value) {
        return setOrCacheFlag(
                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH, value);
    }

    /** Overrides flag used by {@link PhFlags#getPpapiAppAllowList()}. */
    public AdServicesFlagsSetterRule setPpapiAppAllowList(String value) {
        return setOrCacheFlagWithSeparator(
                FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /** Overrides flag used by {@link PhFlags#getMsmtApiAppAllowList()}. */
    public AdServicesFlagsSetterRule setMsmtApiAppAllowList(String value) {
        return setOrCacheFlagWithSeparator(
                FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /** Overrides flag used by {@link PhFlags#getAdIdRequestPermitsPerSecond()}. */
    public AdServicesFlagsSetterRule setAdIdRequestPermitsPerSecond(double value) {
        return setOrCacheFlag(FlagsConstants.KEY_ADID_REQUEST_PERMITS_PER_SECOND, value);
    }

    /** Overrides flag used by {@link PhFlags#getAdIdKillSwitchForTests()}. */
    public AdServicesFlagsSetterRule setAdIdKillSwitchForTests(boolean value) {
        return setOrCacheSystemProperty(FlagsConstants.KEY_ADID_KILL_SWITCH, value);
    }

    /** Calls {@link PhFlags#getAdIdRequestPerSecond()} with the proper permissions. */
    public float getAdIdRequestPerSecond() {
        try {
            return DeviceConfigHelper.callWithDeviceConfigPermissions(
                    () -> PhFlags.getInstance().getAdIdRequestPermitsPerSecond());
        } catch (Throwable t) {
            float defaultValue = FlagsConstants.ADID_REQUEST_PERMITS_PER_SECOND;
            Log.e(
                    TAG,
                    "FlagsConstants.getAdIdRequestPermitsPerSecond() failed, returning default"
                            + " value ("
                            + defaultValue
                            + ")",
                    t);
            return defaultValue;
        }
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
            setBlockedTopicsSourceOfTruth(FlagsConstants.APPSEARCH_ONLY);
            setConsentSourceOfTruth(FlagsConstants.APPSEARCH_ONLY);
            setEnableAppsearchConsentData(true);
            setMeasurementRollbackDeletionAppSearchKillSwitch(false);
            return this;
        }
        Log.d(TAG, "setCompatModeFlags(): setting flags for R+");
        setEnableBackCompat(true);
        // TODO (b/285208753): Update flags once AppSearch is supported on R.
        setBlockedTopicsSourceOfTruth(FlagsConstants.PPAPI_ONLY);
        setConsentSourceOfTruth(FlagsConstants.PPAPI_ONLY);
        setEnableAppsearchConsentData(false);
        setMeasurementRollbackDeletionAppSearchKillSwitch(true);

        return this;
    }

    /**
     * Sets just the flag needed by {@link PhFlags#getEnableBackCompat()}, but only if required by
     * the Android version of the device running the test.
     */
    public AdServicesFlagsSetterRule setCompatModeFlag() {
        if (SdkLevel.isAtLeastT()) {
            Log.d(TAG, "setCompatModeFlag(): ignored on SDK " + SDK_INT);
            // Do nothing; this method is intended to set flags for Android S- only.
            return this;
        }
        Log.d(TAG, "setCompatModeFlag(): setting flags on " + SDK_INT);
        setEnableBackCompat(true);
        return this;
    }

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils.resetFlagsToDefault()} - flags are
     *     automatically reset when used as a JUnit Rule.
     */
    @Deprecated
    void resetCompatModeFlags() {
        Log.d(TAG, "resetCompatModeFlags()");
        assertCalledByLegacyHelper();
        if (SdkLevel.isAtLeastT()) {
            Log.v(TAG, "resetCompatModeFlags(): ignored on " + SDK_INT);
            // Do nothing; this method is intended to set flags for Android S- only.
            return;
        }
        Log.v(TAG, "resetCompatModeFlags(): setting flags on " + SDK_INT);
        setEnableBackCompat(false);
        // TODO (b/285208753): Set to AppSearch always once it's supported on R.
        setBlockedTopicsSourceOfTruth(
                SdkLevel.isAtLeastS() ? FlagsConstants.APPSEARCH_ONLY : FlagsConstants.PPAPI_ONLY);
        setConsentSourceOfTruth(
                SdkLevel.isAtLeastS() ? FlagsConstants.APPSEARCH_ONLY : FlagsConstants.PPAPI_ONLY);
        setEnableAppsearchConsentData(SdkLevel.isAtLeastS());
        setMeasurementRollbackDeletionAppSearchKillSwitch(!SdkLevel.isAtLeastS());
    }

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils}
     */
    @Deprecated
    String getPpapiAppAllowList() {
        assertCalledByLegacyHelper();
        return mDeviceConfig.get(FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST);
    }

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils}
     */
    @Deprecated
    String getMsmtApiAppAllowList() {
        assertCalledByLegacyHelper();
        return mDeviceConfig.get(FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST);
    }

    private void assertCalledByLegacyHelper() {
        if (!mUsedByLegacyHelper) {
            throw new UnsupportedOperationException("Only available for legacy helpers");
        }
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, boolean value) {
        return setOrCacheFlag(name, Boolean.toString(value));
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, int value) {
        return setOrCacheFlag(name, Integer.toString(value));
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, double value) {
        return setOrCacheFlag(name, Double.toString(value));
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
            mInitialFlags.forEach(flag -> setFlag(flag));
        }
        mInitialFlags = null;
    }

    private AdServicesFlagsSetterRule setOrCacheFlagWithSeparator(
            String name, String value, String separator) {
        return setOrCacheFlag(name, value, Objects.requireNonNull(separator));
    }

    private AdServicesFlagsSetterRule setOrCacheFlag(String name, String value) {
        return setOrCacheFlag(name, value, /* separator= */ null);
    }

    // TODO(b/294423183): need to add unit test for setters that call this
    private AdServicesFlagsSetterRule setOrCacheFlag(
            String name, String value, @Nullable String separator) {
        Flag flag = new Flag(name, value, separator);
        if (mInitialFlags != null) {
            if (isFlagManagedByRunner(name)) {
                return this;
            }
            Log.v(TAG, "Caching flag " + flag + " as test is not running yet");
            mInitialFlags.add(flag);
            return this;
        }
        return setFlag(flag);
    }

    private boolean isFlagManagedByRunner(String flag) {
        FlagsRouletteState roulette = AbstractFlagsRouletteRunner.getFlagsRouletteState();
        if (roulette == null || !roulette.flagNames.contains(flag)) {
            return false;
        }
        Log.w(
                TAG,
                "Not setting flag "
                        + flag
                        + " as it's managed by "
                        + roulette.runnerName
                        + " (which manages "
                        + roulette.flagNames
                        + ")");
        return true;
    }

    private AdServicesFlagsSetterRule setFlag(Flag flag) {
        Log.v(TAG, "Setting flag: " + flag);
        if (flag.separator == null) {
            mDeviceConfig.set(flag.name, flag.value);
        } else {
            mDeviceConfig.setWithSeparator(flag.name, flag.value, flag.separator);
        }
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
            Log.e(TAG, "runSafely() failure", e);
            errors.add(e);
        }
    }

    @SuppressWarnings("serial")
    public static final class TestFailure extends Exception {

        private final String mDump;

        TestFailure(Throwable cause, StringBuilder dump) {
            super(
                    "Test failed (see flags / system proprties below the stack trace)",
                    cause,
                    /* enableSuppression= */ false,
                    /* writableStackTrace= */ false);
            mDump = "\n" + dump;
            setStackTrace(cause.getStackTrace());
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            super.printStackTrace(s);
            s.println(mDump);
        }

        @Override
        public void printStackTrace(PrintStream s) {
            super.printStackTrace(s);
            s.println(mDump);
        }

        /** Gets the flags / system properties state. */
        public String getFlagsState() {
            return mDump;
        }
    }

    private final class Flag {
        public final String name;
        public final String value;
        public final @Nullable String separator;

        Flag(String name, String value, @Nullable String separator) {
            this.name = name;
            this.value = value;
            this.separator = separator;
        }

        // TODO(b/294423183): need to add unit test for equals() / hashcode() as they don't use
        // separator

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + Objects.hash(name, value);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Flag other = (Flag) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance())) return false;
            return Objects.equals(name, other.name) && Objects.equals(value, other.value);
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder(name).append('=').append(value);
            if (separator != null) {
                string.append(" (separator=").append(separator).append(')');
            }
            return string.toString();
        }

        private AdServicesFlagsSetterRule getEnclosingInstance() {
            return AdServicesFlagsSetterRule.this;
        }
    }
}
