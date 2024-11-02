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
package com.android.adservices.tests.extdata;

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.AdServicesExtDataStorageService;
import android.adservices.extdata.GetAdServicesExtDataResult;
import android.adservices.extdata.IAdServicesExtDataStorageService;
import android.adservices.extdata.IGetAdServicesExtDataCallback;
import android.content.Intent;
import android.os.IBinder;

import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;

import org.junit.Test;

public final class AdServicesExtDataServiceCtsTest {
    /** Fake AdServicesExtDataStorageService implementation for testing. */
    private static final class AdServicesExtDataStorageServiceTestProxy
            extends AdServicesExtDataStorageService {

        private AdServicesExtDataParams mParamsStorage = null;

        public AdServicesExtDataParams onGetAdServicesExtData() {
            return mParamsStorage;
        }

        public void onPutAdServicesExtData(
                AdServicesExtDataParams adServicesExtDataParams, int[] adServicesExtDataFields) {
            // For testing, ignore adServicesExtDataFields. Update entire object.
            mParamsStorage = adServicesExtDataParams;
        }
    }

    @Test
    public void testAdServicesExtDataStorageService() throws Exception {
        // Check service connection
        AdServicesExtDataStorageServiceTestProxy proxy =
                new AdServicesExtDataStorageServiceTestProxy();
        Intent intent = new Intent();
        intent.setAction(AdServicesExtDataStorageService.SERVICE_INTERFACE);
        IBinder remoteObject = proxy.onBind(intent);
        assertThat(remoteObject).isNotNull();

        IAdServicesExtDataStorageService service =
                IAdServicesExtDataStorageService.Stub.asInterface(remoteObject);
        assertThat(service).isNotNull();

        // Update AdExt data
        AdServicesExtDataParams paramsToUpdate =
                new AdServicesExtDataParams(
                        /*isNotificationDisplayed=*/ BOOLEAN_TRUE,
                        /*isMeasurementConsented=*/ BOOLEAN_FALSE,
                        /*isU18Account=*/ BOOLEAN_TRUE,
                        /*isAdultAccount=*/ BOOLEAN_FALSE,
                        /*manualInteractionWithConsentStatus=*/ STATE_UNKNOWN,
                        /*measurementRollbackApexVersion=*/ 200L);

        SyncAdExtTestCallback putReceiver = new SyncAdExtTestCallback();
        service.putAdServicesExtData(
                paramsToUpdate,
                new int[] {}, // Fake service implementation ignores this parameter.
                putReceiver);

        putReceiver.assertFailureReceived();

        // Get updated AdExt data
        SyncAdExtTestCallback getReceiver = new SyncAdExtTestCallback();
        service.getAdServicesExtData(getReceiver);

        String error = getReceiver.assertFailureReceived();
        assertThat(error).isNotEmpty();
    }

    @Test
    public void testAdServicesExtDataParams() {
        AdServicesExtDataParams adServicesExtDataParams =
                new AdServicesExtDataParams(
                        /* isNotificationDisplayed= */ BOOLEAN_TRUE,
                        /* isMeasurementConsented= */ BOOLEAN_FALSE,
                        /* isU18Account= */ BOOLEAN_TRUE,
                        /* isAdultAccount= */ BOOLEAN_FALSE,
                        /* manualInteractionWithConsentStatus= */ STATE_UNKNOWN,
                        /* measurementRollbackApexVersion= */ 200L);

        assertThat(adServicesExtDataParams.getIsNotificationDisplayed()).isEqualTo(BOOLEAN_TRUE);
        assertThat(adServicesExtDataParams.getIsMeasurementConsented()).isEqualTo(BOOLEAN_FALSE);
        assertThat(adServicesExtDataParams.getIsU18Account()).isEqualTo(BOOLEAN_TRUE);
        assertThat(adServicesExtDataParams.getIsAdultAccount()).isEqualTo(BOOLEAN_FALSE);
        assertThat(adServicesExtDataParams.getManualInteractionWithConsentStatus())
                .isEqualTo(STATE_UNKNOWN);
        assertThat(adServicesExtDataParams.getMeasurementRollbackApexVersion()).isEqualTo(200L);
    }

    private static final class SyncAdExtTestCallback
            extends FailableOnResultSyncCallback<GetAdServicesExtDataResult, String>
            implements IGetAdServicesExtDataCallback {
        @Override
        public void onError(String msg) {
            onFailure(msg);
        }
    }
}
