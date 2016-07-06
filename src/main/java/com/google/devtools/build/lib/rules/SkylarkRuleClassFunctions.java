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

package com.google.devtools.build.lib.rules;

import static com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition.DATA;
import static com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition.HOST;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.packages.BuildType.LICENSE;
import static com.google.devtools.build.lib.syntax.SkylarkType.castMap;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.INTEGER;
import static com.google.devtools.build.lib.syntax.Type.STRING;
import static com.google.devtools.build.lib.syntax.Type.STRING_LIST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.OutputGroupProvider;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.RunUnder;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.cmdline.LabelValidator;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition;
import com.google.devtools.build.lib.packages.Attribute.LateBoundLabel;
import com.google.devtools.build.lib.packages.Attribute.LateBoundLabelList;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.packages.ImplicitOutputsFunction.SkylarkImplicitOutputsFunctionWithCallback;
import com.google.devtools.build.lib.packages.ImplicitOutputsFunction.SkylarkImplicitOutputsFunctionWithMap;
import com.google.devtools.build.lib.packages.Package.NameConflictException;
import com.google.devtools.build.lib.packages.PackageFactory;
import com.google.devtools.build.lib.packages.PackageFactory.PackageContext;
import com.google.devtools.build.lib.packages.PredicateWithMessage;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.packages.RuleFactory;
import com.google.devtools.build.lib.packages.RuleFactory.BuildLangTypedAttributeValuesMap;
import com.google.devtools.build.lib.packages.RuleFactory.InvalidRuleException;
import com.google.devtools.build.lib.packages.SkylarkAspect;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.packages.TestSize;
import com.google.devtools.build.lib.rules.SkylarkAttr.Descriptor;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature.Param;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.ClassObject;
import com.google.devtools.build.lib.syntax.ClassObject.SkylarkClassObject;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Environment.NoSuchVariableException;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.FuncallExpression;
import com.google.devtools.build.lib.syntax.FunctionSignature;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkCallbackFunction;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.syntax.Type.ConversionException;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.util.Preconditions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * A helper class to provide an easier API for Skylark rule definitions.
 */
public class SkylarkRuleClassFunctions {

  //TODO(bazel-team): proper enum support
  @SkylarkSignature(name = "DATA_CFG", returnType = ConfigurationTransition.class,
      doc = "Experimental. Specifies a transition to the data configuration.")
  private static final Object dataTransition = ConfigurationTransition.DATA;

  @SkylarkSignature(name = "HOST_CFG", returnType = ConfigurationTransition.class,
      doc = "Specifies a transition to the host configuration.")
  private static final Object hostTransition = ConfigurationTransition.HOST;

  private static final LateBoundLabel<BuildConfiguration> RUN_UNDER =
      new LateBoundLabel<BuildConfiguration>() {
        @Override
        public Label resolve(Rule rule, AttributeMap attributes,
            BuildConfiguration configuration) {
          RunUnder runUnder = configuration.getRunUnder();
          return runUnder == null ? null : runUnder.getLabel();
        }
      };

  private static final Label COVERAGE_SUPPORT_LABEL =
      Label.parseAbsoluteUnchecked("//tools/defaults:coverage");

  private static final LateBoundLabelList<BuildConfiguration> GCOV =
      new LateBoundLabelList<BuildConfiguration>(ImmutableList.of(COVERAGE_SUPPORT_LABEL)) {
        @Override
        public List<Label> resolve(Rule rule, AttributeMap attributes,
            BuildConfiguration configuration) {
          return configuration.isCodeCoverageEnabled()
              ? ImmutableList.copyOf(configuration.getGcovLabels())
              : ImmutableList.<Label>of();
        }
      };

  private static final LateBoundLabelList<BuildConfiguration> COVERAGE_REPORT_GENERATOR =
      new LateBoundLabelList<BuildConfiguration>(ImmutableList.of(COVERAGE_SUPPORT_LABEL)) {
        @Override
        public List<Label> resolve(Rule rule, AttributeMap attributes,
            BuildConfiguration configuration) {
          return configuration.isCodeCoverageEnabled()
              ? ImmutableList.copyOf(configuration.getCoverageReportGeneratorLabels())
              : ImmutableList.<Label>of();
        }
      };

  private static final LateBoundLabelList<BuildConfiguration> COVERAGE_SUPPORT =
      new LateBoundLabelList<BuildConfiguration>(ImmutableList.of(COVERAGE_SUPPORT_LABEL)) {
        @Override
        public List<Label> resolve(Rule rule, AttributeMap attributes,
            BuildConfiguration configuration) {
          return configuration.isCodeCoverageEnabled()
              ? ImmutableList.copyOf(configuration.getCoverageLabels())
              : ImmutableList.<Label>of();
        }
      };

