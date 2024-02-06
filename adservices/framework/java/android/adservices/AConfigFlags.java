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

package android.adservices;

/**
 * @deprecated this class is not used and will be replaced by {@code aconfig}
 * @hide
 */
@Deprecated
public final class AConfigFlags {
    public static final String FLAG_AD_ID_CACHE_ENABLED =
            "com.android.adservices.flags.ad_id_cache_enabled";
    public static final String FLAG_ENABLE_ADSERVICES_API_ENABLED =
            "com.android.adservices.flags.enable_adservices_api_enabled";
    public static final String FLAG_ADSERVICES_ENABLEMENT_CHECK_ENABLED =
            "com.android.adservices.flags.adservices_enablement_check_enabled";
    public static final String FLAG_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED =
            "com.android.adservices.flags.adservices_outcomereceiver_r_api_enabled";
    public static final String FLAG_ADEXT_DATA_SERVICE_APIS_ENABLED =
            "com.android.adservices.flags.adext_data_service_apis_enabled";
    public static final String FLAG_TOPICS_ENCRYPTION_ENABLED =
            "com.android.adservices.flags.topics_encryption_enabled";
    public static final String FLAG_FLEDGE_AD_SELECTION_FILTERING_ENABLED =
            "com.android.adservices.flags.fledge_ad_selection_filtering_enabled";
    public static final String FLAG_PROTECTED_SIGNALS_ENABLED =
            "com.android.adservices.flags.protected_signals_enabled";

    private AConfigFlags() {
        throw new UnsupportedOperationException("provides only constants");
    }
}
