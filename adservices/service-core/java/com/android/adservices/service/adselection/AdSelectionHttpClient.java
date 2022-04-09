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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Objects;

/**
 * This is an HTTPClient to be used by both the AdSelection API and Report Impression API. The
 * primary uses of this client will be to fetch Javascript code from an SDK provided URL, and
 * perform reporting on the generated reporting_url's through a GET call.
 */
public class AdSelectionHttpClient {

    private final int mTimeoutMS;
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    public AdSelectionHttpClient(int timeoutMS) {
        mTimeoutMS = timeoutMS;
    }

    public AdSelectionHttpClient() {
        mTimeoutMS = DEFAULT_TIMEOUT_MS;
    }

    /** Opens the Url Connection */
    @NonNull
    private URLConnection openUrl(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        return url.openConnection();
    }

    @NonNull
    private URL toUrl(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        // TODO (b/228380865): Check that the scheme matches "https"
        URL url;
        try {
            url = new URL(uri.toString());
        } catch (MalformedURLException e) {
            LogUtil.d("Uri is malformed! ", e);
            throw new IllegalArgumentException("Uri is malformed!");
        }
        return url;
    }

    @NonNull
    private HttpURLConnection setupConnection(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        HttpURLConnection urlConnection = (HttpURLConnection) openUrl(url);
        urlConnection.setConnectTimeout(mTimeoutMS);
        urlConnection.setReadTimeout(mTimeoutMS);
        // Setting true explicitly to follow redirects
        urlConnection.setInstanceFollowRedirects(true);
        return urlConnection;
    }

    @NonNull
    private String inputStreamToString(@NonNull InputStream in) {
        Objects.requireNonNull(in);

        try {
            return new String(ByteStreams.toByteArray(in), Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Error reading GET response to string!");
        }
    }

    /**
     * Performs a GET request on an SDK provided URI in order to fetch javascript code
     *
     * @param uri Provided by the SDK, will be converted to a URL for fetching
     * @return a string containing the fetched javascript
     */
    @NonNull
    public String fetchJavascript(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        URL url = toUrl(uri);
        // TODO (b/228380865): Change to HttpSURLConnection
        HttpURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url);
        } catch (SocketTimeoutException e) {
            throw new IllegalStateException("Connection timed out!");
        } catch (IOException e) {
            LogUtil.d("Failed to open URL", e);
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                return inputStreamToString(in);
            } else {
                InputStream errorStream = urlConnection.getErrorStream();
                if (!Objects.isNull(errorStream)) {
                    String errorMessage = inputStreamToString(urlConnection.getErrorStream());
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and message: %s",
                                    responseCode,
                                    errorMessage);

                    LogUtil.d(exceptionMessage);
                    throw new IllegalStateException(exceptionMessage);
                } else {
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and null message",
                                    responseCode);

                    LogUtil.d(exceptionMessage);
                    throw new IllegalStateException(exceptionMessage);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IllegalStateException("Connection times out while reading response!", e);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Performs a GET request on a Uri to perform reporting
     *
     * @param uri Provided as a result of invoking buyer or seller javascript.
     * @return an int that represents the response code
     */
    public int reportUrl(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        URL url = toUrl(uri);
        // TODO (b/228380865): Change to HttpSURLConnection
        HttpURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url);
        } catch (SocketTimeoutException e) {
            throw new IllegalStateException("Connection timed out!");
        } catch (IOException e) {
            LogUtil.d("Failed to open URL", e);
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                LogUtil.d("Successfully reported for URl: " + url);
            } else {
                LogUtil.w("Failed to report for URL: " + url);
                InputStream errorStream = urlConnection.getErrorStream();
                if (!Objects.isNull(errorStream)) {
                    String errorMessage = inputStreamToString(urlConnection.getErrorStream());
                    String logMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and message: %s",
                                    responseCode,
                                    errorMessage);

                    LogUtil.d(logMessage);
                } else {
                    String logMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and null message",
                                    responseCode);
                    LogUtil.d(logMessage);
                }
            }
            return responseCode;
        } catch (SocketTimeoutException e) {
            throw new IllegalStateException("Connection times out while reading response!", e);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * @return the timeout to be used in this client
     */
    public int getTimeoutMS() {
        return mTimeoutMS;
    }

    /**
     * @return true if responseCode matches 2.*, i.e. 200, 204, 206
     */
    public static boolean isSuccessfulResponse(int responseCode) {
        return (responseCode / 100) == 2;
    }
}