  // TODO(bazel-team): Copied from ConfiguredRuleClassProvider for the transition from built-in
  // rules to skylark extensions. Using the same instance would require a large refactoring.
  // If we don't want to support old built-in rules and Skylark simultaneously
  // (except for transition phase) it's probably OK.
  private static final LoadingCache<String, Label> labelCache =
      CacheBuilder.newBuilder().build(new CacheLoader<String, Label>() {
    @Override
    public Label load(String from) throws Exception {
      try {
        return Label.parseAbsolute(from, false);
      } catch (LabelSyntaxException e) {
        throw new Exception(from);
      }
    }
  });

  // TODO(bazel-team): Remove the code duplication (BaseRuleClasses and this class).
  /** Parent rule class for non-executable non-test Skylark rules. */
  public static final RuleClass baseRule =
      BaseRuleClasses.commonCoreAndSkylarkAttributes(
          new RuleClass.Builder("$base_rule", RuleClassType.ABSTRACT, true))
          .add(attr("expect_failure", STRING))
          .build();

  /** Parent rule class for executable non-test Skylark rules. */
  public static final RuleClass binaryBaseRule =
      new RuleClass.Builder("$binary_base_rule", RuleClassType.ABSTRACT, true, baseRule)
          .add(
              attr("args", STRING_LIST)
                  .nonconfigurable("policy decision: should be consistent across configurations"))
          .add(attr("output_licenses", LICENSE))
          .build();

  /** Parent rule class for test Skylark rules. */
  public static final RuleClass getTestBaseRule(String toolsRespository) {
    return new RuleClass.Builder("$test_base_rule", RuleClassType.ABSTRACT, true, baseRule)
        .add(attr("size", STRING).value("medium").taggable()
            .nonconfigurable("used in loading phase rule validation logic"))
        .add(attr("timeout", STRING).taggable()
            .nonconfigurable("used in loading phase rule validation logic").value(
                new Attribute.ComputedDefault() {
                  @Override
                  public Object getDefault(AttributeMap rule) {
                    TestSize size = TestSize.getTestSize(rule.get("size", Type.STRING));
                    if (size != null) {
                      String timeout = size.getDefaultTimeout().toString();
                      if (timeout != null) {
                        return timeout;
                      }
                    }
                    return "illegal";
                  }
                }))
        .add(attr("flaky", BOOLEAN).value(false).taggable()
            .nonconfigurable("taggable - called in Rule.getRuleTags"))
        .add(attr("shard_count", INTEGER).value(-1))
        .add(attr("local", BOOLEAN).value(false).taggable()
            .nonconfigurable("policy decision: this should be consistent across configurations"))
        .add(attr("args", STRING_LIST)
            .nonconfigurable("policy decision: should be consistent across configurations"))
        .add(attr("$test_runtime", LABEL_LIST).cfg(HOST).value(ImmutableList.of(
            labelCache.getUnchecked(toolsRespository + "//tools/test:runtime"))))
        .add(attr(":run_under", LABEL).cfg(DATA).value(RUN_UNDER))
        .add(attr(":gcov", LABEL_LIST).cfg(HOST).value(GCOV))
        .add(attr(":coverage_support", LABEL_LIST).cfg(HOST).value(COVERAGE_SUPPORT))
        .add(
            attr(":coverage_report_generator", LABEL_LIST)
            .cfg(HOST)
            .value(COVERAGE_REPORT_GENERATOR))
        .build();
  }

  /**
   * In native code, private values start with $.
   * In Skylark, private values start with _, because of the grammar.
   */
  public static String attributeToNative(String oldName, Location loc, boolean isLateBound)
      throws EvalException {
    if (oldName.isEmpty()) {
      throw new EvalException(loc, "Attribute name cannot be empty");
    }
    if (isLateBound) {
      if (oldName.charAt(0) != '_') {
        throw new EvalException(loc, "When an attribute value is a function, "
            + "the attribute must be private (start with '_')");
      }
      return ":" + oldName.substring(1);
    }
    if (oldName.charAt(0) == '_') {
      return "$" + oldName.substring(1);
    }
    return oldName;
  }

