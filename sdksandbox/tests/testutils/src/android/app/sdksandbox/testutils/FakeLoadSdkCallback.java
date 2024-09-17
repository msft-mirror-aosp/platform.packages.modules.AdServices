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
import android.os.OutcomeReceiver;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

public class FakeLoadSdkCallback implements OutcomeReceiver<SandboxedSdk, LoadSdkException> {

    private static final int ZYGOTE_CONTENTION_PERIOD_MS = 5 * 60 * 1000;
    private static final int DEFAULT_WAIT_TIME_SEC = 5;
    private static final int CONTEND_WAIT_TIME_SEC = 30;

    private final WaitableCountDownLatch mLoadSdkLatch;

    private boolean mLoadSdkSuccess;

    private SandboxedSdk mSandboxedSdk;
    private LoadSdkException mLoadSdkException = null;

    /**
     * Calculates default timeout depending on time since device boot.
     *
     * <p>Loading SDK right after device reboot take more time because of zygote contention.
     */
    private static int defaultWaitTimeSec() {
        return SystemClock.uptimeMillis() > ZYGOTE_CONTENTION_PERIOD_MS
                ? DEFAULT_WAIT_TIME_SEC
                : CONTEND_WAIT_TIME_SEC;
    }

    public FakeLoadSdkCallback() {
        mLoadSdkLatch = new WaitableCountDownLatch(defaultWaitTimeSec());
    }

    public FakeLoadSdkCallback(int waitTimeSec) {
        Preconditions.checkArgument(waitTimeSec > 0, "Callback should use a positive wait time");
        mLoadSdkLatch = new WaitableCountDownLatch(waitTimeSec);
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

    public void assertLoadSdkIsUnsuccessful() {
        mLoadSdkLatch.waitForLatch();
        if (mLoadSdkException == null) {
            fail("Load SDK was successful, which was not expected.");
        }
    }

    public void assertLoadSdkIsSuccessful() {
        assertLoadSdkIsSuccessful("Load SDK");
    }

    public void assertLoadSdkIsSuccessful(@Nullable String message) {
        mLoadSdkLatch.waitForLatch(message);
        if (mLoadSdkException != null) {
            fail(
                    message
                            + " was not successful. errorCode: "
                            + this.getLoadSdkErrorCode()
                            + ", errorMsg: "
                            + this.getLoadSdkErrorMsg());
        }
    }

    public int getLoadSdkErrorCode() {
        mLoadSdkLatch.waitForLatch();
        assertThat(mLoadSdkSuccess).isFalse();
        return mLoadSdkException.getLoadSdkErrorCode();
    }

    public String getLoadSdkErrorMsg() {
        mLoadSdkLatch.waitForLatch();
        assertThat(mLoadSdkSuccess).isFalse();
        return mLoadSdkException.getMessage();
    }

    public SandboxedSdk getSandboxedSdk() {
        mLoadSdkLatch.waitForLatch();
        return mSandboxedSdk;
    }

    public LoadSdkException getLoadSdkException() {
        mLoadSdkLatch.waitForLatch();
        return mLoadSdkException;
    }
}