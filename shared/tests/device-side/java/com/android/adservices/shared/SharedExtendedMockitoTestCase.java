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

package com.android.adservices.shared;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AndroidExtendedMockitoMocker;
import com.android.adservices.mockito.AndroidStaticMocker;
import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.mockito.StaticClassChecker;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.testing.CallSuper;

import org.junit.Rule;

// NOTE: currently no subclass needs a custom mocker; once they do, this class should be split
// into a SharedMockerLessExtendededMockitoTestCase (similar to AdServiceExtendedMockitoTestCase /
// AdServicesMockerLessExtendedMockitoTestCase)
// TODO(b/335935200): fix this
@DisabledOnRavenwood(reason = "Uses ExtendedMockito")
public abstract class SharedExtendedMockitoTestCase extends SharedUnitTestCase {

    @Rule(order = 10)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this).build();

    /** Provides common expectations. */
    public final Mocker mocker = new Mocker(extendedMockito);

    protected final Context mMockContext = mock(Context.class);
    protected final ModuleSharedFlags mMockFlags = mock(ModuleSharedFlags.class);

    // TODO(b/361555631): rename to testSharedExtendedMockitoTestCaseFixtures() and annotate it with
    // @MetaTest
    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        checkProhibitedMockitoFields(SharedExtendedMockitoTestCase.class, this);
    }

    public static final class Mocker implements AndroidStaticMocker {

        private final AndroidStaticMocker mAndroidMocker;

        Mocker(StaticClassChecker checker) {
            mAndroidMocker = new AndroidExtendedMockitoMocker(checker);
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
        public void mockSdkLevelS() {
            mAndroidMocker.mockSdkLevelS();
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
    }
}
