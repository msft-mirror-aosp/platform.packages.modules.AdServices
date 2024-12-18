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
package com.android.adservices.mockito;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;

import org.junit.Test;

public final class SharedExtendedMockitoTestCaseTest extends SharedExtendedMockitoTestCase {

    @Test
    public void testFixtures() {
        assertWithMessage("mockito").that(extendedMockito).isNotNull();
        assertWithMessage("mocker").that(mocker).isNotNull();
        assertWithMessage("mMockContext").that(mMockContext).isNotNull();
        assertWithMessage("mMockFlags").that(mMockFlags).isNotNull();
    }
}