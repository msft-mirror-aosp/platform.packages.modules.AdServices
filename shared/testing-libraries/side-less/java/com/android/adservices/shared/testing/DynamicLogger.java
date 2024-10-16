/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

/**
 * {@link RealLogger} implementation that uses reflection to delegate to the proper logger.
 *
 * <p>It's used on side-less tests that run on device side (as currently we don't have a project to
 * run those test in the host).
 */
public final class DynamicLogger implements RealLogger {

    private static final String TAG = DynamicLogger.class.getSimpleName();

    private static final RealLogger sFallbackLogger = StandardStreamsLogger.getInstance();
    private static final DynamicLogger sInstance = new DynamicLogger();

    private final RealLogger mRealLogger;
    private final String mLoggerClassName;

    public static DynamicLogger getInstance() {
        return sInstance;
    }

    private DynamicLogger() {
        RealLogger realLogger = null;
        String loggerClass = HostSideRealLogger.DELEGATE_CLASS;

        // Ideally we should check for device-side first as that's the most common case, but it's
        // possible that host-side tests included the Android SDK in their classpath somehow,
        // while device-side are less likely to include tradefed
        Class<?> hostSideClass = getClass(loggerClass);
        if (hostSideClass != null) {
            realLogger = new HostSideRealLogger(hostSideClass);
        } else {
            loggerClass = DeviceSideRealLogger.DELEGATE_CLASS;
            Class<?> deviceSideClass = getClass(loggerClass);
            if (deviceSideClass != null) {
                realLogger = new DeviceSideRealLogger(deviceSideClass);
            }
        }
        if (realLogger == null) {
            // Worst-case scenario, fall back to standard streams...
            realLogger = sFallbackLogger;
            loggerClass = "System.out / System.err";
        }
        mRealLogger = realLogger;
        mLoggerClassName = loggerClass;
        mRealLogger.log(LogLevel.INFO, TAG, "delegating to %s", loggerClass);
    }

    @Override
    @FormatMethod
    public void log(
            LogLevel level, String tag, final @FormatString String msgFmt, Object... msgArgs) {
        mRealLogger.log(level, tag, msgFmt, msgArgs);
    }

    @Override
    @FormatMethod
    public void log(
            LogLevel level,
            String tag,
            Throwable throwable,
            final @FormatString String msgFmt,
            Object... msgArgs) {
        mRealLogger.log(level, tag, throwable, msgFmt, msgArgs);
    }

    @Override
    public String toString() {
        return DynamicLogger.class.getSimpleName() + "[" + mLoggerClassName + "]";
    }

