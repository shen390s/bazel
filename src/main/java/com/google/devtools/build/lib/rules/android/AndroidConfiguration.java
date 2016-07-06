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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.build.lib.analysis.RedirectChaser;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.EmptyToNullLabelConverter;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.Fragment;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.LabelConverter;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.StrictDepsConverter;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.StrictDepsMode;
import com.google.devtools.build.lib.analysis.config.BuildOptions;
import com.google.devtools.build.lib.analysis.config.ConfigurationEnvironment;
import com.google.devtools.build.lib.analysis.config.ConfigurationFragmentFactory;
import com.google.devtools.build.lib.analysis.config.FragmentOptions;
import com.google.devtools.build.lib.analysis.config.InvalidConfigurationException;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.Attribute.SplitTransition;
import com.google.devtools.common.options.Converter;
import com.google.devtools.common.options.Converters;
import com.google.devtools.common.options.EnumConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsParsingException;

import java.util.List;
import java.util.Set;

/**
 * Configuration fragment for Android rules.
 */
@Immutable
public class AndroidConfiguration extends BuildConfiguration.Fragment {

  /**
   * Converter for {@link com.google.devtools.build.lib.rules.android.AndroidConfiguration.ConfigurationDistinguisher}
   */
  public static final class ConfigurationDistinguisherConverter
      extends EnumConverter<ConfigurationDistinguisher> {
    public ConfigurationDistinguisherConverter() {
      super(ConfigurationDistinguisher.class, "Android configuration distinguisher");
    }
  }

  /**
   * Converter for {@link IncrementalDexing}.
   */
  public static final class IncrementalDexingConverter extends EnumConverter<IncrementalDexing> {
    public IncrementalDexingConverter() {
      super(IncrementalDexing.class, "incremental dexing option");
    }
  }

  /**
   * Converter for a set of {@link AndroidBinaryType}s.
   */
  public static final class AndroidBinaryTypesConverter
      implements Converter<Set<AndroidBinaryType>> {

    private final EnumConverter<AndroidBinaryType> elementConverter =
        new EnumConverter<AndroidBinaryType>(AndroidBinaryType.class, "Android binary type") {};
    private final Splitter splitter = Splitter.on(',').omitEmptyStrings().trimResults();

    public AndroidBinaryTypesConverter() {}

    @Override
    public ImmutableSet<AndroidBinaryType> convert(String input) throws OptionsParsingException {
      if ("all".equals(input)) {
        return ImmutableSet.copyOf(AndroidBinaryType.values());
      }
      ImmutableSet.Builder<AndroidBinaryType> result = ImmutableSet.builder();
      for (String opt : splitter.split(input)) {
        result.add(elementConverter.convert(opt));
      }
      return result.build();
    }

    @Override
    public String getTypeDescription() {
      return "comma-separated list of: " + elementConverter.getTypeDescription();
    }
  }

  /**
   * Value used to avoid multiple configurations from conflicting.
   *
   * <p>This is set to {@code ANDROID} in Android configurations and to {@code MAIN} otherwise. This
   * influences the output directory name: if it didn't, an Android and a non-Android configuration
   * would conflict if they had the same toolchain identifier.
   *
   * <p>Note that this is not just a theoretical concern: even if {@code --crosstool_top} and
   * {@code --android_crosstool_top} point to different labels, they may end up being redirected to
   * the same thing, and this is exactly what happens on OSX X.
   */
  public enum ConfigurationDistinguisher {
    MAIN(null),
    ANDROID("android");

    private final String suffix;

    private ConfigurationDistinguisher(String suffix) {
      this.suffix = suffix;
    }
  }

  /** Types of android binaries as {@link AndroidBinary#dex} distinguishes them. */
  public enum AndroidBinaryType {
    MONODEX, MULTIDEX_UNSHARDED, MULTIDEX_SHARDED
  }

