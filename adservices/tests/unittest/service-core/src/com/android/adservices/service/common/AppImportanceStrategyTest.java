/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.app.ActivityManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Test;
import org.mockito.Mock;

@MockStatic(SdkLevel.class)
public final class AppImportanceStrategyTest extends AdServicesExtendedMockitoTestCase {
    @Mock private ActivityManager mActivityManagerMock;

    @Test
    public void testCreateInstanceReturnsGetBindingStrategyWhenVPlusAndFeatureEnabled() {
        mockIsAtLeastV(/* isIt= */ true);

        AppImportanceStrategy appImportanceStrategyEnabled =
                AppImportanceStrategy.createInstance(
                        /* bindingUidApiEnabled= */ true, mActivityManagerMock);
        expect.that(appImportanceStrategyEnabled)
                .isInstanceOf(AppImportanceStrategyGetBindingUidImportance.class);
    }

    @Test
    public void testCreateInstanceReturnsGetUidStrategyWhenUMinusAndFeatureEnabled() {
        mockIsAtLeastV(/* isIt= */ false);

        AppImportanceStrategy appImportanceStrategyDisabled =
                AppImportanceStrategy.createInstance(
                        /* bindingUidApiEnabled= */ true, mActivityManagerMock);

        expect.that(appImportanceStrategyDisabled)
                .isInstanceOf(AppImportanceStrategyGetUidImportance.class);
    }

    @Test
    public void testCreateInstanceReturnsGetUidStrategyWhenVPlusAndFeatureDisabled() {
        mockIsAtLeastV(/* isIt= */ true);

        AppImportanceStrategy appImportanceStrategyDisabled =
                AppImportanceStrategy.createInstance(
                        /* bindingUidApiEnabled= */ false, mActivityManagerMock);

        expect.that(appImportanceStrategyDisabled)
                .isInstanceOf(AppImportanceStrategyGetUidImportance.class);
    }

    private void mockIsAtLeastV(boolean isIt) {
        mocker.mockIsAtLeastV(isIt);
    }
}
