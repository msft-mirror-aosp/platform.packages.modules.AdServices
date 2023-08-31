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

package com.android.adservices.service.common;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.exception.XmlParseException;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@SmallTest
public final class AppManifestConfigHelperTest {

    private static final String TAG = AppManifestConfigHelperTest.class.getSimpleName();
    private static final int RESOURCE_ID = 123;
    private static final String AD_SERVICES_CONFIG_PROPERTY =
            "android.adservices.AD_SERVICES_CONFIG";
    private static final String PACKAGE_NAME = "TEST_PACKAGE";
    private static final String ENROLLMENT_ID = "ENROLLMENT_ID";

    @Mock private AppManifestConfig mMockAppManifestConfig;
    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private AssetManager mMockAssetManager;
    @Mock private Resources mMockResources;
    @Mock private XmlResourceParser mMockParser;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AppManifestConfigParser.class)
                    .spyStatic(AndroidManifestConfigParser.class)
                    .spyStatic(SdkLevel.class)
                    .build();

    @Rule public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAttributionAccess_sPlus() throws Exception {
        PackageManager.Property property = Mockito.mock(PackageManager.Property.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(property)
                .when(mMockPackageManager)
                .getProperty(AD_SERVICES_CONFIG_PROPERTY, PACKAGE_NAME);
        doReturn(RESOURCE_ID).when(property).getResourceId();
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doReturn(mMockAppManifestConfig).when(() -> AppManifestConfigParser.getConfig(mMockParser));
        doReturn(true).when(mMockAppManifestConfig).isAllowedAttributionAccess(ENROLLMENT_ID);

        assertThat(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAttributionAccess_parsingExceptionSwallowed_sPlus() throws Exception {
        PackageManager.Property property = Mockito.mock(PackageManager.Property.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(property)
                .when(mMockPackageManager)
                .getProperty(AD_SERVICES_CONFIG_PROPERTY, PACKAGE_NAME);
        doReturn(RESOURCE_ID).when(property).getResourceId();
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doThrow(XmlParseException.class).when(() -> AppManifestConfigParser.getConfig(mMockParser));

        assertThat(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
    }

    @Test
    public void testIsAllowedAttributionAccess_rMinus() throws Exception {
        mockSdkLevelR();

        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(mMockContext).when(mMockContext).createPackageContext(PACKAGE_NAME, 0);
        doReturn(mMockAssetManager).when(mMockContext).getAssets();
        doReturn(mMockParser).when(mMockAssetManager).openXmlResourceParser(anyString());
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(RESOURCE_ID)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doReturn(mMockAppManifestConfig).when(() -> AppManifestConfigParser.getConfig(mMockParser));
        doReturn(true).when(mMockAppManifestConfig).isAllowedAttributionAccess(ENROLLMENT_ID);

        assertThat(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedCustomAudiencesAccess_sPlus() throws Exception {
        PackageManager.Property property = Mockito.mock(PackageManager.Property.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(property)
                .when(mMockPackageManager)
                .getProperty(AD_SERVICES_CONFIG_PROPERTY, PACKAGE_NAME);
        doReturn(RESOURCE_ID).when(property).getResourceId();
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doReturn(mMockAppManifestConfig).when(() -> AppManifestConfigParser.getConfig(mMockParser));
        doReturn(true).when(mMockAppManifestConfig).isAllowedCustomAudiencesAccess(ENROLLMENT_ID);

        assertThat(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedCustomAudiencesAccess_parsingExceptionSwallowed_sPlus()
            throws Exception {
        PackageManager.Property property = Mockito.mock(PackageManager.Property.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(property)
                .when(mMockPackageManager)
                .getProperty(AD_SERVICES_CONFIG_PROPERTY, PACKAGE_NAME);
        doReturn(RESOURCE_ID).when(property).getResourceId();
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doThrow(XmlParseException.class).when(() -> AppManifestConfigParser.getConfig(mMockParser));

        assertThat(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
    }

    @Test
    public void testIsAllowedCustomAudiencesAccess_rMinus() throws Exception {
        mockSdkLevelR();

        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(mMockContext).when(mMockContext).createPackageContext(PACKAGE_NAME, 0);
        doReturn(mMockAssetManager).when(mMockContext).getAssets();
        doReturn(mMockParser).when(mMockAssetManager).openXmlResourceParser(anyString());
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(RESOURCE_ID)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));

        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doReturn(mMockAppManifestConfig).when(() -> AppManifestConfigParser.getConfig(mMockParser));
        doReturn(true).when(mMockAppManifestConfig).isAllowedCustomAudiencesAccess(ENROLLMENT_ID);

        assertThat(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccess_sPlus() throws Exception {
        PackageManager.Property property = Mockito.mock(PackageManager.Property.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(property)
                .when(mMockPackageManager)
                .getProperty(AD_SERVICES_CONFIG_PROPERTY, PACKAGE_NAME);
        doReturn(RESOURCE_ID).when(property).getResourceId();
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doReturn(mMockAppManifestConfig).when(() -> AppManifestConfigParser.getConfig(mMockParser));
        doReturn(true).when(mMockAppManifestConfig).isAllowedTopicsAccess(ENROLLMENT_ID);

        assertThat(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ true,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccess_parsingExceptionSwallowed_sPlus() throws Exception {
        PackageManager.Property property = Mockito.mock(PackageManager.Property.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(property)
                .when(mMockPackageManager)
                .getProperty(AD_SERVICES_CONFIG_PROPERTY, PACKAGE_NAME);
        doReturn(RESOURCE_ID).when(property).getResourceId();
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doThrow(XmlParseException.class).when(() -> AppManifestConfigParser.getConfig(mMockParser));

        assertThat(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ true,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isFalse();
    }

    @Test
    public void testIsAllowedTopicsAccess_rMinus() throws Exception {
        mockSdkLevelR();

        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(mMockContext).when(mMockContext).createPackageContext(PACKAGE_NAME, 0);
        doReturn(mMockAssetManager).when(mMockContext).getAssets();
        doReturn(mMockParser).when(mMockAssetManager).openXmlResourceParser(anyString());
        doReturn(mMockResources).when(mMockPackageManager).getResourcesForApplication(PACKAGE_NAME);
        doReturn(RESOURCE_ID)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));
        doReturn(mMockParser).when(mMockResources).getXml(RESOURCE_ID);
        doReturn(mMockAppManifestConfig).when(() -> AppManifestConfigParser.getConfig(mMockParser));
        doReturn(true).when(mMockAppManifestConfig).isAllowedTopicsAccess(ENROLLMENT_ID);

        assertThat(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ true,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isTrue();
    }

    private void mockSdkLevelR() {
        if (SdkLevel.isAtLeastS()) {
            mockIsAtLeastS(false);
        } else {
            Log.v(TAG, "mockSdkLevelR(): not needed, device is not at least S");
        }
    }
}
