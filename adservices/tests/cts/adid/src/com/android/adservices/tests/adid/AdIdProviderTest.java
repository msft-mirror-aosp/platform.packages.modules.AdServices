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
package com.android.adservices.tests.adid;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdProviderService;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IAdIdProviderService;
import android.adservices.adid.IGetAdIdProviderCallback;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public final class AdIdProviderTest extends AdServicesCtsTestCase {

    private static final String ZEROED_OUT_AD_ID = "00000000-0000-0000-0000-000000000000";
    private static final boolean DEFAULT_IS_LIMIT_AD_TRACKING_ENABLED = false;

    private static final class AdIdProviderServiceProxy extends AdIdProviderService {

        @Override
        public AdId onGetAdId(int clientUid, String clientPackageName) throws IOException {
            return new AdId(ZEROED_OUT_AD_ID, DEFAULT_IS_LIMIT_AD_TRACKING_ENABLED);
        }
    }

    @Test
    public void testAdIdProvider() throws Exception {
        AdIdProviderServiceProxy proxy = new AdIdProviderServiceProxy();

        Intent intent = new Intent();
        intent.setAction(AdIdProviderService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        assertWithMessage("remoteObject").that(remoteObject).isNotNull();

        IAdIdProviderService service = IAdIdProviderService.Stub.asInterface(remoteObject);
        assertWithMessage("service").that(service).isNotNull();

        SyncGetAdIdProviderCallback callback = new SyncGetAdIdProviderCallback();
        service.getAdIdProvider(/* testAppUId */ 0, "testPackageName", callback);

        GetAdIdResult adIdResult = callback.assertResultReceived();
        assertWithMessage("adIdResult").that(adIdResult).isNotNull();
        expect.withMessage("getAdId").that(adIdResult.getAdId()).isEqualTo(ZEROED_OUT_AD_ID);
        expect.withMessage("isLimitedAdTrackingEnabled")
                .that(adIdResult.isLatEnabled())
                .isEqualTo(DEFAULT_IS_LIMIT_AD_TRACKING_ENABLED);
    }

    private static final class SyncGetAdIdProviderCallback
            extends FailableOnResultSyncCallback<GetAdIdResult, String>
            implements IGetAdIdProviderCallback {
        @Override
        public void onError(String errorMessage) {
            onFailure(errorMessage);
        }
    }
}
