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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link SandboxLatencyInfo} APIs. */
@RunWith(JUnit4.class)
public class SandboxLatencyInfoUnitTest {

    private static final long TIME_SYSTEM_SERVER_CALLED_SANDBOX = 1;
    private static final long TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER = 2;
    private static final long TIME_SANDBOX_CALLED_SDK = 3;
    private static final long TIME_SDK_CALL_COMPLETED = 4;
    private static final long TIME_SANDBOX_CALLED_SYSTEM_SERVER = 5;
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 6;
    private static final long TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER = 7;

    @Test
    public void testSandboxLatencyInfo_describeContents() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testSandboxLatencyInfo_isParcelable() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        final Parcel sandboxLatencyInfoParcel = Parcel.obtain();
        sandboxLatencyInfo.writeToParcel(sandboxLatencyInfoParcel, /*flags=*/ 0);

        sandboxLatencyInfoParcel.setDataPosition(0);
        final SandboxLatencyInfo sandboxLatencyInfoCreatedWithParcel =
                SandboxLatencyInfo.CREATOR.createFromParcel(sandboxLatencyInfoParcel);

        assertThat(sandboxLatencyInfo).isEqualTo(sandboxLatencyInfoCreatedWithParcel);
    }

    @Test
    public void testMethodIsSet() {
        final SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(SandboxLatencyInfo.METHOD_LOAD_SDK);
    }

    @Test
    public void testMethodIsUnspecified() {
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(SandboxLatencyInfo.METHOD_UNSPECIFIED);
    }

    @Test
    public void testSandboxLatencyInfo_getLatencySystemServerToSandbox() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        final int systemServerToSandboxLatency =
                (int)
                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                - TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        assertThat(sandboxLatencyInfo.getLatencySystemServerToSandbox())
                .isEqualTo(systemServerToSandboxLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSandboxLatency() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        final int sdkLatency = (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK);
        final int sandboxLatency =
                (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER)
                        - sdkLatency;
        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSandboxLatency_timeFieldsNotSetForSdk() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Reset the values
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        final int sandboxLatency =
                (int)
                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSdkLatency() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        final int sdkLatency = (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK);
        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(sdkLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSdkLatency_timeFieldsNotSetForSdk() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Reset the values
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(-1);
    }

    @Test
    public void testSandboxLatencyInfo_getSystemServerToAppLatency() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        final int systemServerToAppLatency =
                (int) (TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER - TIME_SYSTEM_SERVER_CALLED_APP);
        assertThat(sandboxLatencyInfo.getSystemServerToAppLatency())
                .isEqualTo(systemServerToAppLatency);
    }

    @Test
    public void testSandboxLatencyInfo_isSuccessfulAtSdk() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSdk() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SDK);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isFalse();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_isSuccessfulAtSandbox() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSandbox() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isFalse();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_isSuccessfulAtSystemServerToApp() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSystemServerToApp() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_TO_APP);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isFalse();
    }

    @Test
    public void testGetTimeSystemServerCalledSandbox() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.getTimeSystemServerCalledSandbox())
                .isEqualTo(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
    }

    @Test
    public void testGetTimeSandboxCalledSystemServer() {
        final SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    private SandboxLatencyInfo getSandboxLatencyObjectWithAllFieldsSet() {
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCalledSandbox(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(TIME_SANDBOX_CALLED_SDK);
        sandboxLatencyInfo.setTimeSdkCallCompleted(TIME_SDK_CALL_COMPLETED);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSystemServerCalledApp(TIME_SYSTEM_SERVER_CALLED_APP);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        return sandboxLatencyInfo;
    }
}
