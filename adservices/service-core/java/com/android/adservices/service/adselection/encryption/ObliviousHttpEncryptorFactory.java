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

package com.android.adservices.service.adselection.encryption;

import android.content.Context;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

/** A factory class used to create an instance of {@link KAnonObliviousHttpEncryptorImpl}. */
public class ObliviousHttpEncryptorFactory {
    private Context mContext;

    public ObliviousHttpEncryptorFactory(Context context) {
        mContext = context;
    }
    /** Returns an instance of {@link KAnonObliviousHttpEncryptorImpl} */
    public ObliviousHttpEncryptor getKAnonObliviousHttpEncryptor() {
        AdServicesHttpsClient adServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.create(mContext, FlagsFactory.getFlags()));
        AdSelectionEncryptionKeyManager encryptionKeyManager =
                new AdSelectionEncryptionKeyManager(
                        AdSelectionServerDatabase.getInstance(mContext).encryptionKeyDao(),
                        FlagsFactory.getFlags(),
                        adServicesHttpsClient,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesLoggerImpl.getInstance());
        return new KAnonObliviousHttpEncryptorImpl(
                encryptionKeyManager, AdServicesExecutors.getLightWeightExecutor());
    }
}
