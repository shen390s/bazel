package(default_visibility = ["//visibility:private"])

genrule(
    name = "langtools-copy",
    testonly = 1,
    srcs = ["//third_party/java/jdk/langtools:javac_jar"],
    outs = ["langtools.jar"],
    cmd = "cp $< $@",
)

config_setting(
    name = "darwin",
    values = {"host_cpu": "darwin"},
)

filegroup(
    name = "objc-deps",
    testonly = 1,
    srcs = select({
        ":darwin": [
            "//src/objc_tools/bundlemerge:bundlemerge_deploy.jar",
            "//src/objc_tools/plmerge:plmerge_deploy.jar",
            "//src/objc_tools/xcodegen:xcodegen_deploy.jar",
            "//src/tools/xcode/actoolwrapper",
            "//src/tools/xcode/environment:environment_plist",
            "//src/tools/xcode/ibtoolwrapper",
            "//src/tools/xcode/libtool",
            "//src/tools/xcode/momcwrapper",
            "//src/tools/xcode/realpath",
            "//src/tools/xcode/stdredirect:StdRedirect.dylib",
            "//src/tools/xcode/swiftstdlibtoolwrapper",
            "//src/tools/xcode/xcrunwrapper",
            "//third_party/iossim",
        ],
        "//conditions:default": [],
    }),
)

filegroup(
    name = "test-deps",
    testonly = 1,
    srcs = [
        "bazel_sandboxing_test_utils.sh",
        "remote_helpers.sh",
        "test-setup.sh",
        "testenv.sh",
        "testing_server.py",
        ":langtools-copy",
        "//examples:srcs",
        "//external:bootclasspath",
        "//external:extdir",
        "//external:jdk-default",
        "//src:bazel",
        "//src/java_tools/buildjar:JavaBuilder_deploy.jar",
        "//src/java_tools/buildjar/java/com/google/devtools/build/buildjar/genclass:GenClass_deploy.jar",
        "//src/java_tools/junitrunner/java/com/google/testing/junit/runner:Runner_deploy.jar",
        "//src/java_tools/singlejar:SingleJar_deploy.jar",
        "//src/main/tools:namespace-sandbox",
        "//src/main/tools:process-wrapper",
        "//src/test/shell:bashunit",
        "//third_party:srcs",
        "//third_party/ijar",
        "//third_party/java/jdk/langtools:srcs",
        "//tools:srcs",
    ],
    visibility = [
        "//src/test/shell:__subpackages__",
    ],
)

sh_test(
    name = "bazel_example_test",
    size = "large",
    srcs = ["bazel_example_test.sh"],
    data = [
        ":objc-deps",
        ":test-deps",
    ],
    shard_count = 3,
)

sh_test(
    name = "bazel_windows_cpp_test",
    size = "large",
    srcs = ["bazel_windows_cpp_test.sh"],
    data = [
        ":test-deps",
    ],
)

sh_test(
    name = "bazel_apple_test",
    srcs = ["bazel_apple_test.sh"],
    data = [
        ":objc-deps",
        ":test-deps",
        "//:workspace-file",
        "//tools/build_defs/apple/test:srcs",
    ],
    shard_count = 3,
)

