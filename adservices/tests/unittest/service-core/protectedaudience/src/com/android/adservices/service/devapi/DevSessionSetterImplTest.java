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

package com.android.adservices.service.devapi;

import static com.android.adservices.service.devapi.DevSessionSetterImpl.DAYS_UNTIL_EXPIRY;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import static java.time.temporal.ChronoUnit.HOURS;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.DatabaseClearer;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DevSessionSetterImplTest extends AdServicesMockitoTestCase {

    private static final int TIMEOUT_SEC = 5;
    private final Clock mClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    @Mock private DatabaseClearer mDatabaseClearerMock;
    @Mock private DevSessionDataStore mDevSessionDataStoreMock;
    private DevSessionSetterImpl mDevSessionSetter;

    @Before
    public void setUp() {
        mDevSessionSetter =
                new DevSessionSetterImpl(
                        mDatabaseClearerMock,
                        mDevSessionDataStoreMock,
                        AdServicesExecutors.getLightWeightExecutor(),
                        mClock);
    }

    @Test
    public void setToEnabled_withDevSessionDisabled_returnsTrue() throws Exception {
        doReturn(Futures.immediateFuture(false))
                .when(mDevSessionDataStoreMock)
                .isDevSessionActive();
        doReturn(Futures.immediateVoidFuture())
                .when(mDevSessionDataStoreMock)
                .startDevSession(mClock.instant().plus(DAYS_UNTIL_EXPIRY, HOURS));
        doReturn(Futures.immediateVoidFuture())
                .when(mDatabaseClearerMock)
                .deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true);

        Future<Boolean> resultFuture = mDevSessionSetter.set(true);

        expect.withMessage("dev session should disable").that(wait(resultFuture)).isTrue();
        verify(mDatabaseClearerMock)
                .deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true);
        verify(mDevSessionDataStoreMock)
                .startDevSession(mClock.instant().plus(DAYS_UNTIL_EXPIRY, HOURS));
    }

    @Test
    public void setToEnabled_withDevSessionActive_throwsException() throws Exception {
        doReturn(Futures.immediateFuture(true)).when(mDevSessionDataStoreMock).isDevSessionActive();

        Future<Boolean> resultFuture = mDevSessionSetter.set(true);

        try {
            wait(resultFuture);
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        verifyZeroInteractions(mDatabaseClearerMock);
    }

    @Test
    public void setToDisabled_withDevSessionActive_returnsTrue() throws Exception {
        doReturn(Futures.immediateFuture(true)).when(mDevSessionDataStoreMock).isDevSessionActive();
        doReturn(Futures.immediateVoidFuture()).when(mDevSessionDataStoreMock).endDevSession();
        doReturn(Futures.immediateVoidFuture())
                .when(mDatabaseClearerMock)
                .deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true);

        Future<Boolean> resultFuture = mDevSessionSetter.set(false);

        expect.withMessage("dev session should not disable").that(wait(resultFuture)).isTrue();
        verify(mDatabaseClearerMock)
                .deleteProtectedAudienceAndAppSignalsData(
                        /* deleteCustomAudienceUpdate= */ true,
                        /* deleteAppInstallFiltering= */ true,
                        /* deleteProtectedSignals= */ true);
        verify(mDevSessionDataStoreMock).endDevSession();
    }

    @Test
    public void setToDisabled_withDevSessionDisabled_returnsFalse() throws Exception {
        doReturn(Futures.immediateFuture(false))
                .when(mDevSessionDataStoreMock)
                .isDevSessionActive();
        doReturn(Futures.immediateVoidFuture()).when(mDevSessionDataStoreMock).endDevSession();

        Future<Boolean> resultFuture = mDevSessionSetter.set(false);

        try {
            wait(resultFuture);
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        verifyZeroInteractions(mDatabaseClearerMock);
    }

    private static <T> T wait(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}