  // TODO(bazel-team): implement attribute copy and other rule properties
  @SkylarkSignature(name = "rule", doc =
      "Creates a new rule. Store it in a global value, so that it can be loaded and called "
      + "from BUILD files.",
      returnType = BaseFunction.class,
      mandatoryPositionals = {
        @Param(name = "implementation", type = BaseFunction.class,
            doc = "the function implementing this rule, must have exactly one parameter: "
            + "<a href=\"ctx.html\">ctx</a>. The function is called during the analysis phase "
            + "for each instance of the rule. It can access the attributes provided by the user. "
            + "It must create actions to generate all the declared outputs.")
      },
      optionalPositionals = {
        @Param(name = "test", type = Boolean.class, defaultValue = "False",
            doc = "Whether this rule is a test rule. "
            + "If True, the rule must end with <code>_test</code> (otherwise it must not), "
            + "and there must be an action that generates <code>ctx.outputs.executable</code>."),
        @Param(name = "attrs", type = SkylarkDict.class, noneable = true, defaultValue = "None",
            doc =
            "dictionary to declare all the attributes of the rule. It maps from an attribute name "
            + "to an attribute object (see <a href=\"attr.html\">attr</a> module). "
            + "Attributes starting with <code>_</code> are private, and can be used to add "
            + "an implicit dependency on a label. The attribute <code>name</code> is implicitly "
            + "added and must not be specified. Attributes <code>visibility</code>, "
            + "<code>deprecation</code>, <code>tags</code>, <code>testonly</code>, and "
            + "<code>features</code> are implicitly added and might be overriden."),
            // TODO(bazel-team): need to give the types of these builtin attributes
        @Param(name = "outputs", type = SkylarkDict.class, callbackEnabled = true, noneable = true,
            defaultValue = "None", doc = "outputs of this rule. "
            + "It is a dictionary mapping from string to a template name. "
            + "For example: <code>{\"ext\": \"%{name}.ext\"}</code>. <br>"
            + "The dictionary key becomes an attribute in <code>ctx.outputs</code>. "
            // TODO(bazel-team): Make doc more clear, wrt late-bound attributes.
            + "It may also be a function (which receives <code>ctx.attr</code> as argument) "
            + "returning such a dictionary."),
        @Param(name = "executable", type = Boolean.class, defaultValue = "False",
            doc = "whether this rule is marked as executable or not. If True, "
            + "there must be an action that generates <code>ctx.outputs.executable</code>."),
        @Param(name = "output_to_genfiles", type = Boolean.class, defaultValue = "False",
            doc = "If true, the files will be generated in the genfiles directory instead of the "
            + "bin directory. Unless you need it for compatibility with existing rules "
            + "(e.g. when generating header files for C++), do not set this flag."),
        @Param(name = "fragments", type = SkylarkList.class, generic1 = String.class,
            defaultValue = "[]",
            doc = "List of names of configuration fragments that the rule requires "
            + "in target configuration."),
        @Param(name = "host_fragments", type = SkylarkList.class, generic1 = String.class,
            defaultValue = "[]",
            doc = "List of names of configuration fragments that the rule requires "
            + "in host configuration.")},
      useAst = true, useEnvironment = true)
  private static final BuiltinFunction rule = new BuiltinFunction("rule") {
    @SuppressWarnings({"rawtypes", "unchecked"}) // castMap produces
    // an Attribute.Builder instead of a Attribute.Builder<?> but it's OK.
    public BaseFunction invoke(BaseFunction implementation, Boolean test, Object attrs,
        Object implicitOutputs, Boolean executable, Boolean outputToGenfiles, SkylarkList fragments,
        SkylarkList hostFragments, FuncallExpression ast, Environment funcallEnv)
        throws EvalException, ConversionException {
      funcallEnv.checkLoadingPhase("rule", ast.getLocation());
      RuleClassType type = test ? RuleClassType.TEST : RuleClassType.NORMAL;
      RuleClass parent = test ? getTestBaseRule(funcallEnv.getToolsRepository())
          : (executable ? binaryBaseRule : baseRule);

      // We'll set the name later, pass the empty string for now.
      RuleClass.Builder builder = new RuleClass.Builder("", type, true, parent);
      ImmutableList<Pair<String, SkylarkAttr.Descriptor>> attributes =
          attrObjectToAttributesList(attrs, ast);
      if (executable || test) {
        addAttribute(
            ast.getLocation(),
            builder,
            attr("$is_executable", BOOLEAN)
                .value(true)
                .nonconfigurable("Called from RunCommand.isExecutable, which takes a Target")
                .build());
        builder.setOutputsDefaultExecutable();
      }

      if (implicitOutputs != Runtime.NONE) {
        if (implicitOutputs instanceof BaseFunction) {
          BaseFunction func = (BaseFunction) implicitOutputs;
          SkylarkCallbackFunction callback = new SkylarkCallbackFunction(func, ast, funcallEnv);
          builder.setImplicitOutputsFunction(
              new SkylarkImplicitOutputsFunctionWithCallback(callback, ast.getLocation()));
        } else {
          builder.setImplicitOutputsFunction(
              new SkylarkImplicitOutputsFunctionWithMap(
                  ImmutableMap.copyOf(
                      castMap(
                          implicitOutputs,
                          String.class,
                          String.class,
                          "implicit outputs of the rule class"))));
        }
      }

      if (outputToGenfiles) {
        builder.setOutputToGenfiles();
      }

      builder.requiresConfigurationFragmentsBySkylarkModuleName(
          fragments.getContents(String.class, "fragments"));
      builder.requiresHostConfigurationFragmentsBySkylarkModuleName(
          hostFragments.getContents(String.class, "host_fragments"));
      builder.setConfiguredTargetFunction(implementation);
      builder.setRuleDefinitionEnvironment(funcallEnv);
      return new RuleFunction(builder, type, attributes, ast.getLocation());
    }
    };

