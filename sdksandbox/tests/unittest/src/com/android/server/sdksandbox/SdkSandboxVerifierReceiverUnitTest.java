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

package com.android.server.sdksandbox;

import static com.android.server.sdksandbox.SdkSandboxSettingsListener.PROPERTY_ENFORCE_RESTRICTIONS;
import static com.android.server.sdksandbox.SdkSandboxSettingsListener.PROPERTY_VERIFY_DEX_FILES;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.server.sdksandbox.verifier.SdkDexVerifier;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;

/** Unit tests for {@link SdkSandboxVerifierReceiver}. */
@RunWith(JUnit4.class)
public class SdkSandboxVerifierReceiverUnitTest extends DeviceSupportedBaseTest {

    private static final Intent VERIFY_INTENT =
            new Intent().setData(Uri.fromFile(new File("sdk.apk")));
    private static final PackageInfo FAKE_PACKAGE_INFO = new PackageInfo();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;
    private SdkSandboxVerifierReceiver mVerifierReceiver;
    private Context mSpyContext;
    private PackageManager mSpyPm;
    private SdkDexVerifier mDexVerifier;
    private Handler mSpyHandler;

    @Rule public final AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.newInstance();

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.PACKAGE_VERIFICATION_AGENT);

        mVerifierReceiver = new SdkSandboxVerifierReceiver();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);
        mSdkSandboxSettingsListener = new SdkSandboxSettingsListener(mSpyContext, null);

        mDexVerifier = SdkDexVerifier.getInstance();
        mDexVerifier.setSdkSandboxSettingsListener(mSdkSandboxSettingsListener);

        PackageManager pm = mSpyContext.getPackageManager();
        mSpyPm = Mockito.spy(pm);
        mSpyHandler = Mockito.spy(HANDLER);

        Mockito.when(mSpyContext.getPackageManager()).thenReturn(mSpyPm);
        Mockito.when(mSpyPm.getPackageArchiveInfo(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(FAKE_PACKAGE_INFO);
    }

    @SetFlagTrue(PROPERTY_ENFORCE_RESTRICTIONS)
    @SetFlagTrue(PROPERTY_VERIFY_DEX_FILES)
    @Test
    public void verifierBroadcastReceived_startsDexParsing() {
        mVerifierReceiver.verifySdkHandler(mSpyContext, VERIFY_INTENT, mSpyHandler);

        Mockito.verify(mSpyHandler, Mockito.times(1)).post(Mockito.any());
    }

    @Test
    public void verifierBroadcastReceived_doesNotStartDexParsing() {
        flags.setFlag(PROPERTY_ENFORCE_RESTRICTIONS, false);
        flags.setFlag(PROPERTY_VERIFY_DEX_FILES, false);
        mVerifierReceiver.verifySdkHandler(mSpyContext, VERIFY_INTENT, mSpyHandler);

        Mockito.verify(mSpyHandler, Mockito.times(0)).post(Mockito.any());

        flags.setFlag(PROPERTY_ENFORCE_RESTRICTIONS, true);
        mVerifierReceiver.verifySdkHandler(mSpyContext, VERIFY_INTENT, mSpyHandler);

        Mockito.verify(mSpyHandler, Mockito.times(0)).post(Mockito.any());

        flags.setFlag(PROPERTY_ENFORCE_RESTRICTIONS, false);
        flags.setFlag(PROPERTY_VERIFY_DEX_FILES, true);
        mVerifierReceiver.verifySdkHandler(mSpyContext, VERIFY_INTENT, mSpyHandler);

        Mockito.verify(mSpyHandler, Mockito.times(0)).post(Mockito.any());
    }
}
