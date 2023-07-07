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
package com.android.adservices.cobalt;

import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_DEV;
import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_PROD;

import static com.android.adservices.AdServicesCommon.ACTION_AD_SERVICES_COBALT_UPLOAD_SERVICE;

import android.adservices.cobalt.EncryptedCobaltEnvelopeParams;
import android.adservices.cobalt.IAdServicesCobaltUploadService;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.android.adservices.ServiceBinder;
import com.android.cobalt.CobaltPipelineType;
import com.android.cobalt.upload.Uploader;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.EncryptedMessage;

/** Cobalt uploader for sending data via AdServices' upload protocol. */
final class CobaltUploader implements Uploader {
    private static final String TAG = CobaltUploader.class.getSimpleName();

    private final Context mContext;
    private final ServiceBinder<IAdServicesCobaltUploadService> mServiceBinder;
    private final int mEnvironment;

    CobaltUploader(Context context, CobaltPipelineType pipelineType) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        ACTION_AD_SERVICES_COBALT_UPLOAD_SERVICE,
                        IAdServicesCobaltUploadService.Stub::asInterface);
        mEnvironment =
                (pipelineType == CobaltPipelineType.DEV) ? ENVIRONMENT_DEV : ENVIRONMENT_PROD;
    }

    @Override
    public void upload(EncryptedMessage encryptedMessage) {
        IAdServicesCobaltUploadService service = getService();
        if (service == null) {
            Log.w(TAG, "Unable to find Cobalt upload service, message will be dropped");
            return;
        }

        try {
            service.uploadEncryptedCobaltEnvelope(
                    new EncryptedCobaltEnvelopeParams(
                            mEnvironment,
                            encryptedMessage.getKeyIndex(),
                            encryptedMessage.getCiphertext().toByteArray()));
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception while sending message, will be dropped", e);
        }
    }

    @VisibleForTesting
    IAdServicesCobaltUploadService getService() {
        return mServiceBinder.getService();
    }
}
