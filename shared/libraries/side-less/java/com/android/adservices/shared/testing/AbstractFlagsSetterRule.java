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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.concurrency.SyncCallback.LOG_TAG;

import com.android.adservices.shared.testing.DeviceConfigHelper.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.NameValuePair.Matcher;
import com.android.adservices.shared.testing.annotations.DisableDebugFlag;
import com.android.adservices.shared.testing.annotations.DisableDebugFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.EnableDebugFlags;
import com.android.adservices.shared.testing.annotations.SetDoubleFlag;
import com.android.adservices.shared.testing.annotations.SetDoubleFlags;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetFlagsDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagsEnabled;
import com.android.adservices.shared.testing.annotations.SetFlagsFalse;
import com.android.adservices.shared.testing.annotations.SetFlagsTrue;
import com.android.adservices.shared.testing.annotations.SetFloatFlag;
import com.android.adservices.shared.testing.annotations.SetFloatFlags;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetIntegerFlags;
import com.android.adservices.shared.testing.annotations.SetLogcatTag;
import com.android.adservices.shared.testing.annotations.SetLogcatTags;
import com.android.adservices.shared.testing.annotations.SetLongDebugFlag;
import com.android.adservices.shared.testing.annotations.SetLongDebugFlags;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlags;
import com.android.adservices.shared.testing.annotations.SetStringArrayFlag;
import com.android.adservices.shared.testing.annotations.SetStringArrayFlags;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.shared.testing.annotations.SetStringFlags;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// TODO(b/294423183): add unit tests for the most relevant / less repetitive stuff (don't need to
// test all setters / getters, for example)
/**
 * Rule used to properly set "Android flags"- it will take care of permissions, restoring values at
 * the end, setting {@link android.provider.DeviceConfig} or {@link android.os.SystemProperties},
 * etc...
 */
