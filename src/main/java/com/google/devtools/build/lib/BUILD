# Description:
#   Main Java code for Bazel
package(
    default_visibility = ["//src:__subpackages__"],
)

java_library(
    name = "shell",
    srcs = glob(["shell/*.java"]),
    deps = [
        "//third_party:guava",
    ],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]) + [
        "//src/main/java/com/google/devtools/build/docgen:srcs",
        "//src/main/java/com/google/devtools/build/lib/actions:srcs",
        "//src/main/java/com/google/devtools/build/lib/bazel/dash:srcs",
        "//src/main/java/com/google/devtools/build/lib/bazel/repository/downloader:srcs",
        "//src/main/java/com/google/devtools/build/lib/query2:srcs",
        "//src/main/java/com/google/devtools/build/lib/remote:srcs",
        "//src/main/java/com/google/devtools/build/lib/rules/apple:srcs",
        "//src/main/java/com/google/devtools/build/lib/rules/apple/cpp:srcs",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp:srcs",
        "//src/main/java/com/google/devtools/build/lib/rules/genquery:srcs",
        "//src/main/java/com/google/devtools/build/lib/rules/objc:srcs",
        "//src/main/java/com/google/devtools/build/lib/sandbox:srcs",
        "//src/main/java/com/google/devtools/build/lib/ssd:srcs",
        "//src/main/java/com/google/devtools/build/lib/standalone:srcs",
        "//src/main/java/com/google/devtools/build/lib/worker:srcs",
        "//src/main/java/com/google/devtools/build/skyframe:srcs",
        "//src/main/java/com/google/devtools/common/options:srcs",
    ],
    visibility = [
        "//src:__pkg__",
        "//src/test/shell/bazel:__pkg__",
    ],
)

# This package provides a general-purpose directed graph utility class with
# implementations of numerous textbook graph algorithms and code to read and
# write AT&T GraphViz files.