  protected static ImmutableList<Pair<String, Descriptor>> attrObjectToAttributesList(
      Object attrs, FuncallExpression ast) throws EvalException {
    ImmutableList.Builder<Pair<String, Descriptor>> attributes = ImmutableList.builder();

    if (attrs != Runtime.NONE) {
      for (Map.Entry<String, Descriptor> attr :
          castMap(attrs, String.class, Descriptor.class, "attrs").entrySet()) {
        Descriptor attrDescriptor = attr.getValue();
        String attrName =
            attributeToNative(
                attr.getKey(),
                ast.getLocation(),
                attrDescriptor.getAttributeBuilder().hasLateBoundValue());
        attributes.add(Pair.of(attrName, attrDescriptor));
      }
    }
    return attributes.build();
  }

  private static void addAttribute(
      Location location, RuleClass.Builder builder, Attribute attribute) throws EvalException {
    try {
      builder.addOrOverrideAttribute(attribute);
    } catch (IllegalArgumentException ex) {
      throw new EvalException(location, ex);
    }
  }


  @SkylarkSignature(name = "aspect", doc =
    "Creates a new aspect. The result of this function must be stored in a global value.",
    returnType = SkylarkAspect.class,
    mandatoryPositionals = {
        @Param(name = "implementation", type = BaseFunction.class,
            doc = "the function implementing this aspect. Must have two parameters: "
            + "<a href=\"Target.html\">Target</a> (the target to which the aspect is applied) and "
            + "<a href=\"ctx.html\">ctx</a>. Attributes of the target are available via ctx.rule "
            + " field. The function is called during the analysis phase for each application of "
            + "an aspect to a target."
        ),
    },
    optionalPositionals = {
      @Param(name = "attr_aspects", type = SkylarkList.class, generic1 = String.class,
        defaultValue = "[]",
        doc = "List of attribute names.  The aspect propagates along dependencies specified by "
        + " attributes of a target with this name"
      ),
      @Param(name = "attrs", type = SkylarkDict.class, noneable = true, defaultValue = "None",
        doc = "dictionary to declare all the attributes of the aspect.  "
        + "It maps from an attribute name to an attribute object "
        + "(see <a href=\"attr.html\">attr</a> module). "
        + "Aspect attributes are available to implementation function as fields of ctx parameter. "
        + "Implicit attributes starting with <code>_</code> must have default values, and have "
        + "type <code>label</code> or <code>label_list</code>."
        + "Explicit attributes must have type <code>string</code>, and must use the "
        + "<code>values</code> restriction. If explicit attributes are present, the aspect can "
        + "only be used with rules that have attributes of the same name and type, with valid "
        + "values. "
      ),
      @Param(
        name = "fragments",
        type = SkylarkList.class,
        generic1 = String.class,
        defaultValue = "[]",
        doc =
            "List of names of configuration fragments that the aspect requires "
                + "in target configuration."
      ),
      @Param(
        name = "host_fragments",
        type = SkylarkList.class,
        generic1 = String.class,
        defaultValue = "[]",
        doc =
            "List of names of configuration fragments that the aspect requires "
                + "in host configuration."
      )
    },
    useEnvironment = true,
    useAst = true
  )
  private static final BuiltinFunction aspect =
      new BuiltinFunction("aspect") {
        public SkylarkAspect invoke(
            BaseFunction implementation,
            SkylarkList attributeAspects,
            Object attrs,
            SkylarkList fragments,
            SkylarkList hostFragments,
            FuncallExpression ast,
            Environment funcallEnv)
            throws EvalException {
          ImmutableList.Builder<String> attrAspects = ImmutableList.builder();
          for (Object attributeAspect : attributeAspects) {
            String attrName = STRING.convert(attributeAspect, "attr_aspects");
            if (!attrName.startsWith("_")) {
              attrAspects.add(attrName);
            } else  {
              // Implicit attribute names mean ether implicit or late-bound attributes
              // (``$attr`` or ``:attr``). Depend on both.
              attrAspects.add(attributeToNative(attrName, location, false));
              attrAspects.add(attributeToNative(attrName, location, true));
            }
          }

          ImmutableList<Pair<String, SkylarkAttr.Descriptor>> descriptors =
              attrObjectToAttributesList(attrs, ast);
          ImmutableList.Builder<Attribute> attributes = ImmutableList.builder();
          ImmutableSet.Builder<String> requiredParams = ImmutableSet.<String>builder();
          for (Pair<String, Descriptor> descriptor : descriptors) {
            String nativeName = descriptor.getFirst();
            boolean hasDefault = descriptor.getSecond().getAttributeBuilder().isValueSet();
            Attribute attribute = descriptor.second.getAttributeBuilder().build(descriptor.first);
            if (attribute.getType() == Type.STRING
                && ((String) attribute.getDefaultValue(null)).isEmpty()) {
              hasDefault = false;  // isValueSet() is always true for attr.string.
            }
            if (!Attribute.isImplicit(nativeName)) {
              if (!attribute.checkAllowedValues() || attribute.getType() != Type.STRING) {
                throw new EvalException(
                    ast.getLocation(),
                    String.format(
                        "Aspect parameter attribute '%s' must have type 'string' and use the "
                        + "'values' restriction.",
                        nativeName));
              }
              if (!hasDefault) {
                requiredParams.add(nativeName);
              } else {
                PredicateWithMessage<Object> allowed = attribute.getAllowedValues();
                Object defaultVal = attribute.getDefaultValue(null);
                if (!allowed.apply(defaultVal)) {
                  throw new EvalException(
                      ast.getLocation(),
                      String.format(
                          "Aspect parameter attribute '%s' has a bad default value: %s",
                          nativeName,
                          allowed.getErrorReason(defaultVal)));
                }
              }
            } else if (!hasDefault) {  // Implicit attribute
              String skylarkName = "_" + nativeName.substring(1);
              throw new EvalException(
                  ast.getLocation(),
                  String.format("Aspect attribute '%s' has no default value.", skylarkName));
            }
            attributes.add(attribute);
          }

          return new SkylarkAspect(
              implementation,
              attrAspects.build(),
              attributes.build(),
              requiredParams.build(),
              ImmutableSet.copyOf(fragments.getContents(String.class, "fragments")),
              ImmutableSet.copyOf(hostFragments.getContents(String.class, "host_fragments")),
              funcallEnv);
        }
      };


