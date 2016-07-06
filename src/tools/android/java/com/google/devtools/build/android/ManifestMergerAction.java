// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import static java.util.logging.Level.SEVERE;

import com.google.devtools.build.android.Converters.ExistingPathConverter;
import com.google.devtools.build.android.Converters.ExistingPathListConverter;
import com.google.devtools.build.android.Converters.MergeTypeConverter;
import com.google.devtools.build.android.Converters.PathConverter;
import com.google.devtools.build.android.Converters.StringDictionaryConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;

import com.android.manifmerger.ManifestMerger2.MergeType;
import com.android.utils.StdLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * An action to perform manifest merging using the Gradle manifest merger.
 *
 * <pre>
 * Example Usage:
 *   java/com/google/build/android/ManifestMergerAction
 *       --manifest path to primary manifest
 *       --mergeeManifests colon separated list of manifests to merge
 *       --mergeType APPLICATION|LIBRARY
 *       --manifestValues key value pairs of manifest overrides
 *       --customPackage package to write for library manifest
 *       --manifestOutput path to write output manifest
 * </pre>
 */
public class ManifestMergerAction {
  /** Flag specifications for this action. */
  public static final class Options extends OptionsBase {
    @Option(name = "manifest",
        defaultValue = "null",
        converter = ExistingPathConverter.class,
        category = "input",
        help = "Path of primary manifest.")
    public Path manifest;

    @Option(name = "mergeeManifests",
        defaultValue = "",
        converter = ExistingPathListConverter.class,
        category = "input",
        help = "A list of manifests to be merged into manifest.")
    public List<Path> mergeeManifests;

    @Option(name = "mergeType",
        defaultValue = "APPLICATION",
        converter = MergeTypeConverter.class,
        category = "config",
        help = "The type of merging to perform.")
    public MergeType mergeType;

    @Option(name = "manifestValues",
        defaultValue = "",
        converter = StringDictionaryConverter.class,
        category = "config",
        help = "A dictionary string of values to be overridden in the manifest. Any instance of "
            + "${name} in the manifest will be replaced with the value corresponding to name in "
            + "this dictionary. applicationId, versionCode, versionName, minSdkVersion, "
            + "targetSdkVersion and maxSdkVersion have a dual behavior of also overriding the "
            + "corresponding attributes of the manifest and uses-sdk tags. packageName will be "
            + "ignored and will be set from either applicationId or the package in manifest. The "
            + "expected format of this string is: key:value[,key:value]*. The keys and values "
            + "may contain colons and commas as long as they are escaped with a backslash.")
    public Map<String, String> manifestValues;

    @Option(name = "customPackage",
        defaultValue = "null",
        category = "config",
        help = "Custom java package to insert in the package attribute of the manifest tag.")
    public String customPackage;

    @Option(name = "manifestOutput",
        defaultValue = "null",
        converter = PathConverter.class,
        category = "output",
        help = "Path for the merged manifest.")
    public Path manifestOutput;
  }

  private static final StdLogger stdLogger = new StdLogger(StdLogger.Level.WARNING);
  private static final Logger logger = Logger.getLogger(ManifestMergerAction.class.getName());

  private static Options options;

  public static void main(String[] args) throws Exception {
    OptionsParser optionsParser = OptionsParser.newOptionsParser(Options.class);
    optionsParser.parseAndExitUponError(args);
    options = optionsParser.getOptions(Options.class);

    final AndroidResourceProcessor resourceProcessor = new AndroidResourceProcessor(stdLogger);

    try {
      Path mergedManifest;
      if (options.mergeType == MergeType.APPLICATION) {
        // Ignore custom package at the binary level.
        mergedManifest = resourceProcessor.mergeManifest(
            options.manifest,
            options.mergeeManifests,
            options.mergeType,
            options.manifestValues,
            options.manifestOutput);
      } else {
        // Only need to stamp custom package into the library level.
        mergedManifest = resourceProcessor.writeManifestPackage(
            options.manifest, options.customPackage, options.manifestOutput);
      }

      if (!mergedManifest.equals(options.manifestOutput)) {
        Files.copy(options.manifest, options.manifestOutput, StandardCopyOption.REPLACE_EXISTING);
      }

      // Set to the epoch for caching purposes.
      Files.setLastModifiedTime(options.manifestOutput, FileTime.fromMillis(0L));
    } catch (IOException e) {
      logger.log(SEVERE, "Error during merging manifests", e);
      throw e;
    } finally {
      resourceProcessor.shutdown();
    }
  }
}

