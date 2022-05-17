/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.cts;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.exceptions.AdServicesException;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class CustomAudienceCtsTest {

    private AdvertisingCustomAudienceClient mClient;

    @Before
    public void setup() {
        mClient = new AdvertisingCustomAudienceClient.Builder()
                .setContext(ApplicationProvider.getApplicationContext())
                .setExecutor(MoreExecutors.directExecutor())
                .build();
    }

    @Test
    public void testJoinCustomAudience_validCustomAudience_success()
            throws ExecutionException, InterruptedException {
        mClient.joinCustomAudience(CustomAudienceFixture.getValidBuilder().build()).get();
    }

    @Test
    public void testJoinCustomAudience_illegalExpirationTime_fail() {
        Exception exception = assertThrows(ExecutionException.class,
                () -> mClient.joinCustomAudience(CustomAudienceFixture.getValidBuilder()
                        .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                        .build()).get());
        assertTrue(exception.getCause() instanceof AdServicesException);
        assertTrue(exception.getCause().getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testLeaveCustomAudience_joinedCustomAudience_success()
            throws ExecutionException, InterruptedException {
        mClient.joinCustomAudience(CustomAudienceFixture.getValidBuilder().build()).get();
        mClient.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME).get();
    }

    @Test
    public void testLeaveCustomAudience_notJoinedCustomAudience_doesNotFail()
            throws ExecutionException, InterruptedException {
        mClient.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, "not_exist_name").get();
    }
}
