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

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.ArrayMap;
import android.util.JsonReader;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.MappedByteBufferOpener;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Model Manager.
 *
 * <p>Model Manager to manage models used the Classifier. Currently, there are 2 types of models: 1)
 * Bundled Model in the APK. 2) Downloaded Model via MDD.
 *
 * <p>ModelManager will select the right model to serve Classifier.
 */
public class ModelManager {
    private static final String FILE_GROUP_NAME = "topics-classifier-model";

    @VisibleForTesting
    static final String BUNDLED_LABELS_FILE_PATH = "classifier/labels_topics.txt";

    @VisibleForTesting
    static final String BUNDLED_TOP_APP_FILE_PATH = "classifier/precomputed_app_list.csv";

    @VisibleForTesting
    static final String BUNDLED_CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_assets_metadata.json";

    @VisibleForTesting static final String BUNDLED_MODEL_FILE_PATH = "classifier/model.tflite";

    // Use "\t" as a delimiter to read the precomputed app topics file
    private static final String LIST_COLUMN_DELIMITER = "\t";
    // Use "," as a delimiter to read multi-topics of one app in precomputed app topics file
    private static final String TOPICS_DELIMITER = ",";

    // The key name of asset metadata property in classifier_assets_metadata.json
    private static final String ASSET_PROPERTY_NAME = "property";
    // The key name of asset element in classifier_assets_metadata.json
    private static final String ASSET_ELEMENT_NAME = "asset_name";
    // The attributions of assets property in classifier_assets_metadata.json
    private static final Set<String> ASSETS_PROPERTY_ATTRIBUTIONS =
            new HashSet(Arrays.asList("taxonomy_type", "taxonomy_version", "updated_date"));
    // The attributions of assets metadata in classifier_assets_metadata.json
    private static final Set<String> ASSETS_NORMAL_ATTRIBUTIONS =
            new HashSet(Arrays.asList("asset_version", "path", "checksum", "updated_date"));

    private static final String DOWNLOADED_LABEL_FILE_ID = "labels_topics.txt";
    private static final String DOWNLOADED_TOP_APPS_FILE_ID = "precomputed_app_list.csv";
    private static final String DOWNLOADED_CLASSIFIER_ASSETS_METADATA_ID =
            "classifier_assets_metadata.json";
    private static final String DOWNLOADED_MODEL_FILE_ID = "model.tflite";

    private static ModelManager sSingleton;
    private final AssetManager mAssetManager;
    private final String mLabelsFilePath;
    private final String mTopAppsFilePath;
    private final String mClassifierAssetsMetadataPath;
    private final String mModelFilePath;
    private final SynchronousFileStorage mFileStorage;
    private final Map<String, ClientFile> mDownloadedFiles;

    @VisibleForTesting
    ModelManager(
            @NonNull Context context,
            @NonNull String labelsFilePath,
            @NonNull String topAppsFilePath,
            @NonNull String classifierAssetsMetadataPath,
            @NonNull String modelFilePath,
            @NonNull SynchronousFileStorage fileStorage,
            @Nullable Map<String, ClientFile> downloadedFiles) {
        mAssetManager = context.getAssets();
        mLabelsFilePath = labelsFilePath;
        mTopAppsFilePath = topAppsFilePath;
        mClassifierAssetsMetadataPath = classifierAssetsMetadataPath;
        mModelFilePath = modelFilePath;
        mFileStorage = fileStorage;
        mDownloadedFiles = downloadedFiles;
    }

    /** Returns the singleton instance of the {@link ModelManager} given a context. */
    @NonNull
    public static ModelManager getInstance(@NonNull Context context) {
        synchronized (ModelManager.class) {
            if (sSingleton == null) {
                sSingleton =
                        new ModelManager(
                                context,
                                BUNDLED_LABELS_FILE_PATH,
                                BUNDLED_TOP_APP_FILE_PATH,
                                BUNDLED_CLASSIFIER_ASSETS_METADATA_PATH,
                                BUNDLED_MODEL_FILE_PATH,
                                MobileDataDownloadFactory.getFileStorage(context),
                                getDownloadedFiles(context));
            }
        }
        return sSingleton;
    }

    /**
     * This function populates metadata files to a map.
     *
     * @param context {@link Context}
     * @return A map<FileId, ClientFile> contains downloaded fileId with ClientFile or null if no
     *     downloaded files found.
     */
    @VisibleForTesting
    static @Nullable Map<String, ClientFile> getDownloadedFiles(@NonNull Context context) {
        MobileDataDownload mobileDataDownload =
                MobileDataDownloadFactory.getMdd(context, FlagsFactory.getFlags());
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder().setGroupName(FILE_GROUP_NAME).build();
        ClientFileGroup fileGroup = null;
        try {
            // TODO(b/242908564). Remove get()
            fileGroup = mobileDataDownload.getFileGroup(getFileGroupRequest).get();
        } catch (ExecutionException | InterruptedException e) {
            LogUtil.e(e, "Unable to load MDD file group.");
            return null;
        }
        Map<String, ClientFile> downloadedFiles = new ArrayMap<>();
        if (fileGroup != null) {
            LogUtil.v("Populating downloadFiles map.");
            for (ClientFile file : fileGroup.getFileList()) {
                downloadedFiles.put(file.getFileId(), file);
            }
        }
        return downloadedFiles;
    }

