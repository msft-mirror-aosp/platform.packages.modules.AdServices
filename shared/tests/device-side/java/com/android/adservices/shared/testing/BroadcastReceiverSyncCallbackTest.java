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

import static com.android.adservices.shared.testing.ConcurrencyHelper.runOnMainThread;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.testing.BroadcastReceiverSyncCallback.ResultBroadcastReceiver;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;

import org.junit.Test;

public final class BroadcastReceiverSyncCallbackTest extends SharedMockitoTestCase {
    private static final int TIMEOUT_MS = 200;
    private static final String ACTION = "android.cts.ACTION_1";
    private static final Intent INTENT = new Intent(ACTION);

    private final ResultBroadcastReceiver mReceiver =
            new ResultBroadcastReceiver(
                    SyncCallbackFactory.newSettingsBuilder()
                            .setMaxTimeoutMs(TIMEOUT_MS)
                            .setFailIfCalledOnMainThread(false)
                            .build());

    @Test
    public void testInvalidConstructor() {
        assertThrows(NullPointerException.class, () -> new ResultBroadcastReceiver(null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResultBroadcastReceiver(
                                SyncCallbackFactory.newSettingsBuilder()
                                        .setFailIfCalledOnMainThread(true)
                                        .build()));
    }

    @Test
    public void testPrepare() {
        when(mMockContext.registerReceiver(any(), any(), eq(Context.RECEIVER_EXPORTED)))
                .thenReturn(INTENT);
        BroadcastReceiverSyncCallback callback =
                new BroadcastReceiverSyncCallback(mMockContext, mReceiver);

        callback.prepare(ACTION);

        verify(mMockContext).registerReceiver(any(), any(), eq(Context.RECEIVER_EXPORTED));

        // calling prepare again
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.prepare(ACTION));
        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("BroadcastReceiverCallback.prepared() already called");
    }

    @Test
    public void testAssertResultReceived_prepareNotCalled() {
        BroadcastReceiverSyncCallback callback =
                new BroadcastReceiverSyncCallback(mMockContext, mReceiver);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> callback.assertResultReceived());
        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("Call BroadcastReceiverCallback.prepare() before this method");
    }

    @Test
    public void testAssertResultReceived() throws Exception {
        when(mMockContext.registerReceiver(any(), any(), eq(Context.RECEIVER_EXPORTED)))
                .thenReturn(INTENT);
        BroadcastReceiverSyncCallback callback =
                new BroadcastReceiverSyncCallback(mMockContext, mReceiver);

        callback.prepare(ACTION);

        runOnMainThread(() -> mReceiver.onReceive(mMockContext, INTENT));

        Intent actualResult = callback.assertResultReceived();

        expect.that(actualResult).isEqualTo(INTENT);
        verify(mMockContext).unregisterReceiver(mReceiver);
    }
}
