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

package com.android.adservices.service.ui.data;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.common.AtomicFileDatastore;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

/** This test reads and writes to test files under the test app dir instead of using full mocks. */
public final class UxStatesDaoTest extends AdServicesMockitoTestCase {
    private UxStatesDao mUxStatesDao;

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    @Before
    public void setup() throws IOException {
        AtomicFileDatastore atomicFileDatastore =
                new AtomicFileDatastore(
                        mContext,
                        UxStatesDao.TEST_DATASTORE_NAME,
                        UxStatesDao.DATASTORE_VERSION,
                        mMockAdServicesErrorLogger);
        mUxStatesDao = new UxStatesDao(atomicFileDatastore);
    }

    @Test
    public void uxTest_datastoreConformance() {
        for (PrivacySandboxUxCollection uxCollection : PrivacySandboxUxCollection.values()) {
            mUxStatesDao.setUx(uxCollection);
            assertThat(mUxStatesDao.getUx()).isEqualTo(uxCollection);
        }
    }

    @Test
    public void enrollmentChannelTest_datastoreConformance() {
        for (PrivacySandboxUxCollection uxCollection : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection enrollmentChannelCollection :
                    uxCollection.getEnrollmentChannelCollection()) {
                mUxStatesDao.setEnrollmentChannel(uxCollection, enrollmentChannelCollection);
                assertThat(mUxStatesDao.getEnrollmentChannel(uxCollection))
                        .isEqualTo(enrollmentChannelCollection);
            }
        }
    }
}
