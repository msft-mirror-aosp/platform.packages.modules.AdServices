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

package android.adservices;

/**
 * Class to store flags related constants.
 *
 * <p>The constants are cloned from service side's {@code FlagsConstants} class.
 *
 * @hide
 */
// TODO(b/302041492): Extend FlagsConstants class to framework package.
public final class FlagsConstants {
    public static final String KEY_AD_ID_CACHE_ENABLED =
            "com.android.adservices.flags.ad_id_cache_enabled";
    public static final String KEY_ENABLE_ADSERVICES_API_ENABLED =
            "com.android.adservices.flags.enable_adservices_api_enabled";
    public static final String KEY_ADSERVICES_ENABLEMENT_CHECK_ENABLED =
            "com.android.adservices.flags.adservices_enablement_check_enabled";
    public static final String KEY_ADSERVICES_OUTCOMERECEIVER_R_API_ENABLED =
            "com.android.adservices.flags.adservices_outcomereceiver_r_api_enabled";
    public static final String KEY_ENABLE_ADEXT_DATA_SERVICE_APIS =
            "com.android.adservices.flags.adext_data_service_apis_enabled";
    public static final String KEY_TOPICS_ENCRYPTION_ENABLED =
            "com.android.adservices.flags.topics_encryption_enabled";
    public static final String KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED =
            "com.android.adservices.flags.fledge_ad_selection_filtering_enabled";
    public static final String KEY_PROTECTED_SIGNALS_ENABLED =
            "com.android.adservices.flags.protected_signals_enabled";
}
