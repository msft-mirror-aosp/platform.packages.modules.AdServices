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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.common.FledgeErrorResponse;
import android.os.RemoteException;

import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

// TODO(b/344677163): there's no result and FailableResultSyncCallback<Void> wouldn't work; if
// more tests need something like this, we should create a FailableOnSuccessSyncCallback class.
final class ReportEventTestCallback
        extends FailableOnResultSyncCallback<Boolean, FledgeErrorResponse>
        implements ReportInteractionCallback {

    ReportEventTestCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    public void onSuccess() throws RemoteException {
        injectResult(Boolean.TRUE);
    }

    void assertSuccess() throws InterruptedException {
        assertResultReceived();
    }

    void assertErrorReceived(int expectedCode, String expectedMessage) throws InterruptedException {
        FledgeErrorResponse response = assertErrorReceived(expectedCode);
        assertWithMessage("error message on %s", response)
                .that(response.getErrorMessage())
                .isEqualTo(expectedMessage);
    }

    FledgeErrorResponse assertErrorReceived(int expectedCode) throws InterruptedException {
        FledgeErrorResponse response = assertFailureReceived();

        assertThat(response).isNotNull();
        assertWithMessage("status code on %s", response)
                .that(response.getStatusCode())
                .isEqualTo(expectedCode);
        return response;
    }
}
