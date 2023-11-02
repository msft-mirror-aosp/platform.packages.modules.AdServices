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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastR;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastS;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastT;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastU;

import android.util.Log;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;

public final class SdkLevelSupportRuleTest
        extends AbstractSdkLevelSupportedRuleTestCase<SdkLevelSupportRule> {

    private static final String TAG = SdkLevelSupportRuleTest.class.getSimpleName();

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder().spyStatic(SdkLevel.class).build();

    @Override
    protected SdkLevelSupportRule newRuleForAtLeast(AndroidSdkLevel level) {
        return new SdkLevelSupportRule(level);
    }

    @Override
    protected void setDeviceSdkLevel(AndroidSdkLevel level) {
        Log.v(TAG, "setDeviceSdkLevel(" + level + ")");
        switch (level) {
                // TODO(b/295321663): this combination of mockIsAtLeastX() is hacky, need to
                // refactor
                // the rule to use SDK ints directly
            case R:
                mockIsAtLeastR(true);
                mockIsAtLeastS(false);
                mockIsAtLeastT(false);
                mockIsAtLeastU(false);
                return;
            case S:
                mockIsAtLeastR(true);
                mockIsAtLeastS(true);
                mockIsAtLeastT(false);
                mockIsAtLeastU(false);
                return;
            case T:
                mockIsAtLeastR(true);
                mockIsAtLeastS(true);
                mockIsAtLeastT(true);
                mockIsAtLeastU(false);
                return;
            case U:
                mockIsAtLeastR(true);
                mockIsAtLeastS(true);
                mockIsAtLeastT(true);
                mockIsAtLeastU(true);
                return;
            default:
                throw new UnsupportedOperationException(
                        "mocking level " + level + " not implemented yet");
        }
    }
}
