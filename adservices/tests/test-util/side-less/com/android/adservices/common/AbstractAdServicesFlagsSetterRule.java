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

import static com.android.adservices.service.FlagsConstants.ARRAY_SPLITTER_COMMA;
import static com.android.adservices.service.FlagsConstants.NAMESPACE_ADSERVICES;

import com.android.adservices.common.DeviceConfigHelper.SyncDisabledModeForTest;
import com.android.adservices.common.Logger.RealLogger;
import com.android.adservices.common.annotations.SetDoubleFlag;
import com.android.adservices.common.annotations.SetDoubleFlags;
import com.android.adservices.common.annotations.SetFlagDisabled;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.adservices.common.annotations.SetFlagsDisabled;
import com.android.adservices.common.annotations.SetFlagsEnabled;
import com.android.adservices.common.annotations.SetFloatFlag;
import com.android.adservices.common.annotations.SetFloatFlags;
import com.android.adservices.common.annotations.SetIntegerFlag;
import com.android.adservices.common.annotations.SetIntegerFlags;
import com.android.adservices.common.annotations.SetLongFlag;
import com.android.adservices.common.annotations.SetLongFlags;
import com.android.adservices.common.annotations.SetStringFlag;
import com.android.adservices.common.annotations.SetStringFlags;
import com.android.adservices.service.FlagsConstants;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
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
 * <p>Most methods set {@link android.provider.DeviceConfig} flags, although some sets {@link
 * android.os.SystemProperties} instead - those are typically suffixed with {@code forTests}
 */