  /** When to use incremental dexing (using {@link DexArchiveProvider}). */
  private enum IncrementalDexing {
    OFF(),
    WITH_DEX_SHARDS(AndroidBinaryType.MULTIDEX_SHARDED),
    WITH_MULTIDEX(AndroidBinaryType.MULTIDEX_UNSHARDED, AndroidBinaryType.MULTIDEX_SHARDED),
    WITH_MONODEX_OR_DEX_SHARDS(AndroidBinaryType.MONODEX, AndroidBinaryType.MULTIDEX_SHARDED),
    AS_PERMITTED(AndroidBinaryType.values());

    private ImmutableSet<AndroidBinaryType> binaryTypes;

    private IncrementalDexing(AndroidBinaryType... binaryTypes) {
      this.binaryTypes = ImmutableSet.copyOf(binaryTypes);
    }
  }

  /**
   * Android configuration options.
   */
  public static class Options extends FragmentOptions {
    // Spaces make it impossible to specify this on the command line
    @Option(name = "Android configuration distinguisher",
        defaultValue = "MAIN",
        converter = ConfigurationDistinguisherConverter.class,
        category = "undocumented")
    public ConfigurationDistinguisher configurationDistinguisher;

    // For deploying incremental installation of native libraries. Do not use on the command line.
    // The idea is that once this option works, we'll flip the default value in a config file, then
    // once it is proven that it works, remove it from Bazel and said config file.
    @Option(name = "android_incremental_native_libs",
        defaultValue = "false",
        category = "undocumented")
    public boolean incrementalNativeLibs;

    @Option(name = "android_crosstool_top",
        defaultValue = "//external:android/crosstool",
        category = "semantics",
        converter = EmptyToNullLabelConverter.class,
        help = "The location of the C++ compiler used for Android builds.")
    public Label androidCrosstoolTop;

    @Option(name = "android_cpu",
        defaultValue = "armeabi",
        category = "semantics",
        help = "The Android target CPU.")
    public String cpu;

    @Option(
      name = "android_compiler",
      defaultValue = "null",
      category = "semantics",
      help = "The Android target compiler."
    )
    public String cppCompiler;

    @Option(name = "strict_android_deps",
        allowMultiple = false,
        defaultValue = "default",
        converter = StrictDepsConverter.class,
        category = "semantics",
        help = "If true, checks that an Android target explicitly declares all directly used "
            + "targets as dependencies.")
    public StrictDepsMode strictDeps;

    // Label of filegroup combining all Android tools used as implicit dependencies of
    // android_* rules
    @Option(name = "android_sdk",
            defaultValue = "@bazel_tools//tools/android:sdk",
            category = "version",
            converter = LabelConverter.class,
            help = "Specifies Android SDK/platform that is used to build Android applications.")
    public Label sdk;

    @Option(name = "legacy_android_native_support",
        defaultValue = "true",
        category = "semantics",
        help = "Switches back to old native support for android_binaries. Disable to link together "
            + "native deps of android_binaries into a single .so by default.")
    public boolean legacyNativeSupport;

    // TODO(bazel-team): Maybe merge this with --android_cpu above.
    @Option(name = "fat_apk_cpu",
            converter = Converters.CommaSeparatedOptionListConverter.class,
            defaultValue = "armeabi-v7a",
            category = "undocumented",
            help = "Setting this option enables fat APKs, which contain native binaries for all "
                + "specified target architectures, e.g., --fat_apk_cpu=x86,armeabi-v7a. Note that "
                + "you will also at least need to select an Android-compatible crosstool. "
                + "If this flag is specified, then --android_cpu is ignored for dependencies of "
                + "android_binary rules.")
    public List<String> fatApkCpus;

    @Option(name = "experimental_android_use_jack_for_dexing",
        defaultValue = "false",
        category = "semantics",
        help = "Switches to the Jack and Jill toolchain for dexing instead of javac and dx.")
    public boolean useJackForDexing;

    @Option(name = "experimental_android_jack_sanity_checks",
        defaultValue = "false",
        category = "semantics",
        help = "Enables sanity checks for Jack and Jill compilation.")
    public boolean jackSanityChecks;

