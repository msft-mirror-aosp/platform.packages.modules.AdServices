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

import android.annotation.NonNull;
import android.content.res.XmlResourceParser;

import com.android.adservices.service.exception.XmlParseException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Parser and validator for {@link AppManifestConfig} objects. */
public class AppManifestConfigParser {
    private static final String TAG_AD_SERVICES_CONFIG = "ad-services-config";
    private static final String TAG_ATTRIBUTION = "attribution";
    private static final String TAG_CUSTOM_AUDIENCES = "custom-audiences";
    private static final String TAG_TOPICS = "topics";
    private static final String TAG_ADID = "adid";
    private static final String ATTR_ALLOW_ALL_TO_ACCESS = "allowAllToAccess";
    private static final String ATTR_ALLOW_AD_PARTNERS_TO_ACCESS = "allowAdPartnersToAccess";

    private AppManifestConfigParser() {}

    /**
     * Parses and validates the given XML resource into a {@link AppManifestConfig} object.
     *
     * <p>It throws a {@link XmlParseException} if the given XML resource does not comply with the
     * app_manifest_config.xsd schema.
     *
     * @param parser the XmlParser representing the AdServices App Manifest configuration
     */
    public static AppManifestConfig getConfig(@NonNull XmlResourceParser parser)
            throws XmlParseException, XmlPullParserException, IOException {
        AppManifestAttributionConfig attributionConfig = null;
        AppManifestCustomAudiencesConfig customAudiencesConfig = null;
        AppManifestTopicsConfig topicsConfig = null;
        AppManifestAdIdConfig adIdConfig = null;

        // The first next goes to START_DOCUMENT, so we need another next to go to START_TAG.
        parser.next();
        parser.next();
        parser.require(XmlPullParser.START_TAG, null, TAG_AD_SERVICES_CONFIG);
        parser.next();

        // Walk through the config to parse required values.
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlResourceParser.START_TAG) {
                parser.next();
                continue;
            }
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                String attr = parser.getAttributeName(i);
                if (!attr.equals(ATTR_ALLOW_ALL_TO_ACCESS)
                        && !attr.equals(ATTR_ALLOW_AD_PARTNERS_TO_ACCESS)) {
                    throw new XmlParseException(
                            "Unknown attribute: "
                                    + attr
                                    + " [Tags and attributes are case sensitive]");
                }
            }
            switch (parser.getName()) {
                case TAG_AD_SERVICES_CONFIG:
                    break;

                case TAG_ATTRIBUTION:
                    boolean allowAllToAccess = getAllowAllToAccess(parser);
                    if (attributionConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    attributionConfig =
                            new AppManifestAttributionConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_CUSTOM_AUDIENCES:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (customAudiencesConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    customAudiencesConfig =
                            new AppManifestCustomAudiencesConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_TOPICS:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (topicsConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    topicsConfig =
                            new AppManifestTopicsConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_ADID:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (adIdConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    adIdConfig =
                            new AppManifestAdIdConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                default:
                    throw new XmlParseException(
                            "Unknown tag: "
                                    + parser.getName()
                                    + " [Tags and attributes are case sensitive]");
            }
            parser.next();
        }

        return new AppManifestConfig(
                attributionConfig, customAudiencesConfig, topicsConfig, adIdConfig);
    }

    private static boolean getAllowAllToAccess(@NonNull XmlResourceParser parser) {
        String allowAllToAccess =
                parser.getAttributeValue(/* namespace */ null, ATTR_ALLOW_ALL_TO_ACCESS);
        return allowAllToAccess.equals("false") ? false : true;
    }

    private static List<String> getAllowAdPartnersToAccess(
            @NonNull XmlResourceParser parser, @NonNull boolean allowAllToAccess)
            throws XmlParseException {
        String allowAdPartnersToAccess =
                parser.getAttributeValue(null, ATTR_ALLOW_AD_PARTNERS_TO_ACCESS);
        if (allowAdPartnersToAccess == null || allowAdPartnersToAccess.isEmpty()) {
            return new ArrayList<>();
        }
        if (allowAllToAccess) {
            throw new XmlParseException(
                    "allowAll cannot be set to true when allowAdPartners is also set");
        }
        return Arrays.asList(allowAdPartnersToAccess.split("\\s*,\\s*"));
    }
}
