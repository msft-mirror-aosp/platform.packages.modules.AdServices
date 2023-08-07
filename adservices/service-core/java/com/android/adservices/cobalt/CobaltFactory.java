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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.cobalt.CobaltLogger;
import com.android.cobalt.CobaltPeriodicJob;
import com.android.cobalt.CobaltPipelineType;
import com.android.cobalt.crypto.HpkeEncrypter;
import com.android.cobalt.data.DataService;
import com.android.cobalt.impl.CobaltLoggerImpl;
import com.android.cobalt.impl.CobaltPeriodicJobImpl;
import com.android.cobalt.observations.PrivacyGenerator;
import com.android.cobalt.system.SystemClockImpl;
import com.android.cobalt.system.SystemData;
import com.android.cobalt.upload.Uploader;

import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.EncryptedMessage;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Factory for Cobalt's logger and periodic job implementations. */
public final class CobaltFactory {
    /*
     * Use the prod pipeline because AdServices' reports are for either the DEBUG or GA release
     * stage and DEBUG is sufficient for local testing.
     */
    private static final CobaltPipelineType PIPELINE_TYPE = CobaltPipelineType.PROD;

    // Objects which are non-trivial to construct or need to be shared between the logger and
    // periodic job are static.
    private static CobaltRegistry sSingletonCobaltRegistry;
    private static DataService sSingletonDataService;
    private static SecureRandom sSingletonSecureRandom;

    private static CobaltLogger sSingletonCobaltLogger;
    private static CobaltPeriodicJob sSingletonCobaltPeriodicJob;

    /**
     * Returns the static singleton CobaltLogger.
     *
     * @throws CobaltInitializationException if an unrecoverable errors occurs during initialization
     */
    @NonNull
    public static CobaltLogger getCobaltLogger(@NonNull Context context, @NonNull Flags flags)
            throws CobaltInitializationException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        synchronized (CobaltFactory.class) {
            if (sSingletonCobaltLogger == null) {
                sSingletonCobaltLogger =
                        sSingletonCobaltLogger =
                                new CobaltLoggerImpl(
                                        getRegistry(),
                                        CobaltReleaseStages.getReleaseStage(
                                                flags.getAdservicesReleaseStageForCobalt()),
                                        getDataService(context),
                                        new SystemData(),
                                        getExecutor(),
                                        new SystemClockImpl(),
                                        flags.getTopicsCobaltLoggingEnabled());
            }
            return sSingletonCobaltLogger;
        }
    }

    /**
     * Returns the static singleton CobaltPeriodicJob.
     *
     * <p>Note, this implementation does not result in any data being uploaded because the upload
     * API does not exist yet and the actual uploader is blocked on it landing.
     *
     * @throws CobaltInitializationException if an unrecoverable errors occurs during initialization
     */
    @NonNull
    public static CobaltPeriodicJob getCobaltPeriodicJob(
            @NonNull Context context, @NonNull Flags flags) throws CobaltInitializationException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        synchronized (CobaltFactory.class) {
            if (sSingletonCobaltPeriodicJob == null) {
                sSingletonCobaltPeriodicJob =
                        new CobaltPeriodicJobImpl(
                                getRegistry(),
                                CobaltReleaseStages.getReleaseStage(
                                        flags.getAdservicesReleaseStageForCobalt()),
                                getDataService(context),
                                getExecutor(),
                                new SystemClockImpl(),
                                new SystemData(),
                                new PrivacyGenerator(getSecureRandom()),
                                getSecureRandom(),
                                getNoOpUploader(),
                                HpkeEncrypter.createForEnvironment(
                                        new HpkeEncryptImpl(), PIPELINE_TYPE),
                                CobaltApiKeys.copyFromHexApiKey(
                                        flags.getCobaltAdservicesApiKeyHex()),
                                flags.getTopicsCobaltLoggingEnabled());
            }
            return sSingletonCobaltPeriodicJob;
        }
    }

    @NonNull
    private static ExecutorService getExecutor() {
        // Cobalt requires disk I/O and must run on the background executor.
        return AdServicesExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static CobaltRegistry getRegistry() throws CobaltInitializationException {
        if (sSingletonCobaltRegistry == null) {
            sSingletonCobaltRegistry = CobaltRegistryLoader.getRegistry();
        }
        return sSingletonCobaltRegistry;
    }

    @NonNull
    private static DataService getDataService(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (sSingletonDataService == null) {
            sSingletonDataService =
                    CobaltDataServiceFactory.createDataService(context, getExecutor());
        }

        return sSingletonDataService;
    }

    @NonNull
    private static SecureRandom getSecureRandom() {
        if (sSingletonSecureRandom == null) {
            sSingletonSecureRandom = new SecureRandom();
        }

        return sSingletonSecureRandom;
    }

    @NonNull
    private static Uploader getNoOpUploader() {
        return new Uploader() {
            @Override
            public void upload(EncryptedMessage encryptedMessage) {
                Log.w(
                        CobaltFactory.class.getSimpleName(),
                        "Dropping encrypted message while upload is unimplemented");
            }
        };
    }
}
