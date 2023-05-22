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

package com.android.adservices.service.appsearch;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@SmallTest
public class AppSearchUxStatesDaoTest {
    private static final String ID1 = "1";
    private static final String ID2 = "2";
    private static final String NAMESPACE = "uxstates";
    private MockitoSession mStaticMockSession;

    @Before
    public void setup() {
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
        AppSearchUxStatesDao dao =
                new AppSearchUxStatesDao(ID1, ID2, NAMESPACE, false, false, false);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID1
                                + "; userId="
                                + ID2
                                + "; namespace="
                                + NAMESPACE
                                + "; isU18Account=false"
                                + "; isAdultAccount=false"
                                + "; isAdIdEnabled=false");
    }

    @Test
    public void testEquals() {
        AppSearchUxStatesDao dao1 =
                new AppSearchUxStatesDao(ID1, ID2, NAMESPACE, true, false, false);
        AppSearchUxStatesDao dao2 =
                new AppSearchUxStatesDao(ID1, ID2, NAMESPACE, true, false, false);
        AppSearchUxStatesDao dao3 =
                new AppSearchUxStatesDao(ID1, "foo", NAMESPACE, true, false, false);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID1;
        assertThat(AppSearchNotificationDao.getQuery(ID1)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        assertThat(AppSearchNotificationDao.getRowId(ID1)).isEqualTo(ID1);
    }
}
