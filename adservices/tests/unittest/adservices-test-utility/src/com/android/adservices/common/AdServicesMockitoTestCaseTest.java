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

import static com.android.adservices.shared.testing.mockito.MockitoHelper.getSpiedInstance;
import static com.android.adservices.shared.testing.mockito.MockitoHelper.isMock;
import static com.android.adservices.shared.testing.mockito.MockitoHelper.isSpy;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.mockito.AndroidMocker;

import org.junit.Test;

public final class AdServicesMockitoTestCaseTest extends AdServicesMockitoTestCase {

    @Test
    public void testMockContext() {
        assertWithMessage("mMockContext").that(mMockContext).isNotNull();
        expect.withMessage("mMockContext is a mock").that(isMock(mMockContext)).isTrue();
        expect.withMessage("mMockContext is a spy").that(isSpy(mMockContext)).isFalse();
    }

    @Test
    public void testSpyContext() {
        expect.withMessage("mSpyContext").that(mSpyContext).isNotNull();
        expect.withMessage("mSpyContext is a mock").that(isMock(mSpyContext)).isTrue();
        assertWithMessage("mSpyContext is a spy").that(isSpy(mSpyContext)).isTrue();
        expect.withMessage("spied context")
                .that(getSpiedInstance(mSpyContext))
                .isSameInstanceAs(mContext);
    }

    @Test
    public void testMockFlags() {
        expect.withMessage("mMockFlags").that(mMockFlags).isNotNull();
        expect.withMessage("mMockDebugFlags").that(mMockDebugFlags).isNotNull();
    }

    @Test
    public void testMocker() {
        assertWithMessage("mocker").that(mocker).isNotNull();
        expect.withMessage("mocker").that(mocker).isInstanceOf(AndroidMocker.class);
    }
}
