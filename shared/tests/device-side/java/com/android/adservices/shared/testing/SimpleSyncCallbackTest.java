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

package com.android.adservices.shared.testing;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

public final class SimpleSyncCallbackTest extends SharedUnitTestCase {

    @Test
    public void testCustomMethods() throws Exception {
        SimpleSyncCallback callback = new SimpleSyncCallback();
        expect.withMessage("isCalled() before called").that(callback.isCalled()).isFalse();

        callback.setCalled();

        expect.withMessage("isCalled() after called").that(callback.isCalled()).isTrue();
        callback.assertCalled();
    }

    @Test
    public void testUnsupportedMethods() throws Exception {
        SimpleSyncCallback callback = new SimpleSyncCallback();

        assertThrows(
                UnsupportedOperationException.class, () -> callback.injectResult(new Object()));
        assertThrows(UnsupportedOperationException.class, () -> callback.assertResultReceived());
        assertThrows(UnsupportedOperationException.class, () -> callback.getResultReceived());

        assertThrows(UnsupportedOperationException.class, () -> callback.injectError(null));
        assertThrows(UnsupportedOperationException.class, () -> callback.assertErrorReceived());
        assertThrows(
                UnsupportedOperationException.class,
                () -> callback.assertErrorReceived(Void.class));
        assertThrows(UnsupportedOperationException.class, () -> callback.getErrorReceived());
    }
}
