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

package com.android.adservices.service.devapi;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.devapi.DevSessionFixture;

import org.junit.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DevSessionDataStoreFactoryTest extends AdServicesMockitoTestCase {

    private static final int TIMEOUT_SEC = 3;

    @Test
    public void testGet_developerModeEnabled_returnsDataStoreWithExpectedBehavior()
            throws Exception {
        DevSessionDataStore dataStore =
                DevSessionDataStoreFactory.get(/* developerModeFeatureEnabled= */ true);

        wait(dataStore.set(DevSessionFixture.IN_DEV));
        DevSession devSession = wait(dataStore.get());

        expect.that(devSession.getState()).isEqualTo(DevSessionState.IN_DEV);

        // Leave device in a clean state.
        wait(dataStore.set(DevSessionFixture.IN_DEV));
    }

    @Test
    public void testGet_developerModeDisabled_returnsDataStoreWithExpectedBehavior()
            throws Exception {
        DevSessionDataStore dataStore =
                DevSessionDataStoreFactory.get(/* developerModeFeatureEnabled= */ false);
        wait(dataStore.set(DevSessionFixture.IN_DEV)); // This should do nothing.
        DevSession devSession = wait(dataStore.get());

        expect.that(devSession.getState()).isEqualTo(DevSessionState.IN_PROD);
    }

    private static <T> T wait(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}
