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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastS;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.Property;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.exception.XmlParseException;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

@SmallTest
public final class AppManifestConfigHelperTest {

    private static final String TAG = AppManifestConfigHelperTest.class.getSimpleName();
    private static final int RESOURCE_ID = 123;
    private static final String AD_SERVICES_CONFIG_PROPERTY =
            "android.adservices.AD_SERVICES_CONFIG";
    private static final String PACKAGE_NAME = "TEST_PACKAGE";
    private static final String ENROLLMENT_ID = "ENROLLMENT_ID";

    @Mock private AppManifestConfig mMockAppManifestConfig;
    @Mock private AppManifestIncludesSdkLibraryConfig mMockSdkLibraryConfig;
    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private AssetManager mMockAssetManager;
    @Mock private Resources mMockResources;
    @Mock private XmlResourceParser mMockParser;
    @Mock private Flags mMockFlags;

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AppManifestConfigParser.class)
                    .spyStatic(AndroidManifestConfigParser.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(SdkLevel.class)
                    .spyStatic(ErrorLogUtil.class)
                    .build();

    @Rule public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    @Rule public final Expect expect = Expect.create();

    @Before
    public void setCommonExpectations() {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        mockGetFlags(mMockFlags);
        setEnabledByDefault(false);
        doNothingOnErrorLogUtilError();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAttributionAccess_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAttributionAccess(ENROLLMENT_ID, true);

        assertWithMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    public void testIsAllowedAttributionAccess_rMinus() throws Exception {
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAttributionAccess(ENROLLMENT_ID, true);

        assertWithMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedCustomAudiencesAccess_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedCustomAudiencesAccess(ENROLLMENT_ID, true);

        assertWithMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    public void testIsAllowedCustomAudiencesAccess_rMinus() throws Exception {
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedCustomAudiencesAccess(ENROLLMENT_ID, true);

        assertWithMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromSandbox_allowed_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromSandbox_notAllowed_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromApp_allowed_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfSdk_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ false);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfTopics_sPlus()
            throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    @Test
    public void testIsAllowedTopicsAccessFromSandbox_allowed_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    public void testIsAllowedTopicsAccessFromSandbox_notAllowed_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    @Test
    public void testIsAllowedTopicsAccessFromApp_allowed_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfSdk_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ false);
    }

    @Test
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfTopics_rMinus()
            throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    private void executeIsAllowedTopicAccessTest(
            boolean isRMinus,
            boolean useSandboxCheck,
            boolean containsSdk,
            boolean topicsAllowed,
            boolean expectedAllowed)
            throws Exception {
        if (isRMinus) {
            mockSdkLevelR();
            mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        } else {
            mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        }
        mockAppManifestConfigParserGetConfigSucceeds();
        mockContainsSdk(ENROLLMENT_ID, containsSdk);
        mockIsAllowedTopicsAccess(ENROLLMENT_ID, topicsAllowed);

        assertWithMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ useSandboxCheck,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isEqualTo(expectedAllowed);
    }


    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();
        verifyErrorLogUtilErrorLogged(e, times(4)); // Called once for each API
    }

    @Test
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_rMinus() throws Exception {
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();
        verifyErrorLogUtilErrorLogged(e, times(4)); // Called once for each API
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_enabledByDefault_sPlus()
            throws Exception {
        setEnabledByDefault(true);
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();
        verifyErrorLogUtilErrorLogged(e, times(4)); // Called once for each API
    }

    @Test
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_enabledByDefault_rMinus()
            throws Exception {
        setEnabledByDefault(true);
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();
        verifyErrorLogUtilErrorLogged(e, times(4)); // Called once for each API
    }

    @Test
    public void testIsAllowedApiAccess_packageNotFound() throws Exception {
        mockAppNotFound(PACKAGE_NAME);

        assertNoAccessAllowed();
    }

    @Test
    public void testIsAllowedApiAccess_packageNotFound_enabledByDefault() throws Exception {
        setEnabledByDefault(true);
        mockAppNotFound(PACKAGE_NAME);

        assertNoAccessAllowed();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_noConfigXmlForPackage_sPlus() throws Exception {
        mockAppFound(PACKAGE_NAME);
        mockGetPropertyNotFound(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY);

        assertNoAccessAllowed();
    }

    @Test
    public void testIsAllowedApiAccess_noConfigXmlForPackage_rMinus() throws Exception {
        mockSdkLevelR();
        mockAppFound(PACKAGE_NAME);
        mockGetAssetNotFound(PACKAGE_NAME);

        assertNoAccessAllowed();
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_noConfigXmlForPackage_enabledByDefault_sPlus()
            throws Exception {
        setEnabledByDefault(true);
        mockAppFound(PACKAGE_NAME);
        mockGetPropertyNotFound(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY);

        assertAllAccessAllowed();
    }

    @Test
    public void testIsAllowedApiAccess_noConfigXmlForPackage_enabledByDefault_rMinus()
            throws Exception {
        setEnabledByDefault(true);
        mockSdkLevelR();
        mockAppFound(PACKAGE_NAME);
        mockGetAssetNotFound(PACKAGE_NAME);

        assertAllAccessAllowed();
    }

    private void mockSdkLevelR() {
        if (SdkLevel.isAtLeastS()) {
            mockIsAtLeastS(false);
        } else {
            Log.v(TAG, "mockSdkLevelR(): not needed, device is not at least S");
        }
    }

    private void mockGetPropertySucceeds(String pkgName, String propName, int resId)
            throws Exception {
        Property property = mock(Property.class);
        mockAppFound(pkgName);
        when(mMockPackageManager.getProperty(propName, pkgName)).thenReturn(property);
        when(property.getResourceId()).thenReturn(resId);
        when(mMockResources.getXml(resId)).thenReturn(mMockParser);
    }

    private void mockGetPropertyNotFound(String pkgName, String propName) throws Exception {
        mockAppFound(pkgName);
        when(mMockPackageManager.getProperty(propName, pkgName))
                .thenThrow(new NameNotFoundException("A property has no name."));
    }

    private void mockGetAssetSucceeds(String pkgName, int resId) throws Exception {
        mockAppFound(pkgName);
        when(mMockContext.createPackageContext(pkgName, /* flags= */ 0)).thenReturn(mMockContext);
        when(mMockContext.getAssets()).thenReturn(mMockAssetManager);
        when(mMockAssetManager.openXmlResourceParser(anyString())).thenReturn(mMockParser);
        doReturn(resId)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));
        when(mMockResources.getXml(resId)).thenReturn(mMockParser);
    }

    private void mockGetAssetNotFound(String pkgName) throws Exception {
        mockAppFound(pkgName);
        when(mMockContext.createPackageContext(pkgName, /* flags= */ 0)).thenReturn(mMockContext);
        when(mMockContext.getAssets()).thenReturn(mMockAssetManager);
        when(mMockAssetManager.openXmlResourceParser(anyString())).thenReturn(mMockParser);
        doReturn(null)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));
    }

    private void mockAppFound(String pkgName) throws Exception {
        when(mMockPackageManager.getResourcesForApplication(pkgName)).thenReturn(mMockResources);
    }

    private void mockAppNotFound(String pkgName) throws Exception {
        when(mMockPackageManager.getResourcesForApplication(pkgName))
                .thenThrow(new NameNotFoundException("A package has no name."));
    }

    private void mockAppManifestConfigParserGetConfigSucceeds() throws Exception {
        doReturn(mMockAppManifestConfig)
                .when(() -> AppManifestConfigParser.getConfig(eq(mMockParser), anyBoolean()));
    }

    private XmlParseException mockAppManifestConfigParserGetConfigThrows() throws Exception {
        XmlParseException e = new XmlParseException("D'OH!");
        doThrow(e).when(() -> AppManifestConfigParser.getConfig(eq(mMockParser), anyBoolean()));
        return e;
    }

    private void mockIsAllowedAttributionAccess(String partnerId, boolean value) {
        when(mMockAppManifestConfig.isAllowedAttributionAccess(partnerId)).thenReturn(value);
    }

    private void mockIsAllowedCustomAudiencesAccess(String partnerId, boolean value) {
        when(mMockAppManifestConfig.isAllowedCustomAudiencesAccess(partnerId)).thenReturn(value);
    }

    private void mockIsAllowedTopicsAccess(String partnerId, boolean value) {
        when(mMockAppManifestConfig.isAllowedTopicsAccess(partnerId)).thenReturn(value);
    }

    private void mockContainsSdk(String partnerId, boolean value) {
        when(mMockAppManifestConfig.getIncludesSdkLibraryConfig())
                .thenReturn(mMockSdkLibraryConfig);
        when(mMockSdkLibraryConfig.contains(partnerId)).thenReturn(value);
    }

    private void assertNoAccessAllowed() {
        expect.withMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ true,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isFalse();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ false,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isFalse();
    }

    private void assertAllAccessAllowed() {
        expect.withMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                mMockContext, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ true,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isTrue();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                mMockContext,
                                /* useSandboxCheck= */ false,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isTrue();

        verifyErrorLogUtilErrorLogged(any(), never());
    }

    private void verifyErrorLogUtilErrorLogged(Exception e, VerificationMode mode) {
        // NOTE: e is null when passed as any().
        Exception exceptionMatcher = e == null ? e : eq(e);
        verifyErrorLogUtilError(
                exceptionMatcher,
                eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR),
                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED),
                mode);
    }

    private void setEnabledByDefault(boolean value) {
        when(mMockFlags.getAppConfigReturnsEnabledByDefault()).thenReturn(value);
    }
}