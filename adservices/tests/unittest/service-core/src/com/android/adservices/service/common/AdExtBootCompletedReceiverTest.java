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

package com.android.adservices.service.common;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_BOOT_COMPLETED_RECEIVER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

@SpyStatic(AdServicesBackCompatInit.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        errorCode =
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__BACK_COMPAT_INIT_BOOT_COMPLETED_RECEIVER_FAILURE,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
public class AdExtBootCompletedReceiverTest extends AdServicesExtendedMockitoTestCase {
    @Mock private AdServicesBackCompatInit mMockBackCompatInit;
    @Spy private AdExtBootCompletedReceiver mSpyReceiver;

    @Test
    public void testOnReceive_withNoException_executesBackCompatInit() {
        doReturn(mMockBackCompatInit).when(AdServicesBackCompatInit::getInstance);

        mSpyReceiver.onReceive(mContext, new Intent());

        verify(mMockBackCompatInit).initializeComponents();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall
    public void testOnReceive_withExceptionThrown_handlesGracefully() {
        doReturn(mMockBackCompatInit).when(AdServicesBackCompatInit::getInstance);
        doThrow(IllegalArgumentException.class).when(mMockBackCompatInit).initializeComponents();

        // No exception expected, so no need to explicitly handle any exceptions here.
        mSpyReceiver.onReceive(mContext, new Intent());
    }

    @Test
    public void testClassNameMatchesExpectedValue() {
        // IMPORTANT: AdExtBootCompletedReceiver class name is hardcoded in places
        // like AdExtServicesManifest. If the name changes, ensure changes are made in
        // unison across all appropriate places.
        assertWithMessage("AdExtBootCompletedReceiver class name")
                .that(AdExtBootCompletedReceiver.class.getName())
                .isEqualTo("com.android.adservices.service.common.AdExtBootCompletedReceiver");
    }
}
