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

import com.android.adservices.common.AdServicesExtendedMockitoTestCase.Mocker;
import com.android.adservices.mockito.AdServicesDebugFlagsMockerTestCase;
import com.android.adservices.service.DebugFlags;

@SuppressWarnings("VisibleForTests") // TODO(b/343741206): Remove suppress warning once fixed.
/**
 * Unit tests for {@link AdServicesExtendedMockitoTestCase.Mocker}'s implementation of {@link
 * com.android.adservices.mockito.AdServicesDebugFlagsMocker}.
 */
public final class AdServicesExtendedMockitoTestCaseAdServicesDebugFlagsMockerTest
        extends AdServicesDebugFlagsMockerTestCase<Mocker> {

    @Override
    protected Mocker getMocker(DebugFlags debugFlags) {
        return Mocker.forAdServicesDebugFlagsMockerTests(debugFlags);
    }
}