  /** The implementation for the magic function "rule" that creates Skylark rule classes */
  public static final class RuleFunction extends BaseFunction {
    private RuleClass.Builder builder;

    private RuleClass ruleClass;
    private final RuleClassType type;
    private ImmutableList<Pair<String, SkylarkAttr.Descriptor>> attributes;
    private final Location definitionLocation;
    private Label skylarkLabel;

    public RuleFunction(Builder builder, RuleClassType type,
        ImmutableList<Pair<String, SkylarkAttr.Descriptor>> attributes,
        Location definitionLocation) {
      super("rule", FunctionSignature.KWARGS);
      this.builder = builder;
      this.type = type;
      this.attributes = attributes;
      this.definitionLocation = definitionLocation;
    }

    @Override
    @SuppressWarnings("unchecked") // the magic hidden $pkg_context variable is guaranteed
    // to be a PackageContext
    public Object call(Object[] args, FuncallExpression ast, Environment env)
        throws EvalException, InterruptedException, ConversionException {
      env.checkLoadingPhase(getName(), ast.getLocation());
      try {
        if (ruleClass == null) {
          throw new EvalException(ast.getLocation(),
              "Invalid rule class hasn't been exported by a Skylark file");
        }

        for (Attribute attribute : ruleClass.getAttributes()) {
          // TODO(dslomov): If a Skylark parameter extractor is specified for this aspect, its
          // attributes may not be required.
          for (Map.Entry<String, ImmutableSet<String>> attrRequirements :
               attribute.getRequiredAspectParameters().entrySet()) {
            for (String required : attrRequirements.getValue()) {
              if (!ruleClass.hasAttr(required, Type.STRING)) {
                throw new EvalException(definitionLocation, String.format(
                    "Aspect %s requires rule %s to specify attribute '%s' with type string.",
                    attrRequirements.getKey(),
                    ruleClass.getName(),
                    required));
              }
            }
          }
        }

        PackageContext pkgContext = (PackageContext) env.lookup(PackageFactory.PKG_CONTEXT);
        BuildLangTypedAttributeValuesMap attributeValues =
            new BuildLangTypedAttributeValuesMap((Map<String, Object>) args[0]);
        return RuleFactory.createAndAddRule(pkgContext, ruleClass, attributeValues, ast, env);
      } catch (InvalidRuleException | NameConflictException | NoSuchVariableException e) {
        throw new EvalException(ast.getLocation(), e.getMessage());
      }
    }

