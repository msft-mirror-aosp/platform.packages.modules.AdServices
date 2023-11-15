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

package com.android.adservices.service.extdata;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_MEASUREMENT_CONSENTED;
import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_IS_NOTIFICATION_DISPLAYED;

import static org.mockito.Mockito.times;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.GetAdServicesExtDataResult;
import android.adservices.extdata.IAdServicesExtDataStorageService;
import android.adservices.extdata.IGetAdServicesExtDataCallback;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

public class AdServicesExtDataStorageServiceWorkerTest {
    private static final String TEST_EXCEPTION_MSG = "Test exception thrown!";
    private static final String NO_SERVICE_EXCEPTION_MESSAGE =
            "Unable to find AdServicesExtDataStorageService!";
    private static final AdServicesExtDataParams TEST_PARAMS =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(1)
                    .setMsmtConsent(0)
                    .setIsU18Account(1)
                    .setIsAdultAccount(0)
                    .setManualInteractionWithConsentStatus(-1)
                    .setMsmtRollbackApexVersion(-1)
                    .build();
    private static final int[] TEST_FIELD_LIST = {
        FIELD_IS_MEASUREMENT_CONSENTED, FIELD_IS_NOTIFICATION_DISPLAYED
    };

    private boolean mIsSuccess;
    private AdServicesExtDataStorageServiceWorker mSpyWorker;

    @Before
    public void setup() {
        mSpyWorker =
                Mockito.spy(
                        AdServicesExtDataStorageServiceWorker.getInstance(
                                ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void testGetAdServicesExtData_serviceNotFound_resultsInOnErrorSet() throws Exception {
        Mockito.doReturn(null).when(mSpyWorker).getService();

        CompletableFuture<String> future = new CompletableFuture<>();
        mSpyWorker.getAdServicesExtData(constructFailureCallback(future));

        Assert.assertEquals(NO_SERVICE_EXCEPTION_MESSAGE, future.get());
        Mockito.verify(mSpyWorker, times(0)).unbindFromService();
    }

    @Test
    public void testGetAdServicesExtData_onResultSet() throws Exception {
        Mockito.doReturn(mService).when(mSpyWorker).getService();

        mIsSuccess = true;
        CompletableFuture<AdServicesExtDataParams> future = new CompletableFuture<>();
        mSpyWorker.getAdServicesExtData(
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdServicesExtDataParams result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                });

        Assert.assertEquals(TEST_PARAMS, future.get());
        Mockito.verify(mSpyWorker, times(1)).unbindFromService();
    }

    @Test
    public void testGetAdServicesExtData_onErrorSet() throws Exception {
        Mockito.doReturn(mService).when(mSpyWorker).getService();

        mIsSuccess = false;
        CompletableFuture<String> future = new CompletableFuture<>();
        mSpyWorker.getAdServicesExtData(constructFailureCallback(future));

        Assert.assertEquals(TEST_EXCEPTION_MSG, future.get());
        Mockito.verify(mSpyWorker, times(1)).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_serviceNotFound_resultsInOnErrorSet() throws Exception {
        Mockito.doReturn(null).when(mSpyWorker).getService();

        CompletableFuture<String> future = new CompletableFuture<>();
        mSpyWorker.setAdServicesExtData(
                TEST_PARAMS, TEST_FIELD_LIST, constructFailureCallback(future));

        Assert.assertEquals(NO_SERVICE_EXCEPTION_MESSAGE, future.get());
        Mockito.verify(mSpyWorker, times(0)).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_onResultSet() throws Exception {
        Mockito.doReturn(mService).when(mSpyWorker).getService();

        mIsSuccess = true;
        CompletableFuture<AdServicesExtDataParams> future = new CompletableFuture<>();
        mSpyWorker.setAdServicesExtData(
                TEST_PARAMS, TEST_FIELD_LIST, constructSuccessCallback(future));

        Assert.assertEquals(TEST_PARAMS, future.get());
        Mockito.verify(mSpyWorker, times(1)).unbindFromService();
    }

    @Test
    public void testSetAdServicesExtData_onErrorSet() throws Exception {
        Mockito.doReturn(mService).when(mSpyWorker).getService();

        mIsSuccess = false;
        CompletableFuture<String> future = new CompletableFuture<>();
        mSpyWorker.setAdServicesExtData(
                TEST_PARAMS, TEST_FIELD_LIST, constructFailureCallback(future));

        Assert.assertEquals(TEST_EXCEPTION_MSG, future.get());
        Mockito.verify(mSpyWorker, times(1)).unbindFromService();
    }

    private AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> constructSuccessCallback(
            CompletableFuture<AdServicesExtDataParams> future) {
        return new AdServicesOutcomeReceiver<>() {
            @Override
            public void onResult(AdServicesExtDataParams result) {
                future.complete(result);
            }

            @Override
            public void onError(Exception error) {
                Assert.fail();
            }
        };
    }

    private AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> constructFailureCallback(
            CompletableFuture<String> future) {
        return new AdServicesOutcomeReceiver<>() {
            @Override
            public void onResult(AdServicesExtDataParams result) {
                Assert.fail();
            }

            @Override
            public void onError(Exception e) {
                future.complete(e.getMessage());
            }
        };
    }

    private final IAdServicesExtDataStorageService mService =
            new IAdServicesExtDataStorageService.Stub() {
                @Override
                public void getAdServicesExtData(IGetAdServicesExtDataCallback callback)
                        throws RemoteException {
                    try {
                        if (mIsSuccess) {
                            GetAdServicesExtDataResult result =
                                    new GetAdServicesExtDataResult.Builder()
                                            .setStatusCode(STATUS_SUCCESS)
                                            .setErrorMessage("")
                                            .setAdServicesExtDataParams(TEST_PARAMS)
                                            .build();
                            callback.onResult(result);
                        } else {
                            throw new Exception(TEST_EXCEPTION_MSG);
                        }
                    } catch (Throwable e) {
                        callback.onError(e.getMessage());
                    }
                }

                @Override
                public void putAdServicesExtData(
                        AdServicesExtDataParams params,
                        int[] fields,
                        IGetAdServicesExtDataCallback callback)
                        throws RemoteException {
                    try {
                        if (mIsSuccess) {
                            GetAdServicesExtDataResult result =
                                    new GetAdServicesExtDataResult.Builder()
                                            .setStatusCode(STATUS_SUCCESS)
                                            .setErrorMessage("")
                                            .setAdServicesExtDataParams(params)
                                            .build();
                            callback.onResult(result);
                        } else {
                            throw new Exception(TEST_EXCEPTION_MSG);
                        }
                    } catch (Throwable e) {
                        callback.onError(e.getMessage());
                    }
                }
            };
}
