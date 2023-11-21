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

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel;

/**
 * Bogus implementation of {@link AbstractSdkLevelSupportedRuleTestCase} that uses a bogus {@link
 * AbstractSdkLevelSupportedRule} implementation.
 *
 * <p>It's not even ran by Android build targets, but it's useful for developers working on those
 * class so they can run the test directly in an IDE.
 */
public final class FakeSdkLevelSupportedRuleTest
        extends AbstractSdkLevelSupportedRuleTestCase<
                FakeSdkLevelSupportedRuleTest.FakeSdkLevelSupportedRule> {

    private static final Logger sLog =
            new Logger(StandardStreamsLogger.getInstance(), FakeSdkLevelSupportedRuleTest.class);

    private @Nullable AndroidSdkLevel mDeviceLevel;

    @Override
    protected FakeSdkLevelSupportedRule newRuleForAtLeast(AndroidSdkLevel level) {
        return new FakeSdkLevelSupportedRule(level);
    }

    @Override
    protected void setDeviceSdkLevel(FakeSdkLevelSupportedRule rule, AndroidSdkLevel level) {
        sLog.v("setDeviceSdkLevel(%s)", level);
        mDeviceLevel = level;
    }

    final class FakeSdkLevelSupportedRule extends AbstractSdkLevelSupportedRule {

        private FakeSdkLevelSupportedRule(AndroidSdkLevel level) {
            super(StandardStreamsLogger.getInstance(), level);
        }

        @Override
        public AndroidSdkLevel getDeviceApiLevel() {
            return mDeviceLevel;
        }
    }
}
