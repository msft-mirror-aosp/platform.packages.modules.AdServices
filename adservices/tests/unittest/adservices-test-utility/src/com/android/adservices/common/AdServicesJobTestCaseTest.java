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

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.mockito.AdServicesJobMocker;

import org.junit.Test;

public final class AdServicesJobTestCaseTest extends AdServicesJobServiceTestCase {

    @Test
    public void testJobMocker() {
        assertWithMessage("mocker").that(mocker).isNotNull();

        // Test parent subclass so we don't need to create one test class for each of the mocker
        // interfaces it implements
        expect.withMessage("mocker")
                .that(mocker)
                .isInstanceOf(AdServicesMockerLessExtendedMockitoTestCase.InternalMocker.class);

        expect.withMessage("mocker").that(mocker).isInstanceOf(AdServicesJobMocker.class);
    }
}
