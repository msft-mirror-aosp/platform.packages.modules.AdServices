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

import static com.android.adservices.common.logging.ErrorLogUtilSyncCallback.mockErrorLogUtilWithThrowable;
import static com.android.adservices.common.logging.ErrorLogUtilSyncCallback.mockErrorLogUtilWithoutThrowable;
import static com.android.adservices.service.common.AppManifestConfigCall.API_ATTRIBUTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_TOPICS;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_UNSPECIFIED;
import static com.android.adservices.service.common.AppManifestConfigCall.apiToString;
import static com.android.adservices.service.common.AppManifestConfigCall.resultToString;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.PREFS_KEY_TEMPLATE;
import static com.android.adservices.service.common.AppManifestConfigMetricsLogger.PREFS_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_LOGGING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.sleep;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.ErrorLogUtilSyncCallback;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall.ApiType;
import com.android.adservices.service.common.AppManifestConfigCall.Result;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.shared.testing.SkipLoggingUsageRule;
import com.android.adservices.shared.testing.common.DumpHelper;
import com.android.adservices.shared.testing.common.FakeSharedPreferences;
import com.android.adservices.shared.testing.concurrency.SyncOnSharedPreferenceChangeListener;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SpyStatic(FlagsFactory.class)
@SpyStatic(StatsdAdServicesLogger.class)
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
public final class AppManifestConfigMetricsLoggerTest extends AdServicesExtendedMockitoTestCase {

    private static final String PKG_NAME = "pkg.I.am";
    private static final String PKG_NAME2 = "or.not";

    // Generic API - exact value doesn't matter
    @ApiType private static final int API = API_TOPICS;

    private static final String KEY_PKG_NAME_API =
            String.format(Locale.US, PREFS_KEY_TEMPLATE, PKG_NAME, API);

    @Mock private StatsdAdServicesLogger mStatsdLogger;

    private final FakeSharedPreferences mPrefs = new FakeSharedPreferences();

    @Before
    public void setExpectations() {
        appContext.set(mMockContext);
        mocker.mockGetFlags(mMockFlags);
        mockGetStatsdAdServicesLogger();

        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenReturn(mPrefs);
    }

    @Test
    public void testLogUsage_nullArgs() {
        assertThrows(
                NullPointerException.class,
                () ->
                        logUsageAndDontWait(
                                /* packageName= */ null, API, RESULT_ALLOWED_APP_ALLOWS_ALL));

        assertNoMetricsLogged();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_LOGGING_ERROR)
    public void testLogUsage_callWithInvalidResult() {
        AppManifestConfigCall call = new AppManifestConfigCall(PKG_NAME, API);
        call.result = RESULT_UNSPECIFIED;
        mPrefs.onEditThrows(); // will throw if edit() is called

        AppManifestConfigMetricsLogger.logUsage(call);

        assertEditNotCalled();
        assertNoMetricsLogged();
    }

    @Test
    public void testLogUsage_firstTime() throws Exception {
        logUsageAndWait(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).hasSize(1);
        assertWithMessage("properties keys")
                .that(allProps.keySet())
                .containsExactly(KEY_PKG_NAME_API);
        assertMetricsLogged(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);
    }

    @Test
    public void testLogUsage_secondTimeSameResult() throws Exception {
        // 1st time is fine
        logUsageAndWait(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);

        // 2nd time should not call edit
        mPrefs.onEditThrows(); // will throw if edit() is called
        logUsageAndDontWait(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).hasSize(1);
        assertWithMessage("properties keys")
                .that(allProps.keySet())
                .containsExactly(KEY_PKG_NAME_API);

        assertEditNotCalled();
        assertMetricsLogged(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);
    }

    @Test
    public void testLogUsage_secondTimeDifferentResult() throws Exception {
        int result = RESULT_ALLOWED_APP_ALLOWS_ALL;
        // 1st call
        mLog.d("1st call: result=%s", result);
        logUsageAndWait(PKG_NAME, API, result);

        int valueBefore = mPrefs.getInt(KEY_PKG_NAME_API, RESULT_UNSPECIFIED);
        expect.withMessage("stored value of %s after 1st call (result=%s)", PKG_NAME, result)
                .that(valueBefore)
                .isEqualTo(RESULT_ALLOWED_APP_ALLOWS_ALL);

        // 2nd call
        result = RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
        mLog.d("2nd call: result=%s", result);
        logUsageAndWait(PKG_NAME, API, result);

        Map<String, ?> allProps = mPrefs.getAll();
        expect.withMessage("allProps").that(allProps).hasSize(1);
        expect.withMessage("properties keys")
                .that(allProps.keySet())
                .containsExactly(KEY_PKG_NAME_API);

        int valueAfter = mPrefs.getInt(KEY_PKG_NAME_API, RESULT_UNSPECIFIED);
        expect.withMessage("stored value of %s after 2nd call (result=%s)", PKG_NAME, result)
                .that(valueAfter)
                .isEqualTo(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG);
        assertMetricsLogged(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);
    }

