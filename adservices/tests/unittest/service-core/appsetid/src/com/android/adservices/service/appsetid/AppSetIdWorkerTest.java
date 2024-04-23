/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.appsetid;

import static android.adservices.appsetid.GetAppSetIdResult.SCOPE_APP;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IAppSetIdProviderService;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.appsetid.IGetAppSetIdProviderCallback;
import android.os.RemoteException;
import android.util.Log;

import com.android.adservices.FakeServiceBinder;
import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

/** Unit test for {@link com.android.adservices.service.appsetid.AppSetIdWorker}. */
public final class AppSetIdWorkerTest extends AdServicesUnitTestCase {

    private static final String PGK_NAME = "testPackageName";
    private static final int UID = 42;
    private static final String DEFAULT_APP_SET_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    public void testGetAppSetIdOnResult() throws Exception {
        CompletableFuture<GetAppSetIdResult> future = new CompletableFuture<>();
        AppSetIdWorker worker = newAppSetIdWorker(/* forSuccess= */ true);

        worker.getAppSetId(
                PGK_NAME,
                UID,
                new IGetAppSetIdCallback.Stub() {
                    @Override
                    public void onResult(GetAppSetIdResult resultParcel) {
                        Log.d(mTag, "onResult(): " + resultParcel);
                        future.complete(resultParcel);
                    }

                    @Override
                    public void onError(int resultCode) {
                        Log.d(mTag, "onError(): " + resultCode);
                        // should never be called.
                        fail("onError() called: " + resultCode);
                    }
                });
        GetAppSetIdResult result = future.get();

        assertWithMessage("result of getAppSetId()").that(result).isNotNull();
        expect.withMessage("result.getAppSetId()")
                .that(result.getAppSetId())
                .isEqualTo(DEFAULT_APP_SET_ID);
        expect.withMessage("result.getAppSetIdScope()")
                .that(result.getAppSetIdScope())
                .isEqualTo(SCOPE_APP);
    }

    @Test
    public void testGetAppSetIdOnError() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AppSetIdWorker worker = newAppSetIdWorker(/* forSuccess= */ false);

        worker.getAppSetId(
                PGK_NAME,
                UID,
                new IGetAppSetIdCallback.Stub() {
                    @Override
                    public void onResult(GetAppSetIdResult resultParcel) {
                        Log.d(mTag, "onResult(): " + resultParcel);
                        // should never be called.
                        fail("onResult called: " + resultParcel);
                    }

                    @Override
                    public void onError(int resultCode) {
                        Log.d(mTag, "onError(): " + resultCode);
                        future.complete(resultCode);
                    }
                });

        int result = future.get();
        expect.withMessage("result of getAppSetId()").that(result).isEqualTo(STATUS_INTERNAL_ERROR);
    }

    private AppSetIdWorker newAppSetIdWorker(boolean forSuccess) {
        IAppSetIdProviderService service =
                new IAppSetIdProviderService.Stub() {
                    @Override
                    public void getAppSetId(
                            int appUID,
                            String packageName,
                            IGetAppSetIdProviderCallback resultCallback)
                            throws RemoteException {
                        if (forSuccess) {
                            GetAppSetIdResult appSetIdInternal =
                                    new GetAppSetIdResult.Builder()
                                            .setStatusCode(STATUS_SUCCESS)
                                            .setErrorMessage("")
                                            .setAppSetId(DEFAULT_APP_SET_ID)
                                            .setAppSetIdScope(SCOPE_APP)
                                            .build();
                            resultCallback.onResult(appSetIdInternal);
                        } else {
                            resultCallback.onError("testOnError");
                        }
                    }
                };
        return new AppSetIdWorker(new FakeServiceBinder<>(service));
    }
}
