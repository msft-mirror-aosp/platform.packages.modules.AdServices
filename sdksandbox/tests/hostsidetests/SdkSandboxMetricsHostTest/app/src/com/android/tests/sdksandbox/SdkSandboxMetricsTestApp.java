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

package com.android.tests.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.DropBoxManager;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.crashtest.ICrashTestSdkApi;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SdkSandboxMetricsTestApp {

    private SdkSandboxManager mSdkSandboxManager;

    private ICrashTestSdkApi mSdk;
    private static final String SDK_PACKAGE = "com.android.tests.sdkprovider.crashtest";
    private static final String TAG_SYSTEM_APP_CRASH = "system_app_crash";
    private static final int N_DROPBOX_HEADER_BYTES = 1024;

    // Values declared in SdkSandboxMetricsTestAppManifest.xml
    private static final String PACKAGE_NAME = "com.android.tests.sdksandbox";
    private static final String VERSION_NAME = "1.0";
    private static final int VERSION_CODE = 1;

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);
    private DropBoxManager mDropboxManager;
    private Context mContext;
    private PackageManager mPackageManager;
    private String mCrashEntryText;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        mDropboxManager = mContext.getSystemService(DropBoxManager.class);
        mPackageManager = mContext.getPackageManager();
        assertThat(mSdkSandboxManager).isNotNull();
    }

    @Test
    public void testCrashSandboxGeneratesDropboxReport() throws Exception {
        mRule.getScenario();

        CountDownLatch latch = new CountDownLatch(1);

        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!intent.getStringExtra(DropBoxManager.EXTRA_TAG)
                                .equals(TAG_SYSTEM_APP_CRASH)) {
                            return;
                        }
                        final long timeBeforeEntry =
                                intent.getLongExtra(DropBoxManager.EXTRA_TIME, 0) - 1;
                        DropBoxManager.Entry entry =
                                mDropboxManager.getNextEntry(TAG_SYSTEM_APP_CRASH, timeBeforeEntry);

                        if (entry == null) {
                            return;
                        }

                        String entryText = entry.getText(N_DROPBOX_HEADER_BYTES);
                        if (entryText == null) {
                            entry.close();
                            return;
                        }
                        // Check if SDK sandbox crash
                        if (entryText.contains(
                                String.format(
                                        "Package: %s",
                                        mPackageManager.getSdkSandboxPackageName()))) {
                            mCrashEntryText = entryText;
                            latch.countDown();
                        }
                        entry.close();
                    }
                };

        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(
                Runnable::run,
                () -> {
                    // Avoid being killed when sandbox crashes
                });

        mContext.registerReceiver(
                receiver, new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED));

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        mSdk = ICrashTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
        mSdk.triggerCrash();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(mCrashEntryText)
                .contains(
                        String.format(
                                "SdkSandbox-Client-Package: %s v%d (%s)",
                                PACKAGE_NAME, VERSION_CODE, VERSION_NAME));
    }
}
