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

package com.google.devtools.build.lib.rules.objc;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Root;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.rules.cpp.CppCompilationContext.Builder;
import com.google.devtools.build.lib.rules.cpp.CppCompileActionBuilder;
import com.google.devtools.build.lib.rules.cpp.CppCompileActionContext;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.HeadersCheckingMode;
import com.google.devtools.build.lib.rules.cpp.CppHelper;
import com.google.devtools.build.lib.rules.cpp.CppSemantics;
import com.google.devtools.build.lib.vfs.PathFragment;

/**
 * CppSemantics for objc builds.
 */
public class ObjcCppSemantics implements CppSemantics {

  // We make CppSemantics a singleton object for efficiency and consistency, since we expect
  // any instance to be identical.
  public static final CppSemantics INSTANCE = new ObjcCppSemantics();

  @Override
  public PathFragment getEffectiveSourcePath(Artifact source) {
    return source.getRootRelativePath();
  }

  @Override
  public void finalizeCompileActionBuilder(
      RuleContext ruleContext, CppCompileActionBuilder actionBuilder) {
    actionBuilder.setCppConfiguration(ruleContext.getFragment(CppConfiguration.class));
    actionBuilder.setActionContext(CppCompileActionContext.class);
    // Because Bazel does not support include scanning, we need the entire crosstool filegroup,
    // including header files, as opposed to just the "compile" filegroup.
    actionBuilder.addTransitiveMandatoryInputs(CppHelper.getToolchain(ruleContext).getCrosstool());
    actionBuilder.setShouldScanIncludes(false);
  }

  @Override
  public void setupCompilationContext(RuleContext ruleContext, Builder contextBuilder) {
    // For objc builds, no extra setup is required.
  }

  @Override
  public HeadersCheckingMode determineHeadersCheckingMode(RuleContext ruleContext) {
    // Currently, objc builds do not enforce strict deps.  To begin enforcing strict deps in objc,
    // switch this flag to STRICT.
    return HeadersCheckingMode.WARN;
  }

  @Override
  public boolean needsIncludeScanning(RuleContext ruleContext) {
    return false;
  }

  @Override
  public Root getGreppedIncludesDirectory(RuleContext ruleContext) {
    return null;
  }
}
