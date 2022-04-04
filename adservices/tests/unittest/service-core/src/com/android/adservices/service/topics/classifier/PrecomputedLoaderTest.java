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

package com.android.adservices.service.topics.classifier;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * PrecomputedLoader Test {@link PrecomputedLoader}
 */
public class PrecomputedLoaderTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final PrecomputedLoader sPrecomputedLoader = new PrecomputedLoader(sContext);

    @Test
    public void checkLoadedLabels() throws IOException {
        List<String> labels = sPrecomputedLoader.retrieveLabels();
        // Check size of list
        // The labels.txt contains 350 topics
        assertThat(labels.size()).isEqualTo(350);

        // Check some labels
        assertThat(labels).containsAtLeast(
                "/Finance/Banking",
                "/Shopping/Shopping Portals",
                "/Internet & Telecom/Email & Messaging/Voice & Video Chat",
                "/Games/Board Games/Chess & Abstract Strategy Games");
    }

    @Test
    public void checkLoadedAppTopics() throws IOException {
        Map<String, List<String>> appTopic = sPrecomputedLoader.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 1000 apps + 11 sample apps.
        assertThat(appTopic.size()).isEqualTo(1011);

        // Check whatsApp, chrome and a sample app topics in map
        List<String> whatsAppTopics =
                Arrays.asList(
                        "/Internet & Telecom/Email & Messaging/Voice & Video Chat",
                        "/Internet & Telecom/Email & Messaging/Text & Instant Messaging",
                        "/Computers & Electronics/Software/Business & Productivity Software",
                        "/Business & Industrial/Energy & Utilities/Oil & Gas",
                        "/Food & Drink/Restaurants");
        assertThat(appTopic.get("com.whatsapp")).isEqualTo(whatsAppTopics);

        List<String> chromeTopics =
                Arrays.asList(
                        "/Home & Garden/Kitchen & Dining/Small Kitchen Appliances/"
                                + "Coffee & Espresso Makers",
                        "/Computers & Electronics/Software/Internet Software/Web Browsers",
                        "/Business & Industrial/Business Operations/Human Resources/"
                                + "Compensation & Benefits",
                        "/Internet & Telecom/Service Providers/Cable & Satellite Providers",
                        "/Computers & Electronics/Consumer Electronics/Media Streaming Devices");
        assertThat(appTopic.get("com.android.chrome")).isEqualTo(chromeTopics);

        List<String> sampleAppTopics =
                Arrays.asList(
                        "/Internet & Telecom/Email & Messaging/Voice & Video Chat",
                        "/Internet & Telecom/Email & Messaging/Text & Instant Messaging",
                        "/Computers & Electronics/Software/Business & Productivity Software",
                        "/Business & Industrial/Energy & Utilities/Oil & Gas",
                        "/Food & Drink/Restaurants");
        assertThat(appTopic.get("com.android.adservices.samples.topics.sampleapp"))
                .isEqualTo(sampleAppTopics);
    }
}
