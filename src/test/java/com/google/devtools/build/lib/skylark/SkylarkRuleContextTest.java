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

package com.google.devtools.build.lib.skylark;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.FileConfiguredTarget;
import com.google.devtools.build.lib.analysis.SkylarkProviders;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.rules.SkylarkRuleContext;
import com.google.devtools.build.lib.rules.java.JavaSourceJarsProvider;
import com.google.devtools.build.lib.rules.python.PyCommon;
import com.google.devtools.build.lib.skylark.util.SkylarkTestCase;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.testutil.TestConstants;
import com.google.devtools.build.lib.vfs.PathFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Tests for SkylarkRuleContext.
 */
@RunWith(JUnit4.class)
public class SkylarkRuleContextTest extends SkylarkTestCase {

  @Before
  public final void generateBuildFile() throws Exception {
    scratch.file(
        "foo/BUILD",
        "package(features = ['-f1', 'f2', 'f3'])",
        "genrule(name = 'foo',",
        "  cmd = 'dummy_cmd',",
        "  srcs = ['a.txt', 'b.img'],",
        "  tools = ['t.exe'],",
        "  outs = ['c.txt'])",
        "genrule(name = 'foo2',",
        "  cmd = 'dummy_cmd',",
        "  outs = ['e.txt'])",
        "genrule(name = 'bar',",
        "  cmd = 'dummy_cmd',",
        "  srcs = [':jl', ':gl'],",
        "  outs = ['d.txt'])",
        "java_library(name = 'jl',",
        "  srcs = ['a.java'])",
        "android_library(name = 'androidlib',",
        "  srcs = ['a.java'])",
        "java_import(name = 'asr',",
        "  jars = [ 'asr.jar' ],",
        "  srcjar = 'asr-src.jar',",
        ")",
        "genrule(name = 'gl',",
        "  cmd = 'touch $(OUTS)',",
        "  srcs = ['a.go'],",
        "  outs = [ 'gl.a', 'gl.gcgox', ],",
        "  output_to_bindir = 1,",
        ")",
        "cc_library(name = 'cc_with_features',",
        "           srcs = ['dummy.cc'],",
        "           features = ['f1', '-f3'],",
        ")"
    );
  }

  private void setUpAttributeErrorTest() throws Exception {
    scratch.file("test/BUILD",
        "load('/test/macros', 'macro_native_rule', 'macro_skylark_rule', 'skylark_rule')",
        "macro_native_rule(name = 'm_native',",
        "  deps = [':jlib'])",
        "macro_skylark_rule(name = 'm_skylark',",
        "  deps = [':jlib'])",
        "java_library(name = 'jlib',",
        "  srcs = ['bla.java'])",
        "cc_library(name = 'cclib',",
        "  deps = [':jlib'])",
        "skylark_rule(name = 'skyrule',",
        "  deps = [':jlib'])");
    scratch.file("test/macros.bzl",
        "def _impl(ctx):",
        "  return",
        "skylark_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'deps': attr.label_list(providers = ['some_provider'], allow_files=True)",
        "  }",
        ")",
        "def macro_native_rule(name, deps): ",
        "  native.cc_library(name = name, deps = deps)",
        "def macro_skylark_rule(name, deps):",
        "  skylark_rule(name = name, deps = deps)");
    reporter.removeHandler(failFastHandler);
  }

  @Test
  public void hasCorrectLocationForRuleAttributeError_NativeRuleWithMacro() throws Exception {
    setUpAttributeErrorTest();
    try {
      createRuleContext("//test:m_native");
      fail("Should have failed because of invalid dependency");
    } catch (Exception ex) {
      // Macro creates native rule -> location points to the rule and the message contains details
      // about the macro.
      assertContainsEvent(
          "ERROR /workspace/test/BUILD:2:1: in deps attribute of cc_library rule //test:m_native: "
              + "java_library rule '//test:jlib' is misplaced here (expected ");
      // Skip the part of the error message that has details about the allowed deps since the mocks
      // for the mac tests might have different values for them.
      assertContainsEvent(". Since this "
          + "rule was created by the macro 'macro_native_rule', the error might have been caused "
          + "by the macro implementation in /workspace/test/macros.bzl:10:41");
    }
  }

