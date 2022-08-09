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

package com.android.adservices.topics;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.topics.EpochJobService;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/** Unit test for {@link com.android.adservices.topics.TopicsService}. */
public class TopicsServiceTest {
    private static final String TAG = "TopicsServiceTest";

    @Mock Flags mMockFlags;
    @Mock TopicsWorker mMockTopicsWorker;
    @Mock ConsentManager mMockConsentManager;
    @Mock AdServicesLoggerImpl mMockAdServicesLoggerImpl;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @Ignore("b/241788223")
    public void testBindableTopicsService_killswitchOff() {
        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(AdServicesLoggerImpl.class)
                        .spyStatic(MaintenanceJobService.class)
                        .spyStatic(EpochJobService.class)
                        .spyStatic(MddJobService.class)
                        .startMocking();

        try {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(() -> FlagsFactory.getFlags());

            ExtendedMockito.doReturn(mMockTopicsWorker)
                    .when(() -> TopicsWorker.getInstance(any(Context.class)));
            ExtendedMockito.doReturn(mMockConsentManager)
                    .when(() -> ConsentManager.getInstance(any(Context.class)));
            ExtendedMockito.doReturn(mMockAdServicesLoggerImpl)
                    .when(() -> AdServicesLoggerImpl.getInstance());
            ExtendedMockito.doNothing()
                    .when(() -> MaintenanceJobService.schedule(any(Context.class)));
            ExtendedMockito.doNothing().when(() -> EpochJobService.schedule(any(Context.class)));
            ExtendedMockito.doNothing().when(() -> MddJobService.schedule(any(Context.class)));

            TopicsService topicsService = new TopicsService();
            topicsService.onCreate();
            IBinder binder = topicsService.onBind(getIntentForTopicsService());
            assertNotNull(binder);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testBindableTopicsService_killswitchOn() {
        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(FlagsFactory.class).startMocking();

        try {
            // Killswitch is on.
            doReturn(true).when(mMockFlags).getTopicsKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(() -> FlagsFactory.getFlags());

            TopicsService topicsService = new TopicsService();
            topicsService.onCreate();
            IBinder binder = topicsService.onBind(getIntentForTopicsService());
            assertNull(binder);
        } finally {
            session.finishMocking();
        }
    }

    private Intent getIntentForTopicsService() {
        return new Intent(ApplicationProvider.getApplicationContext(), TopicsService.class);
    }
}
