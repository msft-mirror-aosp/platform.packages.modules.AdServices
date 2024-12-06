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
package com.android.adservices.common;

import static com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.Mode.CLEAR_AFTER_TEST_CLASS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;

import android.annotation.CallSuper;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.adservices.common.AdServicesMockerLessExtendedMockitoTestCase.InternalMocker;
import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesDebugFlagsMocker;
import com.android.adservices.mockito.AdServicesExtendedMockitoMocker;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AdServicesFlagsMocker;
import com.android.adservices.mockito.AdServicesMockitoDebugFlagsMocker;
import com.android.adservices.mockito.AdServicesMockitoFlagsMocker;
import com.android.adservices.mockito.AdServicesMockitoMocker;
import com.android.adservices.mockito.AdServicesPragmaticMocker;
import com.android.adservices.mockito.AdServicesStaticMocker;
import com.android.adservices.mockito.AndroidExtendedMockitoMocker;
import com.android.adservices.mockito.AndroidMocker;
import com.android.adservices.mockito.AndroidMockitoMocker;
import com.android.adservices.mockito.AndroidStaticMocker;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.ClearInlineMocksMode;
import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.mockito.SharedMocker;
import com.android.adservices.mockito.SharedMockitoMocker;
import com.android.adservices.mockito.StaticClassChecker;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.util.Clock;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.ClassRule;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

/**
 * Base class for {@link AdServicesExtendedMockitoTestCase}, but leaving a hook ({@link
 * #newMocker(AdServicesExtendedMockitoRule, Flags)} for subclasses to provide the {@code mocker}
 * object.
 *
 * @param <M> mocker type
 */
