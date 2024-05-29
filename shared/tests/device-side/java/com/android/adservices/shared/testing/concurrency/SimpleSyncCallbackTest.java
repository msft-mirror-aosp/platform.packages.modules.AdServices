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
package com.android.adservices.shared.testing.concurrency;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.Test;

public final class SimpleSyncCallbackTest extends SharedMockitoTestCase {

    @Test
    public void testGetCalled_singleCallback() {
        SimpleSyncCallback singleCallback = new SimpleSyncCallback();
        expect.withMessage("%s.isCalled() before setCalled()", singleCallback)
                .that(singleCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getNumberActualCalls() before setCalled()", singleCallback)
                .that(singleCallback.getNumberActualCalls())
                .isEqualTo(0);

        singleCallback.setCalled();
        expect.withMessage("%s.isCalled() after setCalled()", singleCallback)
                .that(singleCallback.isCalled())
                .isTrue();
        expect.withMessage("%s.getNumberActualCalls() after 1st setCalled()", singleCallback)
                .that(singleCallback.getNumberActualCalls())
                .isEqualTo(1);

        singleCallback.setCalled();
        expect.withMessage("%s.isCalled() after setCalled()", singleCallback)
                .that(singleCallback.isCalled())
                .isTrue();
        expect.withMessage("%s.getNumberActualCalls() after 2nd setCalled()", singleCallback)
                .that(singleCallback.getNumberActualCalls())
                .isEqualTo(2);
    }

    // TODO(b/337014024): move logic to SyncCallbackTestCase
    @Test
    public void testGetCalled_multipleCallbacks() {
        SimpleSyncCallback multiCallback =
                new SimpleSyncCallback(
                        SyncCallbackFactory.newSettingsBuilder().setExpectedNumberCalls(2).build());
        expect.withMessage("%s.isCalled() before 1st setCalled()", multiCallback)
                .that(multiCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getNumberActualCalls() before 1st setCalled()", multiCallback)
                .that(multiCallback.getNumberActualCalls())
                .isEqualTo(0);

        multiCallback.setCalled();
        expect.withMessage("%s.isCalled() after 1st setCalled()", multiCallback)
                .that(multiCallback.isCalled())
                .isFalse();
        expect.withMessage("%s.getNumberActualCalls() after 1st setCalled()", multiCallback)
                .that(multiCallback.getNumberActualCalls())
                .isEqualTo(1);

        multiCallback.setCalled();
        expect.withMessage("%s.isCalled() after 2nd setCalled()", multiCallback)
                .that(multiCallback.isCalled())
                .isTrue();
        expect.withMessage("%s.getNumberActualCalls() after 2nd setCalled()", multiCallback)
                .that(multiCallback.getNumberActualCalls())
                .isEqualTo(2);

        multiCallback.setCalled();
        expect.withMessage("%s.isCalled() after 3rd setCalled()", multiCallback)
                .that(multiCallback.isCalled())
                .isTrue();
        expect.withMessage("%s.getNumberActualCalls() after 3rd setCalled()", multiCallback)
                .that(multiCallback.getNumberActualCalls())
                .isEqualTo(3);
    }
}