    @Nullable
    private static Class<?> getClass(String name) {
        try {
            return Class.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (Exception e) {
            sFallbackLogger.log(
                    LogLevel.WTF,
                    TAG,
                    e,
                    "Failed to get method %s.%s(%s)",
                    clazz.getSimpleName(),
                    name,
                    Arrays.toString(parameterTypes));
            return null;
        }
    }

    /**
     * Calls the given logging method, falling back to StandardStreamsLogger if it's {@code null}.
     */
    private static void call(LogLevel level, String tag, @Nullable Method method, Object... args) {
        if (method == null) {
            // Method could not be inferred, so fall back to StandardStreamsLogger
            sFallbackLogger.log(level, tag, "%s", args);
            return;
        }
        try {
            method.invoke(null, args);
        } catch (Exception e) {
            sFallbackLogger.log(
                    LogLevel.WTF, TAG, e, "Failed to call %s(%s)", method, Arrays.toString(args));
        }
    }

    /**
     * Calls the given logging method, falling back to StandardStreamsLogger if it's {@code null}.
     */
    private static void call(
            LogLevel level,
            String tag,
            Throwable throwable,
            @Nullable Method method,
            Object... args) {
        if (method == null) {
            // Method could not be inferred, so fall back to StandardStreamsLogger
            sFallbackLogger.log(level, tag, throwable, "%s", args);
            return;
        }
        try {
            method.invoke(null, args);
        } catch (Exception e) {
            sFallbackLogger.log(
                    LogLevel.WTF, TAG, e, "Failed to call %s(%s)", method, Arrays.toString(args));
        }
    }

    @VisibleForTesting
    static String getMessageWithFlattenedException(String msg, @Nullable Throwable t) {
        return t == null ? msg : String.format(Locale.ENGLISH, "message=%s, exception=%s", msg, t);
    }

    // To test it, run: atest AdServicesSharedLibrariesUnitTests:DeviceSideDynamicLoggerTest
    private static final class DeviceSideRealLogger implements RealLogger {

        private static final String DELEGATE_CLASS = "android.util.Log";

        private final Method mWtf;
        private final Method mExcWtf;
        private final Method mE;
        private final Method mExcE;
        private final Method mW;
        private final Method mExcW;
        private final Method mI;
        private final Method mExcI;
        private final Method mD;
        private final Method mExcD;
        private final Method mV;
        private final Method mExcV;

        private DeviceSideRealLogger(Class<?> logClass) {
            mWtf = getMethod(logClass, "wtf", String.class, String.class);
            mExcWtf = getMethod(logClass, "wtf", String.class, String.class, Throwable.class);
            mE = getMethod(logClass, "e", String.class, String.class);
            mExcE = getMethod(logClass, "e", String.class, String.class, Throwable.class);
            mW = getMethod(logClass, "w", String.class, String.class);
            mExcW = getMethod(logClass, "w", String.class, String.class, Throwable.class);
            mI = getMethod(logClass, "i", String.class, String.class);
            mExcI = getMethod(logClass, "i", String.class, String.class, Throwable.class);
            mD = getMethod(logClass, "d", String.class, String.class);
            mExcD = getMethod(logClass, "d", String.class, String.class, Throwable.class);
            mV = getMethod(logClass, "v", String.class, String.class);
            mExcV = getMethod(logClass, "v", String.class, String.class, Throwable.class);
        }

        @Override
        @FormatMethod
        public void log(
                LogLevel level, String tag, @FormatString String msgFmt, Object... msgArgs) {
            String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
            switch (level) {
                case WTF:
                    call(level, tag, mWtf, tag, msg);
                    return;
                case ERROR:
                    call(level, tag, mE, tag, msg);
                    return;
                case WARNING:
                    call(level, tag, mW, tag, msg);
                    return;
                case INFO:
                    call(level, tag, mI, tag, msg);
                    return;
                case DEBUG:
                    call(level, tag, mD, tag, msg);
                    return;
                case VERBOSE:
                    call(level, tag, mV, tag, msg);
                    return;
                default:
                    call(LogLevel.WTF, tag, mWtf, tag, "invalid level (" + level + "): " + msg);
            }
        }

        @Override
        @FormatMethod
        public void log(
                LogLevel level,
                String tag,
                Throwable throwable,
                @FormatString String msgFmt,
                Object... msgArgs) {
            String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);
            switch (level) {
                case WTF:
                    call(level, tag, throwable, mExcWtf, tag, msg, throwable);
                    return;
                case ERROR:
                    call(level, tag, throwable, mExcE, tag, msg, throwable);
                    return;
                case WARNING:
                    call(level, tag, throwable, mExcW, tag, msg, throwable);
                    return;
                case INFO:
                    call(level, tag, throwable, mExcI, tag, msg, throwable);
                    return;
                case DEBUG:
                    call(level, tag, throwable, mExcD, tag, msg, throwable);
                    return;
                case VERBOSE:
                    call(level, tag, throwable, mExcV, tag, msg, throwable);
                    return;
                default:
                    call(
                            LogLevel.WTF,
                            tag,
                            throwable,
                            mWtf,
                            tag,
                            "invalid level (" + level + "): " + msg);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + DELEGATE_CLASS + "]";
        }
    }

    // To test it, run: atest AdServicesSharedLibrariesHostTests:DeviceSideDynamicLoggerTest
    private static final class HostSideRealLogger implements RealLogger {
        private static final String DELEGATE_CLASS = "com.android.tradefed.log.LogUtil$CLog";

        private final Method mWtf;
        private final Method mExcWtf;
        private final Method mE;
        private final Method mExcE;
        private final Method mW;
        private final Method mExcW;
        private final Method mI;
        private final Method mD;
        private final Method mV;

        HostSideRealLogger(Class<?> logClass) {
            System.out.println("YO: " + logClass);
            mWtf = getMethod(logClass, "wtf", String.class);
            mExcWtf = getMethod(logClass, "wtf", String.class, Throwable.class);
            mE = getMethod(logClass, "e", String.class);
            mExcE = getMethod(logClass, "e", Throwable.class);
            mW = getMethod(logClass, "w", String.class);
            mExcW = getMethod(logClass, "w", Throwable.class);
            mI = getMethod(logClass, "i", String.class);
            mD = getMethod(logClass, "d", String.class);
            mV = getMethod(logClass, "v", String.class);
        }

        @FormatMethod
        public void log(
                LogLevel level, String tag, final @FormatString String msgFmt, Object... msgArgs) {
            log(level, tag, /* throwable= */ null, msgFmt, msgArgs);
        }

        @Override
        @FormatMethod
        public void log(
                LogLevel level,
                String tag,
                @Nullable Throwable throwable,
                @FormatString String msgFmt,
                Object... msgArgs) {
            String tagPrefix = "[" + tag + "]: ";
            String msg = String.format(Locale.ENGLISH, msgFmt, msgArgs);

            switch (level) {
                case WTF:
                    if (throwable == null) {
                        call(level, tag, mWtf, tagPrefix + msg);
                    } else {
                        call(level, tag, throwable, mExcWtf, tagPrefix + msg, throwable);
                    }
                    return;
                case ERROR:
                    // NOTE: CLog.e() and CLog.d() don't take a throwable and a message, so we need
                    // to call both separately
                    call(level, tag, mE, tagPrefix + msg);
                    if (throwable != null) {
                        call(level, tag, throwable, mExcE, throwable);
                    }
                    return;
                case WARNING:
                    call(level, tag, mW, tagPrefix + msg);
                    if (throwable != null) {
                        call(level, tag, throwable, mExcW, throwable);
                    }
                    return;
                case INFO:
                    // NOTE: CLog.i(), CLog.d(), and CLog.V() don't take a throwable at all, so
                    // we need to "flatten" it (i.e., log just the exception, without the stack
                    // trace)
                    call(
                            level,
                            tag,
                            mI,
                            tagPrefix + getMessageWithFlattenedException(msg, throwable));
                    return;
                case DEBUG:
                    call(
                            level,
                            tag,
                            mD,
                            tagPrefix + getMessageWithFlattenedException(msg, throwable));
                    return;
                case VERBOSE:
                    call(
                            level,
                            tag,
                            mV,
                            tagPrefix + getMessageWithFlattenedException(msg, throwable));
                    return;
                default:
                    call(LogLevel.WTF, tag, mWtf, tag, "invalid level (" + level + "): " + msg);
            }
        }
    }
}