    // Return true if Model Manager should uses downloaded model. Otherwise, use bundled model.
    private boolean useDownloadedFiles() {
        return mDownloadedFiles != null && mDownloadedFiles.size() > 0;
    }

    /**
     * Load TFLite model as a ByteBuffer.
     *
     * @throws IOException if failed to read downloaded or bundled model file.
     */
    @NonNull
    public ByteBuffer retrieveModel() throws IOException {
        if (useDownloadedFiles()) {
            ClientFile downloadedFile = mDownloadedFiles.get(DOWNLOADED_MODEL_FILE_ID);
            MappedByteBuffer buffer = null;
            if (downloadedFile == null) {
                LogUtil.e("Failed to find downloaded model file");
                return ByteBuffer.allocate(0);
            } else {
                buffer =
                        mFileStorage.open(
                                Uri.parse(downloadedFile.getFileUri()),
                                MappedByteBufferOpener.createForRead());
                return buffer;
            }
        } else {
            // Use bundled files.
            AssetFileDescriptor fileDescriptor = mAssetManager.openFd(mModelFilePath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();

            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    /**
     * Retrieve a list of topicIDs from labels file.
     *
     * @return The list of topicIDs from downloaded or bundled labels file. Empty list will be
     *     returned for {@link IOException}.
     */
    @NonNull
    public ImmutableList<Integer> retrieveLabels() {
        ImmutableList.Builder<Integer> labels = new ImmutableList.Builder();
        InputStream inputStream = InputStream.nullInputStream();
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_LABEL_FILE_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mLabelsFilePath);
            } catch (IOException e) {
                LogUtil.e(e, "Failed to read labels file");
            }
        }
        return getLabelsList(labels, inputStream);
    }

    @NonNull
    private ImmutableList<Integer> getLabelsList(
            @NonNull ImmutableList.Builder<Integer> labels, @NonNull InputStream inputStream) {
        String line;
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            while ((line = reader.readLine()) != null) {
                // If the line has at least 1 digit, this line will be added to the labels.
                if (line.length() > 0) {
                    labels.add(Integer.parseInt(line));
                }
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read precomputed labels");
            // When catching IOException -> return empty immutable list
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ImmutableList.of();
        }

        return labels.build();
    }

