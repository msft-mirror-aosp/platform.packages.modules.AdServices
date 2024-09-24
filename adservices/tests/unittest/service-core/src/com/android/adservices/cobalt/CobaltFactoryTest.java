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

package com.android.adservices.cobalt;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.cobalt.domain.ReportIdentifier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public final class CobaltFactoryTest extends AdServicesMockitoTestCase {
    private static final String ADSERVICES_APEX_PACKAGE_NAME = "com.google.android.adservices";
    private static final String EXTSERVICES_APEX_PACKAGE_NAME = "com.google.android.extservices";
    private static final long ADSERVICES_VERSION = 349990000L;
    private static final long EXTSERVICES_VERSION = 330400000;
    private static final String VERSION_NOT_FOUND = "-1";
    private final List<PackageInfo> mInstalledPackages = new ArrayList<>();
    @Mock private PackageManager mMockPackageManager;

    @Before
    public void setup() {
        mockPackageManager();
    }

    @Test
    public void testComputeApexVersion_apexNotFound() {
        assertThat(CobaltFactory.computeApexVersion(mMockContext)).isEqualTo(VERSION_NOT_FOUND);
    }

    @Test
    public void testComputeApexVersion_bothApexInstalled() {
        addPackageInfo(ADSERVICES_APEX_PACKAGE_NAME, ADSERVICES_VERSION);
        addPackageInfo(EXTSERVICES_APEX_PACKAGE_NAME, EXTSERVICES_VERSION);

        assertThat(CobaltFactory.computeApexVersion(mMockContext))
                .isEqualTo(String.valueOf(ADSERVICES_VERSION));
    }

    @Test
    public void testComputeApexVersion_extservicesApexInstalled() {
        addPackageInfo(EXTSERVICES_APEX_PACKAGE_NAME, EXTSERVICES_VERSION);

        assertThat(CobaltFactory.computeApexVersion(mMockContext))
                .isEqualTo(String.valueOf(EXTSERVICES_VERSION));
    }

    @Test
    public void testComputeApexVersion_adservicesApexInstalled() {
        addPackageInfo(ADSERVICES_APEX_PACKAGE_NAME, ADSERVICES_VERSION);

        assertThat(CobaltFactory.computeApexVersion(mMockContext))
                .isEqualTo(String.valueOf(ADSERVICES_VERSION));
    }

    @Test
    public void testGetReportsToIgnore_singleReportIdParsed() {
        when(mMockFlags.getCobaltIgnoredReportIdList()).thenReturn("1:2:3:4");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags))
                .containsExactly(ReportIdentifier.create(1, 2, 3, 4));
    }

    @Test
    public void testGetReportsToIgnore_commaSepareatedReportIds_parsedIntoList() {
        when(mMockFlags.getCobaltIgnoredReportIdList()).thenReturn("1:2:3:4,5:6:7:8,11:12:13:14");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags))
                .containsExactly(
                        ReportIdentifier.create(1, 2, 3, 4),
                        ReportIdentifier.create(5, 6, 7, 8),
                        ReportIdentifier.create(11, 12, 13, 14));
    }

    @Test
    public void testGetReportsToIgnore_commaSepareatedReportIds_whitespaceIgnored() {
        when(mMockFlags.getCobaltIgnoredReportIdList())
                .thenReturn("   1 :2:3:4,5:  6 :7:8, 11: 12:1 3:14");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags))
                .containsExactly(
                        ReportIdentifier.create(1, 2, 3, 4),
                        ReportIdentifier.create(5, 6, 7, 8),
                        ReportIdentifier.create(11, 12, 13, 14));
    }

    @Test
    public void testGetReportsToIgnore_zeroIdsSupported() {
        when(mMockFlags.getCobaltIgnoredReportIdList()).thenReturn("0:0:0:0");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags))
                .containsExactly(ReportIdentifier.create(0, 0, 0, 0));
    }

    @Test
    public void testGetReportsToIgnore_nullFlag_emptyList() {
        when(mMockFlags.getCobaltIgnoredReportIdList()).thenReturn(null);
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags)).isEmpty();
    }

    @Test
    public void testGetReportsToIgnore_non4PartIds_ignored() {
        when(mMockFlags.getCobaltIgnoredReportIdList())
                .thenReturn("11,21:22,31:32:33,41:42:43:44,51:52:53:54:55");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags))
                .containsExactly(ReportIdentifier.create(41, 42, 43, 44));
    }

    @Test
    public void testGetReportsToIgnore_nonParseableIds_ignored() {
        when(mMockFlags.getCobaltIgnoredReportIdList())
                .thenReturn("a:2:3:4,11:b:13:14,21:22:c:24,31:32:33:d");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags)).isEmpty();
    }

    @Test
    public void testGetReportsToIgnore_negativeIds_ignored() {
        when(mMockFlags.getCobaltIgnoredReportIdList())
                .thenReturn("-1:2:3:4,11:-12:13:14,21:22:-23:24,31:32:33:-34");
        assertThat(CobaltFactory.parseReportsToIgnore(mMockFlags)).isEmpty();
    }

    private void addPackageInfo(String packageName, long version) {
        PackageInfo adservicesPackageInfo = Mockito.mock(PackageInfo.class);
        adservicesPackageInfo.packageName = packageName;
        adservicesPackageInfo.isApex = true;
        when(adservicesPackageInfo.getLongVersionCode()).thenReturn(version);

        mInstalledPackages.add(adservicesPackageInfo);
    }

    private void mockPackageManager() {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getInstalledPackages(anyInt())).thenReturn(mInstalledPackages);
    }
}
