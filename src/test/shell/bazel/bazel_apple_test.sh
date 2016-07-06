#!/bin/bash
#
# Copyright 2016 The Bazel Authors. All rights reserved.
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
# Tests the examples provided in Bazel
#

# Load test environment
source $(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-setup.sh \
  || { echo "test-setup.sh not found!" >&2; exit 1; }

if [ "${PLATFORM}" != "darwin" ]; then
  echo "This test suite requires running on OS X" >&2
  exit 0
fi

function set_up() {
  copy_examples
  setup_objc_test_support
}

function test_swift_library() {
  rm WORKSPACE
  ln -sv ${workspace_file} WORKSPACE

  local swift_lib_pkg=examples/swift
  assert_build_output ./bazel-bin/${swift_lib_pkg}/swift_lib.a \
      ${swift_lib_pkg}:swift_lib --ios_sdk_version=$IOS_SDK_VERSION
  assert_build_output ./bazel-bin/${swift_lib_pkg}/swift_lib.swiftmodule \
      ${swift_lib_pkg}:swift_lib --ios_sdk_version=$IOS_SDK_VERSION
}

run_suite "apple_tests"
