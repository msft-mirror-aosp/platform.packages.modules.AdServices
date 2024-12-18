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

package com.android.adservices.service.consent;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Locale;

@SpyStatic(FlagsFactory.class)
public final class DeviceRegionProviderTest extends AdServicesExtendedMockitoTestCase {

    @Mock private TelephonyManager mTelephonyManager;
    @Mock private PackageManager mPackageManager;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);

        // return the default EEA countries list for most test cases
        doReturn(Flags.UI_EEA_COUNTRIES).when(mMockFlags).getUiEeaCountries();
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testGbEmptyEeaCountriesList() {
        // simulate the case where we update the default list to empty.
        doReturn("").when(mMockFlags).getUiEeaCountries();

        doReturn("gb").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isFalse();
    }

    @Test
    public void testChEmptyEeaCountriesList() {
        // simulate the case where we update the default list to empty.
        doReturn("").when(mMockFlags).getUiEeaCountries();

        doReturn("ch").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isFalse();
    }

    @Test
    public void testUsEmptyEeaCountriesList() {
        // simulate the case where we update the default list to empty.
        doReturn("").when(mMockFlags).getUiEeaCountries();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isFalse();
    }

    @Test
    public void isGbDeviceTrue() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("gb").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void isChDeviceTrue() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("ch").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void isEuDeviceTrue() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void isEuDeviceFalse() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isFalse();
    }

    @Test
    public void noSimCardInstalledTest() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void telephonyManagerDoesntExistTest() {
        doReturn(false).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void telephonyManagerNotAccessibleTest() {
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(null).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_isEeaDeviceNoSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_isEeaDeviceUsSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_isEeaDeviceGbSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_notEeaDeviceUsSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(false).when(mMockFlags).isEeaDevice();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isFalse();
    }

    @Test
    public void deviceRegionFlagOnTest_notEeaDeviceChSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(false).when(mMockFlags).isEeaDevice();

        doReturn("ch").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isFalse();
    }

    @Test
    public void deviceRegionFlagOnTest_gbFlagNoSimTabletOff() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(mTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_gbFlagNoSimTabletOn() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();
        doReturn(true).when(mMockFlags).getEnableTabletRegionFix();

        assertThat(DeviceRegionProvider.isEuDevice(mMockContext)).isTrue();
    }

    @Test
    public void validEeaCountriesStringTest_defaultList() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(Flags.UI_EEA_COUNTRIES)).isTrue();
    }

    @Test
    public void invalidEeaCountriesStringTest_nullString() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(null)).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_emptyString() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(" ")).isFalse();
    }

    @Test
    public void validEeaCountriesStringTest_singleCountry() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL")).isTrue();
    }

    @Test
    public void validEeaCountriesStringTest_multipleCountries() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL,GB,CH")).isTrue();
    }

    @Test
    public void invalidEeaCountriesStringTest_extraComma() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("US,")).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_missingComma() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PLGB")).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_nullString_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(null)).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_emptyString_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(" ")).isFalse();
    }

    @Test
    public void validEeaCountriesStringTest_singleCountry_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL")).isTrue();
    }

    @Test
    public void validEeaCountriesStringTest_multipleCountries_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL,GB,CH")).isTrue();
    }

    @Test
    public void invalidEeaCountriesStringTest_extraComma_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("US,")).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_missingComma_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PLGB")).isFalse();
    }

    @Test
    public void eeaCountriesForAllLocales_defaultList() {
        for (Locale locale : Locale.getAvailableLocales()) {
            Locale.setDefault(locale);
            assertThat(DeviceRegionProvider.isValidEeaCountriesString(Flags.UI_EEA_COUNTRIES))
                    .isTrue();
        }
    }
}