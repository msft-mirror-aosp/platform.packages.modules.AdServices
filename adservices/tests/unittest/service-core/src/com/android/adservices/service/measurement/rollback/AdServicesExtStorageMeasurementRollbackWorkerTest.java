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

package com.android.adservices.service.measurement.rollback;

import static android.adservices.extdata.AdServicesExtDataStorageService.FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION;
import static android.app.adservices.AdServicesManager.MEASUREMENT_DELETION;

import static com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager.APEX_VERSION_WHEN_NOT_FOUND;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.extdata.AdServicesExtDataParams;
import android.util.Pair;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class AdServicesExtStorageMeasurementRollbackWorkerTest {
    private static final long CURRENT_APEX_VERSION = 1000;

    private AdServicesExtStorageMeasurementRollbackWorker mWorker;

    @Mock private AdServicesExtDataStorageServiceManager mStorageManager;

    @Rule
    public final AdServicesExtendedMockitoRule mockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(AdServicesExtDataStorageServiceManager.class)
                    .build();

    @Before
    public void setup() {
        mWorker = new AdServicesExtStorageMeasurementRollbackWorker(mStorageManager);
    }

    @Test
    public void testRecordAdServicesDeletionOccurred() {
        mWorker.recordAdServicesDeletionOccurred(MEASUREMENT_DELETION, CURRENT_APEX_VERSION);

        ArgumentCaptor<AdServicesExtDataParams> paramsCaptor =
                ArgumentCaptor.forClass(AdServicesExtDataParams.class);
        ArgumentCaptor<int[]> fieldsCaptor = ArgumentCaptor.forClass(int[].class);
        ExtendedMockito.verify(mStorageManager)
                .setAdServicesExtData(paramsCaptor.capture(), fieldsCaptor.capture());

        assertThat(paramsCaptor.getValue().getMeasurementRollbackApexVersion())
                .isEqualTo(CURRENT_APEX_VERSION);
        assertThat(fieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION);
    }

    @Test
    public void testClearAdServicesDeletionOccurred() {
        mWorker.clearAdServicesDeletionOccurred(null);

        ArgumentCaptor<AdServicesExtDataParams> paramsCaptor =
                ArgumentCaptor.forClass(AdServicesExtDataParams.class);
        ArgumentCaptor<int[]> fieldsCaptor = ArgumentCaptor.forClass(int[].class);
        ExtendedMockito.verify(mStorageManager)
                .setAdServicesExtData(paramsCaptor.capture(), fieldsCaptor.capture());

        assertThat(paramsCaptor.getValue().getMeasurementRollbackApexVersion())
                .isEqualTo(APEX_VERSION_WHEN_NOT_FOUND);
        assertThat(fieldsCaptor.getValue())
                .asList()
                .containsExactly(FIELD_MEASUREMENT_ROLLBACK_APEX_VERSION);
    }

    @Test
    public void testGetAdServicesDeletionRollbackMetadata() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setMsmtRollbackApexVersion(CURRENT_APEX_VERSION - 1)
                        .build();
        ExtendedMockito.doReturn(params).when(mStorageManager).getAdServicesExtData();

        Pair<Long, Void> data = mWorker.getAdServicesDeletionRollbackMetadata(MEASUREMENT_DELETION);

        assertThat(data.first).isEqualTo(CURRENT_APEX_VERSION - 1);
    }

    @Test
    public void testGetAdServicesDeletionRollbackMetadataIfNoDataPresent() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setMsmtRollbackApexVersion(APEX_VERSION_WHEN_NOT_FOUND)
                        .build();
        ExtendedMockito.doReturn(params).when(mStorageManager).getAdServicesExtData();

        Pair<Long, Void> data = mWorker.getAdServicesDeletionRollbackMetadata(MEASUREMENT_DELETION);

        assertThat(data).isNull();
    }

    @Test
    public void testGetAdServicesDeletionRollbackMetadataIfReturnedParamsNull() {
        ExtendedMockito.doReturn(null).when(mStorageManager).getAdServicesExtData();

        Pair<Long, Void> data = mWorker.getAdServicesDeletionRollbackMetadata(MEASUREMENT_DELETION);

        assertThat(data).isNull();
    }
}