    /**
     * Export a RuleFunction from a Skylark file with a given name.
     */
    void export(Label skylarkLabel, String ruleClassName) throws EvalException {
      Preconditions.checkState(ruleClass == null && builder != null);
      this.skylarkLabel = skylarkLabel;
      if (type == RuleClassType.TEST != TargetUtils.isTestRuleName(ruleClassName)) {
        throw new EvalException(definitionLocation, "Invalid rule class name '" + ruleClassName
            + "', test rule class names must end with '_test' and other rule classes must not");
      }
      for (Pair<String, SkylarkAttr.Descriptor> attribute : attributes) {
        SkylarkAttr.Descriptor descriptor = attribute.getSecond();
        Attribute.Builder<?> attributeBuilder = descriptor.getAttributeBuilder();
        for (SkylarkAspect skylarkAspect : descriptor.getAspects()) {
          if (!skylarkAspect.isExported()) {
            throw new EvalException(definitionLocation,
                "All aspects applied to rule dependencies must be top-level values");
          }
          attributeBuilder.aspect(skylarkAspect);
        }

        addAttribute(definitionLocation, builder,
            descriptor.getAttributeBuilder().build(attribute.getFirst()));
      }
      this.ruleClass = builder.build(ruleClassName);

      this.builder = null;
      this.attributes = null;
    }

    @VisibleForTesting
    public RuleClass getRuleClass() {
      Preconditions.checkState(ruleClass != null && builder == null);
      return ruleClass;
    }
  }

  public static void exportRuleFunctionsAndAspects(Environment env, Label skylarkLabel)
      throws EvalException {
    Set<String> globalNames = env.getGlobals().getDirectVariableNames();

    // Export aspects first since rules can depend on aspects.
    for (String name : globalNames) {
      Object value;
      try {
        value = env.lookup(name);
      } catch (NoSuchVariableException e) {
        throw new AssertionError(e);
      }
      if (value instanceof SkylarkAspect) {
        SkylarkAspect skylarkAspect = (SkylarkAspect) value;
        if (!skylarkAspect.isExported()) {
          skylarkAspect.export(skylarkLabel, name);
        }
      }
    }

    for (String name : globalNames) {
      try {
        Object value = env.lookup(name);
        if (value instanceof RuleFunction) {
          RuleFunction function = (RuleFunction) value;
          if (function.skylarkLabel == null) {
            function.export(skylarkLabel, name);
          }
        }
      } catch (NoSuchVariableException e) {
        throw new AssertionError(e);
      }
    }
  }

  @SkylarkSignature(name = "Label", doc = "Creates a Label referring to a BUILD target. Use "
      + "this function only when you want to give a default value for the label attributes. "
      + "The argument must refer to an absolute label. "
      + "Example: <br><pre class=language-python>Label(\"//tools:default\")</pre>",
      returnType = Label.class,
      mandatoryPositionals = {@Param(name = "label_string", type = String.class,
          doc = "the label string")},
      optionalNamedOnly = {@Param(
          name = "relative_to_caller_repository",
          type = Boolean.class,
          defaultValue = "False",
          doc = "whether the label should be resolved relative to the label of the file this "
              + "function is called from.")},
      useLocation = true,
      useEnvironment = true)
  private static final BuiltinFunction label = new BuiltinFunction("Label") {
      @SuppressWarnings({"unchecked", "unused"})
      public Label invoke(
          String labelString, Boolean relativeToCallerRepository, Location loc, Environment env)
          throws EvalException {
        Label parentLabel = null;
        if (relativeToCallerRepository) {
          parentLabel = env.getCallerLabel();
        } else {
          parentLabel = env.getGlobals().label();
        }
        try {
          if (parentLabel != null) {
            LabelValidator.parseAbsoluteLabel(labelString);
            labelString = parentLabel.getRelative(labelString)
                .getUnambiguousCanonicalForm();
          }
          return labelCache.get(labelString);
        } catch (LabelValidator.BadLabelException | LabelSyntaxException | ExecutionException e) {
          throw new EvalException(loc, "Illegal absolute label syntax: " + labelString);
        }
      }
    };