  @Test
  public void hasCorrectLocationForRuleAttributeError_SkylarkRuleWithMacro() throws Exception {
    setUpAttributeErrorTest();
    try {
      createRuleContext("//test:m_skylark");
      fail("Should have failed because of invalid attribute value");
    } catch (Exception ex) {
      // Macro creates Skylark rule -> location points to the rule and the message contains details
      // about the macro.
      assertContainsEvent(
          "ERROR /workspace/test/BUILD:4:1: in deps attribute of skylark_rule rule "
              + "//test:m_skylark: '//test:jlib' does not have mandatory provider 'some_provider'. "
              + "Since this rule was created by the macro 'macro_skylark_rule', the error might "
              + "have been caused by the macro implementation in /workspace/test/macros.bzl:12:36");
    }
  }

  @Test
  public void hasCorrectLocationForRuleAttributeError_NativeRule() throws Exception {
    setUpAttributeErrorTest();
    try {
      createRuleContext("//test:cclib");
      fail("Should have failed because of invalid dependency");
    } catch (Exception ex) {
      // Native rule WITHOUT macro -> location points to the attribute and there is no mention of
      // 'macro' at all.
      assertContainsEvent("ERROR /workspace/test/BUILD:9:10: in deps attribute of "
          + "cc_library rule //test:cclib: java_library rule '//test:jlib' is misplaced here "
          + "(expected ");
      // Skip the part of the error message that has details about the allowed deps since the mocks
      // for the mac tests might have different values for them.
      assertDoesNotContainEvent("Since this rule was created by the macro");
    }
  }

  @Test
  public void hasCorrectLocationForRuleAttributeError_SkylarkRule() throws Exception {
    setUpAttributeErrorTest();
    try {
      createRuleContext("//test:skyrule");
      fail("Should have failed because of invalid dependency");
    } catch (Exception ex) {
      // Skylark rule WITHOUT macro -> location points to the attribute and there is no mention of
      // 'macro' at all.
      assertContainsEvent("ERROR /workspace/test/BUILD:11:10: in deps attribute of "
          + "skylark_rule rule //test:skyrule: '//test:jlib' does not have mandatory provider "
          + "'some_provider'");
    }
  }

  @Test
  public void testMandatoryProvidersListWithSkylark() throws Exception {
    scratch.file("test/BUILD",
            "load('/test/rules', 'skylark_rule', 'my_rule', 'my_other_rule')",
            "my_rule(name = 'mylib',",
            "  srcs = ['a.py'])",
            "skylark_rule(name = 'skyrule1',",
            "  deps = [':mylib'])",
            "my_other_rule(name = 'my_other_lib',",
            "  srcs = ['a.py'])",
            "skylark_rule(name = 'skyrule2',",
            "  deps = [':my_other_lib'])");
    scratch.file("test/rules.bzl",
            "def _impl(ctx):",
            "  return",
            "skylark_rule = rule(",
            "  implementation = _impl,",
            "  attrs = {",
            "    'deps': attr.label_list(providers = [['a'], ['b', 'c']],",
            "    allow_files=True)",
            "  }",
            ")",
            "def my_rule_impl(ctx):",
            "  return struct(a = [])",
            "my_rule = rule(implementation = my_rule_impl, ",
            "  attrs = { 'srcs' : attr.label_list(allow_files=True)})",
            "def my_other_rule_impl(ctx):",
            "  return struct(b = [])",
            "my_other_rule = rule(implementation = my_other_rule_impl, ",
            "  attrs = { 'srcs' : attr.label_list(allow_files=True)})");
    reporter.removeHandler(failFastHandler);
    assertNotNull(getConfiguredTarget("//test:skyrule1"));

    try {
      createRuleContext("//test:skyrule2");
      fail("Should have failed because of wrong mandatory providers");
    } catch (Exception ex) {
      assertContainsEvent("ERROR /workspace/test/BUILD:9:10: in deps attribute of "
              + "skylark_rule rule //test:skyrule2: '//test:my_other_lib' does not have "
              + "mandatory provider 'a' or 'c'");
    }
  }

