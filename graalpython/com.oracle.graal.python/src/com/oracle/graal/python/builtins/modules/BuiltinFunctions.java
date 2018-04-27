/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.PNotImplemented.NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.BuiltinNames.ABS;
import static com.oracle.graal.python.nodes.BuiltinNames.CALLABLE;
import static com.oracle.graal.python.nodes.BuiltinNames.CHR;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPILE;
import static com.oracle.graal.python.nodes.BuiltinNames.DIR;
import static com.oracle.graal.python.nodes.BuiltinNames.DIVMOD;
import static com.oracle.graal.python.nodes.BuiltinNames.EVAL;
import static com.oracle.graal.python.nodes.BuiltinNames.GETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.HASH;
import static com.oracle.graal.python.nodes.BuiltinNames.ID;
import static com.oracle.graal.python.nodes.BuiltinNames.ISINSTANCE;
import static com.oracle.graal.python.nodes.BuiltinNames.ISSUBCLASS;
import static com.oracle.graal.python.nodes.BuiltinNames.ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.LEN;
import static com.oracle.graal.python.nodes.BuiltinNames.MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.NEXT;
import static com.oracle.graal.python.nodes.BuiltinNames.ORD;
import static com.oracle.graal.python.nodes.BuiltinNames.PRINT;
import static com.oracle.graal.python.nodes.BuiltinNames.REPR;
import static com.oracle.graal.python.nodes.BuiltinNames.ROUND;
import static com.oracle.graal.python.nodes.BuiltinNames.SETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.SUM;
import static com.oracle.graal.python.nodes.BuiltinNames.__BREAKPOINT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.nodes.GraalPythonTranslationErrorNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;

