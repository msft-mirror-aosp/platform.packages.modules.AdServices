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

import static com.android.adservices.common.AbstractAdServicesSystemPropertiesDumperRule.SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX;
import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.NAMESPACE_ADSERVICES;

import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.common.annotations.SetDefaultLogcatTags;
import com.android.adservices.common.annotations.SetMsmtApiAppAllowList;
import com.android.adservices.common.annotations.SetMsmtWebContextClientAppAllowList;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.AbstractFlagsSetterRule;
import com.android.adservices.shared.testing.DeviceConfigHelper;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePair.Matcher;
import com.android.adservices.shared.testing.SystemPropertiesHelper;

import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.function.Consumer;

// TODO(b/294423183): add unit tests for the most relevant / less repetitive stuff (don't need to
// test all setters / getters, for example)
/**
 * Rule used to properly set AdService flags - it will take care of permissions, restoring values at
 * the end, setting {@link android.provider.DeviceConfig} or {@link android.os.SystemProperties},
 * etc...
 */
////////////////////////////////////////////////////////////////////////////////////////////////////
// NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let your   //
// test call setFlags(flagName) (statically import FlagsConstant.flagName), which will make it    //
// easier to transition the test to an annotated-base approach.                                   //
////////////////////////////////////////////////////////////////////////////////////////////////////
public abstract class AbstractAdServicesFlagsSetterRule<
                T extends AbstractAdServicesFlagsSetterRule<T>>
        extends AbstractFlagsSetterRule<T> {

    // TODO(b/295321663): move these constants (and those from LogFactory) to AdServicesCommon
    protected static final String LOGCAT_TAG_ADSERVICES = "adservices";
    protected static final String LOGCAT_TAG_ADSERVICES_SERVICE = LOGCAT_TAG_ADSERVICES + "-system";
    protected static final String LOGCAT_TAG_TOPICS = LOGCAT_TAG_ADSERVICES + ".topics";
    protected static final String LOGCAT_TAG_FLEDGE = LOGCAT_TAG_ADSERVICES + ".fledge";
    protected static final String LOGCAT_TAG_KANON = LOGCAT_TAG_ADSERVICES + ".kanon";
    protected static final String LOGCAT_TAG_MEASUREMENT = LOGCAT_TAG_ADSERVICES + ".measurement";
    protected static final String LOGCAT_TAG_UI = LOGCAT_TAG_ADSERVICES + ".ui";
    protected static final String LOGCAT_TAG_ADID = LOGCAT_TAG_ADSERVICES + ".adid";
    protected static final String LOGCAT_TAG_APPSETID = LOGCAT_TAG_ADSERVICES + ".appsetid";
    protected static final String LOGCAT_TAG_SHARED = "adservices-shared";

    // TODO(b/294423183): instead of hardcoding the SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX, we
    // should dynamically calculate it based on setLogcatTag() calls
    private static final Matcher PROPERTIES_PREFIX_MATCHER =
            (prop) ->
                    prop.name.startsWith(SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX)
                            || prop.name.startsWith(
                                    SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX + "adservices");

    private static final boolean USE_TEST_PACKAGE_AS_DEFAULT = true;
    private static final boolean DONT_USE_TEST_PACKAGE_AS_DEFAULT = false;

    protected AbstractAdServicesFlagsSetterRule(
            RealLogger logger,
            DeviceConfigHelper.InterfaceFactory deviceConfigInterfaceFactory,
            SystemPropertiesHelper.Interface systemPropertiesInterface) {
        super(
                logger,
                NAMESPACE_ADSERVICES,
                SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX,
                PROPERTIES_PREFIX_MATCHER,
                deviceConfigInterfaceFactory,
                systemPropertiesInterface);
    }

    // Used for testing purposes only
    protected AbstractAdServicesFlagsSetterRule(
            RealLogger logger, Consumer<NameValuePair> flagsSetter) {
        super(logger, flagsSetter);
    }

    @Override
    protected boolean isAnnotationSupported(Annotation annotation) {
        // NOTE: add annotations sorted by "most likely usage"
        return annotation instanceof DisableGlobalKillSwitch
                || annotation instanceof EnableAllApis
                || annotation instanceof SetCompatModeFlags
                || annotation instanceof SetPpapiAppAllowList
                || annotation instanceof SetDefaultLogcatTags
                || annotation instanceof SetAllLogcatTags
                || annotation instanceof SetMsmtApiAppAllowList
                || annotation instanceof SetMsmtWebContextClientAppAllowList;
    }

    @Override
    protected void processAnnotation(Description description, Annotation annotation) {
        // NOTE: add annotations sorted by "most likely usage"
        if (annotation instanceof DisableGlobalKillSwitch) {
            setGlobalKillSwitch(false);
        } else if (annotation instanceof EnableAllApis) {
            enableAllApis();
        } else if (annotation instanceof SetCompatModeFlags) {
            setCompatModeFlags();
        } else if (annotation instanceof SetPpapiAppAllowList) {
            setPpapiAppAllowList(
                    ((SetPpapiAppAllowList) annotation).value(), USE_TEST_PACKAGE_AS_DEFAULT);
        } else if (annotation instanceof SetDefaultLogcatTags) {
            setDefaultLogcatTags();
        } else if (annotation instanceof SetAllLogcatTags) {
            setAllLogcatTags();
        } else if (annotation instanceof SetMsmtApiAppAllowList) {
            setMsmtApiAppAllowList(
                    ((SetMsmtApiAppAllowList) annotation).value(), USE_TEST_PACKAGE_AS_DEFAULT);
        } else if (annotation instanceof SetMsmtWebContextClientAppAllowList) {
            setMsmtWebContextClientAllowList(
                    ((SetMsmtWebContextClientAppAllowList) annotation).value(),
                    USE_TEST_PACKAGE_AS_DEFAULT);
        } else {
            // should not happen
            throw new IllegalStateException(
                    "INTERNAL ERROR: processAnnotation() called with unsupported annotation: "
                            + annotation);
        }
    }

    /**
     * Gets the package name of the app running this test.
     *
     * <p>Used on annotations that applies to the test app by default (for example, for allowlist).
     */
    protected String getTestPackageName() {
        //
        throw new UnsupportedOperationException(
                "Concrete rule ("
                        + getClass().getSimpleName()
                        + ") cannot infer the name of the test package (typically happens on"
                        + " host-side tests)");
    }

    // Helper methods to set more commonly used flags such as kill switches.
    // Less common flags can be set directly using setFlags methods.

    /**
     * Overrides the flag that sets the global AdServices kill switch.
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link DisableGlobalKillSwitch}
     * in this case), unless the test need to dynamically change the flags after it started.
     */
    public final T setGlobalKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_GLOBAL_KILL_SWITCH, value);
    }

    final T enableAllApis() {
        return setAllLogcatTags()
                .setGlobalKillSwitch(false)
                .setTopicsKillSwitch(false)
                .setFlag(KEY_ADID_KILL_SWITCH, false)
                .setFlag(KEY_MEASUREMENT_KILL_SWITCH, false)
                .setFlag(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, false)
                .setFlag(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH, false)
                .setFlag(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED, true);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getAdServicesEnabled}.
     */
    public final T setAdServicesEnabled(boolean value) {
        return setFlag(FlagsConstants.KEY_ADSERVICES_ENABLED, value);
    }

    /** Overrides the flag that sets the Topics kill switch. */
    public final T setTopicsKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_TOPICS_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics Device Classifier kill switch. */
    public final T setTopicsOnDeviceClassifierKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getEnableBackCompat()}.
     */
    public final T setEnableBackCompat(boolean value) {
        return setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, value);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMeasurementRollbackDeletionAppSearchKillSwitch()}.
     */
    public final T setMeasurementRollbackDeletionAppSearchKillSwitch(boolean value) {
        return setFlag(
                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getPpapiAppAllowList()}.
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link SetPpapiAppAllowList} in
     * this case), unless the test need to dynamically change the flags after it started.
     */
    public final T setPpapiAppAllowList(String... value) {
        return setPpapiAppAllowList(value, DONT_USE_TEST_PACKAGE_AS_DEFAULT);
    }

    private T setPpapiAppAllowList(String[] value, boolean useTestPackageAsDefault) {
        mLog.d(
                "setPpapiAppAllowList(useTestPackageAsDefault=%b): %s",
                useTestPackageAsDefault, Arrays.toString(value));
        return setAllowListFlag(
                FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST, value, useTestPackageAsDefault);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMsmtApiAppAllowList()}.
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link SetMsmtApiAppAllowList} in
     * this case), unless the test need to dynamically change the flags after it started.
     */
    public final T setMsmtApiAppAllowList(String... value) {
        return setMsmtApiAppAllowList(value, DONT_USE_TEST_PACKAGE_AS_DEFAULT);
    }

    private T setMsmtApiAppAllowList(String[] value, boolean useTestPackageAsDefault) {
        mLog.d(
                "setMsmtApiAppAllowList(useTestPackageAsDefault=%b): %s",
                useTestPackageAsDefault, Arrays.toString(value));
        return setAllowListFlag(
                FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST, value, useTestPackageAsDefault);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getWebContextClientAppAllowList()}.
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link
     * SetMsmtWebContextClientAppAllowList} in this case), unless the test need to dynamically
     * change the flags after it started.
     */
    public final T setMsmtWebContextClientAllowList(String... value) {
        return setMsmtWebContextClientAllowList(value, DONT_USE_TEST_PACKAGE_AS_DEFAULT);
    }

    private T setMsmtWebContextClientAllowList(String[] value, boolean useTestPackageAsDefault) {
        mLog.d(
                "setMsmtWebContextClientAllowList(useTestPackageAsDefault=%b): %s",
                useTestPackageAsDefault, Arrays.toString(value));
        return setAllowListFlag(
                FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, value, useTestPackageAsDefault);
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
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link SetCompatModeFlags} in
     * this case), unless the test need to dynamically change the flags after it started.
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
                    setFlag(FlagsConstants.KEY_ENABLE_ADEXT_DATA_SERVICE_DEBUG_PROXY, true);
                });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the common AdServices {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link SetDefaultLogcatTags} in
     * this case), unless the test need to dynamically change the flags after it started.
     */
    public T setDefaultLogcatTags() {
        setInfraLogcatTags();
        setLogcatTag(LOGCAT_TAG_ADSERVICES, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_SHARED, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_ADSERVICES_SERVICE, LogLevel.VERBOSE);
        return getThis();
    }

    /**
     * Sets all AdServices {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     *
     * <p>NOTE: it's usually cleaner to use an annotation instead ({@link SetAllLogcatTags} in this
     * case), unless the test need to dynamically change the flags after it started.
     */
    public T setAllLogcatTags() {
        setDefaultLogcatTags();
        setLogcatTag(LOGCAT_TAG_TOPICS, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_FLEDGE, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_MEASUREMENT, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_ADID, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_APPSETID, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_UI, LogLevel.VERBOSE);
        setLogcatTag(LOGCAT_TAG_KANON, LogLevel.VERBOSE);
        return getThis();
    }

    /**
     * Sets Measurement {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     */
    public T setMeasurementTags() {
        setLogcatTag(LOGCAT_TAG_MEASUREMENT, LogLevel.VERBOSE);
        return getThis();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private T setAllowListFlag(String name, String[] values, boolean useTestPackageAsDefault) {
        if (values.length == 0 && useTestPackageAsDefault) {
            String testPkg = getTestPackageName();
            mLog.d(
                    "setAllowListUsingTestAppAsDefault(%s): package not set by annotation, using"
                            + " test package name %s",
                    name, testPkg);
            values = new String[] {testPkg};
        }
        return setFlag(name, values);
    }
}