sh_test(
    name = "bazel_rules_test",
    size = "large",
    srcs = ["bazel_rules_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "bazel_test_test",
    srcs = ["bazel_test_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "bazel_localtest_test",
    srcs = ["bazel_localtest_test.sh"],
    data = [":test-deps"],
    tags = ["local"],
)

sh_test(
    name = "bazel_objc_test",
    srcs = ["bazel_objc_test.sh"],
    data = [
        ":objc-deps",
        ":test-deps",
    ],
)

sh_test(
    name = "bazel_execute_testlog",
    srcs = ["bazel_execute_testlog.sh"],
    data = [":test-deps"],
)

# TODO(bazel-team): zip is non-deterministic because of file timestamp,
# we should use a custom zip version (SingleJar?).
genrule(
    name = "doc-srcs",
    testonly = 1,
    srcs = [
        "//src/java_tools/singlejar:srcs",
        "//src/main/protobuf:srcs",
        "//src/main/java/com/google/devtools/build/lib:srcs",
        "//src/main/java/com/google/devtools/build/lib/actions:srcs",
        "//src/main/java/com/google/devtools/build/lib/sandbox:srcs",
        "//src/main/java/com/google/devtools/build/lib/rules/genquery:srcs",
        "//src/main/java/com/google/devtools/build/lib/worker:srcs",
        "//src/main/java/com/google/devtools/build/lib/standalone:srcs",
        "//src/tools/xcode-common:srcs",
        "//third_party:srcs",
    ],
    outs = ["doc-srcs.zip"],
    cmd = "echo $(SRCS) | tr ' ' '\n' | zip -q@ $@",
)

sh_test(
    name = "bazel_docgen_test",
    size = "large",
    srcs = ["bazel_docgen_test.sh"],
    data = ["//src/main/java/com/google/devtools/build/lib:gen_buildencyclopedia"],
)

sh_test(
    name = "external_correctness_test",
    size = "large",
    srcs = ["external_correctness_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "external_integration_test",
    size = "large",
    srcs = ["external_integration_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "git_repository_test",
    size = "large",
    srcs = ["git_repository_test.sh"],
    data = [
        ":test-deps",
        "//src/test/shell/bazel/testdata:git-repos",
    ],
)

sh_test(
    name = "local_repository_test",
    size = "large",
    srcs = ["local_repository_test.sh"],
    data = [":test-deps"],
    shard_count = 3,
)

sh_test(
    name = "local_repository_test_jdk8",
    size = "medium",
    srcs = ["local_repository_test_jdk8.sh"],
    data = [":test-deps"],
    tags = ["jdk8"],
)

sh_test(
    name = "external_skylark_load_test",
    size = "large",
    srcs = ["external_skylark_load_test.sh"],
    data = [":test-deps"],
    shard_count = 6,
)

sh_test(
    name = "skylark_repository_test",
    size = "large",
    srcs = ["skylark_repository_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "runfiles_test",
    size = "medium",
    srcs = ["runfiles_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "empty_package_test",
    srcs = ["empty_package.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "location_test",
    srcs = ["location_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "maven_test",
    size = "medium",
    srcs = ["maven_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "generate_workspace_test",
    size = "large",
    srcs = ["generate_workspace_test.sh"],
    data = [
        ":test-deps",
        "//src/tools/generate_workspace",
    ],
)

sh_test(
    name = "workspace_test",
    srcs = ["workspace_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "process_wrapper_test",
    size = "small",
    srcs = ["process-wrapper_test.sh"],
    data = [
        "test-setup.sh",
        "testenv.sh",
        "//src/main/tools:process-wrapper",
        "//src/test/shell:bashunit",
    ],
)

sh_test(
    name = "namespace_runner_test",
    size = "large",
    srcs = ["namespace-runner_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "cc_inc_library_test",
    size = "medium",
    srcs = ["cc_inc_library_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "bazel_sandboxing_test",
    size = "large",
    srcs = ["bazel_sandboxing_test.sh"],
    data = [":test-deps"],
    tags = ["local"],
)

sh_test(
    name = "bazel_sandboxing_cpp_test",
    srcs = ["bazel_sandboxing_cpp_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "bazel_worker_test",
    size = "large",
    srcs = ["bazel_worker_test.sh"],
    data = [
        ":test-deps",
        "//src/test/java/com/google/devtools/build/lib:ExampleWorker_deploy.jar",
    ],
)

sh_test(
    name = "bazel_workspace_status_test",
    size = "large",
    srcs = ["bazel_workspace_status_test.sh"],
    data = [":test-deps"],
)

sh_test(
    name = "bound_targets_test",
    size = "large",
    srcs = ["bound_targets_test.sh"],
    data = [
        ":objc-deps",
        ":test-deps",
        "//:workspace-file",
    ],
)

sh_test(
    name = "remote_execution_test",
    size = "large",
    srcs = ["remote_execution_test.sh"],
    data = [
        ":test-deps",
        "//src/tools/remote_worker",
    ],
)

sh_test(
    name = "client_test",
    size = "medium",
    srcs = ["client_test.sh"],
    data = [":test-deps"],
)

test_suite(
    name = "all_tests",
    visibility = ["//visibility:public"],
)