    @Option(name = "experimental_incremental_dexing",
        defaultValue = "off",
        category = "undocumented",
        converter = IncrementalDexingConverter.class,
        deprecationWarning = "Use --incremental_dexing instead to turn on incremental dexing.",
        help = "Does most of the work for dexing separately for each Jar file.  Incompatible with "
            + "Jack and Jill.")
    public IncrementalDexing dexingStrategy;

    @Option(name = "incremental_dexing",
        defaultValue = "false",
        category = "semantics",
        implicitRequirements = "--noexperimental_android_use_jack_for_dexing",
        help = "Does most of the work for dexing separately for each Jar file.  Incompatible with "
            + "Jack and Jill.")
    public boolean incrementalDexing;

    // Do not use on the command line.
    // The idea is that this option lets us gradually turn on incremental dexing for different
    // binaries.  Users should rely on --noincremental_dexing to turn it off.
    @Option(name = "incremental_dexing_binary_types",
        defaultValue = "multidex_sharded",
        category = "undocumented",
        converter = AndroidBinaryTypesConverter.class,
        implicitRequirements = "--incremental_dexing",
        help = "Kinds of binaries to incrementally dex if --incremental_dexing is true.")
    public Set<AndroidBinaryType> incrementalDexingBinaries;

    @Option(name = "non_incremental_per_target_dexopts",
        converter = Converters.CommaSeparatedOptionListConverter.class,
        defaultValue = "--no-locals",
        category = "semantics",
        help = "dx flags that that prevent incremental dexing for binary targets that list any of "
            + "the flags listed here in their 'dexopts' attribute, which are ignored with "
            + "incremental dexing.  Defaults to --no-locals for safety but can in general be used "
            + "to make sure the listed dx flags are honored, with additional build latency.  "
            + "Please notify us if you find yourself needing this flag.")
    public List<String> nonIncrementalPerTargetDexopts;

    @Option(name = "experimental_allow_android_library_deps_without_srcs",
        defaultValue = "true",
        category = "undocumented",
        help = "Flag to help transition from allowing to disallowing srcs-less android_library"
            + " rules with deps. The depot needs to be cleaned up to roll this out by default.")
    public boolean allowAndroidLibraryDepsWithoutSrcs;

    @Option(name = "experimental_android_resource_shrinking",
        defaultValue = "false",
        category = "undocumented",
        help = "Enables resource shrinking for android_binary APKs that use proguard.")
    public boolean useAndroidResourceShrinking;

    @Override
    public void addAllLabels(Multimap<String, Label> labelMap) {
      if (androidCrosstoolTop != null) {
        labelMap.put("android_crosstool_top", androidCrosstoolTop);
      }

      labelMap.put("android_sdk", sdk);
    }

    @Override
    public FragmentOptions getHost(boolean fallback) {
      Options host = (Options) super.getHost(fallback);
      host.androidCrosstoolTop = androidCrosstoolTop;
      host.sdk = sdk;
      return host;
    }

    @Override
    public ImmutableList<String> getDefaultsRules() {
      return ImmutableList.of("android_tools_defaults_jar(name = 'android_jar')");
    }

    @Override
    public List<SplitTransition<BuildOptions>> getPotentialSplitTransitions() {
      return ImmutableList.of(AndroidRuleClasses.ANDROID_SPLIT_TRANSITION);
    }
  }

  /**
   * Configuration loader for the Android fragment.
   */
  public static class Loader implements ConfigurationFragmentFactory {
    @Override
    public Fragment create(ConfigurationEnvironment env, BuildOptions buildOptions)
        throws InvalidConfigurationException {
      AndroidConfiguration.Options androidOptions =
          buildOptions.get(AndroidConfiguration.Options.class);
      Label androidSdk = RedirectChaser.followRedirects(env, androidOptions.sdk, "android_sdk");
      if (androidSdk == null) {
        return null;
      }
      return new AndroidConfiguration(buildOptions.get(Options.class), androidSdk);
    }

    @Override
    public Class<? extends Fragment> creates() {
      return AndroidConfiguration.class;
    }

