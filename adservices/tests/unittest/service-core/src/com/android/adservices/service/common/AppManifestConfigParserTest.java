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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.res.XmlResourceParser;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.exception.XmlParseException;
import com.android.adservices.servicecoretest.R;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;

@SmallTest
public final class AppManifestConfigParserTest {

    public @Rule final Expect expect = Expect.create();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mPackageName = mContext.getPackageName();

    @Test
    public void testValidXml() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config);

        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config").that(appManifestConfig).isNotNull();

        // Verify IncludesSdkLibrary tags.
        AppManifestIncludesSdkLibraryConfig includesSdkLibraryConfig =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()")
                .that(includesSdkLibraryConfig)
                .isNotNull();
        if (includesSdkLibraryConfig != null) {
            expect.withMessage("getIncludesSdkLibraryConfig().getIncludesSdkLibraries()")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().getIncludesSdkLibraries())
                    .containsExactly("1234", "4567", "89", "1234567");
        }

        // Verify Attribution tags.
        expect.withMessage("getAttributionConfig().getAllowAdPartnersToAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("1234"))
                .isTrue();
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("108"))
                .isFalse();
        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        expect.withMessage("getAttributionConfig()").that(attributionConfig).isNotNull();
        if (attributionConfig != null) {
            expect.withMessage("getAttributionConfig().getAllowAllToAccess()")
                    .that(attributionConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAttributionConfig().getAllowAdPartnersToAccess()")
                    .that(appManifestConfig.getAttributionConfig().getAllowAdPartnersToAccess())
                    .containsExactly("1234");
        }

        // Verify Custom Audience tags.
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("108"))
                .isFalse();
        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        expect.withMessage("getCustomAudiencesConfig()").that(customAudiencesConfig).isNotNull();
        if (customAudiencesConfig != null) {
            expect.withMessage("getCustomAudiencesConfig().getAllowAllToAccess()")
                    .that(customAudiencesConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getCustomAudiencesConfig().getAllowAdPartnersToAccess()")
                    .that(customAudiencesConfig.getAllowAdPartnersToAccess())
                    .hasSize(2);
            expect.withMessage("getCustomAudiencesConfig().getAllowAdPartnersToAccess()")
                    .that(customAudiencesConfig.getAllowAdPartnersToAccess())
                    .containsExactly("1234", "4567");
        }

        // Verify Topics tags.
        expect.withMessage("1234567()")
                .that(appManifestConfig.isAllowedTopicsAccess("1234567"))
                .isTrue();
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("108"))
                .isFalse();
        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        expect.withMessage("getTopicsConfig()").that(topicsConfig).isNotNull();
        if (topicsConfig != null) {
            expect.withMessage("getTopicsConfig().getAllowAllToAccess()")
                    .that(topicsConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getTopicsConfig().getAllowAdPartnersToAccess()")
                    .that(topicsConfig.getAllowAdPartnersToAccess())
                    .contains("1234567");
        }

        // Verify AppId tags.
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("42"))
                .isTrue();
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("108"))
                .isFalse();
        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        expect.withMessage("getAdIdConfig()").that(adIdConfig).isNotNull();
        if (adIdConfig != null) {
            expect.withMessage("getAdIdConfig().getAllowAllToAccess()")
                    .that(adIdConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAdIdConfig().getAllowAdPartnersToAccess()")
                    .that(adIdConfig.getAllowAdPartnersToAccess())
                    .containsExactly("4", "8", "15", "16", "23", "42");
        }

        // Verify AppSetId tags.
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("42"))
                .isTrue();
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("108"))
                .isFalse();
        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        expect.withMessage("getAppSetIdConfig()").that(appSetIdConfig).isNotNull();
        if (appSetIdConfig != null) {
            expect.withMessage("getAppSetIdConfig().getAllowAllToAccess()")
                    .that(appSetIdConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAppSetIdConfig().getAllowAdPartnersToAccess()")
                    .that(appSetIdConfig.getAllowAdPartnersToAccess())
                    .containsExactly("4", "8", "15", "16", "23", "42");
        }
    }

    @Test
    public void testInvalidXml_missingSdkLibrary() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_sdk_name);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Sdk name not mentioned in <includes-sdk-library>");
    }

    @Test
    public void testInvalidXml_incorrectValues() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_values);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("allowAll cannot be set to true when allowAdPartners is also set");
    }

    @Test
    public void testValidXml_missingTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_tags);

        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config_missing_tags")
                .that(appManifestConfig)
                .isNotNull();

        expect.withMessage("getIncludesSdkLibraryConfig()")
                .that(appManifestConfig.getIncludesSdkLibraryConfig())
                .isNotNull();
        expect.withMessage("getIncludesSdkLibraryConfig().getIncludesSdkLibraries()")
                .that(appManifestConfig.getIncludesSdkLibraryConfig().getIncludesSdkLibraries())
                .isEmpty();
        expect.withMessage("getAttributionConfig()")
                .that(appManifestConfig.getAttributionConfig())
                .isNull();
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("not actually there"))
                .isFalse();
        expect.withMessage("getCustomAudiencesConfig()")
                .that(appManifestConfig.getCustomAudiencesConfig())
                .isNull();
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("not actually there"))
                .isFalse();
        expect.withMessage("getTopicsConfig()").that(appManifestConfig.getTopicsConfig()).isNull();
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("not actually there"))
                .isFalse();
        expect.withMessage("getAdIdConfig()").that(appManifestConfig.getAdIdConfig()).isNull();
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("not actually there"))
                .isFalse();
        expect.withMessage("getAppSetIdConfig()")
                .that(appManifestConfig.getAppSetIdConfig())
                .isNull();
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("not actually there"))
                .isFalse();
    }

    @Test
    public void testValidXml_missingValues() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_values);

        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config_missing_values")
                .that(appManifestConfig)
                .isNotNull();

        expect.withMessage("getAttributionConfig().getAllowAllToAccess()")
                .that(appManifestConfig.getAttributionConfig().getAllowAllToAccess())
                .isFalse();
        expect.withMessage("getCustomAudiencesConfig().getAllowAllToAccess()")
                .that(appManifestConfig.getCustomAudiencesConfig().getAllowAllToAccess())
                .isFalse();
        expect.withMessage("getTopicsConfig().getAllowAllToAccess()")
                .that(appManifestConfig.getTopicsConfig().getAllowAllToAccess())
                .isFalse();
    }

    @Test
    public void testInvalidXml_repeatTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_repeat_tags);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e).hasMessageThat().isEqualTo("Tag custom-audiences appears more than once");
    }

    @Test
    public void testInvalidXml_incorrectStartTag() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_start_tag);

        Exception e =
                assertThrows(
                        XmlPullParserException.class,
                        () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e).hasMessageThat().isEqualTo("expected START_TAGBinary XML file line #17");
    }

    @Test
    public void testInvalidXml_incorrectTag() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_tag);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Unknown tag: foobar [Tags and attributes are case sensitive]");
    }

    @Test
    public void testInvalidXml_incorrectAttr() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_attr);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Unknown attribute: foobar [Tags and attributes are case sensitive]");
    }
}
