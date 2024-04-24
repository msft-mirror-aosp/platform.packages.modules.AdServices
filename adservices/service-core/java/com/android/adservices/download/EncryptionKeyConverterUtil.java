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

package com.android.adservices.download;

import static com.android.adservices.service.common.JsonUtils.getStringFromJson;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_INCORRECT_JSON_VERSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.encryptionkey.EncryptionKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Util class to convert JSON string to {@link EncryptionKey} based on version. */
public final class EncryptionKeyConverterUtil {
    private static final LoggerFactory.Logger LOGGER = LoggerFactory.getLogger();
    private static final int VERSION_3 = 3;

    private static final String KEY_TYPE_KEY = "keyType";
    private static final String ENROLLMENT_ID_KEY = "enrollmentId";
    private static final String REPORTING_ORIGIN_KEY = "reportingOrigin";
    private static final String ENCRYPTION_KEY_URL_KEY = "encryptionKeyUrl";
    private static final String PROTOCOL_TYPE_KEY = "protocolType";
    private static final String KEY_ID_KEY = "keyId";
    private static final String BODY_KEY = "body";
    private static final String EXPIRATION_KEY = "expiration";
    private static final String VERSION_KEY = "version";

    /**
     * Converts {@link JSONObject} to the corresponding {@link EncryptionKey}.
     *
     * <p>Returns {@link Optional#empty()} if conversion fails.
     */
    static Optional<EncryptionKey> createEncryptionKeyFromJson(JSONObject jsonObject) {
        try {
            if (jsonObject.has(VERSION_KEY)) {
                // Only version 3 is supported.
                int version = jsonObject.getInt(VERSION_KEY);
                if (version == VERSION_3) {
                    return convertVersion3Key(jsonObject);
                } else {
                    ErrorLogUtil.e(
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_INCORRECT_JSON_VERSION,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
                    LOGGER.d("Unsupported encryption key version %d", version);
                }
            }
        } catch (JSONException e) {
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
        return Optional.empty();
    }

    /** Converts version 3 {@link JSONObject} to the corresponding {@link EncryptionKey}. */
    private static Optional<EncryptionKey> convertVersion3Key(JSONObject jsonObject) {
        /* Expected Version 3 JSON example:
         * { "keyType": "Signing", "enrollmentId": "TEST0", "reportingOrigin":
         * "https://adtech.example.com/reporting", "encryptionKeyUrl":
         * "https://adtech.example.com/keys/fetch/98765", "protocolType": "ECDSA", "keyId": 98765,
         * "body": "MIIBCgKCAQEAwVACPi9w23nBqQn+BTdRSmYXZGUtIuEhJhTVUZCmYR
         * +BNdKaIbMnzj2nXmG7nXkJaWUYnmRRhk2XnTKSWbMVzpEtqSIrGxuZUXH9DQ
         * +3VJXO0BQvScj9KQVcrKPpTZeJrAjj8aS8FmI2+tQvSwFUJNQvPgOODYtveSZVC5YH+kIWC2LSFJQYrGBqDf
         * +IqhQPjwFUO4T7NUzl7++YigOzMOgAoO5+SxWGOjPNsWt26CkGNzYv+8IfeYZJG3LQs+38dhCSG1qA==",
         * "expiration": 1682516522000, "version": 3 }
         */
        try {
            EncryptionKey.Builder builder = new EncryptionKey.Builder();
            // Generate UUID for this key
            builder.setId(UUID.randomUUID().toString());
            builder.setKeyType(
                    EncryptionKey.KeyType.valueOf(
                            getStringFromJson(jsonObject, KEY_TYPE_KEY).toUpperCase(Locale.ROOT)));
            builder.setEnrollmentId(getStringFromJson(jsonObject, ENROLLMENT_ID_KEY));
            builder.setReportingOrigin(
                    Uri.parse(getStringFromJson(jsonObject, REPORTING_ORIGIN_KEY)));
            builder.setEncryptionKeyUrl(getStringFromJson(jsonObject, ENCRYPTION_KEY_URL_KEY));
            builder.setProtocolType(
                    EncryptionKey.ProtocolType.valueOf(
                            getStringFromJson(jsonObject, PROTOCOL_TYPE_KEY)
                                    .toUpperCase(Locale.ROOT)));
            builder.setKeyCommitmentId(jsonObject.getInt(KEY_ID_KEY));
            builder.setBody(getStringFromJson(jsonObject, BODY_KEY));
            builder.setExpiration(jsonObject.getLong(EXPIRATION_KEY));
            builder.setLastFetchTime(System.currentTimeMillis());

            EncryptionKey encryptionKey = builder.build();
            LOGGER.v(
                    "Successfully built EncryptionKey = %s for enrollment id = %s",
                    encryptionKey.getBody(), encryptionKey.getEnrollmentId());
            return Optional.of(encryptionKey);
        } catch (JSONException | IllegalArgumentException e) {
            LOGGER.e(e, "Failed parsing for %s", jsonObject);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            return Optional.empty();
        }
    }
}
