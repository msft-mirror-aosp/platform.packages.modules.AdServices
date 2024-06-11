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

package android.adservices.cts;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@SmallTest
public class ScheduleCustomAudienceUpdateRequestTest extends AdServicesUnitTestCase {
    public static Uri VALID_UPDATE_URI_1 =
            CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_1, "1");
    public static Duration VALID_DELAY = Duration.ofMinutes(100);
    public static PartialCustomAudience VALID_PARTIAL_CA =
            new PartialCustomAudience.Builder("fake_ca").build();
    public static List<PartialCustomAudience> VALID_PARTIAL_CA_LIST = List.of(VALID_PARTIAL_CA);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidRequest_All_Success() {
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .build();

        expect.withMessage("Update Uri").that(request.getUpdateUri()).isEqualTo(VALID_UPDATE_URI_1);
        expect.withMessage("Min Delay Time").that(request.getMinDelay()).isEqualTo(VALID_DELAY);
        expect.withMessage("Partial Custom Audience List")
                .that(request.getPartialCustomAudienceList())
                .containsExactly(VALID_PARTIAL_CA);
        expect.withMessage("Default value of shouldReplacePendingUpdates()")
                .that(request.shouldReplacePendingUpdates())
                .isFalse();
    }

    @Test
    public void testBuildValidRequest_AllSetters_Success() {
        Uri uri2 = CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_2, "2");
        Duration delay2 = Duration.ofMinutes(200);
        List<PartialCustomAudience> emptyCaList = Collections.emptyList();

        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .setUpdateUri(uri2)
                        .setMinDelay(delay2)
                        .setPartialCustomAudienceList(emptyCaList)
                        .setShouldReplacePendingUpdates(true)
                        .build();

        expect.withMessage("Update Uri").that(request.getUpdateUri()).isEqualTo(uri2);
        expect.withMessage("Min Delay time").that(request.getMinDelay()).isEqualTo(delay2);
        expect.withMessage("Partial Custom Audience List")
                .that(request.getPartialCustomAudienceList())
                .isEmpty();
        expect.withMessage("shouldReplacePendingUpdates")
                .that(request.shouldReplacePendingUpdates())
                .isTrue();
    }

    @Test
    public void testConstructor_NullUpdateUri_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateRequest.Builder(
                                null, VALID_DELAY, VALID_PARTIAL_CA_LIST));
    }

    @Test
    public void testConstructor_NullDelay_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, null, VALID_PARTIAL_CA_LIST));
    }

    @Test
    public void testConstructor_NegativeDelay_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, Duration.ofDays(-1), VALID_PARTIAL_CA_LIST));
    }

    @Test
    public void testConstructor_NullPartialCa_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, null));
    }

    @Test
    public void testSetter_NullUpdateUri_Throws() {
        ScheduleCustomAudienceUpdateRequest.Builder builder =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                        VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST);

        assertThrows(NullPointerException.class, () -> builder.setUpdateUri(null));
    }

    @Test
    public void testSetter_NullDelay_Throws() {
        ScheduleCustomAudienceUpdateRequest.Builder builder =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                        VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST);

        assertThrows(NullPointerException.class, () -> builder.setMinDelay(null));
    }

    @Test
    public void testSetter_NegativeDelay_Throws() {
        ScheduleCustomAudienceUpdateRequest.Builder builder =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                        VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST);

        assertThrows(
                IllegalArgumentException.class, () -> builder.setMinDelay(Duration.ofDays(-1)));
    }

    @Test
    public void testSetter_NullPartialCa_Throws() {
        ScheduleCustomAudienceUpdateRequest.Builder builder =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                        VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST);

        assertThrows(NullPointerException.class, () -> builder.setPartialCustomAudienceList(null));
    }

    @Test
    public void testEquals_Same() {
        EqualsTester et = new EqualsTester(expect);
        ScheduleCustomAudienceUpdateRequest request1 =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .build();

        ScheduleCustomAudienceUpdateRequest request2 =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .build();

        et.expectObjectsAreEqual(request2, request1);
    }

    @Test
    public void testEquals_Different() {
        EqualsTester et = new EqualsTester(expect);
        ScheduleCustomAudienceUpdateRequest request1 =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .build();

        ScheduleCustomAudienceUpdateRequest request2 =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, Collections.emptyList())
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testHashCode_Same() {
        int request1Hash =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .setShouldReplacePendingUpdates(true)
                        .build()
                        .hashCode();

        int request2Hash =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .setShouldReplacePendingUpdates(true)
                        .build()
                        .hashCode();

        expect.withMessage("Object hash").that(request1Hash == request2Hash).isTrue();
    }

    @Test
    public void testHashCode_Different() {
        int request1Hash =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .build()
                        .hashCode();

        int request2Hash =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, Collections.emptyList())
                        .build()
                        .hashCode();

        expect.withMessage("Object hash").that(request1Hash == request2Hash).isFalse();
    }

    @Test
    public void testToString() {
        String request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                VALID_UPDATE_URI_1, VALID_DELAY, VALID_PARTIAL_CA_LIST)
                        .setShouldReplacePendingUpdates(true)
                        .build()
                        .toString();

        String requestString = request.toString();
        expect.withMessage("toString()")
                .that(requestString)
                .contains("ScheduleCustomAudienceUpdateRequest");
        expect.withMessage("toString()")
                .that(requestString)
                .contains("mUpdateUri=" + VALID_UPDATE_URI_1);
        expect.withMessage("toString()").that(requestString).contains("mMinDelay=" + VALID_DELAY);
        expect.withMessage("toString()")
                .that(requestString)
                .contains("mPartialCustomAudienceList=" + VALID_PARTIAL_CA_LIST);
        expect.withMessage("toString()")
                .that(requestString)
                .contains("mShouldReplacePendingUpdates=" + true);
    }
}
