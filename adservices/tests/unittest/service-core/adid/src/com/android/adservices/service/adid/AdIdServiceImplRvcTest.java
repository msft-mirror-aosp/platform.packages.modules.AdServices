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

package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import static org.mockito.Mockito.spy;

import android.adservices.adid.GetAdIdParam;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.common.CallerMetadata;
import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.testing.IntFailureSyncCallback;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.util.Clock;

import org.junit.Test;
import org.mockito.Mock;

public final class AdIdServiceImplRvcTest extends AdServicesExtendedMockitoTestCase {
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private final AdServicesLogger mSpyAdServicesLogger = spy(AdServicesLoggerImpl.getInstance());
    private CallerMetadata mCallerMetadata;
    private AdIdWorker mAdIdWorker;
    private GetAdIdParam mRequest;

    @Mock private Clock mMockClock;
    @Mock private Throttler mMockThrottler;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;

    @Test
    @RequiresSdkRange(atMost = RVC)
    public void testGetAdId_onR_invokesCallbackOnError() throws Exception {
        invokeGetAdIdAndVerifyError(mContext, STATUS_ADSERVICES_DISABLED, mRequest);
    }

    private void invokeGetAdIdAndVerifyError(
            Context context, int expectedResultCode, GetAdIdParam request)
            throws InterruptedException {
        SyncIGetAdIdCallback callback = new SyncIGetAdIdCallback(BINDER_CONNECTION_TIMEOUT_MS);

        AdIdServiceImpl adIdServiceImpl =
                new AdIdServiceImpl(
                        context,
                        mAdIdWorker,
                        mSpyAdServicesLogger,
                        mMockClock,
                        mMockFlags,
                        mMockThrottler,
                        mMockAppImportanceFilter);
        adIdServiceImpl.getAdId(request, mCallerMetadata, callback);
        callback.assertFailed(expectedResultCode);
    }

    private static final class SyncIGetAdIdCallback extends IntFailureSyncCallback<GetAdIdResult>
            implements IGetAdIdCallback {

        private SyncIGetAdIdCallback(long timeout) {
            super(timeout);
        }

        @Override
        public void onError(int resultCode) {
            onFailure(resultCode);
        }
    }
}
