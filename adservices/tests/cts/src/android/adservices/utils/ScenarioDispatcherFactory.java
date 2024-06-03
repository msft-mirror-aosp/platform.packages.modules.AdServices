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

package android.adservices.utils;

import org.json.JSONException;

import java.io.IOException;
import java.net.URL;
import java.util.Random;

/** Used to construct {@link ScenarioDispatcher} instances by loading required data. */
public class ScenarioDispatcherFactory {

    private final String mFilePath;
    private final String mPrefix;

    private ScenarioDispatcherFactory(String filePath, String prefix) {
        this.mFilePath = filePath;
        this.mPrefix = prefix;
    }

    /**
     * Construct a factory for a given scenario file path.
     *
     * @param filePath Path to scenario JSON.
     * @return Factory for constructing {@link ScenarioDispatcher} instances.
     */
    public static ScenarioDispatcherFactory createFromScenarioFile(String filePath) {
        return new ScenarioDispatcherFactory(filePath, "");
    }

    /**
     * Construct a factory for a given scenario file path (and add a random prefix).
     *
     * @param filePath Path to scenario JSON.
     * @return Factory for constructing {@link ScenarioDispatcher} instances.
     */
    public static ScenarioDispatcherFactory createFromScenarioFileWithRandomPrefix(
            String filePath) {
        Random random = new Random();
        String prefix = "/" + random.nextInt();
        return new ScenarioDispatcherFactory(filePath, prefix);
    }

    /**
     * Construct a valid dispatcher.
     *
     * @param serverBaseAddress Base URL of the MockWebServer being used
     * @return A valid interface implementing {@link com.squareup.okhttp.Dispatcher}
     * @throws JSONException if the scenario JSON was not able to parse correctly.
     * @throws IOException if the scenario file was not able to load correctly.
     */
    public ScenarioDispatcher getDispatcher(URL serverBaseAddress)
            throws JSONException, IOException {
        return new ScenarioDispatcher(mFilePath, mPrefix, serverBaseAddress);
    }
}
