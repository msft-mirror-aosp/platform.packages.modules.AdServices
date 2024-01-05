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

package com.android.adservices.service.common;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockErrorLogUtilWithThrowable;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockErrorLogUtilWithoutThrowable;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilErrorWithAnyException;
import static com.android.adservices.service.common.AppManifestConfigCall.API_ATTRIBUTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_TOPICS;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_UNSPECIFIED;
import static com.android.adservices.service.common.AppManifestConfigCall.apiToString;
import static com.android.adservices.service.common.AppManifestConfigCall.resultToString;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.dump;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.PREFS_KEY_TEMPLATE;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.PREFS_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_LOGGING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.Nullable;
import com.android.adservices.common.SyncCallback;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.ExtendedMockitoExpectations.ErrorLogUtilCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall.ApiType;
import com.android.adservices.service.common.AppManifestConfigCall.Result;
import com.android.adservices.shared.testing.common.DumpHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SpyStatic(ErrorLogUtil.class)
@SpyStatic(FlagsFactory.class)
public final class AppManifestConfigMetricsLoggerTest extends AdServicesExtendedMockitoTestCase {

    private static final String PKG_NAME = "pkg.I.am";
    private static final String PKG_NAME2 = "or.not";

    // Generic API - exact value doesn't matter
    private static final @ApiType int API = API_TOPICS;

    private static final String KEY_PKG_NAME_API =
            String.format(Locale.US, PREFS_KEY_TEMPLATE, PKG_NAME, API);

    @Mock private Context mMockContext;
    @Mock private Flags mMockFlags;

    private final FakeSharedPreferences mPrefs = new FakeSharedPreferences();

    private ErrorLogUtilCallback mErrorLogUtilWithThrowableCallback;
    private ErrorLogUtilCallback mErrorLogUtilWithoutThrowableCallback;

    @Before
    public void setExpectations() {
        appContext.set(mMockContext);
        extendedMockito.mockGetFlags(mMockFlags);
        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenReturn(mPrefs);
        mErrorLogUtilWithThrowableCallback = mockErrorLogUtilWithThrowable();
        mErrorLogUtilWithoutThrowableCallback = mockErrorLogUtilWithoutThrowable();
    }