  @SkylarkSignature(name = "FileType",
      doc = "Creates a file filter from a list of strings. For example, to match files ending "
      + "with .cc or .cpp, use: <pre class=language-python>FileType([\".cc\", \".cpp\"])</pre>",
      returnType = SkylarkFileType.class,
      mandatoryPositionals = {
      @Param(name = "types", type = SkylarkList.class, generic1 = String.class, defaultValue = "[]",
          doc = "a list of the accepted file extensions")})
  private static final BuiltinFunction fileType = new BuiltinFunction("FileType") {
      public SkylarkFileType invoke(SkylarkList types) throws EvalException {
        return SkylarkFileType.of(types.getContents(String.class, "types"));
      }
    };

  @SkylarkSignature(name = "to_proto",
      doc = "Creates a text message from the struct parameter. This method only works if all "
          + "struct elements (recursively) are strings, ints, booleans, other structs or a "
          + "list of these types. Quotes and new lines in strings are escaped. "
          + "Examples:<br><pre class=language-python>"
          + "struct(key=123).to_proto()\n# key: 123\n\n"
          + "struct(key=True).to_proto()\n# key: true\n\n"
          + "struct(key=[1, 2, 3]).to_proto()\n# key: 1\n# key: 2\n# key: 3\n\n"
          + "struct(key='text').to_proto()\n# key: \"text\"\n\n"
          + "struct(key=struct(inner_key='text')).to_proto()\n"
          + "# key {\n#   inner_key: \"text\"\n# }\n\n"
          + "struct(key=[struct(inner_key=1), struct(inner_key=2)]).to_proto()\n"
          + "# key {\n#   inner_key: 1\n# }\n# key {\n#   inner_key: 2\n# }\n\n"
          + "struct(key=struct(inner_key=struct(inner_inner_key='text'))).to_proto()\n"
          + "# key {\n#    inner_key {\n#     inner_inner_key: \"text\"\n#   }\n# }\n</pre>",
      objectType = SkylarkClassObject.class, returnType = String.class,
      mandatoryPositionals = {
        // TODO(bazel-team): shouldn't we accept any ClassObject?
        @Param(name = "self", type = SkylarkClassObject.class,
            doc = "this struct")},
      useLocation = true)
  private static final BuiltinFunction toProto = new BuiltinFunction("to_proto") {
      public String invoke(SkylarkClassObject self, Location loc) throws EvalException {
        StringBuilder sb = new StringBuilder();
        printTextMessage(self, sb, 0, loc);
        return sb.toString();
      }

      private void printTextMessage(ClassObject object, StringBuilder sb,
          int indent, Location loc) throws EvalException {
        for (String key : object.getKeys()) {
          printTextMessage(key, object.getValue(key), sb, indent, loc);
        }
      }

      private void printSimpleTextMessage(String key, Object value, StringBuilder sb,
          int indent, Location loc, String container) throws EvalException {
        if (value instanceof ClassObject) {
          print(sb, key + " {", indent);
          printTextMessage((ClassObject) value, sb, indent + 1, loc);
          print(sb, "}", indent);
        } else if (value instanceof String) {
          print(sb, key + ": \"" + escapeString((String) value) + "\"", indent);
        } else if (value instanceof Integer) {
          print(sb, key + ": " + value, indent);
        } else if (value instanceof Boolean) {
          // We're relying on the fact that Java converts Booleans to Strings in the same way
          // as the protocol buffers do.
          print(sb, key + ": " + value, indent);
        } else {
          throw new EvalException(loc,
              "Invalid text format, expected a struct, a string, a bool, or an int but got a "
              + EvalUtils.getDataTypeName(value) + " for " + container + " '" + key + "'");
        }
      }

      private void printTextMessage(String key, Object value, StringBuilder sb,
          int indent, Location loc) throws EvalException {
        if (value instanceof SkylarkList) {
          for (Object item : ((SkylarkList) value)) {
            // TODO(bazel-team): There should be some constraint on the fields of the structs
            // in the same list but we ignore that for now.
            printSimpleTextMessage(key, item, sb, indent, loc, "list element in struct field");
          }
        } else {
          printSimpleTextMessage(key, value, sb, indent, loc, "struct field");
        }
      }

      private void print(StringBuilder sb, String text, int indent) {
        for (int i = 0; i < indent; i++) {
          sb.append("  ");
        }
      sb.append(text);
      sb.append("\n");
      }
    };