////////////////////////////////////////////////////////////////////////////////////////////////////
// NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let your   //
// test call setFlags(flagName) (statically import FlagsConstant.flagName), which will make it    //
// easier to transition the test to an annotated-base approach.                                   //
////////////////////////////////////////////////////////////////////////////////////////////////////
abstract class AbstractAdServicesFlagsSetterRule<T extends AbstractAdServicesFlagsSetterRule<T>>
        implements TestRule {

    private static final String ALLOWLIST_SEPARATOR = ARRAY_SPLITTER_COMMA;

    // TODO(b/295321663): static import from AdServicesCommonConstants instead
    public static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";

    private static final String SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX = "log.tag.";

    protected static final String LOGCAT_LEVEL_VERBOSE = "VERBOSE";

    // TODO(b/295321663): move these constants (and those from LogFactory) to AdServicesCommon
    protected static final String LOGCAT_TAG_ADSERVICES = "adservices";
    protected static final String LOGCAT_TAG_ADSERVICES_SERVICE = LOGCAT_TAG_ADSERVICES + "-system";
    protected static final String LOGCAT_TAG_TOPICS = LOGCAT_TAG_ADSERVICES + ".topics";
    protected static final String LOGCAT_TAG_FLEDGE = LOGCAT_TAG_ADSERVICES + ".fledge";
    protected static final String LOGCAT_TAG_MEASUREMENT = LOGCAT_TAG_ADSERVICES + ".measurement";
    protected static final String LOGCAT_TAG_UI = LOGCAT_TAG_ADSERVICES + ".ui";
    protected static final String LOGCAT_TAG_ADID = LOGCAT_TAG_ADSERVICES + ".adid";
    protected static final String LOGCAT_TAG_APPSETID = LOGCAT_TAG_ADSERVICES + ".appsetid";

    // TODO(b/294423183): make private once not used by subclass for legacy methods
    protected final DeviceConfigHelper mDeviceConfig;
    protected final SystemPropertiesHelper mSystemProperties;
    protected final Logger mLog;

    // Cache methods that were called before the test started, so the rule can be
    // instantiated using a builder-like approach.
    private final List<Command> mInitialCommands = new ArrayList<>();

    private final SyncDisabledModeForTest mPreviousSyncDisabledModeForTest;

    private boolean mIsRunning;
    private boolean mFlagsClearedBeforeTest;

    protected AbstractAdServicesFlagsSetterRule(
            RealLogger logger,
            DeviceConfigHelper.InterfaceFactory deviceConfigInterfaceFactory,
            SystemPropertiesHelper.Interface systemPropertiesInterface) {
        mLog = new Logger(Objects.requireNonNull(logger), "AdServicesFlagsSetterRule");
        mDeviceConfig =
                new DeviceConfigHelper(deviceConfigInterfaceFactory, NAMESPACE_ADSERVICES, logger);
        mSystemProperties = new SystemPropertiesHelper(systemPropertiesInterface, logger);
        // TODO(b/294423183): ideally the current mode should be returned by
        // setSyncDisabledMode(),
        // but unfortunately getting the current mode is not straightforward due to
        // different
        // behaviors:
        // - T+ provides get_sync_disabled_for_tests
        // - S provides is_sync_disabled_for_tests
        // - R doesn't provide anything
        mPreviousSyncDisabledModeForTest = SyncDisabledModeForTest.NONE;
        // Must set right away to avoid race conditions (for example, backend setting flags before
        // apply() is called)
        setSyncDisabledMode(SyncDisabledModeForTest.PERSISTENT);

        mLog.v(
                "Constructor: mDeviceConfig=%s, mSystemProperties=%s,"
                        + " mPreviousSyncDisabledModeForTest=%s",
                mDeviceConfig, mSystemProperties, mPreviousSyncDisabledModeForTest);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        String testName = description.getDisplayName();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mIsRunning = true;
                setAnnotatedFlags(description);
                runInitialCommands(testName);
                List<Throwable> cleanUpErrors = new ArrayList<>();
                Throwable testError = null;
                StringBuilder dump = new StringBuilder("*** Flags before:\n");
                if (mFlagsClearedBeforeTest) {
                    dump.append("\tTest explicitly cleared all flags\n");
                } else {
                    dumpFlagsSafely(dump);
                }
                dump.append("\n\n*** SystemProperties before:\n");
                dumpSystemPropertiesSafely(dump);
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    testError = t;
                } finally {
                    mIsRunning = false;
                    dump.append("\n*** Flags after:\n");
                    if (mFlagsClearedBeforeTest) {
                        runSafely(
                                cleanUpErrors,
                                () -> {
                                    clearFlags();
                                    dump.append("\tTest explicitly cleared all flags\n");
                                });
                    } else {
                        dumpFlagsSafely(dump);
                    }
                    dump.append("\n\n***SystemProperties after:\n");
                    dumpSystemPropertiesSafely(dump);
                    runSafely(cleanUpErrors, () -> resetFlags(testName));
                    runSafely(cleanUpErrors, () -> resetSystemProperties(testName));
                    runSafely(
                            cleanUpErrors,
                            () -> setSyncDisabledMode(mPreviousSyncDisabledModeForTest));
                    mIsRunning = false;
                }
                // TODO(b/294423183): ideally it should throw an exception if cleanUpErrors is not
                // empty, but it's better to wait until this class is unit tested to do so (for now,
                // it's just logging it)
                throwIfNecessary(testName, dump, testError);
            }
        };
    }

    private void setSyncDisabledMode(SyncDisabledModeForTest mode) {
        runOrCache(
                "setSyncDisabledMode(" + mode + ")", () -> mDeviceConfig.setSyncDisabledMode(mode));
    }

    private void throwIfNecessary(
            String testName, StringBuilder dump, @Nullable Throwable testError) throws Throwable {
        if (testError == null) {
            mLog.v("Good News, Everyone! %s passed.", testName);
            return;
        }
        if (testError instanceof AssumptionViolatedException) {
            mLog.i("%s is being ignored: %s", testName, testError);
            throw testError;
        }
        mLog.e("%s failed with %s.\n%s", testName, testError, dump);
        throw new TestFailure(testError, dump);
    }

    /**
     * Dumps all flags using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code dumpFlags("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public void dumpFlags(@FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        StringBuilder message =
                new StringBuilder("Logging all flags on ")
                        .append(mLog.getTag())
                        .append(". Reason: ")
                        .append(String.format(reasonFmt, reasonArgs))
                        .append(". Flags: \n");
        dumpFlagsSafely(message);
        mLog.i("%s", message);
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
                        .append(mLog.getTag())
                        .append(". Reason: ")
                        .append(String.format(reasonFmt, reasonArgs))
                        .append(". SystemProperties: \n");
        dumpSystemPropertiesSafely(message);
        mLog.i("%s", message);
    }

    private StringBuilder dumpSystemPropertiesSafely(StringBuilder dump) {
        try {
            mSystemProperties.dumpSystemProperties(dump, SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX);
        } catch (Throwable t) {
            dump.append("Failed to dump SystemProperties: ").append(t);
        }
        return dump;
    }

    /** Clear all flags from the {@code AdServices} namespace */
    public T clearFlags() {
        return runOrCache(
                "clearFlags()",
                () -> {
                    mLog.i("Clearing all flags. mIsRunning=%b", mIsRunning);
                    mDeviceConfig.clearFlags();
                    // TODO(b/294423183): ideally we should save the flags and restore - possibly
                    // using
                    // DeviceConfig properties - but for now let's just clear it.
                    if (!mIsRunning) {
                        mFlagsClearedBeforeTest = true;
                    }
                });
    }

    /** Sets the flag with the given value. */
    public T setFlag(String name, boolean value) {
        return setOrCacheFlag(name, Boolean.toString(value));
    }

    /** Sets the flag with the given value. */
    public T setFlag(String name, int value) {
        return setOrCacheFlag(name, Integer.toString(value));
    }

    /** Sets the flag with the given value. */
    public T setFlag(String name, long value) {
        return setOrCacheFlag(name, Long.toString(value));
    }

    /** Sets the flag with the given value. */
    public T setFlag(String name, float value) {
        return setOrCacheFlag(name, Float.toString(value));
    }

    /** Sets the flag with the given value. */
    public T setFlag(String name, double value) {
        return setOrCacheFlag(name, Double.toString(value));
    }

    /** Sets the flag with the given value. */
    public T setFlag(String name, String value) {
        return setOrCacheFlag(name, value);
    }

    // Add more generic setFlag for other types as needed

    // Helper methods to set more commonly used flags such as kill switches.
    // Less common flags can be set directly using setFlags methods.

    /** Overrides the flag that sets the global AdServices kill switch. */
    public T setGlobalKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_GLOBAL_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getAdServicesEnabled}.
     */
    public T setAdServicesEnabled(boolean value) {
        return setFlag(FlagsConstants.KEY_ADSERVICES_ENABLED, value);
    }

    /** Overrides the flag that sets the AppsetId kill switch. */
    public T setAppsetIdKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_APPSETID_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics kill switch. */
    public T setTopicsKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_TOPICS_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics Device Classifier kill switch. */
    public T setTopicsOnDeviceClassifierKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, value);
    }

    /**
     * Overrides the system property that sets max time period between each epoch computation job
     * run.
     */
    public T setTopicsEpochJobPeriodMsForTests(long value) {
        return setOrCacheDebugSystemProperty(FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS, value);
    }

    /** Overrides the system property that defines the percentage for random topic. */
    public T setTopicsPercentageForRandomTopicForTests(long value) {
        return setOrCacheDebugSystemProperty(
                FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC, value);
    }

    /** Overrides the system property used to disable topics enrollment check. */
    public T setDisableTopicsEnrollmentCheckForTests(boolean value) {
        return setOrCacheDebugSystemProperty(
                FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, value);
    }

    /** Overrides the system property used to set ConsentManager debug mode keys. */
    public T setConsentManagerDebugMode(boolean value) {
        return setOrCacheDebugSystemProperty(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getEnableBackCompat()}.
     */
    public T setEnableBackCompat(boolean value) {
        return setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, value);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMeasurementRollbackDeletionAppSearchKillSwitch()}.
     */
    public T setMeasurementRollbackDeletionAppSearchKillSwitch(boolean value) {
        return setFlag(
                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getPpapiAppAllowList()}.
     */
    // <p> TODO (b/303901926) - apply consistent naming to allow list methods
    public T setPpapiAppAllowList(String value) {
        return setOrCacheFlag(FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /**
     * Overrides flag used by (@link
     * com.android.adservices.service.PhFlags#getPpapiAppSignatureAllowList()}. NOTE: this will
     * completely override the allow list, *not* append to it.
     */
    // <p> TODO (b/303901926) - apply consistent naming to allow list methods
    public T overridePpapiAppSignatureAllowList(String value) {
        return setFlag(FlagsConstants.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST, value);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMsmtApiAppAllowList()}.
     */
    public T setMsmtApiAppAllowList(String value) {
        return setOrCacheFlag(
                FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getWebContextClientAppAllowList()}.
     */
    public T setMsmtWebContextClientAllowList(String value) {
        return setOrCacheFlagWithSeparator(
                FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getAdIdKillSwitchForTests()}.
     */
    public T setAdIdKillSwitchForTests(boolean value) {
        return setOrCacheDebugSystemProperty(FlagsConstants.KEY_ADID_KILL_SWITCH, value);
    }

    /** Overrides flag used by {@link android.adservices.common.AdServicesCommonManager}. */
    public T setAdserviceEnableStatus(boolean value) {
        return setFlag(FlagsConstants.KEY_ADSERVICES_ENABLED, value);
    }

    /** Overrides flag used by {@link android.adservices.common.AdServicesCommonManager}. */
    public T setUpdateAdIdCacheEnabled(boolean value) {
        return setFlag(FlagsConstants.KEY_AD_ID_CACHE_ENABLED, value);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMddBackgroundTaskKillSwitch()}.
     */
    public T setMddBackgroundTaskKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH, value);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets all flags needed to enable compatibility mode, according to the Android version of the
     * device running the test.
     */
    public T setCompatModeFlags() {
        return runOrCache(
                "setCompatModeFlags()",
                () -> {
                    if (isAtLeastT()) {
                        mLog.d("setCompatModeFlags(): ignored on SDK %d", getDeviceSdk());
                        // Do nothing; this method is intended to set flags for Android S- only.
                        return;
                    }

                    if (isAtLeastS()) {
                        mLog.d("setCompatModeFlags(): setting flags for S+");
                        setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, true);
                        setFlag(
                                FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH,
                                FlagsConstants.APPSEARCH_ONLY);
                        setFlag(
                                FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH,
                                FlagsConstants.APPSEARCH_ONLY);
                        setFlag(FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA, true);
                        setFlag(
                                FlagsConstants
                                        .KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                                false);
                        return;
                    }
                    mLog.d("setCompatModeFlags(): setting flags for R+");
                    setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, true);
                    // TODO (b/285208753): Update flags once AppSearch is supported on R.
                    setFlag(
                            FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH,
                            FlagsConstants.PPAPI_ONLY);
                    setFlag(FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH, FlagsConstants.PPAPI_ONLY);
                    setFlag(FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA, false);
                    setFlag(
                            FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                            true);
                });
    }

    /**
     * Sets just the flag needed by {@link
     * com.android.adservices.service.PhFlags#getEnableBackCompat()}, but only if required by the
     * Android version of the device running the test.
     */
    public T setCompatModeFlag() {
        return runOrCache(
                "setCompatModeFlag()",
                () -> {
                    if (isAtLeastT()) {
                        mLog.d("setCompatModeFlag(): ignored on SDK %d", getDeviceSdk());
                        // Do nothing; this method is intended to set flags for Android S- only.
                        return;
                    }
                    mLog.d("setCompatModeFlag(): setting flags on SDK %d", getDeviceSdk());
                    setEnableBackCompat(true);
                });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @deprecated only used by {@code CompatAdServicesTestUtils.resetFlagsToDefault()} - flags are
     *     automatically reset when used as a JUnit Rule.
     */
    @Deprecated
    T resetCompatModeFlags() {
        assertCalledByLegacyHelper();
        return runOrCache(
                "resetCompatModeFlags()",
                () -> {
                    if (isAtLeastT()) {
                        mLog.d("resetCompatModeFlags(): ignored on %d", getDeviceSdk());
                        // Do nothing; this method is intended to set flags for Android S- only.
                        return;
                    }
                    mLog.v("resetCompatModeFlags(): setting flags on %d", getDeviceSdk());
                    setEnableBackCompat(false);
                    // TODO (b/285208753): Set to AppSearch always once it's supported on R.
                    boolean atLeastS = isAtLeastS();
                    int sourceOfTruth =
                            atLeastS ? FlagsConstants.APPSEARCH_ONLY : FlagsConstants.PPAPI_ONLY;
                    setFlag(FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH, sourceOfTruth);
                    setFlag(FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH, sourceOfTruth);
                    setFlag(FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA, atLeastS);
                    setMeasurementRollbackDeletionAppSearchKillSwitch(!atLeastS);
                });
    }

    /** Sets a {@code logcat} tag. */
    public T setLogcatTag(String tag, String level) {
        setOrCacheLogtagSystemProperty(tag, level);
        return getThis();
    }

    /**
     * Sets the common AdServices {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     */
    public T setDefaultLogcatTags() {
        setLogcatTag(LOGCAT_TAG_ADSERVICES, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_ADSERVICES_SERVICE, LOGCAT_LEVEL_VERBOSE);
        return getThis();
    }

    /**
     * Sets all AdServices {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     */
    public T setAllLogcatTags() {
        setDefaultLogcatTags();
        setLogcatTag(LOGCAT_TAG_TOPICS, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_FLEDGE, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_MEASUREMENT, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_ADID, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_APPSETID, LOGCAT_LEVEL_VERBOSE);
        return getThis();
    }

    // TODO(295007931): abstract SDK-related methods in a new SdkLevelHelper and reuse them on
    // SdkLevelSupportRule
    /** Gets the device's SDK level. */
    protected abstract int getDeviceSdk();

    protected boolean isAtLeastS() {
        return getDeviceSdk() > 31;
    }

    protected boolean isAtLeastT() {
        return getDeviceSdk() > 32;
    }

    // TODO(b/294423183): remove once legacy usage is gone
    /**
     * Checks whether this rule is used to implement some "legacy" helpers (i.e., deprecated classes
     * that will be removed once their clients use the rule) like {@code CompatAdServicesTestUtils}.
     */
    protected boolean isCalledByLegacyHelper() {
        return false;
    }

    // TODO(b/294423183): remove once legacy usage is gone
    protected final void assertCalledByLegacyHelper() {
        if (!isCalledByLegacyHelper()) {
            throw new UnsupportedOperationException("Only available for legacy helpers");
        }
    }

    // Set the annotated flags with the specified value for a particular test method.
    protected void setAnnotatedFlags(Description description) {
        for (Annotation annotation : description.getAnnotations()) {
            if (annotation instanceof SetFlagEnabled) {
                setAnnotatedFlag((SetFlagEnabled) annotation);
            } else if (annotation instanceof SetFlagsEnabled) {
                setAnnotatedFlag((SetFlagsEnabled) annotation);
            } else if (annotation instanceof SetFlagDisabled) {
                setAnnotatedFlag((SetFlagDisabled) annotation);
            } else if (annotation instanceof SetFlagsDisabled) {
                setAnnotatedFlag((SetFlagsDisabled) annotation);
            } else if (annotation instanceof SetIntegerFlag) {
                setAnnotatedFlag((SetIntegerFlag) annotation);
            } else if (annotation instanceof SetIntegerFlags) {
                setAnnotatedFlag((SetIntegerFlags) annotation);
            } else if (annotation instanceof SetLongFlag) {
                setAnnotatedFlag((SetLongFlag) annotation);
            } else if (annotation instanceof SetLongFlags) {
                setAnnotatedFlag((SetLongFlags) annotation);
            } else if (annotation instanceof SetFloatFlag) {
                setAnnotatedFlag((SetFloatFlag) annotation);
            } else if (annotation instanceof SetFloatFlags) {
                setAnnotatedFlag((SetFloatFlags) annotation);
            } else if (annotation instanceof SetDoubleFlag) {
                setAnnotatedFlag((SetDoubleFlag) annotation);
            } else if (annotation instanceof SetDoubleFlags) {
                setAnnotatedFlag((SetDoubleFlags) annotation);
            } else if (annotation instanceof SetStringFlag) {
                setAnnotatedFlag((SetStringFlag) annotation);
            } else if (annotation instanceof SetStringFlags) {
                setAnnotatedFlag((SetStringFlags) annotation);
            }
        }
        // TODO(b/300146214) Add code to scan class / superclasses flag annotations.
    }

    private T setOrCacheFlagWithSeparator(String name, String value, String separator) {
        return setOrCacheFlag(name, value, Objects.requireNonNull(separator));
    }

    private T setOrCacheFlag(String name, String value) {
        return setOrCacheFlag(name, value, /* separator= */ null);
    }

    // TODO(b/294423183): need to add unit test for setters that call this
    private T setOrCacheFlag(String name, String value, @Nullable String separator) {
        FlagOrSystemProperty flag = new FlagOrSystemProperty(name, value, separator);
        if (!mIsRunning && !isCalledByLegacyHelper()) {
            if (isFlagManagedByRunner(name)) {
                return getThis();
            }
            cacheCommand(new SetFlagCommand(flag));
            return getThis();
        }
        return setFlag(flag);
    }

    // TODO(b/295321663): need to provide a more elegant way to integrate it with the custom runners
    protected boolean isFlagManagedByRunner(String flag) {
        return false;
    }

    private T setFlag(FlagOrSystemProperty flag) {
        mLog.d("Setting flag: %s", flag);
        if (flag.separator == null) {
            mDeviceConfig.set(flag.name, flag.value);
        } else {
            mDeviceConfig.setWithSeparator(flag.name, flag.value, flag.separator);
        }
        return getThis();
    }

    private void resetFlags(String testName) {
        mLog.d("Resetting flags after %s", testName);
        mDeviceConfig.reset();
    }

    protected T setOrCacheDebugSystemProperty(String name, boolean value) {
        return setOrCacheDebugSystemProperty(name, Boolean.toString(value));
    }

    private T setOrCacheDebugSystemProperty(String name, long value) {
        return setOrCacheDebugSystemProperty(name, Long.toString(value));
    }

    private T setOrCacheDebugSystemProperty(String name, String value) {
        return setOrCacheSystemProperty(SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + name, value);
    }

    private T setOrCacheLogtagSystemProperty(String name, String value) {
        return setOrCacheSystemProperty(SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX + name, value);
    }

    private T setOrCacheSystemProperty(String name, String value) {
        FlagOrSystemProperty systemProperty = new FlagOrSystemProperty(name, value);
        if (!mIsRunning && !isCalledByLegacyHelper()) {
            cacheCommand(new SetSystemPropertyCommand(systemProperty));
            return getThis();
        }
        return setSystemProperty(systemProperty);
    }

    private T setSystemProperty(FlagOrSystemProperty systemProperty) {
        mLog.d("Setting system property: %s", systemProperty);
        mSystemProperties.set(systemProperty.name, systemProperty.value);
        return getThis();
    }

    private void resetSystemProperties(String testName) {
        mLog.d("Resetting SystemProperties after %s", testName);
        mSystemProperties.reset();
    }

    private T runOrCache(String description, Runnable r) {
        RunnableCommand command = new RunnableCommand(description, r);
        if (!mIsRunning && !isCalledByLegacyHelper()) {
            cacheCommand(command);
            return getThis();
        }
        command.execute();
        return getThis();
    }

    private void cacheCommand(Command command) {
        if (mIsRunning) {
            throw new IllegalStateException(
                    "Cannot cache " + command + " as test is already running");
        }
        mLog.v("Caching %s as test is not running yet", command);
        mInitialCommands.add(command);
    }

    private void runCommand(String description, Runnable runnable) {
        mLog.v("Running runnable for %s", description);
        runnable.run();
    }

    // TODO(b/294423183): make private once not used by subclass for legacy methods
    protected void runInitialCommands(String testName) {
        if (mInitialCommands.isEmpty()) {
            mLog.d("Not executing any command before %s", testName);
        } else {
            int size = mInitialCommands.size();
            mLog.d("Executing %d commands before %s", size, testName);
            for (int i = 0; i < mInitialCommands.size(); i++) {
                Command command = mInitialCommands.get(i);
                mLog.v("\t%d: %s", i, command);
                command.execute();
            }
        }
    }

    private void runSafely(List<Throwable> errors, Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            mLog.e(e, "runSafely() failed");
            errors.add(e);
        }
    }

    // Single SetFlagEnabled annotations present
    private void setAnnotatedFlag(SetFlagEnabled annotation) {
        setFlag(annotation.name(), true);
    }

    // Multiple SetFlagEnabled annotations present
    private void setAnnotatedFlag(SetFlagsEnabled repeatedAnnotation) {
        for (SetFlagEnabled annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetFlagDisabled annotations present
    private void setAnnotatedFlag(SetFlagDisabled annotation) {
        setFlag(annotation.name(), false);
    }

    // Multiple SetFlagDisabled annotations present
    private void setAnnotatedFlag(SetFlagsDisabled repeatedAnnotation) {
        for (SetFlagDisabled annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetIntegerFlag annotations present
    private void setAnnotatedFlag(SetIntegerFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetIntegerFlag annotations present
    private void setAnnotatedFlag(SetIntegerFlags repeatedAnnotation) {
        for (SetIntegerFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetLongFlag annotations present
    private void setAnnotatedFlag(SetLongFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetLongFlag annotations present
    private void setAnnotatedFlag(SetLongFlags repeatedAnnotation) {
        for (SetLongFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetLongFlag annotations present
    private void setAnnotatedFlag(SetFloatFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetLongFlag annotations present
    private void setAnnotatedFlag(SetFloatFlags repeatedAnnotation) {
        for (SetFloatFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetDoubleFlag annotations present
    private void setAnnotatedFlag(SetDoubleFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetDoubleFlag annotations present
    private void setAnnotatedFlag(SetDoubleFlags repeatedAnnotation) {
        for (SetDoubleFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetStringFlag annotations present
    private void setAnnotatedFlag(SetStringFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetStringFlag annotations present
    private void setAnnotatedFlag(SetStringFlags repeatedAnnotation) {
        for (SetStringFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Helper to get a reference to this object, taking care of the generic casting.
    @SuppressWarnings("unchecked")
    private T getThis() {
        return (T) this;
    }

    @SuppressWarnings("serial")
    public static final class TestFailure extends Exception {

        private final String mDump;

        TestFailure(Throwable cause, StringBuilder dump) {
            super(
                    "Test failed (see flags / system properties below the stack trace)",
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

        // toString() is overridden to remove the AbstractAdServicesFlagsSetterRule$ from the name
        @SuppressWarnings("OverrideThrowableToString")
        @Override
        public String toString() {
            return getClass().getSimpleName() + ": " + getMessage();
        }

        /** Gets the flags / system properties state. */
        public String getFlagsState() {
            return mDump;
        }
    }

    private static final class FlagOrSystemProperty {
        public final String name;
        public final String value;
        public final @Nullable String separator;

        FlagOrSystemProperty(String name, String value, @Nullable String separator) {
            this.name = name;
            this.value = value;
            this.separator = separator;
        }

        FlagOrSystemProperty(String name, String value) {
            this(name, value, /* separator= */ null);
        }

        // TODO(b/294423183): need to add unit test for equals() / hashcode() as they don't use
        // separator

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            FlagOrSystemProperty other = (FlagOrSystemProperty) obj;
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
    }

    @SuppressWarnings("ClassCanBeStatic") // Subclasses reference enclosing class
    private abstract class Command {
        protected final String mDescription;

        Command(String description) {
            mDescription = description;
        }

        abstract void execute();

        @Override
        public final String toString() {
            return mDescription;
        }
    }

    private final class RunnableCommand extends Command {
        private final Runnable mRunnable;

        RunnableCommand(String description, Runnable runnable) {
            super(description);
            mRunnable = runnable;
        }

        @Override
        void execute() {
            runCommand(mDescription, mRunnable);
        }
    }

    private abstract class SetFlagOrSystemPropertyCommand extends Command {
        protected final FlagOrSystemProperty mFlagOrSystemProperty;

        SetFlagOrSystemPropertyCommand(
                String description, FlagOrSystemProperty flagOrSystemProperty) {
            super(description + "(" + flagOrSystemProperty + ")");
            mFlagOrSystemProperty = flagOrSystemProperty;
        }
    }

    private final class SetFlagCommand extends SetFlagOrSystemPropertyCommand {
        SetFlagCommand(FlagOrSystemProperty flag) {
            super("SetFlag", flag);
        }

        @Override
        void execute() {
            setFlag(mFlagOrSystemProperty);
        }
    }

    private final class SetSystemPropertyCommand extends SetFlagOrSystemPropertyCommand {
        SetSystemPropertyCommand(FlagOrSystemProperty flag) {
            super("SetSystemProperty", flag);
        }

        @Override
        void execute() {
            setSystemProperty(mFlagOrSystemProperty);
        }
    }
}
