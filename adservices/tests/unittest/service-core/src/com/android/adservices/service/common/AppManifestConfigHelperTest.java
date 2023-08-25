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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.exception.XmlParseException;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

import java.io.IOException;

@SmallTest
public class AppManifestConfigHelperTest {
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

    private MockitoSession mSession;

    @Before
    public void before() {
        mSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AppManifestConfigParser.class)
                        .spyStatic(AndroidManifestConfigParser.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        mSession.finishMocking();
    }

    @Test
    public void testIsAllowedAttributionAccess_sPlus() throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

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
    public void testIsAllowedAttributionAccess_parsingExceptionSwallowed_sPlus()
            throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

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
    public void testIsAllowedAttributionAccess_rMinus()
            throws PackageManager.NameNotFoundException, IOException {
        Assume.assumeFalse(SdkLevel.isAtLeastS());

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
    public void testIsAllowedCustomAudiencesAccess_sPlus()
            throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

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
    public void testIsAllowedCustomAudiencesAccess_parsingExceptionSwallowed_sPlus()
            throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

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
    public void testIsAllowedCustomAudiencesAccess_rMinus()
            throws PackageManager.NameNotFoundException, IOException {
        Assume.assumeFalse(SdkLevel.isAtLeastS());

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
    public void testIsAllowedTopicsAccess_sPlus() throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

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
    public void testIsAllowedTopicsAccess_parsingExceptionSwallowed_sPlus()
            throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

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
    public void testIsAllowedTopicsAccess_rMinus()
            throws PackageManager.NameNotFoundException, IOException {
        Assume.assumeFalse(SdkLevel.isAtLeastS());

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
}
