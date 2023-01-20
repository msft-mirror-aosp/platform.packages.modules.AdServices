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

package com.android.server.adservices;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.adservices.consent.AppConsentManager;
import com.android.server.adservices.consent.ConsentManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/** Tests for {@link UserInstanceManager} */
public class UserInstanceManagerTest {
    private static final Context APPLICATION_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String TEST_BASE_PATH =
            APPLICATION_CONTEXT.getFilesDir().getAbsolutePath();

    private UserInstanceManager mUserInstanceManager;

    @Before
    public void setup() throws IOException {
        mUserInstanceManager = new UserInstanceManager(TEST_BASE_PATH);
    }

    @After
    public void tearDown() {
        // We need tear down this instance since it can have underlying persisted Data Store.
        mUserInstanceManager.tearDownForTesting();
    }

    @Test
    public void testGetOrCreateUserConsentManagerInstance() throws IOException {
        ConsentManager consentManager0 =
                mUserInstanceManager.getOrCreateUserConsentManagerInstance(/* userIdentifier */ 0);

        ConsentManager consentManager1 =
                mUserInstanceManager.getOrCreateUserConsentManagerInstance(/* userIdentifier */ 1);

        AppConsentManager appConsentManager0 =
                mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(0);

        AppConsentManager appConsentManager1 =
                mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(1);

        // One instance per user.
        assertThat(
                        mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                                /* userIdentifier */ 0))
                .isNotSameInstanceAs(consentManager1);
        assertThat(mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(0))
                .isNotSameInstanceAs(appConsentManager1);

        // Creating instance once per user.
        assertThat(
                        mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                                /* userIdentifier */ 0))
                .isSameInstanceAs(consentManager0);
        assertThat(
                        mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                                /* userIdentifier */ 1))
                .isSameInstanceAs(consentManager1);
        assertThat(mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(0))
                .isSameInstanceAs(appConsentManager0);
        assertThat(mUserInstanceManager.getOrCreateUserAppConsentManagerInstance(1))
                .isSameInstanceAs(appConsentManager1);
    }
}