@ClearInlineMocksMode(CLEAR_AFTER_TEST_CLASS)
@SpyStatic(ErrorLogUtil.class)
public abstract class AdServicesMockerLessExtendedMockitoTestCase<M extends InternalMocker>
        extends AdServicesUnitTestCase {

    @Mock protected Context mMockContext;

    protected final Flags mMockFlags = mock(Flags.class);
    protected final DebugFlags mMockDebugFlags = mock(DebugFlags.class);

    /** Spy the {@link AdServicesUnitTestCase#mContext} */
    @Spy protected final Context mSpyContext = mContext;

    // NOTE: must use CLEAR_AFTER_TEST_CLASS by default (defined as a class annotation, so it's used
    // by both ExtendedMockitoInlineCleanerRule and AdServicesExtendedMockitoRule), as some tests
    // performing complicated static class initialization on @Before methods, which often cause test
    // failure when called after the mocks are cleared (for example, DialogFragmentTest would fail
    // after the first method was executed)
    @ClassRule
    public static final ExtendedMockitoInlineCleanerRule sInlineCleaner =
            new ExtendedMockitoInlineCleanerRule();

    @Rule(order = 10)
    public final AdServicesExtendedMockitoRule extendedMockito = getAdServicesExtendedMockitoRule();

    /**
     * Scans for usage of {@code ErrorLogUtil.e(int, int)} and {@code ErrorLogUtil.e(Throwable, int
     * int)} invocations. Fails the test if calls haven't been verified using {@link
     * ExpectErrorLogUtilCall} and/or {@link ExpectErrorLogUtilWithExceptionCall}.
     *
     * <p>Also see {@link SetErrorLogUtilDefaultParams} to set common default logging params.
     */
    // TODO(b/342639109): Fix the order of the rules.
    @Rule(order = 11)
    public final AdServicesLoggingUsageRule errorLogUtilUsageRule =
            AdServicesLoggingUsageRule.errorLogUtilUsageRule();

    /** Provides common expectations. */
    public final M mocker = newMocker(extendedMockito, mMockFlags, mMockDebugFlags);

    /**
     * Gets the {@link AdServicesExtendedMockitoRule} that will be set as the {@code
     * extendedMockito} rule.
     *
     * <p>By default returns a rule created using {@link
     * #newDefaultAdServicesExtendedMockitoRuleBuilder()}, which is enough for most tests. But
     * subclasses can override it to handle special cases that cannot be configured through
     * annotations, like :
     *
     * <ul>
     *   <li>Changing the strictness mode.
     *   <li>Setting the {@link com.android.modules.utils.testing.StaticMockFixture}s.
     * </ul>
     */
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder().build();
    }

    /** Returns the object that will be referenced by {@code mocker}. */
    protected abstract M newMocker(
            AdServicesExtendedMockitoRule rule, Flags mockFlags, DebugFlags mockDebugFlags);

    /**
     * Creates a new {@link AdServicesExtendedMockitoRule.Builder} with the default properties.
     *
     * @return builder that initialize mocks for the class, using {@link Strictness.LENIENT lenient}
     *     mode.
     */
    protected final AdServicesExtendedMockitoRule.Builder
            newDefaultAdServicesExtendedMockitoRuleBuilder() {
        return new AdServicesExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT);
    }

    // TODO(b/361555631): rename to testAdServicesExtendedMockitoTestCaseFixtures() and annotate
    // it with @MetaTest
    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        checkProhibitedMockitoFields(AdServicesMockerLessExtendedMockitoTestCase.class, this);
    }

    private static final String REASON_SESSION_MANAGED_BY_RULE =
            "mockito session is automatically managed by a @Rule";

    public abstract static class InternalMocker
            implements AndroidMocker,
                    AndroidStaticMocker,
                    AdServicesPragmaticMocker,
                    AdServicesFlagsMocker,
                    AdServicesDebugFlagsMocker,
                    AdServicesStaticMocker,
                    SharedMocker {

        private final AndroidMocker mAndroidMocker = new AndroidMockitoMocker();
        private final SharedMocker mSharedMocker = new SharedMockitoMocker();
        private final AdServicesPragmaticMocker mAdServicesMocker = new AdServicesMockitoMocker();
        @Nullable private final AdServicesFlagsMocker mAdServicesFlagsMocker;
        @Nullable private final AdServicesDebugFlagsMocker mAdServicesDebugFlagsMocker;
        @Nullable private final AndroidStaticMocker mAndroidStaticMocker;
        @Nullable private final AdServicesStaticMocker mAdServicesStaticMocker;

        protected InternalMocker(StaticClassChecker checker, Flags flags, DebugFlags debugFlags) {
            if (checker != null) {
                mAndroidStaticMocker = new AndroidExtendedMockitoMocker(checker);
                mAdServicesStaticMocker = new AdServicesExtendedMockitoMocker(checker);
            } else {
                mAndroidStaticMocker = null;
                mAdServicesStaticMocker = null;
            }
            mAdServicesFlagsMocker = flags != null ? new AdServicesMockitoFlagsMocker(flags) : null;
            mAdServicesDebugFlagsMocker =
                    debugFlags != null ? new AdServicesMockitoDebugFlagsMocker(debugFlags) : null;
        }

        // AndroidMocker methods

        @Override
        public void mockQueryIntentService(PackageManager mockPm, ResolveInfo... resolveInfos) {
            mAndroidMocker.mockQueryIntentService(mockPm, resolveInfos);
        }

        @Override
        public void mockGetApplicationContext(Context mockContext, Context appContext) {
            mAndroidMocker.mockGetApplicationContext(mockContext, appContext);
        }

        // AndroidStaticMocker methods

        @Override
        public void mockGetCallingUidOrThrow(int uid) {
            mAndroidStaticMocker.mockGetCallingUidOrThrow(uid);
        }

        @Override
        public void mockGetCallingUidOrThrow() {
            mAndroidStaticMocker.mockGetCallingUidOrThrow();
        }

        @Override
        public void mockIsAtLeastR(boolean isIt) {
            mAndroidStaticMocker.mockIsAtLeastR(isIt);
        }

        @Override
        public void mockIsAtLeastS(boolean isIt) {
            mAndroidStaticMocker.mockIsAtLeastS(isIt);
        }

        @Override
        public void mockIsAtLeastT(boolean isIt) {
            mAndroidStaticMocker.mockIsAtLeastT(isIt);
        }

        @Override
        public void mockSdkLevelR() {
            mAndroidStaticMocker.mockSdkLevelR();
        }

        @Override
        public void mockSdkLevelS() {
            mAndroidStaticMocker.mockSdkLevelS();
        }

        @Override
        public void mockGetCurrentUser(int user) {
            mAndroidStaticMocker.mockGetCurrentUser(user);
        }

        @Override
        public LogInterceptor interceptLogD(String tag) {
            return mAndroidStaticMocker.interceptLogD(tag);
        }

        @Override
        public LogInterceptor interceptLogV(String tag) {
            return mAndroidStaticMocker.interceptLogV(tag);
        }

        @Override
        public LogInterceptor interceptLogE(String tag) {
            return mAndroidStaticMocker.interceptLogE(tag);
        }

        // AdServicesPragmaticMocker methods

        @Override
        public ResultSyncCallback<ApiCallStats> mockLogApiCallStats(
                AdServicesLogger adServicesLogger) {
            return mAdServicesMocker.mockLogApiCallStats(adServicesLogger);
        }

        @Override
        public ResultSyncCallback<ApiCallStats> mockLogApiCallStats(
                AdServicesLogger adServicesLogger, long timeoutMs) {
            return mAdServicesMocker.mockLogApiCallStats(adServicesLogger, timeoutMs);
        }

        // AdServicesFlagsMocker methods
        @Override
        public void mockGetBackgroundJobsLoggingKillSwitch(boolean value) {
            mAdServicesFlagsMocker.mockGetBackgroundJobsLoggingKillSwitch(value);
        }

        @Override
        public void mockGetCobaltLoggingEnabled(boolean value) {
            mAdServicesFlagsMocker.mockGetCobaltLoggingEnabled(value);
        }

        @Override
        public void mockGetAppNameApiErrorCobaltLoggingEnabled(boolean value) {
            mAdServicesFlagsMocker.mockGetAppNameApiErrorCobaltLoggingEnabled(value);
        }

        @Override
        public void mockGetEnableApiCallResponseLoggingEnabled(boolean value) {
            mAdServicesFlagsMocker.mockGetEnableApiCallResponseLoggingEnabled(value);
        }

        @Override
        public void mockGetAdservicesReleaseStageForCobalt(String stage) {
            mAdServicesFlagsMocker.mockGetAdservicesReleaseStageForCobalt(stage);
        }

        @Override
        public void mockAllCobaltLoggingFlags(boolean enabled) {
            mAdServicesFlagsMocker.mockAllCobaltLoggingFlags(enabled);
        }

        @Override
        public void mockGetDeveloperSessionFeatureEnabled(boolean value) {
            mAdServicesDebugFlagsMocker.mockGetDeveloperSessionFeatureEnabled(value);
        }

        // AdServicesDebugFlagsMocker methods
        @Override
        public void mockGetConsentManagerDebugMode(boolean value) {
            mAdServicesDebugFlagsMocker.mockGetConsentManagerDebugMode(value);
        }

        @Override
        public void mockGetConsentNotificationDebugMode(boolean value) {
            mAdServicesDebugFlagsMocker.mockGetConsentNotificationDebugMode(value);
        }

        // AdServicesStaticMocker methods

        @Override
        public void mockGetFlags(Flags mockedFlags) {
            mAdServicesStaticMocker.mockGetFlags(mockedFlags);
        }

        @Override
        public void mockGetFlagsForTesting() {
            mAdServicesStaticMocker.mockGetFlagsForTesting();
        }

        @Override
        public void mockGetDebugFlags(DebugFlags mockedDebugFlags) {
            mAdServicesStaticMocker.mockGetDebugFlags(mockedDebugFlags);
        }

        @Override
        public void mockSpeJobScheduler(AdServicesJobScheduler mockedAdServicesJobScheduler) {
            mAdServicesStaticMocker.mockSpeJobScheduler(mockedAdServicesJobScheduler);
        }

        @Override
        public void mockAdServicesJobServiceFactory(
                AdServicesJobServiceFactory mockedAdServicesJobServiceFactory) {
            mAdServicesStaticMocker.mockAdServicesJobServiceFactory(
                    mockedAdServicesJobServiceFactory);
        }

        @Override
        public void mockAdServicesLoggerImpl(AdServicesLoggerImpl mockedAdServicesLoggerImpl) {
            mAdServicesStaticMocker.mockAdServicesLoggerImpl(mockedAdServicesLoggerImpl);
        }

        // SharedMocker methods

        @Override
        public Context setApplicationContextSingleton() {
            return mSharedMocker.setApplicationContextSingleton();
        }

        @Override
        public void mockSetApplicationContextSingleton(Context context) {
            mSharedMocker.mockSetApplicationContextSingleton(context);
        }

        @Override
        public JobServiceLoggingCallback syncRecordOnStopJob(JobServiceLogger logger) {
            return mSharedMocker.syncRecordOnStopJob(logger);
        }

        @Override
        public void mockCurrentTimeMillis(Clock mockClock, long... mockedValues) {
            mSharedMocker.mockCurrentTimeMillis(mockClock, mockedValues);
        }

        @Override
        public void mockElapsedRealtime(Clock mockClock, long... mockedValues) {
            mSharedMocker.mockElapsedRealtime(mockClock, mockedValues);
        }
    }

    /**
     * @deprecated Use {@link AdServicesLoggingUsageRule} to verify {@link ErrorLogUtil#e()} calls.
     *     Tests using this rule should NOT mock {@link ErrorLogUtil#e()} calls as it's taken care
     *     of under the hood.
     */
    // TODO(b/359964245): final use case that needs some investigation before this can be deleted
    @Deprecated
    protected final void doNothingOnErrorLogUtilError() {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
    }
}
