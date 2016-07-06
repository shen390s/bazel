// Copyright 2014 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.analysis;

import static com.google.devtools.build.lib.analysis.ExtraActionUtils.createExtraActionProvider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.LicensesProvider.TargetLicense;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.constraints.ConstraintSemantics;
import com.google.devtools.build.lib.analysis.constraints.EnvironmentCollection;
import com.google.devtools.build.lib.analysis.constraints.SupportedEnvironments;
import com.google.devtools.build.lib.analysis.constraints.SupportedEnvironmentsProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.License;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.rules.test.ExecutionInfoProvider;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProvider;
import com.google.devtools.build.lib.rules.test.TestActionBuilder;
import com.google.devtools.build.lib.rules.test.TestEnvironmentProvider;
import com.google.devtools.build.lib.rules.test.TestProvider;
import com.google.devtools.build.lib.rules.test.TestProvider.TestParams;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.Preconditions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Builder class for analyzed rule instances (i.e., instances of {@link ConfiguredTarget}).
 */
public final class RuleConfiguredTargetBuilder {
  private final RuleContext ruleContext;
  private final Map<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> providers =
      new LinkedHashMap<>();
  private final ImmutableMap.Builder<String, Object> skylarkProviders = ImmutableMap.builder();
  private final Map<String, NestedSetBuilder<Artifact>> outputGroupBuilders = new TreeMap<>();

  /** These are supported by all configured targets and need to be specially handled. */
  private NestedSet<Artifact> filesToBuild = NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  private RunfilesSupport runfilesSupport;
  private Artifact executable;
  private ImmutableSet<ActionAnalysisMetadata> actionsWithoutExtraAction = ImmutableSet.of();

  public RuleConfiguredTargetBuilder(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
    add(LicensesProvider.class, initializeLicensesProvider());
    add(VisibilityProvider.class, new VisibilityProviderImpl(ruleContext.getVisibility()));
  }

  /**
   * Constructs the RuleConfiguredTarget instance based on the values set for this Builder.
   */
  public ConfiguredTarget build() {
    if (ruleContext.getConfiguration().enforceConstraints()) {
      checkConstraints();
    }
    if (ruleContext.hasErrors()) {
      return null;
    }

    FilesToRunProvider filesToRunProvider = new FilesToRunProvider(
        getFilesToRun(runfilesSupport, filesToBuild), runfilesSupport, executable);
    add(FileProvider.class, new FileProvider(filesToBuild));
    add(FilesToRunProvider.class, filesToRunProvider);
    addSkylarkTransitiveInfo(FilesToRunProvider.SKYLARK_NAME, filesToRunProvider);

    if (runfilesSupport != null) {
      // If a binary is built, build its runfiles, too
      addOutputGroup(
          OutputGroupProvider.HIDDEN_TOP_LEVEL, runfilesSupport.getRunfilesMiddleman());
    } else if (providers.get(RunfilesProvider.class) != null) {
      // If we don't have a RunfilesSupport (probably because this is not a binary rule), we still
      // want to build the files this rule contributes to runfiles of dependent rules so that we
      // report an error if one of these is broken.
      //
      // Note that this is a best-effort thing: there is .getDataRunfiles() and all the language-
      // specific *RunfilesProvider classes, which we don't add here for reasons that are lost in
      // the mists of time.
      addOutputGroup(OutputGroupProvider.HIDDEN_TOP_LEVEL,
          ((RunfilesProvider) providers.get(RunfilesProvider.class))
              .getDefaultRunfiles().getAllArtifacts());
    }

    // Create test action and artifacts if target was successfully initialized
    // and is a test.
    if (TargetUtils.isTestRule(ruleContext.getTarget())) {
      Preconditions.checkState(runfilesSupport != null);
      add(TestProvider.class, initializeTestProvider(filesToRunProvider));
    }

    ExtraActionArtifactsProvider extraActionsProvider =
        createExtraActionProvider(actionsWithoutExtraAction, ruleContext);
    add(ExtraActionArtifactsProvider.class, extraActionsProvider);

    if (!outputGroupBuilders.isEmpty()) {
      ImmutableMap.Builder<String, NestedSet<Artifact>> outputGroups = ImmutableMap.builder();
      for (Map.Entry<String, NestedSetBuilder<Artifact>> entry : outputGroupBuilders.entrySet()) {
        outputGroups.put(entry.getKey(), entry.getValue().build());
      }

      add(OutputGroupProvider.class, new OutputGroupProvider(outputGroups.build()));
    }

    addRegisteredProvidersToSkylarkProviders();
      
    return new RuleConfiguredTarget(ruleContext, skylarkProviders.build(), providers);
  }
  
