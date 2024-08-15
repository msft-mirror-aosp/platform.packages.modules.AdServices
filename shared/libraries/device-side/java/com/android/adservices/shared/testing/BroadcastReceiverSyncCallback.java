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

import static com.android.adservices.shared.testing.concurrency.SyncCallbackSettings.DEFAULT_TIMEOUT_MS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Helper used to block util a broadcast is received.
 *
 * <p>Caller passes the action as part of the constructor which registers the receiver for the
 * broadcast. Then caller calls {@link #assertResultReceived()} to assert the expected outcome.
 */
public final class BroadcastReceiverSyncCallback extends ResultSyncCallback<Intent> {

    private static final String TAG = BroadcastReceiverSyncCallback.class.getSimpleName();

    private final Context mContext;
    private final ResultBroadcastReceiver mReceiver;
    private final String mAction;

    public BroadcastReceiverSyncCallback(Context context, String action) {
        this(context, action, DEFAULT_TIMEOUT_MS);
    }

    public BroadcastReceiverSyncCallback(Context context, String action, long timeoutMs) {
        this(
                context,
                action,
                SyncCallbackFactory.newSettingsBuilder()
                        .setMaxTimeoutMs(timeoutMs)
                        .setFailIfCalledOnMainThread(false)
                        .build());
    }

    public BroadcastReceiverSyncCallback(
            Context context, String action, SyncCallbackSettings settings) {
        super(SyncCallbackSettings.checkCanFailOnMainThread(settings));

        mContext = Objects.requireNonNull(context);
        mAction = Objects.requireNonNull(action);
        mReceiver = new ResultBroadcastReceiver(this);

        IntentFilter filter = new IntentFilter(action);
        Log.d(TAG, "Registering receiver for action: " + mAction);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    /**
     * Asserts that broadcast is received, waiting up to timeout milliseconds before failing (if not
     * received).
     *
     * <p>Also, unregisters the receiver.
     *
     * @return the intent received from the broadcast.
     */
    @Override
    public Intent assertResultReceived() throws InterruptedException {
        try {
            return super.assertResultReceived();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        Log.d(TAG, "Unregistering receiver for action: " + mAction);
        mContext.unregisterReceiver(mReceiver);
    }

    @VisibleForTesting
    static class ResultBroadcastReceiver extends BroadcastReceiver {
        private final BroadcastReceiverSyncCallback mSyncCallback;

        ResultBroadcastReceiver(BroadcastReceiverSyncCallback syncCallback) {
            mSyncCallback = Objects.requireNonNull(syncCallback);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Broadcast received with intent: " + intent);
            mSyncCallback.internalInjectResult("onReceive", intent);
        }
    }
}
