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

package com.android.adservices.service.kanon;

import static com.android.adservices.service.common.httpclient.AdServicesHttpsClient.DEFAULT_MAX_BYTES;

import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptorFactory;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import java.time.Clock;

/** A factory class used to create an instance of {@link KAnonSignJoinManager}. */
@RequiresApi(Build.VERSION_CODES.S)
public class KAnonSignJoinFactory {
    private final Context mContext;

    /**
     * Returns an instance for this class. Once you have the instance you can use {@link
     * KAnonSignJoinFactory#getKAnonSignJoinManager()} to create an instance of {@link
     * KAnonSignJoinManager}
     */
    public KAnonSignJoinFactory(Context context) {
        mContext = context;
    }

    /** Returns an instance of {@link KAnonSignJoinManager}. */
    public KAnonSignJoinManager getKAnonSignJoinManager() {
        AdServicesHttpsClient adServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        FlagsFactory.getFlags().getFledgeKanonHttpClientTimeoutInMs(),
                        FlagsFactory.getFlags().getFledgeKanonHttpClientTimeoutInMs(),
                        DEFAULT_MAX_BYTES);
        KAnonMessageManager kAnonMessageManager =
                new KAnonMessageManager(
                        KAnonDatabase.getInstance().kAnonMessageDao(),
                        FlagsFactory.getFlags(),
                        Clock.systemUTC());
        KeyAttestationFactory keyAttestationFactory = new KeyAttestationFactory(mContext);
        KAnonCallerImpl kAnonCaller =
                new KAnonCallerImpl(
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        new AnonymousCountingTokensImpl(),
                        adServicesHttpsClient,
                        KAnonDatabase.getInstance().clientParametersDao(),
                        KAnonDatabase.getInstance().serverParametersDao(),
                        UserProfileIdManager.getInstance(),
                        new BinaryHttpMessageDeserializer(),
                        FlagsFactory.getFlags(),
                        kAnonMessageManager,
                        AdServicesLoggerImpl.getInstance(),
                        keyAttestationFactory,
                        new ObliviousHttpEncryptorFactory(mContext));
        return new KAnonSignJoinManager(
                mContext,
                kAnonCaller,
                kAnonMessageManager,
                FlagsFactory.getFlags(),
                Clock.systemUTC(),
                AdServicesLoggerImpl.getInstance());
    }
}