  /**
   * Adds skylark providers from a skylark provider registry, and checks for collisions.
   */
  private void addRegisteredProvidersToSkylarkProviders() {
    Map<String, Object> nativeSkylarkProviders = new HashMap<>();
    for (Entry<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> entry :
        providers.entrySet()) {
      if (ruleContext.getSkylarkProviderRegistry().containsValue(entry.getKey())) {
        String skylarkName = ruleContext.getSkylarkProviderRegistry().inverse().get(entry.getKey());
        nativeSkylarkProviders.put(skylarkName, entry.getValue());
      }
    }
    try {
      skylarkProviders.putAll(nativeSkylarkProviders);
    } catch (IllegalArgumentException e) {
      ruleContext.ruleError("Collision caused by duplicate skylark providers: " + e.getMessage());
    }
  }

  /**
   * Like getFilesToBuild(), except that it also includes the runfiles middleman, if any.
   * Middlemen are expanded in the SpawnStrategy or by the Distributor.
   */
  private ImmutableList<Artifact> getFilesToRun(
      RunfilesSupport runfilesSupport, NestedSet<Artifact> filesToBuild) {
    if (runfilesSupport == null) {
      return ImmutableList.copyOf(filesToBuild);
    } else {
      ImmutableList.Builder<Artifact> allFilesToBuild = ImmutableList.builder();
      allFilesToBuild.addAll(filesToBuild);
      allFilesToBuild.add(runfilesSupport.getRunfilesMiddleman());
      return allFilesToBuild.build();
    }
  }

  /**
   * Invokes Blaze's constraint enforcement system: checks that this rule's dependencies
   * support its environments and reports appropriate errors if violations are found. Also
   * publishes this rule's supported environments for the rules that depend on it.
   */
  private void checkConstraints() {
    if (!ruleContext.getRule().getRuleClassObject().supportsConstraintChecking()) {
      return;
    }
    EnvironmentCollection supportedEnvironments =
        ConstraintSemantics.getSupportedEnvironments(ruleContext);
    if (supportedEnvironments != null) {
      EnvironmentCollection.Builder refinedEnvironments = new EnvironmentCollection.Builder();
      ConstraintSemantics.checkConstraints(ruleContext, supportedEnvironments, refinedEnvironments);
      add(SupportedEnvironmentsProvider.class,
          new SupportedEnvironments(supportedEnvironments, refinedEnvironments.build()));
    }
  }

  private TestProvider initializeTestProvider(FilesToRunProvider filesToRunProvider) {
    int explicitShardCount = ruleContext.attributes().get("shard_count", Type.INTEGER);
    if (explicitShardCount < 0
        && ruleContext.getRule().isAttributeValueExplicitlySpecified("shard_count")) {
      ruleContext.attributeError("shard_count", "Must not be negative.");
    }
    if (explicitShardCount > 50) {
      ruleContext.attributeError("shard_count",
          "Having more than 50 shards is indicative of poor test organization. "
          + "Please reduce the number of shards.");
    }
    TestActionBuilder testActionBuilder =
        new TestActionBuilder(ruleContext)
            .setInstrumentedFiles(findProvider(InstrumentedFilesProvider.class));

    TestEnvironmentProvider environmentProvider = findProvider(TestEnvironmentProvider.class);
    if (environmentProvider != null) {
      testActionBuilder.setExtraEnv(environmentProvider.getEnvironment());
    }

    final TestParams testParams =
        testActionBuilder
            .setFilesToRunProvider(filesToRunProvider)
            .setExecutionRequirements(findProvider(ExecutionInfoProvider.class))
            .setShardCount(explicitShardCount)
            .build();
    final ImmutableList<String> testTags =
        ImmutableList.copyOf(ruleContext.getRule().getRuleTags());
    return new TestProvider(testParams, testTags);
  }

