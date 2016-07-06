# Copyright 2016 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Implementaion of AndroidStudio-specific information collecting aspect.

# A map to convert rule names to a RuleIdeInfo.Kind
# Deprecated - only here for backwards compatibility with the tests
_kind_to_kind_id = {
  "android_binary"  : 0,
  "android_library" : 1,
  "android_test" :    2,
  "android_robolectric_test" : 3,
  "java_library" : 4,
  "java_test" : 5,
  "java_import" : 6,
  "java_binary" : 7,
  "proto_library" : 8,
  "android_sdk" : 9,
  "java_plugin" : 10,
  "android_resources" : 11,
  "cc_library" : 12,
  "cc_binary" : 13,
  "cc_test" : 14,
  "cc_inc_library" : 15,
  "cc_toolchain": 16,
  "java_wrap_cc": 17,
}

_unrecognized_rule = -1;

def get_kind_legacy(target, ctx):
  """ Gets kind of a rule given a target and rule context.
  """
  return _kind_to_kind_id.get(ctx.rule.kind, _unrecognized_rule)


# Compile-time dependency attributes, grouped by type.
DEPS = struct(
    label = [
      "binary_under_test", #  From android_test
      "java_lib",# From proto_library
      "_proto1_java_lib", # From proto_library
      "_junit", # From android_robolectric_test
      "_cc_toolchain", # From C rules
      "module_target",
    ],
    label_list = [
      "deps",
      "exports",
      "_robolectric", # From android_robolectric_test
    ],
)


# Run-time dependency attributes, grouped by type.
RUNTIME_DEPS = struct(
    label = [
      # todo(dslomov,tomlu): resources are tricky since they are sometimes labels and sometimes label_lists.
      # "resources"
    ],
    label_list = [
      "runtime_deps",
    ]
)

# All dependency attributes along which the aspect propagates, grouped by type.
ALL_DEPS = struct(
  label = DEPS.label + RUNTIME_DEPS.label,
  label_list = DEPS.label_list + RUNTIME_DEPS.label_list,
)

def struct_omit_none(**kwargs):
    """ A replacement for standard `struct` function that omits the fields with None value.
    """
    d = {name: kwargs[name] for name in kwargs if kwargs[name] != None}
    return struct(**d)

def artifact_location(file):
  """ Creates an ArtifactLocation proto from a File.
  """
  if file == None:
    return None
  return struct_omit_none(
      relative_path = file.short_path,
      is_source = file.is_source,
      root_execution_path_fragment = file.root.path if not file.is_source else None
  )

def source_directory_tuple(resource_file):
  """ Creates a tuple of (source directory, is_source, root execution path) from an android resource file.
  """
  return (
      str(android_common.resource_source_directory(resource_file)),
      resource_file.is_source,
      resource_file.root.path if not resource_file.is_source else None
  )

def all_unique_source_directories(resources):
  """ Builds a list of ArtifactLocation protos for all source directories for a list of Android resources.
  """
  # Sets can contain tuples, but cannot contain structs.
  # Use set of tuples to unquify source directories.
  source_directory_tuples = set([source_directory_tuple(file) for file in resources])
  return [struct_omit_none(relative_path = relative_path,
                           is_source = is_source,
                           root_execution_path_fragment = root_execution_path_fragment)
          for (relative_path, is_source, root_execution_path_fragment) in source_directory_tuples]

def build_file_artifact_location(build_file_path):
  """ Creates an ArtifactLocation proto representing a location of a given BUILD file.
  """
  return struct(
      relative_path = build_file_path,
      is_source = True,
  )

def library_artifact(java_output):
  """ Creates a LibraryArtifact representing a given java_output.
  """
  if java_output == None or java_output.class_jar == None:
    return None
  return struct_omit_none(
        jar = artifact_location(java_output.class_jar),
        interface_jar = artifact_location(java_output.ijar),
        source_jar = artifact_location(java_output.source_jar),
  )

def annotation_processing_jars(annotation_processing):
  """ Creates a LibraryArtifact representing Java annotation processing jars.
  """
  return struct_omit_none(
        jar = artifact_location(annotation_processing.class_jar),
        source_jar = artifact_location(annotation_processing.source_jar),
  )

def jars_from_output(output):
  """ Collect jars for ide-resolve-files from Java output.
  """
  if output == None:
    return []
  return [jar
          for jar in [output.class_jar, output.ijar, output.source_jar]
          if jar != None and not jar.is_source]