@CoreFunctions(extendModule = "builtins")
public final class BuiltinFunctions extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return BuiltinFunctionsFactory.getFactories();
    }

    // abs(x)
    @Builtin(name = ABS, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class AbsNode extends PythonBuiltinNode {

        @Specialization
        public int absInt(int arg) {
            return Math.abs(arg);
        }

        @Specialization
        public double absDouble(double arg) {
            return Math.abs(arg);
        }

        @Specialization
        public int absBoolean(boolean arg) {
            return arg ? 1 : 0;
        }

        @Specialization
        public Object absObject(Object object,
                        @Cached("create(__ABS__)") LookupAndCallUnaryNode callAbsNode) {
            Object result = callAbsNode.executeObject(object);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "bad operand type for abs():  %p", object);
            }
            return result;
        }
    }

    // callable(object)
    @Builtin(name = CALLABLE, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class CallableNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        public boolean callable(PythonCallable callable) {
            return true;
        }

        @Specialization
        public boolean callable(Object object) {
            /**
             * Added temporarily to skip translation/execution errors in unit testing
             */

            if (object.equals(GraalPythonTranslationErrorNode.MESSAGE)) {
                return true;
            }

            return object instanceof PythonCallable;
        }
    }

    // chr(i)
    @Builtin(name = CHR, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ChrNode extends PythonBuiltinNode {

        @TruffleBoundary
        @Specialization
        public String charFromInt(int arg) {
            if (arg >= 0 && arg < 1114111) {
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

    // hash([object])
    @Builtin(name = HASH, minNumOfArguments = 0, maxNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @Specialization  // tfel: TODO: this shouldn't be needed!
        public Object hash(PException exception) {
            return exception.hashCode();
        }

        protected boolean isPException(Object object) {
            return object instanceof PException;
        }

        @Specialization(guards = "!isPException(object)")
        public Object hash(Object object,
                        @Cached("create()") LookupInheritedAttributeNode lookupDirNode,
                        @Cached("create(__HASH__)") LookupAndCallUnaryNode dispatchHash,
                        @Cached("createIfTrueNode()") CastToBooleanNode trueNode) {
            if (trueNode.executeWith(lookupDirNode.execute(object, __DIR__))) {
                return dispatchHash.executeObject(object);
            }
            return object.hashCode();
        }
    }

    // dir([object])
    @Builtin(name = DIR, minNumOfArguments = 0, maxNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(object)")
        @SuppressWarnings("unused")
        public Object dir(VirtualFrame frame, Object object) {
            PList locals = factory().createList();
            FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
            addIdsFromDescriptor(locals, frameDescriptor);
            return locals;
        }

        @TruffleBoundary
        private static void addIdsFromDescriptor(PList locals, FrameDescriptor frameDescriptor) {
            for (FrameSlot slot : frameDescriptor.getSlots()) {
                // XXX: remove this special case
                if (slot.getIdentifier().equals(RETURN_SLOT_ID)) {
                    continue;
                }
                locals.append(slot.getIdentifier());
            }
        }

        @Specialization(guards = "!isNoValue(object)")
        public Object dir(Object object,
                        @Cached("create(__DIR__)") LookupAndCallUnaryNode dirNode) {
            return dirNode.executeObject(object);
        }
    }

    // divmod(a, b)
    @Builtin(name = DIVMOD, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DivModNode extends PythonBuiltinNode {

        @Specialization
        public PTuple doInt(int a, int b) {
            return factory().createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization
        public PTuple doInt(long a, long b) {
            return factory().createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization
        public PTuple doDouble(double a, double b) {
            double q = Math.floor(a / b);
            return factory().createTuple(new Object[]{q, a % b});
        }

        @Specialization
        public PTuple doObject(Object a, Object b,
                        @Cached("create(__FLOORDIV__)") LookupAndCallBinaryNode floordivNode,
                        @Cached("create(__MOD__)") LookupAndCallBinaryNode modNode) {
            Object div = floordivNode.executeObject(a, b);
            Object mod = modNode.executeObject(a, b);
            return factory().createTuple(new Object[]{div, mod});
        }

        @TruffleBoundary
        private static BigInteger[] divideAndRemainder(PInt a, PInt b) {
            BigInteger[] result = a.getValue().divideAndRemainder(b.getValue());
            assert result.length == 2;
            return result;
        }
    }

    // eval(expression, globals=None, locals=None)
    @Builtin(name = EVAL, fixedNumOfArguments = 1, keywordArguments = {"globals", "locals"})
    @GenerateNodeFactory
    public abstract static class EvalNode extends PythonBuiltinNode {
        @Child private GetItemNode getNameNode = GetItemNode.create();
        @Child private ReadCallerFrameNode readCallerFrameNode = ReadCallerFrameNode.create();

        @Specialization
        public Object eval(VirtualFrame frame, String expression, @SuppressWarnings("unused") PNone globals, @SuppressWarnings("unused") PNone locals) {
            Frame callerFrame = readCallerFrameNode.executeWith(frame);
            PythonObject callerGlobals = PArguments.getGlobals(callerFrame);
            PCell[] callerClosure = PArguments.getClosure(callerFrame);
            return evalExpression(expression, callerGlobals, callerGlobals, callerClosure);
        }

        @Specialization
        public Object eval(String expression, PythonObject globals, @SuppressWarnings("unused") PNone locals) {
            return evalExpression(expression, globals, globals, null);
        }

        @Specialization
        public Object eval(String expression, PythonObject globals, PythonObject locals) {
            return evalExpression(expression, globals, locals, null);
        }

        @Specialization
        public Object eval(VirtualFrame frame, String expression, @SuppressWarnings("unused") PNone globals, PythonObject locals) {
            Frame callerFrame = readCallerFrameNode.executeWith(frame);
            PythonObject callerGlobals = PArguments.getGlobals(callerFrame);
            PCell[] callerClosure = PArguments.getClosure(callerFrame);
            return evalExpression(expression, callerGlobals, locals, callerClosure);
        }

        @Specialization
        public Object eval(VirtualFrame frame, PythonParseResult code, @SuppressWarnings("unused") PNone globals, @SuppressWarnings("unused") PNone locals) {
            Frame callerFrame = readCallerFrameNode.executeWith(frame);
            PythonObject callerGlobals = PArguments.getGlobals(callerFrame);
            PCell[] callerClosure = PArguments.getClosure(callerFrame);
            return evalExpression(code, callerGlobals, callerGlobals, callerClosure);
        }

        @Specialization
        public Object eval(VirtualFrame frame, PythonParseResult code, PythonObject globals, @SuppressWarnings("unused") PNone locals) {
            Frame callerFrame = readCallerFrameNode.executeWith(frame);
            PCell[] callerClosure = PArguments.getClosure(callerFrame);
            return evalExpression(code, globals, globals, callerClosure);
        }

        @Specialization
        public Object eval(VirtualFrame frame, PythonParseResult code, PythonObject globals, PythonObject locals) {
            Frame callerFrame = readCallerFrameNode.executeWith(frame);
            PCell[] callerClosure = PArguments.getClosure(callerFrame);
            return evalExpression(code, globals, locals, callerClosure);
        }

        @Specialization
        public Object eval(VirtualFrame frame, PythonParseResult code, @SuppressWarnings("unused") PNone globals, PythonObject locals) {
            Frame callerFrame = readCallerFrameNode.executeWith(frame);
            PythonObject callerGlobals = PArguments.getGlobals(callerFrame);
            PCell[] callerClosure = PArguments.getClosure(callerFrame);
            return evalExpression(code, callerGlobals, locals, callerClosure);
        }

        /**
         * @param locals TODO: support the locals dictionary in execution
         */
        @TruffleBoundary
        private static Object evalExpression(PythonParseResult code, PythonObject globals, PythonObject locals, PCell[] closure) {
            RootNode root = code.getRootNode();
            Object[] args = PArguments.create();
            PArguments.setGlobals(args, globals);
            PArguments.setClosure(args, closure);
            // TODO: cache code and CallTargets and use Direct/IndirectCallNode
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(root);
            return callTarget.call(args);
        }

        @TruffleBoundary
        private Object evalExpression(String expression, PythonObject globals, PythonObject locals, PCell[] closure) {
            String name = "<eval>";
            if (globals instanceof PDict) {
                Object nameObject = getNameNode.execute(globals, __NAME__);
                if (nameObject instanceof String) {
                    name = (String) nameObject;
                }
            }
            PythonParser parser = getCore().getParser();
            PythonParseResult parsed = parser.parseEval(getCore(), expression, name);
            return evalExpression(parsed, globals, locals, closure);
        }
    }

    // compile(source, filename, mode, flags=0, dont_inherit=False, optimize=-1)
    @Builtin(name = COMPILE, fixedNumOfArguments = 3, keywordArguments = {"flags", "dont_inherit", "optimize"})
    @GenerateNodeFactory
    public abstract static class CompileNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object compile(PBytes source, String filename, String mode, Object kwFlags, Object kwDontInherit, Object kwOptimize) {
            return compile(new String(source.getInternalByteArray()), filename, mode, kwFlags, kwDontInherit, kwOptimize);
        }

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object compile(String source, String filename, String mode, Object kwFlags, Object kwDontInherit, Object kwOptimize) {
            PythonParser parser = getCore().getParser();
            if (mode.equals("exec")) {
                return parser.parseExec(getCore(), source, filename);
            } else if (mode.equals("eval")) {
                return parser.parseEval(getCore(), source, filename);
            } else if (mode.equals("single")) {
                return parser.parseSingle(getCore(), source, filename);
            } else {
                // create source
                Source src = Source.newBuilder(source).name(filename).mimeType(mode).build();
                CallTarget parse = getContext().getEnv().parse(src);
                if (parse instanceof RootCallTarget) {
                    return new PythonParseResult(((RootCallTarget) parse).getRootNode(), getCore());
                }
                throw raise(ValueError, "compile() mode must be 'exec', 'eval' or 'single'");
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        Object compile(PythonParseResult code, String filename, String mode, Object flags, Object dontInherit, Object optimize) {
            return code;
        }
    }

    // getattr(object, name[, default])
    @Builtin(name = GETATTR, minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class GetAttrNode extends PythonTernaryBuiltinNode {
        public abstract Object executeWithArgs(Object primary, String name, Object defaultValue);

        @SuppressWarnings("unused")
        @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", guards = {"name.equals(cachedName)", "isNoValue(defaultValue)"})
        public Object getAttrDefault(Object primary, String name, PNone defaultValue,
                        @Cached("name") String cachedName,
                        @Cached("create()") GetAttributeNode getter) {
            return getter.execute(primary, cachedName);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", guards = {"name.equals(cachedName)", "!isNoValue(defaultValue)"})
        public Object getAttr(Object primary, String name, Object defaultValue,
                        @Cached("name") String cachedName,
                        @Cached("create()") GetAttributeNode getter,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            try {
                return getter.execute(primary, cachedName);
            } catch (PException e) {
                e.expect(AttributeError, getCore(), errorProfile);
                return defaultValue;
            }
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "isNoValue(defaultValue)")
        public Object getAttrFromObject(Object primary, String name, @SuppressWarnings("unused") PNone defaultValue,
                        @Cached("create()") GetAttributeNode getter) {
            return getter.execute(primary, name);
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "!isNoValue(defaultValue)")
        public Object getAttrFromObject(Object primary, String name, Object defaultValue,
                        @Cached("create()") GetAttributeNode getter,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            try {
                return getter.execute(primary, name);
            } catch (PException e) {
                e.expect(AttributeError, getCore(), errorProfile);
                return defaultValue;
            }
        }

        @Specialization
        public Object getAttr(Object object, PString name, Object defaultValue) {
            return executeWithArgs(object, name.getValue(), defaultValue);
        }

        @Specialization(guards = "!isString(name)")
        public Object getAttrGeneric(Object primary, Object name, Object defaultValue,
                        @Cached("create()") GetAttributeNode getter,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            if (PGuards.isNoValue(defaultValue)) {
                return getter.execute(primary, name);
            } else {
                try {
                    return getter.execute(primary, name);
                } catch (PException e) {
                    e.expect(AttributeError, getCore(), errorProfile);
                    return defaultValue;
                }
            }
        }
    }

    // id(object)
    @Builtin(name = ID, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IdNode extends PythonBuiltinNode {
        /**
         * The reserved ids at the beginning.
         *
         * <pre>
         * None, NotImplemented, True, False
         * </pre>
         */
        private static long KNOWN_OBJECTS_COUNT = 4L;

        /**
         * The next available global id. We reserve space for all integers to be their own id +
         * offset.
         */
        private static long GLOBAL_ID = Integer.MAX_VALUE * 2 + 4L;
        private static HiddenKey idKey = new HiddenKey("object_id");

        @Child private ReadAttributeFromObjectNode readId = null;
        @Child private WriteAttributeToObjectNode writeId = null;

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

        /**
         * TODO: {@link #doId(String)} and {@link #doId(double)} are not quite right, because the
         * hashCode will certainly collide with integer hashes. It should be good for comparisons
         * between String and String id, though, it'll just look as if we interned all strings from
         * the Python code's perspective.
         */
        @Specialization
        int doId(String value) {
            return value.hashCode();
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

        @Specialization(guards = {"!isPInt(obj)", "!isPString(obj)", "!isPFloat(obj)"})
        Object doId(PythonObject obj) {
            return getId(obj);
        }

        @Fallback
        Object doId(Object obj) {
            return obj.hashCode();
        }

        private Object getId(PythonObject obj) {
            if (readId == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readId = insert(ReadAttributeFromObjectNode.create());
                writeId = insert(WriteAttributeToObjectNode.create());
            }
            Object id = readId.execute(obj, idKey);
            if (id == PNone.NO_VALUE) {
                id = GLOBAL_ID++;
                writeId.execute(obj, idKey, id);
            }
            return id;
        }
    }

    // isinstance(object, classinfo)
    @Builtin(name = ISINSTANCE, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class IsInstanceNode extends PythonBuiltinNode {
        @Child private GetClassNode getClassNode = GetClassNode.create();
        @Child private LookupAndCallBinaryNode instanceCheckNode = LookupAndCallBinaryNode.create(__INSTANCECHECK__);
        @Child private CastToBooleanNode castToBooleanNode = CastToBooleanNode.createIfTrueNode();
        @Child private TypeBuiltins.InstanceCheckNode typeInstanceCheckNode = TypeBuiltins.InstanceCheckNode.create();

        public static IsInstanceNode create() {
            return BuiltinFunctionsFactory.IsInstanceNodeFactory.create(null);
        }

        private boolean isInstanceCheckInternal(Object instance, Object cls) {
            Object instanceCheckResult = instanceCheckNode.executeObject(cls, instance);
            return instanceCheckResult != NOT_IMPLEMENTED && castToBooleanNode.executeWith(instanceCheckResult);
        }

        public abstract boolean executeWith(Object instance, Object cls);

        @Specialization
        public boolean isInstance(Object instance, PythonClass cls,
                        @Cached("create()") IsSubtypeNode isSubtypeNode) {
            PythonClass instanceClass = getClassNode.execute(instance);
            return instanceClass == cls || isSubtypeNode.execute(instanceClass, cls) || isInstanceCheckInternal(instance, cls);
        }

        @Specialization(guards = "clsTuple.len() == cachedLen", limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop
        public boolean isInstanceTupleConstantLen(Object instance, PTuple clsTuple,
                        @Cached("clsTuple.len()") int cachedLen,
                        @Cached("create()") IsInstanceNode isInstanceNode) {
            Object[] array = clsTuple.getArray();
            for (int i = 0; i < cachedLen; i++) {
                Object cls = array[i];
                if (isInstanceNode.executeWith(instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(replaces = "isInstanceTupleConstantLen")
        public boolean isInstance(Object instance, PTuple clsTuple,
                        @Cached("create()") IsInstanceNode instanceNode) {
            for (Object cls : clsTuple.getArray()) {
                if (instanceNode.executeWith(instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Fallback
        public boolean isInstance(Object instance, Object cls) {
            return isInstanceCheckInternal(instance, cls) || typeInstanceCheckNode.executeWith(cls, instance);
        }
    }

    // issubclass(class, classinfo)
    @Builtin(name = ISSUBCLASS, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class IsSubClassNode extends PythonBuiltinNode {
        @Child private LookupAndCallBinaryNode subclassCheckNode = LookupAndCallBinaryNode.create(__SUBCLASSCHECK__);
        @Child private CastToBooleanNode castToBooleanNode = CastToBooleanNode.createIfTrueNode();
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();

        public static IsSubClassNode create() {
            return BuiltinFunctionsFactory.IsSubClassNodeFactory.create(null);
        }

        private boolean isInstanceCheckInternal(Object derived, Object cls) {
            Object instanceCheckResult = subclassCheckNode.executeObject(cls, derived);
            return instanceCheckResult != NOT_IMPLEMENTED && castToBooleanNode.executeWith(instanceCheckResult);
        }

        public abstract boolean executeWith(Object derived, Object cls);

        @Specialization
        public boolean isSubclassType(Object derived, PythonClass cls) {
            return derived == cls || isSubtypeNode.execute(derived, cls);
        }

        @Specialization(guards = "clsTuple.len() == cachedLen", limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop
        public boolean isSubclassTupleConstantLen(Object derived, PTuple clsTuple,
                        @Cached("clsTuple.len()") int cachedLen,
                        @Cached("create()") IsSubClassNode isSubclassNode) {
            Object[] array = clsTuple.getArray();
            for (int i = 0; i < cachedLen; i++) {
                Object cls = array[i];
                if (isSubclassNode.executeWith(derived, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(replaces = "isSubclassTupleConstantLen")
        public boolean isSubclass(Object derived, PTuple clsTuple,
                        @Cached("create()") IsSubClassNode isSubclassNode) {
            for (Object cls : clsTuple.getArray()) {
                if (isSubclassNode.executeWith(derived, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Fallback
        public boolean isSubclass(Object derived, Object cls) {
            return isInstanceCheckInternal(derived, cls) || isSubtypeNode.execute(derived, cls);
        }
    }

    // iter(object[, sentinel])
    @Builtin(name = ITER, minNumOfArguments = 1, maxNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(sentinel)")
        public Object iter(Object object, @SuppressWarnings("unused") PNone sentinel,
                        @Cached("create()") GetIteratorNode getIterNode) {
            return getIterNode.executeWith(object);
        }

        @Specialization(guards = "!isNoValue(sentinel)")
        public Object iter(Object callable, Object sentinel) {
            return factory().createSentinelIterator(callable, sentinel);
        }
    }

    // len(s)
    @Builtin(name = LEN, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        private static Supplier<NoAttributeHandler> NO_LEN = () -> new NoAttributeHandler() {
            @Override
            public Object execute(Object receiver) {
                throw raise(TypeError, "object of type '%p' has no len()", receiver);
            }
        };

        public abstract Object executeWith(Object object);

        protected static LookupAndCallUnaryNode createLen() {
            return LookupAndCallUnaryNode.create(__LEN__, NO_LEN);
        }

        @Specialization
        public Object len(Object obj,
                        @Cached("createLen()") LookupAndCallUnaryNode dispatch) {
            return dispatch.executeObject(obj);
        }
    }

    public abstract static class MinMaxNode extends PythonBuiltinNode {

        protected final BinaryComparisonNode createComparison() {
            if (this instanceof MaxNode) {
                return BinaryComparisonNode.create(SpecialMethodNames.__GT__, SpecialMethodNames.__LT__, ">");
            } else {
                return BinaryComparisonNode.create(SpecialMethodNames.__LT__, SpecialMethodNames.__GT__, "<");
            }
        }

        @Specialization(guards = "args.length == 0")
        public Object maxSequence(PythonObject arg1, Object[] args, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile2) {
            return minmaxSequenceWithKey(arg1, args, null, getIterator, next, compare, null, errorProfile1, errorProfile2);
        }

        @Specialization(guards = "args.length == 0")
        public Object minmaxSequenceWithKey(PythonObject arg1, @SuppressWarnings("unused") Object[] args, PythonObject keywordArg,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("create()") CallNode keyCall,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile1,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile2) {
            Object iterator = getIterator.executeWith(arg1);
            Object currentValue;
            try {
                currentValue = next.execute(iterator);
            } catch (PException e) {
                e.expectStopIteration(getCore(), errorProfile1);
                throw raise(PythonErrorType.ValueError, "%s() arg is an empty sequence", this instanceof MaxNode ? "max" : "min");
            }
            Object currentKey = applyKeyFunction(keywordArg, keyCall, currentValue);
            while (true) {
                Object nextValue;
                try {
                    nextValue = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile2);
                    break;
                }
                Object nextKey = applyKeyFunction(keywordArg, keyCall, nextValue);
                if (compare.executeBool(nextKey, currentKey)) {
                    currentKey = nextKey;
                    currentValue = nextValue;
                }
            }
            return currentValue;
        }

        @Specialization(guards = "args.length != 0")
        public Object minmaxBinary(Object arg1, Object[] args, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("createBinaryProfile()") ConditionProfile moreThanTwo) {
            return minmaxBinaryWithKey(arg1, args, null, compare, null, moreThanTwo);
        }

        @Specialization(guards = "args.length != 0")
        public Object minmaxBinaryWithKey(Object arg1, Object[] args, PythonObject keywordArg,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("create()") CallNode keyCall,
                        @Cached("createBinaryProfile()") ConditionProfile moreThanTwo) {
            Object currentValue = arg1;
            Object currentKey = applyKeyFunction(keywordArg, keyCall, currentValue);
            Object nextValue = args[0];
            Object nextKey = applyKeyFunction(keywordArg, keyCall, nextValue);
            if (compare.executeBool(nextKey, currentKey)) {
                currentKey = nextKey;
                currentValue = nextValue;
            }
            if (moreThanTwo.profile(args.length > 1)) {
                for (int i = 0; i < args.length; i++) {
                    nextValue = args[i];
                    nextKey = applyKeyFunction(keywordArg, keyCall, nextValue);
                    if (compare.executeBool(nextKey, currentKey)) {
                        currentKey = nextKey;
                        currentValue = nextValue;
                    }
                }
            }
            return currentValue;
        }

        private static Object applyKeyFunction(PythonObject keywordArg, CallNode keyCall, Object currentValue) {
            return keyCall == null ? currentValue : keyCall.execute(keywordArg, new Object[]{currentValue}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // max(iterable, *[, key])
    // max(arg1, arg2, *args[, key])
    @Builtin(name = MAX, minNumOfArguments = 1, takesVariableArguments = true, keywordArguments = {"key"})
    @GenerateNodeFactory
    public abstract static class MaxNode extends MinMaxNode {

    }

    // min(iterable, *[, key])
    // min(arg1, arg2, *args[, key])
    @Builtin(name = MIN, minNumOfArguments = 1, takesVariableArguments = true, keywordArguments = {"key"})
    @GenerateNodeFactory
    public abstract static class MinNode extends MinMaxNode {

    }

    // next(iterator[, default])
    @SuppressWarnings("unused")
    @Builtin(name = NEXT, minNumOfArguments = 1, maxNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonBuiltinNode {

        @Specialization
        public Object next(PythonObject iterator, PNone defaultObject,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            try {
                return next.execute(iterator);
            } catch (PException e) {
                e.expect(AttributeError, getCore(), errorProfile);
                throw raise(TypeError, "'%p' object is not an iterator", iterator);
            }
        }

        @Specialization
        public Object next(Object iterator, Object defaultObject) {
            throw new RuntimeException("Unsupported iterator " + iterator);
        }
    }

    // ord(c)
    @Builtin(name = ORD, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class OrdNode extends PythonBuiltinNode {

        @Specialization
        public int ord(String chr) {
            if (chr.length() != 1) {
                raise(TypeError, "ord() expected a character, but string of length %d found", chr.length());
            }

            return chr.charAt(0);
        }
    }

    // print(*objects, sep=' ', end='\n', file=sys.stdout, flush=False)
    @Builtin(name = PRINT, fixedNumOfArguments = 5)
    @GenerateNodeFactory
    public abstract static class PrintNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object print(PTuple values, String sep, String end, Object file, boolean flush,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode callStr) {
            try {
                PythonContext context = getContext();
                if (values.len() == 0) {
                    write(context, end);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < values.len() - 1; i++) {
                        sb.append(callStr.executeObject(values.getItem(i)) + " ");
                    }
                    sb.append(callStr.executeObject(values.getItem(values.len() - 1)));
                    sb.append(end);
                    write(context, sb.toString());
                }
            } catch (IOException e) {
                // pass through
            }

            return PNone.NONE;
        }

        @TruffleBoundary
        private static void write(PythonContext context, String string) throws IOException {
            context.getStandardOut().write(string.getBytes());
        }
    }

    // repr(object)
    @Builtin(name = REPR, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonBuiltinNode {

        @Specialization
        public Object repr(Object obj,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprCallNode,
                        @Cached("createBinaryProfile()") ConditionProfile isString,
                        @Cached("createBinaryProfile()") ConditionProfile isPString) {

            Object result = reprCallNode.executeObject(obj);
            if (isString.profile(result instanceof String) || isPString.profile(result instanceof PString)) {
                return result;
            }
            throw getCore().raise(TypeError, "__repr__ returned non-string (type %p)", obj);
        }
    }

    // round(number[, ndigits])
    @Builtin(name = ROUND, minNumOfArguments = 1, maxNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBuiltinNode {
        @Specialization
        Object round(Object x, Object n,
                        @Cached("create(__ROUND__)") LookupAndCallBinaryNode callNode) {
            return callNode.executeObject(x, n);
        }
    }

    // setattr(object, name, value)
    @Builtin(name = SETATTR, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetAttrNode extends PythonBuiltinNode {
        @Specialization
        public Object setAttr(Object object, Object key, Object value,
                        @Cached("create()") SetAttributeNode setAttrNode) {
            return setAttrNode.execute(object, key, value);
        }
    }

    @Builtin(name = __BREAKPOINT__, fixedNumOfArguments = 0)
    @GenerateNodeFactory
    public abstract static class BreakPointNode extends PythonBuiltinNode {
        @Specialization
        public Object doIt() {
            return PNone.NONE;
        }
    }

    @Builtin(name = "__tdebug__", takesVariableArguments = true)
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

    // sum(iterable[, start])
    @Builtin(name = SUM, fixedNumOfArguments = 1, keywordArguments = {"start"})
    @GenerateNodeFactory
    public abstract static class SumFunctionNode extends PythonBuiltinNode {

        @Child private GetIteratorNode iter = GetIteratorNode.create();
        @Child private LookupAndCallUnaryNode next = LookupAndCallUnaryNode.create(__NEXT__);
        @Child private LookupAndCallBinaryNode add = BinaryArithmetic.Add.create();

        private final ConditionProfile errorProfile1 = ConditionProfile.createBinaryProfile();
        private final ConditionProfile errorProfile2 = ConditionProfile.createBinaryProfile();
        private final ConditionProfile errorProfile3 = ConditionProfile.createBinaryProfile();

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public int sumInt(Object arg1, @SuppressWarnings("unused") PNone start) throws UnexpectedResultException {
            return sumIntInternal(arg1, 0, false);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public int sumInt(Object arg1, int start) throws UnexpectedResultException {
            return sumIntInternal(arg1, start, true);
        }

        private int sumIntInternal(Object arg1, int start, boolean firstValProvided) throws UnexpectedResultException {
            Object iterator = iter.executeWith(arg1);
            int value = start;
            while (true) {
                int nextValue;
                try {
                    nextValue = next.executeInt(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile1);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = firstValProvided || value != start ? add.executeObject(value, e.getResult()) : e.getResult();
                    throw new UnexpectedResultException(iterateGeneric(iterator, newValue, errorProfile2));
                }
                try {
                    value = add.executeInt(value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(iterator, e.getResult(), errorProfile3));
                }
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public double sumDouble(Object arg1, @SuppressWarnings("unused") PNone start) throws UnexpectedResultException {
            return sumDoubleInternal(arg1, 0, false);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        public double sumDouble(Object arg1, double start) throws UnexpectedResultException {
            return sumDoubleInternal(arg1, start, true);
        }

        private double sumDoubleInternal(Object arg1, double start, boolean firstValProvided) throws UnexpectedResultException {
            Object iterator = iter.executeWith(arg1);
            double value = start;
            while (true) {
                double nextValue;
                try {
                    nextValue = next.executeDouble(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile1);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = firstValProvided || value != start ? add.executeObject(value, e.getResult()) : e.getResult();
                    throw new UnexpectedResultException(iterateGeneric(iterator, newValue, errorProfile2));
                }
                try {
                    value = add.executeDouble(value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(iterator, e.getResult(), errorProfile3));
                }
            }
        }

        @Specialization
        public Object sum(Object arg1, Object start,
                        @Cached("createBinaryProfile()") ConditionProfile hasStart) {
            Object iterator = iter.executeWith(arg1);
            return iterateGeneric(iterator, hasStart.profile(start != PNone.NO_VALUE) ? start : 0, errorProfile1);
        }

        private Object iterateGeneric(Object iterator, Object start, ConditionProfile errorProfile) {
            Object value = start;
            while (true) {
                Object nextValue;
                try {
                    nextValue = next.executeObject(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return value;
                }
                value = add.executeObject(value, nextValue);
            }
        }
    }

    @Builtin(name = "__load_builtins__", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LoadBuiltinsNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object doIt(String name) {
            PythonModule mod = getCore().isInitialized() ? getContext().getBuiltins() : getCore().lookupBuiltinModule("builtins");
            Source src = getCore().getCoreSource(name);
            PythonParseResult parsedModule = getCore().getParser().parse(getCore(), src);
            CallTarget callTarget = Truffle.getRuntime().createCallTarget(parsedModule.getRootNode());
            callTarget.call(PArguments.withGlobals(mod));
            return PNone.NONE;
        }
    }
}