  @Test
  public void testMandatoryProvidersListWithNative() throws Exception {
    scratch.file("test/BUILD",
            "load('/test/rules', 'my_rule', 'my_other_rule')",
            "my_rule(name = 'mylib',",
            "  srcs = ['a.py'])",
            "testing_rule_for_mandatory_providers(name = 'skyrule1',",
            "  deps = [':mylib'])",
            "my_other_rule(name = 'my_other_lib',",
            "  srcs = ['a.py'])",
            "testing_rule_for_mandatory_providers(name = 'skyrule2',",
            "  deps = [':my_other_lib'])");
    scratch.file("test/rules.bzl",
            "def my_rule_impl(ctx):",
            "  return struct(a = [])",
            "my_rule = rule(implementation = my_rule_impl, ",
            "  attrs = { 'srcs' : attr.label_list(allow_files=True)})",
            "def my_other_rule_impl(ctx):",
            "  return struct(b = [])",
            "my_other_rule = rule(implementation = my_other_rule_impl, ",
            "  attrs = { 'srcs' : attr.label_list(allow_files=True)})");
    reporter.removeHandler(failFastHandler);
    assertNotNull(getConfiguredTarget("//test:skyrule1"));

    try {
      createRuleContext("//test:skyrule2");
      fail("Should have failed because of wrong mandatory providers");
    } catch (Exception ex) {
      assertContainsEvent("ERROR /workspace/test/BUILD:9:10: in deps attribute of "
              + "testing_rule_for_mandatory_providers rule //test:skyrule2: '//test:my_other_lib' "
              + "does not have mandatory provider 'a' or 'c'");
    }
  }

  /* Sharing setup code between the testPackageBoundaryError*() methods is not possible since the
   * errors already happen when loading the file. Consequently, all tests would fail at the same
   * statement. */
  @Test
  public void testPackageBoundaryError_NativeRule() throws Exception {
    scratch.file("test/BUILD", "cc_library(name = 'cclib',", "  srcs = ['sub/my_sub_lib.h'])");
    scratch.file("test/sub/BUILD", "cc_library(name = 'my_sub_lib', srcs = ['my_sub_lib.h'])");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//test:cclib");
    assertContainsEvent(
        "ERROR /workspace/test/BUILD:2:10: Label '//test:sub/my_sub_lib.h' crosses boundary of "
            + "subpackage 'test/sub' (perhaps you meant to put the colon here: "
            + "'//test/sub:my_sub_lib.h'?)");
  }

  @Test
  public void testPackageBoundaryError_SkylarkRule() throws Exception {
    scratch.file("test/BUILD",
        "load('/test/macros', 'skylark_rule')",
        "skylark_rule(name = 'skyrule',",
        "  srcs = ['sub/my_sub_lib.h'])");
    scratch.file("test/sub/BUILD",
        "cc_library(name = 'my_sub_lib', srcs = ['my_sub_lib.h'])");
    scratch.file("test/macros.bzl",
        "def _impl(ctx):",
        "  return",
        "skylark_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'srcs': attr.label_list(allow_files=True)",
        "  }",
        ")");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//test:skyrule");
    assertContainsEvent(
        "ERROR /workspace/test/BUILD:3:10: Label '//test:sub/my_sub_lib.h' crosses boundary of "
            + "subpackage 'test/sub' (perhaps you meant to put the colon here: "
            + "'//test/sub:my_sub_lib.h'?)");
  }

  @Test
  public void testPackageBoundaryError_SkylarkMacro() throws Exception {
    scratch.file("test/BUILD",
        "load('/test/macros', 'macro_skylark_rule')",
        "macro_skylark_rule(name = 'm_skylark',",
        "  srcs = ['sub/my_sub_lib.h'])");
    scratch.file("test/sub/BUILD",
        "cc_library(name = 'my_sub_lib', srcs = ['my_sub_lib.h'])");
    scratch.file("test/macros.bzl",
        "def _impl(ctx):",
        "  return",
        "skylark_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'srcs': attr.label_list(allow_files=True)",
        "  }",
        ")",
        "def macro_skylark_rule(name, srcs=[]):",
        "  skylark_rule(name = name, srcs = srcs)");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//test:m_skylark");
    assertContainsEvent("ERROR /workspace/test/BUILD:2:1: Label '//test:sub/my_sub_lib.h' "
        + "crosses boundary of subpackage 'test/sub' (perhaps you meant to put the colon here: "
        + "'//test/sub:my_sub_lib.h'?)");
  }

  /* The error message for this case used to be wrong. */
  @Test
  public void testPackageBoundaryError_ExternalRepository() throws Exception {
    scratch.file("/r/BUILD", "cc_library(name = 'cclib',", "  srcs = ['sub/my_sub_lib.h'])");
    scratch.file("/r/sub/BUILD", "cc_library(name = 'my_sub_lib', srcs = ['my_sub_lib.h'])");
    scratch.overwriteFile("WORKSPACE", "local_repository(name='r', path='/r')");
    invalidatePackages();
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("@r//:cclib");
    assertContainsEvent(
        "/external/r/BUILD:2:10: Label '@r//:sub/my_sub_lib.h' crosses boundary of "
            + "subpackage '@r//sub' (perhaps you meant to put the colon here: "
            + "'@r//sub:my_sub_lib.h'?)");
  }

