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

package com.android.adservices.service.appsearch;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.Executor;

@SmallTest
public class AppSearchModuleEnrollmentStateDaoTest {
    private static final String ID1 = "1";
    private static final String ID2 = "2";
    private static final String NAMESPACE = "moduleEnrollmentState";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private String mAdServicesPackageName;
    private final ListenableFuture mSearchSessionFuture = Futures.immediateFuture(null);
    private MockitoSession mStaticMockSession;

    @Mock private Executor mMockExecutor;

    @Rule
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Before
    public void setup() {
        // TODO(b/347043278): must be set inside @Before so it's not called when device is not
        // supported
        mAdServicesPackageName = AppSearchConsentWorker.getAdServicesPackageName(mContext);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AppSearchDao.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testToString() {
        AppSearchModuleEnrollmentStateDao dao =
                new AppSearchModuleEnrollmentStateDao(ID1, ID2, NAMESPACE, "");
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID1
                                + "; userId="
                                + ID2
                                + "; namespace="
                                + NAMESPACE
                                + "; moduleEnrollmentState=");
    }

    @Test
    public void testEquals() {
        AppSearchModuleEnrollmentStateDao dao1 =
                new AppSearchModuleEnrollmentStateDao(ID1, ID2, NAMESPACE, "");
        AppSearchModuleEnrollmentStateDao dao2 =
                new AppSearchModuleEnrollmentStateDao(ID1, ID2, NAMESPACE, "");
        AppSearchModuleEnrollmentStateDao dao3 =
                new AppSearchModuleEnrollmentStateDao(ID1, "foo", NAMESPACE, "");
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID1;
        assertThat(AppSearchModuleEnrollmentStateDao.getQuery(ID1)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        assertThat(AppSearchModuleEnrollmentStateDao.getRowId(ID1)).isEqualTo(ID1);
    }

    @Test
    public void moduleEnrollmentStateTest_nullDao() {
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        String result =
                AppSearchModuleEnrollmentStateDao.readModuleEnrollmentState(
                        mSearchSessionFuture, mMockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isEqualTo("");
    }

    @Test
    public void readModuleEnrollmentStateTest_testString() {
        String query = "userId:" + ID1;
        String jsonString = "{test: 1, 1: \"test\"}";
        AppSearchModuleEnrollmentStateDao dao =
                Mockito.mock(AppSearchModuleEnrollmentStateDao.class);
        Mockito.when(dao.getModuleEnrollmentState()).thenReturn(jsonString);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));

        String result =
                AppSearchModuleEnrollmentStateDao.readModuleEnrollmentState(
                        mSearchSessionFuture, mMockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isEqualTo(jsonString);
    }
}
