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

import static org.junit.Assert.assertThrows;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import org.junit.Test;

public final class SyncCallbackTimeoutExceptionTest extends SharedSidelessTestCase {

    private static final String WHAT = "Me worry?";
    private static final long TIMEOUT = 42;

    private SyncCallbackTimeoutException mException =
            new SyncCallbackTimeoutException(WHAT, TIMEOUT, MILLISECONDS);

    @Test
    public void testNullConstructor() {
        assertThrows(
                NullPointerException.class,
                () -> new SyncCallbackTimeoutException(WHAT, TIMEOUT, /* unit= */ null));
        assertThrows(
                NullPointerException.class,
                () -> new SyncCallbackTimeoutException(/* what= */ null, TIMEOUT, MILLISECONDS));
    }

    @Test
    public void testCustomGetters() {
        expect.withMessage("what").that(mException.getWhat()).isEqualTo(WHAT);
        expect.withMessage("timeout").that(mException.getTimeout()).isEqualTo(TIMEOUT);
        expect.withMessage("unit").that(mException.getUnit()).isEqualTo(MILLISECONDS);
    }

    @Test
    public void testGetMessage() {
        String expectMsg =
                String.format(
                        SyncCallbackTimeoutException.MSG_TEMPLATE, WHAT, TIMEOUT, MILLISECONDS);

        expect.withMessage("getMessage()").that(mException).hasMessageThat().isEqualTo(expectMsg);
    }
}
