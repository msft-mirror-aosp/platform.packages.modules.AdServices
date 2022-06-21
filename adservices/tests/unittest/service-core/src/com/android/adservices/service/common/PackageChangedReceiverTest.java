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

package com.android.adservices.service.common;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class PackageChangedReceiverTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String SAMPLE_PACKAGE = "com.example.measurement.sampleapp";
    private static final String PACKAGE_SCHEME = "package:";
    private @Mock PackageChangedReceiver mPackageChangedReceiver = new PackageChangedReceiver();

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        doNothing()
                .when(mPackageChangedReceiver)
                .onPackageFullyRemoved(any(Context.class), any(Uri.class));
        doNothing()
                .when(mPackageChangedReceiver)
                .onPackageAdded(any(Context.class), any(Uri.class));
        doCallRealMethod()
                .when(mPackageChangedReceiver)
                .onReceive(any(Context.class), any(Intent.class));
    }

    @Test
    public void testReceivePackageFullyRemoved() {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(
                PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_FULLY_REMOVED);

        mPackageChangedReceiver.onReceive(sContext, intent);

        verify(mPackageChangedReceiver, times(1))
                .onPackageFullyRemoved(any(Context.class), any(Uri.class));
        verify(mPackageChangedReceiver, never()).onPackageAdded(any(Context.class), any(Uri.class));
    }

    @Test
    public void testReceivePackageAdded() {
        Intent intent = new Intent();
        intent.setAction(PackageChangedReceiver.PACKAGE_CHANGED_BROADCAST);
        intent.setData(Uri.parse(PACKAGE_SCHEME + SAMPLE_PACKAGE));
        intent.putExtra(PackageChangedReceiver.ACTION_KEY, PackageChangedReceiver.PACKAGE_ADDED);

        mPackageChangedReceiver.onReceive(sContext, intent);

        verify(mPackageChangedReceiver, times(1))
                .onPackageAdded(any(Context.class), any(Uri.class));
        verify(mPackageChangedReceiver, never())
                .onPackageFullyRemoved(any(Context.class), any(Uri.class));
    }
}
