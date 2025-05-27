/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.adservices.data.enrollment;

import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.proto.PrivacySandboxApi;
import com.android.adservices.service.proto.RbEnrollment;
import com.android.adservices.service.proto.config_delivery.Configuration;
import com.android.adservices.service.proto.config_delivery.ConfigurationRecord;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;
import com.android.adservices.service.proto.config_delivery.VersionedConfiguration;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Arrays;
import java.util.List;

/** Container class for pre-enrolled Adtech enrollment data to aid testing. */
final class PreEnrolledAdTechForTest {

    static List<EnrollmentData> getList() {
        return Arrays.asList(
                SIMPLE_ENROLLMENT,
                ONE_SDK_MULTIPLE_URLS,
                SECOND_ENROLLMENT,
                TOPICS_SAMPLE_APPS,
                MSMT_SYS_HEALTH_TEST_ENROLLMENT,
                LOCAL_SERVER_FOR_MSMT_REMARKETING,
                LOCAL_SERVER_FOR_PAS);
    }

    static VersionedConfiguration getV3List() {
        return VersionedConfiguration.newBuilder()
                .setVersion(1)
                .setConfiguration(
                        Configuration.newBuilder()
                                .setConfigurationType(ConfigurationType.TYPE_RB_ENROLLMENT)
                                .addAllConfigurationRecords(
                                        List.of(
                                                SIMPLE_ENROLLMENT_V3,
                                                SECOND_ENROLLMENT_V3,
                                                TOPICS_SAMPLE_APPS_V3,
                                                MSMT_SYS_HEALTH_TEST_ENROLLMENT_V3,
                                                LOCAL_SERVER_FOR_MSMT_REMARKETING_V3,
                                                LOCAL_SERVER_FOR_PAS_V3,
                                                AR_ENROLLMENT_V3,
                                                PAS_ENROLLMENT_V3,
                                                PA_ENROLLMENT_V3,
                                                INVALID_ENROLLMENT_V3)))
                .build();
    }

