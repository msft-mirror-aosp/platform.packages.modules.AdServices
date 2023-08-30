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
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX = 6;
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 7;
    private static final long TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER = 8;

    @Test
    public void testSandboxLatencyInfo_describeContents() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testSandboxLatencyInfo_isParcelable() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        Parcel sandboxLatencyInfoParcel = Parcel.obtain();
        sandboxLatencyInfo.writeToParcel(sandboxLatencyInfoParcel, /*flags=*/ 0);

        sandboxLatencyInfoParcel.setDataPosition(0);
        SandboxLatencyInfo sandboxLatencyInfoCreatedWithParcel =
                SandboxLatencyInfo.CREATOR.createFromParcel(sandboxLatencyInfoParcel);

        assertThat(sandboxLatencyInfo).isEqualTo(sandboxLatencyInfoCreatedWithParcel);
    }

    @Test
    public void testMethodIsSet() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(SandboxLatencyInfo.METHOD_LOAD_SDK);
    }

    @Test
    public void testMethodIsUnspecified() {
        SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(SandboxLatencyInfo.METHOD_UNSPECIFIED);
    }

    @Test
    public void testSandboxLatencyInfo_getSystemServerToSandboxLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerToSandboxLatency =
                (int)
                        (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                - TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        assertThat(sandboxLatencyInfo.getSystemServerToSandboxLatency())
                .isEqualTo(systemServerToSandboxLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSandboxLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int sdkLatency = (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK);
        int sandboxLatency =
                (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER)
                        - sdkLatency;
        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSandboxLatency_timeFieldsNotSetForSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Reset the values
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        int sandboxLatency =
                (int)
                        (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSdkLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int sdkLatency = (int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK);
        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(sdkLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSdkLatency_timeFieldsNotSetForSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Reset the values
        sandboxLatencyInfo.setTimeSandboxCalledSdk(-1);
        sandboxLatencyInfo.setTimeSdkCallCompleted(-1);

        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(-1);
    }

    @Test
    public void testSandboxLatencyInfo_getSandboxToSystemServerLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int sandboxToSystemServerLatency =
                (int)
                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX
                                - TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        assertThat(sandboxLatencyInfo.getSandboxToSystemServerLatency())
                .isEqualTo(sandboxToSystemServerLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSystemServerSandboxToAppLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerSandboxToAppLatency =
                (int)
                        (TIME_SYSTEM_SERVER_CALLED_APP
                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX);
        assertThat(sandboxLatencyInfo.getSystemServerSandboxToAppLatency())
                .isEqualTo(systemServerSandboxToAppLatency);
    }

    @Test
    public void testSandboxLatencyInfo_getSystemServerToAppLatency() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        int systemServerToAppLatency =
                (int) (TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER - TIME_SYSTEM_SERVER_CALLED_APP);
        assertThat(sandboxLatencyInfo.getSystemServerToAppLatency())
                .isEqualTo(systemServerToAppLatency);
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_isSuccessfulAtSystemServerToSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSystemServerToSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_TO_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox()).isFalse();
    }

    @Test
    public void testSandboxLatencyInfo_isSuccessfulAtSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSdk() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

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
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();

        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandbox()).isFalse();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSdk()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_isSuccessfulAtSandboxToSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSandboxToSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX_TO_SYSTEM_SERVER);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer()).isFalse();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_isSuccessfulAtSystemServerSandboxToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSystemServerSandboxToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_SANDBOX_TO_APP);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp()).isFalse();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_isSuccessfulAtSystemServerToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isTrue();
    }

    @Test
    public void testSandboxLatencyInfo_sandboxStatus_failedAtSystemServerToApp() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        // Verify state before status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isTrue();

        sandboxLatencyInfo.setSandboxStatus(
                SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_TO_APP);

        // Verify state after status change
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerToApp()).isFalse();
    }

    @Test
    public void testGetTimeSystemServerCalledSandbox() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.getTimeSystemServerCalledSandbox())
                .isEqualTo(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
    }

    @Test
    public void testGetTimeSandboxCalledSystemServer() {
        SandboxLatencyInfo sandboxLatencyInfo = getSandboxLatencyObjectWithAllFieldsSet();
        assertThat(sandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    private SandboxLatencyInfo getSandboxLatencyObjectWithAllFieldsSet() {
        SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();
        sandboxLatencyInfo.setTimeSystemServerCalledSandbox(TIME_SYSTEM_SERVER_CALLED_SANDBOX);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(TIME_SANDBOX_CALLED_SDK);
        sandboxLatencyInfo.setTimeSdkCallCompleted(TIME_SDK_CALL_COMPLETED);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromSandbox(
                TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX);
        sandboxLatencyInfo.setTimeSystemServerCalledApp(TIME_SYSTEM_SERVER_CALLED_APP);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        return sandboxLatencyInfo;
    }
}
