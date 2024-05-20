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
package com.android.adservices.mockito;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;

/**
 * {@link AdServicesStaticMockitoMocker} implementation that uses {@code ExtendedMockito}.
 *
 * <p><b>NOTE: </b> most expectations require {@code spyStatic()} or {@code mockStatic()} in the
 * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession session} ahead of time - this
 * helper doesn't check that such calls were made, it's up to the caller to do so.
 */
public final class AdServicesExtendedMockitoMocker extends AbstractStaticMocker
        implements AdServicesStaticMockitoMocker {

    public AdServicesExtendedMockitoMocker(StaticClassChecker staticClassChecker) {
        super(staticClassChecker);
    }

    @Override
    public void mockGetFlags(Flags mockedFlags) {
        logV("mockGetFlags(%s)", mockedFlags);
        assertSpiedOrMocked(FlagsFactory.class);
        doReturn(mockedFlags).when(FlagsFactory::getFlags);
    }

    @Override
    public void mockGetFlagsForTesting() {
        mockGetFlags(FakeFlagsFactory.getFlagsForTest());
    }

    @Override
    public void mockSpeJobScheduler(AdServicesJobScheduler mockedAdServicesJobScheduler) {
        logV("mockSpeJobScheduler(%s)", mockedAdServicesJobScheduler);
        assertSpiedOrMocked(AdServicesJobScheduler.class);
        doReturn(mockedAdServicesJobScheduler).when(AdServicesJobScheduler::getInstance);
    }

    @Override
    public void mockAdServicesJobServiceFactory(
            AdServicesJobServiceFactory mockedAdServicesJobServiceFactory) {
        logV("mockAdServicesJobServiceFactory(%s)", mockedAdServicesJobServiceFactory);
        assertSpiedOrMocked(AdServicesJobServiceFactory.class);
        doReturn(mockedAdServicesJobServiceFactory).when(AdServicesJobServiceFactory::getInstance);
    }

    @Override
    public void mockAdServicesLoggerImpl(AdServicesLoggerImpl mockedAdServicesLoggerImpl) {
        logV("mockAdServicesLoggerImpl(%s)", mockedAdServicesLoggerImpl);
        assertSpiedOrMocked(AdServicesLoggerImpl.class);
        doReturn(mockedAdServicesLoggerImpl).when(AdServicesLoggerImpl::getInstance);
    }
}
