/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.J_PARAM_SPEC;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PARAM_SPEC_ARGS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PARAM_SPEC_KWARGS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPE_ALIAS_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPE_VAR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPE_VAR_TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.J__TYPING;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPING;
import static com.oracle.graal.python.nodes.ErrorMessages.A_SINGLE_CONSTRAINT_IS_NOT_ALLOWED;
import static com.oracle.graal.python.nodes.ErrorMessages.BIVARIANT_TYPES_ARE_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.BOUND_MUST_BE_A_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.CONSTRAINTS_CANNOT_BE_COMBINED_WITH_BOUND;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_PARAMS_MUST_BE_A_TUPLE;
import static com.oracle.graal.python.nodes.ErrorMessages.VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_GET;
import static com.oracle.graal.python.util.PythonUtils.arraycopy;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltinsClinicProviders.ParamSpecNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltinsClinicProviders.TypeAliasTypeNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltinsClinicProviders.TypeVarNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltinsClinicProviders.TypeVarTupleNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins.GetGlobalsNode;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.typing.PParamSpec;
import com.oracle.graal.python.builtins.objects.typing.PParamSpecArgs;
import com.oracle.graal.python.builtins.objects.typing.PParamSpecKwargs;
import com.oracle.graal.python.builtins.objects.typing.PTypeAliasType;
import com.oracle.graal.python.builtins.objects.typing.PTypeVar;
import com.oracle.graal.python.builtins.objects.typing.PTypeVarTuple;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__TYPING)
public class TypingModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypingModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("Generic", PythonBuiltinClassType.PGeneric);
    }

    @Builtin(name = "_idfunc", minNumOfPositionalArgs = 1, parameterNames = {"x"})
    @GenerateNodeFactory
    abstract static class IdFuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(Object x) {
            return x;
        }
    }

    @Builtin(name = J_TYPE_VAR, minNumOfPositionalArgs = 2, takesVarArgs = true, parameterNames = {"$cls", "name"}, keywordOnlyNames = {"bound", "covariant",
                    "contravariant", "infer_variance"}, constructsClass = PythonBuiltinClassType.PTypeVar, needsFrame = true, alwaysNeedsCallerFrame = true, doc = """
                                    Type variable.

                                    The preferred way to construct a type variable is via the dedicated
                                    syntax for generic functions, classes, and type aliases::

                                        class Sequence[T]:  # T is a TypeVar
                                            ...

                                    This syntax can also be used to create bound and constrained type
                                    variables::

                                        # S is a TypeVar bound to str
                                        class StrSequence[S: str]:
                                            ...

                                        # A is a TypeVar constrained to str or bytes
                                        class StrOrBytesSequence[A: (str, bytes)]:
                                            ...

                                    However, if desired, reusable type variables can also be constructed
                                    manually, like so::

                                       T = TypeVar('T')  # Can be anything
                                       S = TypeVar('S', bound=str)  # Can be any subtype of str
                                       A = TypeVar('A', str, bytes)  # Must be exactly str or bytes

                                    Type variables exist primarily for the benefit of static type
                                    checkers.  They serve as the parameters for generic types as well
                                    as for generic function and type alias definitions.

                                    The variance of type variables is inferred by type checkers when they
                                    are created through the type parameter syntax and when
                                    ``infer_variance=True`` is passed. Manually created type variables may
                                    be explicitly marked covariant or contravariant by passing
                                    ``covariant=True`` or ``contravariant=True``. By default, manually
                                    created type variables are invariant. See PEP 484 and PEP 695 for more
                                    details.
                                    """)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "covariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "contravariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "infer_variance", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class TypeVarNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TypeVarNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTypeVar newTypeVar(VirtualFrame frame, Object cls, TruffleString name, Object[] constraints, Object bound, boolean covariant, boolean contravariant, boolean inferVariance,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CheckBoundNode checkBoundNode,
                        @Cached CallerNode callerNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectSetAttr setAttrNode) {
            if (covariant && contravariant) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, BIVARIANT_TYPES_ARE_NOT_SUPPORTED);
            }

            if (inferVariance && (covariant || contravariant)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE);
            }

            Object boundChecked = checkBoundNode.execute(frame, inliningTarget, bound);
            Object constraintsTuple;

            if (constraints.length == 1) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, A_SINGLE_CONSTRAINT_IS_NOT_ALLOWED);
            } else if (constraints.length == 0) {
                constraintsTuple = PFactory.createEmptyTuple(language);
            } else if (boundChecked != PNone.NONE) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, CONSTRAINTS_CANNOT_BE_COMBINED_WITH_BOUND);
            } else {
                constraintsTuple = PFactory.createTuple(language, constraints);
            }
            Object module = callerNode.execute(frame, inliningTarget);

            PTypeVar result = PFactory.createTypeVar(language, cls, getInstanceShape.execute(cls), name, boundChecked, null, constraintsTuple, null, covariant, contravariant, inferVariance);
            setAttrNode.execute(frame, inliningTarget, result, T___MODULE__, module);
            return result;
        }
    }

    @Builtin(name = J_TYPE_VAR_TUPLE, minNumOfPositionalArgs = 2, parameterNames = {"$cls",
                    "name"}, constructsClass = PythonBuiltinClassType.PTypeVarTuple, needsFrame = true, alwaysNeedsCallerFrame = true, doc = """
                                    Type variable tuple. A specialized form of type variable that enables
                                    variadic generics.

                                    The preferred way to construct a type variable tuple is via the
                                    dedicated syntax for generic functions, classes, and type aliases,
                                    where a single '*' indicates a type variable tuple::

                                        def move_first_element_to_last[T, *Ts](tup: tuple[T, *Ts]) -> tuple[*Ts, T]:
                                            return (*tup[1:], tup[0])

                                    For compatibility with Python 3.11 and earlier, TypeVarTuple objects
                                    can also be created as follows::

                                        Ts = TypeVarTuple('Ts')  # Can be given any name

                                    Just as a TypeVar (type variable) is a placeholder for a single type,
                                    a TypeVarTuple is a placeholder for an *arbitrary* number of types. For
                                    example, if we define a generic class using a TypeVarTuple::

                                        class C[*Ts]: ...

                                    Then we can parameterize that class with an arbitrary number of type
                                    arguments::

                                        C[int]       # Fine
                                        C[int, str]  # Also fine
                                        C[()]        # Even this is fine

                                    For more details, see PEP 646.

                                    Note that only TypeVarTuples defined in the global scope can be
                                    pickled.
                                    """)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class TypeVarTupleNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TypeVarTupleNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTypeVarTuple newTypeVarTuple(VirtualFrame frame, Object cls, TruffleString name,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CallerNode callerNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyObjectSetAttr setAttrNode) {
            Object module = callerNode.execute(frame, inliningTarget);
            PTypeVarTuple result = PFactory.createTypeVarTuple(language, cls, getInstanceShape.execute(cls), name);
            setAttrNode.execute(frame, inliningTarget, result, T___MODULE__, module);
            return result;
        }
    }

    @Builtin(name = J_PARAM_SPEC, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "name"}, keywordOnlyNames = {"bound", "covariant",
                    "contravariant", "infer_variance"}, constructsClass = PythonBuiltinClassType.PParamSpec, needsFrame = true, alwaysNeedsCallerFrame = true, doc = """
                                    Parameter specification variable.

                                    The preferred way to construct a parameter specification is via the
                                    dedicated syntax for generic functions, classes, and type aliases,
                                    where the use of '**' creates a parameter specification::

                                        type IntFunc[**P] = Callable[P, int]

                                    For compatibility with Python 3.11 and earlier, ParamSpec objects
                                    can also be created as follows::

                                        P = ParamSpec('P')

                                    Parameter specification variables exist primarily for the benefit of
                                    static type checkers.  They are used to forward the parameter types of
                                    one callable to another callable, a pattern commonly found in
                                    higher-order functions and decorators.  They are only valid when used
                                    in ``Concatenate``, or as the first argument to ``Callable``, or as
                                    parameters for user-defined Generics. See class Generic for more
                                    information on generic types.

                                    An example for annotating a decorator::

                                        def add_logging[**P, T](f: Callable[P, T]) -> Callable[P, T]:
                                            '''A type-safe decorator to add logging to a function.'''
                                            def inner(*args: P.args, **kwargs: P.kwargs) -> T:
                                                logging.info(f'{f.__name__} was called')
                                                return f(*args, **kwargs)
                                            return inner

                                        @add_logging
                                        def add_two(x: float, y: float) -> float:
                                            '''Add two numbers together.'''
                                            return x + y

                                    Parameter specification variables can be introspected. e.g.::

                                        >>> P = ParamSpec("P")
                                        >>> P.__name__
                                        'P'

                                    Note that only parameter specification variables defined in the global
                                    scope can be pickled.
                                    """)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "covariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "contravariant", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "infer_variance", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class ParamSpecNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ParamSpecNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PParamSpec newParamSpec(VirtualFrame frame, Object cls, TruffleString name, Object bound, boolean covariant, boolean contravariant, boolean inferVariance,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CheckBoundNode checkBoundNode,
                        @Cached CallerNode callerNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyObjectSetAttr setAttrNode) {
            if (covariant && contravariant) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, BIVARIANT_TYPES_ARE_NOT_SUPPORTED);
            }

            if (inferVariance && (covariant || contravariant)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, VARIANCE_CANNOT_BE_SPECIFIED_WITH_INFER_VARIANCE);
            }

            Object boundChecked = checkBoundNode.execute(frame, inliningTarget, bound);

            Object module = callerNode.execute(frame, inliningTarget);

            PParamSpec result = PFactory.createParamSpec(language, cls, getInstanceShape.execute(cls), name, boundChecked, covariant, contravariant, inferVariance);
            setAttrNode.execute(frame, inliningTarget, result, T___MODULE__, module);
            return result;
        }
    }

    @Builtin(name = J_PARAM_SPEC_ARGS, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "origin"}, constructsClass = PythonBuiltinClassType.PParamSpecArgs, doc = """
                    The args for a ParamSpec object.

                    Given a ParamSpec object P, P.args is an instance of ParamSpecArgs.

                    ParamSpecArgs objects have a reference back to their ParamSpec::

                        >>> P = ParamSpec("P")
                        >>> P.args.__origin__ is P
                        True

                    This type is meant for runtime introspection and has no special meaning
                    to static type checkers.
                    """)
    @GenerateNodeFactory
    abstract static class ParamSpecArgsNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PParamSpecArgs newParamSpecArgs(Object cls, Object origin,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createParamSpecArgs(language, cls, getInstanceShape.execute(cls), origin);
        }
    }

    @Builtin(name = J_PARAM_SPEC_KWARGS, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "origin"}, constructsClass = PythonBuiltinClassType.PParamSpecKwargs, doc = """
                    The kwargs for a ParamSpec object.

                    Given a ParamSpec object P, P.kwargs is an instance of ParamSpecKwargs.

                    ParamSpecKwargs objects have a reference back to their ParamSpec::

                        >>> P = ParamSpec("P")
                        >>> P.kwargs.__origin__ is P
                        True

                    This type is meant for runtime introspection and has no special meaning
                    to static type checkers.
                    """)
    @GenerateNodeFactory
    abstract static class ParamSpecKwargsNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PParamSpecKwargs newParamSpecKwargs(Object cls, Object origin,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createParamSpecKwargs(language, cls, getInstanceShape.execute(cls), origin);
        }
    }

    @Builtin(name = J_TYPE_ALIAS_TYPE, minNumOfPositionalArgs = 3, parameterNames = {"$cls", "name", "value"}, keywordOnlyNames = {
                    "type_params"}, constructsClass = PythonBuiltinClassType.PTypeAliasType, needsFrame = true, alwaysNeedsCallerFrame = true, doc = """
                                    Type alias.

                                    Type aliases are created through the type statement::

                                        type Alias = int

                                    In this example, Alias and int will be treated equivalently by static
                                    type checkers.

                                    At runtime, Alias is an instance of TypeAliasType. The __name__
                                    attribute holds the name of the type alias. The value of the type alias
                                    is stored in the __value__ attribute. It is evaluated lazily, so the
                                    value is computed only if the attribute is accessed.

                                    Type aliases can also be generic::

                                        type ListOrSet[T] = list[T] | set[T]

                                    In this case, the type parameters of the alias are stored in the
                                    __type_params__ attribute.

                                    See PEP 695 for more information.
                                    """)
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class TypeAliasTypeNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TypeAliasTypeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTypeAliasType newTypeAliasType(VirtualFrame frame, Object cls, TruffleString name, Object value, Object typeParams,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CheckTypeParamsNode checkNode,
                        @Cached CallerNode callerNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PTuple typeParamsTuple = checkNode.execute(inliningTarget, typeParams);
            Object module = callerNode.execute(frame, inliningTarget);
            return PFactory.createTypeAliasType(language, cls, getInstanceShape.execute(cls), name, typeParamsTuple, null, value, module);
        }

        @GenerateInline
        @GenerateCached(false)
        @ImportStatic(PGuards.class)
        abstract static class CheckTypeParamsNode extends Node {
            abstract PTuple execute(Node inliningTarget, Object o);

            @Specialization(guards = "isNoValue(o)")
            static PTuple doDefault(@SuppressWarnings("unused") Object o) {
                return null;
            }

            @Specialization
            static PTuple doTuple(PTuple o) {
                return o.getSequenceStorage().length() == 0 ? null : o;
            }

            @Fallback
            static PTuple doError(@SuppressWarnings("unused") Object o,
                            @Bind("this") Node inliningTarget) {
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, TYPE_PARAMS_MUST_BE_A_TUPLE);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CheckBoundNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object bound);

        @Specialization
        static Object none(@SuppressWarnings("unused") PNone bound) {
            return PNone.NONE;
        }

        @Fallback
        static Object check(VirtualFrame frame, Node inliningTarget, Object bound,
                        @Cached TypeCheckNode typeCheckNode) {
            return typeCheckNode.execute(frame, inliningTarget, bound, BOUND_MUST_BE_A_TYPE);
        }
    }

    /**
     * Equivalent of {@code type_check} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class TypeCheckNode extends Node {
        private static final TruffleString T__TYPE_CHECK = tsLiteral("_type_check");

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object arg, TruffleString msg);

        @Specialization
        static Object none(@SuppressWarnings("unused") PNone arg, @SuppressWarnings("unused") TruffleString msg) {
            // Calling typing.py here leads to bootstrapping problems
            return PNone.NONE;
        }

        @Fallback
        static Object check(VirtualFrame frame, Node inliningTarget, Object arg, TruffleString msg,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T__TYPE_CHECK, arg, msg);
        }
    }

    /**
     * Equivalent of {@code call_typing_func_object} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallTypingFuncObjectNode extends Node {
        abstract Object executeInternal(VirtualFrame frame, Node inliningTarget, TruffleString name, Object[] args);

        public final Object execute(VirtualFrame frame, Node inliningTarget, TruffleString name, Object... args) {
            return executeInternal(frame, inliningTarget, name, args);
        }

        @Specialization
        static Object doCall(VirtualFrame frame, Node inliningTarget, TruffleString name, Object[] args,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached(inline = false) CallNode callNode) {
            PythonModule typing = AbstractImportNode.importModule(T_TYPING);
            Object func = getAttrNode.execute(frame, inliningTarget, typing, name);
            return callNode.execute(frame, func, args);
        }
    }

    /**
     * Equivalent of {@code call_typing_args_kwargs} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CallTypingArgsKwargsNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TruffleString name, Object cls, Object[] args, PKeyword[] keywords);

        @Specialization
        static Object doCall(VirtualFrame frame, Node inliningTarget, TruffleString name, Object cls, Object[] args, PKeyword[] keywords,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached(inline = false) CallNode callNode) {
            PythonModule typing = AbstractImportNode.importModule(T_TYPING);
            Object func = getAttrNode.execute(frame, inliningTarget, typing, name);
            Object[] args2 = new Object[args.length + 1];
            args2[0] = cls;
            arraycopy(args, 0, args2, 1, args.length);
            return callNode.execute(frame, func, args2, keywords);
        }
    }

    /**
     * Equivalent of {@code caller} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class CallerNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget);

        @Specialization
        static Object caller(VirtualFrame frame,
                        @Cached(inline = false) GetGlobalsNode getGlobalsNode,
                        @Cached(inline = false) PyObjectCallMethodObjArgs callMethod,
                        @Cached(inline = false) ReadCallerFrameNode readCallerNode) {
            Reference currentFrameInfo = PArguments.getCurrentFrameInfo(frame);
            PFrame pFrame = readCallerNode.executeWith(currentFrameInfo, 0);
            Object globals = getGlobalsNode.execute(frame, pFrame);

            return callMethod.executeCached(frame, globals, T_GET, T___NAME__, PNone.NONE);
        }

    }

    /**
     * Equivalent of {@code typevartuple_unpack}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class UnpackNode extends Node {
        private static final TruffleString T_UNPACK = tsLiteral("Unpack");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object tvt);

        @Specialization
        static Object doUnpack(VirtualFrame frame, Node inliningTarget, Object tvt,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached PyObjectGetItem getItemNode) {
            PythonModule typing = AbstractImportNode.importModule(T_TYPING);
            Object unpack = getAttrNode.execute(frame, inliningTarget, typing, T_UNPACK);
            return getItemNode.execute(frame, inliningTarget, unpack, tvt);
        }
    }

    /**
     * Equivalent of {@code unpack_typevartuples}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class UnpackTypeVarTuplesNode extends Node {
        public abstract PTuple execute(VirtualFrame frame, Node inliningTarget, PTuple params);

        @Specialization
        static PTuple doUnpack(VirtualFrame frame, Node inliningTarget, PTuple params,
                        @Bind PythonLanguage language,
                        @Cached ToArrayNode toArrayNode,
                        @Cached UnpackNode unpackNode) {
            Object[] elements = toArrayNode.execute(inliningTarget, params.getSequenceStorage());
            boolean found = false;
            for (Object element : elements) {
                if (element instanceof PTypeVarTuple) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return params;
            }
            Object[] unpacked = new Object[elements.length];
            for (int i = 0; i < unpacked.length; ++i) {
                if (elements[i] instanceof PTypeVarTuple typeVarTuple) {
                    unpacked[i] = unpackNode.execute(frame, inliningTarget, typeVarTuple);
                } else {
                    unpacked[i] = elements[i];
                }
            }
            return PFactory.createTuple(language, unpacked);
        }
    }
}
