/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.sdksandbox.testutils;

import android.app.sdksandbox.ISharedPreferencesSyncCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeSharedPreferencesSyncCallback extends ISharedPreferencesSyncCallback.Stub {
    private CountDownLatch mSyncDataLatch = new CountDownLatch(1);

    boolean mOnSuccessCalled = false;

    boolean mOnSandboxStartCalled = false;

    boolean mOnErrorCalled = false;
    private int mErrorCode;
    private String mErrorMsg;

    @Override
    public void onSuccess() {
        mOnSuccessCalled = true;
        mSyncDataLatch.countDown();
    }

    @Override
    public void onSandboxStart() {
        mOnSandboxStartCalled = true;
        mSyncDataLatch.countDown();
    }

    @Override
    public void onError(int errorCode, String errorMsg) {
        mOnErrorCalled = true;
        mErrorCode = errorCode;
        mErrorMsg = errorMsg;
        mSyncDataLatch.countDown();
    }

    public boolean isSuccessful() {
        waitForLatch(mSyncDataLatch);
        return mOnSuccessCalled;
    }

    public boolean hasSandboxStarted() {
        waitForLatch(mSyncDataLatch);
        return mOnSandboxStartCalled;
    }

    public boolean hasError() {
        waitForLatch(mSyncDataLatch);
        return mOnErrorCalled;
    }

    public int getErrorCode() {
        waitForLatch(mSyncDataLatch);
        return mErrorCode;
    }

    public String getErrorMsg() {
        waitForLatch(mSyncDataLatch);
        return mErrorMsg;
    }

    public void resetLatch() {
        mSyncDataLatch = new CountDownLatch(1);
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            // Wait for callback to be called
            final int waitTime = 5;
            if (!latch.await(waitTime, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Callback not called within " + waitTime + " seconds");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(
                    "Interrupted while waiting on callback: " + e.getMessage());
        }
    }
}
