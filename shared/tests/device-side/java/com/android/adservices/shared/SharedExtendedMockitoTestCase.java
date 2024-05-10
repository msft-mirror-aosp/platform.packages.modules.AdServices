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

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AndroidExtendedMockitoMocker;
import com.android.adservices.mockito.AndroidStaticMocker;
import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.mockito.StaticClassChecker;

import com.google.common.collect.ImmutableSet;

import org.junit.Rule;

public abstract class SharedExtendedMockitoTestCase extends SharedUnitTestCase {

    @Rule(order = 10)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this).build();

    /** Provides common expectations. */
    public final Mocker mocker = new Mocker(extendedMockito);

    public static final class Mocker implements AndroidStaticMocker {

        private final AndroidStaticMocker mAndroidMocker;

        // TODO(b/338132355): create helper class to implement StaticClassChecker from rule
        private Mocker(AdServicesExtendedMockitoRule rule) {
            StaticClassChecker staticClassChecker =
                    new StaticClassChecker() {
                        @Override
                        public boolean isSpiedOrMocked(Class<?> clazz) {
                            return getSpiedOrMockedClasses().contains(clazz);
                        }

                        @Override
                        public ImmutableSet<Class<?>> getSpiedOrMockedClasses() {
                            return rule.getSpiedOrMockedClasses();
                        }

                        @Override
                        public String getTestName() {
                            return rule.getTestName();
                        }
                    };
            mAndroidMocker = new AndroidExtendedMockitoMocker(staticClassChecker);
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
        public LogInterceptor interceptLogV(String tag) {
            return mAndroidMocker.interceptLogV(tag);
        }

        @Override
        public LogInterceptor interceptLogE(String tag) {
            return mAndroidMocker.interceptLogE(tag);
        }
    }
}