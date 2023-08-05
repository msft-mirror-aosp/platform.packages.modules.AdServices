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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DebugReportingTest {

    @Mock
    Flags mFlagsMock;

    @Mock
    AdServicesHttpsClient mHttpClientMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mFlagsMock.getAdIdKillSwitch()).thenReturn(true);
    }

    @Test
    public void isEnabled_withFlagEnabled_returnsTrue() {
        when(mFlagsMock.getAdIdKillSwitch()).thenReturn(true);
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);

        DebugReporting debugReporting = new DebugReporting(mFlagsMock, null);

        assertThat(debugReporting.isEnabled()).isTrue();
    }

    @Test
    public void isEnabled_withFlagDisabled_returnsFalse() {
        when(mFlagsMock.getAdIdKillSwitch()).thenReturn(true);
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = new DebugReporting(mFlagsMock, null);

        assertThat(debugReporting.isEnabled()).isFalse();
    }

    @Test
    public void getScriptStrategy_isEnabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);

        DebugReporting debugReporting = new DebugReporting(mFlagsMock, mHttpClientMock);

        assertThat(debugReporting.getScriptStrategy()).isInstanceOf(
                DebugReportingEnabledScriptStrategy.class);
    }

    @Test
    public void getScriptStrategy_isDisabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = new DebugReporting(mFlagsMock, mHttpClientMock);

        assertThat(debugReporting.getScriptStrategy()).isInstanceOf(
                DebugReportingScriptDisabledStrategy.class);
    }

    @Test
    public void getSenderStrategy_isEnabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);

        DebugReporting debugReporting = new DebugReporting(mFlagsMock, mHttpClientMock);

        assertThat(debugReporting.getSenderStrategy()).isInstanceOf(
                DebugReportSenderStrategyHttpImpl.class);
    }

    @Test
    public void getSenderStrategy_isDisabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = new DebugReporting(mFlagsMock, mHttpClientMock);

        assertThat(debugReporting.getSenderStrategy()).isInstanceOf(
                DebugReportSenderStrategyNoOp.class);
    }
}