  /*
   * Making the location in BUILD file the default for "crosses boundary of subpackage" errors does
   * not work in this case since the error actually happens in the bzl file. However, because of
   * the current design, we can neither show the location in the bzl file nor display both
   * locations (BUILD + bzl).
   *
   * Since this case is less common than having such an error in a BUILD file, we can live
   * with it.
   */
  @Test
  public void testPackageBoundaryError_SkylarkMacroWithErrorInBzlFile() throws Exception {
    scratch.file("test/BUILD",
        "load('/test/macros', 'macro_skylark_rule')",
        "macro_skylark_rule(name = 'm_skylark')");
    scratch.file("test/sub/BUILD",
        "cc_library(name = 'my_sub_lib', srcs = ['my_sub_lib.h'])");
    scratch.file("test/macros.bzl",
        "def _impl(ctx):",
        "  return",
        "skylark_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'srcs': attr.label_list(allow_files=True)",
        "  }",
        ")",
        "def macro_skylark_rule(name, srcs=[]):",
        "  skylark_rule(name = name, srcs = srcs + ['sub/my_sub_lib.h'])");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//test:m_skylark");
    assertContainsEvent("ERROR /workspace/test/BUILD:2:1: Label '//test:sub/my_sub_lib.h' "
        + "crosses boundary of subpackage 'test/sub' (perhaps you meant to put the colon here: "
        + "'//test/sub:my_sub_lib.h'?)");
  }

  @Test
  public void testPackageBoundaryError_NativeMacro() throws Exception {
    scratch.file("test/BUILD",
        "load('/test/macros', 'macro_native_rule')",
        "macro_native_rule(name = 'm_native',",
        "  srcs = ['sub/my_sub_lib.h'])");
    scratch.file("test/sub/BUILD",
        "cc_library(name = 'my_sub_lib', srcs = ['my_sub_lib.h'])");
    scratch.file("test/macros.bzl",
        "def macro_native_rule(name, deps=[], srcs=[]): ",
        "  native.cc_library(name = name, deps = deps, srcs = srcs)");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//test:m_native");
    assertContainsEvent("ERROR /workspace/test/BUILD:2:1: Label '//test:sub/my_sub_lib.h' "
        + "crosses boundary of subpackage 'test/sub' (perhaps you meant to put the colon here: "
        + "'//test/sub:my_sub_lib.h'?)");
  }

  @Test
  public void shouldGetPrerequisiteArtifacts() throws Exception {

    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.files.srcs");
    assertArtifactList(result, ImmutableList.of("a.txt", "b.img"));
  }

  private void assertArtifactList(Object result, List<String> artifacts) {
    assertThat(result).isInstanceOf(SkylarkList.class);
    SkylarkList resultList = (SkylarkList) result;
    assertEquals(artifacts.size(), resultList.size());
    int i = 0;
    for (String artifact : artifacts) {
      assertEquals(artifact, ((Artifact) resultList.get(i++)).getFilename());
    }
  }

