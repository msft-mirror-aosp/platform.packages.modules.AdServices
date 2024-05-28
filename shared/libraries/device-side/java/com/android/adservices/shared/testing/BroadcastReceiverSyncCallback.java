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
import com.android.adservices.shared.util.Preconditions;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Helper used to block util a broadcast is received.
 *
 * <p>Caller typically calls {@link #prepare(String)} to pass the action as argument which registers
 * the receiver for the broadcast. Then caller calls {@link #assertResultReceived()} to assert the
 * expected outcome.
 */
public final class BroadcastReceiverSyncCallback {

    private static final String TAG = BroadcastReceiverSyncCallback.class.getSimpleName();

    private final Context mContext;
    private final ResultBroadcastReceiver mReceiver;
    private String mAction;

    public BroadcastReceiverSyncCallback(Context context) {
        this(context, DEFAULT_TIMEOUT_MS);
    }

    public BroadcastReceiverSyncCallback(Context context, long timeoutMs) {
        this(context, new ResultBroadcastReceiver(timeoutMs));
    }

    @VisibleForTesting
    BroadcastReceiverSyncCallback(Context context, ResultBroadcastReceiver receiver) {
        mContext = context;
        mReceiver = receiver;
    }

    /**
     * Registers receiver for the action.
     *
     * @param action The action to match, such as Intent.ACTION_MAIN.
     */
    public void prepare(String action) {
        Preconditions.checkState(
                mAction == null, "BroadcastReceiverCallback.prepared() already called");
        mAction = action;
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
    public Intent assertResultReceived() throws InterruptedException {
        Preconditions.checkState(
                mAction != null, "Call BroadcastReceiverCallback.prepare() before this method");
        try {
            return mReceiver.assertResultReceived();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (mAction == null) {
            return;
        }
        Log.d(TAG, "Unregistering receiver for action: " + mAction);
        mContext.unregisterReceiver(mReceiver);
        mAction = null;
    }

    @VisibleForTesting
    static class ResultBroadcastReceiver extends BroadcastReceiver {
        private final ResultSyncCallback<Intent> mSyncCallback;

        ResultBroadcastReceiver(long timeoutMs) {
            mSyncCallback =
                    new ResultSyncCallback<>(
                            SyncCallbackFactory.newSettingsBuilder()
                                    .setMaxTimeoutMs(timeoutMs)
                                    .setFailIfCalledOnMainThread(false)
                                    .build());
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Broadcast received with intent: " + intent);
            mSyncCallback.injectResult(intent);
        }

        public Intent assertResultReceived() throws InterruptedException {
            return mSyncCallback.assertResultReceived();
        }
    }
}