    @Test
    public void testLogUsage_nullArgs() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> logUsageAndDontWait(/* packageName= */ null, RESULT_ALLOWED_APP_ALLOWS_ALL));
    }

    @Test
    public void testLogUsage_callWithInvalidResult() throws Exception {
        AppManifestConfigCall call = new AppManifestConfigCall(PKG_NAME, API);
        call.result = RESULT_UNSPECIFIED;
        mPrefs.onEditThrows(); // will throw if edit() is called

        AppManifestConfigMetricsLogger.logUsage(call);

        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_LOGGING_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        assertEditNotCalled();
    }

    @Test
    public void testLogUsage_firstTime() throws Exception {
        logUsageAndWait(PKG_NAME, RESULT_ALLOWED_APP_ALLOWS_ALL);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).hasSize(1);
        assertWithMessage("properties keys")
                .that(allProps.keySet())
                .containsExactly(KEY_PKG_NAME_API);
    }

    @Test
    public void testLogUsage_secondTimeSameResult() throws Exception {
        // 1st time is fine
        logUsageAndWait(PKG_NAME, RESULT_ALLOWED_APP_ALLOWS_ALL);

        // 2nd time should not call edit
        mPrefs.onEditThrows(); // will throw if edit() is called
        logUsageAndDontWait(PKG_NAME, RESULT_ALLOWED_APP_ALLOWS_ALL);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).hasSize(1);
        assertWithMessage("properties keys")
                .that(allProps.keySet())
                .containsExactly(KEY_PKG_NAME_API);

        assertEditNotCalled();
    }

    @FlakyTest(
            bugId = 315979774,
            detail =
                    "Should be fine now (issue was probably calling mPrefs instead of pref, and"
                        + " method is simpler now regardless), but annotation will be removed in a"
                        + " follow-up CL")
    @Test
    public void testLogUsage_secondTimeDifferentResult() throws Exception {
        int result = RESULT_ALLOWED_APP_ALLOWS_ALL;
        // 1st call
        Log.d(mTag, "1st call: result=" + result);
        logUsageAndWait(PKG_NAME, result);

        int valueBefore = mPrefs.getInt(KEY_PKG_NAME_API, RESULT_UNSPECIFIED);
        expect.withMessage("stored value of %s after 1st call (result=%s)", PKG_NAME, result)
                .that(valueBefore)
                .isEqualTo(RESULT_ALLOWED_APP_ALLOWS_ALL);

        // 2nd call
        result = RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
        Log.d(mTag, "2nd call: result=" + result);
        logUsageAndWait(PKG_NAME, result);

        Map<String, ?> allProps = mPrefs.getAll();
        expect.withMessage("allProps").that(allProps).hasSize(1);
        expect.withMessage("properties keys")
                .that(allProps.keySet())
                .containsExactly(KEY_PKG_NAME_API);

        int valueAfter = mPrefs.getInt(KEY_PKG_NAME_API, RESULT_UNSPECIFIED);
        expect.withMessage("stored value of %s after 2nd call (result=%s)", PKG_NAME, result)
                .that(valueAfter)
                .isEqualTo(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG);
    }

    @Test
    public void testLogUsage_handlesRuntimeException() throws Exception {
        RuntimeException exception = new RuntimeException("D'OH!");

        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenThrow(exception);

        logUsageAndDontWait(PKG_NAME, RESULT_ALLOWED_APP_ALLOWS_ALL);

        mErrorLogUtilWithThrowableCallback.assertReceived(
                expect,
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testLogUsage_commitFailed() throws Exception {
        mPrefs.onCommitReturns(/* result= */ false);

        logUsageAndDontWait(PKG_NAME, RESULT_ALLOWED_APP_ALLOWS_ALL);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).isEmpty();

        mErrorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testLogUsage_handlesToBgThread() throws Exception {
        Thread currentThread = Thread.currentThread();
        AtomicReference<Thread> executionThread = new AtomicReference<>();

        when(mMockContext.getSharedPreferences(any(String.class), anyInt()))
                .thenAnswer(
                        (inv) -> {
                            executionThread.set(Thread.currentThread());
                            return mPrefs;
                        });

        logUsageAndWait(PKG_NAME, RESULT_ALLOWED_APP_ALLOWS_ALL);

        assertWithMessage("execution thread")
                .that(executionThread.get())
                .isNotSameInstanceAs(currentThread);
    }

    @Test
    public void testDump_empty() throws Exception {
        when(mMockFlags.getAppConfigReturnsEnabledByDefault()).thenReturn(true);
        when(mMockContext.getDataDir()).thenReturn(new File("/la/la/land"));

        String dump = DumpHelper.dump(pw -> AppManifestConfigMetricsLogger.dump(mMockContext, pw));

        expect.withMessage("empty dump")
                .that(dump)
                .matches(
                        Pattern.compile(
                                ".*file:.*/la/la/land/shared_prefs/"
                                        + PREFS_NAME
                                        + "\\.xml.*\n"
                                        + ".*enabled by default: "
                                        + true
                                        + ".*\n"
                                        + ".*0 entries:.*",
                                Pattern.DOTALL));
    }

    @Test
    public void testDump_multipleEntries() throws Exception {
        logUsageAndWait(PKG_NAME, API_TOPICS, RESULT_ALLOWED_APP_ALLOWS_ALL);
        logUsageAndWait(PKG_NAME, API_ATTRIBUTION, RESULT_DISALLOWED_BY_APP);
        logUsageAndWait(
                PKG_NAME2, API_ATTRIBUTION, RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG);

        String dump = DumpHelper.dump(pw -> AppManifestConfigMetricsLogger.dump(mMockContext, pw));

        String entry1 =
                ".*"
                        + PKG_NAME
                        + "-"
                        + apiToString(API_TOPICS)
                        + ": "
                        + resultToString(RESULT_ALLOWED_APP_ALLOWS_ALL)
                        + ".*\n";
        String entry2 =
                ".*"
                        + PKG_NAME
                        + "-"
                        + apiToString(API_ATTRIBUTION)
                        + ": "
                        + resultToString(RESULT_DISALLOWED_BY_APP)
                        + ".*\n";
        String entry3 =
                ".*"
                        + PKG_NAME2
                        + "-"
                        + apiToString(API_ATTRIBUTION)
                        + ": "
                        + resultToString(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG)
                        + ".*\n";
        expect.withMessage("dump")
                .that(dump)
                .matches(
                        Pattern.compile(
                                ".*3 entries.*\n" + entry1 + entry2 + entry3, Pattern.DOTALL));
    }

    // Needs to wait until the shared prefs is committed() as it happens in a separated thread
    private void logUsageAndWait(String appName, @Result int callResult)
            throws InterruptedException {
        logUsageAndWait(appName, API, callResult);
    }

    // Needs to wait until the shared prefs is committed() as it happens in a separated thread
    private void logUsageAndWait(String appName, @ApiType int api, @Result int callResult)
            throws InterruptedException {
        SyncOnSharedPreferenceChangeListener listener = new SyncOnSharedPreferenceChangeListener();
        mPrefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            AppManifestConfigCall call = new AppManifestConfigCall(appName, api);
            call.result = callResult;
            Log.v(mTag, "logUsageAndWait(call=" + call + ", listener=" + listener + ")");

            AppManifestConfigMetricsLogger.logUsage(call);
            String result = listener.assertResultReceived();
            Log.v(mTag, "result: " + result);
        } finally {
            mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    // Should only be used in cases where the call is expect to not change the shared preferences
    // (in which case a listener would not be called)
    private void logUsageAndDontWait(String appName, @Result int callResult) {
        AppManifestConfigCall call = new AppManifestConfigCall(appName, API);
        call.result = callResult;
        Log.v(mTag, "logUsageAndDontWait(call=" + call + ")");
        AppManifestConfigMetricsLogger.logUsage(call);
    }

    // Must call mPrefs.onEditThrows() first
    private void assertEditNotCalled() {
        sleep(
                1_000,
                "waiting to make sure edit() was not called in the background (which would "
                        + "have thrown an exception)");

        verifyErrorLogUtilErrorWithAnyException(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON,
                never());
    }

    // TODO(b/309857141): move to its own class / common package (it will be done in a later CL so
    // this class can be easily cherry picked into older releases).
    // TODO(b/309857141): add unit tests when move
    /**
     * Fake implementation of {@link SharedPreferences}.
     *
     * <p><b>Note: </b>calls made to the {@link #edit() editor} are persisted right away, unless
     * disabled by calls to {@link #onCommitReturns(boolean) onCommitReturns(false)} or {@link
     * #disableApply}.
     *
     * <p>This class is not thread safe.
     */
    public static final class FakeSharedPreferences implements SharedPreferences {

        private static final String TAG = FakeSharedPreferences.class.getSimpleName();

        private static final IllegalStateException EDIT_DISABLED_EXCEPTION =
                new IllegalStateException("edit() is not available");
        private static final IllegalStateException COMMIT_DISABLED_EXCEPTION =
                new IllegalStateException("commit() is not available");
        private static final IllegalStateException APPLY_DISABLED_EXCEPTION =
                new IllegalStateException("apply() is not available");

        private final FakeEditor mEditor = new FakeEditor();

        @Nullable private RuntimeException mEditException;

        /**
         * Changes behavior of {@link #edit()} so it throws an exception.
         *
         * @return the exception that will be thrown by {@link #edit()}.
         */
        public RuntimeException onEditThrows() {
            mEditException = EDIT_DISABLED_EXCEPTION;
            Log.v(TAG, "onEditThrows(): edit() will return " + mEditException);
            return mEditException;
        }

        /**
         * Sets the result of calls to {@link Editor#commit()}.
         *
         * <p><b>Note: </b>when called with {@code false}, calls made to the {@link #edit() editor}
         * after this call will be ignored (until it's called again with {@code true}).
         */
        public void onCommitReturns(boolean result) {
            mEditor.mCommitResult = result;
            Log.v(TAG, "onCommitReturns(): commit() will return " + mEditor.mCommitResult);
        }

        @Override
        public Map<String, ?> getAll() {
            return mEditor.mProps;
        }

        @Override
        public String getString(String key, String defValue) {
            return mEditor.get(key, String.class, defValue);
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            @SuppressWarnings("unchecked")
            Set<String> value = mEditor.get(key, Set.class, defValues);
            return value;
        }

        @Override
        public int getInt(String key, int defValue) {
            return mEditor.get(key, Integer.class, defValue);
        }

        @Override
        public long getLong(String key, long defValue) {
            return mEditor.get(key, Long.class, defValue);
        }

        @Override
        public float getFloat(String key, float defValue) {
            return mEditor.get(key, Float.class, defValue);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return mEditor.get(key, Boolean.class, defValue);
        }

        @Override
        public boolean contains(String key) {
            return mEditor.mProps.containsKey(key);
        }

        @Override
        public FakeEditor edit() {
            Log.v(TAG, "edit(): mEditException=" + mEditException);
            if (mEditException != null) {
                throw mEditException;
            }
            return mEditor;
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            mEditor.mListeners.add(listener);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            mEditor.mListeners.remove(listener);
        }

        private final class FakeEditor implements Editor {

            private final Map<String, Object> mProps = new LinkedHashMap<>();
            private final Set<String> mKeysToNotify = new LinkedHashSet<>();
            private final List<OnSharedPreferenceChangeListener> mListeners = new ArrayList<>();

            private boolean mCommitResult = true;

            @Override
            public Editor putString(String key, String value) {
                return put(key, value);
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                return put(key, values);
            }

            @Override
            public Editor putInt(String key, int value) {
                return put(key, value);
            }

            @Override
            public Editor putLong(String key, long value) {
                return put(key, value);
            }

            @Override
            public Editor putFloat(String key, float value) {
                return put(key, value);
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                return put(key, value);
            }

            @Override
            public Editor remove(String key) {
                Log.v(TAG, "remove(" + key + ")");
                mProps.remove(key);
                mKeysToNotify.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                Log.v(TAG, "clear()");
                mProps.clear();
                return this;
            }

            @Override
            public boolean commit() {
                Log.v(TAG, "commit(): mCommitResult=" + mCommitResult);
                try {
                    return mCommitResult;
                } finally {
                    notifyListeners();
                }
            }

            @Override
            public void apply() {
                Log.v(TAG, "apply(): mCommitResult=" + mCommitResult);
                notifyListeners();
            }

            private <T> T get(String key, Class<T> clazz, T defValue) {
                Object value = mProps.get(key);
                return value == null ? defValue : clazz.cast(value);
            }

            private FakeEditor put(String key, Object value) {
                Log.v(
                        TAG,
                        "put(): "
                                + key
                                + "="
                                + value
                                + " ("
                                + value.getClass().getSimpleName()
                                + "): mCommitResult="
                                + mCommitResult);
                if (mCommitResult) {
                    mProps.put(key, value);
                    mKeysToNotify.add(key);
                }
                return mEditor;
            }

            private void notifyListeners() {
                for (OnSharedPreferenceChangeListener listener : mListeners) {
                    for (String key : mKeysToNotify) {
                        Log.v(TAG, "Notifying key change (" + key + ") to " + listener);
                        listener.onSharedPreferenceChanged(FakeSharedPreferences.this, key);
                    }
                }
                Log.v(TAG, "Clearing keys to notify (" + mKeysToNotify + ")");
                mKeysToNotify.clear();
            }
        }
    }

    // TODO(b/309857141): move to its own class / common package (it will be done in a later CL so
    // this class can be easily cherry picked into older releases).
    /**
     * OnSharedPreferenceChangeListener implementation that blocks until the first key is received.
     */
    public static final class SyncOnSharedPreferenceChangeListener
            extends SyncCallback<String, RuntimeException>
            implements OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            injectResult(key);
        }
    }
}
