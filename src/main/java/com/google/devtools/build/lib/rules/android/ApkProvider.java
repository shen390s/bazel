// Copyright 2015 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.rules.android;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

/**
 * A provider for targets that can build .apk files. Currently used for coverage collection.
 */
@Immutable
public final class ApkProvider implements TransitiveInfoProvider {

  private final NestedSet<Artifact> transitiveApks;

  private final NestedSet<Artifact> coverageMetadata;

  private final NestedSet<Artifact> mergedManifests;

  public ApkProvider(NestedSet<Artifact> transitiveApks, NestedSet<Artifact> coverageMetdata,
      NestedSet<Artifact> mergedManifests) {
    this.transitiveApks = transitiveApks;
    this.coverageMetadata = coverageMetdata;
    this.mergedManifests = mergedManifests;
  }

  /**
   * Returns the APK files generated in the transitive closure.
   */
  public NestedSet<Artifact> getTransitiveApks() {
    return transitiveApks;
  }

  /**
   * Returns the coverage metadata artifacts generated in the transitive closure.
   */
  public NestedSet<Artifact> getCoverageMetadata() {
    return coverageMetadata;
  }

  /**
   * Returns the merged manifests
   */
  public NestedSet<Artifact> getMergedManifests() {
    return mergedManifests;
  }
}