  // Escapes the given string for use in Proto messages or JSON strings.
  private static String escapeString(String string) {
    // TODO(bazel-team): use guava's SourceCodeEscapers when it's released.
    return string.replace("\"", "\\\"").replace("\n", "\\n");
  }

  @SkylarkSignature(name = "to_json",
      doc = "Creates a JSON string from the struct parameter. This method only works if all "
          + "struct elements (recursively) are strings, ints, booleans, other structs or a "
          + "list of these types. Quotes and new lines in strings are escaped. "
          + "Examples:<br><pre class=language-python>"
          + "struct(key=123).to_json()\n# {\"key\":123}\n\n"
          + "struct(key=True).to_json()\n# {\"key\":true}\n\n"
          + "struct(key=[1, 2, 3]).to_json()\n# {\"key\":[1,2,3]}\n\n"
          + "struct(key='text').to_json()\n# {\"key\":\"text\"}\n\n"
          + "struct(key=struct(inner_key='text')).to_json()\n"
          + "# {\"key\":{\"inner_key\":\"text\"}}\n\n"
          + "struct(key=[struct(inner_key=1), struct(inner_key=2)]).to_json()\n"
          + "# {\"key\":[{\"inner_key\":1},{\"inner_key\":2}]}\n\n"
          + "struct(key=struct(inner_key=struct(inner_inner_key='text'))).to_json()\n"
          + "# {\"key\":{\"inner_key\":{\"inner_inner_key\":\"text\"}}}\n</pre>",
      objectType = SkylarkClassObject.class, returnType = String.class,
      mandatoryPositionals = {
          // TODO(bazel-team): shouldn't we accept any ClassObject?
          @Param(name = "self", type = SkylarkClassObject.class,
              doc = "this struct")},
      useLocation = true)
  private static final BuiltinFunction toJson = new BuiltinFunction("to_json") {
    public String invoke(SkylarkClassObject self, Location loc) throws EvalException {
      StringBuilder sb = new StringBuilder();
      printJson(self, sb, loc, "struct field", null);
      return sb.toString();
    }

    private void printJson(Object value, StringBuilder sb, Location loc, String container,
        String key) throws EvalException {
      if (value == Runtime.NONE) {
        sb.append("null");
      } else if (value instanceof ClassObject) {
        sb.append("{");

        String join = "";
        for (String subKey : ((ClassObject) value).getKeys()) {
          sb.append(join);
          join = ",";
          sb.append("\"");
          sb.append(subKey);
          sb.append("\":");
          printJson(((ClassObject) value).getValue(subKey), sb, loc, "struct field", subKey);
        }
        sb.append("}");
      } else if (value instanceof List) {
        sb.append("[");
        String join = "";
        for (Object item : ((List) value)) {
          sb.append(join);
          join = ",";
          printJson(item, sb, loc, "list element in struct field", key);
        }
        sb.append("]");
      } else if (value instanceof String) {
        sb.append("\"");
        sb.append(jsonEscapeString((String) value));
        sb.append("\"");
      } else if (value instanceof Integer || value instanceof Boolean) {
        sb.append(value);
      } else {
        String errorMessage = "Invalid text format, expected a struct, a string, a bool, or an int "
            + "but got a " + EvalUtils.getDataTypeName(value) + " for " + container;
        if (key != null) {
          errorMessage += " '" + key + "'";
        }
        throw new EvalException(loc, errorMessage);
      }
    }

    private String jsonEscapeString(String string) {
      return escapeString(string.replace("\\", "\\\\")
          .replace("\r", "\\r")
          .replace("\t", "\\t"));
    }
  };

  @SkylarkSignature(name = "output_group",
      documented = false, //  TODO(dslomov): document.
      objectType =  TransitiveInfoCollection.class,
      returnType = SkylarkNestedSet.class,
      mandatoryPositionals = {
          @Param(name = "self", type = TransitiveInfoCollection.class, doc =
              "this target"
          ),
          @Param(name = "group_name", type = String.class, doc =
              "Output group name"
          )
      }
  )
  public static final BuiltinFunction output_group = new BuiltinFunction("output_group") {
      public SkylarkNestedSet invoke(TransitiveInfoCollection self, String group) {
        OutputGroupProvider provider = self.getProvider(OutputGroupProvider.class);
        NestedSet<Artifact> result = provider != null
            ? provider.getOutputGroup(group)
            : NestedSetBuilder.<Artifact>emptySet(Order.STABLE_ORDER);
        return SkylarkNestedSet.of(Artifact.class, result);
      }
  };

  static {
    SkylarkSignatureProcessor.configureSkylarkFunctions(SkylarkRuleClassFunctions.class);
  }
}
