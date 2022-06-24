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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LogUtil;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

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
import java.util.concurrent.ExecutorService;

import javax.net.ssl.HttpsURLConnection;

/**
 * This is an HTTPS client to be used by the PP API services. The primary uses of this client
 * include fetching payloads from ad tech-provided URLs and reporting on generated reporting URLs
 * through GET calls.
 */
public class AdServicesHttpsClient {

    private final int mTimeoutMS;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private final ListeningExecutorService mExecutorService;

    public AdServicesHttpsClient(int timeoutMS, ExecutorService executorService) {
        mTimeoutMS = timeoutMS;
        this.mExecutorService = MoreExecutors.listeningDecorator(executorService);
    }

    public AdServicesHttpsClient(ExecutorService executorService) {
        mTimeoutMS = DEFAULT_TIMEOUT_MS;
        this.mExecutorService = MoreExecutors.listeningDecorator(executorService);
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
        Preconditions.checkArgument(
                uri.getScheme().equalsIgnoreCase("https"), "Only HTTPS connections are allowed!");

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
    private HttpsURLConnection setupConnection(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);

        // We validated that the URL is https in toUrl
        HttpsURLConnection urlConnection = (HttpsURLConnection) openUrl(url);
        urlConnection.setConnectTimeout(mTimeoutMS);
        urlConnection.setReadTimeout(mTimeoutMS);
        // Setting true explicitly to follow redirects
        urlConnection.setInstanceFollowRedirects(true);
        return urlConnection;
    }

    @NonNull
    private String inputStreamToString(@NonNull InputStream in) throws IOException {
        Objects.requireNonNull(in);

        return new String(ByteStreams.toByteArray(in), Charsets.UTF_8);
    }

    /**
     * Performs a GET request on an SDK provided URI in order to fetch javascript code
     *
     * @param uri Provided by the SDK, will be converted to a URL for fetching
     * @return a string containing the fetched javascript
     */
    @NonNull
    public ListenableFuture<String> fetchJavascript(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        return mExecutorService.submit(() -> doFetchJavascript(uri));
    }

    private String doFetchJavascript(@NonNull Uri uri) throws IOException {
        URL url = toUrl(uri);
        HttpsURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url);
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out!");
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
                                    "Server returned an error with code %d and message:" + " %s",
                                    responseCode,
                                    errorMessage);

                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                } else {
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and null" + " message",
                                    responseCode);

                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection times out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
        }
    }

    /**
     * Performs a GET request on a Uri to perform reporting
     *
     * @param uri Provided as a result of invoking buyer or seller javascript.
     * @return an int that represents the HTTP response code in a successful case
     */
    // TODO (b/229660545): Return a void instead of an int
    public ListenableFuture<Void> reportUrl(@NonNull Uri uri) {
        Objects.requireNonNull(uri);

        return mExecutorService.submit(() -> doReportUrl(uri));
    }

    private Void doReportUrl(@NonNull Uri uri) throws IOException {
        URL url = toUrl(uri);
        // TODO (b/228380865): Change to HttpSURLConnection
        HttpsURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url);
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out!");
        } catch (IOException e) {
            LogUtil.d("Failed to open URL", e);
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                LogUtil.d("Successfully reported for URl: " + url);
                return null;
            } else {
                LogUtil.w("Failed to report for URL: " + url);
                InputStream errorStream = urlConnection.getErrorStream();
                if (!Objects.isNull(errorStream)) {
                    String errorMessage = inputStreamToString(urlConnection.getErrorStream());
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and message:" + " %s",
                                    responseCode,
                                    errorMessage);

                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);

                } else {
                    String exceptionMessage =
                            String.format(
                                    Locale.US,
                                    "Server returned an error with code %d and null" + " message",
                                    responseCode);
                    LogUtil.d(exceptionMessage);
                    throw new IOException(exceptionMessage);
                }
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection times out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
        }
    }

    private static void maybeDisconnect(@Nullable URLConnection urlConnection) {
        if (urlConnection == null) {
            return;
        }

        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
            httpUrlConnection.disconnect();
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
