/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.service;

import static com.android.adservices.flags.Flags.FLAG_ADEXT_DATA_SERVICE_APIS_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_ADSERVICES_ENABLEMENT_CHECK_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_AD_ID_CACHE_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_ENABLE_ADSERVICES_API_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_AD_SELECTION_FILTERING_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_TOPICS_ENCRYPTION_ENABLED;
import static com.android.adservices.service.FlagsConstants.ACONFIG_PREFIX;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLEMENT_CHECK_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_AD_ID_CACHE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ADEXT_DATA_SERVICE_APIS;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ADSERVICES_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_ENCRYPTION_ENABLED;
import static com.android.adservices.service.FlagsConstants.aconfigToDeviceConfig;

import static org.junit.Assert.assertThrows;

import android.util.Pair;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class FlagsConstantsTest extends AdServicesUnitTestCase {

    @Test
    public void testNoFlagHasTheAConfigPrefix() throws Exception {
        for (Pair<String, String> constant : getAllFlagNameConstants()) {
            String name = constant.first;
            String value = constant.second;
            expect.withMessage(
                            "Value (%s) of constants %s starts with prefix %s",
                            value, name, ACONFIG_PREFIX)
                    .that(value.startsWith(ACONFIG_PREFIX))
                    .isFalse();
        }
    }

    /** Tests flags whose initial value were hardcoded (but now calls aconfigToDeviceConfig()). */
    @Test
    public void testAconfigToDeviceConfig_legacyFlags() {
        expect.withMessage("KEY_AD_ID_CACHE_ENABLED")
                .that(KEY_AD_ID_CACHE_ENABLED)
                .isEqualTo("ad_id_cache_enabled");
        expect.withMessage("KEY_ENABLE_ADSERVICES_API_ENABLED")
                .that(KEY_ENABLE_ADSERVICES_API_ENABLED)
                .isEqualTo("enable_adservices_api_enabled");
        expect.withMessage("KEY_ADSERVICES_ENABLEMENT_CHECK_ENABLED")
                .that(KEY_ADSERVICES_ENABLEMENT_CHECK_ENABLED)
                .isEqualTo("adservices_enablement_check_enabled");
        expect.withMessage("KEY_ENABLE_ADEXT_DATA_SERVICE_APIS")
                .that(KEY_ENABLE_ADEXT_DATA_SERVICE_APIS)
                .isEqualTo("adext_data_service_apis_enabled");
        expect.withMessage("KEY_TOPICS_ENCRYPTION_ENABLED")
                .that(KEY_TOPICS_ENCRYPTION_ENABLED)
                .isEqualTo("topics_encryption_enabled");
        expect.withMessage("KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED")
                .that(KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED)
                .isEqualTo("fledge_ad_selection_filtering_enabled");
        expect.withMessage("KEY_PROTECTED_SIGNALS_ENABLED")
                .that(KEY_PROTECTED_SIGNALS_ENABLED)
                .isEqualTo("protected_signals_enabled");
    }

    // TODO(b/318891959): temporary test until refactoring constants to use aconfigToDeviceConfig -
    // this test makes sure changing them won't break the current value
    @Test
    @Deprecated
    public void testFlagsThatWillBeRefactoredToUseAconfigToDeviceConfig() {
        expect.withMessage("KEY_AD_ID_CACHE_ENABLED")
                .that(KEY_AD_ID_CACHE_ENABLED)
                .isEqualTo(aconfigToDeviceConfig(FLAG_AD_ID_CACHE_ENABLED));
        expect.withMessage("KEY_ENABLE_ADSERVICES_API_ENABLED")
                .that(KEY_ENABLE_ADSERVICES_API_ENABLED)
                .isEqualTo(aconfigToDeviceConfig(FLAG_ENABLE_ADSERVICES_API_ENABLED));
        expect.withMessage("KEY_ADSERVICES_ENABLEMENT_CHECK_ENABLED")
                .that(KEY_ADSERVICES_ENABLEMENT_CHECK_ENABLED)
                .isEqualTo(aconfigToDeviceConfig(FLAG_ADSERVICES_ENABLEMENT_CHECK_ENABLED));
        expect.withMessage("KEY_ENABLE_ADEXT_DATA_SERVICE_APIS")
                .that(KEY_ENABLE_ADEXT_DATA_SERVICE_APIS)
                .isEqualTo(aconfigToDeviceConfig(FLAG_ADEXT_DATA_SERVICE_APIS_ENABLED));
        expect.withMessage("KEY_TOPICS_ENCRYPTION_ENABLED")
                .that(KEY_TOPICS_ENCRYPTION_ENABLED)
                .isEqualTo(aconfigToDeviceConfig(FLAG_TOPICS_ENCRYPTION_ENABLED));
        expect.withMessage("KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED")
                .that(KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED)
                .isEqualTo(aconfigToDeviceConfig(FLAG_FLEDGE_AD_SELECTION_FILTERING_ENABLED));
        expect.withMessage("KEY_PROTECTED_SIGNALS_ENABLED")
                .that(KEY_PROTECTED_SIGNALS_ENABLED)
                .isEqualTo(aconfigToDeviceConfig(FLAG_PROTECTED_SIGNALS_ENABLED));
    }

    @Test
    public void testAconfigToDeviceConfig_null() {
        assertThrows(NullPointerException.class, () -> aconfigToDeviceConfig(null));
    }

    @Test
    public void testAconfigToDeviceConfig() {
        String noPrefix = "D'OH!";
        expect.withMessage("aconfigToDeviceConfig(%s)", noPrefix)
                .that(aconfigToDeviceConfig(noPrefix))
                .isEqualTo(noPrefix);

        String nonAconfigPrefix = "annoyed.grunt.D'OH!";
        expect.withMessage("aconfigToDeviceConfig(%s)", nonAconfigPrefix)
                .that(aconfigToDeviceConfig(nonAconfigPrefix))
                .isEqualTo(nonAconfigPrefix);

        String aconfigPrefix = ACONFIG_PREFIX + noPrefix;
        expect.withMessage("aconfigToDeviceConfig(%s)", aconfigPrefix)
                .that(aconfigToDeviceConfig(aconfigPrefix))
                .isEqualTo(noPrefix);
    }

    private static List<Pair<String, String>> getAllFlagNameConstants()
            throws IllegalAccessException {
        List<Pair<String, String>> constants = new ArrayList<>();
        for (Field field : FlagsConstants.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && (field.getType().equals(String.class))) {
                String name = field.getName();
                if (name.startsWith("KEY_") || name.startsWith("FLAG_")) {
                    String value = (String) field.get(null);
                    constants.add(new Pair<>(name, value));
                }
            }
        }
        return constants;
    }
}
