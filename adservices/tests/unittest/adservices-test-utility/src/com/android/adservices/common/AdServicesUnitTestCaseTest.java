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

import static com.android.adservices.shared.testing.mockito.MockitoHelper.isMock;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public final class AdServicesUnitTestCaseTest extends AdServicesUnitTestCase {

    @Test
    public void testAppContext() {
        assertWithMessage("appContext").that(appContext).isNotNull();
        assertWithMessage("appContext.get()")
                .that(appContext.get())
                .isEqualTo(mContext.getApplicationContext());
    }

    @Test
    public void testMAppContext() {
        assertWithMessage("mAppContext")
                .that(mAppContext)
                .isEqualTo(mContext.getApplicationContext());
    }

    @Test
    public void testFlagsRelatedAttributes() {
        assertWithMessage("flags").that(flags).isNotNull();
        expect.withMessage("flags.getFlags()").that(flags.getFlags()).isNotNull();

        assertWithMessage("mMockFlags").that(mMockFlags).isNotNull();
        expect.withMessage("mMockFlags is a mock").that(isMock(mMockFlags)).isTrue();
    }
}