public abstract class AbstractFlagsSetterRule<T extends AbstractFlagsSetterRule<T>>
        extends AbstractRethrowerRule {

    protected static final String SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX = "log.tag.";

    private final String mDeviceConfigNamespace;
    private final DeviceConfigHelper mDeviceConfig;
    // TODO(b/338067482): move system properties to its own rule?
    // Prefix used on SystemProperties used for DebugFlags
    private final String mDebugFlagPrefix;
    private final SystemPropertiesHelper mSystemProperties;

    // Cache methods that were called before the test started, so the rule can be
    // instantiated using a builder-like approach.
    private final List<Command> mInitialCommands = new ArrayList<>();

    // Name of flags that were changed by the test
    private final Set<String> mChangedFlags = new LinkedHashSet<>();
    // Name of system properties that were changed by the test
    private final Set<String> mChangedSystemProperties = new LinkedHashSet<>();
    private final Matcher mSystemPropertiesMatcher;

    private final List<NameValuePair> mPreTestFlags = new ArrayList<>();
    private final List<NameValuePair> mPreTestSystemProperties = new ArrayList<>();
    private final List<NameValuePair> mOnTestFailureFlags = new ArrayList<>();
    private final List<NameValuePair> mOnTestFailureSystemProperties = new ArrayList<>();

    private SyncDisabledModeForTest mPreviousSyncDisabledModeForTest = null;

    private boolean mIsRunning;
    private boolean mFlagsClearedByTest;

    protected AbstractFlagsSetterRule(
            RealLogger logger,
            String deviceConfigNamespace,
            String debugFlagPrefix,
            DeviceConfigHelper.InterfaceFactory deviceConfigInterfaceFactory,
            SystemPropertiesHelper.Interface systemPropertiesInterface) {
        this(
                logger,
                deviceConfigNamespace,
                debugFlagPrefix,
                // TODO(b/294423183, 328682831): should not be necessary, but integrated with
                // setLogcatTag()
                (prop) ->
                        prop.name.startsWith(debugFlagPrefix)
                                || prop.name.startsWith(SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX),
                deviceConfigInterfaceFactory,
                systemPropertiesInterface);
    }

    protected AbstractFlagsSetterRule(
            RealLogger logger,
            String deviceConfigNamespace,
            String debugFlagPrefix,
            Matcher systemPropertiesMatcher,
            DeviceConfigHelper.InterfaceFactory deviceConfigInterfaceFactory,
            SystemPropertiesHelper.Interface systemPropertiesInterface) {

        super(logger);

        mDeviceConfigNamespace = Objects.requireNonNull(deviceConfigNamespace);
        mDebugFlagPrefix = Objects.requireNonNull(debugFlagPrefix);
        mSystemPropertiesMatcher = Objects.requireNonNull(systemPropertiesMatcher);
        mDeviceConfig =
                new DeviceConfigHelper(deviceConfigInterfaceFactory, deviceConfigNamespace, logger);
        mSystemProperties = new SystemPropertiesHelper(systemPropertiesInterface, logger);
        storeSyncDisabledMode();
        // Must set right away to avoid race conditions (for example, backend setting flags before
        // apply() is called)
        setSyncDisabledMode(SyncDisabledModeForTest.PERSISTENT);

        mLog.v(
                "Constructor: mDeviceConfigNamespace=%s,"
                        + " mDebugFlagPrefix=%s,mDeviceConfig=%s, mSystemProperties=%s",
                mDeviceConfigNamespace, mDebugFlagPrefix, mDeviceConfig, mSystemProperties);
    }

    @Override
    protected void preTest(Statement base, Description description, List<Throwable> cleanUpErrors) {
        String testName = TestHelper.getTestName(description);
        mIsRunning = true;

        // TODO(b/294423183): ideally should be "setupErrors", but it's not used yet (other
        // than logging), so it doesn't matter
        runSafely(cleanUpErrors, () -> mPreTestFlags.addAll(mDeviceConfig.getAll()));
        // Log flags set on the device prior to test execution. Useful for verifying if flag state
        // is correct for flag-ramp / AOAO testing.
        log(mPreTestFlags, "pre-test flags");
        runSafely(
                cleanUpErrors,
                () ->
                        mPreTestSystemProperties.addAll(
                                mSystemProperties.getAll(mSystemPropertiesMatcher)));

        setAnnotatedFlags(description);
        runInitialCommands(testName);
    }

    @Override
    protected void onTestFailure(
            Statement base,
            Description description,
            List<Throwable> cleanUpErrors,
            Throwable testFailure) {
        runSafely(cleanUpErrors, () -> mOnTestFailureFlags.addAll(mDeviceConfig.getAll()));
        runSafely(
                cleanUpErrors,
                () ->
                        mOnTestFailureSystemProperties.addAll(
                                mSystemProperties.getAll(mSystemPropertiesMatcher)));
    }

    @Override
    protected void postTest(
            Statement base, Description description, List<Throwable> cleanUpErrors) {
        String testName = TestHelper.getTestName(description);
        runSafely(cleanUpErrors, () -> resetFlags(testName));
        runSafely(cleanUpErrors, () -> resetSystemProperties(testName));
        restoreSyncDisabledMode(cleanUpErrors);
        mIsRunning = false;
    }

    private void restoreSyncDisabledMode(List<Throwable> cleanUpErrors) {
        if (mPreviousSyncDisabledModeForTest != null) {
            mLog.v(
                    "mPreviousSyncDisabledModeForTest=%s; restoring flag sync mode",
                    mPreviousSyncDisabledModeForTest);
            runSafely(cleanUpErrors, () -> setSyncDisabledMode(mPreviousSyncDisabledModeForTest));
        } else {
            mLog.v("mPreviousSyncDisabledModeForTest=null; not restoring flag sync mode");
        }
    }

    private void setSyncDisabledMode(SyncDisabledModeForTest mode) {
        runOrCache(
                "setSyncDisabledMode(" + mode + ")", () -> mDeviceConfig.setSyncDisabledMode(mode));
    }

    private void storeSyncDisabledMode() {
        runOrCache(
                "storeSyncDisabledMode()",
                () -> mPreviousSyncDisabledModeForTest = mDeviceConfig.getSyncDisabledMode());
    }

    @Override
    protected String decorateTestFailureMessage(StringBuilder dump, List<Throwable> cleanUpErrors) {
        if (mFlagsClearedByTest) {
            dump.append("NOTE: test explicitly cleared all flags.\n");
        }

        logAllAndDumpDiff("flags", dump, mChangedFlags, mPreTestFlags, mOnTestFailureFlags);
        logAllAndDumpDiff(
                "system properties",
                dump,
                mChangedSystemProperties,
                mPreTestSystemProperties,
                mOnTestFailureSystemProperties);
        return "flags / system properties state";
    }

    private void logAllAndDumpDiff(
            String what,
            StringBuilder dump,
            Set<String> changedNames,
            List<NameValuePair> preTest,
            List<NameValuePair> postTest) {
        // Log all values
        log(preTest, "%s before the test", what);
        log(postTest, "%s after the test", what);

        // Dump only what was change
        appendChanges(dump, what, changedNames, preTest, postTest);
    }

    private void appendChanges(
            StringBuilder dump,
            String what,
            Set<String> changedNames,
            List<NameValuePair> preTest,
            List<NameValuePair> postTest) {
        if (changedNames.isEmpty()) {
            dump.append("Tested didn't change any ").append(what).append('\n');
            return;
        }
        dump.append("Test changed ")
                .append(changedNames.size())
                .append(' ')
                .append(what)
                .append(" (see log for all changes): \n");

        for (String name : changedNames) {
            String before = getValue("before", preTest, name);
            String after = getValue("after", postTest, name);
            dump.append('\t')
                    .append(name)
                    .append(": ")
                    .append(before)
                    .append(", ")
                    .append(after)
                    .append('\n');
        }
    }

    private String getValue(String when, List<NameValuePair> list, String name) {
        for (NameValuePair candidate : list) {
            if (candidate.name.equals(name)) {
                return when + "=" + candidate.value;
            }
        }
        return "(not set " + when + ")";
    }

    /**
     * Dumps all flags using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code dumpFlags("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public final void dumpFlags(@FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        log(mDeviceConfig.getAll(), "flags (Reason: %s)", String.format(reasonFmt, reasonArgs));
    }

    /**
     * Dumps all system properties using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code
     * dumpSystemProperties("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public final void dumpSystemProperties(
            @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        log(
                mSystemProperties.getAll(mSystemPropertiesMatcher),
                "system properties (Reason: %s)",
                String.format(reasonFmt, reasonArgs));
    }

    @FormatMethod
    private void log(
            List<NameValuePair> values,
            @FormatString String whatFmt,
            @Nullable Object... whatArgs) {
        String what = String.format(whatFmt, whatArgs);
        if (values.isEmpty()) {
            mLog.e("%s: empty", what);
            return;
        }
        mLog.i("Logging name/value of %d %s:", values.size(), what);
        values.forEach(value -> mLog.i("\t%s", value));
    }

    /** Clears all flags from the namespace */
    public final T clearFlags() {
        return runOrCache(
                "clearFlags()",
                () -> {
                    mLog.i("Clearing all flags. mIsRunning=%b", mIsRunning);
                    mDeviceConfig.clearFlags();
                    // TODO(b/294423183): ideally we should save the flags and restore - possibly
                    // using DeviceConfig properties - but for now let's just clear it.
                    mFlagsClearedByTest = true;
                });
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, boolean value) {
        return setOrCacheFlag(name, Boolean.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, int value) {
        return setOrCacheFlag(name, Integer.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, long value) {
        return setOrCacheFlag(name, Long.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, float value) {
        return setOrCacheFlag(name, Float.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, double value) {
        return setOrCacheFlag(name, Double.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, String value) {
        return setOrCacheFlag(name, value);
    }

    // TODO(b/303901926): add unit test
    /**
     * Sets the string array flag with the given value , using the {@code separator} to flatten it.
     *
     * <p><b>Note:</b> in most cases, it's clearer to use the {@link SetLogcatTag} annotation
     * instead.
     */
    public final T setFlag(String name, String[] value, String separator) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("no values (name=" + name + ")");
        }
        if (value.length == 1) {
            return setFlag(name, value[0]);
        }

        // TODO(b/303901926): use some existing helper / utility to flatten it - or a stream like
        // list.stream().map(Object::toString).collect(Collectors.joining(delimiter) - once it's
        // unit tested
        StringBuilder flattenedValue = new StringBuilder().append(value[0]);
        for (int i = 1; i < value.length; i++) {
            String nextValue = value[i];
            if (i < value.length) {
                flattenedValue.append(separator);
            }
            flattenedValue.append(nextValue);
        }
        return setFlag(new NameValuePair(name, flattenedValue.toString(), separator));
    }

    /**
     * Sets a {@code logcat} tag.
     *
     * <p><b>Note: </b> it's clearer to use the {@link SetLogcatTag} annotation instead.
     */
    public final T setLogcatTag(String tag, LogLevel level) {
        setOrCacheLogtagSystemProperty(tag, level.name());
        return getThis();
    }

    // TODO(b/331781012): add all of them
    // TODO(b/331781012): create @SetInfraLogcatTags as well
    /** Sets the {@code logcat} tags for the (shared) infra classes. */
    public final T setInfraLogcatTags() {
        // TODO(b/331781012): create a String[] constants somewhere and iterate over it
        setLogcatTag(LOG_TAG, LogLevel.VERBOSE);
        return getThis();
    }

    /** Gets the value of the given flag. */
    @Nullable
    public final String getFlag(String flag) {
        return mDeviceConfig.get(flag);
    }

    // TODO(295007931): abstract SDK-related methods in a new SdkLevelHelper and reuse them on
    // SdkLevelSupportRule
    /** Gets the device's SDK level. */
    protected abstract int getDeviceSdk();

    protected boolean isAtLeastR() {
        return getDeviceSdk() >= 30;
    }

    protected boolean isAtLeastS() {
        return getDeviceSdk() >= 31;
    }

    protected boolean isAtLeastT() {
        return getDeviceSdk() > 32;
    }

    // Helper to get a reference to this object, taking care of the generic casting.
    @SuppressWarnings("unchecked")
    protected final T getThis() {
        return (T) this;
    }

    // Set the annotated flags with the specified value for a particular test method.
    // NOTE: when adding an annotation here, you also need to add it on isFlagAnnotationPresent()
    private void setAnnotatedFlags(Description description) {
        List<Annotation> annotations = getAllFlagAnnotations(description);

        // Apply the annotations in the reverse order. First apply from the super classes, test
        // class and then test method. If same annotated flag is present in class and test
        // method, test method takes higher priority.
        // NOTE: add annotations sorted by "most likely usage" and "groups"
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation annotation = annotations.get(i);

            // Boolean
            if (annotation instanceof SetFlagEnabled) {
                setAnnotatedFlag((SetFlagEnabled) annotation);
            } else if (annotation instanceof SetFlagsEnabled) {
                setAnnotatedFlag((SetFlagsEnabled) annotation);
            } else if (annotation instanceof SetFlagDisabled) {
                setAnnotatedFlag((SetFlagDisabled) annotation);
            } else if (annotation instanceof SetFlagsDisabled) {
                setAnnotatedFlag((SetFlagsDisabled) annotation);
            } else if (annotation instanceof SetFlagTrue) {
                setAnnotatedFlag((SetFlagTrue) annotation);
            } else if (annotation instanceof SetFlagsTrue) {
                setAnnotatedFlag((SetFlagsTrue) annotation);
            } else if (annotation instanceof SetFlagFalse) {
                setAnnotatedFlag((SetFlagFalse) annotation);
            } else if (annotation instanceof SetFlagsFalse) {
                setAnnotatedFlag((SetFlagsFalse) annotation);

                // Numbers
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

                // String
            } else if (annotation instanceof SetStringFlag) {
                setAnnotatedFlag((SetStringFlag) annotation);
            } else if (annotation instanceof SetStringFlags) {
                setAnnotatedFlag((SetStringFlags) annotation);
            } else if (annotation instanceof SetStringArrayFlag) {
                setAnnotatedFlag((SetStringArrayFlag) annotation);
            } else if (annotation instanceof SetStringArrayFlags) {
                setAnnotatedFlag((SetStringArrayFlags) annotation);

                // Debug flags
            } else if (annotation instanceof EnableDebugFlag) {
                setAnnotatedFlag((EnableDebugFlag) annotation);
            } else if (annotation instanceof EnableDebugFlags) {
                setAnnotatedFlag((EnableDebugFlags) annotation);
            } else if (annotation instanceof DisableDebugFlag) {
                setAnnotatedFlag((DisableDebugFlag) annotation);
            } else if (annotation instanceof DisableDebugFlags) {
                setAnnotatedFlag((DisableDebugFlags) annotation);
            } else if (annotation instanceof SetLongDebugFlag) {
                setAnnotatedFlag((SetLongDebugFlag) annotation);
            } else if (annotation instanceof SetLongDebugFlags) {
                setAnnotatedFlag((SetLongDebugFlags) annotation);

                // Logcat flags
            } else if (annotation instanceof SetLogcatTag) {
                setAnnotatedFlag((SetLogcatTag) annotation);
            } else if (annotation instanceof SetLogcatTags) {
                setAnnotatedFlag((SetLogcatTags) annotation);
            } else {
                processAnnotation(description, annotation);
            }
        }
    }

    private T setOrCacheFlag(String name, String value) {
        return setOrCacheFlag(name, value, /* separator= */ null);
    }

    // TODO(b/294423183): need to add unit test for setters that call this
    protected final T setOrCacheFlag(String name, String value, @Nullable String separator) {
        NameValuePair flag = new NameValuePair(name, value, separator);
        if (!mIsRunning) {
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

    private T setFlag(NameValuePair flag) {
        mLog.d("Setting flag: %s", flag);
        if (flag.separator == null) {
            mDeviceConfig.set(flag.name, flag.value);
        } else {
            mDeviceConfig.setWithSeparator(flag.name, flag.value, flag.separator);
        }
        mChangedFlags.add(flag.name);
        return getThis();
    }

    private void resetFlags(String testName) {
        mLog.d("Resetting flags after %s", testName);
        mDeviceConfig.reset();
    }

    /** Sets the value of the given {@link com.android.adservices.service.DebugFlag}. */
    public final T setDebugFlag(String name, boolean value) {
        return setDebugFlag(name, Boolean.toString(value));
    }

    /** Sets the value of the given {@link com.android.adservices.service.DebugFlag}. */
    public final T setDebugFlag(String name, int value) {
        return setDebugFlag(name, Integer.toString(value));
    }

    private T setOrCacheLogtagSystemProperty(String name, String value) {
        return setOrCacheSystemProperty(SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX + name, value);
    }

    private T setDebugFlag(String name, String value) {
        return setOrCacheSystemProperty(mDebugFlagPrefix + name, value);
    }

    private T setOrCacheSystemProperty(String name, String value) {
        NameValuePair systemProperty = new NameValuePair(name, value);
        if (!mIsRunning) {
            cacheCommand(new SetSystemPropertyCommand(systemProperty));
            return getThis();
        }
        return setSystemProperty(systemProperty);
    }

    private T setSystemProperty(NameValuePair systemProperty) {
        mLog.d("Setting system property: %s", systemProperty);
        mSystemProperties.set(systemProperty.name, systemProperty.value);
        mChangedSystemProperties.add(systemProperty.name);
        return getThis();
    }

    private void resetSystemProperties(String testName) {
        mLog.d("Resetting SystemProperties after %s", testName);
        mSystemProperties.reset();
    }

    protected T runOrCache(String description, Runnable r) {
        RunnableCommand command = new RunnableCommand(description, r);
        if (!mIsRunning) {
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
    protected final void runInitialCommands(String testName) {
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

    // TODO(b/294423183): improve logic used here and on setAnnotatedFlags()
    // NOTE: when adding an annotation here, you also need to add it on setAnnotatedFlags()
    private boolean isFlagAnnotationPresent(Annotation annotation) {
        // NOTE: add annotations sorted by "most likely usage" and "groups"
        boolean processedHere =
                // Boolean
                (annotation instanceof SetFlagEnabled)
                        || (annotation instanceof SetFlagsEnabled)
                        || (annotation instanceof SetFlagDisabled)
                        || (annotation instanceof SetFlagsDisabled)
                        || (annotation instanceof SetFlagTrue)
                        || (annotation instanceof SetFlagsTrue)
                        || (annotation instanceof SetFlagFalse)
                        || (annotation instanceof SetFlagsFalse)
                        // Numbers
                        || (annotation instanceof SetIntegerFlag)
                        || (annotation instanceof SetIntegerFlags)
                        || (annotation instanceof SetLongFlag)
                        || (annotation instanceof SetLongFlags)
                        || (annotation instanceof SetFloatFlag)
                        || (annotation instanceof SetFloatFlags)
                        || (annotation instanceof SetDoubleFlag)
                        || (annotation instanceof SetDoubleFlags)
                        // Strings
                        || (annotation instanceof SetStringFlag)
                        || (annotation instanceof SetStringFlags)
                        || (annotation instanceof SetStringArrayFlag)
                        || (annotation instanceof SetStringArrayFlags)
                        // Debug flags
                        || (annotation instanceof DisableDebugFlag)
                        || (annotation instanceof DisableDebugFlags)
                        || (annotation instanceof EnableDebugFlag)
                        || (annotation instanceof EnableDebugFlags)
                        || (annotation instanceof SetLongDebugFlag)
                        || (annotation instanceof SetLongDebugFlags)
                        // Logcat flags
                        || (annotation instanceof SetLogcatTag)
                        || (annotation instanceof SetLogcatTags);
        return processedHere || isAnnotationSupported(annotation);
    }

    /**
     * By default returns {@code false}, but subclasses can override to support custom annotations.
     *
     * <p>Note: when overridden, {@link #processAnnotation(Description, Annotation)} should be
     * overridden as well.
     */
    protected boolean isAnnotationSupported(Annotation annotation) {
        return false;
    }

    /**
     * Called to process custom annotations present in the test (when {@link
     * #isAnnotationSupported(Annotation)} returns {@code true} for that annotation type).
     */
    protected void processAnnotation(Description description, Annotation annotation) {
        throw new IllegalStateException(
                "Rule subclass ("
                        + this.getClass().getName()
                        + ") supports annotation "
                        + annotation.annotationType().getName()
                        + ", but doesn't override processAnnotation(), which was called with "
                        + annotation);
    }

    private List<Annotation> getAllFlagAnnotations(Description description) {
        // TODO(b/318893752): Move this to a helper function to scan test method, class and
        //  superclasses for annotations.
        List<Annotation> result = new ArrayList<>();
        for (Annotation testMethodAnnotation : description.getAnnotations()) {
            if (isFlagAnnotationPresent(testMethodAnnotation)) {
                result.add(testMethodAnnotation);
            } else {
                mLog.v("Ignoring annotation %s", testMethodAnnotation);
            }
        }

        // Get all the flag based annotations from test class and super classes
        Class<?> clazz = description.getTestClass();
        do {
            addFlagAnnotations(result, clazz);
            for (Class<?> classInterface : clazz.getInterfaces()) {
                // TODO(b/340882758): add unit test for this as well. Also, unit test need to make
                // sure class prevails - for example, if interface has SetFlag(x, true) and test
                // have SetFlag(x, false), the interface annotation should be applied before the
                // class one.
                addFlagAnnotations(result, classInterface);
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return result;
    }

    private void addFlagAnnotations(List<Annotation> annotations, Class<?> clazz) {
        Annotation[] classAnnotations = clazz.getAnnotations();
        if (classAnnotations == null) {
            return;
        }
        for (Annotation annotation : classAnnotations) {
            if (isFlagAnnotationPresent(annotation)) {
                annotations.add(annotation);
            }
        }
    }

    // Single SetFlagEnabled annotations present
    private void setAnnotatedFlag(SetFlagEnabled annotation) {
        setFlag(annotation.value(), true);
    }

    // Multiple SetFlagEnabled annotations present
    private void setAnnotatedFlag(SetFlagsEnabled repeatedAnnotation) {
        for (SetFlagEnabled annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetFlagDisabled annotations present
    private void setAnnotatedFlag(SetFlagDisabled annotation) {
        setFlag(annotation.value(), false);
    }

    // Multiple SetFlagDisabled annotations present
    private void setAnnotatedFlag(SetFlagsDisabled repeatedAnnotation) {
        for (SetFlagDisabled annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetFlagTrue annotations present
    private void setAnnotatedFlag(SetFlagTrue annotation) {
        setFlag(annotation.value(), true);
    }

    // Multiple SetFlagTrue annotations present
    private void setAnnotatedFlag(SetFlagsTrue repeatedAnnotation) {
        for (SetFlagTrue annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetFlagFalse annotations present
    private void setAnnotatedFlag(SetFlagFalse annotation) {
        setFlag(annotation.value(), false);
    }

    // Multiple SetFlagFalse annotations present
    private void setAnnotatedFlag(SetFlagsFalse repeatedAnnotation) {
        for (SetFlagFalse annotation : repeatedAnnotation.value()) {
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

    // Single SetStringArrayFlag annotations present
    private void setAnnotatedFlag(SetStringArrayFlag annotation) {
        setFlag(annotation.name(), annotation.value(), annotation.separator());
    }

    // Multiple SetStringArrayFlag annotations present
    private void setAnnotatedFlag(SetStringArrayFlags repeatedAnnotation) {
        for (SetStringArrayFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single EnableDebugFlag annotations present
    private void setAnnotatedFlag(EnableDebugFlag annotation) {
        setDebugFlag(annotation.value(), true);
    }

    // Multiple EnableDebugFlag annotations present
    private void setAnnotatedFlag(EnableDebugFlags repeatedAnnotation) {
        for (EnableDebugFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single DisableDebugFlag annotations present
    private void setAnnotatedFlag(DisableDebugFlag annotation) {
        setDebugFlag(annotation.value(), false);
    }

    // Multiple DisableDebugFlag annotations present
    private void setAnnotatedFlag(DisableDebugFlags repeatedAnnotation) {
        for (DisableDebugFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetLongDebugFlag annotations present
    private void setAnnotatedFlag(SetLongDebugFlag annotation) {
        setDebugFlag(annotation.name(), Long.toString(annotation.value()));
    }

    // Multiple SetLongDebugFlag annotations present
    private void setAnnotatedFlag(SetLongDebugFlags repeatedAnnotation) {
        for (SetLongDebugFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetLogcatTag annotations present
    private void setAnnotatedFlag(SetLogcatTag annotation) {
        setLogcatTag(annotation.tag(), annotation.level());
    }

    // Multiple SetLogcatTag annotations present
    private void setAnnotatedFlag(SetLogcatTags repeatedAnnotation) {
        for (SetLogcatTag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
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
        protected final NameValuePair mFlagOrSystemProperty;

        SetFlagOrSystemPropertyCommand(String description, NameValuePair flagOrSystemProperty) {
            super(description + "(" + flagOrSystemProperty + ")");
            mFlagOrSystemProperty = flagOrSystemProperty;
        }
    }

    private final class SetFlagCommand extends SetFlagOrSystemPropertyCommand {
        SetFlagCommand(NameValuePair flag) {
            super("SetFlag", flag);
        }

        @Override
        void execute() {
            setFlag(mFlagOrSystemProperty);
        }
    }

    private final class SetSystemPropertyCommand extends SetFlagOrSystemPropertyCommand {
        SetSystemPropertyCommand(NameValuePair flag) {
            super("SetSystemProperty", flag);
        }

        @Override
        void execute() {
            setSystemProperty(mFlagOrSystemProperty);
        }
    }
}