    @Test
    // TODO (b/355696393): Enhance rule to verify log calls that happen in the background.
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testLogUsage_handlesRuntimeException() throws Exception {
        // Do not move this into setup as it will conflict with ErrorLogUtil mocking behavior
        // required by the AdServicesLoggingUsageRule.
        ErrorLogUtilSyncCallback errorLogUtilWithThrowableCallback =
                mockErrorLogUtilWithThrowable();

        RuntimeException exception = new RuntimeException("D'OH!");

        when(mMockContext.getSharedPreferences(any(String.class), anyInt())).thenThrow(exception);

        logUsageAndDontWait(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);

        errorLogUtilWithThrowableCallback.assertReceived(
                expect,
                exception,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        assertNoMetricsLogged();
    }

    @Test
    // TODO (b/355696393): Enhance rule to verify log calls that happen in the background.
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testLogUsage_commitFailed() throws Exception {
        // Do not move this into setup as it will conflict with ErrorLogUtil mocking behavior
        // required by the AdServicesLoggingUsageRule.
        ErrorLogUtilSyncCallback errorLogUtilWithoutThrowableCallback =
                mockErrorLogUtilWithoutThrowable();
        mPrefs.onCommitReturns(/* result= */ false);

        logUsageAndDontWait(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);

        Map<String, ?> allProps = mPrefs.getAll();
        assertWithMessage("allProps").that(allProps).isEmpty();

        errorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        assertMetricsLogged(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);
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

        logUsageAndWait(/* async= */ true, PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);

        assertWithMessage("execution thread")
                .that(executionThread.get())
                .isNotSameInstanceAs(currentThread);
        assertMetricsLogged(PKG_NAME, API, RESULT_ALLOWED_APP_ALLOWS_ALL);
    }

    @Test
    public void testDump_empty() throws Exception {
        when(mMockContext.getDataDir()).thenReturn(new File("/la/la/land"));

        String dump = DumpHelper.dump(pw -> AppManifestConfigMetricsLogger.dump(mMockContext, pw));

        expect.withMessage("empty dump")
                .that(dump)
                .matches(
                        Pattern.compile(
                                ".*file:.*/la/la/land/shared_prefs/"
                                        + PREFS_NAME
                                        + "\\.xml.*\n"
                                        + ".*\\(Always\\) enabled by default.*\n"
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

        assertMetricsLogged(PKG_NAME, API_TOPICS, RESULT_ALLOWED_APP_ALLOWS_ALL);
        assertMetricsLogged(PKG_NAME, API_ATTRIBUTION, RESULT_DISALLOWED_BY_APP);
        assertMetricsLogged(
                PKG_NAME2, API_ATTRIBUTION, RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG);
    }

    // TODO(b/309857141): we're seeing failures (bug 321768749) where testDump_multipleEntries()
    // fails with the following exception:
    //
    // IllegalStateException: injectResult(pkg.I.am-3) called after injectResult(pkg.I.am-1)
    //
    // That's happening in the listener.assertResultReceived() call, which means the same
    // listener received the updates. Unfortunately we haven't be able to reproduce it, and the
    // listener in theory is local to this method - the only possible explanation is that
    // because FakeSharedPreferences is not thread-safe, the reference is somehow used
    // afterwards (even though it's unregistered in the finally block below.
    //
    // So, to fix that issue, we're currently guarding access to mPrefs, but hopefully we can
    // remove this synchronization once FakeSharedPreferences is thread safe.
    private final Object mPrefsListenersLock = new Object();

    // Needs to wait until the shared prefs is committed() as it happens in a separated thread
    private void logUsageAndWait(
            boolean async, String appName, @ApiType int api, @Result int callResult)
            throws InterruptedException {
        synchronized (mPrefsListenersLock) {
            SyncOnSharedPreferenceChangeListener listener =
                    new SyncOnSharedPreferenceChangeListener();
            mPrefs.registerOnSharedPreferenceChangeListener(listener);
            try {
                AppManifestConfigCall call = new AppManifestConfigCall(appName, api);
                call.result = callResult;
                mLog.v("logUsageAndWait(async=%b, call=%s, listener=%s)", async, call, listener);

                if (async) {
                    // Only testLogUsage_handlesToBgThread care about the "real" method...
                    AppManifestConfigMetricsLogger.logUsage(call);
                } else {
                    // ...every other test can just call handleLogUsage() instead
                    AppManifestConfigMetricsLogger.handleLogUsage(call);
                }

                String result = listener.assertResultReceived();
                mLog.v("result: %s", result);
            } finally {
                mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
            }
        }
    }

    private void logUsageAndWait(String appName, @ApiType int api, @Result int callResult)
            throws InterruptedException {
        logUsageAndWait(/* async= */ false, appName, api, callResult);
    }

    // Should only be used in cases where the call is expect to not change the shared preferences
    // (in which case a listener would not be called)
    private void logUsageAndDontWait(String appName, int api, @Result int callResult) {
        AppManifestConfigCall call = new AppManifestConfigCall(appName, api);
        call.result = callResult;
        mLog.v("logUsageAndDontWait(call=%s)", call);
        AppManifestConfigMetricsLogger.logUsage(call);
    }

    // Must call mPrefs.onEditThrows() first. No need to explicitly check whether ErrorLogUtil
    // has been invoked, as the AdServicesLoggingUsageRule will automatically fail the test if
    // logging is not verified.
    private void assertEditNotCalled() {
        sleep(
                1_000,
                "waiting to make sure edit() was not called in the background (which would "
                        + "have thrown an exception)");
    }

    private void assertMetricsLogged(String pkgName, @ApiType int api, @Result int result) {
        AppManifestConfigCall call = new AppManifestConfigCall(pkgName, api);
        call.result = result;
        verify(mStatsdLogger).logAppManifestConfigCall(call);
    }

    private void assertNoMetricsLogged() {
        verify(mStatsdLogger, never()).logAppManifestConfigCall(any());
    }

    // TODO(b/295321663): ideally it should be a method from AdServicesExtendedMockitoRule (so it
    // checks if StatsdAdServicesLogger is mocked), but it would add a dependency to the
    // adservices-service-core project - need to figure out a way to extend the rule to allow such
    // dependencies)
    private void mockGetStatsdAdServicesLogger() {
        mLog.v("mockGetStatsdAdServicesLogger(): %s", mStatsdLogger);
        doReturn(mStatsdLogger).when(StatsdAdServicesLogger::getInstance);
    }
}
