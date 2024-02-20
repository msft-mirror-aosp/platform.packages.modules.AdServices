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

package com.android.adservices.flags;

/**
 * This class is not auto-merged into {@code main} - to add a new constant here, you <b>MUST</b>
 * follow the process below:
 *
 * <p><b>NOTE</b>these flags are only set on <code>@FlaggedApi</code> annotations, not by {@code
 * DeviceConfig}. Hence, it's expected that a flag with the same name (but without the prefix)
 * exists on {@link com.android.adservices.service.FlagsConstants}; if there isn't one yet, you'll
 * need to add the new flag to one of the constants on {@link
 * com.android.adservices.service.FlagsConstantsTest} (in both steps 1 and 2 below), and properly
 * fix it later.
 *
 * <ol>
 *   <li>Add a new flag to {@code adservices/flags/adservices_flags.aconfig} on {@code main} first.
 *       Make sure to include a {@code Test: atest
 *       AdServicesServiceCoreUnitTests:FlagsConstantsTest} in the CL (and to run the test before
 *       uploading it).
 *   <li>Add it here in a CL that's {@code Merged-In:} the same CL as main (and preferably using the
 *       same {@code Change-Id:}. Add the same {@code Test:} line.
 *   <li><b>THEN</b> you can add code that uses the flag (for example, adding new flags on {@link
 *       com.android.adservices.service.Flags} and {@link com.android.adservices.service.PhFlags} -
 *       see examples on those classes).
 * </ol>
 *
 * @hide
 */
public final class Flags {

    private static final String ACONFIG_PREFIX = "com.android.adservices.flags.";

    public static final String FLAG_AD_ID_CACHE_ENABLED = ACONFIG_PREFIX + "ad_id_cache_enabled";
    public static final String FLAG_ENABLE_ADSERVICES_API_ENABLED =
            ACONFIG_PREFIX + "enable_adservices_api_enabled";
    public static final String FLAG_ADSERVICES_ENABLEMENT_CHECK_ENABLED =
            ACONFIG_PREFIX + "adservices_enablement_check_enabled";
    public static final String FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED =
            ACONFIG_PREFIX + "adservices_outcomereceiver_r_api_enabled";
    public static final String FLAG_ADEXT_DATA_SERVICE_APIS_ENABLED =
            ACONFIG_PREFIX + "adext_data_service_apis_enabled";
    public static final String FLAG_TOPICS_ENCRYPTION_ENABLED =
            ACONFIG_PREFIX + "topics_encryption_enabled";
    public static final String FLAG_FLEDGE_AD_SELECTION_FILTERING_ENABLED =
            ACONFIG_PREFIX + "fledge_ad_selection_filtering_enabled";
    public static final String FLAG_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED =
            ACONFIG_PREFIX + "fledge_schedule_custom_audience_update_enabled";
    public static final String FLAG_FLEDGE_SERVER_AUCTION_MULTI_CLOUD_ENABLED =
            ACONFIG_PREFIX + "fledge_server_auction_multi_cloud_enabled";
    public static final String FLAG_PROTECTED_SIGNALS_ENABLED =
            ACONFIG_PREFIX + "protected_signals_enabled";
    public static final String FLAG_GET_ADSERVICES_COMMON_STATES_API_ENABLED =
            ACONFIG_PREFIX + "get_adservices_common_states_api_enabled";
    public static final String FLAG_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_ID_ENABLED =
            ACONFIG_PREFIX + "fledge_auction_server_get_ad_selection_data_id_enabled";
    public static final String FLAG_FLEDGE_CUSTOM_AUDIENCE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED =
            ACONFIG_PREFIX + "fledge_custom_audience_auction_server_request_flags_enabled";

    private Flags() {
        throw new UnsupportedOperationException("provides only constants");
    }
}
