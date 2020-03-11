/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.PNotImplemented.NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.BuiltinNames.ABS;
import static com.oracle.graal.python.nodes.BuiltinNames.ASCII;
import static com.oracle.graal.python.nodes.BuiltinNames.BIN;
import static com.oracle.graal.python.nodes.BuiltinNames.BREAKPOINT;
import static com.oracle.graal.python.nodes.BuiltinNames.BREAKPOINTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.CALLABLE;
import static com.oracle.graal.python.nodes.BuiltinNames.CHR;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPILE;
import static com.oracle.graal.python.nodes.BuiltinNames.DELATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.DIR;
import static com.oracle.graal.python.nodes.BuiltinNames.DIVMOD;
import static com.oracle.graal.python.nodes.BuiltinNames.EVAL;
import static com.oracle.graal.python.nodes.BuiltinNames.EXEC;
import static com.oracle.graal.python.nodes.BuiltinNames.GETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.HASH;
import static com.oracle.graal.python.nodes.BuiltinNames.HEX;
import static com.oracle.graal.python.nodes.BuiltinNames.ID;
import static com.oracle.graal.python.nodes.BuiltinNames.ISINSTANCE;
import static com.oracle.graal.python.nodes.BuiltinNames.ISSUBCLASS;
import static com.oracle.graal.python.nodes.BuiltinNames.ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.LEN;
import static com.oracle.graal.python.nodes.BuiltinNames.MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.NEXT;
import static com.oracle.graal.python.nodes.BuiltinNames.OCT;
import static com.oracle.graal.python.nodes.BuiltinNames.ORD;
import static com.oracle.graal.python.nodes.BuiltinNames.POW;
import static com.oracle.graal.python.nodes.BuiltinNames.PRINT;
import static com.oracle.graal.python.nodes.BuiltinNames.REPR;
import static com.oracle.graal.python.nodes.BuiltinNames.ROUND;
import static com.oracle.graal.python.nodes.BuiltinNames.SETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.SUM;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTIN__;
import static com.oracle.graal.python.nodes.BuiltinNames.__DEBUG__;
import static com.oracle.graal.python.nodes.BuiltinNames.__DUMP_TRUFFLE_AST__;
import static com.oracle.graal.python.nodes.BuiltinNames.__JYTHON_CURRENT_IMPORT__;
import static com.oracle.graal.python.nodes.HiddenAttributes.ID_KEY;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.GetAttrNodeFactory;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.GlobalsNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.GraalPythonTranslationErrorNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.HasInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.frame.ReadLocalsNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CoerceToStringNode;
import com.oracle.graal.python.nodes.util.CoerceToStringNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;