    public static final ConfigurationRecord SIMPLE_ENROLLMENT_V3;
    private static final EnrollmentData SIMPLE_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E1")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk1")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test.com"))
                    .setEncryptionKeyUrl("https://test.com/keys")
                    .build();

    // Multiple URLS are deprecated and not supported in Enrollment V3
    public static final EnrollmentData ONE_SDK_MULTIPLE_URLS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E2")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk2")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://test2.com/source", "https://testtest.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://test2.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test2.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/keys")
                    .build();

    public static final ConfigurationRecord SECOND_ENROLLMENT_V3;
    private static final EnrollmentData SECOND_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E3")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk3")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test3.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://test3.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test3.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test3.com"))
                    .setEncryptionKeyUrl("https://test3.com/keys")
                    .build();

    public static final ConfigurationRecord TOPICS_SAMPLE_APPS_V3;
    private static final EnrollmentData TOPICS_SAMPLE_APPS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E4")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_TOPICS")
                    .setSdkNames(
                            Arrays.asList(
                                    "SdkName1", "SdkName2", "SdkName3", "SdkName4", "SdkName5"))
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://test.com"))
                    .setEncryptionKeyUrl("https://test.com/keys")
                    .build();

    public static final ConfigurationRecord MSMT_SYS_HEALTH_TEST_ENROLLMENT_V3;
    private static final EnrollmentData MSMT_SYS_HEALTH_TEST_ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E5")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://rb-measurement.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://rb-measurement.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://rb-measurement.com:38383"))
                    .build();

    public static final ConfigurationRecord LOCAL_SERVER_FOR_MSMT_REMARKETING_V3;
    private static final EnrollmentData LOCAL_SERVER_FOR_MSMT_REMARKETING =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E6")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://localhost:8080/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://localhost:8080/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://localhost:8080"))
                    .setRemarketingResponseBasedRegistrationUrl(
                            Arrays.asList("https://localhost:8080"))
                    .build();

    public static final ConfigurationRecord LOCAL_SERVER_FOR_PAS_V3;
    private static final EnrollmentData LOCAL_SERVER_FOR_PAS =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E7")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setEncryptionKeyUrl("https://localhost")
                    .build();

    public static final ConfigurationRecord AR_ENROLLMENT_V3;
    public static final ConfigurationRecord INVALID_ENROLLMENT_V3;
    public static final ConfigurationRecord PAS_ENROLLMENT_V3;
    public static final ConfigurationRecord PA_ENROLLMENT_V3;

    static {
        try {
            SIMPLE_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E1")
                            .addLabels("API:ATTRIBUTION_REPORTING")
                            .addLabels("SITE:https://test.com")
                            .addLabels("SDK:sdk3")
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING)
                                                    .addSdkNames("sdk1")
                                                    .setEnrolledSite("https://test.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            SECOND_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E3")
                            .addLabels("API:ATTRIBUTION_REPORTING")
                            .addLabels("SITE:https://test.com")
                            .addLabels("SDK:sdk3")
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING)
                                                    .addSdkNames("sdk3")
                                                    .setEnrolledSite("https://test.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            TOPICS_SAMPLE_APPS_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E4")
                            .addAllLabels(
                                    List.of(
                                            "API:TOPICS",
                                            "SITE:https://test.com",
                                            "SDK:sdkname1",
                                            "SDK:sdkname2",
                                            "SDK:sdkname3",
                                            "SDK:sdkname4",
                                            "SDK:sdkname5"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_TOPICS)
                                                    .addAllSdkNames(
                                                            List.of(
                                                                    "SdkName1",
                                                                    "SdkName2",
                                                                    "SdkName3",
                                                                    "SdkName4",
                                                                    "SdkName5"))
                                                    .setEnrolledSite("https://test.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            MSMT_SYS_HEALTH_TEST_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E5")
                            .addAllLabels(
                                    List.of(
                                            "API:ATTRIBUTION_REPORTING",
                                            "SITE:https://rb-measurement.com"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING)
                                                    .setEnrolledSite("https://rb-measurement.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            LOCAL_SERVER_FOR_MSMT_REMARKETING_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E6")
                            .addAllLabels(
                                    List.of(
                                            "API:ATTRIBUTION_REPORTING",
                                            "SITE:https://localhost:8080"))
                            // TODO: CHECK IF PORT NUMBER IS NEEDED IN TEST
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING)
                                                    .setEnrolledSite("https://localhost:8080")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            LOCAL_SERVER_FOR_PAS_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E7")
                            .addAllLabels(
                                    List.of("API:PROTECTED_APP_SIGNALS", "SITE:https://localhost"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS)
                                                    .setEnrolledSite("https://localhost")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            AR_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E8")
                            .addAllLabels(
                                    List.of(
                                            "API:ATTRIBUTION_REPORTING",
                                            "SITE:https://google-analytics.com"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING)
                                                    .setEnrolledSite("https://google-analytics.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            INVALID_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E9")
                            .addAllLabels(
                                    List.of(
                                            "API:ATTRIBUTION_REPORTING",
                                            "SITE:https://example.invalid"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING)
                                                    .setEnrolledSite("https://example.invalid")
                                                    .build()
                                                    .toByteArray()))
                            .build();

            PAS_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E10")
                            .addAllLabels(
                                    List.of(
                                            "API:PROTECTED_APP_SIGNALS",
                                            "SITE:https://pas-test.com"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS)
                                                    .setEnrolledSite("https://pas-test.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();
            PA_ENROLLMENT_V3 =
                    ConfigurationRecord.newBuilder()
                            .setId("E11")
                            .addAllLabels(
                                    List.of("API:PROTECTED_AUDIENCE", "SITE:https://pa-test.com"))
                            .setValue(
                                    Any.parseFrom(
                                            RbEnrollment.newBuilder()
                                                    .addEnrolledApis(
                                                            PrivacySandboxApi
                                                                    .PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE)
                                                    .setEnrolledSite("https://pa-test.com")
                                                    .build()
                                                    .toByteArray()))
                            .build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
