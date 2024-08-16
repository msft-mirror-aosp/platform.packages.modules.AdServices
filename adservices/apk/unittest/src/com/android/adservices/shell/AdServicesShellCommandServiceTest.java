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

package com.android.adservices.shell;

import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.IBinder;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;

@SpyStatic(FlagsFactory.class)
public final class AdServicesShellCommandServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final String ACTION_SHELL_COMMAND_SERVICE =
            "android.adservices.SHELL_COMMAND_SERVICE";

    @Before
    public void setup() {
        when(mMockFlags.getFledgeAuctionServerCompressionAlgorithmVersion())
                .thenReturn(Flags.FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION);
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testOnBindShellCommandService_flagEnabled() {
        AdServicesShellCommandService shellService =
                new AdServicesShellCommandService(/* shellCommandEnabled= */ true);
        shellService.onCreate();
        IBinder binder = shellService.onBind(getIntentForShellCommandService());

        expect.withMessage("onBind()").that(binder).isNotNull();
    }

    @Test
    public void testOnBindShellCommandService_flagDisabled() {
        AdServicesShellCommandService shellService =
                new AdServicesShellCommandService(/* shellCommandEnabled= */ false);
        shellService.onCreate();
        IBinder binder = shellService.onBind(getIntentForShellCommandService());

        expect.withMessage("onBind()").that(binder).isNull();
    }

    private Intent getIntentForShellCommandService() {
        return new Intent(ACTION_SHELL_COMMAND_SERVICE);
    }
}
