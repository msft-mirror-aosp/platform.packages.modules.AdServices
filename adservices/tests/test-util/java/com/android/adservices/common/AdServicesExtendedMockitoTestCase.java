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

import static com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.Mode.CLEAR_AFTER_TEST_CLASS;

import android.content.Context;

import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoMocker;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AdServicesStaticMockitoMocker;
import com.android.adservices.mockito.AndroidExtendedMockitoMocker;
import com.android.adservices.mockito.AndroidStaticMocker;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule;
import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.ClearInlineMocksMode;
import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.mockito.SharedMocker;
import com.android.adservices.mockito.SharedMockitoMocker;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.ClassRule;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

/**
 * Base class for all unit tests that use {@code ExtendedMockito} - for "regular Mockito" use {@link
 * AdServicesMockitoTestCase} instead).
 *
 * <p><b>NOTE:</b> subclasses MUST use {@link
 * com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic} and/or {@link
 * com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic} to set which static classes are
 * mocked ad/or spied.
 *
 * <p>TODO(b/355699778) - Add linter to ensure ErrorLogUtil invocation is not mocked in subclasses.
 * {@link ErrorLogUtil} is automatically spied for all subclasses to ensure {@link
 * AdServicesLoggingUsageRule} is enforced for logging verification. Subclasses should not mock
 * {@link ErrorLogUtil} to avoid interference with mocking behavior needed for the rule.
 */
@ClearInlineMocksMode(CLEAR_AFTER_TEST_CLASS)
@SpyStatic(ErrorLogUtil.class)
public abstract class AdServicesExtendedMockitoTestCase extends AdServicesUnitTestCase {

    @Mock protected Context mMockContext;

    /** Spy the {@link AdServicesUnitTestCase#sContext} */
    @Spy protected final Context mSpyContext = sContext;

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
    public final Mocker mocker = new Mocker(extendedMockito);

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

    public static final class Mocker
            implements AndroidStaticMocker, AdServicesStaticMockitoMocker, SharedMocker {

        private final AndroidStaticMocker mAndroidMocker;
        private final AdServicesStaticMockitoMocker mAdServicesMocker;
        private final SharedMocker mSharedMocker = new SharedMockitoMocker();

        private Mocker(AdServicesExtendedMockitoRule rule) {
            mAndroidMocker = new AndroidExtendedMockitoMocker(rule);
            mAdServicesMocker = new AdServicesExtendedMockitoMocker(rule);
        }

        // AndroidStaticMocker methods

        @Override
        public void mockGetCallingUidOrThrow(int uid) {
            mAndroidMocker.mockGetCallingUidOrThrow(uid);
        }

        @Override
        public void mockGetCallingUidOrThrow() {
            mAndroidMocker.mockGetCallingUidOrThrow();
        }

        @Override
        public void mockIsAtLeastR(boolean isIt) {
            mAndroidMocker.mockIsAtLeastR(isIt);
        }

        @Override
        public void mockIsAtLeastS(boolean isIt) {
            mAndroidMocker.mockIsAtLeastS(isIt);
        }

        @Override
        public void mockIsAtLeastT(boolean isIt) {
            mAndroidMocker.mockIsAtLeastT(isIt);
        }

        @Override
        public void mockSdkLevelR() {
            mAndroidMocker.mockSdkLevelR();
        }

        @Override
        public void mockGetCurrentUser(int user) {
            mAndroidMocker.mockGetCurrentUser(user);
        }

        @Override
        public LogInterceptor interceptLogD(String tag) {
            return mAndroidMocker.interceptLogD(tag);
        }

        @Override
        public LogInterceptor interceptLogV(String tag) {
            return mAndroidMocker.interceptLogV(tag);
        }

        @Override
        public LogInterceptor interceptLogE(String tag) {
            return mAndroidMocker.interceptLogE(tag);
        }

        // AdServicesExtendedMockitoMocker methods

        @Override
        public void mockGetFlags(Flags mockedFlags) {
            mAdServicesMocker.mockGetFlags(mockedFlags);
        }

        @Override
        public void mockGetFlagsForTesting() {
            mAdServicesMocker.mockGetFlagsForTesting();
        }

        @Override
        public void mockSpeJobScheduler(AdServicesJobScheduler mockedAdServicesJobScheduler) {
            mAdServicesMocker.mockSpeJobScheduler(mockedAdServicesJobScheduler);
        }

        @Override
        public void mockAdServicesJobServiceFactory(
                AdServicesJobServiceFactory mockedAdServicesJobServiceFactory) {
            mAdServicesMocker.mockAdServicesJobServiceFactory(mockedAdServicesJobServiceFactory);
        }

        @Override
        public void mockAdServicesLoggerImpl(AdServicesLoggerImpl mockedAdServicesLoggerImpl) {
            mAdServicesMocker.mockAdServicesLoggerImpl(mockedAdServicesLoggerImpl);
        }

        // SharedMocker methods

        @Override
        public Context setApplicationContextSingleton() {
            return mSharedMocker.setApplicationContextSingleton();
        }

        @Override
        public JobServiceLoggingCallback syncRecordOnStopJob(JobServiceLogger logger) {
            return mSharedMocker.syncRecordOnStopJob(logger);
        }
    }
}
