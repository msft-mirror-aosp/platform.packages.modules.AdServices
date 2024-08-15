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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;

import com.android.adservices.shared.testing.BroadcastReceiverSyncCallback.ResultBroadcastReceiver;
import com.android.adservices.shared.testing.concurrency.IResultSyncCallbackTestCase;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

// NOTE: this is the only subclass of SyncCallbackTestCase that uses mocks, so
// SyncCallbackTestCase doesn't extend a SharedMockitoTestCase superclass.
// Once more subclasses use them, we should get rid of rule and mock context.
public final class BroadcastReceiverSyncCallbackTest
        extends IResultSyncCallbackTestCase<Intent, BroadcastReceiverSyncCallback> {
    private static final String ACTION = "android.cts.ACTION_";

    private final Map<BroadcastReceiverSyncCallback, ResultBroadcastReceiver>
            mCallBackToReceiverMap = new HashMap<>();
    private final Map<BroadcastReceiverSyncCallback, Intent> mCallBackToIntentMap = new HashMap<>();

    @Rule(order = 10)
    public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    @Mock private Context mMockContext;

    @Test
    public void testInvalidConstructor() {
        assertThrows(NullPointerException.class, () -> new ResultBroadcastReceiver(null));

        assertThrows(NullPointerException.class, () -> new BroadcastReceiverSyncCallback(null, ""));
        assertThrows(
                NullPointerException.class,
                () -> new BroadcastReceiverSyncCallback(mMockContext, null));
    }

    @Override
    protected BroadcastReceiverSyncCallback newCallback(SyncCallbackSettings settings) {
        Intent intent = newResult();
        ArgumentCaptor<ResultBroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(ResultBroadcastReceiver.class);
        when(mMockContext.registerReceiver(
                        receiverCaptor.capture(), any(), eq(Context.RECEIVER_EXPORTED)))
                .thenReturn(intent);

        BroadcastReceiverSyncCallback callback =
                new BroadcastReceiverSyncCallback(mMockContext, intent.getAction(), settings);

        ResultBroadcastReceiver receiver = receiverCaptor.getValue();

        mCallBackToReceiverMap.put(callback, receiver);
        mCallBackToIntentMap.put(callback, intent);

        return callback;
    }

    @Override
    protected Intent newResult() {
        return new Intent(ACTION + getNextUniqueId());
    }

    @Override
    protected String callCallback(BroadcastReceiverSyncCallback callback) {
        ResultBroadcastReceiver receiver = mCallBackToReceiverMap.get(callback);

        Intent intent = mCallBackToIntentMap.get(callback);
        receiver.onReceive(mMockContext, intent);
        return "onReceive(" + intent + ")";
    }

    @Override
    protected boolean providesExpectedConstructors() {
        return false;
    }

    @Override
    protected boolean supportsFailIfCalledOnMainThread() {
        return false;
    }
}
