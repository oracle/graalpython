/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.ErrorMessages.INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins.DebugNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFileSystemEncodingNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiCodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ClearNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativePointer;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleReleaser;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode.SplitFormatStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public final class PythonCextBuiltins {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PythonCextBuiltins.class);

    /**
     * Certain builtin types like {@code int} cannot handle refcounts. They cannot be handed out to
     * the native side as borrowed references, since the handle would be collected immediately. (the
     * boxed int, for example, is not referenced from anyhwere). This node promotes these types to
     * full types like {@link PInt} and {@link PString}.
     */
    @GenerateUncached
    public abstract static class PromoteBorrowedValue extends Node {

        public abstract Object execute(Object value);

        @Specialization
        public static PString doString(TruffleString str,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(str);
        }

        @Specialization
        static PythonBuiltinObject doInteger(int i,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(i);
        }

        @Specialization
        static PythonBuiltinObject doLong(long i,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(i);
        }

        @Specialization(guards = "!isNaN(d)")
        static PythonBuiltinObject doDouble(double d,
                        @Cached PythonObjectFactory factory) {
            return factory.createFloat(d);
        }

        static boolean isNaN(double d) {
            return Double.isNaN(d);
        }

        @Fallback
        static PythonBuiltinObject doOther(@SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    public static PException checkThrowableBeforeNative(Throwable t, String where1, Object where2) {
        if (t instanceof PException pe) {
            // this is ok, and will be handled correctly
            throw pe;
        }
        if (t instanceof ThreadDeath td) {
            // ThreadDeath subclasses are used internally by Truffle
            throw td;
        }
        if (t instanceof StackOverflowError soe) {
            PythonContext context = PythonContext.get(null);
            context.reacquireGilAfterStackOverflow();
            PBaseException newException = context.factory().createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, EMPTY_OBJECT_ARRAY);
            PException pe = ExceptionUtils.wrapJavaException(soe, null, newException);
            throw pe;
        }
        if (t instanceof OutOfMemoryError oome) {
            PBaseException newException = PythonContext.get(null).factory().createBaseException(MemoryError);
            throw ExceptionUtils.wrapJavaException(oome, null, newException);
        }
        // everything else: log and convert to PException (SystemError)
        CompilerDirectives.transferToInterpreter();
        PNodeWithContext.printStack();
        PrintStream out = new PrintStream(PythonContext.get(null).getEnv().err());
        out.println("while executing " + where1 + " " + where2);
        out.println("should not throw exceptions apart from PException");
        t.printStackTrace(out);
        out.flush();
        throw PRaiseNode.raiseUncached(null, SystemError, ErrorMessages.INTERNAL_EXCEPTION_OCCURED);
    }

    public abstract static class CApiBuiltinNode extends PNodeWithRaiseAndIndirectCall {

        public abstract Object execute(Object[] args);

        protected final PythonNativePointer getNativeNull() {
            return getContext().getNativeNull();
        }

        /**
         * Returns the "NULL" pointer retrieved from the native backend, e.g., an LLVMPointer
         * instance. This is not wrapped, i.e., it cannot be passed through a PyObject
         * Python-To-Native transition (because it would be treated as a foreign Truffle object at
         * that point).
         */
        protected final Object getNULL() {
            return getContext().getNativeNull().getPtr();
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data, int offset, int length) {
            return ByteBuffer.wrap(data, offset, length);
        }

        @Child private PythonObjectFactory objectFactory;

        @CompilationFinal private ArgDescriptor ret;

        protected final PythonObjectFactory factory() {
            if (objectFactory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (isAdoptable()) {
                    objectFactory = insert(PythonObjectFactory.create());
                } else {
                    objectFactory = getCore().factory();
                }
            }
            return objectFactory;
        }

        public final Python3Core getCore() {
            return getContext();
        }

        protected final CApiContext getCApiContext() {
            return getContext().getCApiContext();
        }

        protected final PException badInternalCall(String argName) {
            CompilerDirectives.transferToInterpreter();
            throw raise(SystemError, ErrorMessages.S_S_BAD_ARG_TO_INTERNAL_FUNC, getName(), argName);
        }

        private String getName() {
            Class<?> c = getClass();
            while (c.getSimpleName().endsWith("NodeGen")) {
                c = c.getSuperclass();
            }
            String name = c.getSimpleName();
            return name;
        }

        @TruffleBoundary
        protected PException raiseFallback(Object obj, PythonBuiltinClassType type) {
            if (obj == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S, getName());
            }
            if (IsSubtypeNode.getUncached().execute(InlinedGetClassNode.executeUncached(obj), type)) {
                throw raise(NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, type.getName());
            } else {
                throw raise(SystemError, ErrorMessages.EXPECTED_S_NOT_P, type.getName(), obj);
            }
        }

        @TruffleBoundary
        protected PException raiseFallback(Object obj, PythonBuiltinClassType type1, PythonBuiltinClassType type2) {
            if (obj == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S, getName());
            }
            Object objType = InlinedGetClassNode.executeUncached(obj);
            if (IsSubtypeNode.getUncached().execute(objType, type1) || IsSubtypeNode.getUncached().execute(objType, type2)) {
                throw raise(NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, type1.getName());
            } else {
                throw raise(SystemError, ErrorMessages.EXPECTED_S_NOT_P, type1.getName(), obj);
            }
        }

        protected final ArgDescriptor getRetDescriptor() {
            return ret;
        }

        protected final int castToInt(long elementSize) {
            if (elementSize == (int) elementSize) {
                return (int) elementSize;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw raise(SystemError, INDEX_OUT_OF_RANGE);
        }

        public final Object getPosixSupport() {
            return getContext().getPosixSupport();
        }

        protected void checkNonNullArg(Object obj) {
            if (obj == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.NULL_ARG_INTERNAL);
            }
        }

        protected void checkNonNullArg(Object obj1, Object obj2) {
            if (obj1 == PNone.NO_VALUE || obj2 == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.NULL_ARG_INTERNAL);
            }
        }
    }

    public abstract static class CApiNullaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute();

        @Override
        public final Object execute(Object[] args) {
            return execute();
        }
    }

    public abstract static class CApiUnaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0]);
        }
    }

    public abstract static class CApiBinaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1]);
        }
    }

    public abstract static class CApiTernaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2]);
        }
    }

    public abstract static class CApiQuaternaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3]);
        }
    }

    public abstract static class CApi5BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4]);
        }
    }

    public abstract static class CApi6BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5]);
        }
    }

    public abstract static class CApi7BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        }
    }

    public abstract static class CApi8BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
        }
    }

    public abstract static class CApi9BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
        }
    }

    public abstract static class CApi10BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
        }
    }

    public abstract static class CApi11BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10]);
        }
    }

    public abstract static class CApi15BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11,
                        Object arg12, Object arg13, Object arg14);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14]);
        }
    }

    public abstract static class CApi16BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11,
                        Object arg12, Object arg13, Object arg14, Object arg15);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class CApiBuiltinExecutable implements TruffleObject {

        private final CApiTiming timing;
        private final ArgDescriptor ret;
        private final ArgDescriptor[] args;
        @CompilationFinal private CallTarget callTarget;
        private final CApiCallPath call;
        private final String name;
        private final int id;

        public CApiBuiltinExecutable(String name, CApiCallPath call, ArgDescriptor ret, ArgDescriptor[] args, int id) {
            this.timing = CApiTiming.create(false, name);
            this.name = name;
            this.call = call;
            this.ret = ret;
            this.args = args;
            this.id = id;
        }

        CallTarget getCallTarget() {
            if (callTarget == null) {
                CompilerDirectives.transferToInterpreter();
                CompilerDirectives.shouldNotReachHere("call target slow path not implemented");
            }
            return callTarget;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        public CApiCallPath call() {
            return call;
        }

        public String name() {
            return name;
        }

        CExtToNativeNode createRetNode() {
            return ret.createPythonToNativeNode();
        }

        ArgDescriptor getRetDescriptor() {
            return ret;
        }

        CExtToJavaNode[] createArgNodes() {
            return ArgDescriptor.createNativeToPython(args);
        }

        public CApiBuiltinNode createBuiltinNode() {
            CApiBuiltinNode node = PythonCextBuiltinRegistry.createBuiltinNode(id);
            node.ret = ret;
            return node;
        }

        public CApiBuiltinNode getUncachedNode() {
            // TODO: how to set "node.ret"?
            throw CompilerDirectives.shouldNotReachHere("not supported - uncached for " + name);
        }

        @ExportMessage
        static final class Execute {
            @Specialization(guards = "self == cachedSelf")
            public static Object doExecute(@SuppressWarnings("unused") CApiBuiltinExecutable self, Object[] arguments,
                            @Cached("self") CApiBuiltinExecutable cachedSelf,
                            @Cached(parameters = "cachedSelf") ExecuteCApiBuiltinNode call) {

                try {
                    return call.execute(cachedSelf, arguments);
                } catch (Throwable t) {
                    CompilerDirectives.transferToInterpreter();
                    t.printStackTrace();
                    throw CompilerDirectives.shouldNotReachHere(t);
                }
            }

            @Specialization
            public static Object doFallback(@SuppressWarnings("unused") CApiBuiltinExecutable self, @SuppressWarnings("unused") Object[] arguments) {
                CompilerDirectives.transferToInterpreter();
                throw CompilerDirectives.shouldNotReachHere("shouldn't hit generic case of " + Execute.class.getName());
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean isPointer() {
            long pointer = PythonContext.get(null).getCApiContext().getClosurePointer(this);
            return pointer != -1;
        }

        @ExportMessage
        @TruffleBoundary
        long asPointer() throws UnsupportedMessageException {
            long pointer = PythonContext.get(null).getCApiContext().getClosurePointer(this);
            if (pointer == -1) {
                throw UnsupportedMessageException.create();
            }
            return pointer;
        }

        private static final class SignatureContainerRootNode extends RootNode {

            final HashMap<String, SignatureLibrary> libs = new HashMap<>();

            protected SignatureContainerRootNode() {
                super(null);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                throw CompilerDirectives.shouldNotReachHere("not meant to be executed");
            }

            public SignatureLibrary getLibrary(String name) {
                return libs.computeIfAbsent(name, n -> {
                    SignatureLibrary lib = SignatureLibrary.getFactory().createDispatched(3);
                    SignatureContainerRootNode.this.insert(lib);
                    return lib;
                });
            }
        }

        @ExportMessage
        @TruffleBoundary
        void toNative() {
            CApiContext context = PythonContext.get(null).getCApiContext();
            long pointer = context.getClosurePointer(this);
            if (pointer == -1) {
                if (context.signatureContainer == null) {
                    context.signatureContainer = new SignatureContainerRootNode().getCallTarget();
                }

                try {
                    SignatureContainerRootNode container = (SignatureContainerRootNode) context.signatureContainer.getRootNode();
                    // create NFI closure and get its address
                    boolean panama = PythonOptions.UsePanama.getValue(PythonContext.get(null).getEnv().getOptions());
                    StringBuilder signature = new StringBuilder(panama ? "with panama (" : "(");
                    for (int i = 0; i < args.length; i++) {
                        signature.append(i == 0 ? "" : ",");
                        signature.append(args[i].getNFISignature());
                    }
                    signature.append("):").append(ret.getNFISignature());

                    Object nfiSignature = PythonContext.get(null).getEnv().parseInternal(Source.newBuilder(J_NFI_LANGUAGE, signature.toString(), "exec").build()).call();
                    Object closure = container.getLibrary(name).createClosure(nfiSignature, this);
                    InteropLibrary.getUncached().toNative(closure);
                    try {
                        pointer = InteropLibrary.getUncached().asPointer(closure);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    context.setClosurePointer(closure, null, this, pointer);
                    LOGGER.finer(CApiBuiltinExecutable.class.getSimpleName() + " toNative: " + id + " / " + name() + " -> " + pointer);
                } catch (Throwable t) {
                    t.printStackTrace(new PrintStream(PythonContext.get(null).getEnv().err()));
                    throw t;
                }
            }
        }

        @Override
        public String toString() {
            return "CApiBuiltin(" + name + " / " + id + ")";
        }
    }

    @GenerateUncached
    abstract static class ExecuteCApiBuiltinNode extends Node {
        abstract Object execute(CApiBuiltinExecutable self, Object[] arguments);

        public static ExecuteCApiBuiltinNode create(CApiBuiltinExecutable self) {
            try {
                return new CachedExecuteCApiBuiltinNode(self);
            } catch (Throwable t) {
                PNodeWithContext.printStack();
                LOGGER.logp(Level.SEVERE, "ExecuteCApiBuiltinNode", "create", "while creating CApiBuiltin " + self.name, t);
                throw t;
            }
        }

        public static ExecuteCApiBuiltinNode getUncached(@SuppressWarnings("unused") CApiBuiltinExecutable self) {
            return UncachedExecuteCApiBuiltinNode.INSTANCE;
        }
    }

    static final class CachedExecuteCApiBuiltinNode extends ExecuteCApiBuiltinNode {
        private final CApiBuiltinExecutable cachedSelf;
        @Child private CExtToNativeNode retNode;
        @Children private final CExtToJavaNode[] argNodes;
        @Child private CApiBuiltinNode builtinNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        CachedExecuteCApiBuiltinNode(CApiBuiltinExecutable cachedSelf) {
            assert cachedSelf.ret.createCheckResultNode() == null : "primitive result check types are only intended for ExternalFunctionInvokeNode";
            this.cachedSelf = cachedSelf;
            this.retNode = cachedSelf.createRetNode();
            this.argNodes = cachedSelf.createArgNodes();
            this.builtinNode = cachedSelf.createBuiltinNode();
        }

        @Override
        Object execute(CApiBuiltinExecutable self, Object[] arguments) {
            CApiTiming.enter();
            try {
                try {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(() -> "CAPI-" + self.name + " " + Arrays.toString(arguments));
                    }
                    assert cachedSelf == self;
                    assert arguments.length == argNodes.length;

                    Object[] argCast = new Object[argNodes.length];
                    castArguments(arguments, argCast);
                    Object result = builtinNode.execute(argCast);
                    if (retNode != null) {
                        result = retNode.execute(result);
                    }
                    assert !(result instanceof PythonNativePointer);
                    CApiTransitions.maybeGCALot();
                    return result;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "CApiBuiltin", self.name);
                }
            } catch (PException e) {
                if (transformExceptionToNativeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
                }
                transformExceptionToNativeNode.execute(e);
                if (cachedSelf.getRetDescriptor().isIntType()) {
                    return -1;
                } else if (cachedSelf.getRetDescriptor().isPyObjectOrPointer()) {
                    return PythonContext.get(this).getNativeNull().getPtr();
                } else if (cachedSelf.getRetDescriptor().isFloatType()) {
                    return -1.0;
                } else if (cachedSelf.getRetDescriptor().isVoid()) {
                    return PNone.NO_VALUE;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw CompilerDirectives.shouldNotReachHere("return type while handling PException: " + cachedSelf.getRetDescriptor() + " in " + self.name);
                }
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @ExplodeLoop
        private void castArguments(Object[] arguments, Object[] argCast) {
            for (int i = 0; i < argNodes.length; i++) {
                argCast[i] = argNodes[i] == null ? arguments[i] : argNodes[i].execute(arguments[i]);
            }
        }
    }

    static final class UncachedExecuteCApiBuiltinNode extends ExecuteCApiBuiltinNode {

        static final UncachedExecuteCApiBuiltinNode INSTANCE = new UncachedExecuteCApiBuiltinNode();

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        Object execute(CApiBuiltinExecutable self, Object[] arguments) {
            return IndirectCallNode.getUncached().call(self.getCallTarget(), arguments);
        }
    }

    /**
     * How the call is routed from native code to the Java CApiBuiltin implementation, i.e., whether
     * there needs to be some intermediate C code.
     */
    public enum CApiCallPath {
        /**
         * The Java code of this builtin can be called without any intermediate C code - a call stub
         * will be generated as appropriate.
         */
        Direct,
        /**
         * This builtin has an explicit C implementation that can be executed both from native and
         * from Sulong - no automatic stub will be generated.
         */
        CImpl,
        /**
         * This builtin has an explicit C implementation that needs to be executed in Sulong - an
         * automatic stub will be generated for native calls.
         */
        PolyglotImpl,
        /**
         * This builtin is not implemented - create an empty stub that raises an error.
         */
        NotImplemented,
        /**
         * This builtin is not part of the Python C API, no proxy is generated.
         */
        Ignored,
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CApiBuiltins {
        CApiBuiltin[] value();
    }

    /**
     * Builtin implementations in the C API (or helpers for implementing builtins with C code) are
     * marked with this annotation. The information in the annotation allows code generation
     * ({@link CApiCodeGen}), argument conversions (based on {@link ArgDescriptor}s), and
     * verification of the C API implementation in general.
     *
     * Apart from being placed on classes that implement {@link CApiBuiltinNode}, this annotation is
     * also used in {@link CApiFunction} to list all functions that are implemented in C code or
     * that are not currently implemented.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(value = CApiBuiltins.class)
    public @interface CApiBuiltin {

        /**
         * Name of this builtin - the name can be omitted, which will use the name of the class that
         * this annotation is applied to.
         */
        String name() default "";

        ArgDescriptor ret();

        ArgDescriptor[] args() default {};

        /**
         * This will generate a builtin with the name suffix "_Inlined" on the native side
         * ({@code capi_forwards.h}), which can be useful if a builtin needs a different
         * implementation on the native side, in {@code capi_native.c}, which may in some cases
         * forward to the original implementation.
         */
        boolean inlined() default false;

        boolean acquiresGIL() default true;

        /**
         * @see CApiCallPath
         */
        CApiCallPath call();

        /**
         * Specifies a va_list function that this builtin can be forwarded to, e.g., "PyErr_FormatV"
         * for "PyErr_Format".
         */
        String forwardsTo() default "";

        /**
         * Comment to explain, e.g., why a builtin is ignored.
         */
        String comment() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CApiSymbols {
        CApiSymbol[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(value = CApiSymbols.class)
    public @interface CApiSymbol {

        String name() default "";

        ArgDescriptor type();
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @CApiBuiltin(ret = Pointer, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleToCharPointer extends CApiUnaryBuiltinNode {

        @Specialization(guards = "isString(str)")
        static Object run(Object str,
                        @Cached AsCharPointerNode asCharPointerNode) {
            return asCharPointerNode.execute(str);
        }

        @Fallback
        Object run(Object o) {
            throw raise(PythonErrorType.SystemError, ErrorMessages.CANNOT_CONVERT_OBJ_TO_C_STRING, o, o.getClass().getName());
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffle_FileSystemDefaultEncoding extends CApiNullaryBuiltinNode {
        @Specialization
        static TruffleString encoding() {
            return GetFileSystemEncodingNode.getFileSystemEncoding();
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffle_Type extends CApiUnaryBuiltinNode {

        private static final TruffleString[] LOOKUP_MODULES = new TruffleString[]{
                        T__WEAKREF,
                        T_BUILTINS
        };

        @Specialization
        Object doI(TruffleString typeName,
                        @Cached TruffleString.EqualNode eqNode) {
            Python3Core core = getCore();
            for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
                if (eqNode.execute(type.getName(), typeName, TS_ENCODING)) {
                    return core.lookupType(type);
                }
            }
            for (TruffleString module : LOOKUP_MODULES) {
                Object attribute = core.lookupBuiltinModule(module).getAttribute(typeName);
                if (attribute != PNone.NO_VALUE) {
                    return attribute;
                }
            }
            throw raise(PythonErrorType.KeyError, ErrorMessages.APOSTROPHE_S, typeName);
        }
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    abstract static class CreateFunctionNode extends PNodeWithContext {

        abstract Object execute(TruffleString name, Object callable, Object wrapper, Object type, Object flags, PythonObjectFactory factory);

        @Specialization(guards = {"!isNoValue(type)", "isNoValue(wrapper)"})
        static Object doPythonCallableWithoutWrapper(@SuppressWarnings("unused") TruffleString name, PythonNativeWrapper callable,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object type,
                        @SuppressWarnings("unused") Object flags,
                        @SuppressWarnings("unused") PythonObjectFactory factory) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            return callable.getDelegate();
        }

        @Specialization(guards = "!isNoValue(type)")
        @TruffleBoundary
        Object doPythonCallable(TruffleString name, PythonNativeWrapper callable, int signature, Object type, int flags, PythonObjectFactory factory) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            Object managedCallable = callable.getDelegate();
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags, signature, getLanguage(), factory, false);
            return function != null ? function : managedCallable;
        }

        @Specialization(guards = {"!isNativeWrapper(callable)"})
        @TruffleBoundary
        Object doNativeCallableWithWrapper(TruffleString name, Object callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib) {
            /*
             * This can happen if a native type inherits slots from a managed type. For example, if
             * a native type inherits 'base->tp_richcompare' and this is '__truffle_richcompare__'
             * and we are going to install it as '__eq__', we still need to have a wrapper around
             * the managed callable since we need to bind the 3rd argument.
             */
            Object resolvedCallable = resolveClosurePointer(getContext(), callable, lib);
            boolean doArgAndResultConversion;
            if (resolvedCallable != null) {
                doArgAndResultConversion = false;
            } else {
                doArgAndResultConversion = true;
                resolvedCallable = callable;
            }
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, resolvedCallable, type, flags, signature, getLanguage(), factory, doArgAndResultConversion);
            return function != null ? function : resolvedCallable;
        }

        @Specialization(guards = {"isNoValue(wrapper)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapper(TruffleString name, Object callable, Object type, @SuppressWarnings("unused") PNone wrapper, @SuppressWarnings("unused") Object flags,
                        PythonObjectFactory factory,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib) {
            /*
             * This can happen if a native type inherits slots from a managed type. Therefore,
             * something like 'base->tp_new' will be a wrapper of the managed '__new__'. In this
             * case, we can just return the managed callable since we do also not have a wrapper
             * that could shuffle or bind arguments.
             */
            PBuiltinFunction managedCallable = resolveClosurePointer(getContext(), callable, lib);
            if (managedCallable != null) {
                return managedCallable;
            }
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, 0, PExternalFunctionWrapper.DIRECT, getLanguage(), factory, true);
        }

        private static PBuiltinFunction resolveClosurePointer(PythonContext context, Object callable, InteropLibrary lib) {
            if (lib.isPointer(callable)) {
                long pointer;
                try {
                    pointer = lib.asPointer(callable);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                Object delegate = context.getCApiContext().getClosureDelegate(pointer);
                if (delegate instanceof PBuiltinFunction function) {
                    LOGGER.fine(() -> PythonUtils.formatJString("forwarding %d 0x%x to %s", pointer, pointer, function));
                    return function;
                }
            }
            return null;
        }
    }

    abstract static class PyObjectSetAttrNode extends PNodeWithContext {

        abstract Object execute(Object object, TruffleString key, Object value);

        @Specialization
        static Object doBuiltinClass(PythonBuiltinClass object, TruffleString key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization
        static Object doNativeClass(PythonNativeClass object, TruffleString key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPythonBuiltinClass(object)"})
        static Object doObject(PythonObject object, TruffleString key, Object value,
                        @Exclusive @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode) {
            writeAttrToDynamicObjectNode.execute(object.getStorage(), key, value);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, Pointer, Pointer}, call = Ignored)
    abstract static class PyTruffle_Set_Native_Slots extends CApiTernaryBuiltinNode {
        static final HiddenKey NATIVE_SLOTS = new HiddenKey("__native_slots__");

        @Specialization
        static int doPythonClass(PythonClass pythonClass, Object nativeGetSets, Object nativeMembers,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(pythonClass, NATIVE_SLOTS, new Object[]{nativeGetSets, nativeMembers});
            return 0;
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffle_Get_Inherited_Native_Slots extends CApiBinaryBuiltinNode {
        private static final int INDEX_GETSETS = 0;
        private static final int INDEX_MEMBERS = 1;

        private static final TruffleString T_MEMBERS = tsLiteral("members");
        private static final TruffleString T_GETSETS = tsLiteral("getsets");

        /**
         * A native class may inherit from a managed class. However, the managed class may define
         * custom slots at a time where the C API is not yet loaded. So we need to check if any of
         * the base classes defines custom slots and adapt the basicsize to allocate space for the
         * slots and add the native member slot descriptors.
         */
        @Specialization
        Object slots(Object pythonClass, TruffleString subKey,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached TruffleString.EqualNode eqNode) {
            int idx;
            if (eqNode.execute(T_GETSETS, subKey, TS_ENCODING)) {
                idx = INDEX_GETSETS;
            } else if (eqNode.execute(T_MEMBERS, subKey, TS_ENCODING)) {
                idx = INDEX_MEMBERS;
            } else {
                return getNULL();
            }

            Object[] values = collect(getMroStorageNode.execute(pythonClass), idx);
            return new PySequenceArrayWrapper(factory().createTuple(values), Long.BYTES);
        }

        @TruffleBoundary
        private static Object[] collect(MroSequenceStorage mro, int idx) {
            ArrayList<Object> l = new ArrayList<>();
            int mroLength = mro.length();
            for (int i = 0; i < mroLength; i++) {
                PythonAbstractClass kls = mro.getItemNormalized(i);
                Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(kls, PyTruffle_Set_Native_Slots.NATIVE_SLOTS);
                if (value != PNone.NO_VALUE) {
                    Object[] tuple = (Object[]) value;
                    assert tuple.length == 2;
                    l.add(new PythonNativePointer(tuple[idx]));
                }
            }
            return l.toArray();
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {PyThreadState, PyCodeObject, PyObject, PyObject}, call = Direct)
    abstract static class PyFrame_New extends CApiQuaternaryBuiltinNode {
        @Specialization
        Object newFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
            Object frameLocals;
            if (locals == null || PGuards.isPNone(locals)) {
                frameLocals = factory().createDict();
            } else {
                frameLocals = locals;
            }
            return factory().createPFrame(threadState, code, globals, frameLocals);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyTypeObject, Pointer}, call = Ignored)
    abstract static class PyTruffle_Set_SulongType extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doPythonObject(PythonManagedClass klass, Object ptr) {
            klass.setSulongType(ptr);
            return ptr;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, PyObject, Py_ssize_t, Int, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Pointer, Pointer, Pointer, Pointer}, call = Ignored)
    abstract static class PyTruffle_MemoryViewFromBuffer extends CApi11BuiltinNode {

        @Specialization
        Object wrap(Object bufferStructPointer, Object ownerObj, Object lenObj,
                        Object readonlyObj, Object itemsizeObj, TruffleString format,
                        Object ndimObj, Object bufPointer, Object shapePointer, Object stridesPointer, Object suboffsetsPointer,
                        @Cached ConditionProfile zeroDimProfile,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
            try {
                int ndim = castToIntNode.execute(ndimObj);
                int itemsize = castToIntNode.execute(itemsizeObj);
                int len = castToIntNode.execute(lenObj);
                boolean readonly = castToIntNode.execute(readonlyObj) != 0;
                Object owner = ownerObj instanceof PythonNativePointer ? null : ownerObj;
                int[] shape = null;
                int[] strides = null;
                int[] suboffsets = null;
                if (zeroDimProfile.profile(ndim > 0)) {
                    if (!lib.isNull(shapePointer)) {
                        shape = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            shape[i] = castToIntNode.execute(lib.readArrayElement(shapePointer, i));
                        }
                    } else {
                        assert ndim == 1;
                        shape = new int[1];
                        shape[0] = len / itemsize;
                    }
                    if (!lib.isNull(stridesPointer)) {
                        strides = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            strides[i] = castToIntNode.execute(lib.readArrayElement(stridesPointer, i));
                        }
                    } else {
                        strides = PMemoryView.initStridesFromShape(ndim, itemsize, shape);
                    }
                    if (!lib.isNull(suboffsetsPointer)) {
                        suboffsets = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            suboffsets[i] = castToIntNode.execute(lib.readArrayElement(suboffsetsPointer, i));
                        }
                    }
                }
                Object buffer = NativeSequenceStorage.create(bufPointer, len, len, SequenceStorage.ListStorageType.Byte, false);
                int flags = initFlagsNode.execute(ndim, itemsize, shape, strides, suboffsets);
                BufferLifecycleManager bufferLifecycleManager = null;
                if (!lib.isNull(bufferStructPointer)) {
                    bufferLifecycleManager = new NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromType(bufferStructPointer);
                }
                return factory().createMemoryView(getContext(), bufferLifecycleManager, buffer, owner, len, readonly, itemsize,
                                BufferFormat.forMemoryView(format, lengthNode, atIndexNode), format, ndim, bufPointer, 0, shape, strides, suboffsets, flags);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    abstract static class PyTruffle_Register_NULL extends CApiUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object) {
            PythonNativePointer nn = getContext().getNativeNull();
            nn.setPtr(object);
            return PNone.NO_VALUE;
        }
    }

    @ReportPolymorphism
    abstract static class CastArgsNode extends PNodeWithContext {

        public abstract Object[] execute(VirtualFrame frame, Object argsObj);

        @Specialization(guards = "isNoValue(args)")
        @SuppressWarnings("unused")
        static Object[] doNull(PNone args) {
            return EMPTY_OBJECT_ARRAY;
        }

        @Specialization(guards = "!isNoValue(args)")
        static Object[] doNotNull(VirtualFrame frame, Object args,
                        @Cached ExecutePositionalStarargsNode expandArgsNode) {
            return expandArgsNode.executeWith(frame, args);
        }
    }

    @ReportPolymorphism
    abstract static class CastKwargsNode extends PNodeWithContext {

        public abstract PKeyword[] execute(Object kwargsObj);

        @Specialization(guards = "isNoValue(kwargs)")
        @SuppressWarnings("unused")
        static PKeyword[] doNoKeywords(Object kwargs) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = "!isNoValue(kwargs)")
        static PKeyword[] doKeywords(Object kwargs,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode) {
            return expandKwargsNode.execute(kwargs);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer}, call = Ignored)
    abstract static class PyTruffle_Arg_ParseTupleAndKeywords extends CApi5BuiltinNode {

        @Specialization
        static int doConvert(Object argv, Object nativeKwds, TruffleString formatString, Object nativeKwdnames, Object varargs,
                        @Cached SplitFormatStringNode splitFormatStringNode,
                        @CachedLibrary(limit = "2") InteropLibrary kwdnamesRefLib,
                        @Cached ConditionProfile kwdsProfile,
                        @Cached ConditionProfile kwdnamesProfile,
                        @Cached CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {
            // force 'format' to be a String
            TruffleString[] split;
            try {
                split = splitFormatStringNode.execute(formatString);
                assert split.length == 2;
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }

            TruffleString format = split[0];
            TruffleString functionName = split[1];

            // sort out if kwds is native NULL
            Object kwds;
            if (kwdsProfile.profile(PGuards.isNoValue(nativeKwds))) {
                kwds = null;
            } else {
                kwds = nativeKwds;
            }

            // sort out if kwdnames is native NULL
            Object kwdnames = kwdnamesProfile.profile(kwdnamesRefLib.isNull(nativeKwdnames)) ? null : nativeKwdnames;

            return parseTupleAndKeywordsNode.execute(functionName, argv, kwds, format, kwdnames, varargs);
        }
    }

    @CApiBuiltin(ret = SIZE_T, args = {}, call = Ignored)
    abstract static class PyTruffle_MaxNativeMemory extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long get() {
            return PythonOptions.MaxNativeMemory.getValue(getContext().getEnv().getOptions());
        }
    }

    @CApiBuiltin(ret = SIZE_T, args = {}, call = Ignored)
    abstract static class PyTruffle_InitialNativeMemory extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long get() {
            return PythonOptions.InitialNativeMemory.getValue(getContext().getEnv().getOptions());
        }
    }

    @CApiBuiltin(ret = Void, args = {SIZE_T}, call = Ignored)
    abstract static class PyTruffle_TriggerGC extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object trigger(long delay) {
            LOGGER.fine("full GC due to native memory");
            PythonUtils.forceFullGC();
            try {
                Thread.sleep(delay);
            } catch (InterruptedException x) {
                // Restore interrupt status
                Thread.currentThread().interrupt();
            }
            CApiTransitions.pollReferenceQueue();
            PythonContext.triggerAsyncActions(this);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffle_Object_Free extends CApiUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffle_Object_Free.class);

        @Specialization(guards = "!isCArrayWrapper(nativeWrapper)")
        static PNone doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @Cached ClearNativeWrapperNode clearNativeWrapperNode,
                        @Cached PCallCapiFunction callReleaseHandleNode) {
            // if (nativeWrapper.getRefCount() > 0) {
            // CompilerDirectives.transferToInterpreterAndInvalidate();
            // throw new IllegalStateException("deallocating native object with refcnt > 0");
            // }

            // clear native wrapper
            Object delegate = nativeWrapper.getDelegate();
            clearNativeWrapperNode.execute(delegate, nativeWrapper);

            doNativeWrapper(nativeWrapper, callReleaseHandleNode);
            return PNone.NO_VALUE;
        }

        @Specialization
        static PNone arrayWrapper(@SuppressWarnings("unused") CArrayWrapper object) {
            // It's a pointer to a managed object but doesn't need special handling, so we just
            // ignore it.
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNativeWrapper(object)")
        static PNone doOther(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere("Attempted to free a managed object");
        }

        protected static boolean isCArrayWrapper(Object obj) {
            return obj instanceof CArrayWrapper;
        }

        static void doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @Cached PCallCapiFunction callReleaseHandleNode) {

            // If wrapper already received toNative, release the handle or free the native
            // memory.
            if (nativeWrapper.isNative()) {
                // We do not call 'truffle_release_handle' directly because we still want to
                // support
                // native wrappers that have a real native pointer. 'PyTruffle_Free' does the
                // necessary distinction.
                long nativePointer = nativeWrapper.getNativePointer();
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(() -> PythonUtils.formatJString("Releasing handle: %x (object: %s)", nativePointer, nativeWrapper));
                }
                if (HandlePointerConverter.pointsToPyHandleSpace(nativePointer)) {
                    HandleReleaser.release(nativePointer);
                } else {
                    callReleaseHandleNode.call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, nativePointer);
                }
            }
        }
    }

    @CApiBuiltin(ret = Int, args = {UNSIGNED_INT, UINTPTR_T, SIZE_T}, call = Direct)
    @ImportStatic(CApiGuards.class)
    abstract static class PyTraceMalloc_Track extends CApiTernaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTraceMalloc_Track.class);

        @Specialization(guards = {"isSingleContext()", "domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(@SuppressWarnings("unused") int domain, Object pointerObject, long size,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @CachedLibrary("pointerObject") InteropLibrary lib,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            // this will also be called if the allocation failed
            if (!lib.isNull(pointerObject)) {
                CApiContext cApiContext = getCApiContext();
                Object key = CApiContext.asPointer(pointerObject, lib);
                cApiContext.getTraceMallocDomain(cachedDomainIdx).track(key, size);
                cApiContext.increaseMemoryPressure(null, getThreadStateNode, this, size);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(() -> PythonUtils.formatJString("Tracking memory (size: %d): %s", size, CApiContext.asHex(key)));
                }
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx", limit = "3")
        int doGeneric(int domain, Object pointerObject, long size,
                        @CachedLibrary("pointerObject") InteropLibrary lib,
                        @Cached GetThreadStateNode getThreadStateNode) {
            return doCachedDomainIdx(domain, pointerObject, size, getThreadStateNode, lib, domain, lookupDomain(domain));
        }

        int lookupDomain(int domain) {
            return getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @CApiBuiltin(ret = Int, args = {UNSIGNED_INT, UINTPTR_T}, call = Direct)
    @ImportStatic(CApiGuards.class)
    abstract static class PyTraceMalloc_Untrack extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTraceMalloc_Untrack.class);

        @Specialization(guards = {"isSingleContext()", "domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(@SuppressWarnings("unused") int domain, Object pointerObject,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx,
                        @CachedLibrary("pointerObject") InteropLibrary lib) {

            CApiContext cApiContext = getCApiContext();
            Object key = CApiContext.asPointer(pointerObject, lib);
            long trackedMemorySize = cApiContext.getTraceMallocDomain(cachedDomainIdx).untrack(key);
            cApiContext.reduceMemoryPressure(trackedMemorySize);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> PythonUtils.formatJString("Untracking memory (size: %d): %s", trackedMemorySize, CApiContext.asHex(key)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(int domain, Object pointerObject,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return doCachedDomainIdx(domain, pointerObject, domain, lookupDomain(domain), lib);
        }

        int lookupDomain(int domain) {
            return getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class _PyTraceMalloc_NewReference extends CApiUnaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static int doCachedDomainIdx(Object pointerObject) {
            // TODO(fa): implement; capture tracebacks in PyTraceMalloc_Track and update them
            // here
            return 0;
        }
    }

    abstract static class PyTruffleGcTracingNode extends CApiUnaryBuiltinNode {

        @Specialization(guards = {"!traceCalls(getContext())", "traceMem(getContext())"})
        Object doNativeWrapper(Object ptr,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            trace(getContext(), CApiContext.asPointer(ptr, lib), null, null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = {"traceCalls(getContext())", "traceMem(getContext())"})
        Object doNativeWrapperTraceCall(Object ptr,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {

            PFrame.Reference ref = getCurrentFrameRef.execute(null);
            trace(getContext(), CApiContext.asPointer(ptr, lib), ref, null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!traceMem(getContext())")
        static Object doNothing(@SuppressWarnings("unused") Object ptr) {
            // do nothing
            return PNone.NO_VALUE;
        }

        @NonIdempotent
        static boolean traceMem(PythonContext context) {
            return context.getOption(PythonOptions.TraceNativeMemory);
        }

        @NonIdempotent
        static boolean traceCalls(PythonContext context) {
            return context.getOption(PythonOptions.TraceNativeMemoryCalls);
        }

        @SuppressWarnings("unused")
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("should not reach");
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Direct)
    abstract static class PyObject_GC_UnTrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyObject_GC_UnTrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            LOGGER.finer(() -> PythonUtils.formatJString("Untracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().untrackObject(ptr, ref, className);
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Direct)
    abstract static class PyObject_GC_Track extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyObject_GC_Track.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            LOGGER.finer(() -> PythonUtils.formatJString("Tracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().trackObject(ptr, ref, className);
        }
    }

    private static final int TRACE_MEM = 0x1;
    private static final int LOG_INFO = 0x2;
    private static final int LOG_CONFIG = 0x4;
    private static final int LOG_FINE = 0x8;
    private static final int LOG_FINER = 0x10;
    private static final int LOG_FINEST = 0x20;

    @CApiBuiltin(ret = Int, call = Ignored)
    abstract static class PyTruffle_Native_Options extends CApiNullaryBuiltinNode {

        @Specialization
        int getNativeOptions() {
            int options = 0;
            if (getContext().getOption(PythonOptions.TraceNativeMemory)) {
                options |= TRACE_MEM;
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                options |= LOG_INFO;
            }
            if (LOGGER.isLoggable(Level.CONFIG)) {
                options |= LOG_CONFIG;
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                options |= LOG_FINE;
            }
            if (LOGGER.isLoggable(Level.FINER)) {
                options |= LOG_FINER;
            }
            if (LOGGER.isLoggable(Level.FINEST)) {
                options |= LOG_FINEST;
            }
            return options;
        }
    }

    @CApiBuiltin(ret = Void, args = {Int, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffle_LogString extends CApiBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object log(int level, TruffleString message) {
            String msg = message.toJavaStringUncached();
            switch (level) {
                case LOG_INFO:
                    LOGGER.info(msg);
                    break;
                case LOG_CONFIG:
                    LOGGER.config(msg);
                    break;
                case LOG_FINE:
                    LOGGER.fine(msg);
                    break;
                case LOG_FINER:
                    LOGGER.finer(msg);
                    break;
                case LOG_FINEST:
                    LOGGER.finest(msg);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("unknown log level: " + level);
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {}, call = Direct)
    abstract static class PyTruffle_DebugTrace extends CApiNullaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object trace() {
            PrintStream out = new PrintStream(getContext().getEnv().out());
            if (getContext().getOption(PythonOptions.EnableDebuggingBuiltins)) {
                out.println("\n\nJava Stacktrace:");
                new RuntimeException().printStackTrace(out);
                out.println("\n\nTruffle Stacktrace:");
                printStack();
                out.println("\n\nFrames:");
                Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {

                    public Void visitFrame(FrameInstance frame) {
                        out.println("  ===========================");
                        out.println("  call: " + frame.getCallNode());
                        out.println("  target: " + frame.getCallTarget());
                        Frame f = frame.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        out.println("  args: " + Arrays.asList(f.getArguments()));
                        return null;
                    }
                });
            } else {
                out.println("\n\nDEBUG TRACE (enable details via --python.EnableDebuggingBuiltins)");
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Direct)
    abstract static class PyTruffle_Debug extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doIt(Object arg,
                        @Cached DebugNode debugNode) {
            debugNode.execute(new Object[]{arg});
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Direct)
    abstract static class PyTruffle_ToNative extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int doIt(Object object) {
            if (!PythonOptions.EnableDebuggingBuiltins.getValue(getContext().getEnv().getOptions())) {
                String message = "PyTruffle_ToNative is not enabled - enable with --python.EnableDebuggingBuiltins\n";
                try {
                    getContext().getEnv().out().write(message.getBytes());
                } catch (IOException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return 1;
            }
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            if ("native".equals(toolchain.getIdentifier())) {
                InteropLibrary.getUncached().toNative(object);
            }
            return 0;
        }
    }
}
