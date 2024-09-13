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

package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_PROVIDER_SERVICE_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.adid.AdIdCacheManager.SHARED_PREFS_IAPC;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_AD_ID_PROVIDER_NOT_AVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adid.AdId;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IAdIdProviderService;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.adid.IGetAdIdProviderCallback;
import android.adservices.common.UpdateAdIdRequest;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.testing.IntFailureSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit test for {@link AdIdCacheManager}. */
@SpyStatic(FlagsFactory.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID)
public final class AdIdCacheManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final String PACKAGE_NAME = "package_name";
    // Use a non zeroed out AdId differentiate from the scenario without the provider service.
    private static final String AD_ID = "10000000-0000-0000-0000-000000000000";
    private static final String AD_ID_UPDATE = "20000000-0000-0000-0000-000000000000";
    private static final int DUMMY_CALLER_UID = 0;

    private static final String SUCCESS_RESPONSE = "success";
    private static final String UNAUTHORIZED_RESPONSE = "unauthorized";
    private static final String FAILURE_RESPONSE = "failure";

    private IAdIdProviderService mAdIdProviderService;
    private AdIdCacheManager mAdIdCacheManager;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);

        deleteIapcSharedPreference();

        mAdIdCacheManager = spy(new AdIdCacheManager(mContext));
        when(mMockFlags.getAdIdCacheTtlMs()).thenReturn(0L);
    }

    // Clear the shared preference to isolate the test result from other tests.
    @After
    public void deleteIapcSharedPreference() {
        mContext.deleteSharedPreferences(SHARED_PREFS_IAPC);
    }

    @Test
    public void testGetAdId() throws Exception {
        mAdIdProviderService = createAdIdProviderService(SUCCESS_RESPONSE);
        when(mAdIdCacheManager.getService()).thenReturn(mAdIdProviderService);

        // First getAdId() call should get AdId from the provider.
        SyncIGetAdIdCallback callback1 = new SyncIGetAdIdCallback();

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback1);

        GetAdIdResult result = callback1.assertResultReceived();
        assertWithMessage("result from 1st call").that(result).isNotNull();
        AdId actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        AdId expectedAdId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        assertWithMessage("The first result is from the Provider.")
                .that(actualAdId)
                .isEqualTo(expectedAdId);

        // Verify the first call should call the provider to fetch the AdId
        verify(mAdIdCacheManager).getAdIdFromProvider(PACKAGE_NAME, DUMMY_CALLER_UID, callback1);

        // Second getAdId() call should get AdId from the cache.
        SyncIGetAdIdCallback callback2 = new SyncIGetAdIdCallback();
        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback2);
        result = callback2.assertResultReceived();
        assertWithMessage("result from 2nd call").that(result).isNotNull();
        actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        assertWithMessage("The second result is from the Cache")
                .that(actualAdId)
                .isEqualTo(expectedAdId);

        // Verify the second call should NOT call the provider to fetch the AdId, the only
        // invocation comes from the first getAdId() call.
        verify(mAdIdCacheManager).getAdIdFromProvider(any(), anyInt(), any());

        // Make the third getAdId() call after updating the shared preference.
        mAdIdCacheManager.setAdIdInStorage(
                new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true));
        SyncIGetAdIdCallback callback3 = new SyncIGetAdIdCallback();
        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback3);

        result = callback3.assertResultReceived();
        assertWithMessage("result from 3rd call").that(result).isNotNull();
        actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        expectedAdId = new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true);

        expect.withMessage("The third result is from the Cache (Updated)")
                .that(actualAdId)
                .isEqualTo(expectedAdId);
        verify(mAdIdCacheManager).getAdIdFromProvider(any(), anyInt(), any());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_AD_ID_PROVIDER_NOT_AVAILABLE)
    public void testGetAdIdOnError() throws Exception {
        mAdIdProviderService = createAdIdProviderService(FAILURE_RESPONSE);
        when(mAdIdCacheManager.getService()).thenReturn(mAdIdProviderService);
        SyncIGetAdIdCallback callback = new SyncIGetAdIdCallback();

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback);

        int result = callback.assertFailureReceived();
        expect.that(result).isEqualTo(STATUS_PROVIDER_SERVICE_INTERNAL_ERROR);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__IAPC_AD_ID_PROVIDER_NOT_AVAILABLE)
    public void testGetAdIdOnUnauthorizedError() throws Exception {
        mAdIdProviderService = createAdIdProviderService(UNAUTHORIZED_RESPONSE);
        when(mAdIdCacheManager.getService()).thenReturn(mAdIdProviderService);

        SyncIGetAdIdCallback callback = new SyncIGetAdIdCallback();

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback);

        GetAdIdResult result = callback.assertResultReceived();
        assertWithMessage("result").that(result).isNotNull();
        AdId actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        AdId expectedAdId = new AdId(AdId.ZERO_OUT, /* limitAdTrackingEnabled= */ true);
        expect.withMessage("Get AdId Unauthorized failed").that(actualAdId).isEqualTo(expectedAdId);
    }

    @Test
    public void testUpdateAdId_success() {
        AdId adId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        AdId adIdUpdate = new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true);

        mAdIdCacheManager.setAdIdInStorage(adId);

        mAdIdCacheManager.updateAdId(
                new UpdateAdIdRequest.Builder(adIdUpdate.getAdId())
                        .setLimitAdTrackingEnabled(adIdUpdate.isLimitAdTrackingEnabled())
                        .build());
        expect.withMessage("getAdIdInStorage()")
                .that(mAdIdCacheManager.getAdIdInStorage())
                .isEqualTo(adIdUpdate);
        verify(mAdIdCacheManager).setAdIdInStorage(adIdUpdate);
    }

    @Test
    public void testGetAdId_cacheTtlEnabled() throws Exception {
        mAdIdProviderService = createAdIdProviderService(SUCCESS_RESPONSE);
        when(mAdIdCacheManager.getService()).thenReturn(mAdIdProviderService);

        // First getAdId() call should get AdId from the provider.
        SyncIGetAdIdCallback callback1 = new SyncIGetAdIdCallback();

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback1);

        GetAdIdResult result = callback1.assertResultReceived();
        AdId actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        AdId expectedAdId = new AdId(AD_ID, /* limitAdTrackingEnabled= */ false);
        assertWithMessage("The first result is from the Provider.")
                .that(actualAdId)
                .isEqualTo(expectedAdId);

        // Verify the first call should call the provider to fetch the AdId
        verify(mAdIdCacheManager).getAdIdFromProvider(PACKAGE_NAME, DUMMY_CALLER_UID, callback1);

        // Second getAdId() call have ttl expired, thus get it from the provider.
        when(mMockFlags.getAdIdCacheTtlMs()).thenReturn(100L);
        when(mAdIdCacheManager.isCacheTimeout(anyLong(), anyLong(), anyLong())).thenReturn(true);
        // Set the cache adid to update adid
        mAdIdCacheManager.setAdIdInStorage(
                new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true));
        SyncIGetAdIdCallback callback2 = new SyncIGetAdIdCallback();
        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback2);
        result = callback2.assertResultReceived();
        actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        // Expected adid should still be the provider ad id
        assertWithMessage("The second result is from the Cache")
                .that(actualAdId)
                .isEqualTo(expectedAdId);

        // Verify the second call should call the provider again to fetch the AdId
        verify(mAdIdCacheManager).getAdIdFromProvider(PACKAGE_NAME, DUMMY_CALLER_UID, callback2);

        // Make the third getAdId() call from the cache.
        when(mAdIdCacheManager.isCacheTimeout(anyLong(), anyLong(), anyLong())).thenReturn(false);
        // Set the cache adid to the updated adid
        mAdIdCacheManager.setAdIdInStorage(
                new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true));
        SyncIGetAdIdCallback callback3 = new SyncIGetAdIdCallback();

        mAdIdCacheManager.getAdId(PACKAGE_NAME, DUMMY_CALLER_UID, callback3);

        result = callback3.assertResultReceived();
        actualAdId = new AdId(result.getAdId(), result.isLatEnabled());
        expectedAdId = new AdId(AD_ID_UPDATE, /* limitAdTrackingEnabled= */ true);

        expect.withMessage("The third result is from the Cache (Updated)")
                .that(actualAdId)
                .isEqualTo(expectedAdId);
        verify(mAdIdCacheManager, times(2)).getAdIdFromProvider(any(), anyInt(), any());
    }

    @Test
    public void testIsCacheTimeout() throws Exception {
        expect.that(mAdIdCacheManager.isCacheTimeout(0, 100, 100)).isTrue();
        expect.that(mAdIdCacheManager.isCacheTimeout(10, 100, 100)).isFalse();
        expect.that(mAdIdCacheManager.isCacheTimeout(10, 100, 80)).isTrue();
    }

    private static final class SyncIGetAdIdCallback extends IntFailureSyncCallback<GetAdIdResult>
            implements IGetAdIdCallback {

        @Override
        public void onError(int resultCode) {
            onFailure(resultCode);
        }
    }

    private IAdIdProviderService createAdIdProviderService(String response) {
        return new IAdIdProviderService.Stub() {
            @Override
            public void getAdIdProvider(
                    int appUid, String packageName, IGetAdIdProviderCallback resultCallback)
                    throws RemoteException {

                if (response.equals(SUCCESS_RESPONSE)) {
                    GetAdIdResult adIdInternal =
                            new GetAdIdResult.Builder()
                                    .setStatusCode(STATUS_SUCCESS)
                                    .setErrorMessage("")
                                    .setAdId(AD_ID)
                                    .setLatEnabled(/* isLimitAdTrackingEnabled= */ false)
                                    .build();

                    // Mock the write operation to the storage
                    mAdIdCacheManager.setAdIdInStorage(
                            new AdId(AD_ID, /* limitAdTrackingEnabled= */ false));
                    resultCallback.onResult(adIdInternal);
                } else if (response.equals(UNAUTHORIZED_RESPONSE)) {
                    resultCallback.onError("Unauthorized caller: com.google.test");
                } else {
                    resultCallback.onError("testOnError");
                }
            }
        };
    }
}
