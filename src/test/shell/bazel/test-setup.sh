#!/bin/bash
#
# Copyright 2015 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Setup bazel for integration tests
#

# Load test environment
source $(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/testenv.sh \
  || { echo "testenv.sh not found!" >&2; exit 1; }

# OS X has a limit in the pipe length, so force the root to a shorter one
bazel_root="${TEST_TMPDIR}/root"
mkdir -p "${bazel_root}"

bazel_javabase="${jdk_dir}"

echo "bazel binary is at $bazel"

# Here we unset variable that were set by the invoking Blaze instance
unset JAVA_RUNFILES

function is_windows() {
  # On windows, the shell test actually running on msys
  if [ "${PLATFORM}" == "msys_nt-6.1" ]; then
    true
  else
    false
  fi
}

function setup_bazelrc() {
  # enable batch mode when running on windows
  if is_windows; then
    BATCH_MODE="startup --batch"
  fi
  cat >$TEST_TMPDIR/bazelrc <<EOF
${BATCH_MODE:-}
startup --output_user_root=${bazel_root}
startup --host_javabase=${bazel_javabase}
build -j 8
${EXTRA_BAZELRC:-}
EOF
}

function bazel() {
  ${bazel} --bazelrc=$TEST_TMPDIR/bazelrc "$@"
}

function setup_android_support() {
  ANDROID_NDK=$PWD/android_ndk
  ANDROID_SDK=$PWD/android_sdk

  # TODO(bazel-team): This hard-codes the name of the Android repository in
  # the WORKSPACE file of Bazel. Change this once external repositories have
  # their own defined names under which they are mounted.
  NDK_SRCDIR=$BAZEL_RUNFILES/external/androidndk/ndk
  SDK_SRCDIR=$BAZEL_RUNFILES/external/androidsdk

  mkdir -p $ANDROID_NDK
  mkdir -p $ANDROID_SDK

  for i in $NDK_SRCDIR/*; do
    if [[ "$(basename $i)" != "BUILD" ]]; then
      ln -s "$i" "$ANDROID_NDK/$(basename $i)"
    fi
  done

  for i in $SDK_SRCDIR/*; do
    if [[ "$(basename $i)" != "BUILD" ]]; then
      ln -s "$i" "$ANDROID_SDK/$(basename $i)"
    fi
  done


  local ANDROID_SDK_API_LEVEL=$(ls $SDK_SRCDIR/platforms | cut -d '-' -f 2 | sort -n | tail -1)
  local ANDROID_NDK_API_LEVEL=$(ls $NDK_SRCDIR/platforms | cut -d '-' -f 2 | sort -n | tail -1)
  local ANDROID_SDK_TOOLS_VERSION=$(ls $SDK_SRCDIR/build-tools | sort -n | tail -1)
  cat >> WORKSPACE <<EOF
android_ndk_repository(
    name = "androidndk",
    path = "$ANDROID_NDK",
    api_level = $ANDROID_NDK_API_LEVEL,
)

android_sdk_repository(
    name = "androidsdk",
    path = "$ANDROID_SDK",
    build_tools_version = "$ANDROID_SDK_TOOLS_VERSION",
    api_level = $ANDROID_SDK_API_LEVEL,
)
EOF
}

function setup_javatest_common() {
  # TODO(bazel-team): we should use remote repositories.
  mkdir -p third_party
  if [ ! -f third_party/BUILD ]; then
    cat <<EOF >third_party/BUILD
package(default_visibility = ["//visibility:public"])
EOF
  fi

  [ -e third_party/junit.jar ] || ln -s ${junit_jar} third_party/junit.jar
  [ -e third_party/hamcrest.jar ] \
    || ln -s ${hamcrest_jar} third_party/hamcrest.jar
}

function setup_javatest_support() {
  setup_javatest_common
  cat <<EOF >>third_party/BUILD
java_import(
    name = "junit4",
    jars = [
        "junit.jar",
        "hamcrest.jar",
    ],
)
EOF
}

function setup_skylark_javatest_support() {
  setup_javatest_common
  cat <<EOF >>third_party/BUILD
filegroup(
    name = "junit4-jars",
    srcs = [
        "junit.jar",
        "hamcrest.jar",
    ],
)
EOF
}

function setup_iossim() {
  mkdir -p third_party/iossim
  ln -sv ${iossim_path} third_party/iossim/iossim

  cat <<EOF >>third_party/iossim/BUILD
licenses(["unencumbered"])
package(default_visibility = ["//visibility:public"])

exports_files(["iossim"])
EOF
}

# Sets up Objective-C tools. Mac only.
function setup_objc_test_support() {
  IOS_SDK_VERSION=$(xcrun --sdk iphoneos --show-sdk-version)
}

workspaces=()
# Set-up a new, clean workspace with only the tools installed.
function create_new_workspace() {
  new_workspace_dir=${1:-$(mktemp -d ${TEST_TMPDIR}/workspace.XXXXXXXX)}
  rm -fr ${new_workspace_dir}
  mkdir -p ${new_workspace_dir}
  workspaces+=(${new_workspace_dir})
  cd ${new_workspace_dir}
  mkdir tools
  mkdir -p third_party/java/jdk/langtools

  copy_tools_directory

  [ -e third_party/java/jdk/langtools/javac.jar ] \
    || ln -s "${langtools_path}"  third_party/java/jdk/langtools/javac.jar

  touch WORKSPACE
}

# Set-up a clean default workspace.
function setup_clean_workspace() {
  export WORKSPACE_DIR=${TEST_TMPDIR}/workspace
  echo "setting up client in ${WORKSPACE_DIR}" > $TEST_log
  rm -fr ${WORKSPACE_DIR}
  create_new_workspace ${WORKSPACE_DIR}
  [ "${new_workspace_dir}" = "${WORKSPACE_DIR}" ] || \
    { echo "Failed to create workspace" >&2; exit 1; }
  export BAZEL_INSTALL_BASE=$(bazel info install_base)
  export BAZEL_GENFILES_DIR=$(bazel info bazel-genfiles)
  export BAZEL_BIN_DIR=$(bazel info bazel-bin)
  if is_windows; then
    export BAZEL_SH="$(cygpath --windows /bin/bash)"
  fi
}

# Clean up all files that are not in tools directories, to restart
# from a clean workspace
function cleanup_workspace() {
  if [ -d "${WORKSPACE_DIR:-}" ]; then
    echo "Cleaning up workspace" > $TEST_log
    cd ${WORKSPACE_DIR}
    bazel clean >& $TEST_log # Clean up the output base

    for i in $(ls); do
      if ! is_tools_directory "$i"; then
        rm -fr "$i"
      fi
    done
    touch WORKSPACE
  fi
  for i in ${workspaces}; do
    if [ "$i" != "${WORKSPACE_DIR:-}" ]; then
      rm -fr $i
    fi
  done
  workspaces=()
}

# Clean-up the bazel install base
function cleanup() {
  if [ -d "${BAZEL_INSTALL_BASE:-__does_not_exists__}" ]; then
    rm -fr "${BAZEL_INSTALL_BASE}"
  fi
}

function tear_down() {
  cleanup_workspace
}

#
# Simples assert to make the tests more readable
#
function assert_build() {
  bazel build -s --verbose_failures $* || fail "Failed to build $*"
}

function assert_build_output() {
  local OUTPUT=$1
  shift
  assert_build "$*"
  test -f "$OUTPUT" || fail "Output $OUTPUT not found for target $*"
}

function assert_build_fails() {
  bazel build -s $1 >& $TEST_log \
    && fail "Test $1 succeed while expecting failure" \
    || true
  if [ -n "${2:-}" ]; then
    expect_log "$2"
  fi
}

function assert_test_ok() {
  bazel test --test_output=errors $* >& $TEST_log \
    || fail "Test $1 failed while expecting success"
}

function assert_test_fails() {
  bazel test --test_output=errors $* >& $TEST_log \
    && fail "Test $* succeed while expecting failure" \
    || true
  expect_log "$1.*FAILED"
}

function assert_binary_run() {
  $1 >& $TEST_log || fail "Failed to run $1"
  [ -z "${2:-}" ] || expect_log "$2"
}

function assert_bazel_run() {
  bazel run $1 >& $TEST_log || fail "Failed to run $1"
    [ -z "${2:-}" ] || expect_log "$2"

  assert_binary_run "./bazel-bin/$(echo "$1" | sed 's|^//||' | sed 's|:|/|')" "${2:-}"
}

setup_bazelrc
setup_clean_workspace
