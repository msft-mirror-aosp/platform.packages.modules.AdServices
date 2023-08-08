/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;
import android.webkit.WebViewUpdateService;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.restrictions.contentproviders.IContentProvidersSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ContentProviderRestrictionsTestApp {
    private SdkSandboxManager mSdkSandboxManager;

    // Keep the value consistent with SdkSandboxmanagerService.ENFORCE_RESTRICTIONS
    private static final String ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    // Keep the value consistent with SdkSandboxmanagerService.PROPERTY_CONTENTPROVIDER_ALLOWLIST.
    private static final String PROPERTY_CONTENTPROVIDER_ALLOWLIST =
            "contentprovider_allowlist_per_targetSdkVersion";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS.
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST.
    private static final String PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST =
            "sdksandbox_next_contentprovider_allowlist";

    private static final String SDK_PACKAGE =
            "com.android.tests.sdkprovider.restrictions.contentproviders";

    /** This rule is defined to start an activity in the foreground to call the sandbox APIs */
    @Rule
    public final ActivityScenarioRule mRule =
            new ActivityScenarioRule<>(SdkSandboxEmptyActivity.class);

    private String mInitialContentProviderRestrictionValue;
    private String mInitialContentProviderAllowlistValue;
    private String mInitialApplyNextContentProviderAllowlistValue;
    private String mInitialNextContentProviderAllowlistValue;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.READ_DEVICE_CONFIG);
        mInitialContentProviderRestrictionValue =
                DeviceConfig.getProperty(DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS);
        mInitialContentProviderAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_CONTENTPROVIDER_ALLOWLIST);
        mInitialApplyNextContentProviderAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        mInitialNextContentProviderAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST);

        // Greedily unload SDK to reduce flakiness
        mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
    }

    @After
    public void teardown() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_RESTRICTIONS,
                mInitialContentProviderRestrictionValue,
                /*makeDefault=*/ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_CONTENTPROVIDER_ALLOWLIST,
                mInitialContentProviderAllowlistValue,
                /*makeDefault=*/ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                mInitialApplyNextContentProviderAllowlistValue,
                /*makeDefault=*/ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST,
                mInitialNextContentProviderAllowlistValue,
                /*makeDefault=*/ false);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        // Greedily unload SDK to reduce flakiness
        mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
    }

    @Test
    public void testGetContentProvider_restrictionsApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS, "true", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        assertThrows(SecurityException.class, () -> contentProvidersSdkApi.getContentProvider());
    }

    @Test
    public void testRegisterContentObserver_restrictionsApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS, "true", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        assertThrows(
                SecurityException.class, () -> contentProvidersSdkApi.registerContentObserver());
    }

    @Test
    public void testGetContentProvider_DeviceConfigAllowlistApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        /**
         * Base64 encoded proto ContentProviderAllowlists containing allowlist_per_target_sdk { key:
         * 34 value { authorities: "com.android.textclassifier.icons" authorities: "user_dictionary"
         * } }
         *
         * <p>allowlist_per_target_sdk { key: 35 value { authorities:
         * "com.android.textclassifier.icons" authorities: "user_dictionary" } }
         */
        final String encodedAllowlist =
                "CjcIIhIzCiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucwoPdXNlcl9kaWN0aW9uYXJ5CjcII"
                        + "xIzCiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucwoPdXNlcl9kaWN0aW9uYXJ5";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_CONTENTPROVIDER_ALLOWLIST,
                encodedAllowlist,
                false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        contentProvidersSdkApi.getContentProviderByAuthority("com.android.textclassifier.icons");
        contentProvidersSdkApi.getContentProvider();

        assertThrows(
                SecurityException.class,
                () ->
                        contentProvidersSdkApi.getContentProviderByAuthority(
                                "com.android.contacts.dumpfile/a-contacts-db.zip"));
    }

    @Test
    public void testGetContentProvider_DeviceConfigNextAllowlistApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        // Base64 encoded proto AllowedContentProviders containing the string
        // 'com.android.textclassifier.icons'
        final String encodedNextAllowlist = "CiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucw==";
        // Set the canary set.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST,
                encodedNextAllowlist,
                false);

        // Base64 encoded proto ContentProviderAllowlists containing mappings to the string
        // 'com.android.textclassifier.icons' and 'user_dictionary'.
        final String encodedAllowlist =
                "CjcIIhIzCiBjb20uYW5kcm9pZC50ZXh0Y2xhc3NpZmllci5pY29ucwoPdXNlcl9kaWN0aW9uYXJ5";
        // Also set the non-canary allowlist to verify that this allowlist is not applied when the
        // canary flag is set.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_CONTENTPROVIDER_ALLOWLIST,
                encodedAllowlist,
                false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        contentProvidersSdkApi.getContentProviderByAuthority("com.android.textclassifier.icons");
        assertThrows(SecurityException.class, () -> contentProvidersSdkApi.getContentProvider());
        assertThrows(
                SecurityException.class,
                () ->
                        contentProvidersSdkApi.getContentProviderByAuthority(
                                "com.android.contacts.dumpfile/a-contacts-db.zip"));
    }

    @Test
    public void testGetContentProvider_DeviceConfigWildcardAllowlistApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        /*
         * Base64 encoded proto ContentProviderAllowlists in the following form:
         * allowlist_per_target_sdk {
         *   key: 34
         *   value {
         *     authorities: "*"
         *   }
         * }
         */
        final String encodedAllowlist = "CgcIIhIDCgEq";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_CONTENTPROVIDER_ALLOWLIST,
                encodedAllowlist,
                false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        // All kinds of ContentProviders should be accessible.
        contentProvidersSdkApi.getContentProviderByAuthority("com.android.textclassifier.icons");
        contentProvidersSdkApi.getContentProvider();
        contentProvidersSdkApi.getContentProviderByAuthority(
                "com.android.contacts.dumpfile/a-contacts-db.zip");
    }

    @Test
    public void testGetContentProvider_DeviceConfigAllowlistWithWildcardApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                ENFORCE_RESTRICTIONS,
                "true",
                /*makeDefault=*/ false);

        /*
         * Base64 encoded proto ContentProviderAllowlists in the following form:
         * allowlist_per_target_sdk {
         *   key: 34
         *   value {
         *     authorities: "com.android.contacts.*"
         *   }
         * }
         */
        final String encodedAllowlist = "ChwIIhIYChZjb20uYW5kcm9pZC5jb250YWN0cy4q";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                PROPERTY_CONTENTPROVIDER_ALLOWLIST,
                encodedAllowlist,
                false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        contentProvidersSdkApi.getContentProviderByAuthority(
                "com.android.contacts.dumpfile/a-contacts-db.zip");
        assertThrows(SecurityException.class, () -> contentProvidersSdkApi.getContentProvider());
        assertThrows(
                SecurityException.class,
                () ->
                        contentProvidersSdkApi.getContentProviderByAuthority(
                                "com.android.textclassifier.icons"));
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testGetWebViewContentProvider_restrictionsApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS, "true", false);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        final IBinder binder = sandboxedSdk.getInterface();
        final IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        contentProvidersSdkApi.getContentProviderByAuthority(
                WebViewUpdateService.getCurrentWebViewPackageName()
                        + ".DeveloperModeContentProvider");
        contentProvidersSdkApi.getContentProviderByAuthority(
                WebViewUpdateService.getCurrentWebViewPackageName() + ".SafeModeContentProvider");
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testGetContentProvider_restrictionsNotApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS, "false", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        contentProvidersSdkApi.getContentProvider();
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterContentObserver_restrictionsNotApplied() throws Exception {
        mRule.getScenario();

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS, "false", false);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        contentProvidersSdkApi.registerContentObserver();
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testGetContentProvider_defaultValueRestrictionsApplied() throws Exception {
        /** Ensuring that the property is not present in DeviceConfig */
        DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        assertThrows(SecurityException.class, () -> contentProvidersSdkApi.getContentProvider());
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterContentObserver_defaultValueRestrictionsApplied() throws Exception {
        /** Ensuring that the property is not present in DeviceConfig */
        DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_ADSERVICES, ENFORCE_RESTRICTIONS);

        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        IContentProvidersSdkApi contentProvidersSdkApi =
                IContentProvidersSdkApi.Stub.asInterface(binder);

        assertThrows(SecurityException.class, () -> contentProvidersSdkApi.getContentProvider());
    }
}
