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

import static com.android.adservices.flags.Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API;
import static com.android.adservices.flags.Flags.FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_AD_SELECTION_FILTERING_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_ID_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_CUSTOM_AUDIENCE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_FLEDGE_SERVER_AUCTION_MULTI_CLOUD_ENABLED;
import static com.android.adservices.flags.Flags.FLAG_SDKSANDBOX_DUMP_EFFECTIVE_TARGET_SDK_VERSION;
import static com.android.adservices.flags.Flags.FLAG_SDKSANDBOX_INVALIDATE_EFFECTIVE_TARGET_SDK_VERSION_CACHE;

import android.util.Log;
import android.util.Pair;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.internal.util.Preconditions;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// NOTE: when making changes on com.android.adservices.flags.Flags, you need to install the new
// apex - just running atest wouldn't affect the test result
public final class FlagsConstantsTest extends AdServicesUnitTestCase {

    private static final String ACONFIG_PREFIX = "com.android.adservices.flags.";

    private static final String HOW_TO_FIX_IT_MESSAGE =
            "If this is expected, you might need to change MISSING_FLAGS_ALLOWLIST or"
                    + " NON_CANONICAL_FLAGS (on this file).";

    /**
     * List used by {@link #testAllAconfigFlagsAreMapped()}, it contains the name of flags that are
     * present in the {@code aconfig} file but are missing on {@link FlagsConstants}.
     *
     * <p>Add more entries in the bottom, either explaining the reason or using a TODO(b/BUG) that
     * will add the missing {@link com.android.adservices.service.PhFlags} / {@link
     * com.android.adservices.service.FlagsConstants} counterpart.
     */
    private static final List<String> MISSING_FLAGS_ALLOWLIST =
            List.of(
                    // FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED guards APIs that are overloaded
                    // to take android.adservices.common.OutcomeReceiver (instead of
                    // android.os.OutcomeReceiver) and hence don't need to be checked at runtime.
                    FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED,

                    // TODO(b/347764094): Remove from this allowlist after implementing feature and
                    //  adding matching DeviceConfig flag
                    FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API,

                    // TODO(b/323397060): Remove from this allowlist after implementing feature and
                    //  adding matching DeviceConfig flag
                    FLAG_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_ID_ENABLED,

                    // The DeviceConfig flag guarding this feature is intentionally named
                    // differently so that it is not scoped to the Custom Audience API.
                    FLAG_FLEDGE_CUSTOM_AUDIENCE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED,

                    // There used to be a matching DeviceConfig flag, but it guarded too many
                    // features.  Because the feature APIs are unhidden and published already, they
                    // cannot be changed.  The old DeviceConfig flag has instead been removed and
                    // split into individual feature flags to allow each feature to launch
                    // independently.
                    FLAG_FLEDGE_AD_SELECTION_FILTERING_ENABLED,

                    // This flag is to guard a feature for trunk stable purpose. The flag guards the
                    // invalidation of the effective target SDK version cache. If any regression is
                    // observed, this feature can be rolled back
                    FLAG_SDKSANDBOX_INVALIDATE_EFFECTIVE_TARGET_SDK_VERSION_CACHE,

                    // This flag is to guard a feature for trunk stable purpose. The flag guards the
                    // dump function to include the effective target SDK version cache. If any
                    // regression is observed, this feature can be rolled back
                    FLAG_SDKSANDBOX_DUMP_EFFECTIVE_TARGET_SDK_VERSION);

