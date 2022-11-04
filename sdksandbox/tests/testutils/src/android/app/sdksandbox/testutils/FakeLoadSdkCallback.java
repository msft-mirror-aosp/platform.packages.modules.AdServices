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

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.os.OutcomeReceiver;

import com.google.common.base.Preconditions;

public class FakeLoadSdkCallback implements OutcomeReceiver<SandboxedSdk, LoadSdkException> {
    private final WaitableCountDownLatch mLoadSdkLatch;

    private boolean mLoadSdkSuccess;

    private SandboxedSdk mSandboxedSdk;
    private LoadSdkException mLoadSdkException = null;

    public FakeLoadSdkCallback() {
        mLoadSdkLatch = new WaitableCountDownLatch(5);
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

    public boolean isLoadSdkSuccessful() {
        return isLoadSdkSuccessful(false);
    }

    public boolean isLoadSdkSuccessful(boolean ignoreSdkAlreadyLoadedError) {
        mLoadSdkLatch.waitForLatch();
        if (ignoreSdkAlreadyLoadedError
                && ((mLoadSdkException == null)
                        || (mLoadSdkException.getLoadSdkErrorCode()
                                == SdkSandboxManager.LOAD_SDK_ALREADY_LOADED))) {
            mLoadSdkSuccess = true;
        }
        return mLoadSdkSuccess;
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