java_library(
    name = "graph",
    srcs = glob(["graph/*.java"]),
    deps = [
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

#   Java interfaces to various native UNIX system services.
java_library(
    name = "unix",
    srcs = glob([
        "unix/*.java",
    ]) + ["UnixJniLoader.java"],
    resources = select({
        # todo(dslomov): Get rid of this whole library on Windows.
        "//src:windows": [],
        "//conditions:default": ["//src/main/native:libunix.so"],
    }),
    deps = [
        ":os_util",
        ":shell",
        "//third_party:guava",
    ],
)

java_library(
    name = "windows",
    srcs = glob(["windows/*.java"]),
)

# Library of concurrency utilities.
java_library(
    name = "concurrent",
    srcs = glob(["concurrent/*.java"]),
    deps = [
        ":preconditions",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

# Library of collection utilities.
java_library(
    name = "collect",
    srcs = glob([
        "collect/*.java",
        "collect/nestedset/*.java",
    ]),
    deps = [
        ":preconditions",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

java_library(
    name = "flags",
    srcs = glob([
        "flags/*.java",
    ]),
    deps = [
        ":io",
        ":preconditions",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:invocation_policy_java_proto",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

# Virtual file system; do not use externally!
java_library(
    name = "vfs",
    srcs = glob([
        "profiler/*.java",
        "vfs/*.java",
    ]),
    deps = [
        ":base-util",
        ":clock",
        ":concurrent",
        ":os_util",
        ":preconditions",
        ":unix",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

# Profiler chart library.
java_library(
    name = "profiler-output",
    srcs = glob([
        "profiler/chart/*.java",
        "profiler/output/*.java",
        "profiler/statistics/*.java",
    ]),
    deps = [
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

# In-memory virtual file system.
java_library(
    name = "inmemoryfs",
    srcs = glob(["vfs/inmemoryfs/*.java"]),
    deps = [
        ":clock",
        ":concurrent",
        ":preconditions",
        ":unix",
        ":vfs",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

# Clock classes.
java_library(
    name = "clock",
    srcs = [
        "util/BlazeClock.java",
        "util/Clock.java",
        "util/JavaClock.java",
    ],
    deps = [
        ":concurrent",
    ],
)

# String canonicalizer.
java_library(
    name = "base-util",
    srcs = [
        "util/StringCanonicalizer.java",
        "util/StringTrie.java",
        "util/VarInt.java",
    ],
    deps = [
        ":preconditions",
        "//third_party:guava",
    ],
)

# Varargs-avoiding overloads of Preconditions functions.
java_library(
    name = "preconditions",
    srcs = ["util/Preconditions.java"],
    deps = [
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

# I/O utilities.
java_library(
    name = "io",
    srcs = glob(["util/io/*.java"]),
    deps = [
        ":clock",
        ":concurrent",
        ":vfs",
        "//third_party:guava",
    ],
)

# General utilities.
java_library(
    name = "os_util",
    srcs = ["util/OS.java"],
)

java_library(
    name = "util",
    srcs = glob(
        ["util/*.java"],
        exclude = [
            "util/BlazeClock.java",
            "util/Clock.java",
            "util/ExitCode.java",
            "util/JavaClock.java",
            "util/OS.java",
            "util/Preconditions.java",
            "util/StringCanonicalizer.java",
            "util/StringTrie.java",
            "util/VarInt.java",
        ],
    ),
    exports = [
        ":base-util",
        ":clock",
        ":collect",
        ":exitcode-external",
        ":os_util",
        ":preconditions",
    ],
    deps = [
        ":base-util",
        ":collect",
        ":concurrent",
        ":exitcode-external",
        ":os_util",
        ":preconditions",
        ":shell",
        ":unix",
        ":vfs",
        "//src/main/java/com/google/devtools/common/options",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

java_library(
    name = "exitcode-external",
    srcs = [
        "util/ExitCode.java",
    ],
    deps = [
        "//third_party:guava",
    ],
)

# Event reporting infrastructure.
java_library(
    name = "events",
    srcs = glob(["events/*.java"]),
    deps = [
        ":concurrent",
        ":io",
        ":preconditions",
        ":vfs",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

########################################################################
#
# The "foundation" library (concurrent, events, util, vfs, inmemoryfs, options)
# Filesystem abstraction, event reporting, options parsing, I/O routines.
#

# These packages apparently depend on :foundation. I have no idea why, but to
# avoid breaking them, I allow them to depend on it.
java_library(
    name = "foundation",
    exports = [
        ":concurrent",
        ":events",
        # This should be removed at some point. Production code shouldn't use this.
        ":inmemoryfs",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/common/options",
    ],
)

java_library(
    name = "skylarkinterface",
    srcs = glob([
        "skylarkinterface/*.java",
    ]),
    deps = [
        "//third_party:jsr305",
    ],
)

########################################################################
#
# The "cmdline" library: Label validation and target pattern
# parsing. Both the label syntax and the target pattern syntax are a
# public interface, and all changes have to be made in a
# backwards-compatible manner. This library is intentionally
# standalone so it can be used outside without pulling in any internal
# code.
#

java_library(
    name = "cmdline",
    srcs = glob(["cmdline/*.java"]),
    deps = [
        ":base-util",
        ":concurrent",
        ":skylarkinterface",
        ":util",
        ":vfs",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

java_library(
    name = "syntax",
    srcs = glob([
        "syntax/**/*.java",
    ]),
    deps = [
        ":cmdline",
        ":collect",
        ":concurrent",
        ":events",
        ":skylarkinterface",
        ":util",
        ":vfs",
        "//third_party:asm",
        "//third_party:asm-commons",
        "//third_party:asm-util",
        "//third_party:auto_value",
        "//third_party:bytebuddy",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

########################################################################
#
# The "packages" library: syntax, packages, pkgcache.
# BUILD file scanning, parsing and evaluation; package loading and caching.
#

# IMPORTANT:  NOT A PUBLIC INTERFACE.
#
# DO NOT ADD A DEPENDENCY ON THIS TARGET, or any other in this
# package, without first talking to us.  Here are two good
# reasons:
#
# 1.  FRAGILITY. This is not a stable API, it's an internal interface.
#     It changes from time to time, and your client *will* be broken.
#
# 2.  CORRECTNESS.  The BUILD file parser changes rapidly.  If
#     your application depends upon it, your application will start to
#     bit-rot almost immediately.

java_library(
    name = "packages-internal",
    srcs = glob([
        "packages/*.java",
        "pkgcache/*.java",
    ]),
    exports = [
        ":cmdline",
        ":syntax",
    ],
    deps = [
        ":cmdline",
        ":collect",
        ":concurrent",
        ":events",
        ":skylarkinterface",
        ":syntax",
        ":transitive-info-provider",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:build_java_proto",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "packages",
    exports = [
        ":foundation",
        ":packages-internal",
    ],
)

java_library(
    name = "java-toolchain-parser",
    srcs = [
        "rules/java/JavaToolchainData.java",
        "rules/java/JavaToolchainDataParser.java",
    ],
    deps = [
        ":concurrent",
        ":preconditions",
        ":shell",
        "//src/main/protobuf:build_java_proto",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "execution-requirements",
    srcs = ["analysis/actions/ExecutionRequirements.java"],
    visibility = [
        "//src/main/java/com/google/devtools/build/lib/rules:__subpackages__",
    ],
)

java_library(
    name = "transitive-info-provider",
    srcs = ["analysis/TransitiveInfoProvider.java"],
)

java_library(
    name = "build-base",
    srcs = glob(
        [
            "analysis/*.java",
            "analysis/actions/*.java",
            "analysis/buildinfo/*.java",
            "analysis/config/*.java",
            "analysis/constraints/*.java",
            "exec/*.java",
            "rules/*.java",
            "rules/extra/*.java",
            "rules/filegroup/*.java",
            "rules/fileset/*.java",
            "rules/test/*.java",
            "rules/repository/*.java",
            "skyframe/*.java",
        ],
        exclude = [
            "analysis/BuildInfo.java",
            "analysis/TransitiveInfoProvider.java",
        ],
    ) + [
        "runtime/BlazeServerStartupOptions.java",
    ],
    exports = [
        ":transitive-info-provider",
    ],
    deps = [
        ":base-util",
        ":cmdline",
        ":collect",
        ":concurrent",
        ":events",
        ":exitcode-external",
        ":flags",
        ":graph",
        ":io",
        ":os_util",
        ":packages-internal",
        ":shell",
        ":skylarkinterface",
        ":transitive-info-provider",
        ":unix",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/skyframe",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:extra_actions_base_java_proto",
        "//src/main/protobuf:invocation_policy_java_proto",
        "//src/main/protobuf:test_status_java_proto",
        "//third_party:auto_value",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "bazel-rules",
    srcs = glob(
        [
            "bazel/rules/**/*.java",
        ],
        exclude = [
            "bazel/rules/workspace/*.java",
        ],
    ),
    resources = glob(
        [
            "bazel/rules/**/*.txt",
            "bazel/rules/**/*.WORKSPACE",
        ],
    ),
    deps = [
        ":android-rules",
        ":bazel",
        ":bazel-repository",
        ":build-base",
        ":build-info",
        ":collect",
        ":concurrent",
        ":events",
        ":ideinfo",
        ":java-rules",
        ":packages-internal",
        ":proto-rules",
        ":python-rules",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/query2:query-output",
        "//src/main/java/com/google/devtools/build/lib/rules/apple",
        "//src/main/java/com/google/devtools/build/lib/rules/apple/cpp",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/java/com/google/devtools/build/lib/rules/genquery",
        "//src/main/java/com/google/devtools/build/lib/rules/objc",
        "//src/main/java/com/google/devtools/build/skyframe",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:crosstool_config_java_proto",
        "//third_party:guava",
        "//third_party:joda_time",
        "//third_party:jsr305",
    ],
)

java_library(
    name = "bazel-main",
    srcs = glob(["bazel/*.java"]),
    resources = [
        "bazel/rules/java/java_stub_template.txt",
        "bazel/rules/python/stub_template.txt",
    ],
    deps = [
        ":bazel",
        ":bazel-commands",
        ":bazel-repository",
        ":bazel-rules",
        ":build-base",
        ":build-info",
        ":clock",
        ":events",
        ":io",
        ":packages-internal",
        ":shell",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/bazel/dash",
        "//src/main/java/com/google/devtools/build/lib/remote",
        "//src/main/java/com/google/devtools/build/lib/sandbox",
        "//src/main/java/com/google/devtools/build/lib/ssd",
        "//src/main/java/com/google/devtools/build/lib/standalone",
        "//src/main/java/com/google/devtools/build/lib/worker",
        "//src/main/java/com/google/devtools/build/skyframe",
        "//src/main/java/com/google/devtools/common/options",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

java_library(
    name = "bazel-commands",
    srcs = glob(["bazel/commands/*.java"]),
    resources = ["bazel/commands/fetch.txt"],
    deps = [
        ":events",
        ":exitcode-external",
        ":java-rules",
        ":packages-internal",
        ":runtime",
        ":util",
        "//src/main/java/com/google/devtools/build/lib/query2",
        "//src/main/java/com/google/devtools/build/lib/query2:query-engine",
        "//src/main/java/com/google/devtools/common/options",
        "//third_party:guava",
    ],
)

java_library(
    name = "bazel-repository",
    srcs = glob(
        [
            "bazel/repository/**/*.java",
            "bazel/rules/workspace/*.java",
        ],
        exclude = ["bazel/repository/MavenConnector.java"],
    ),
    runtime_deps = [
        "//third_party:aether",
        "//third_party:apache_commons_codec",
        "//third_party:apache_commons_logging",
        "//third_party:apache_httpclient",
        "//third_party:apache_httpcore",
        "//third_party:jsch",
        "//third_party:maven",
        "//third_party:maven_model",
        "//third_party:plexus_interpolation",
        "//third_party:plexus_utils",
        "//third_party:slf4j",
    ],
    deps = [
        ":concurrent",
        ":events",
        ":maven-connector",
        ":runtime",
        ":util",
        "//src/java_tools/singlejar/java/com/google/devtools/build/zip",
        "//src/main/java/com/google/devtools/build/lib:build-base",
        "//src/main/java/com/google/devtools/build/lib:packages-internal",
        "//src/main/java/com/google/devtools/build/lib:shell",
        "//src/main/java/com/google/devtools/build/lib:skylarkinterface",
        "//src/main/java/com/google/devtools/build/lib:vfs",
        "//src/main/java/com/google/devtools/build/lib/bazel/repository/downloader",
        "//src/main/java/com/google/devtools/build/skyframe",
        "//third_party:aether",
        "//third_party:apache_commons_compress",
        "//third_party:guava",
        "//third_party:jgit",
        "//third_party:jsr305",
        "//third_party:maven",
        "//third_party:maven_model",
        "//third_party:plexus_component_annotations",
        "//third_party:xz",
    ],
)

java_library(
    name = "maven-connector",
    srcs = ["bazel/repository/MavenConnector.java"],
    deps = [
        "//third_party:aether",
        "//third_party:maven_model",
    ],
)

# Rules
java_library(
    name = "nativedeps-rules",
    srcs = glob(
        ["rules/nativedeps/*.java"],
    ),
    deps = [
        ":build-base",
        ":collect",
        ":concurrent",
        ":packages-internal",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "proto-rules",
    srcs = glob(
        ["rules/proto/*.java"],
    ),
    deps = [
        ":build-base",
        ":collect",
        ":concurrent",
        ":packages-internal",
        ":skylarkinterface",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/common/options",
        "//third_party:auto_value",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "java-rules",
    srcs = glob(
        ["rules/java/*.java"],
        exclude = [
            "rules/java/JavaImplicitAttributes.java",
            "rules/java/JavaToolchainDataParser.java",
        ],
    ),
    deps = [
        ":build-base",
        ":collect",
        ":concurrent",
        ":events",
        ":java-implicit-attributes",
        ":packages-internal",
        ":shell",
        ":skylarkinterface",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:extra_actions_base_java_proto",
        "//third_party:guava",
        "//third_party:joda_time",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "java-implicit-attributes",
    srcs = ["rules/java/JavaImplicitAttributes.java"],
)

java_library(
    name = "android-rules",
    srcs = glob(
        ["rules/android/*.java"],
    ),
    deps = [
        ":build-base",
        ":collect",
        ":concurrent",
        ":events",
        ":java-rules",
        ":nativedeps-rules",
        ":packages-internal",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib:skylarkinterface",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:android_deploy_info_java_proto",
        "//src/main/protobuf:apk_manifest_java_proto",
        "//third_party:android_sdklib",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "python-rules",
    srcs = glob(
        ["rules/python/*.java"],
    ),
    deps = [
        ":build-base",
        ":collect",
        ":concurrent",
        ":packages-internal",
        ":skylarkinterface",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:crosstool_config_java_proto",
        "//src/main/protobuf:extra_actions_base_java_proto",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "ideinfo",
    srcs = glob(
        ["ideinfo/*.java"],
    ),
    deps = [
        ":android-rules",
        ":build-base",
        ":collect",
        ":concurrent",
        ":java-rules",
        ":packages-internal",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/protobuf:android_studio_ide_info_java_proto",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "shared-base-rules",
    exports = [
        ":android-rules",
        ":java-rules",
        ":nativedeps-rules",
        ":proto-rules",
        ":python-rules",
    ],
)

java_library(
    name = "runtime",
    srcs = glob(
        [
            "runtime/*.java",
            "server/**/*.java",
            "runtime/commands/*.java",
            "buildtool/*.java",
            "buildtool/buildevent/*.java",
        ],
    ),
    deps = [
        ":build-base",
        ":clock",
        ":cmdline",
        ":collect",
        ":concurrent",
        ":events",
        ":exitcode-external",
        ":flags",
        ":io",
        ":packages-internal",
        ":profiler-output",
        ":shared-base-rules",
        ":shell",
        ":unix",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/docgen:docgen_javalib",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/query2",
        "//src/main/java/com/google/devtools/build/lib/query2:query-engine",
        "//src/main/java/com/google/devtools/build/lib/query2:query-output",
        "//src/main/java/com/google/devtools/build/skyframe",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:build_java_proto",
        "//src/main/protobuf:command_server_java_proto",
        "//src/main/protobuf:invocation_policy_java_proto",
        "//src/main/protobuf:test_status_java_proto",
        "//third_party:guava",
        "//third_party:joda_time",
        "//third_party:jsr305",
        "//third_party/grpc:grpc-jar",
        "//third_party/protobuf",
    ],
)

java_library(
    name = "bazel",
    resources = glob(["runtime/commands/*.txt"]),
    exports = [":runtime"],
)

java_library(
    name = "all-build-rules",
    exports = [
        ":build-base",
        ":concurrent",
        ":graph",
        ":packages-internal",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/actions",
        "//src/main/java/com/google/devtools/build/lib/rules/objc",
        "//src/main/java/com/google/devtools/common/options",
        "//third_party:guava",
    ],
)

java_library(
    name = "build",
    exports = [
        ":all-build-rules",
        ":build-base",
        ":foundation",
        ":packages",
        ":query2",
        "//src/main/java/com/google/devtools/build/lib/actions",
    ],
)

########################################################################
#
# The Blaze query language, revision 2: query2.
#

java_library(
    name = "query2",
    exports = [
        ":concurrent",
        ":events",
        ":foundation",
        ":graph",
        ":packages",
        ":util",
        ":vfs",
        "//src/main/java/com/google/devtools/build/lib/query2",
        "//src/main/java/com/google/devtools/common/options",
        "//src/main/protobuf:build_java_proto",
        "//third_party:guava",
    ],
)

java_binary(
    name = "bazel/BazelServer",
    javacopts = [
        "-Xlint:all",
        "-Xlint:-path",
    ],
    main_class = "com.google.devtools.build.lib.bazel.BazelMain",
    runtime_deps = [
        ":bazel-main",
    ],
)

filegroup(
    name = "docs_embedded_in_sources",
    srcs = glob(["**/*.java"]),
)

genrule(
    name = "gen_buildencyclopedia",
    srcs = [":docs_embedded_in_sources"],
    outs = ["build-encyclopedia.zip"],
    cmd = (
        "mkdir -p $(@D)/be && " +
        "$(location //src/main/java/com/google/devtools/build/docgen:docgen_bin)" +
        " $$PWD/src/main/java/com/google/devtools/build/lib " +
        "com.google.devtools.build.lib.bazel.rules.BazelRuleClassProvider" +
        " $(@D)/be &&" +
        "zip -qj $@ $(@D)/be/*"
    ),
    tools = [
        "//src/main/java/com/google/devtools/build/docgen:docgen_bin",
        "//src/main/java/com/google/devtools/build/docgen:docgen_javalib",
    ],
    visibility = [
        "//site:__pkg__",
        "//src/test/shell/bazel:__pkg__",
    ],
)

genrule(
    name = "gen_command-line-reference",
    srcs = [
        "//site:command-line-reference-prefix.html",
        "//site:command-line-reference-suffix.html",
    ],
    outs = ["command-line-reference.html"],
    cmd = (
        "cat $(location //site:command-line-reference-prefix.html) > $@ && " +
        "TMP=`mktemp -d /tmp/tmp.XXXXXXXXXX` && " +
        "mkdir $${TMP}/_embedded_binaries/ && " +
        "$(location :bazel/BazelServer) " +
        "--jvm_flag=-Dio.bazel.UnixFileSystem=0 --batch " +
        "--install_base=$${TMP} --output_base=$${TMP}/output/ " +
        "help everything-as-html >> $@ 2>/dev/null && " +
        "cat $(location //site:command-line-reference-suffix.html) >> $@"
    ),
    tools = [
        ":bazel/BazelServer",
    ],
    visibility = [
        "//site:__pkg__",
    ],
)

# The skylark repository classes are passed as parameter of the Skylark documentation generator.
SKYLARK_REPOSITORY_CLASSES = [
    "com.google.devtools.build.lib.bazel.repository.skylark.SkylarkExecutionResult",
    "com.google.devtools.build.lib.bazel.repository.skylark.SkylarkOS",
    "com.google.devtools.build.lib.bazel.repository.skylark.SkylarkPath",
    "com.google.devtools.build.lib.bazel.repository.skylark.SkylarkRepositoryContext",
    "com.google.devtools.build.lib.bazel.repository.skylark.SkylarkRepositoryModule",
]

genrule(
    name = "gen_skylarklibrary",
    outs = ["skylark-library.zip"],
    cmd = "mkdir -p $(@D)/skylark-lib &&" +
          "$(location //src/main/java/com/google/devtools/build/docgen:skydoc_bin) $(@D)/skylark-lib " +
          " ".join(SKYLARK_REPOSITORY_CLASSES) +
          " && zip -qj $@ $(@D)/skylark-lib/*",
    tools = ["//src/main/java/com/google/devtools/build/docgen:skydoc_bin"],
    visibility = ["//site:__pkg__"],
)

# Bootstrapping SingleJar using Skylark rules
#
load("//tools/build_rules:java_rules_skylark.bzl", "bootstrap_java_library")

bootstrap_java_library(
    name = "shell-skylark",
    srcs = glob(["shell/*.java"]),
    jars = ["//third_party:guava-jars"],
)

########################################################################
#
# Miscellaneous targets
#

# A library for just the BuildInfo class, so it can be used by the external
# SingleJar implementation.

java_library(
    name = "build-info",
    srcs = [
        "analysis/BuildInfo.java",
    ],
    deps = ["//third_party:joda_time"],
)