def c_rule_ide_info(target, ctx):
  """ Build CRuleIdeInfo.

  Returns a pair of (CRuleIdeInfo proto, a set of ide-resolve-files).
  (or (None, empty set) if the rule is not a C rule).
  """
  if not hasattr(target, "cc"):
    return (None, set())

  sources = getSourcesFromRule(ctx)

  if hasattr(ctx.rule.attr, "hdrs"):
    exported_headers = [artifact_location(file)
                        for hdr in ctx.rule.attr.hdrs
                        for file in hdr.files]
  else:
    exported_headers = []

  rule_includes = []
  if hasattr(ctx.rule.attr, "includes"):
    rule_includes = ctx.rule.attr.includes
  rule_defines = []
  if hasattr(ctx.rule.attr, "defines"):
    rule_defines = ctx.rule.attr.defines
  rule_copts = []
  if hasattr(ctx.rule.attr, "copts"):
    rule_copts = ctx.rule.attr.copts

  cc_provider = target.cc

  ide_resolve_files = set()

  return (struct_omit_none(
                  source = sources,
                  exported_header = exported_headers,
                  rule_include = rule_includes,
                  rule_define = rule_defines,
                  rule_copt = rule_copts,
                  transitive_include_directory = cc_provider.include_directories,
                  transitive_quote_include_directory = cc_provider.quote_include_directories,
                  transitive_define = cc_provider.defines,
                  transitive_system_include_directory = cc_provider.system_include_directories
         ),
         ide_resolve_files)

def c_toolchain_ide_info(target, ctx):
  """ Build CToolchainIdeInfo.

  Returns a pair of (CToolchainIdeInfo proto, a set of ide-resolve-files).
  (or (None, empty set) if the rule is not a cc_toolchain rule).
  """

  if ctx.rule.kind != "cc_toolchain":
    return (None, set())

  # This should exist because we requested it in our aspect definition.
  cc_fragment = ctx.fragments.cpp

  return (struct_omit_none(
                  target_name = cc_fragment.target_gnu_system_name,
                  base_compiler_option = cc_fragment.compiler_options(ctx.features),
                  c_option = cc_fragment.c_options,
                  cpp_option = cc_fragment.cxx_options(ctx.features),
                  link_option = cc_fragment.link_options,
                  unfiltered_compiler_option = cc_fragment.unfiltered_compiler_options(ctx.features),
                  preprocessor_executable =
                      replaceEmptyPathWithDot(str(cc_fragment.preprocessor_executable)),
                  cpp_executable = str(cc_fragment.compiler_executable),
                  built_in_include_directory = [str(d)
                                                for d in cc_fragment.built_in_include_directories]
         ),
         set())

# TODO(salguarnieri) Remove once skylark provides the path safe string from a PathFragment.
def replaceEmptyPathWithDot(pathString):
  return "." if len(pathString) == 0 else pathString

def getSourcesFromRule(context):
  """
  Get the list of sources from a rule as artifact locations.

  Returns the list of sources as artifact locations for a rule or an empty list if no sources are
  present.
  """

  if hasattr(context.rule.attr, "srcs"):
    return [artifact_location(file)
            for src in context.rule.attr.srcs
            for file in src.files]
  return []

def java_rule_ide_info(target, ctx):
  """ Build JavaRuleIdeInfo.

  Returns a pair of (JavaRuleIdeInfo proto, a set of ide-resolve-files).
  (or (None, empty set) if the rule is not Java rule).
  """
  if not hasattr(target, "java"):
    return (None, set())

  sources = getSourcesFromRule(ctx)

  jars = [library_artifact(output) for output in target.java.outputs.jars]
  ide_resolve_files = set([jar
       for output in target.java.outputs.jars
       for jar in jars_from_output(output)])

  gen_jars = []
  if target.java.annotation_processing and target.java.annotation_processing.enabled:
    gen_jars = [annotation_processing_jars(target.java.annotation_processing)]
    ide_resolve_files = ide_resolve_files | set([ jar
        for jar in [target.java.annotation_processing.class_jar,
                    target.java.annotation_processing.source_jar]
        if jar != None and not jar.is_source])

  jdeps = artifact_location(target.java.outputs.jdeps)

  return (struct_omit_none(
                 sources = sources,
                 jars = jars,
                 jdeps = jdeps,
                 generated_jars = gen_jars
          ),
          ide_resolve_files)

def android_rule_ide_info(target, ctx):
  """ Build AndroidRuleIdeInfo.

  Returns a pair of (AndroidRuleIdeInfo proto, a set of ide-resolve-files).
  (or (None, empty set) if the rule is not Android rule).
  """
  if not hasattr(target, 'android'):
    return (None, set())
  ide_resolve_files = set(jars_from_output(target.android.idl.output))
  return (struct_omit_none(
            java_package = target.android.java_package,
            manifest = artifact_location(target.android.manifest),
            apk = artifact_location(target.android.apk),
            dependency_apk = [artifact_location(apk) for apk in target.android.apks_under_test],
            has_idl_sources = target.android.idl.output != None,
            idl_jar = library_artifact(target.android.idl.output),
            generate_resource_class = target.android.defines_resources,
            resources = all_unique_source_directories(target.android.resources),
        ),
        ide_resolve_files)

def test_info(target, ctx):
  """ Build TestInfo """
  if not is_test_rule(ctx):
    return None
  return struct_omit_none(
           size = ctx.rule.attr.size,
         )