    /**
     * Retrieve the app classification topicIDs.
     *
     * @return The map from App to the list of its classification topicIDs.
     */
    @NonNull
    public Map<String, List<Integer>> retrieveAppClassificationTopics() {
        // appTopicsMap = Map<App, List<Topic>>
        Map<String, List<Integer>> appTopicsMap = new ArrayMap<>();

        // The immutable set of the topics from labels file
        ImmutableList<Integer> validTopics = retrieveLabels();
        InputStream inputStream = InputStream.nullInputStream();
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_TOP_APPS_FILE_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mTopAppsFilePath);
            } catch (IOException e) {
                LogUtil.e(e, "Failed to read top apps file");
            }
        }
        return getAppsTopicMap(appTopicsMap, validTopics, inputStream);
    }

    @NonNull
    private Map<String, List<Integer>> getAppsTopicMap(
            @NonNull Map<String, List<Integer>> appTopicsMap,
            @NonNull ImmutableList<Integer> validTopics,
            @NonNull InputStream inputStream) {
        String line;
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            // Skip first line (columns name)
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(LIST_COLUMN_DELIMITER);

                // If the line has less than 2 elements, this app contains empty topic
                // and save an empty topic list of this app in appTopicsMap.
                if (columns.length < 2) {
                    // columns[0] if the app's name
                    appTopicsMap.put(columns[0], ImmutableList.of());
                    continue;
                }

                // The first column is app package name
                String app = columns[0];

                // The second column is multi-topics of the app
                String[] appTopics = columns[1].split(TOPICS_DELIMITER);

                // This list is used to temporarily store the allowed topicIDs of one app.
                List<Integer> allowedAppTopics = new ArrayList<>();

                for (String appTopic : appTopics) {
                    // The topic will not save to the app topics map
                    // if it is not a valid topic in labels file
                    if (!validTopics.contains(Integer.parseInt(appTopic))) {
                        LogUtil.e(
                                "Unable to load topicID \"%s\" in app \"%s\", "
                                        + "because it is not a valid topic in labels file.",
                                appTopic, app);
                        continue;
                    }

                    // Add the allowed topic to the list
                    allowedAppTopics.add(Integer.parseInt(appTopic));
                }

                appTopicsMap.put(app, ImmutableList.copyOf(allowedAppTopics));
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read precomputed app topics list");
            // When catching IOException -> return empty hash map
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ImmutableMap.of();
        }

        return appTopicsMap;
    }

    /**
     * Retrieve the assets names and their corresponding metadata.
     *
     * @return The immutable map of assets metadata from {@code mClassifierAssetsMetadataPath}.
     *     Empty map will be returned for {@link IOException}.
     */
    @NonNull
    public ImmutableMap<String, ImmutableMap<String, String>> retrieveClassifierAssetsMetadata() {
        // Initialize a ImmutableMap.Builder to store the classifier assets metadata iteratively.
        // classifierAssetsMetadata = ImmutableMap<AssetName, ImmutableMap<MetadataName, Value>>
        ImmutableMap.Builder<String, ImmutableMap<String, String>> classifierAssetsMetadata =
                new ImmutableMap.Builder<>();
        InputStream inputStream = InputStream.nullInputStream();
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_CLASSIFIER_ASSETS_METADATA_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mClassifierAssetsMetadataPath);
            } catch (IOException e) {
                LogUtil.e(e, "Failed to read bundled metadata file");
            }
        }
        return getAssetsMetadataMap(classifierAssetsMetadata, inputStream);
    }

    @NonNull
    private ImmutableMap<String, ImmutableMap<String, String>> getAssetsMetadataMap(
            @NonNull
                    ImmutableMap.Builder<String, ImmutableMap<String, String>>
                            classifierAssetsMetadata,
            @NonNull InputStream inputStream) {
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            JsonReader reader = new JsonReader(inputStreamReader);

            reader.beginArray();
            while (reader.hasNext()) {
                // Use an immutable map to store the metadata of one asset.
                // assetMetadata = ImmutableMap<MetadataName, Value>
                ImmutableMap.Builder<String, String> assetMetadata = new ImmutableMap.Builder<>();

                // Use jsonElementKey to save the key name of each array element.
                String jsonElementKey = null;

                // Begin to read one json element in the array here.
                reader.beginObject();
                if (reader.hasNext()) {
                    String elementKeyName = reader.nextName();

                    if (elementKeyName.equals(ASSET_PROPERTY_NAME)) {
                        jsonElementKey = reader.nextString();

                        while (reader.hasNext()) {
                            String attribution = reader.nextName();
                            // Check if the attribution name can be found in the property's key set.
                            if (ASSETS_PROPERTY_ATTRIBUTIONS.contains(attribution)) {
                                assetMetadata.put(attribution, reader.nextString());
                            } else {
                                // Skip the redundant metadata name if it can't be found
                                // in the ASSETS_PROPERTY_ATTRIBUTIONS.
                                reader.skipValue();
                                LogUtil.e(
                                        attribution,
                                        " is a redundant metadata attribution of "
                                                + "metadata property.");
                            }
                        }
                    } else if (elementKeyName.equals(ASSET_ELEMENT_NAME)) {
                        jsonElementKey = reader.nextString();

                        while (reader.hasNext()) {
                            String attribution = reader.nextName();
                            // Check if the attribution name can be found in the asset's key set.
                            if (ASSETS_NORMAL_ATTRIBUTIONS.contains(attribution)) {
                                assetMetadata.put(attribution, reader.nextString());
                            } else {
                                // Skip the redundant metadata name if it can't be found
                                // in the ASSET_NORMAL_ATTRIBUTIONS.
                                reader.skipValue();
                                LogUtil.e(
                                        attribution,
                                        " is a redundant metadata attribution of asset.");
                            }
                        }
                    } else {
                        // Skip the json element if it doesn't have key "property" or "asset_name".
                        while (reader.hasNext()) {
                            reader.skipValue();
                        }
                        LogUtil.e(
                                "Can't load this json element, "
                                        + "because \"property\" or \"asset_name\" "
                                        + "can't be found in the json element.");
                    }
                }
                reader.endObject();

                // Save the metadata of the asset if and only if the assetName can be retrieved
                // correctly from the metadata json file.
                if (jsonElementKey != null) {
                    classifierAssetsMetadata.put(jsonElementKey, assetMetadata.build());
                }
            }
            reader.endArray();
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read classifier assets metadata file");
            // When catching IOException -> return empty immutable map
            return ImmutableMap.of();
        }
        return classifierAssetsMetadata.build();
    }

    // Return an InputStream if downloaded model file can be found by
    // ClientFile.file_id.
    @NonNull
    private InputStream readDownloadedFile(String fileId) {
        InputStream inputStream = InputStream.nullInputStream();
        ClientFile downloadedFile = mDownloadedFiles.get(fileId);
        if (downloadedFile == null) {
            LogUtil.e("Failed to find downloaded %s file", fileId);
            return inputStream;
        }
        try {
            inputStream =
                    mFileStorage.open(
                            Uri.parse(downloadedFile.getFileUri()), ReadStreamOpener.create());
        } catch (IOException e) {
            LogUtil.e(e, "Failed to load fileId = %s", fileId);
        }
        return inputStream;
    }
}
