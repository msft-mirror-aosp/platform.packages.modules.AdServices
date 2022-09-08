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
package com.android.adservices.tests.appsetid;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdProviderService;
import android.content.Intent;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AppSetIdProviderTest {

    private static final String DEFAULT_APP_SET_ID = "00000000-0000-0000-0000-000000000000";
    private static final int DEFAULT_SCOPE = 1;

    private static final class AppSetIdProviderServiceProxy extends AppSetIdProviderService {

        @Override
        public AppSetId onGetAppSetId(int clientUid, String clientPackageName) throws IOException {
            return new AppSetId(DEFAULT_APP_SET_ID, DEFAULT_SCOPE);
        }
    }

    @Test
    public void testAppSetIdProvider() {
        AppSetIdProviderServiceProxy proxy = new AppSetIdProviderServiceProxy();

        Intent intent = new Intent();
        intent.setAction(AppSetIdProviderService.SERVICE_INTERFACE);

        Assert.assertNotNull(proxy.onBind(intent));
    }
}