def is_test_rule(ctx):
  kind_string = ctx.rule.kind
  return kind_string.endswith("_test")

def collect_labels(rule_attrs, attrs):
  """ Collect labels from attribute values.

  Assuming that values of attributes from attr_list in rule_atrs
  are label lists, collect a set of string representation of those labels.
  """
  return set([str(dep.label)
      for attr_name in attrs.label_list
      if hasattr(rule_attrs, attr_name)
      for dep in getattr(rule_attrs, attr_name)]) | \
         set([str(getattr(rule_attrs, attr_name).label)
      for attr_name in attrs.label
      if hasattr(rule_attrs, attr_name)])

def collect_export_deps(rule_attrs):
  """ Build a union of all export dependencies.
  """
  result = set()
  for attr_name in DEPS.label_list:
    if hasattr(rule_attrs, attr_name):
      for dep in getattr(rule_attrs, attr_name):
        result = result | dep.export_deps
  for attr_name in DEPS.label:
    if hasattr(rule_attrs, attr_name):
      dep = getattr(rule_attrs, attr_name)
      result = result | dep.export_deps

  return result

def _aspect_impl(target, ctx):
  """ Aspect implementation function
  """
  kind_legacy = get_kind_legacy(target, ctx)
  kind_string = ctx.rule.kind
  rule_attrs = ctx.rule.attr

  # Collect transitive values
  compiletime_deps = collect_labels(rule_attrs, DEPS) | collect_export_deps(rule_attrs)
  runtime_deps = collect_labels(rule_attrs, RUNTIME_DEPS)

  ide_info_text = set()
  ide_resolve_files = set()

  for attr_name in ALL_DEPS.label_list:
    if hasattr(rule_attrs, attr_name):
      for dep in getattr(rule_attrs, attr_name):
        ide_info_text = ide_info_text | dep.intellij_info_files.ide_info_text
        ide_resolve_files = ide_resolve_files | dep.intellij_info_files.ide_resolve_files

  for attr_name in ALL_DEPS.label:
    if hasattr(rule_attrs, attr_name):
      dep = getattr(rule_attrs, attr_name)
      ide_info_text = ide_info_text | dep.intellij_info_files.ide_info_text
      ide_resolve_files = ide_resolve_files | dep.intellij_info_files.ide_resolve_files


  # Collect C-specific information
  (c_rule_ide_info, c_ide_resolve_files) = c_rule_ide_info(target, ctx)
  ide_resolve_files = ide_resolve_files | c_ide_resolve_files

  (c_toolchain_ide_info, c_toolchain_ide_resolve_files) = c_toolchain_ide_info(target, ctx)
  ide_resolve_files = ide_resolve_files | c_toolchain_ide_resolve_files

  # Collect Java-specific information
  (java_rule_ide_info, java_ide_resolve_files) = java_rule_ide_info(target, ctx)
  ide_resolve_files = ide_resolve_files | java_ide_resolve_files

  # Collect Android-specific information
  (android_rule_ide_info, android_ide_resolve_files) = android_rule_ide_info(target, ctx)
  ide_resolve_files = ide_resolve_files | android_ide_resolve_files

  # Collect test info
  test_info = test_info(target, ctx)

  # Collect information about exports.
  export_deps = set()
  if hasattr(target, "java"):
    export_deps = set([str(l) for l in target.java.transitive_exports])
    # Empty android libraries export all their dependencies.
    if ctx.rule.kind == "android_library" and \
            (not hasattr(rule_attrs, "src") or not ctx.rule.attr.src):
      export_deps = export_deps | compiletime_deps

  # Build RuleIdeInfo proto
  info = struct_omit_none(
      label = str(target.label),
      kind = kind_legacy if kind_legacy != _unrecognized_rule else None,
      kind_string = kind_string,
      dependencies = list(compiletime_deps),
      runtime_deps = list(runtime_deps),
      build_file_artifact_location = build_file_artifact_location(ctx.build_file_path),
      c_rule_ide_info = c_rule_ide_info,
      c_toolchain_ide_info = c_toolchain_ide_info,
      java_rule_ide_info = java_rule_ide_info,
      android_rule_ide_info = android_rule_ide_info,
      tags = ctx.rule.attr.tags,
      test_info = test_info,
  )

  # Output the ide information file.
  output = ctx.new_file(target.label.name + ".aswb-build.txt")
  ctx.file_action(output, info.to_proto())
  ide_info_text += set([output])

  # Return providers.
  return struct(
      output_groups = {
        "ide-info-text" : ide_info_text,
        "ide-resolve" : ide_resolve_files,
      },
      intellij_info_files = struct(
        ide_info_text = ide_info_text,
        ide_resolve_files = ide_resolve_files,
      ),
      export_deps = export_deps,
    )

intellij_info_aspect = aspect(
    implementation = _aspect_impl,
    attr_aspects = ALL_DEPS.label + ALL_DEPS.label_list,
)
