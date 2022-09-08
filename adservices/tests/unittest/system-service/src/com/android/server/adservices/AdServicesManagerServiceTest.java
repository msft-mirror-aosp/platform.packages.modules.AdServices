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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Tests for {@link AdServicesManagerService} */
public class AdServicesManagerServiceTest {
    private AdServicesManagerService mService;
    private Context mSpyContext;
    private static final String PACKAGE_NAME = "com.package.example";
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";
    private static final String PACKAGE_ADDED = "package_added";

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mService = new AdServicesManagerService(mSpyContext);
    }

    @Test
    public void testSendBroadcastForPackageFullyRemoved() {
        Intent i = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());
        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        Truth.assertThat(argumentIntent.getValue().getAction())
                .isEqualTo(PACKAGE_CHANGED_BROADCAST);
        Truth.assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        Truth.assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_FULLY_REMOVED);
        Truth.assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }

    @Test
    public void testSendBroadcastForPackageAdded() {
        Intent i = new Intent(Intent.ACTION_PACKAGE_ADDED);
        i.setData(Uri.parse("package:" + PACKAGE_NAME));
        i.putExtra(Intent.EXTRA_REPLACING, false);

        ArgumentCaptor<Intent> argumentIntent = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<UserHandle> argumentUser = ArgumentCaptor.forClass(UserHandle.class);

        Mockito.doNothing().when(mSpyContext).sendBroadcastAsUser(Mockito.any(), Mockito.any());
        mService.onPackageChange(i, mSpyContext.getUser());

        Mockito.verify(mSpyContext, Mockito.times(1))
                .sendBroadcastAsUser(argumentIntent.capture(), argumentUser.capture());

        Truth.assertThat(argumentIntent.getValue().getAction())
                .isEqualTo(PACKAGE_CHANGED_BROADCAST);
        Truth.assertThat(argumentIntent.getValue().getData()).isEqualTo(i.getData());
        Truth.assertThat(argumentIntent.getValue().getStringExtra("action"))
                .isEqualTo(PACKAGE_ADDED);
        Truth.assertThat(argumentUser.getValue()).isEqualTo(mSpyContext.getUser());
    }
}
