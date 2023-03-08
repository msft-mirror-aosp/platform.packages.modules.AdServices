# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_PACKAGE_NAME := AdServicesScenarioTests
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_STATIC_JAVA_LIBRARIES := \
        androidx.test.runner \
        androidx.test.rules \
        collector-device-lib-platform \
        common-platform-scenarios \
        common-platform-scenario-tests \
        guava \
        health-testing-utils \
        microbenchmark-device-lib \
        platform-test-options \
        platform-test-rules \
        adservices-test-scenarios \
        ub-uiautomator \
        launcher-aosp-tapl \
        handheld-app-helpers \
        framework-adservices-lib \
        adservices-device-collectors-lib \
        hamcrest-library

LOCAL_CERTIFICATE := platform
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_SRC_FILES := $(call all-java-files-under, src/)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/../util/hawkeye/res $(LOCAL_PATH)/res
LOCAL_COMPATIBILITY_SUITE := device-tests

LOCAL_MIN_SDK_VERSION := 24

include $(BUILD_PACKAGE)
