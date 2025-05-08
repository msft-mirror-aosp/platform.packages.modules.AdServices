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

package com.android.adservices.service.measurement.access;

import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.service.devapi.DevContext;

import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.function.Supplier;

public final class DevContextAccessResolverTest extends AdServicesMockitoTestCase {

    private static final String ERROR_MESSAGE = "Developer options or dev session are not enabled.";
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://registration-uri.test");
    private static final Uri LOCALHOST = Uri.parse("https://localhost");

    @Mock private RegistrationRequest mRegistrationRequest;
    @Mock private WebSourceRegistrationRequest mWebSourceRegistrationRequest;
    @Mock private WebSourceParams mWebSourceParams;
    @Mock private WebSourceParams mWebSourceParams2;
    @Mock private WebTriggerRegistrationRequest mWebTriggerRegistrationRequest;
    @Mock private WebTriggerParams mWebTriggerParams;
    @Mock private WebTriggerParams mWebTriggerParams2;
    @Mock private SourceRegistrationRequest mSourceRegistrationRequest;

    private DevContextAccessResolver mClassUnderTest;

    @Test
    public void isAllowed_register_nonLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextEnabledSupplier(), mRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_register_nonLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextDisabledSupplier(), mRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_register_localhost_devEnabled_returnsTrue() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(LOCALHOST);
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextEnabledSupplier(), mRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_register_localhost_devDisabled_returnsFalse() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(LOCALHOST);
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextDisabledSupplier(), mRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebSource_nonLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_nonLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_allLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_someLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams())
                .thenReturn(List.of(mWebSourceParams, mWebSourceParams2));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_allLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebSource_someLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams())
                .thenReturn(List.of(mWebSourceParams, mWebSourceParams2));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebTrigger_nonLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_nonLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_allLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_someLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams, mWebTriggerParams2));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_allLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebTrigger_someLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams, mWebTriggerParams2));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerSources_noneLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_noneLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_allLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris()).thenReturn(List.of(LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_someLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI, LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_allLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris()).thenReturn(List.of(LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerSources_someLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI, LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextDisabledSupplier(), mSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void getErrorMessageRegister() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextEnabledSupplier(), mRegistrationRequest);

        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }

    @Test
    public void getErrorMessageRegisterWebSource() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebSourceRegistrationRequest);

        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }

    @Test
    public void getErrorMessageRegisterWebTrigger() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextEnabledSupplier(), mWebTriggerRegistrationRequest);

        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }

    @Test
    public void isAllowed_register_localhost_throwsSecurityException() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(LOCALHOST);
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextThrowsSecurityExceptionSupplier(), mRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebSource_allLocalhost_throwsSecurityException() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextThrowsSecurityExceptionSupplier(),
                        mWebSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebSource_someLocalhost_throwsSecurityException() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams())
                .thenReturn(List.of(mWebSourceParams, mWebSourceParams2));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextThrowsSecurityExceptionSupplier(),
                        mWebSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebTrigger_allLocalhost_throwsSecurityException() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextThrowsSecurityExceptionSupplier(),
                        mWebTriggerRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_registerWebTrigger_someLocalhost_throwsSecurityException() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams, mWebTriggerParams2));
        mClassUnderTest =
                new DevContextAccessResolver(
                        getDevContextThrowsSecurityExceptionSupplier(),
                        mWebTriggerRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_devContextOnly_devOptionsEnabled_returnsTrue() {
        // Setup
        mClassUnderTest = new DevContextAccessResolver(getDevContextEnabledSupplier());

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_devContextOnly_devOptionsDisabled_returnsTrue() {
        // Setup
        mClassUnderTest = new DevContextAccessResolver(getDevContextDisabledSupplier());

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_devContextOnly_devContextThrowsSecurityException_returnsFalse() {
        // Setup
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextThrowsSecurityExceptionSupplier());

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    @Test
    public void isAllowed_devContextOnly_throwsSecurityException() {
        // Setup
        mClassUnderTest = new DevContextAccessResolver(getDevContextThrowsSecurityExceptionSupplier());

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(STATUS_UNAUTHORIZED, accessInfo.getResponseCode());
    }

    private Supplier<DevContext> getDevContextEnabledSupplier() {
        return () -> DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build();
    }

    private Supplier<DevContext> getDevContextThrowsSecurityExceptionSupplier() {
        return () -> {
            throw new SecurityException();
        };
    }

    private static Supplier<DevContext> getDevContextDisabledSupplier() {
        return () -> DevContext.createForDevOptionsDisabled();
    }
}
