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

LOCAL_MODULE := adservices-test-scenarios
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_STATIC_JAVA_LIBRARIES := \
        adservices-clients \
        mockwebserver \
        platform-test-annotations
LOCAL_JAVA_LIBRARIES := \
        framework-adservices-lib \
        androidx.test.rules \
        androidx.test.runner \
        app-helpers-handheld-interfaces \
        guava \
        platform-test-rules \
        health-testing-utils \
        microbenchmark-device-lib \
        ub-uiautomator \
        common-platform-scenarios \
        launcher-aosp-tapl \
        platform-test-options \
        hamcrest-library \
        androidx.media_media \
        mockwebserver \
        modules-utils-testable-device-config

LOCAL_SRC_FILES := $(call all-java-files-under, src)

include $(BUILD_STATIC_JAVA_LIBRARY)

######################################

include $(call all-makefiles-under, $(LOCAL_PATH))