@CoreFunctions(defineModule = BuiltinNames.BUILTINS)
public final class BuiltinFunctions extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinFunctionsFactory.getFactories();
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule builtinsModule = core.lookupBuiltinModule(BuiltinNames.BUILTINS);
        builtinsModule.setAttribute(__DEBUG__, !PythonOptions.getOption(core.getContext(), PythonOptions.PythonOptimizeFlag));
    }

    // abs(x)
    @Builtin(name = ABS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        public boolean absInt(boolean arg) {
            return arg;
        }

        @Specialization
        public int absInt(int arg) {
            return Math.abs(arg);
        }

        @Specialization
        public long absInt(long arg) {
            return Math.abs(arg);
        }

        @Specialization
        public double absDouble(double arg) {
            return Math.abs(arg);
        }

        @Specialization
        public Object absObject(VirtualFrame frame, Object object,
                        @Cached("create(__ABS__)") LookupAndCallUnaryNode callAbsNode) {
            Object result = callAbsNode.executeObject(frame, object);
            if (result == NO_VALUE) {
                throw raise(TypeError, "bad operand type for abs():  %p", object);
            }
            return result;
        }
    }

    // bin(object)
    @Builtin(name = BIN, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class BinNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        protected String buildString(boolean isNegative, String number) {
            StringBuilder sb = new StringBuilder();
            if (isNegative) {
                sb.append('-');
            }
            sb.append(prefix());
            sb.append(number);
            return sb.toString();
        }

        protected String prefix() {
            return "0b";
        }

        @TruffleBoundary
        protected String longToString(long x) {
            return Long.toBinaryString(Math.abs(x));
        }

        @TruffleBoundary
        protected String bigToString(BigInteger x) {
            return x.toString(2);
        }

        @Specialization
        String doL(long x) {
            return buildString(x < 0, longToString(x));
        }

        @Specialization
        String doD(double x,
                        @Cached PRaiseNode raise) {
            throw raise.raiseIntegerInterpretationError(x);
        }

        @Specialization
        @TruffleBoundary
        String doPI(PInt x) {
            BigInteger value = x.getValue();
            return buildString(value.compareTo(BigInteger.ZERO) < 0, bigToString(value.abs()));
        }

        @Specialization(replaces = {"doL", "doD", "doPI"})
        String doO(VirtualFrame frame, Object x,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached IsSubtypeNode isSubtype,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached BranchProfile isInt,
                        @Cached BranchProfile isLong,
                        @Cached BranchProfile isPInt) {
            Object index;
            if (hasFrame.profile(frame != null)) {
                index = lib.asIndexWithState(x, PArguments.getThreadState(frame));
            } else {
                index = lib.asIndex(x);
            }
            if (isSubtype.execute(lib.getLazyPythonClass(index), PythonBuiltinClassType.PInt)) {
                if (index instanceof Boolean || index instanceof Integer) {
                    isInt.enter();
                    return doL(lib.asSize(index));
                } else if (index instanceof Long) {
                    isLong.enter();
                    return doL((long) index);
                } else if (index instanceof PInt) {
                    isPInt.enter();
                    return doPI((PInt) index);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw raise(PythonBuiltinClassType.NotImplementedError, "bin/oct/hex with native integer subclasses");
                }
            }
            CompilerDirectives.transferToInterpreter();
            /*
             * It should not be possible to get here, as PyNumber_Index already has a check for the
             * same condition
             */
            throw raise(PythonBuiltinClassType.ValueError, "PyNumber_ToBase: index not int");
        }
    }

    // oct(object)
    @Builtin(name = OCT, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class OctNode extends BinNode {
        @Override
        @TruffleBoundary
        protected String bigToString(BigInteger x) {
            return x.toString(8);
        }

        @Override
        @TruffleBoundary
        protected String longToString(long x) {
            return Long.toOctalString(x);
        }

        @Override
        protected String prefix() {
            return "0o";
        }
    }

    // hex(object)
    @Builtin(name = HEX, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class HexNode extends BinNode {
        @Override
        @TruffleBoundary
        protected String bigToString(BigInteger x) {
            return x.toString(16);
        }

        @Override
        @TruffleBoundary
        protected String longToString(long x) {
            return Long.toHexString(x);
        }

        @Override
        protected String prefix() {
            return "0x";
        }
    }

    // callable(object)
    @Builtin(name = CALLABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CallableNode extends PythonBuiltinNode {

        @Specialization(guards = "isCallable(callable)")
        boolean doCallable(@SuppressWarnings("unused") Object callable) {
            return true;
        }

        @Specialization
        boolean doGeneric(Object object,
                        @Cached("create(__CALL__)") LookupInheritedAttributeNode getAttributeNode) {
            /**
             * Added temporarily to skip translation/execution errors in unit testing
             */

            if (object.equals(GraalPythonTranslationErrorNode.MESSAGE)) {
                return true;
            }

            Object callAttr = getAttributeNode.execute(object);
            if (callAttr != NO_VALUE) {
                return true;
            }

            return PGuards.isCallable(object);
        }
    }

    // chr(i)
    @Builtin(name = CHR, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ChrNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        public String charFromInt(int arg) {
            if (arg >= 0 && arg <= 1114111) {
                return Character.toString((char) arg);
            } else {
                throw raise(ValueError, "chr() arg not in range(0x110000)");
            }
        }

        @TruffleBoundary
        @Specialization
        public char charFromObject(PInt arg) {
            if (arg.longValue() > Integer.MAX_VALUE) {
                throw raise(OverflowError, "integer is greater than maximum");
            } else {
                throw new RuntimeException("chr does not support PInt " + arg);
            }
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization
        public Object charFromObject(double arg) {
            throw raise(TypeError, "integer argument expected, got float");
        }

        @TruffleBoundary
        @Specialization
        public char charFromObject(Object arg) {
            if (arg instanceof Double) {
                throw raise(TypeError, "integer argument expected, got float");
            }

            throw raise(TypeError, "an integer is required");
        }
    }

    // hash(object)
    @Builtin(name = HASH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        long hash(VirtualFrame frame, Object object,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            if (profile.profile(frame != null)) {
                return lib.hashWithState(object, PArguments.getThreadState(frame));
            } else {
                return lib.hash(object);
            }
        }
    }

    // dir([object])
    @Builtin(name = DIR, minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {

        // logic like in 'Objects/object.c: _dir_locals'
        @Specialization(guards = "isNoValue(object)")
        Object locals(VirtualFrame frame, @SuppressWarnings("unused") Object object,
                        @Cached ReadLocalsNode readLocalsNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached("createBinaryProfile()") ConditionProfile inGenerator,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {

            Object localsDict = LocalsNode.getLocalsDict(frame, this, readLocalsNode, readCallerFrameNode, materializeNode, inGenerator);
            Object keysObj = callKeysNode.executeObject(frame, localsDict);
            return constructListNode.execute(keysObj);
        }

        @Specialization(guards = "!isNoValue(object)")
        Object dir(VirtualFrame frame, Object object,
                        @Cached("create(__DIR__)") LookupAndCallUnaryNode dirNode) {
            return dirNode.executeObject(frame, object);
        }
    }

    // divmod(a, b)
    @Builtin(name = DIVMOD, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class DivModNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "b != 0")
        public PTuple doLong(long a, long b) {
            return factory().createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization(replaces = "doLong")
        public PTuple doLongZero(long a, long b) {
            if (b == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "ZeroDivisionError: integer division or modulo by zero");
            }
            return factory().createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization
        public PTuple doDouble(double a, double b) {
            double q = Math.floor(a / b);
            return factory().createTuple(new Object[]{q, a % b});
        }

        @Specialization
        public PTuple doObject(VirtualFrame frame, Object a, Object b,
                        @Cached("FloorDiv.create()") LookupAndCallBinaryNode floordivNode,
                        @Cached("Mod.create()") LookupAndCallBinaryNode modNode) {
            Object div = floordivNode.executeObject(frame, a, b);
            Object mod = modNode.executeObject(frame, a, b);
            return factory().createTuple(new Object[]{div, mod});
        }

    }

    // eval(expression, globals=None, locals=None)
    @Builtin(name = EVAL, minNumOfPositionalArgs = 1, parameterNames = {"expression", "globals", "locals"})
    @GenerateNodeFactory
    public abstract static class EvalNode extends PythonBuiltinNode {
        protected final String funcname = "eval";
        private final BranchProfile hasFreeVarsBranch = BranchProfile.create();
        @Child protected CompileNode compileNode = CompileNode.create(false);
        @Child private GenericInvokeNode invokeNode = GenericInvokeNode.create();
        @Child private HasInheritedAttributeNode hasGetItemNode;

        private HasInheritedAttributeNode getHasGetItemNode() {
            if (hasGetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasGetItemNode = insert(HasInheritedAttributeNode.create(SpecialMethodNames.__GETITEM__));
            }
            return hasGetItemNode;
        }

        protected void assertNoFreeVars(PCode code) {
            Object[] freeVars = code.getFreeVars();
            if (freeVars.length > 0) {
                hasFreeVarsBranch.enter();
                throw raise(PythonBuiltinClassType.TypeError, "code object passed to %s may not contain free variables", getMode());
            }
        }

        protected String getMode() {
            return "eval";
        }

        protected boolean isMapping(Object object) {
            // tfel: it seems that CPython only checks that there is __getitem__
            if (object instanceof PDict) {
                return true;
            } else {
                return getHasGetItemNode().execute(object);
            }
        }

        protected boolean isAnyNone(Object object) {
            return object instanceof PNone;
        }

        protected PCode createAndCheckCode(VirtualFrame frame, Object source) {
            PCode code = compileNode.execute(frame, source, "<string>", getMode(), 0, false, -1);
            assertNoFreeVars(code);
            return code;
        }

        private static void inheritGlobals(PFrame callerFrame, Object[] args) {
            PArguments.setGlobals(args, callerFrame.getGlobals());
        }

        private static void inheritLocals(VirtualFrame frame, PFrame callerFrame, Object[] args, ReadLocalsNode getLocalsNode) {
            Object callerLocals = getLocalsNode.execute(frame, callerFrame);
            setCustomLocals(args, callerLocals);
        }

        private static void setCustomLocals(Object[] args, Object locals) {
            PArguments.setSpecialArgument(args, locals);
            PArguments.setCustomLocals(args, locals);
        }

        private void setBuiltinsInGlobals(VirtualFrame frame, PDict globals, HashingCollectionNodes.SetItemNode setBuiltins, PythonModule builtins, PythonObjectLibrary lib) {
            if (builtins != null) {
                PHashingCollection builtinsDict = lib.getDict(builtins);
                if (builtinsDict == null) {
                    builtinsDict = factory().createDictFixedStorage(builtins);
                    try {
                        lib.setDict(builtins, builtinsDict);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException(e);
                    }
                }
                setBuiltins.execute(frame, globals, BuiltinNames.__BUILTINS__, builtinsDict);
            } else {
                // This happens during context initialization
                return;
            }
        }

        private void setCustomGlobals(VirtualFrame frame, PDict globals, HashingCollectionNodes.SetItemNode setBuiltins, Object[] args, PythonObjectLibrary lib) {
            PythonModule builtins = getContext().getBuiltins();
            setBuiltinsInGlobals(frame, globals, setBuiltins, builtins, lib);
            PArguments.setGlobals(args, globals);
        }

        @Specialization
        Object execInheritGlobalsInheritLocals(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone globals, @SuppressWarnings("unused") PNone locals,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached ReadLocalsNode getLocalsNode) {
            PCode code = createAndCheckCode(frame, source);
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Object[] args = PArguments.create();
            inheritGlobals(callerFrame, args);
            inheritLocals(frame, callerFrame, args, getLocalsNode);

            return invokeNode.execute(frame, code.getRootCallTarget(), args);
        }

        @Specialization
        Object execCustomGlobalsGlobalLocals(VirtualFrame frame, Object source, PDict globals, @SuppressWarnings("unused") PNone locals,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setBuiltins) {
            PCode code = createAndCheckCode(frame, source);
            Object[] args = PArguments.create();
            setCustomGlobals(frame, globals, setBuiltins, args, lib);
            // here, we don't need to set any locals, since the {Write,Read,Delete}NameNodes will
            // fall back (like their CPython counterparts) to writing to the globals. We only need
            // to ensure that the `locals()` call still gives us the globals dict
            PArguments.setCustomLocals(args, globals);
            RootCallTarget rootCallTarget = code.getRootCallTarget();
            if (rootCallTarget == null) {
                throw raise(ValueError, "cannot create the a call target from the code object: %p", code);
            }

            return invokeNode.execute(frame, rootCallTarget, args);
        }

        @Specialization(guards = {"isMapping(locals)"})
        Object execInheritGlobalsCustomLocals(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone globals, Object locals,
                        @Cached("create()") ReadCallerFrameNode readCallerFrameNode) {
            PCode code = createAndCheckCode(frame, source);
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Object[] args = PArguments.create();
            inheritGlobals(callerFrame, args);
            setCustomLocals(args, locals);

            return invokeNode.execute(frame, code.getRootCallTarget(), args);
        }

        @Specialization(guards = {"isMapping(locals)"})
        Object execCustomGlobalsCustomLocals(VirtualFrame frame, Object source, PDict globals, Object locals,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setBuiltins) {
            PCode code = createAndCheckCode(frame, source);
            Object[] args = PArguments.create();
            setCustomGlobals(frame, globals, setBuiltins, args, lib);
            setCustomLocals(args, locals);

            return invokeNode.execute(frame, code.getRootCallTarget(), args);
        }

        @Specialization(guards = {"!isAnyNone(globals)", "!isDict(globals)"})
        PNone badGlobals(@SuppressWarnings("unused") Object source, Object globals, @SuppressWarnings("unused") Object locals) {
            throw raise(TypeError, "%s() globals must be a dict, not %p", funcname, globals);
        }

        @Specialization(guards = {"isAnyNone(globals) || isDict(globals)", "!isAnyNone(locals)", "!isMapping(locals)"})
        PNone badLocals(@SuppressWarnings("unused") Object source, @SuppressWarnings("unused") PDict globals, Object locals) {
            throw raise(TypeError, "%s() locals must be a mapping or None, not %p", funcname, locals);
        }
    }

    @Builtin(name = EXEC, minNumOfPositionalArgs = 1, parameterNames = {"source", "globals", "locals"})
    @GenerateNodeFactory
    abstract static class ExecNode extends EvalNode {
        protected abstract Object executeInternal(VirtualFrame frame);

        @Override
        protected String getMode() {
            return "exec";
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            executeInternal(frame);
            return PNone.NONE;
        }
    }

    // compile(source, filename, mode, flags=0, dont_inherit=False, optimize=-1)
    @Builtin(name = COMPILE, minNumOfPositionalArgs = 3, parameterNames = {"source", "filename", "mode", "flags", "dont_inherit", "optimize", "_feature_version"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class CompileNode extends PythonBuiltinNode {
        /**
         * Decides wether this node should attempt to map the filename to a URI for the benefit of
         * Truffle tooling
         */
        private final boolean mayBeFromFile;

        public CompileNode(boolean mayBeFromFile) {
            this.mayBeFromFile = mayBeFromFile;
        }

        public CompileNode() {
            this.mayBeFromFile = true;
        }

        public abstract PCode execute(VirtualFrame frame, Object source, String filename, String mode, Object kwFlags, Object kwDontInherit, Object kwOptimize);

        @Specialization
        PCode compile(VirtualFrame frame, PBytes source, String filename, String mode, Object kwFlags, Object kwDontInherit, Object kwOptimize,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return compile(createString(toBytesNode.execute(frame, source)), filename, mode, kwFlags, kwDontInherit, kwOptimize);
        }

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        PCode compile(String expression, String filename, String mode, Object kwFlags, Object kwDontInherit, Object kwOptimize) {
            PythonContext context = getContext();
            ParserMode pm;
            if (mode.equals("exec")) {
                pm = ParserMode.File;
            } else if (mode.equals("eval")) {
                pm = ParserMode.Eval;
            } else if (mode.equals("single")) {
                pm = ParserMode.Statement;
            } else {
                throw raise(ValueError, "compile() mode must be 'exec', 'eval' or 'single'");
            }
            Supplier<CallTarget> createCode = () -> {
                Source source = PythonLanguage.newSource(context, expression, filename, mayBeFromFile);
                return Truffle.getRuntime().createCallTarget((RootNode) getCore().getParser().parse(pm, getCore(), source, null));
            };
            RootCallTarget ct;
            if (getCore().isInitialized()) {
                ct = (RootCallTarget) createCode.get();
            } else {
                ct = (RootCallTarget) getCore().getLanguage().cacheCode(filename, createCode);
            }
            return factory().createCode(ct);
        }

        @SuppressWarnings("unused")
        @Specialization
        PCode compile(PCode code, String filename, String mode, Object flags, Object dontInherit, Object optimize) {
            return code;
        }

        @TruffleBoundary
        private static String createString(byte[] bytes) {
            return new String(bytes);

        }

        public static CompileNode create(boolean mapFilenameToUri) {
            return BuiltinFunctionsFactory.CompileNodeFactory.create(mapFilenameToUri, new ReadArgumentNode[]{});
        }
    }

    // delattr(object, name)
    @Builtin(name = DELATTR, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelAttrNode extends PythonBinaryBuiltinNode {
        @Child private DeleteAttributeNode delNode = DeleteAttributeNode.create();

        @Specialization
        Object delattr(VirtualFrame frame, Object object, Object name) {
            delNode.execute(frame, object, name);
            return PNone.NONE;
        }
    }

    // getattr(object, name[, default])
    @Builtin(name = GETATTR, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetAttrNode extends PythonTernaryBuiltinNode {
        public static GetAttrNode create() {
            return GetAttrNodeFactory.create();
        }

        public abstract Object executeWithArgs(VirtualFrame frame, Object primary, String name, Object defaultValue);

        @SuppressWarnings("unused")
        @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", guards = {"stringEquals(cachedName, name, stringProfile)", "isNoValue(defaultValue)"})
        public Object getAttrDefault(VirtualFrame frame, Object primary, String name, PNone defaultValue,
                        @Cached("createBinaryProfile()") ConditionProfile stringProfile,
                        @Cached("name") String cachedName,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, primary);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", guards = {"stringEquals(cachedName, name, stringProfile)", "!isNoValue(defaultValue)"})
        Object getAttr(VirtualFrame frame, Object primary, String name, Object defaultValue,
                        @Cached("createBinaryProfile()") ConditionProfile stringProfile,
                        @Cached("name") String cachedName,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            try {
                return getAttributeNode.executeObject(frame, primary);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                return defaultValue;
            }
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "isNoValue(defaultValue)")
        Object getAttrFromObject(VirtualFrame frame, Object primary, String name, @SuppressWarnings("unused") PNone defaultValue,
                        @Cached("create()") GetAnyAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, primary, name);
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "!isNoValue(defaultValue)")
        Object getAttrFromObject(VirtualFrame frame, Object primary, String name, Object defaultValue,
                        @Cached("create()") GetAnyAttributeNode getAttributeNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            try {
                return getAttributeNode.executeObject(frame, primary, name);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                return defaultValue;
            }
        }

        @Specialization
        Object getAttr2(VirtualFrame frame, Object object, PString name, Object defaultValue) {
            return executeWithArgs(frame, object, name.getValue(), defaultValue);
        }

        @Specialization(guards = "!isString(name)")
        Object getAttrGeneric(VirtualFrame frame, Object primary, Object name, Object defaultValue,
                        @Cached("create()") GetAnyAttributeNode getAttributeNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            if (PGuards.isNoValue(defaultValue)) {
                return getAttributeNode.executeObject(frame, primary, name);
            } else {
                try {
                    return getAttributeNode.executeObject(frame, primary, name);
                } catch (PException e) {
                    e.expectAttributeError(errorProfile);
                    return defaultValue;
                }
            }
        }
    }

    // id(object)
    @Builtin(name = ID, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IdNode extends PythonBuiltinNode {
        /**
         * The reserved ids at the beginning.
         *
         * <pre>
         * None, NotImplemented, True, False
         * </pre>
         */
        private static final long KNOWN_OBJECTS_COUNT = 4L;
        // borrowed logic from pypy
        // -1 - (-maxunicode-1): unichar
        // 0 - 255: char
        // 256: empty string
        // 257: empty unicode
        // 258: empty tuple
        // 259: empty frozenset
        private static final long BASE_EMPTY_BYTES = 256;
        private static final long BASE_EMPTY_UNICODE = 257;
        private static final long BASE_EMPTY_TUPLE = 258;
        private static final long BASE_EMPTY_FROZENSET = 259;
        private static final long IDTAG_SPECIAL = 11;
        private static final int IDTAG_SHIFT = 4;

        /**
         * The next available global id. We reserve space for all integers to be their own id +
         * offset.
         */

        @Child private ReadAttributeFromObjectNode readId = null;
        @Child private WriteAttributeToObjectNode writeId = null;
        @Child private SequenceNodes.LenNode lenNode = null;
        @Child private HashingCollectionNodes.LenNode setLenNode = null;

        @SuppressWarnings("unused")
        @Specialization
        int doId(PNone none) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization
        int doId(PNotImplemented none) {
            return 1;
        }

        @Specialization
        int doId(boolean value) {
            return value ? 2 : 3;
        }

        @Specialization
        long doId(int integer) {
            return integer + KNOWN_OBJECTS_COUNT;
        }

        @Specialization
        Object doId(PInt value) {
            try {
                return value.intValueExact() + KNOWN_OBJECTS_COUNT;
            } catch (ArithmeticException e) {
                return getId(value);
            }
        }

        @Specialization
        int doId(double value) {
            return Double.hashCode(value);
        }

        @Specialization(guards = "isEmpty(value)")
        Object doEmptyString(@SuppressWarnings("unused") String value) {
            return (BASE_EMPTY_UNICODE << IDTAG_SHIFT) | IDTAG_SPECIAL;
        }

        @Specialization(guards = "isEmpty(value)")
        Object doEmptyString(@SuppressWarnings("unused") PString value) {
            return (BASE_EMPTY_UNICODE << IDTAG_SHIFT) | IDTAG_SPECIAL;
        }

        @Specialization(guards = "isEmpty(value)")
        Object doEmptyTuple(@SuppressWarnings("unused") PTuple value) {
            return (BASE_EMPTY_TUPLE << IDTAG_SHIFT) | IDTAG_SPECIAL;
        }

        @Specialization(guards = "isEmpty(value)")
        Object doEmptyBytes(@SuppressWarnings("unused") PBytes value) {
            return (BASE_EMPTY_BYTES << IDTAG_SHIFT) | IDTAG_SPECIAL;
        }

        @Specialization(guards = "isEmpty(value)")
        Object doEmptyFrozenSet(@SuppressWarnings("unused") PFrozenSet value) {
            return (BASE_EMPTY_FROZENSET << IDTAG_SHIFT) | IDTAG_SPECIAL;
        }

        protected boolean isEmptyImmutableBuiltin(Object object) {
            return (object instanceof PTuple && isEmpty((PTuple) object)) ||
                            (object instanceof String && PGuards.isEmpty((String) object)) ||
                            (object instanceof PString && isEmpty((PString) object)) ||
                            (object instanceof PBytes && isEmpty((PBytes) object)) ||
                            (object instanceof PFrozenSet && isEmpty((PFrozenSet) object));
        }

        /**
         * TODO: {@link #doId(String)} and {@link #doId(double)} are not quite right, because the
         * hashCode will certainly collide with integer hashes. It should be good for comparisons
         * between String and String id, though, it'll just look as if we interned all strings from
         * the Python code's perspective.
         */
        @Specialization(guards = "!isEmpty(value)")
        int doId(String value) {
            return value.hashCode();
        }

        /**
         * PCode objects are special - we sometimes create them on-demand. see
         * {@link IsExpressionNode.IsNode#doCode}.
         */
        @Specialization
        @TruffleBoundary(allowInlining = true)
        long doId(PCode obj) {
            RootCallTarget ct = obj.getRootCallTarget();
            if (ct != null) {
                return ct.hashCode();
            } else {
                return obj.hashCode();
            }
        }

        @Specialization(guards = {"!isPCode(obj)", "!isPInt(obj)", "!isPString(obj)", "!isPFloat(obj)", "!isEmptyImmutableBuiltin(obj)"})
        long doId(PythonObject obj) {
            return getId(obj);
        }

        @Fallback
        @TruffleBoundary(allowInlining = true)
        Object doId(Object obj) {
            return obj.hashCode();
        }

        protected boolean isEmpty(PSequence s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceNodes.LenNode.create());
            }
            return lenNode.execute(s) == 0;
        }

        protected boolean isEmpty(PHashingCollection s) {
            if (setLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setLenNode = insert(HashingCollectionNodes.LenNode.create());
            }
            return setLenNode.execute(s) == 0;
        }

        private long getId(PythonObject obj) {
            if (readId == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readId = insert(ReadAttributeFromObjectNode.create());
            }
            Object id = readId.execute(obj, ID_KEY);
            if (id == NO_VALUE) {
                if (writeId == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    writeId = insert(WriteAttributeToObjectNode.create());
                }
                id = getContext().getNextGlobalId();
                writeId.execute(obj, ID_KEY, id);
            }
            assert id instanceof Long : "invalid object ID stored";
            return (long) id;
        }
    }

    // isinstance(object, classinfo)
    @Builtin(name = ISINSTANCE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsInstanceNode extends PythonBinaryBuiltinNode {
        @Child private GetClassNode getClassNode = GetClassNode.create();
        @Child private LookupAndCallBinaryNode instanceCheckNode = LookupAndCallBinaryNode.create(__INSTANCECHECK__);
        @Child private CoerceToBooleanNode castToBooleanNode = CoerceToBooleanNode.createIfTrueNode();
        @Child private TypeBuiltins.InstanceCheckNode typeInstanceCheckNode = TypeBuiltins.InstanceCheckNode.create();
        @Child private SequenceStorageNodes.LenNode lenNode;
        @Child private GetObjectArrayNode getObjectArrayNode;

        @CompilationFinal private Boolean emulateJython;

        public static IsInstanceNode create() {
            return BuiltinFunctionsFactory.IsInstanceNodeFactory.create();
        }

        private boolean isInstanceCheckInternal(VirtualFrame frame, Object instance, Object cls) {
            Object instanceCheckResult = instanceCheckNode.executeObject(frame, cls, instance);
            return instanceCheckResult != NOT_IMPLEMENTED && castToBooleanNode.executeBoolean(frame, instanceCheckResult);
        }

        public abstract boolean executeWith(VirtualFrame frame, Object instance, Object cls);

        @Specialization
        boolean isInstance(VirtualFrame frame, Object instance, PythonAbstractClass cls,
                        @Cached("create()") TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached("create()") IsSubtypeNode isSubtypeNode) {
            PythonAbstractClass instanceClass = getClassNode.execute(instance);
            return isSameTypeNode.execute(instanceClass, cls) || isSubtypeNode.execute(frame, instanceClass, cls) || isInstanceCheckInternal(frame, instance, cls);
        }

        @Specialization(guards = "getLength(clsTuple) == cachedLen", limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        boolean isInstanceTupleConstantLen(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Cached("getLength(clsTuple)") int cachedLen,
                        @Cached("create()") IsInstanceNode isInstanceNode) {
            Object[] array = getArray(clsTuple);
            for (int i = 0; i < cachedLen; i++) {
                Object cls = array[i];
                if (isInstanceNode.executeWith(frame, instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(replaces = "isInstanceTupleConstantLen")
        boolean isInstance(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Cached("create()") IsInstanceNode instanceNode) {
            for (Object cls : getArray(clsTuple)) {
                if (instanceNode.executeWith(frame, instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        protected boolean emulateJython() {
            if (emulateJython == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                emulateJython = PythonOptions.getFlag(getContext(), PythonOptions.EmulateJython);
            }
            return emulateJython;
        }

        @Fallback
        boolean isInstance(VirtualFrame frame, Object instance, Object cls) {
            TruffleLanguage.Env env = getContext().getEnv();
            if (emulateJython() && env.isHostObject(cls)) {
                Object hostCls = env.asHostObject(cls);
                Object hostInstance = env.isHostObject(instance) ? env.asHostObject(instance) : instance;
                return hostCls instanceof Class && ((Class<?>) hostCls).isAssignableFrom(hostInstance.getClass());
            }
            return isInstanceCheckInternal(frame, instance, cls) || typeInstanceCheckNode.executeWith(frame, cls, instance);
        }

        protected int getLength(PTuple t) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(t.getSequenceStorage());
        }

        private Object[] getArray(PTuple tuple) {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode.execute(tuple);
        }
    }

    // issubclass(class, classinfo)
    @Builtin(name = ISSUBCLASS, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsSubClassNode extends PythonBinaryBuiltinNode {
        @Child private LookupAndCallBinaryNode subclassCheckNode = LookupAndCallBinaryNode.create(__SUBCLASSCHECK__);
        @Child private CoerceToBooleanNode castToBooleanNode = CoerceToBooleanNode.createIfTrueNode();
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();
        @Child private SequenceStorageNodes.LenNode lenNode;
        @Child private GetObjectArrayNode getObjectArrayNode;

        public static IsSubClassNode create() {
            return BuiltinFunctionsFactory.IsSubClassNodeFactory.create();
        }

        public abstract boolean executeWith(VirtualFrame frame, Object derived, Object cls);

        private boolean isSubclassCheckInternal(VirtualFrame frame, Object derived, Object cls) {
            Object instanceCheckResult = subclassCheckNode.executeObject(frame, cls, derived);
            return instanceCheckResult != NOT_IMPLEMENTED && castToBooleanNode.executeBoolean(frame, instanceCheckResult);
        }

        @Specialization(guards = "getLength(clsTuple) == cachedLen", limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        public boolean isSubclassTupleConstantLen(VirtualFrame frame, Object derived, PTuple clsTuple,
                        @Cached("getLength(clsTuple)") int cachedLen,
                        @Cached("create()") IsSubClassNode isSubclassNode) {
            Object[] array = getArray(clsTuple);
            for (int i = 0; i < cachedLen; i++) {
                Object cls = array[i];
                if (isSubclassNode.executeWith(frame, derived, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(replaces = "isSubclassTupleConstantLen")
        public boolean isSubclass(VirtualFrame frame, Object derived, PTuple clsTuple,
                        @Cached("create()") IsSubClassNode isSubclassNode) {
            for (Object cls : getArray(clsTuple)) {
                if (isSubclassNode.executeWith(frame, derived, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Fallback
        public boolean isSubclass(VirtualFrame frame, Object derived, Object cls) {
            return isSubclassCheckInternal(frame, derived, cls) || isSubtypeNode.execute(frame, derived, cls);
        }

        protected int getLength(PTuple t) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(t.getSequenceStorage());
        }

        private Object[] getArray(PTuple tuple) {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode.execute(tuple);
        }
    }

    // iter(object[, sentinel])
    @Builtin(name = ITER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(sentinel)")
        public Object iter(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone sentinel,
                        @Cached("create()") GetIteratorNode getIterNode) {
            return getIterNode.executeWith(frame, object);
        }

        @Specialization(guards = "!isNoValue(sentinel)")
        public Object iter(Object callable, Object sentinel) {
            return factory().createSentinelIterator(callable, sentinel);
        }
    }

    // len(s)
    @Builtin(name = LEN, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public int len(VirtualFrame frame, Object obj,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("obj") PythonObjectLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.lengthWithState(obj, PArguments.getThreadState(frame));
            } else {
                return lib.length(obj);
            }
        }
    }

    public abstract static class MinMaxNode extends PythonBuiltinNode {

        @CompilationFinal private boolean seenNonBoolean = false;

        protected final BinaryComparisonNode createComparison() {
            if (this instanceof MaxNode) {
                return BinaryComparisonNode.create(SpecialMethodNames.__GT__, SpecialMethodNames.__LT__, ">");
            } else {
                return BinaryComparisonNode.create(SpecialMethodNames.__LT__, SpecialMethodNames.__GT__, "<");
            }
        }

        @Specialization(guards = "args.length == 0")
        Object maxSequence(VirtualFrame frame, PythonObject arg1, Object[] args, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Shared("castToBooleanNode") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile1,
                        @Cached("create()") IsBuiltinClassProfile errorProfile2) {
            return minmaxSequenceWithKey(frame, arg1, args, null, getIterator, next, compare, castToBooleanNode, null, errorProfile1, errorProfile2);
        }

        @Specialization(guards = "args.length == 0")
        Object minmaxSequenceWithKey(VirtualFrame frame, PythonObject arg1, @SuppressWarnings("unused") Object[] args, PythonObject keywordArg,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Shared("castToBooleanNode") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached("create()") CallNode keyCall,
                        @Cached("create()") IsBuiltinClassProfile errorProfile1,
                        @Cached("create()") IsBuiltinClassProfile errorProfile2) {
            Object iterator = getIterator.executeWith(frame, arg1);
            Object currentValue;
            try {
                currentValue = next.execute(frame, iterator);
            } catch (PException e) {
                e.expectStopIteration(errorProfile1);
                throw raise(PythonErrorType.ValueError, "%s() arg is an empty sequence", this instanceof MaxNode ? "max" : "min");
            }
            Object currentKey = applyKeyFunction(frame, keywordArg, keyCall, currentValue);
            while (true) {
                Object nextValue;
                try {
                    nextValue = next.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile2);
                    break;
                }
                Object nextKey = applyKeyFunction(frame, keywordArg, keyCall, nextValue);
                boolean isTrue;
                if (!seenNonBoolean) {
                    try {
                        isTrue = compare.executeBool(frame, nextKey, currentKey);
                    } catch (UnexpectedResultException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenNonBoolean = true;
                        isTrue = castToBooleanNode.executeBoolean(frame, e.getResult());
                    }
                } else {
                    isTrue = castToBooleanNode.executeBoolean(frame, compare.executeWith(frame, nextKey, currentKey));
                }
                if (isTrue) {
                    currentKey = nextKey;
                    currentValue = nextValue;
                }
            }
            return currentValue;
        }

        @Specialization(guards = "args.length != 0")
        Object minmaxBinary(VirtualFrame frame, Object arg1, Object[] args, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("createBinaryProfile()") ConditionProfile moreThanTwo,
                        @Shared("castToBooleanNode") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode) {
            return minmaxBinaryWithKey(frame, arg1, args, null, compare, null, moreThanTwo, castToBooleanNode);
        }

        @Specialization(guards = "args.length != 0")
        Object minmaxBinaryWithKey(VirtualFrame frame, Object arg1, Object[] args, PythonObject keywordArg,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached CallNode keyCall,
                        @Cached("createBinaryProfile()") ConditionProfile moreThanTwo,
                        @Shared("castToBooleanNode") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode) {
            Object currentValue = arg1;
            Object currentKey = applyKeyFunction(frame, keywordArg, keyCall, currentValue);
            Object nextValue = args[0];
            Object nextKey = applyKeyFunction(frame, keywordArg, keyCall, nextValue);
            boolean isTrue;
            try {
                isTrue = compare.executeBool(frame, nextKey, currentKey);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNonBoolean = true;
                isTrue = castToBooleanNode.executeBoolean(frame, e.getResult());
            }
            if (isTrue) {
                currentKey = nextKey;
                currentValue = nextValue;
            }
            if (moreThanTwo.profile(args.length > 1)) {
                for (int i = 0; i < args.length; i++) {
                    nextValue = args[i];
                    nextKey = applyKeyFunction(frame, keywordArg, keyCall, nextValue);
                    if (!seenNonBoolean) {
                        try {
                            isTrue = compare.executeBool(frame, nextKey, currentKey);
                        } catch (UnexpectedResultException e) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            seenNonBoolean = true;
                            isTrue = castToBooleanNode.executeBoolean(frame, e.getResult());
                        }
                    } else {
                        isTrue = castToBooleanNode.executeBoolean(frame, compare.executeWith(frame, nextKey, currentKey));
                    }
                    if (isTrue) {
                        currentKey = nextKey;
                        currentValue = nextValue;
                    }
                }
            }
            return currentValue;
        }

        private static Object applyKeyFunction(VirtualFrame frame, PythonObject keywordArg, CallNode keyCall, Object currentValue) {
            return keyCall == null ? currentValue : keyCall.execute(frame, keywordArg, new Object[]{currentValue}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // max(iterable, *[, key])
    // max(arg1, arg2, *args[, key])
    @Builtin(name = MAX, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key"})
    @GenerateNodeFactory
    public abstract static class MaxNode extends MinMaxNode {

    }

    // min(iterable, *[, key])
    // min(arg1, arg2, *args[, key])
    @Builtin(name = MIN, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key"})
    @GenerateNodeFactory
    public abstract static class MinNode extends MinMaxNode {

    }

    // next(iterator[, default])
    @SuppressWarnings("unused")
    @Builtin(name = NEXT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(defaultObject)")
        public Object next(VirtualFrame frame, Object iterator, PNone defaultObject,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            try {
                return next.execute(frame, iterator);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                throw raise(TypeError, e.getExceptionObject(), "'%p' object is not an iterator", iterator);
            }
        }

        @Specialization(guards = "!isNoValue(defaultObject)")
        public Object next(VirtualFrame frame, Object iterator, Object defaultObject,
                        @Cached("create()") NextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            try {
                return next.execute(frame, iterator, PNone.NO_VALUE);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                return defaultObject;
            }
        }

        protected static NextNode create() {
            return BuiltinFunctionsFactory.NextNodeFactory.create();
        }
    }

    // ord(c)
    @Builtin(name = ORD, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class OrdNode extends PythonBuiltinNode {

        @Specialization
        public int ord(String chr) {
            if (chr.length() != 1) {
                throw raise(TypeError, "ord() expected a character, but string of length %d found", chr.length());
            }
            return chr.charAt(0);
        }

        @Specialization
        public int ord(VirtualFrame frame, PIBytesLike chr,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
            int len = lenNode.execute(chr.getSequenceStorage());
            if (len != 1) {
                throw raise(TypeError, "ord() expected a character, but string of length %d found", len);
            }

            Object element = getItemNode.execute(frame, chr.getSequenceStorage(), 0);
            if (element instanceof Long) {
                long e = (long) element;
                if (e >= Byte.MIN_VALUE && e <= Byte.MAX_VALUE) {
                    return (int) e;
                }
            } else if (element instanceof Integer) {
                int e = (int) element;
                if (e >= Byte.MIN_VALUE && e <= Byte.MAX_VALUE) {
                    return e;
                }
            } else if (element instanceof Byte) {
                return (byte) element;
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("got a bytes-like with non-byte elements");
        }
    }

    // print(*objects, sep=' ', end='\n', file=sys.stdout, flush=False)
    @Builtin(name = PRINT, takesVarArgs = true, keywordOnlyNames = {"sep", "end", "file", "flush"}, doc = "\n" +
                    "print(value, ..., sep=' ', end='\\n', file=sys.stdout, flush=False)\n" +
                    "\n" +
                    "Prints the values to a stream, or to sys.stdout by default.\n" +
                    "Optional keyword arguments:\n" +
                    "file:  a file-like object (stream); defaults to the current sys.stdout.\n" +
                    "sep:   string inserted between values, default a space.\n" +
                    "end:   string appended after the last value, default a newline.\n" +
                    "flush: whether to forcibly flush the stream.")
    @GenerateNodeFactory
    public abstract static class PrintNode extends PythonBuiltinNode {
        private static final String DEFAULT_END = "\n";
        private static final String DEFAULT_SEPARATOR = " ";
        @Child private ReadAttributeFromObjectNode readStdout;
        @Child private GetAttributeNode getWrite = GetAttributeNode.create("write", null);
        @Child private CallNode callWrite = CallNode.create();
        @Child private CoerceToStringNode toString = CoerceToStringNodeGen.create();
        @Child private LookupAndCallUnaryNode callFlushNode;
        @CompilationFinal private Assumption singleContextAssumption;
        @CompilationFinal private PythonModule cachedSys;

        @Specialization
        PNone printNoKeywords(VirtualFrame frame, Object[] values, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") PNone end, @SuppressWarnings("unused") PNone file,
                        @SuppressWarnings("unused") PNone flush) {
            Object stdout = getStdout();
            return printAllGiven(frame, values, DEFAULT_SEPARATOR, DEFAULT_END, stdout, false);
        }

        @Specialization(guards = {"!isNone(file)", "!isNoValue(file)"})
        PNone printAllGiven(VirtualFrame frame, Object[] values, String sep, String end, Object file, boolean flush) {
            int lastValue = values.length - 1;
            Object write = getWrite.executeObject(frame, file);
            for (int i = 0; i < lastValue; i++) {
                callWrite.execute(frame, write, toString.execute(frame, values[i]));
                callWrite.execute(frame, write, sep);
            }
            if (lastValue >= 0) {
                callWrite.execute(frame, write, toString.execute(frame, values[lastValue]));
            }
            callWrite.execute(frame, write, end);
            if (flush) {
                if (callFlushNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callFlushNode = insert(LookupAndCallUnaryNode.create("flush"));
                }
                callFlushNode.executeObject(frame, file);
            }
            return PNone.NONE;
        }

        @Specialization(replaces = {"printAllGiven", "printNoKeywords"})
        PNone printGeneric(VirtualFrame frame, Object[] values, Object sepIn, Object endIn, Object fileIn, Object flushIn,
                        @Cached CastToJavaStringNode castSep,
                        @Cached CastToJavaStringNode castEnd,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castFlush,
                        @Cached PRaiseNode raiseNode) {
            String sep = sepIn instanceof PNone ? DEFAULT_SEPARATOR : castSep.execute(sepIn);
            if (sep == null) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "sep must be None or a string, not %p", sepIn);
            }

            String end = endIn instanceof PNone ? DEFAULT_END : castEnd.execute(endIn);
            if (end == null) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "end must be None or a string, not %p", sepIn);
            }

            Object file;
            if (fileIn instanceof PNone) {
                file = getStdout();
            } else {
                file = fileIn;
            }
            boolean flush;
            if (flushIn instanceof PNone) {
                flush = false;
            } else {
                flush = castFlush.executeBoolean(frame, flushIn);
            }
            return printAllGiven(frame, values, sep, end, file, flush);
        }

        private Object getStdout() {
            if (singleContextAssumption == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                singleContextAssumption = singleContextAssumption();
            }
            PythonModule sys;
            if (singleContextAssumption.isValid()) {
                if (cachedSys == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedSys = getContext().getCore().lookupBuiltinModule("sys");
                }
                sys = cachedSys;
            } else {
                if (cachedSys != null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedSys = null;
                }
                sys = getContext().getCore().lookupBuiltinModule("sys");
            }
            if (readStdout == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readStdout = insert(ReadAttributeFromObjectNode.create());
            }
            Object stdout = readStdout.execute(sys, "stdout");
            return stdout;
        }
    }

    // repr(object)
    @Builtin(name = REPR, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object repr(VirtualFrame frame, Object obj,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprCallNode,
                        @Cached("createBinaryProfile()") ConditionProfile isString,
                        @Cached("createBinaryProfile()") ConditionProfile isPString) {

            Object result = reprCallNode.executeObject(frame, obj);
            if (isString.profile(result instanceof String) || isPString.profile(result instanceof PString)) {
                return result;
            }
            throw raise(TypeError, "__repr__ returned non-string (type %p)", obj);
        }
    }

    // ascii(object)
    @Builtin(name = ASCII, minNumOfPositionalArgs = 1)
    @ImportStatic(PGuards.class)
    @GenerateNodeFactory
    public abstract static class AsciiNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isString(obj)")
        public Object asciiString(Object obj,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            String str = castToJavaStringNode.execute(obj);
            byte[] bytes = BytesUtils.unicodeEscape(str);
            boolean hasSingleQuote = false;
            boolean hasDoubleQuote = false;
            for (int i = 0; i < bytes.length; i++) {
                char c = (char) bytes[i];
                hasSingleQuote |= c == '\'';
                hasDoubleQuote |= c == '"';
            }
            boolean useDoubleQuotes = hasSingleQuote && !hasDoubleQuote;
            char quote = useDoubleQuotes ? '"' : '\'';
            StringBuilder sb = new StringBuilder(bytes.length + 2);
            sb.append(quote);
            for (int i = 0; i < bytes.length; i++) {
                char c = (char) bytes[i];
                if (c == '\'' && !useDoubleQuotes) {
                    sb.append("\\'");
                } else {
                    sb.append(c);
                }
            }
            sb.append(quote);
            return sb.toString();
        }

        @Specialization(guards = "!isString(obj)")
        public Object asciiGeneric(VirtualFrame frame, Object obj,
                        @Cached ReprNode reprNode) {
            String repr = (String) reprNode.execute(frame, obj);
            byte[] bytes = BytesUtils.unicodeEscape(repr);
            return new String(bytes);
        }
    }

    // round(number[, ndigits])
    @Builtin(name = ROUND, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBuiltinNode {
        @Specialization
        Object round(VirtualFrame frame, Object x, Object n,
                        @Cached("create(__ROUND__)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(frame, x, n);
        }
    }

    // setattr(object, name, value)
    @Builtin(name = SETATTR, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetAttrNode extends PythonBuiltinNode {
        @Specialization
        Object setAttr(VirtualFrame frame, Object object, Object key, Object value,
                        @Cached("new()") SetAttributeNode.Dynamic setAttrNode) {
            setAttrNode.execute(frame, object, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = BREAKPOINT, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BreakPointNode extends PythonBuiltinNode {
        @Child private ReadAttributeFromObjectNode getBreakpointhookNode;
        @Child private CallNode callNode;

        @Specialization
        public Object doIt(VirtualFrame frame, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary("getContext().getSysModules().getDictStorage()") HashingStorageLibrary hlib) {
            if (getDebuggerSessionCount() > 0) {
                // we already have a Truffle debugger attached, it'll stop here
                return PNone.NONE;
            } else if (getContext().isInitialized()) {
                if (callNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getBreakpointhookNode = insert(ReadAttributeFromObjectNode.create());
                    callNode = insert(CallNode.create());
                }
                PDict sysModules = getContext().getSysModules();
                Object sysModule = hlib.getItem(sysModules.getDictStorage(), "sys");
                Object breakpointhook = getBreakpointhookNode.execute(sysModule, BREAKPOINTHOOK);
                if (breakpointhook == PNone.NO_VALUE) {
                    throw raise(PythonBuiltinClassType.RuntimeError, "lost sys.breakpointhook");
                }
                return callNode.execute(frame, breakpointhook, args, kwargs);
            } else {
                return PNone.NONE;
            }
        }

        @TruffleBoundary
        private int getDebuggerSessionCount() {
            return Debugger.find(getContext().getEnv()).getSessionCount();
        }
    }

    @Builtin(name = "__tdebug__", takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DebugNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object doIt(Object[] args) {
            PrintWriter stdout = new PrintWriter(getContext().getStandardOut());
            for (int i = 0; i < args.length; i++) {
                stdout.println(args[i]);
            }
            stdout.flush();
            return PNone.NONE;
        }
    }

    @Builtin(name = POW, minNumOfPositionalArgs = 2, parameterNames = {"x", "y", "z"})
    @GenerateNodeFactory
    public abstract static class PowNode extends PythonBuiltinNode {
        @Child private LookupAndCallTernaryNode powNode = TernaryArithmetic.Pow.create();

        @Specialization
        Object doIt(VirtualFrame frame, Object x, Object y, Object z) {
            return powNode.execute(frame, x, y, z);
        }
    }

    // sum(iterable[, start])
    @Builtin(name = SUM, minNumOfPositionalArgs = 1, parameterNames = {"iterable", "start"})
    @GenerateNodeFactory
    public abstract static class SumFunctionNode extends PythonBuiltinNode {

        @Child private GetIteratorNode iter = GetIteratorNode.create();
        @Child private LookupAndCallUnaryNode next = LookupAndCallUnaryNode.create(__NEXT__);
        @Child private LookupAndCallBinaryNode add = BinaryArithmetic.Add.create();

        private final IsBuiltinClassProfile errorProfile1 = IsBuiltinClassProfile.create();
        private final IsBuiltinClassProfile errorProfile2 = IsBuiltinClassProfile.create();
        private final IsBuiltinClassProfile errorProfile3 = IsBuiltinClassProfile.create();

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public int sumInt(VirtualFrame frame, Object arg1, @SuppressWarnings("unused") PNone start) throws UnexpectedResultException {
            return sumIntInternal(frame, arg1, 0);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public int sumInt(VirtualFrame frame, Object arg1, int start) throws UnexpectedResultException {
            return sumIntInternal(frame, arg1, start);
        }

        private int sumIntInternal(VirtualFrame frame, Object arg1, int start) throws UnexpectedResultException {
            Object iterator = iter.executeWith(frame, arg1);
            int value = start;
            while (true) {
                int nextValue;
                try {
                    nextValue = next.executeInt(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile1);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = add.executeObject(frame, value, e.getResult());
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, newValue, errorProfile2));
                }
                try {
                    value = add.executeInt(frame, value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, e.getResult(), errorProfile3));
                }
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public double sumDouble(VirtualFrame frame, Object arg1, @SuppressWarnings("unused") PNone start) throws UnexpectedResultException {
            return sumDoubleInternal(frame, arg1, 0);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public double sumDouble(VirtualFrame frame, Object arg1, double start) throws UnexpectedResultException {
            return sumDoubleInternal(frame, arg1, start);
        }

        private double sumDoubleInternal(VirtualFrame frame, Object arg1, double start) throws UnexpectedResultException {
            Object iterator = iter.executeWith(frame, arg1);
            double value = start;
            while (true) {
                double nextValue;
                try {
                    nextValue = next.executeDouble(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile1);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = add.executeObject(frame, value, e.getResult());
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, newValue, errorProfile2));
                }
                try {
                    value = add.executeDouble(frame, value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, e.getResult(), errorProfile3));
                }
            }
        }

        @Specialization
        public Object sum(VirtualFrame frame, Object arg1, Object start,
                        @Cached("createBinaryProfile()") ConditionProfile hasStart) {
            Object iterator = iter.executeWith(frame, arg1);
            return iterateGeneric(frame, iterator, hasStart.profile(start != NO_VALUE) ? start : 0, errorProfile1);
        }

        private Object iterateGeneric(VirtualFrame frame, Object iterator, Object start, IsBuiltinClassProfile errorProfile) {
            Object value = start;
            while (true) {
                Object nextValue;
                try {
                    nextValue = next.executeObject(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return value;
                }
                value = add.executeObject(frame, value, nextValue);
            }
        }
    }

    @Builtin(name = __BUILTIN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinNode extends PythonUnaryBuiltinNode {
        @Child private GetItemNode getNameNode = GetItemNode.create();

        @Specialization
        public Object doIt(VirtualFrame frame, PFunction func) {
            PFunction builtinFunc = convertToBuiltin(func);
            PythonObject globals = func.getGlobals();
            PythonModule builtinModule;
            if (globals instanceof PythonModule) {
                builtinModule = (PythonModule) globals;
            } else {
                String moduleName = (String) getNameNode.execute(frame, globals, __NAME__);
                builtinModule = getCore().lookupBuiltinModule(moduleName);
                assert builtinModule != null;
            }
            return factory().createBuiltinMethod(builtinModule, builtinFunc);
        }

        @TruffleBoundary
        public synchronized PFunction convertToBuiltin(PFunction func) {
            /*
             * (tfel): To be compatible with CPython, builtin module functions must be bound to
             * their respective builtin module. We ignore that builtin functions should really be
             * builtin methods here - it does not hurt if they are normal methods. What does hurt,
             * however, is if they are not bound, because then using these functions in class field
             * won't work when they are called from an instance of that class due to the implicit
             * currying with "self".
             */
            Signature signature = func.getSignature();
            PFunction builtinFunc;
            if (signature.getParameterIds().length > 0 && signature.getParameterIds()[0].equals("self")) {
                /*
                 * If the first parameter is called self, we assume the function does explicitly
                 * declare the module argument
                 */
                builtinFunc = func;
                func.getFunctionRootNode().accept(new NodeVisitor() {
                    public boolean visit(Node node) {
                        if (node instanceof PythonCallNode) {
                            node.replace(((PythonCallNode) node).asSpecialCall());
                        }
                        return true;
                    }
                });
            } else {
                /*
                 * Otherwise, we create a new function with a signature that requires one extra
                 * argument in front. We actually modify the function's AST here, so the original
                 * PFunction cannot be used anymore (its signature won't agree with it's indexed
                 * parameter reads).
                 */
                FunctionRootNode functionRootNode = (FunctionRootNode) func.getFunctionRootNode();
                assert !functionRootNode.isRewritten() : "a function cannot be annotated as builtin twice";
                functionRootNode = functionRootNode.copyWithNewSignature(signature.createWithSelf());
                functionRootNode.setRewritten();
                functionRootNode.accept(new NodeVisitor() {

                    public boolean visit(Node node) {
                        if (node instanceof ReadVarArgsNode) {
                            ReadVarArgsNode varArgsNode = (ReadVarArgsNode) node;
                            node.replace(ReadVarArgsNode.create(varArgsNode.getIndex() + 1, varArgsNode.isBuiltin()));
                        } else if (node instanceof ReadIndexedArgumentNode) {
                            node.replace(ReadIndexedArgumentNode.create(((ReadIndexedArgumentNode) node).getIndex() + 1));
                        } else if (node instanceof PythonCallNode) {
                            node.replace(((PythonCallNode) node).asSpecialCall());
                        }
                        return true;
                    }
                });

                String name = func.getName();
                builtinFunc = factory().createFunction(name, func.getEnclosingClassName(),
                                new PCode(PythonBuiltinClassType.PCode, Truffle.getRuntime().createCallTarget(functionRootNode)),
                                func.getGlobals(), func.getDefaults(), func.getKwDefaults(), func.getClosure());
            }

            return builtinFunc;
        }
    }

    @Builtin(name = __DUMP_TRUFFLE_AST__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DumpTruffleAstNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String doIt(PFunction func) {
            return NodeUtil.printTreeToString(func.getCallTarget().getRootNode());
        }

        @Specialization(guards = "isFunction(method.getFunction())")
        @TruffleBoundary
        public String doIt(PMethod method) {
            // cast ensured by guard
            PFunction fun = (PFunction) method.getFunction();
            return NodeUtil.printTreeToString(fun.getCallTarget().getRootNode());
        }

        @Specialization
        @TruffleBoundary
        public String doIt(PGenerator gen) {
            return NodeUtil.printTreeToString(gen.getCurrentCallTarget().getRootNode());
        }

        @Specialization
        @TruffleBoundary
        public String doIt(PCode code) {
            return NodeUtil.printTreeToString(code.getRootNode());
        }

        @Fallback
        @TruffleBoundary
        public Object doit(Object object) {
            return "truffle ast dump not supported for " + object.toString();
        }

        protected static boolean isFunction(Object callee) {
            return callee instanceof PFunction;
        }
    }

    @Builtin(name = __JYTHON_CURRENT_IMPORT__, minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class CurrentImport extends PythonBuiltinNode {
        @Specialization
        String doIt() {
            return getContext().getCurrentImport();
        }
    }

    @Builtin(name = "input", parameterNames = {"prompt"})
    @GenerateNodeFactory
    abstract static class InputNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String input(@SuppressWarnings("unused") PNone prompt) {
            CharBuffer buf = CharBuffer.allocate(1000);
            try {
                InputStream stdin = getContext().getStandardIn();
                int read = stdin.read();
                while (read != -1 && read != '\n') {
                    if (buf.remaining() == 0) {
                        CharBuffer newBuf = CharBuffer.allocate(buf.capacity() * 2);
                        newBuf.put(buf);
                        buf = newBuf;
                    }
                    buf.put((char) read);
                    read = stdin.read();
                }
                buf.limit(buf.position());
                buf.rewind();
                return buf.toString();
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.EOFError, e);
            }
        }

        @Specialization
        String inputPrompt(PString prompt) {
            return inputPrompt(prompt.getValue());
        }

        @Specialization
        @TruffleBoundary
        String inputPrompt(String prompt) {
            new PrintStream(getContext().getStandardOut()).println(prompt);
            return input(null);
        }
    }

    @Builtin(name = "globals", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GlobalsNode extends PythonBuiltinNode {
        @Child private ReadCallerFrameNode readCallerFrameNode = ReadCallerFrameNode.create();

        private final ConditionProfile condProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public Object globals(VirtualFrame frame,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            PythonObject globals = callerFrame.getGlobals();
            if (condProfile.profile(globals instanceof PythonModule)) {
                PHashingCollection dict = lib.getDict(globals);
                if (dict == null) {
                    CompilerDirectives.transferToInterpreter();
                    dict = factory().createDictFixedStorage(globals);
                    try {
                        lib.setDict(globals, dict);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException(e);
                    }
                }
                return dict;
            } else {
                return globals;
            }
        }

        public static GlobalsNode create() {
            return GlobalsNodeFactory.create(null);
        }
    }

    @Builtin(name = "locals", minNumOfPositionalArgs = 0, needsFrame = true)
    @GenerateNodeFactory
    abstract static class LocalsNode extends PythonBuiltinNode {

        @Specialization
        Object locals(VirtualFrame frame,
                        @Cached ReadLocalsNode readLocalsNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached("createBinaryProfile()") ConditionProfile inGenerator) {
            return getLocalsDict(frame, this, readLocalsNode, readCallerFrameNode, materializeNode, inGenerator);
        }

        static Object getLocalsDict(VirtualFrame frame, Node n, ReadLocalsNode readLocalsNode, ReadCallerFrameNode readCallerFrameNode, MaterializeFrameNode materializeNode,
                        ConditionProfile inGenerator) {
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Frame generatorFrame = PArguments.getGeneratorFrame(callerFrame.getArguments());
            if (inGenerator.profile(generatorFrame == null)) {
                return readLocalsNode.execute(frame, callerFrame);
            } else {
                return readLocalsNode.execute(frame, materializeNode.execute(frame, n, false, false, generatorFrame));
            }
        }
    }
}