  private LicensesProvider initializeLicensesProvider() {
    if (!ruleContext.getConfiguration().checkLicenses()) {
      return LicensesProviderImpl.EMPTY;
    }

    NestedSetBuilder<TargetLicense> builder = NestedSetBuilder.linkOrder();
    BuildConfiguration configuration = ruleContext.getConfiguration();
    Rule rule = ruleContext.getRule();
    License toolOutputLicense = rule.getToolOutputLicense(ruleContext.attributes());
    if (configuration.isHostConfiguration() && toolOutputLicense != null) {
      if (toolOutputLicense != License.NO_LICENSE) {
        builder.add(new TargetLicense(rule.getLabel(), toolOutputLicense));
      }
    } else {
      if (rule.getLicense() != License.NO_LICENSE) {
        builder.add(new TargetLicense(rule.getLabel(), rule.getLicense()));
      }

      for (TransitiveInfoCollection dep : ruleContext.getConfiguredTargetMap().values()) {
        LicensesProvider provider = dep.getProvider(LicensesProvider.class);
        if (provider != null) {
          builder.addTransitive(provider.getTransitiveLicenses());
        }
      }
    }

    return new LicensesProviderImpl(builder.build());
  }

  private <T extends TransitiveInfoProvider> T findProvider(Class<T> clazz) {
    return clazz.cast(providers.get(clazz));
  }

  /**
   * Add a specific provider with a given value.
   */
  public <T extends TransitiveInfoProvider> RuleConfiguredTargetBuilder add(Class<T> key, T value) {
    return addProvider(key, value);
  }

  /**
   * Add a specific provider with a given value.
   */
  public RuleConfiguredTargetBuilder addProvider(
      Class<? extends TransitiveInfoProvider> key, TransitiveInfoProvider value) {
    Preconditions.checkNotNull(key);
    Preconditions.checkNotNull(value);
    AnalysisUtils.checkProvider(key);
    providers.put(key, value);
    return this;
  }

  /**
   * Add multiple providers with given values.
   */
  public RuleConfiguredTargetBuilder addProviders(
      Map<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> providers) {
    for (Entry<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> provider :
        providers.entrySet()) {
      addProvider(provider.getKey(), provider.getValue());
    }
    return this;
  }

  /**
   * Add a Skylark transitive info. The provider value must be safe (i.e. a String, a Boolean,
   * an Integer, an Artifact, a Label, None, a Java TransitiveInfoProvider or something composed
   * from these in Skylark using lists, sets, structs or dicts). Otherwise an EvalException is
   * thrown.
   */
  public RuleConfiguredTargetBuilder addSkylarkTransitiveInfo(
      String name, Object value, Location loc) throws EvalException {

    SkylarkProviderValidationUtil.validateAndThrowEvalException(name, value, loc);
    skylarkProviders.put(name, value);
    return this;
  }

  /**
   * Add a Skylark transitive info. The provider value must be safe.
   */
  public RuleConfiguredTargetBuilder addSkylarkTransitiveInfo(
      String name, Object value) {
    SkylarkProviderValidationUtil.checkSkylarkObjectSafe(value);
    skylarkProviders.put(name, value);
    return this;
  }

  /**
   * Set the runfiles support for executable targets.
   */
  public RuleConfiguredTargetBuilder setRunfilesSupport(
      RunfilesSupport runfilesSupport, Artifact executable) {
    this.runfilesSupport = runfilesSupport;
    this.executable = executable;
    return this;
  }

  /**
   * Set the files to build.
   */
  public RuleConfiguredTargetBuilder setFilesToBuild(NestedSet<Artifact> filesToBuild) {
    this.filesToBuild = filesToBuild;
    return this;
  }

  private NestedSetBuilder<Artifact> getOutputGroupBuilder(String name) {
    NestedSetBuilder<Artifact> result = outputGroupBuilders.get(name);
    if (result != null) {
      return result;
    }

    result = NestedSetBuilder.stableOrder();
    outputGroupBuilders.put(name, result);
    return result;
  }

  /**
   * Adds a set of files to an output group.
   */
  public RuleConfiguredTargetBuilder addOutputGroup(String name, NestedSet<Artifact> artifacts) {
    getOutputGroupBuilder(name).addTransitive(artifacts);
    return this;
  }

  /**
   * Adds a file to an output group.
   */
  public RuleConfiguredTargetBuilder addOutputGroup(String name, Artifact artifact) {
    getOutputGroupBuilder(name).add(artifact);
    return this;
  }

  /**
   * Adds multiple output groups.
   */
  public RuleConfiguredTargetBuilder addOutputGroups(Map<String, NestedSet<Artifact>> groups) {
    for (Map.Entry<String, NestedSet<Artifact>> group : groups.entrySet()) {
      getOutputGroupBuilder(group.getKey()).addTransitive(group.getValue());
    }

    return this;
  }

  /**
   * Set the extra action pseudo actions.
   */
  public RuleConfiguredTargetBuilder setActionsWithoutExtraAction(
      ImmutableSet<ActionAnalysisMetadata> actions) {
    this.actionsWithoutExtraAction = actions;
    return this;
  }
}
