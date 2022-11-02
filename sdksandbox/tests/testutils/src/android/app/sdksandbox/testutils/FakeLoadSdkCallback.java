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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.os.OutcomeReceiver;

import com.google.common.base.Preconditions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeLoadSdkCallback implements OutcomeReceiver<SandboxedSdk, LoadSdkException> {
    private final CountDownLatch mLoadSdkLatch = new CountDownLatch(1);

    private boolean mLoadSdkSuccess;

    private SandboxedSdk mSandboxedSdk;
    private LoadSdkException mLoadSdkException = null;
    private final int mWaitTimeSec;

    public FakeLoadSdkCallback() {
        mWaitTimeSec = 5;
    }

    public FakeLoadSdkCallback(int waitTimeSec) {
        Preconditions.checkArgument(waitTimeSec > 0, "Callback should use a positive wait time");
        mWaitTimeSec = waitTimeSec;
    }

    @Override
    public void onResult(SandboxedSdk sandboxedSdk) {
        mLoadSdkSuccess = true;
        mSandboxedSdk = sandboxedSdk;
        mLoadSdkLatch.countDown();
    }

    @Override
    public void onError(LoadSdkException exception) {
        mLoadSdkSuccess = false;
        mLoadSdkException = exception;
        mLoadSdkLatch.countDown();
    }

    public boolean isLoadSdkSuccessful() {
        return isLoadSdkSuccessful(false);
    }

    public boolean isLoadSdkSuccessful(boolean ignoreSdkAlreadyLoadedError) {
        waitForLatch(mLoadSdkLatch);
        if (ignoreSdkAlreadyLoadedError
                && ((mLoadSdkException == null)
                        || (mLoadSdkException.getLoadSdkErrorCode()
                                == SdkSandboxManager.LOAD_SDK_ALREADY_LOADED))) {
            mLoadSdkSuccess = true;
        }
        return mLoadSdkSuccess;
    }

    public void assertLoadSdkIsSuccessful() {
        if (!this.isLoadSdkSuccessful()) {
            fail(
                    "Load SDK was not successful. errorCode: "
                            + this.getLoadSdkErrorCode()
                            + ", errorMsg: "
                            + this.getLoadSdkErrorMsg());
        }
    }

    public int getLoadSdkErrorCode() {
        waitForLatch(mLoadSdkLatch);
        assertThat(mLoadSdkSuccess).isFalse();
        return mLoadSdkException.getLoadSdkErrorCode();
    }

    public String getLoadSdkErrorMsg() {
        waitForLatch(mLoadSdkLatch);
        assertThat(mLoadSdkSuccess).isFalse();
        return mLoadSdkException.getMessage();
    }

    public SandboxedSdk getSandboxedSdk() {
        waitForLatch(mLoadSdkLatch);
        return mSandboxedSdk;
    }

    public LoadSdkException getLoadSdkException() {
        waitForLatch(mLoadSdkLatch);
        return mLoadSdkException;
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            // Wait for callback to be called
            if (!latch.await(mWaitTimeSec, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Callback not called within " + mWaitTimeSec + " seconds");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(
                    "Interrupted while waiting on callback: " + e.getMessage());
        }
    }
}
