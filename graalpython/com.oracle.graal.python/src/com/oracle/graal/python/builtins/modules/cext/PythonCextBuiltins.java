/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.GC_LOGGER;
import static com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupport.NEXT_MASK_UNREACHABLE;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.GraalPyGC_CycleNode__item;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.GraalPyGC_CycleNode__next;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyGetSetDef__closure;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyGetSetDef__doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyGetSetDef__get;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyGetSetDef__name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyGetSetDef__set;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemberDef__doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemberDef__flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemberDef__name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemberDef__offset;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemberDef__type;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.ErrorMessages.INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.HiddenAttr.NATIVE_SLOTS;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins.DebugNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFileSystemEncodingNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltins.GraalPyPrivate_Type_AddGetSet;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltins.GraalPyPrivate_Type_AddMember;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupport.PyObjectGCDelNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.GcNativePtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativePtrToPythonWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.UpdateStrongRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CoerceNativePointerToLongNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CConstants;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public final class PythonCextBuiltins {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PythonCextBuiltins.class);

    /**
     * Certain builtin types like {@code int} cannot handle refcounts. They cannot be handed out to
     * the native side as borrowed references, since the handle would be collected immediately. (the
     * boxed int, for example, is not referenced from anyhwere). This node promotes these types to
     * full types like {@link PInt} and {@link PString}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class PromoteBorrowedValue extends Node {

        public abstract Object execute(Node inliningTarget, Object value);

        @Specialization
        static PString doString(TruffleString str,
                        @Bind PythonLanguage language) {
            return PFactory.createString(language, str);
        }

        @Specialization
        static PythonBuiltinObject doInteger(int i,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, i);
        }

        @Specialization
        static PythonBuiltinObject doLong(long i,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, i);
        }

        @Specialization(guards = "!isNaN(d)")
        static PythonBuiltinObject doDouble(double d,
                        @Bind PythonLanguage language) {
            return PFactory.createFloat(language, d);
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
            CompilerDirectives.transferToInterpreter();
            PythonContext context = PythonContext.get(null);
            context.ensureGilAfterFailure();
            PBaseException newException = PFactory.createBaseException(context.getLanguage(), RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, EMPTY_OBJECT_ARRAY);
            throw ExceptionUtils.wrapJavaException(soe, null, newException);
        }
        if (t instanceof OutOfMemoryError oome) {
            CompilerDirectives.transferToInterpreter();
            PBaseException newException = PFactory.createBaseException(PythonLanguage.get(null), MemoryError);
            throw ExceptionUtils.wrapJavaException(oome, null, newException);
        }
        // everything else: log and convert to PException (SystemError)
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PNodeWithContext.printStack();
        PythonContext context = PythonContext.get(null);
        PrintStream out;
        if (context != null) {
            out = new PrintStream(context.getEnv().err());
        } else {
            out = System.err;
        }
        out.println("should not throw exceptions apart from PException");
        out.println("while executing " + where1 + " " + where2);
        ExceptionUtils.printPythonLikeStackTrace(new PrintWriter(out), t);
        t.printStackTrace(out);
        if (context == null) {
            out.println("ERROR: Native API called without Truffle context. This can happen when called from C-level atexit, C++ global destructor or an unregistered native thread");
        }
        out.flush();
        throw PRaiseNode.raiseStatic(null, SystemError, ErrorMessages.INTERNAL_EXCEPTION_OCCURED);
    }

    public abstract static class CApiBuiltinNode extends PNodeWithContext {

        public abstract Object execute(Object[] args);

        protected final NativePointer getNativeNull() {
            return getContext().getNativeNull();
        }

        protected static NativePointer getNativeNull(Node inliningTarget) {
            return PythonContext.get(inliningTarget).getNativeNull();
        }

        /**
         * Returns the "NULL" pointer retrieved from the native backend, e.g., an LLVMPointer
         * instance. This is not wrapped, i.e., it cannot be passed through a PyObject
         * Python-To-Native transition (because it would be treated as a foreign Truffle object at
         * that point).
         */
        protected final Object getNULL() {
            return getContext().getNativeNull();
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data, int offset, int length) {
            return ByteBuffer.wrap(data, offset, length);
        }

        @CompilationFinal private ArgDescriptor ret;

        public final Python3Core getCore() {
            return getContext();
        }

        protected static CApiContext getCApiContext(Node inliningTarget) {
            return PythonContext.get(inliningTarget).getCApiContext();
        }

        protected final CApiContext getCApiContext() {
            return getContext().getCApiContext();
        }

        protected final PException badInternalCall(String argName) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.S_S_BAD_ARG_TO_INTERNAL_FUNC, getName(), argName);
        }

        @NonIdempotent
        protected final boolean isNativeAccessAllowed() {
            return getContext().isNativeAccessAllowed();
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
                throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S, getName());
            }
            if (IsSubtypeNode.getUncached().execute(GetClassNode.executeUncached(obj), type)) {
                throw PRaiseNode.raiseStatic(this, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, type.getName());
            } else {
                throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.EXPECTED_S_NOT_P, type.getName(), obj);
            }
        }

        @TruffleBoundary
        protected PException raiseFallback(Object obj, PythonBuiltinClassType type1, PythonBuiltinClassType type2) {
            if (obj == PNone.NO_VALUE) {
                throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S, getName());
            }
            Object objType = GetClassNode.executeUncached(obj);
            if (IsSubtypeNode.getUncached().execute(objType, type1) || IsSubtypeNode.getUncached().execute(objType, type2)) {
                throw PRaiseNode.raiseStatic(this, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, type1.getName());
            } else {
                throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.EXPECTED_S_NOT_P, type1.getName(), obj);
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
            throw PRaiseNode.raiseStatic(this, SystemError, INDEX_OUT_OF_RANGE);
        }

        protected static void checkNonNullArg(Node inliningTarget, Object obj, PRaiseNode raiseNode) {
            if (obj == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.NULL_ARG_INTERNAL);
            }
        }

        protected static void checkNonNullArg(Node inliningTarget, Object obj1, Object obj2, PRaiseNode raiseNode) {
            if (obj1 == PNone.NO_VALUE || obj2 == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.NULL_ARG_INTERNAL);
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

    public abstract static class CApi17BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11,
                        Object arg12, Object arg13, Object arg14, Object arg15, Object arg16);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16]);
        }
    }

    public abstract static class CApi18BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11,
                        Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class CApiBuiltinExecutable implements TruffleObject {

        private final CApiTiming timing;
        private final ArgDescriptor ret;
        private final ArgDescriptor[] args;
        private final boolean acquireGil;
        @CompilationFinal private CallTarget callTarget;
        private final CApiCallPath call;
        private final String name;
        private final int id;

        public CApiBuiltinExecutable(String name, CApiCallPath call, ArgDescriptor ret, ArgDescriptor[] args, boolean acquireGil, int id) {
            this.timing = CApiTiming.create(false, name);
            this.name = name;
            this.call = call;
            this.ret = ret;
            this.args = args;
            this.acquireGil = acquireGil;
            this.id = id;
        }

        CallTarget getCallTarget() {
            if (callTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("call target slow path not implemented");
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

        public boolean acquireGil() {
            return acquireGil;
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
            @Specialization(guards = "self == cachedSelf", limit = "3")
            public static Object doExecute(@SuppressWarnings("unused") CApiBuiltinExecutable self, Object[] arguments,
                            @Cached("self") CApiBuiltinExecutable cachedSelf,
                            @Cached(parameters = "cachedSelf") ExecuteCApiBuiltinNode call) {

                try {
                    return call.execute(cachedSelf, arguments);
                } catch (ThreadDeath t) {
                    CompilerDirectives.transferToInterpreter();
                    throw t;
                } catch (Throwable t) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    t.printStackTrace();
                    throw CompilerDirectives.shouldNotReachHere(t);
                }
            }

            @Specialization
            public static Object doFallback(@SuppressWarnings("unused") CApiBuiltinExecutable self, @SuppressWarnings("unused") Object[] arguments) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
            PythonContext context = PythonContext.get(null);
            long pointer = context.getCApiContext().getClosurePointer(this);
            if (pointer == -1) {
                if (context.signatureContainer == null) {
                    context.signatureContainer = new SignatureContainerRootNode().getCallTarget();
                }

                try {
                    SignatureContainerRootNode container = (SignatureContainerRootNode) context.signatureContainer.getRootNode();
                    // create NFI closure and get its address
                    boolean panama = PythonOptions.UsePanama.getValue(context.getEnv().getOptions());
                    StringBuilder signature = new StringBuilder(panama ? "with panama (" : "(");
                    for (int i = 0; i < args.length; i++) {
                        signature.append(i == 0 ? "" : ",");
                        signature.append(args[i].getNFISignature());
                    }
                    signature.append("):").append(ret.getNFISignature());

                    Object nfiSignature = context.getEnv().parseInternal(Source.newBuilder(J_NFI_LANGUAGE, signature.toString(), "exec").build()).call();
                    Object closure = container.getLibrary(name).createClosure(nfiSignature, this);
                    InteropLibrary lib = InteropLibrary.getUncached(closure);
                    lib.toNative(closure);
                    try {
                        pointer = lib.asPointer(closure);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    context.getCApiContext().setClosurePointer(closure, null, this, pointer);
                    LOGGER.finer(CApiBuiltinExecutable.class.getSimpleName() + " toNative: " + id + " / " + name() + " -> " + pointer);
                } catch (Throwable t) {
                    t.printStackTrace(new PrintStream(context.getEnv().err()));
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
        @Child private GilNode gilNode = GilNode.create();
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
            boolean wasAcquired = self.acquireGil() && gilNode.acquire();
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
                transformExceptionToNativeNode.executeCached(e);
                if (cachedSelf.getRetDescriptor().isIntType()) {
                    return -1;
                } else if (cachedSelf.getRetDescriptor().isPyObjectOrPointer()) {
                    return PythonContext.get(this).getNativeNull();
                } else if (cachedSelf.getRetDescriptor().isFloatType()) {
                    return -1.0;
                } else if (cachedSelf.getRetDescriptor().isVoid()) {
                    return PNone.NO_VALUE;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("return type while handling PException: " + cachedSelf.getRetDescriptor() + " in " + self.name);
                }
            } finally {
                gilNode.release(wasAcquired);
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
         * will be generated.
         * <p>
         * In particular, assume there is a C API builtin called {@code PyObject_Str}. This will
         * generate the native symbol {@code GraalPyObject_Str} which is a variable with the
         * function pointer of the native closure and it will generate the native call stub (a C
         * function) {@code PyObject_Str} that calls {@code GraalPyObject_Str}.
         * </p>
         */
        Direct,
        /**
         * This builtin has an explicit C implementation that can be executed both from native - no
         * automatic stub will be generated. Further, there *MUST NOT* be a C API builtin that would
         * implement the function in Java.
         */
        CImpl,
        /**
         * This builtin is not implemented - create an empty stub that raises an error.
         */
        NotImplemented,
        /**
         * This builtin is not part of the Python C API, no call stub is generated.
         * <p>
         * This call path should be used if the builtin is basically implemented in Java but some
         * cases can already be covered in a C implementation. The convention is that if there is a
         * C API function {@code Py<namespace>_<function>} (e.g. {@code PyBytes_FromStringAndSize}),
         * then the Java builtin should be named {@code GraalPyPrivate_<namespace>_<function>} (e.g.
         * {@code GraalPyPrivate_Bytes_FromStringAndSize}). The corresponding C function must be
         * implemented manually and can then call the Java builtin using generated native symbol
         * {@code GraalPy<namespace>_<function>} (e.g.
         * {@code GraalPyPrivate_Bytes_FromStringAndSize}).
         * </p>
         */
        Ignored,
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface CApiBuiltins {
        CApiBuiltin[] value();
    }

    /**
     * Builtin implementations in the C API (or helpers for implementing builtins with C code) are
     * marked with this annotation. The information in the annotation allows code generation,
     * argument conversions (based on {@link ArgDescriptor}s), and verification of the C API
     * implementation in general.
     * <p>
     * Apart from being placed on classes that implement {@link CApiBuiltinNode}, this annotation is
     * also used in {@link CApiFunction} to list all functions that are implemented in C code or
     * that are not currently implemented.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
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

        /**
         * Whether we need to acquire GIL around the call. Since our conversion nodes currently
         * assume GIL, this should be only set to false by GIL-manipulating builtins.
         */
        boolean acquireGil() default true;

        /**
         * @see CApiCallPath
         */
        CApiCallPath call();

        /**
         * Comment to explain, e.g., why a builtin is ignored.
         */
        String comment() default "";
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class GraalPyPrivate_FileSystemDefaultEncoding extends CApiNullaryBuiltinNode {
        @Specialization
        static TruffleString encoding() {
            return GetFileSystemEncodingNode.getFileSystemEncoding();
        }
    }

    @CApiBuiltin(ret = PyTypeObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class GraalPyPrivate_Type extends CApiUnaryBuiltinNode {

        private static final TruffleString[] LOOKUP_MODULES = new TruffleString[]{
                        T__WEAKREF,
                        T_BUILTINS
        };

        @Specialization
        static Object doI(TruffleString typeName,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PRaiseNode raiseNode) {
            Python3Core core = PythonContext.get(inliningTarget);
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
            throw raiseNode.raise(inliningTarget, PythonErrorType.KeyError, ErrorMessages.APOSTROPHE_S, typeName);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class PyObjectSetAttrNode extends PNodeWithContext {

        abstract void execute(Node inliningTarget, Object object, TruffleString key, Object value);

        @Specialization
        static void doBuiltinClass(PythonBuiltinClass object, TruffleString key, Object value,
                        @Exclusive @Cached(value = "createForceType()", inline = false) WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
        }

        @Specialization
        static void doNativeClass(PythonNativeClass object, TruffleString key, Object value,
                        @Exclusive @Cached(value = "createForceType()", inline = false) WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
        }

        @Specialization(guards = {"!isPythonBuiltinClass(object)"})
        static void doObject(PythonObject object, TruffleString key, Object value,
                        @Exclusive @Cached(inline = false) WriteAttributeToPythonObjectNode writeAttrToPythonObjectNode) {
            writeAttrToPythonObjectNode.execute(object, key, value);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, Pointer, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Set_Native_Slots extends CApiTernaryBuiltinNode {

        @Specialization
        static int doPythonClass(PythonClass pythonClass, Object nativeGetSets, Object nativeMembers,
                        @Bind Node inliningTarget,
                        @Cached HiddenAttr.WriteNode writeAttrNode) {
            writeAttrNode.execute(inliningTarget, pythonClass, NATIVE_SLOTS, new Object[]{nativeGetSets, nativeMembers});
            return 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject}, call = Ignored)
    abstract static class GraalPyPrivate_AddInheritedSlots extends CApiUnaryBuiltinNode {
        /**
         * A native class may inherit from a managed class. However, the managed class may define
         * custom slots at a time where the C API is not yet loaded. So we need to check if any of
         * the base classes defines custom slots and adapt the basicsize to allocate space for the
         * slots and add the native member slot descriptors.
         * <p>
         * Additionally, at this point the native slots have been inherited on the native side, here
         * we transfer the result of that inheritance process to the slots mirror on the managed
         * side.
         */
        @TruffleBoundary
        @Specialization
        static Object addInheritedSlots(PythonAbstractNativeObject pythonClass,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CStructAccess.ReadObjectNode readNativeDict,
                        @Cached CStructAccess.ReadPointerNode readPointer,
                        @Cached CStructAccess.ReadI32Node readI32,
                        @Cached CStructAccess.ReadI64Node readI64,
                        @Cached FromCharPointerNode fromCharPointer,
                        @Cached GraalPyPrivate_Type_AddGetSet addGetSet,
                        @Cached GraalPyPrivate_Type_AddMember addMember,
                        @Cached GetMroStorageNode getMroStorageNode) {
            pythonClass.setTpSlots(TpSlots.fromNative(pythonClass, getCApiContext(inliningTarget).getContext()));

            Object[] getsets = collect(getMroStorageNode.execute(inliningTarget, pythonClass), INDEX_GETSETS);
            Object[] members = collect(getMroStorageNode.execute(inliningTarget, pythonClass), INDEX_MEMBERS);

            PDict dict = (PDict) readNativeDict.readFromObj(pythonClass, CFields.PyTypeObject__tp_dict);

            for (Object getset : getsets) {
                if (!PGuards.isNullOrZero(getset, lib)) {
                    for (int i = 0;; i++) {
                        Object namePtr = readPointer.readStructArrayElement(getset, i, PyGetSetDef__name);
                        if (PGuards.isNullOrZero(namePtr, lib)) {
                            break;
                        }
                        TruffleString name = fromCharPointer.execute(namePtr);
                        Object getter = readPointer.readStructArrayElement(getset, i, PyGetSetDef__get);
                        Object setter = readPointer.readStructArrayElement(getset, i, PyGetSetDef__set);
                        Object docPtr = readPointer.readStructArrayElement(getset, i, PyGetSetDef__doc);
                        Object doc = PGuards.isNullOrZero(docPtr, lib) ? PNone.NO_VALUE : fromCharPointer.execute(docPtr);
                        Object closure = readPointer.readStructArrayElement(getset, i, PyGetSetDef__closure);

                        addGetSet.execute(pythonClass, dict, name, getter, setter, doc, closure);
                    }
                }
            }

            for (Object member : members) {
                if (!PGuards.isNullOrZero(member, lib)) {
                    for (int i = 0;; i++) {
                        Object namePtr = readPointer.readStructArrayElement(member, i, PyMemberDef__name);
                        if (PGuards.isNullOrZero(namePtr, lib)) {
                            break;
                        }
                        TruffleString name = fromCharPointer.execute(namePtr);
                        int type = readI32.readStructArrayElement(member, i, PyMemberDef__type);
                        long offset = readI64.readStructArrayElement(member, i, PyMemberDef__offset);
                        int flags = readI32.readStructArrayElement(member, i, PyMemberDef__flags);
                        Object docPtr = readPointer.readStructArrayElement(member, i, PyMemberDef__doc);
                        Object doc = PGuards.isNullOrZero(docPtr, lib) ? PNone.NO_VALUE : fromCharPointer.execute(docPtr);
                        boolean canSet = (flags & CConstants.READONLY.intValue()) == 0;
                        addMember.execute(pythonClass, dict, name, type, offset, canSet ? 1 : 0, doc);
                    }
                }
            }
            return PNone.NO_VALUE;
        }

        private static final int INDEX_GETSETS = 0;
        private static final int INDEX_MEMBERS = 1;

        @TruffleBoundary
        private static Object[] collect(MroSequenceStorage mro, int idx) {
            ArrayList<Object> l = new ArrayList<>();
            int mroLength = mro.length();
            for (int i = 0; i < mroLength; i++) {
                PythonAbstractClass kls = mro.getPythonClassItemNormalized(i);
                Object value = HiddenAttr.ReadNode.executeUncached((PythonAbstractObject) kls, NATIVE_SLOTS, null);
                if (value != null) {
                    Object[] tuple = (Object[]) value;
                    assert tuple.length == 2;
                    l.add(tuple[idx]);
                }
            }
            return l.toArray();
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {PyThreadState, PyCodeObject, PyObject, PyObject}, call = Direct)
    abstract static class PyFrame_New extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object newFrame(Object threadState, PCode code, PythonObject globals, Object locals,
                        @Bind PythonLanguage language) {
            Object frameLocals;
            if (locals == null || PGuards.isPNone(locals)) {
                frameLocals = PFactory.createDict(language);
            } else {
                frameLocals = locals;
            }
            return PFactory.createPFrame(language, threadState, code, globals, frameLocals);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, PyObject, Py_ssize_t, Int, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Pointer, Pointer, Pointer, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_MemoryViewFromBuffer extends CApi11BuiltinNode {

        @Specialization
        static Object wrap(Object bufferStructPointer, Object ownerObj, long lenObj,
                        Object readonlyObj, Object itemsizeObj, TruffleString format,
                        Object ndimObj, Object bufPointer, Object shapePointer, Object stridesPointer, Object suboffsetsPointer,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile zeroDimProfile,
                        @Cached CStructAccess.ReadI64Node readShapeNode,
                        @Cached CStructAccess.ReadI64Node readStridesNode,
                        @Cached CStructAccess.ReadI64Node readSuboffsetsNode,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode atIndexNode,
                        @Bind PythonLanguage language) {
            int ndim = castToIntNode.execute(inliningTarget, ndimObj);
            int itemsize = castToIntNode.execute(inliningTarget, itemsizeObj);
            int len = castToIntNode.execute(inliningTarget, lenObj);
            boolean readonly = castToIntNode.execute(inliningTarget, readonlyObj) != 0;
            Object owner = PGuards.isNullOrZero(ownerObj, lib) ? null : ownerObj;
            int[] shape = null;
            int[] strides = null;
            int[] suboffsets = null;
            if (zeroDimProfile.profile(inliningTarget, ndim > 0)) {
                if (!lib.isNull(shapePointer)) {
                    shape = readShapeNode.readLongAsIntArray(shapePointer, ndim);
                } else {
                    assert ndim == 1;
                    shape = new int[]{len / itemsize};
                }
                if (!lib.isNull(stridesPointer)) {
                    strides = readStridesNode.readLongAsIntArray(stridesPointer, ndim);
                } else {
                    strides = PMemoryView.initStridesFromShape(ndim, itemsize, shape);
                }
                if (!lib.isNull(suboffsetsPointer)) {
                    suboffsets = readSuboffsetsNode.readLongAsIntArray(suboffsetsPointer, ndim);
                }
            }
            Object buffer = NativeByteSequenceStorage.create(bufPointer, len, len, false);
            int flags = initFlagsNode.execute(inliningTarget, ndim, itemsize, shape, strides, suboffsets);
            BufferLifecycleManager bufferLifecycleManager = null;
            if (!lib.isNull(bufferStructPointer)) {
                bufferLifecycleManager = new NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromType(bufferStructPointer);
            }
            return PFactory.createMemoryView(language, PythonContext.get(inliningTarget), bufferLifecycleManager, buffer, owner, len, readonly, itemsize,
                            BufferFormat.forMemoryView(format, lengthNode, atIndexNode), format, ndim, bufPointer, 0, shape, strides, suboffsets, flags);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ReportPolymorphism
    abstract static class CastArgsNode extends PNodeWithContext {

        public abstract Object[] execute(VirtualFrame frame, Node inliningTarget, Object argsObj);

        @Specialization(guards = "isNoValue(args)")
        @SuppressWarnings("unused")
        static Object[] doNull(PNone args) {
            return EMPTY_OBJECT_ARRAY;
        }

        @Specialization(guards = "!isNoValue(args)")
        static Object[] doNotNull(VirtualFrame frame, Object args,
                        @Cached(inline = false) ExecutePositionalStarargsNode expandArgsNode) {
            return expandArgsNode.executeWith(frame, args);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ReportPolymorphism
    abstract static class CastKwargsNode extends PNodeWithContext {

        public abstract PKeyword[] execute(Node inliningTarget, Object kwargsObj);

        @Specialization(guards = "isNoValue(kwargs)")
        @SuppressWarnings("unused")
        static PKeyword[] doNoKeywords(Object kwargs) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = "!isNoValue(kwargs)")
        static PKeyword[] doKeywords(Node inliningTarget, Object kwargs,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode) {
            return expandKwargsNode.execute(inliningTarget, kwargs);
        }
    }

    @CApiBuiltin(ret = SIZE_T, args = {}, call = Ignored)
    abstract static class GraalPyPrivate_GetMaxNativeMemory extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long get() {
            return PythonOptions.MaxNativeMemory.getValue(getContext().getEnv().getOptions());
        }
    }

    @CApiBuiltin(ret = SIZE_T, args = {}, call = Ignored)
    abstract static class GraalPyPrivate_GetInitialNativeMemory extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long get() {
            return PythonOptions.InitialNativeMemory.getValue(getContext().getEnv().getOptions());
        }
    }

    @CApiBuiltin(ret = Void, args = {SIZE_T}, call = Ignored)
    abstract static class GraalPyPrivate_TriggerGC extends CApiUnaryBuiltinNode {

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
    abstract static class GraalPyPrivate_ManagedObject_GC_Del extends CApiUnaryBuiltinNode {

        @Specialization(limit = "3")
        static PNone doObject(Object ptr,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGCDelNode pyObjectGCDelNode,
                        @CachedLibrary("ptr") InteropLibrary lib) {
            // we expect a pointer object here because this is called from native
            assert CApiTransitions.isBackendPointerObject(ptr);
            if (lib.isPointer(ptr)) {
                try {
                    pyObjectGCDelNode.execute(inliningTarget, lib.asPointer(ptr));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {UNSIGNED_INT, UINTPTR_T, SIZE_T}, call = Ignored)
    abstract static class GraalPyPrivate_TraceMalloc_Track extends CApiTernaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(GraalPyPrivate_TraceMalloc_Track.class);

        @Specialization
        @TruffleBoundary
        static Object doCachedDomainIdx(int domain, long ptrVal, long size) {
            // this will also be called if the allocation failed
            if (ptrVal != 0) {
                LOGGER.fine(() -> PythonUtils.formatJString("Tracking memory (domain: %d, size: %d): %s", domain, size, CApiContext.asHex(ptrVal)));
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {UNSIGNED_INT, UINTPTR_T}, call = Ignored)
    abstract static class GraalPyPrivate_TraceMalloc_Untrack extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(GraalPyPrivate_TraceMalloc_Untrack.class);

        @Specialization
        @TruffleBoundary
        Object doCachedDomainIdx(int domain, long ptrVal) {
            LOGGER.fine(() -> PythonUtils.formatJString("Untracking memory (domain: %d): %s", domain, CApiContext.asHex(ptrVal)));
            return PNone.NO_VALUE;
        }
    }

    @GenerateCached(false)
    abstract static class GraalPyPrivate_GcTracingNode extends CApiUnaryBuiltinNode {

        @Specialization(guards = "!traceMem(language)")
        static Object doNothing(@SuppressWarnings("unused") Object ptr,
                        @SuppressWarnings("unused") @Bind PythonLanguage language) {
            // do nothing
            return PNone.NO_VALUE;
        }

        @Fallback
        Object doNativeWrapper(Object ptr,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            PFrame.Reference ref = null;
            if (context.getOption(PythonOptions.TraceNativeMemoryCalls)) {
                ref = getCurrentFrameRef.execute(null, inliningTarget);
            }
            trace(context, CApiContext.asPointer(ptr, lib), ref, null);
            return PNone.NO_VALUE;
        }

        @Idempotent
        boolean traceMem(PythonLanguage language) {
            return language.getEngineOption(PythonOptions.TraceNativeMemory);
        }

        protected abstract void trace(PythonContext context, Object ptr, Reference ref, TruffleString className);
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Object_GC_UnTrack extends GraalPyPrivate_GcTracingNode {
        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            GC_LOGGER.finer(() -> PythonUtils.formatJString("Untracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().untrackObject(ptr, ref, className);
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Object_GC_Track extends GraalPyPrivate_GcTracingNode {
        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            GC_LOGGER.finer(() -> PythonUtils.formatJString("Tracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().trackObject(ptr, ref, className);
        }
    }

    /**
     * Replicates native references in Java.
     * <p>
     * This upcall is the core function for Python GC support. It replicates native references in
     * Java such that the Java GC sees all references (and also cycles) and can properly collect
     * everything.
     * </p>
     * <p>
     * In order to save managed-native round trips, this upcall function expects the native pointer
     * of a primary object and the pointer to a single linked list (node type
     * {@link CStructs#GraalPyGC_CycleNode}) that contains the referents. The referents are usually
     * determined by traversing the primary object.
     * </p>
     * <p>
     * If the primary object is a native object, a Python module (with native module state) or a
     * list/tuple with a native storage, the native references will be replicated. Other object
     * types may be added if they also have some native memory that may contain object references
     * (e.g. a module object may store references in its native module state).
     * </p>
     * <p>
     * The pointers of the referents in the single linked list are resolved (in particular, this
     * means that it ensures the existence of a {@link PythonAbstractNativeObject} for each native
     * object) and then stored in a Java object array which is then attached to the primary object.
     * </p>
     */
    @CApiBuiltin(ret = Void, args = {Pointer, Pointer, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Object_ReplicateNativeReferences extends CApiTernaryBuiltinNode {
        private static final Level LEVEL = Level.FINER;

        @Specialization(guards = "isNativeAccessAllowed()")
        static Object doGeneric(Object pointer, Object listHead, int n,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode readObjectNode,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached CoerceNativePointerToLongNode coerceNativePointerToLongNode,
                        @Cached GcNativePtrToPythonNode gcNativePtrToPythonNode) {
            assert PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.PythonGC);

            boolean loggable = GC_LOGGER.isLoggable(LEVEL);
            long lPointer = coerceNativePointerToLongNode.execute(inliningTarget, pointer);
            assert lPointer != 0;
            Object object = gcNativePtrToPythonNode.execute(inliningTarget, lPointer);

            /*
             * If 'object' is null, there is no 'PythonAbstractNativeObject' wrapper for the native
             * object. This means that the native object is not referenced from managed code. So, we
             * don't need to replicate native references.
             */

            Object repr = object;
            Object[] referents = null;
            if (object instanceof PythonAbstractNativeObject || object instanceof PythonModule || isTupleWithNativeStorage(object) || isListWithNativeStorage(object)) {
                /*
                 * Note: it is important that we first collect the objects such that we have strong
                 * Java references to them on the Java stack and then we overwrite the
                 * 'replicatedNativeReferences' field. This is because the referents may already be
                 * weakly referenced from the handle table and such referents may already be in the
                 * previous array and then it could happen, that they die during list processing.
                 */
                Object[] oldReferents;
                referents = new Object[n];
                if (object instanceof PythonAbstractNativeObject nativeObject) {
                    if (loggable) {
                        repr = nativeObject.toStringWithContext();
                    }
                    oldReferents = nativeObject.getReplicatedNativeReferences();
                    nativeObject.setReplicatedNativeReferences(referents);
                } else if (object instanceof PythonModule module) {
                    oldReferents = module.getReplicatedNativeReferences();
                    module.setReplicatedNativeReferences(referents);
                } else {
                    assert isTupleWithNativeStorage(object) || isListWithNativeStorage(object);
                    NativeSequenceStorage nativeSequenceStorage = getNativeSequenceStorage(object);
                    oldReferents = nativeSequenceStorage.getReplicatedNativeReferences();
                    nativeSequenceStorage.setReplicatedNativeReferences(referents);
                }
                // Collect referents (traverse native list and resolve pointers)
                Object cur = listHead;
                for (int i = 0; i < n; i++) {
                    referents[i] = readObjectNode.read(cur, GraalPyGC_CycleNode__item);
                    cur = readPointerNode.read(cur, GraalPyGC_CycleNode__next);
                }

                /*
                 * As described above: Ensure that the 'old' replicated references are strong until
                 * this point. Otherwise, weakly referenced managed objects could die.
                 */
                java.lang.ref.Reference.reachabilityFence(oldReferents);

                if (loggable) {
                    GC_LOGGER.log(LEVEL, PythonUtils.formatJString("Replicated native refs of %s to managed: %s", repr, arraysToString(referents)));
                }
            } else if (object == null && loggable) {
                GC_LOGGER.log(LEVEL, PythonUtils.formatJString("Did not replicate native refs of %s: no wrapper", CApiContext.asHex(lPointer)));
            }
            return PNone.NO_VALUE;
        }

        private static NativeSequenceStorage getNativeSequenceStorage(Object object) {
            NativeSequenceStorage nativeSequenceStorage;
            if (object instanceof PTuple tuple) {
                // cast is ensured by 'isTupleWithNativeStorage'
                nativeSequenceStorage = (NativeSequenceStorage) tuple.getSequenceStorage();
            } else {
                assert object instanceof PList;
                // casts are ensured by 'isListWithNativeStorage'
                nativeSequenceStorage = (NativeSequenceStorage) ((PList) object).getSequenceStorage();
            }
            return nativeSequenceStorage;
        }

        @Specialization(guards = "!isNativeAccessAllowed()")
        @SuppressWarnings("unused")
        static Object doManaged(Object pointer, Object listHead, int n) {
            return PNone.NO_VALUE;
        }

        @TruffleBoundary
        private static String arraysToString(Object[] arr) {
            return Arrays.toString(arr);
        }

        private static boolean isTupleWithNativeStorage(Object object) {
            return object instanceof PTuple tuple && tuple.getSequenceStorage() instanceof NativeSequenceStorage;
        }

        private static boolean isListWithNativeStorage(Object object) {
            return object instanceof PList list && list.getSequenceStorage() instanceof NativeSequenceStorage;
        }
    }

    /**
     * Iterates over all objects in the given GC list and makes all
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonObjectReference
     * handle table references} of the denoted objects weak. This is used to break reference cycles
     * that involve managed objects.
     */
    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Object_GC_EnsureWeak extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isNativeAccessAllowed()")
        static Object doNative(Object weakCandidates,
                        @Bind Node inliningTarget,
                        @Cached CoerceNativePointerToLongNode coerceToLongNode,
                        @Cached CStructAccess.ReadI64Node readI64Node,
                        @Cached CStructAccess.WriteLongNode writeLongNode,
                        @Cached NativePtrToPythonWrapperNode nativePtrToPythonWrapperNode,
                        @Cached UpdateStrongRefNode updateRefNode) {
            // guaranteed by the guard
            assert PythonContext.get(inliningTarget).isNativeAccessAllowed();
            assert PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.PythonGC);

            /*
             * The list's head is a dummy node that can not be a tagged pointer because it is not an
             * object and always allocated in native.
             */
            long head = coerceToLongNode.execute(inliningTarget, weakCandidates);
            assert !HandlePointerConverter.pointsToPyHandleSpace(head);

            // PyGC_Head *gc = GC_NEXT(head)
            long gc = readI64Node.read(head, CFields.PyGC_Head___gc_next);
            /*
             * The list's head is not polluted with NEXT_MASK_UNREACHABLE. See 'move_weak_reachable'
             * at the end of the function.
             */
            assert (gc & NEXT_MASK_UNREACHABLE) == 0;
            while (gc != head) {
                assert (gc & NEXT_MASK_UNREACHABLE) == 0;

                // PyObject *op = FROM_GC(gc)
                long op = gc + CStructs.PyGC_Head.size();

                PythonNativeWrapper wrapper = nativePtrToPythonWrapperNode.execute(inliningTarget, op, true);
                if (wrapper instanceof PythonAbstractObjectNativeWrapper abstractObjectNativeWrapper) {
                    if (GC_LOGGER.isLoggable(Level.FINE)) {
                        GC_LOGGER.fine(PythonUtils.formatJString("Transitioning to weak reference to break a reference cycle for %s, refcount=%d",
                                        abstractObjectNativeWrapper.ref, abstractObjectNativeWrapper.getRefCount()));
                    }
                    updateRefNode.clearStrongRef(inliningTarget, abstractObjectNativeWrapper);
                }

                // next = GC_NEXT(gc)
                long gcUntagged = HandlePointerConverter.pointerToStub(gc);
                long nextTaggedWithMask = readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_next);
                // remove NEXT_MASK_UNREACHABLE flag
                long next = nextTaggedWithMask & ~NEXT_MASK_UNREACHABLE;
                /*
                 * We expect to process 'weak_candidates' which all have NEXT_MASK_UNREACHABLE set
                 * except of the list head (which is a dummy node)
                 */
                assert next == head || (nextTaggedWithMask & NEXT_MASK_UNREACHABLE) != 0;

                /*
                 * This is a "dirty" untrack since we just overwrite '_gc_prev' and '_gc_next' with
                 * zero. Here it is fine because (a) managed objects will never have flags set in
                 * '_gc_prev' that need to be preserved, and (b) because we untrack all objects in
                 * this list anyway.
                 */
                writeLongNode.write(gcUntagged, CFields.PyGC_Head___gc_next, 0);
                writeLongNode.write(gcUntagged, CFields.PyGC_Head___gc_prev, 0);

                gc = next;
            }
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNativeAccessAllowed()")
        static Object doNative(@SuppressWarnings("unused") Object weakCandidates) {
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_IsReferencedFromManaged extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isNativeAccessAllowed()")
        static int doNative(Object pointer,
                        @Bind Node inliningTarget,
                        @Cached CoerceNativePointerToLongNode coerceToLongNode,
                        @Cached GcNativePtrToPythonNode gcNativePtrToPythonNode) {
            // guaranteed by the guard
            assert PythonContext.get(inliningTarget).isNativeAccessAllowed();
            assert PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.PythonGC);

            long lPointer = coerceToLongNode.execute(inliningTarget, pointer);
            // this upcall doesn't make sense for managed objects
            assert !HandlePointerConverter.pointsToPyHandleSpace(lPointer);

            Object object = gcNativePtrToPythonNode.execute(inliningTarget, lPointer);
            return PInt.intValue(object != null);
        }

        @Specialization(guards = "!isNativeAccessAllowed()")
        static Object doManaged(@SuppressWarnings("unused") Object pointer) {
            return PInt.intValue(false);
        }
    }

    @CApiBuiltin(ret = Void, call = Ignored)
    abstract static class GraalPyPrivate_EnableReferneceQueuePolling extends CApiNullaryBuiltinNode {
        @Specialization
        static Object doGeneric(@Bind Node inliningTarget) {
            assert PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.PythonGC);
            HandleContext handleContext = PythonContext.get(inliningTarget).nativeContext;
            CApiTransitions.enableReferenceQueuePolling(handleContext);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Int, call = Ignored)
    abstract static class GraalPyPrivate_DisableReferneceQueuePolling extends CApiNullaryBuiltinNode {
        @Specialization
        static int doGeneric(@Bind Node inliningTarget) {
            assert PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.PythonGC);
            HandleContext handleContext = PythonContext.get(inliningTarget).nativeContext;
            return PInt.intValue(CApiTransitions.disableReferenceQueuePolling(handleContext));
        }
    }

    private static final int TRACE_MEM = 0x1;
    private static final int LOG_INFO = 0x2;
    private static final int LOG_CONFIG = 0x4;
    private static final int LOG_FINE = 0x8;
    private static final int LOG_FINER = 0x10;
    private static final int LOG_FINEST = 0x20;
    private static final int DEBUG_CAPI = 0x40;
    private static final int PYTHON_GC = 0x80;

    /*
     * These should be kept so they can be shared across multiple contexts in the same engine, if
     * they are stored in a static field on the native side. We have to ensure that this is
     * generally fine. In practice, this means that options should either be marked
     * with @EngineOption so they are sure to be the same, or that options differing is benign.
     */
    @CApiBuiltin(ret = Int, call = Ignored)
    abstract static class GraalPyPrivate_Native_Options extends CApiNullaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        int getNativeOptions() {
            int options = 0;
            PythonLanguage language = PythonLanguage.get(null);
            if (language.getEngineOption(PythonOptions.TraceNativeMemory)) {
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
            if (PythonContext.DEBUG_CAPI) {
                options |= DEBUG_CAPI;
            }
            if (language.getEngineOption(PythonOptions.PythonGC)) {
                options |= PYTHON_GC;
            }
            return options;
        }
    }

    @CApiBuiltin(ret = Void, args = {Int, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class GraalPyPrivate_LogString extends CApiBinaryBuiltinNode {

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
    abstract static class GraalPyPrivate_DebugTrace extends CApiNullaryBuiltinNode {

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
    abstract static class GraalPyPrivate_Debug extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doIt(Object arg,
                        @Cached DebugNode debugNode) {
            debugNode.execute(new Object[]{arg});
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Direct)
    abstract static class GraalPyPrivate_ToNative extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int doIt(Object object) {
            if (!PythonOptions.EnableDebuggingBuiltins.getValue(getContext().getEnv().getOptions())) {
                String message = "GraalPyPrivate_ToNative is not enabled - enable with --python.EnableDebuggingBuiltins\n";
                try {
                    getContext().getEnv().out().write(message.getBytes());
                } catch (IOException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return 1;
            }
            InteropLibrary.getUncached().toNative(object);
            return 0;
        }
    }

    /**
     * Initializes the native type structures.
     * <p>
     * This C API built-in function is called during C API initialization. The caller provides an
     * array of pointers to native type structures (i.e. {@code PyTypeObject *}) and the
     * corresponding built-in type names (see below for a detailed array format specification).
     * </p>
     * <p>
     * The initialization is then done in two phases:
     * <ol>
     * <li>The built-in types are looked up by name and native wrappers are created whereas we set
     * the pointer of the native wrapper to the one provided in the array.</li>
     * <li>Write all information to the native type structures.</li>
     * </ol>
     * The initialization must be done in two phases because before we write the actual values to
     * the native type structure's fields, we first need to have the pointers available. This is
     * because there are dependency cycles between objects. For example, the Python type
     * {@code type} has slot {@code tp_mro} which contains a tuple. The tuple then has Python type
     * {@code tuple} and the type of the type {@code tuple} is again Python type {@code type}.
     * Hence, we cannot initialize type if the pointer is not already available.
     * </p>
     * <b>ATTENTION: KEEP THIS IN SYNC WITH {@code capi.c: initialize_builtin_types_and_structs}</b>
     * <p>
     * Array format:
     * </p>
     *
     * <pre>
     *     void *builtin_types[] = {
     *         // (PyTypeObject *)native_type_store_address, (const char *)python_builtin_type_name
     *         &PyBaseObject_Type, "object",
     *         &PyType_Type, "type",
     *         // ...
     *         NULL, NULL
     *     }
     * </pre>
     */
    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_InitBuiltinTypesAndStructs extends CApiUnaryBuiltinNode {

        @TruffleBoundary
        @Specialization
        Object doGeneric(Object builtinTypesArrayPointer) {
            List<Pair<PythonManagedClass, Object>> builtinTypes = new LinkedList<>();
            PythonContext context = getContext();
            CStructAccess.ReadPointerNode readPointerNode = CStructAccess.ReadPointerNode.getUncached();
            try {
                // first phase: lookup built-in type by name, create wrappers and set native pointer
                InteropLibrary lib = null;
                for (int i = 0;; i += 2) {
                    Object typeStructPtr = readPointerNode.readArrayElement(builtinTypesArrayPointer, i);
                    /*
                     * Most pointer types will be the same. So, we store the last looked up library
                     * in a local variable. However, It may happen that there are different types of
                     * pointer objects involved, so we need to update the library if the current one
                     * does not accept the object.
                     */
                    if (lib == null || !lib.accepts(typeStructPtr)) {
                        lib = InteropLibrary.getUncached(typeStructPtr);
                    }
                    // if we reach the sentinel, stop the loop
                    if (lib.isNull(typeStructPtr)) {
                        break;
                    }
                    Object namePtr = readPointerNode.readArrayElement(builtinTypesArrayPointer, i + 1);
                    TruffleString name = FromCharPointerNodeGen.getUncached().execute(namePtr, false);

                    // lookup the built-in type by name
                    PythonManagedClass clazz = lookupBuiltinTypeWithName(context, name);

                    // create the wrapper and register the pointer
                    LOGGER.fine(() -> "setting type store for built-in class " + name + " to " + PythonUtils.formatPointer(typeStructPtr));
                    PythonClassNativeWrapper.wrapNative(clazz, TypeNodes.GetNameNode.executeUncached(clazz), typeStructPtr);

                    builtinTypes.add(Pair.create(clazz, typeStructPtr));
                }

                // second phase: initialize the native type store
                for (Pair<PythonManagedClass, Object> pair : builtinTypes) {
                    LOGGER.fine(() -> "initializing built-in class " + TypeNodes.GetNameNode.executeUncached(pair.getLeft()));
                    PythonClassNativeWrapper.initNative(pair.getLeft(), pair.getRight());
                }

                return PNone.NO_VALUE;
            } catch (PException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        /**
         * Looks up a built-in type by name. This method may throw a Python exception (i.e.
         * {@code PException}) because if the type belongs to a built-in module, it needs to read an
         * attribute from the module.
         */
        private static PythonManagedClass lookupBuiltinTypeWithName(PythonContext context, TruffleString tsName) {
            Python3Core core = context.getCore();
            PythonManagedClass clazz = null;
            String name = tsName.toJavaStringUncached();
            // see if we're dealing with a type from a specific module
            int index = name.indexOf('.');
            if (index == -1) {
                for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
                    if (type.getName().equalsUncached(tsName, TS_ENCODING)) {
                        clazz = core.lookupType(type);
                        break;
                    }
                }
            } else {
                String module = name.substring(0, index);
                name = name.substring(index + 1);
                Object moduleObject = core.lookupBuiltinModule(toTruffleStringUncached(module));
                if (moduleObject == null) {
                    moduleObject = AbstractImportNode.importModule(toTruffleStringUncached(module));
                }
                Object attribute = PyObjectGetAttr.getUncached().execute(null, moduleObject, toTruffleStringUncached(name));
                if (attribute != PNone.NO_VALUE) {
                    if (attribute instanceof PythonBuiltinClassType builtinType) {
                        clazz = core.lookupType(builtinType);
                    } else {
                        clazz = (PythonManagedClass) attribute;
                    }
                }

            }
            if (clazz == null) {
                throw CompilerDirectives.shouldNotReachHere("cannot find class " + name);
            }

            return clazz;
        }
    }

    @CApiBuiltin(ret = CHAR_PTR, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_GetMMapData extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(PMMap object,
                        @Bind Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy raiseNode) {
            try {
                return posixLib.mmapGetPointer(getPosixSupport(), object.getPosixSupportHandle());
            } catch (PosixSupportLibrary.UnsupportedPosixFeatureException e) {
                throw raiseNode.get(inliningTarget).raiseOSErrorUnsupported(null, e);
            }
        }
    }
}
