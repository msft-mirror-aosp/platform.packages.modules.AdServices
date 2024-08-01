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
package com.android.adservices.tests.appsetid;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdProviderService;
import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IAppSetIdProviderService;
import android.adservices.appsetid.IGetAppSetIdProviderCallback;
import android.content.Intent;
import android.os.IBinder;

import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;

import org.junit.Test;

public final class AppSetIdProviderTest extends CtsAppSetIdEndToEndTestCase {

    private static final String DEFAULT_APP_SET_ID = "00000000-0000-0000-0000-000000000000";
    private static final int DEFAULT_SCOPE = 1;

    private static final class AppSetIdProviderServiceProxy extends AppSetIdProviderService {
        @Override
        public AppSetId onGetAppSetId(int clientUid, String clientPackageName) {
            return new AppSetId(DEFAULT_APP_SET_ID, DEFAULT_SCOPE);
        }
    }

    @Test
    public void testAppSetIdProvider() throws Exception {
        AppSetIdProviderServiceProxy proxy = new AppSetIdProviderServiceProxy();

        Intent intent = new Intent();
        intent.setAction(AppSetIdProviderService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        assertWithMessage("remoteObject").that(remoteObject).isNotNull();

        IAppSetIdProviderService service = IAppSetIdProviderService.Stub.asInterface(remoteObject);
        assertWithMessage("service").that(service).isNotNull();

        SyncGetAppSetIdProviderCallback callback = new SyncGetAppSetIdProviderCallback();
        service.getAppSetId(/* testAppUId */ 0, "testPackageName", callback);
        GetAppSetIdResult appSetIdResult = callback.assertResultReceived();
        assertWithMessage("appSetIdResult").that(appSetIdResult).isNotNull();
        expect.that(appSetIdResult.getAppSetId()).isEqualTo(DEFAULT_APP_SET_ID);
        expect.that(appSetIdResult.getAppSetIdScope()).isEqualTo(DEFAULT_SCOPE);
    }

    private static final class SyncGetAppSetIdProviderCallback
            extends FailableOnResultSyncCallback<GetAppSetIdResult, String>
            implements IGetAppSetIdProviderCallback {
        @Override
        public void onError(String errorMessage) {
            onFailure(errorMessage);
        }
    }
}