  @Test
  public void shouldGetPrerequisites() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:bar");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.srcs");
    // Check for a known provider
    TransitiveInfoCollection tic1 = (TransitiveInfoCollection) ((SkylarkList) result).get(0);
    assertNotNull(tic1.getProvider(JavaSourceJarsProvider.class));
    // Check an unimplemented provider too
    assertNull(tic1.getProvider(SkylarkProviders.class)
        .getValue(PyCommon.PYTHON_SKYLARK_PROVIDER_NAME));
  }

  @Test
  public void shouldGetPrerequisite() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:asr");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.srcjar");
    TransitiveInfoCollection tic = (TransitiveInfoCollection) result;
    assertThat(tic).isInstanceOf(FileConfiguredTarget.class);
    assertEquals("asr-src.jar", tic.getLabel().getName());
  }

  @Test
  public void testMiddleMan() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:jl");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.middle_man(':host_jdk')");
    assertThat(
            Iterables.getOnlyElement(((SkylarkNestedSet) result).getSet(Artifact.class))
                .getExecPathString())
        .contains("middlemen");
  }

  @Test
  public void testGetRuleAttributeListType() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.outs");
    assertThat(result).isInstanceOf(SkylarkList.class);
  }

  @Test
  public void testGetRuleSelect() throws Exception {
    scratch.file("test/skylark/BUILD");
    scratch.file(
        "test/skylark/rulestr.bzl", "def rule_dict(name):", "  return native.existing_rule(name)");

    scratch.file(
        "test/getrule/BUILD",
        "load('/test/skylark/rulestr', 'rule_dict')",
        "cc_library(name ='x', ",
        "  srcs = select({'//conditions:default': []})",
        ")",
        "rule_dict('x')");

    // Parse the BUILD file, to make sure select() makes it out of native.rule().
    createRuleContext("//test/getrule:x");
  }

  @Test
  public void testGetRule() throws Exception {
    scratch.file("test/skylark/BUILD");
    scratch.file(
        "test/skylark/rulestr.bzl",
        "def rule_dict(name):",
        "  return native.existing_rule(name)",
        "def rules_dict():",
        "  return native.existing_rules()",
        "def nop(ctx):",
        "  pass",
        "nop_rule = rule(attrs = {'x': attr.label()}, implementation = nop)",
        "consume_rule = rule(attrs = {'s': attr.string_list()}, implementation = nop)");

    scratch.file(
        "test/getrule/BUILD",
        "load('/test/skylark/rulestr', 'rules_dict', 'rule_dict', 'nop_rule', 'consume_rule')",
        "genrule(name = 'a', outs = ['a.txt'], ",
        "        licenses = ['notice'],",
        "        output_to_bindir = False,",
        "        tools = [ '//test:bla' ], cmd = 'touch $@')",
        "nop_rule(name = 'c', x = ':a')",
        "rlist= rules_dict()",
        "consume_rule(name = 'all_str', s = [rlist['a']['kind'], rlist['a']['name'], ",
        "                                    rlist['c']['kind'], rlist['c']['name']])",
        "adict = rule_dict('a')",
        "cdict = rule_dict('c')",
        "consume_rule(name = 'a_str', ",
        "             s = [adict['kind'], adict['name'], adict['outs'][0], adict['tools'][0]])",
        "consume_rule(name = 'genrule_attr', ",
        "             s = adict.keys())",
        "consume_rule(name = 'c_str', s = [cdict['kind'], cdict['name'], cdict['x']])");

    SkylarkRuleContext allContext = createRuleContext("//test/getrule:all_str");
    Object result = evalRuleContextCode(allContext, "ruleContext.attr.s");
    assertEquals(
        new SkylarkList.MutableList(ImmutableList.<String>of("genrule", "a", "nop_rule", "c")),
        result);

    result = evalRuleContextCode(createRuleContext("//test/getrule:a_str"), "ruleContext.attr.s");
    assertEquals(
        new SkylarkList.MutableList(
            ImmutableList.<String>of("genrule", "a", ":a.txt", "//test:bla")),
        result);

    result = evalRuleContextCode(createRuleContext("//test/getrule:c_str"), "ruleContext.attr.s");
    assertEquals(
        new SkylarkList.MutableList(ImmutableList.<String>of("nop_rule", "c", ":a")), result);

    result =
        evalRuleContextCode(createRuleContext("//test/getrule:genrule_attr"), "ruleContext.attr.s");
    assertEquals(
        new SkylarkList.MutableList(
            ImmutableList.<String>of(
                "cmd",
                "compatible_with",
                "executable",
                "features",
                "generator_function",
                "generator_location",
                "generator_name",
                "heuristic_label_expansion",
                "kind",
                "local",
                "message",
                "name",
                "output_to_bindir",
                "outs",
                "restricted_to",
                "srcs",
                "stamp",
                "tags",
                "tools",
                "visibility")),
        result);
  }

  @Test
  public void testGetRuleAttributeListValue() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.outs");
    assertEquals(1, ((SkylarkList) result).size());
  }

  @Test
  public void testGetRuleAttributeListValueNoGet() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.outs");
    assertEquals(1, ((SkylarkList) result).size());
  }

  @Test
  public void testGetRuleAttributeStringTypeValue() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.cmd");
    assertEquals("dummy_cmd", (String) result);
  }

  @Test
  public void testGetRuleAttributeStringTypeValueNoGet() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.attr.cmd");
    assertEquals("dummy_cmd", (String) result);
  }

  @Test
  public void testGetRuleAttributeBadAttributeName() throws Exception {
    checkErrorContains(
        createRuleContext("//foo:foo"), "No attribute 'bad'", "ruleContext.attr.bad");
  }

  @Test
  public void testGetLabel() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.label");
    assertEquals("//foo:foo", ((Label) result).toString());
  }

  @Test
  public void testRuleError() throws Exception {
    checkErrorContains(createRuleContext("//foo:foo"), "message", "fail('message')");
  }

  @Test
  public void testAttributeError() throws Exception {
    checkErrorContains(
        createRuleContext("//foo:foo"),
        "attribute srcs: message",
        "fail(attr='srcs', msg='message')");
  }

  @Test
  public void testGetExecutablePrerequisite() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:androidlib");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.executable._jarjar_bin");
    assertEquals("jarjar_bin", ((Artifact) result).getFilename());
  }

  @Test
  public void testCreateSpawnActionArgumentsWithExecutableFilesToRunProvider() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:androidlib");
    evalRuleContextCode(
        ruleContext,
        "ruleContext.action(\n"
            + "  inputs = ruleContext.files.srcs,\n"
            + "  outputs = ruleContext.files.srcs,\n"
            + "  arguments = ['--a','--b'],\n"
            + "  executable = ruleContext.executable._jarjar_bin)\n");
    SpawnAction action =
        (SpawnAction)
            Iterables.getOnlyElement(
                ruleContext.getRuleContext().getAnalysisEnvironment().getRegisteredActions());
    assertThat(action.getCommandFilename()).endsWith("/jarjar_bin");
  }

  @Test
  public void testOutputs() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:bar");
    Iterable<?> result = (Iterable<?>) evalRuleContextCode(ruleContext, "ruleContext.outputs.outs");
    assertEquals("d.txt", ((Artifact) Iterables.getOnlyElement(result)).getFilename());
  }

  @Test
  public void testSkylarkRuleContextStr() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "'%s' % ruleContext");
    assertEquals("//foo:foo", result);
  }

  @Test
  public void testSkylarkRuleContextGetDefaultShellEnv() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.configuration.default_shell_env");
    assertThat(result).isInstanceOf(SkylarkDict.class);
  }

  @Test
  public void testCheckPlaceholders() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(ruleContext, "ruleContext.check_placeholders('%{name}', ['name'])");
    assertEquals(true, result);
  }

  @Test
  public void testCheckPlaceholdersBadPlaceholder() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(ruleContext, "ruleContext.check_placeholders('%{name}', ['abc'])");
    assertEquals(false, result);
  }

  @Test
  public void testExpandMakeVariables() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(
            ruleContext, "ruleContext.expand_make_variables('cmd', '$(ABC)', {'ABC': 'DEF'})");
    assertEquals("DEF", result);
  }

  @Test
  public void testExpandMakeVariablesShell() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(ruleContext, "ruleContext.expand_make_variables('cmd', '$$ABC', {})");
    assertEquals("$ABC", result);
  }

  @Test
  public void testConfiguration() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.configuration");
    assertSame(result, ruleContext.getRuleContext().getConfiguration());
  }

  @Test
  public void testFeatures() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:cc_with_features");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.features");
    assertThat((SkylarkList) result).containsExactly("f1", "f2");
  }


  @Test
  public void testHostConfiguration() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.host_configuration");
    assertSame(result, ruleContext.getRuleContext().getHostConfiguration());
  }

  @Test
  public void testWorkspaceName() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.workspace_name");
    assertSame(result, TestConstants.WORKSPACE_NAME);
  }

  @Test
  public void testDeriveArtifactLegacy() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(
            ruleContext,
            "ruleContext.new_file(ruleContext.configuration.genfiles_dir," + "  'a/b.txt')");
    PathFragment fragment = ((Artifact) result).getRootRelativePath();
    assertEquals("foo/a/b.txt", fragment.getPathString());
  }

  @Test
  public void testDeriveArtifact() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result = evalRuleContextCode(ruleContext, "ruleContext.new_file('a/b.txt')");
    PathFragment fragment = ((Artifact) result).getRootRelativePath();
    assertEquals("foo/a/b.txt", fragment.getPathString());
  }

  @Test
  public void testParamFileLegacy() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(
            ruleContext,
            "ruleContext.new_file(ruleContext.configuration.bin_dir,"
                + "ruleContext.files.tools[0], '.params')");
    PathFragment fragment = ((Artifact) result).getRootRelativePath();
    assertEquals("foo/t.exe.params", fragment.getPathString());
  }

  @Test
  public void testParamFileSuffix() throws Exception {
    SkylarkRuleContext ruleContext = createRuleContext("//foo:foo");
    Object result =
        evalRuleContextCode(
            ruleContext,
            "ruleContext.new_file(ruleContext.files.tools[0], "
                + "ruleContext.files.tools[0].basename + '.params')");
    PathFragment fragment = ((Artifact) result).getRootRelativePath();
    assertEquals("foo/t.exe.params", fragment.getPathString());
  }

  @Test
  public void testLabelAttributeDefault() throws Exception {
    scratch.file(
        "my_rule.bzl",
        "def _impl(ctx):",
        "  return",
        "my_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'explicit_dep': attr.label(default = Label('//:dep')),",
        "    '_implicit_dep': attr.label(default = Label('//:dep')),",
        "    'explicit_dep_list': attr.label_list(default = [Label('//:dep')]),",
        "    '_implicit_dep_list': attr.label_list(default = [Label('//:dep')]),",
        "  }",
        ")");

    scratch.file(
        "BUILD", "filegroup(name='dep')", "load('/my_rule', 'my_rule')", "my_rule(name='r')");

    invalidatePackages();
    SkylarkRuleContext context = createRuleContext("//:r");
    Label explicitDepLabel =
        (Label) evalRuleContextCode(context, "ruleContext.attr.explicit_dep.label");
    assertThat(explicitDepLabel).isEqualTo(Label.parseAbsolute("//:dep"));
    Label implicitDepLabel =
        (Label) evalRuleContextCode(context, "ruleContext.attr._implicit_dep.label");
    assertThat(implicitDepLabel).isEqualTo(Label.parseAbsolute("//:dep"));
    Label explicitDepListLabel =
        (Label) evalRuleContextCode(context, "ruleContext.attr.explicit_dep_list[0].label");
    assertThat(explicitDepListLabel).isEqualTo(Label.parseAbsolute("//:dep"));
    Label implicitDepListLabel =
        (Label) evalRuleContextCode(context, "ruleContext.attr._implicit_dep_list[0].label");
    assertThat(implicitDepListLabel).isEqualTo(Label.parseAbsolute("//:dep"));
  }

  @Test
  public void testRelativeLabelInExternalRepository() throws Exception {
    scratch.file("external_rule.bzl",
        "def _impl(ctx):",
        "  return",
        "external_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'internal_dep': attr.label(default = Label('//:dep'))",
        "  }",
        ")");

    scratch.file("BUILD",
        "filegroup(name='dep')");

    scratch.file("/r/a/BUILD",
        "load('/external_rule', 'external_rule')",
        "external_rule(name='r')");

    scratch.overwriteFile("WORKSPACE",
        "local_repository(name='r', path='/r')");

    invalidatePackages();
    SkylarkRuleContext context = createRuleContext("@r//a:r");
    Label depLabel = (Label) evalRuleContextCode(context, "ruleContext.attr.internal_dep.label");
    assertThat(depLabel).isEqualTo(Label.parseAbsolute("//:dep"));
  }

  @Test
  public void testCallerRelativeLabelInExternalRepository() throws Exception {
    scratch.file("BUILD");
    scratch.file("external_rule.bzl",
        "def _impl(ctx):",
        "  return",
        "external_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'internal_dep': attr.label(",
        "        default = Label('//:dep', relative_to_caller_repository = True)",
        "    )",
        "  }",
        ")");

    scratch.file("/r/BUILD",
        "filegroup(name='dep')");

    scratch.file("/r/a/BUILD",
        "load('/external_rule', 'external_rule')",
        "external_rule(name='r')");

    scratch.overwriteFile("WORKSPACE",
        "local_repository(name='r', path='/r')");

    invalidatePackages();
    SkylarkRuleContext context = createRuleContext("@r//a:r");
    Label depLabel = (Label) evalRuleContextCode(context, "ruleContext.attr.internal_dep.label");
    assertThat(depLabel).isEqualTo(Label.parseAbsolute("@r//:dep"));
  }

  @Test
  public void testExternalWorkspaceLoad() throws Exception {
    scratch.file(
        "/r1/BUILD",
        "filegroup(name = 'test',",
        " srcs = ['test.txt'],",
        " visibility = ['//visibility:public'],",
        ")");
    scratch.file("/r1/WORKSPACE");
    scratch.file("/r2/BUILD", "exports_files(['test.bzl'])");
    scratch.file(
        "/r2/test.bzl",
        "def macro(name, path):",
        "  native.local_repository(name = name, path = path)"
    );
    scratch.file(
        "/r2/other_test.bzl",
        "def other_macro(name, path):",
        "  print(name + ': ' + path)"
    );
    scratch.file("BUILD");
    scratch.overwriteFile(
        "WORKSPACE",
        "local_repository(name='r2', path='/r2')",
        "load('@r2//:test.bzl', 'macro')",
        "macro('r1', '/r1')",
        "NEXT_NAME = 'r3'",
        "load('@r2//:other_test.bzl', 'other_macro')",  // We can still refer to r2 in other chunks.
        "macro(NEXT_NAME, '/r2')"  // and we can still use macro outside of its chunk.
    );
    invalidatePackages();
    assertThat(getConfiguredTarget("@r1//:test")).isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testLoadBlockRepositoryRedefinition() throws Exception {
    reporter.removeHandler(failFastHandler);
    scratch.file("/bar/WORKSPACE");
    scratch.file("/bar/bar.txt");
    scratch.file("/bar/BUILD", "filegroup(name = 'baz', srcs = ['bar.txt'])");
    scratch.file("/baz/WORKSPACE");
    scratch.file("/baz/baz.txt");
    scratch.file("/baz/BUILD", "filegroup(name = 'baz', srcs = ['baz.txt'])");
    scratch.overwriteFile(
        "WORKSPACE",
        "local_repository(name = 'foo', path = '/bar')",
        "local_repository(name = 'foo', path = '/baz')");
    invalidatePackages();
    assertThat(
            (List<Label>)
                getConfiguredTarget("@foo//:baz")
                    .getTarget()
                    .getAssociatedRule()
                    .getAttributeContainer()
                    .getAttr("srcs"))
        .contains(Label.parseAbsolute("@foo//:baz.txt"));

    scratch.overwriteFile("BUILD");
    scratch.overwriteFile("bar.bzl", "dummy = 1");
    scratch.overwriteFile(
        "WORKSPACE",
        "local_repository(name = 'foo', path = '/bar')",
        "load('//:bar.bzl', 'dummy')",
        "local_repository(name = 'foo', path = '/baz')");
    try {
      invalidatePackages();
      createRuleContext("@foo//:baz");
      fail("Should have failed because repository 'foo' is overloading after a load!");
    } catch (Exception ex) {
      assertContainsEvent(
          "ERROR /workspace/WORKSPACE:3:1: Cannot redefine repository after any load statement "
              + "in the WORKSPACE file (for repository 'foo')");
    }
  }

  @Test
  public void testAccessingRunfiles() throws Exception {
    scratch.file("test/a.py");
    scratch.file("test/b.py");
    scratch.file("test/__init__.py");
    scratch.file(
        "test/rule.bzl",
        "def _impl(ctx):",
        "  return",
        "skylark_rule = rule(",
        "  implementation = _impl,",
        "  attrs = {",
        "    'dep': attr.label(),",
        "  },",
        ")");
    scratch.file(
        "test/BUILD",
        "load('/test/rule', 'skylark_rule')",
        "py_library(name = 'lib', srcs = ['a.py', 'b.py'])",
        "skylark_rule(name = 'foo', dep = ':lib')",
        "py_library(name = 'lib_with_init', srcs = ['a.py', 'b.py', '__init__.py'])",
        "skylark_rule(name = 'foo_with_init', dep = ':lib_with_init')");

    SkylarkRuleContext ruleContext = createRuleContext("//test:foo");
    Object filenames =
        evalRuleContextCode(
            ruleContext, "[f.short_path for f in ruleContext.attr.dep.default_runfiles.files]");
    assertThat(filenames).isInstanceOf(SkylarkList.class);
    SkylarkList filenamesList = (SkylarkList) filenames;
    assertThat(filenamesList).containsExactly("test/a.py", "test/b.py").inOrder();
    Object emptyFilenames =
        evalRuleContextCode(
            ruleContext, "list(ruleContext.attr.dep.default_runfiles.empty_filenames)");
    assertThat(emptyFilenames).isInstanceOf(SkylarkList.class);
    SkylarkList emptyFilenamesList = (SkylarkList) emptyFilenames;
    assertThat(emptyFilenamesList).containsExactly("test/__init__.py").inOrder();

    SkylarkRuleContext ruleWithInitContext = createRuleContext("//test:foo_with_init");
    Object noEmptyFilenames =
        evalRuleContextCode(
            ruleWithInitContext, "list(ruleContext.attr.dep.default_runfiles.empty_filenames)");
    assertThat(noEmptyFilenames).isInstanceOf(SkylarkList.class);
    SkylarkList noEmptyFilenamesList = (SkylarkList) noEmptyFilenames;
    assertThat(noEmptyFilenamesList).containsExactly().inOrder();
  }
}