    @Override
    public ImmutableSet<Class<? extends FragmentOptions>> requiredOptions() {
      return ImmutableSet.<Class<? extends FragmentOptions>>of(Options.class);
    }
  }

  private final Label sdk;
  private final StrictDepsMode strictDeps;
  private final boolean legacyNativeSupport;
  private final String cpu;
  private final boolean incrementalNativeLibs;
  private final boolean fatApk;
  private final ConfigurationDistinguisher configurationDistinguisher;
  private final boolean useJackForDexing;
  private final boolean jackSanityChecks;
  private final ImmutableSet<AndroidBinaryType> incrementalDexingBinaries;
  private final ImmutableList<String> targetDexoptsThatPreventIncrementalDexing;
  private final boolean allowAndroidLibraryDepsWithoutSrcs;
  private final boolean useAndroidResourceShrinking;

  AndroidConfiguration(Options options, Label androidSdk) {
    this.sdk = androidSdk;
    this.incrementalNativeLibs = options.incrementalNativeLibs;
    this.strictDeps = options.strictDeps;
    this.legacyNativeSupport = options.legacyNativeSupport;
    this.cpu = options.cpu;
    this.fatApk = !options.fatApkCpus.isEmpty();
    this.configurationDistinguisher = options.configurationDistinguisher;
    this.useJackForDexing = options.useJackForDexing;
    this.jackSanityChecks = options.jackSanityChecks;
    if (options.incrementalDexing) {
      this.incrementalDexingBinaries = ImmutableSet.copyOf(options.incrementalDexingBinaries);
    } else {
      this.incrementalDexingBinaries = options.dexingStrategy.binaryTypes;
    }
    this.targetDexoptsThatPreventIncrementalDexing =
        ImmutableList.copyOf(options.nonIncrementalPerTargetDexopts);
    this.allowAndroidLibraryDepsWithoutSrcs = options.allowAndroidLibraryDepsWithoutSrcs;
    this.useAndroidResourceShrinking = options.useAndroidResourceShrinking;
  }

  public String getCpu() {
    return cpu;
  }

  public Label getSdk() {
    return sdk;
  }

  public boolean getLegacyNativeSupport() {
    return legacyNativeSupport;
  }

  public StrictDepsMode getStrictDeps() {
    return strictDeps;
  }

  public boolean isFatApk() {
    return fatApk;
  }

  /**
   * Returns true if Jack should be used in place of javac/dx for Android compilation.
   */
  public boolean isJackUsedForDexing() {
    return useJackForDexing;
  }

  /**
   * Returns true if Jack sanity checks should be enabled. Only relevant if isJackUsedForDexing()
   * also returns true.
   */
  public boolean isJackSanityChecked() {
    return jackSanityChecks;
  }

  public boolean useIncrementalNativeLibs() {
    return incrementalNativeLibs;
  }

  /**
   * Returns when to use incremental dexing using {@link DexArchiveProvider}.  Note this is disabled
   * if {@link #isJackUsedForDexing()}.
   */
  public ImmutableSet<AndroidBinaryType> getIncrementalDexingBinaries() {
    return isJackUsedForDexing() ? ImmutableSet.<AndroidBinaryType>of() : incrementalDexingBinaries;
  }

  /**
   * Regardless of {@link #getIncrementalDexing}, incremental dexing must not be used for binaries
   * that list any of these flags in their {@code dexopts} attribute.
   */
  public ImmutableList<String> getTargetDexoptsThatPreventIncrementalDexing() {
    return targetDexoptsThatPreventIncrementalDexing;
  }

  public boolean allowSrcsLessAndroidLibraryDeps() {
    return allowAndroidLibraryDepsWithoutSrcs;
  }

  public boolean useAndroidResourceShrinking() {
    return useAndroidResourceShrinking;
  }

  @Override
  public void addGlobalMakeVariables(ImmutableMap.Builder<String, String> globalMakeEnvBuilder) {
    globalMakeEnvBuilder.put("ANDROID_CPU", cpu);
  }

  @Override
  public String getOutputDirectoryName() {
    return configurationDistinguisher.suffix;
  }
}
