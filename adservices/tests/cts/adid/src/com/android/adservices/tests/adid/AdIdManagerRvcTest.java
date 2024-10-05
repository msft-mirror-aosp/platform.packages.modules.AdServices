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

package com.android.adservices.tests.adid;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.exceptions.AdServicesException;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RequiresSdkRange(atMost = RVC)
public final class AdIdManagerRvcTest extends AdServicesCtsTestCase
        implements CtsAdIdEndToEndTestFlags {

    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();

    private AdIdManager mAdIdManager;

    @Before
    public void setup() throws Exception {
        DeviceSideConcurrencyHelper.sleep(1000, "setup():sleeping 1s");

        mAdIdManager = AdIdManager.get(sContext);
        assertWithMessage("AdIdManager on context %s", sContext).that(mAdIdManager).isNotNull();
    }

    @Test
    public void testAdIdManager_getAdId_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();

        mAdIdManager.getAdId(sCallbackExecutor, callback);

        callback.assertFailure(AdServicesException.class);
    }
}
