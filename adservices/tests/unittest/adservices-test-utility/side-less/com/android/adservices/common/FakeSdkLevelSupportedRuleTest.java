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

import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.R;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S_V2;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.T;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.U;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.V;

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
    protected void setDeviceSdkLevel(AndroidSdkLevel level) {
        sLog.v("setDeviceSdkLevel(%s)", level);
        mDeviceLevel = level;
    }

    final class FakeSdkLevelSupportedRule extends AbstractSdkLevelSupportedRule {

        private FakeSdkLevelSupportedRule(AndroidSdkLevel level) {
            super(StandardStreamsLogger.getInstance(), level);
        }

        @Override
        public boolean isAtLeastR() throws Exception {
            return mDeviceLevel.isAtLeast(R);
        }

        @Override
        public boolean isAtLeastS() throws Exception {
            return mDeviceLevel.isAtLeast(S);
        }

        @Override
        public boolean isAtLeastSv2() throws Exception {
            return mDeviceLevel.isAtLeast(S_V2);
        }

        @Override
        public boolean isAtLeastT() throws Exception {
            return mDeviceLevel.isAtLeast(T);
        }

        @Override
        public boolean isAtLeastU() throws Exception {
            return mDeviceLevel.isAtLeast(U);
        }

        @Override
        public boolean isAtLeastV() throws Exception {
            return mDeviceLevel.isAtLeast(V);
        }
    }
}