    /**
     * Map used by {@link #testAllAconfigFlagsAreMapped()} - key is the {@code aconfig} flag name,
     * value is the {@link com.android.adservices.service.FlagsConstants} counterpart (i.e, the
     * value defined by the constant, which is the key to a {@link android.provider.DeviceConfig}
     * flag).
     *
     * <p>Flag names on {@link com.android.adservices.service.FlagsConstants} are expect to have the
     * same name (minus prefix) as the {@code aconfig} counterpart, but there are a few exceptions
     * like:
     *
     * <ul>
     *   <li>{@link android.provider.DeviceConfig} flag already pushed to production.
     *   <li>Same {@link android.provider.DeviceConfig} flag is guarding multiple APIs using
     *       different {@code aconfig} flags on their <code>@FlaggedApi</code> annotations.
     * </ul>
     *
     * <p>Add more entries in the bottom, either explaining the reason or using a TODO(b/BUG) that
     * will add the missing {@link com.android.adservices.service.PhFlags} / {@link
     * com.android.adservices.service.FlagsConstants} counterpart.
     */
    private static final Map<String, String> NON_CANONICAL_FLAGS =
            Map.of(
                    // DeviceConfig flags for PA/FLEDGE are named "auction_server" instead of
                    // "server_auction."  This API has already been released, and the aconfig flag
                    // cannot be renamed, so this mismatch is intentional.
                    FLAG_FLEDGE_SERVER_AUCTION_MULTI_CLOUD_ENABLED,
                    FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_MULTI_CLOUD_ENABLED);

    @Test
    public void testNoFlagHasTheAConfigPrefix() throws Exception {
        for (Pair<String, String> constant : getAllFlagNameConstants(FlagsConstants.class)) {
            String name = constant.first;
            String value = constant.second;
            expect.withMessage(
                            "Value (%s) of constants %s starts with prefix %s",
                            value, name, ACONFIG_PREFIX)
                    .that(value.startsWith(ACONFIG_PREFIX))
                    .isFalse();
        }
    }

    @Test
    public void testAllAconfigFlagsAreMapped() throws Exception {
        Map<String, String> reverseServiceFlags =
                getAllFlagNameConstants(FlagsConstants.class).stream()
                        .collect(Collectors.toMap(p -> p.second, p -> p.first));
        List<String> missingFlags = new ArrayList<>();
        for (Pair<String, String> constant :
                getAllFlagNameConstants(com.android.adservices.flags.Flags.class)) {
            String constantName = constant.first;
            String aconfigFlag = constant.second;
            String expectedDeviceConfigFlag = getExpectedDeviceConfigFlag(aconfigFlag);
            String serviceConstant = reverseServiceFlags.get(expectedDeviceConfigFlag);
            if (serviceConstant == null) {
                if (MISSING_FLAGS_ALLOWLIST.contains(aconfigFlag)) {
                    Log.i(
                            mTag,
                            "Missing mapping for allowlisted flag ("
                                    + constantName
                                    + "="
                                    + aconfigFlag
                                    + ")");
                } else {
                    Log.e(mTag, "Missing mapping for " + constantName + "=" + aconfigFlag);
                    missingFlags.add(expectedDeviceConfigFlag);
                }
            } else {
                Log.d(mTag, "Found mapping: " + constantName + "->" + serviceConstant);
            }
        }
        expect.withMessage(
                        "aconfig flags missing counterpart on FlagsConstants. %s",
                        HOW_TO_FIX_IT_MESSAGE)
                .that(missingFlags)
                .isEmpty();
    }

    private static String aconfigToDeviceConfig(String flag) {
        Preconditions.checkArgument(
                Objects.requireNonNull(flag).startsWith(ACONFIG_PREFIX),
                "Flag doesn't start with %s: %s",
                ACONFIG_PREFIX,
                flag);
        return flag.substring(ACONFIG_PREFIX.length());
    }

    private String getExpectedDeviceConfigFlag(String aconfigFlag) {
        String nonCanonical = NON_CANONICAL_FLAGS.get(aconfigFlag);
        if (nonCanonical != null) {
            Log.i(mTag, "Returning non-canonical flag for " + aconfigFlag + ": " + nonCanonical);
            return nonCanonical;
        }
        return aconfigToDeviceConfig(aconfigFlag);
    }

    private static List<Pair<String, String>> getAllFlagNameConstants(Class<?> clazz)
            throws IllegalAccessException {
        List<Pair<String, String>> constants = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
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
