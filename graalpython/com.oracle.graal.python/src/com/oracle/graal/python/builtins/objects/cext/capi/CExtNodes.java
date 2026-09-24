/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GRAALPY_OBJECT_GC_DEL;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_NO_OP_CLEAR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_NO_OP_TRAVERSE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_DEALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_FREE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TYPE_GENERIC_ALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SUBTYPE_TRAVERSE;
import static com.oracle.graal.python.builtins.objects.cext.structs.CConstants.PYLONG_BITS_IN_DIGIT;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyFloatObject__ob_fval;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_clear;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_free;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_methods;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_size;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_slots;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_traverse;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_buffer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readDoubleField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.builtins.objects.object.PythonObject.MANAGED_REFCNT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.calloc;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.mallocByteArray;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.writeByteArrayElement;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.writeByteArrayElements;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.lang.ref.Reference;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltins;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.EnsurePythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonClassInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.UpdateStrongRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformPExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.ModuleGetNameNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.ProfileClassNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.nativeaccess.NativeFunctionPointer;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

public abstract class CExtNodes {

    private static final long SIZEOF_PY_OBJECT_PTR = Long.BYTES;

    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FloatSubtypeNew extends Node {

        public abstract Object execute(Node inliningTarget, Object object, double arg);

        @Specialization
        static Object doGeneric(Node inliningTarget, Object object, double arg,
                        @Cached PythonToNativeInternalNode toNativeNode,
                        @Cached NativeToPythonInternalNode toJavaNode,
                        @Cached PyObjectCheckFunctionResultNode checkFunctionResultNode) {
            assert TypeNodes.NeedsNativeAllocationNode.executeUncached(object);
            NativeFunctionPointer callable = CApiContext.getNativeSymbol(inliningTarget, NativeCAPISymbol.FUN_FLOAT_SUBTYPE_NEW);
            long result;
            try {
                result = ExternalFunctionInvoker.invokeFLOAT_SUBTYPE_NEW(callable.getAddress(), toNativeNode.execute(inliningTarget, object), arg);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return checkFunctionResultNode.execute(PythonContext.get(inliningTarget), NativeCAPISymbol.FUN_FLOAT_SUBTYPE_NEW.getTsName(),
                            toJavaNode.executeTransfer(inliningTarget, result));
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class TupleSubtypeNew extends Node {

        public abstract Object execute(Node inliningTarget, Object object, Object arg);

        @Specialization
        static Object doGeneric(Node inliningTarget, Object object, Object arg,
                        @Cached PythonToNativeInternalNode toNativeNode,
                        @Cached NativeToPythonInternalNode toJavaNode) {
            assert TypeNodes.NeedsNativeAllocationNode.executeUncached(object);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(arg);
            NativeFunctionPointer callable = CApiContext.getNativeSymbol(inliningTarget, NativeCAPISymbol.FUN_TUPLE_SUBTYPE_NEW);
            try {
                long result = ExternalFunctionInvoker.invokeTUPLE_SUBTYPE_NEW(callable.getAddress(),
                                toNativeNode.execute(inliningTarget, object),
                                toNativeNode.execute(inliningTarget, arg));
                return toJavaNode.executeTransfer(inliningTarget, result);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class StringSubtypeNew extends Node {

        public abstract Object execute(Node inliningTarget, Object object, Object arg);

        @Specialization
        static Object doGeneric(Node inliningTarget, Object object, Object arg,
                        @Cached EnsurePythonObjectNode ensurePythonObjectNode,
                        @Cached PythonToNativeInternalNode toNativeNode,
                        @Cached NativeToPythonInternalNode toJavaNode) {
            assert TypeNodes.NeedsNativeAllocationNode.executeUncached(object);
            NativeFunctionPointer callable = CApiContext.getNativeSymbol(inliningTarget, NativeCAPISymbol.FUN_UNICODE_SUBTYPE_NEW);
            try {
                Object promotedArg = ensurePythonObjectNode.execute(PythonContext.get(inliningTarget), arg, false);
                long result = ExternalFunctionInvoker.invokeUNICODE_SUBTYPE_NEW(callable.getAddress(),
                                toNativeNode.execute(inliningTarget, object),
                                toNativeNode.execute(inliningTarget, promotedArg));
                Reference.reachabilityFence(promotedArg);
                return toJavaNode.executeTransfer(inliningTarget, result);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static <T extends PythonObject> T allocateNativePart(Node inliningTarget, Object cls, T managedSide) {
        assert !managedSide.isNative();
        assert EnsurePythonObjectNode.doesNotNeedPromotion(cls);
        long nativeObject;
        try {
            nativeObject = ExternalFunctionInvoker.invokePY_TYPE_GENERIC_NEW_RAW(CApiContext.getNativeSymbol(inliningTarget, NativeCAPISymbol.FUN_PY_TYPE_GENERIC_NEW_RAW).getAddress(),
                            PythonToNativeInternalNode.executeUncached(cls, false), 0L, 0L);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        } finally {
            Reference.reachabilityFence(cls);
        }
        PythonContext context = PythonContext.get(inliningTarget);
        TransformExceptionFromNativeNode.executeUncached(context.getThreadState(context.getLanguage()), NativeCAPISymbol.FUN_PY_TYPE_GENERIC_NEW_RAW.getTsName(), nativeObject == NULLPTR,
                        true);
        CApiTransitions.writeNativeRefCount(nativeObject, MANAGED_REFCNT);
        CApiTransitions.createReference(context, managedSide, nativeObject);
        assert managedSide.isNative();
        return managedSide;
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateInline(false) // footprint reduction 40 -> 21
    public abstract static class FromNativeSubclassNode extends Node {

        public abstract Double execute(VirtualFrame frame, PythonAbstractNativeObject object);

        @Specialization
        static Double doDouble(PythonAbstractNativeObject object,
                        @Bind Node inliningTarget,
                        @Cached GetPythonObjectClassNode getClass,
                        @Cached IsSubtypeNode isSubtype) {
            if (isFloatSubtype(inliningTarget, object, getClass, isSubtype)) {
                return readDoubleField(object.getPtr(), PyFloatObject__ob_fval);
            }
            return null;
        }

        public static boolean isFloatSubtype(Node inliningTarget, Object object, GetClassNode getClass, IsSubtypeNode isSubtype) {
            return isSubtype.execute(getClass.execute(inliningTarget, object), PythonBuiltinClassType.PFloat);
        }

        public static boolean isFloatSubtype(Node inliningTarget, PythonAbstractNativeObject object, GetPythonObjectClassNode getClass, IsSubtypeNode isSubtype) {
            return isSubtype.execute(getClass.execute(inliningTarget, object), PythonBuiltinClassType.PFloat);
        }

        @NeverDefault
        public static FromNativeSubclassNode create() {
            return CExtNodesFactory.FromNativeSubclassNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 60 -> 41
    public abstract static class AsCharPointerNode extends Node {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(AsCharPointerNode.class);

        public abstract long execute(Object obj);

        @Specialization
        static long doPString(PString str,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncoding,
                        @Shared @Cached CStructAccess.WriteTruffleStringNode writeTruffleString) {
            TruffleString value = castToStringNode.execute(inliningTarget, str);
            return doString(value, switchEncoding, writeTruffleString);
        }

        @Specialization
        static long doString(TruffleString str,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncoding,
                        @Shared @Cached CStructAccess.WriteTruffleStringNode writeTruffleString) {
            TruffleString utf8Str = switchEncoding.execute(str, Encoding.UTF_8);
            int size = utf8Str.byteLength(Encoding.UTF_8) + 1;
            long mem = calloc(size);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Allocated (const char *)0x%x of size %d for %s", mem, size, utf8Str));
            }
            writeTruffleString.write(mem, utf8Str, Encoding.UTF_8);
            return mem;
        }

        @Specialization
        static long doBytes(PBytes bytes,
                        @Bind Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ToByteArrayNode toBytesNode) {
            return doByteArray(toBytesNode.execute(inliningTarget, bytes.getSequenceStorage()));
        }

        @Specialization
        static long doBytes(PByteArray bytes,
                        @Bind Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ToByteArrayNode toBytesNode) {
            return doByteArray(toBytesNode.execute(inliningTarget, bytes.getSequenceStorage()));
        }

        @Specialization
        static long doByteArray(byte[] arr) {
            long size = arr.length + 1L;
            long mem = mallocByteArray(size);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Allocated (const char *)0x%x of size %d for (byte[])%s", mem, size, arr));
            }
            writeByteArrayElements(mem, 0, arr, 0, arr.length);
            writeByteArrayElement(mem, arr.length, (byte) 0);
            return mem;
        }

        public static AsCharPointerNode getUncached() {
            return AsCharPointerNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FromCharPointerNode extends Node {
        public final TruffleString execute(Node inliningTarget, long charPtr) {
            return execute(inliningTarget, charPtr, true);
        }

        @TruffleBoundary
        public static TruffleString executeUncached(long charPtr) {
            return FromCharPointerNodeGen.getUncached().execute(null, charPtr, true);
        }

        @TruffleBoundary
        public static TruffleString executeUncached(long charPtr, boolean copy) {
            return FromCharPointerNodeGen.getUncached().execute(null, charPtr, copy);
        }

        public abstract TruffleString execute(Node inliningTarget, long charPtr, boolean copy);

        @Specialization
        static TruffleString doPointer(long charPtr, boolean copy,
                        @Cached(inline = false) TruffleString.FromZeroTerminatedNativePointerNode fromNativePointerNode,
                        @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode) {
            TruffleString nativeBacked = fromNativePointerNode.execute8Bit(charPtr, 0, Encoding.UTF_8, copy);
            return switchEncodingNode.execute(nativeBacked, TS_ENCODING);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetNativeClassNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, PythonAbstractNativeObject object);

        @Specialization
        static Object getNativeClass(Node inliningTarget, PythonAbstractNativeObject object,
                        @Cached NativeToPythonClassInternalNode nativeToPythonClassInternalNode,
                        @Cached ProfileClassNode classProfile) {
            long obType = readPtrField(object.pointer, PyObject__ob_type);
            Object type = nativeToPythonClassInternalNode.execute(inliningTarget, obType);
            return classProfile.profile(inliningTarget, type);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Converts a Python object to a
     * {@link com.oracle.graal.python.builtins.objects.complex.PComplex} .<br/>
     * This node is, for example, used to implement {@code PyComplex_AsCComplex} and does coercion
     * and may raise a Python exception if coercion fails.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class AsNativeComplexNode extends PNodeWithContext {
        public abstract PComplex execute(Node inliningTarget, boolean arg);

        public abstract PComplex execute(Node inliningTarget, int arg);

        public abstract PComplex execute(Node inliningTarget, long arg);

        public abstract PComplex execute(Node inliningTarget, double arg);

        public abstract PComplex execute(Node inliningTarget, Object arg);

        @Specialization
        static PComplex doPComplex(PComplex value) {
            return value;
        }

        @Specialization
        static PComplex doBoolean(boolean value,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, value ? 1.0 : 0.0, 0.0);
        }

        @Specialization
        static PComplex doInt(int value,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, value, 0.0);
        }

        @Specialization
        static PComplex doLong(long value,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, value, 0.0);
        }

        @Specialization
        PComplex doDouble(double value,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, value, 0.0);
        }

        @Specialization
        static PComplex doPInt(PInt value,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, value.doubleValue(), 0.0);
        }

        @Specialization
        static PComplex doPFloat(PFloat value,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, value.getValue(), 0.0);
        }

        @Specialization(replaces = {"doPComplex", "doBoolean", "doInt", "doLong", "doDouble", "doPInt", "doPFloat"})
        static PComplex runGeneric(Node inliningTarget, Object value,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached(inline = false) LookupAndCallUnaryDynamicNode callComplex,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            Object result = callComplex.executeObject(value, T___COMPLEX__);
            // TODO(fa) according to CPython's 'PyComplex_AsCComplex', they still allow subclasses
            // of PComplex
            if (result != NO_VALUE) {
                if (result instanceof PComplex) {
                    return (PComplex) result;
                } else {
                    throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.COMPLEX_RETURNED_NON_COMPLEX, value);
                }
            } else {
                return PFactory.createComplex(language, asDoubleNode.execute(null, inliningTarget, value), 0.0);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Casts a Python object to a Java long value without doing any coercion, i.e., it does not call
     * any magic method like {@code __index__} or {@code __int__}.<br/>
     * The semantics is like a Java type cast and therefore lossy.<br/>
     * As an optimization, this node can also unwrap {@code PrimitiveNativeWrapper} instances to
     * avoid eager and explicit conversion.
     */
    @ImportStatic(PythonUtils.class)
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CastToNativeLongNode extends PNodeWithContext {
        public abstract long execute(Node inliningTarget, boolean arg);

        public abstract long execute(Node inliningTarget, byte arg);

        public abstract long execute(Node inliningTarget, int arg);

        public abstract long execute(Node inliningTarget, long arg);

        public abstract long execute(Node inliningTarget, double arg);

        public abstract Object execute(Node inliningTarget, Object arg);

        @Specialization(guards = "lengthNode.execute(value, TS_ENCODING) == 1", limit = "1")
        static long doString(TruffleString value,
                        @Cached TruffleString.CodePointAtIndexUTF32Node codepointAtIndexNode,
                        @SuppressWarnings("unused") @Cached TruffleString.CodePointLengthNode lengthNode) {
            return codepointAtIndexNode.execute(value, 0);
        }

        @Specialization
        static long doBoolean(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        static long doByte(byte value) {
            return value;
        }

        @Specialization
        static long doInt(int value) {
            return value;
        }

        @Specialization
        static long doLong(long value) {
            return value;
        }

        @Specialization
        static long doDouble(double value) {
            return (long) value;
        }

        @Specialization
        static long doPInt(PInt value) {
            return value.longValue();
        }

        @Specialization
        static long doPFloat(PFloat value) {
            return (long) value.getValue();
        }
    }

    /**
     * Use this method to lookup a native type member like {@code tp_alloc}.<br>
     * <p>
     * This method basically implements the native member inheritance that is done by
     * {@code inherit_special} or other code in {@code PyType_Ready}. In addition, we do a special
     * case for special slots assignment that happens within {@code type_new_alloc} for heap types.
     * </p>
     * <p>
     * Since it may be that a managed types needs to emulate such members but there is no
     * corresponding Python attribute (e.g. {@code tp_vectorcall_offset}), such members are stored
     * as hidden keys on the managed type. However, the MRO may contain native types and in this
     * case, we need to access the native member.
     * </p>
     */
    @TruffleBoundary
    public static long lookupNativeMemberInMRO(PythonManagedClass cls, CFields nativeMemberName, HiddenAttr managedMemberName) {
        NativeCAPISymbol symbol = null;
        // We need to point to PyType_GenericAlloc or PyObject_GC_Del
        if (managedMemberName == HiddenAttr.ALLOC) {
            symbol = FUN_PY_TYPE_GENERIC_ALLOC;
        } else if (managedMemberName == HiddenAttr.FREE) {
            /*
             * See 'typeobject.c: inherit_slots': A bit of magic to plug in the correct default
             * tp_free function when a derived class adds gc, didn't define tp_free, and the base
             * uses the default non-gc tp_free.
             */
            if ((GetTypeFlagsNode.executeUncached(cls) & TypeFlags.HAVE_GC) != 0) {
                symbol = FUN_GRAALPY_OBJECT_GC_DEL;
            } else {
                symbol = FUN_PY_OBJECT_FREE;
            }
        } else if (managedMemberName == HiddenAttr.TRAVERSE) {
            symbol = cls instanceof PythonBuiltinClass ? FUN_NO_OP_TRAVERSE : FUN_SUBTYPE_TRAVERSE;
        } else if (managedMemberName == HiddenAttr.CLEAR) {
            // This will need to be subtype_clear when we implement native GC
            symbol = FUN_NO_OP_CLEAR;
        }
        if (symbol != null) {
            long func = HiddenAttr.ReadLongNode.executeUncached(cls, managedMemberName, NULLPTR);
            if (func != NULLPTR) {
                return func;
            }
            return CApiContext.getNativeSymbol(null, symbol).getAddress();
        }
        MroSequenceStorage mroStorage = GetMroStorageNode.executeUncached(cls);
        int n = mroStorage.length();
        for (int i = 0; i < n; i++) {
            PythonAbstractClass mroCls = (PythonAbstractClass) SequenceStorageNodes.GetItemDynamicNode.executeUncached(mroStorage, i);
            if (PGuards.isManagedClass(mroCls)) {
                long result = HiddenAttr.ReadLongNode.executeUncached((PythonObject) mroCls, managedMemberName, NULLPTR);
                if (result != NULLPTR) {
                    return result;
                }
            } else {
                assert PGuards.isNativeClass(mroCls) : "invalid class inheritance structure; expected native class";
                long result = CStructAccess.readPtrField(((PythonNativeClass) mroCls).getPtr(), nativeMemberName);
                if (result != NULLPTR) {
                    return result;
                }
            }
        }
        if (managedMemberName == HiddenAttr.CLEAR && (TypeNodes.GetTypeFlagsNode.executeUncached(cls) & TypeFlags.HAVE_GC) != 0) {
            return CApiContext.getNativeSymbol(null, FUN_NO_OP_CLEAR).getAddress();
        }
        return HiddenAttr.ReadLongNode.executeUncached(PythonContext.get(null).lookupType(PythonBuiltinClassType.PythonObject), managedMemberName, NULLPTR);
    }

    /**
     * Like {@link #lookupNativeMemberInMRO(PythonManagedClass, CFields, HiddenAttr)}, but for i64
     * values.
     */
    @TruffleBoundary
    public static long lookupNativeI64MemberInMRO(Object cls, CFields nativeMemberName, Object managedMemberName) {
        assert managedMemberName instanceof HiddenAttr || managedMemberName instanceof TruffleString;

        MroSequenceStorage mroStorage = GetMroStorageNode.executeUncached(cls);
        int n = mroStorage.length();

        boolean isBasicsizeOrWeaklistoffset = nativeMemberName == CFields.PyTypeObject__tp_basicsize || nativeMemberName == CFields.PyTypeObject__tp_weaklistoffset;
        long indexedSlotsSize = isBasicsizeOrWeaklistoffset && cls instanceof PythonManagedClass pmc ? pmc.getIndexedSlotCount() * SIZEOF_PY_OBJECT_PTR : 0;
        for (int i = 0; i < n; i++) {
            PythonAbstractClass mroCls = (PythonAbstractClass) SequenceStorageNodes.GetItemDynamicNode.executeUncached(mroStorage, i);

            if (PGuards.isManagedClass(mroCls)) {
                Object attr;
                if (managedMemberName instanceof HiddenAttr ha) {
                    attr = HiddenAttr.ReadNode.executeUncached((PythonAbstractObject) mroCls, ha, NO_VALUE);
                } else {
                    attr = ReadAttributeFromObjectNode.getUncached().execute(mroCls, CompilerDirectives.castExact(managedMemberName, TruffleString.class));
                }
                if (attr != NO_VALUE) {
                    return PyNumberAsSizeNode.executeExactUncached(attr);
                } else if (indexedSlotsSize != 0) {
                    // managed class with __slots__, but no precomputed
                    // basicsize/dictoffset/weaklistoffset
                    break;
                }
            } else {
                assert PGuards.isNativeClass(mroCls) : "invalid class inheritance structure; expected native class";
                return readLongField(((PythonNativeClass) mroCls).getPtr(), nativeMemberName);
            }
        }
        // return the value from PyBaseObject - assumed to be 0 for vectorcall_offset
        return isBasicsizeOrWeaklistoffset ? indexedSlotsSize + CStructs.PyObject.size() : 0L;
    }

    /**
     * This node is used for lookups of fields that are inherited from the dominant base instead of
     * MRO, such as {@code tp_basicsize}. For MRO lookup, use
     * {@link #lookupNativeI64MemberInMRO(Object, CFields, Object)}.
     */
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 44 -> 26
    public abstract static class LookupNativeI64MemberFromBaseNode extends Node {
        @FunctionalInterface
        public interface BuiltinCallback {
            int apply(PythonBuiltinClassType t);
        }

        public final long execute(Object cls, CFields nativeMemberName, HiddenAttr managedMemberName) {
            return execute(cls, nativeMemberName, managedMemberName, null);
        }

        public abstract long execute(Object cls, CFields nativeMemberName, HiddenAttr managedMemberName, BuiltinCallback builtinCallback);

        @Specialization
        static long doSingleContext(Object cls, CFields nativeMember, HiddenAttr managedMemberName, BuiltinCallback builtinCallback,
                        @Bind Node inliningTarget,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached HiddenAttr.ReadNode readAttrNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            CompilerAsserts.partialEvaluationConstant(builtinCallback);

            Object current = cls;
            boolean isBasicsizeOrWeaklistoffset = nativeMember == CFields.PyTypeObject__tp_basicsize || nativeMember == CFields.PyTypeObject__tp_weaklistoffset;
            long indexedSlotsSize = isBasicsizeOrWeaklistoffset && cls instanceof PythonManagedClass pmc ? pmc.getIndexedSlotCount() * SIZEOF_PY_OBJECT_PTR : 0;
            do {
                if (current instanceof PythonBuiltinClassType pbct) {
                    current = PythonContext.get(inliningTarget).lookupType(pbct);
                }
                if (builtinCallback != null && current instanceof PythonBuiltinClass builtinClass) {
                    return builtinCallback.apply(builtinClass.getType());
                } else if (PGuards.isManagedClass(current)) {
                    Object attr = readAttrNode.execute(inliningTarget, (PythonObject) current, managedMemberName, null);
                    if (attr != null) {
                        return asSizeNode.executeExact(null, inliningTarget, attr);
                    } else if (indexedSlotsSize != 0) {
                        // managed class with __slots__, but no precomputed
                        // basicsize/dictoffset/weaklistoffset
                        break;
                    }
                } else {
                    assert PGuards.isNativeClass(current) : "invalid class inheritance structure; expected native class";
                    return readLongField(((PythonNativeClass) current).getPtr(), nativeMember);
                }
                current = getBaseClassNode.execute(inliningTarget, current);
            } while (current != null);
            // return the value from PyBaseObject - assumed to be 0 for vectorcall_offset
            return isBasicsizeOrWeaklistoffset ? indexedSlotsSize + CStructs.PyObject.size() : 0L;
        }
    }

    @GenerateUncached
    @GenerateCached
    @GenerateInline(false)
    public abstract static class PRaiseNativeNode extends Node {

        public final int raiseInt(Frame frame, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return executeInt(frame, errorValue, errType, format, arguments);
        }

        public final <T> T raise(Frame frame, T errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            Object result = execute(frame, errorValue, errType, format, arguments);
            assert result == errorValue;
            return errorValue;
        }

        public final int raiseIntWithoutFrame(int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return executeInt(null, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments);

        public abstract int executeInt(Frame frame, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments);

        @Specialization
        static int doInt(int errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformPExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(inliningTarget, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        @Specialization
        static Object doObject(Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformPExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(inliningTarget, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        public static <T> T raiseStatic(T errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            try {
                throw PRaiseNode.raiseStatic(EncapsulatingNodeReference.getCurrent().get(), errType, format, arguments);
            } catch (PException p) {
                TransformPExceptionToNativeNode.executeUncached(p);
            }
            return errorValue;
        }

        private static void raiseNative(Node inliningTarget, PythonBuiltinClassType errType, TruffleString format, Object[] arguments, PRaiseNode raiseNode,
                        TransformPExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.raise(inliningTarget, errType, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(inliningTarget, p);
            }
        }

        @GenerateInline
        @GenerateUncached
        @GenerateCached(false)
        public abstract static class Lazy extends Node {

            public final PRaiseNativeNode get(Node inliningTarget) {
                return execute(inliningTarget);
            }

            abstract PRaiseNativeNode execute(Node inliningTarget);

            @Specialization
            static PRaiseNativeNode doIt(@Cached(inline = false) PRaiseNativeNode node) {
                return node;
            }
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class XDecRefPointerNode extends PNodeWithContext {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, FUN_PY_DEALLOC);

        public static void executeUncached(long pointer) {
            CExtNodesFactory.XDecRefPointerNodeGen.getUncached().execute(null, pointer);
        }

        public abstract void execute(Node inliningTarget, long pointer);

        @Specialization
        static void doDecref(Node inliningTarget, long pointer,
                        @Cached CApiTransitions.NativeToPythonInternalNode toPythonNode,
                        @Cached InlinedBranchProfile isWrapperProfile,
                        @Cached InlinedBranchProfile isSpecialSingletonProfile,
                        @Cached UpdateStrongRefNode updateRefNode) {
            if (pointer == NULLPTR) {
                return;
            }
            // Boxed primitive values do not have a native reference count.
            if (HandlePointerConverter.pointsToPyFloatHandle(pointer) || HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                return;
            }
            Object object = toPythonNode.execute(inliningTarget, pointer);
            if (object instanceof PythonAbstractNativeObject) {
                // Native objects use their native reference count and deallocator.
                if (CApiTransitions.subNativeRefCount(pointer, 1) == 0) {
                    PythonContext context = PythonContext.get(inliningTarget);
                    var callable = CApiContext.getNativeSymbol(inliningTarget, FUN_PY_DEALLOC);
                    ExternalFunctionInvoker.invokePY_DEALLOC(null, C_API_TIMING, context.ensureNativeContext(), BoundaryCallData.getUncached(),
                                    context.getThreadState(PythonLanguage.get(inliningTarget)), callable, pointer);
                }
            } else if (CApiContext.isSpecialSingleton(object)) {
                // Special singletons such as PNone are immortal handle-space objects.
                isSpecialSingletonProfile.enter(inliningTarget);
            } else if (object instanceof PythonObject pythonObject) {
                // Managed Python objects keep their reference count in the wrapper.
                isWrapperProfile.enter(inliningTarget);
                updateRefNode.execute(inliningTarget, pythonObject, pythonObject.decRef());
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("unexpected object for decref: " + object);
            }
        }
    }

    /**
     * Calculate the lv_tag of PyLongObject.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class LvTagNode extends PNodeWithContext {

        private static final int SIGN_ZERO = 1;
        private static final int SIGN_NEGATIVE = 2;
        private static final int NON_SIZE_BITS = 3;

        public abstract long execute(Node inliningTarget, Object object);

        public long getDigitCount(Node inliningTarget, Object object) {
            return execute(inliningTarget, object) >> NON_SIZE_BITS;
        }

        static long toLvTag(long x, boolean isNegative) {
            int sign = 0;
            if (x == 0) {
                return SIGN_ZERO;
            }
            if (isNegative) {
                sign = SIGN_NEGATIVE;
            }
            return x << NON_SIZE_BITS | sign;
        }

        @Specialization
        static long doBoolean(boolean object) {
            return toLvTag(object ? 1 : 0, false);
        }

        @Specialization
        static long doInteger(int object) {
            return doLong(object);
        }

        @Specialization
        static long doLong(long object) {
            long t = PInt.abs(object);
            boolean sign = object < 0;
            int size = 0;
            while (t != 0) {
                ++size;
                t >>>= PYLONG_BITS_IN_DIGIT.intValue();
            }
            return toLvTag(size, sign);
        }

        @Specialization
        static long doPInt(PInt object) {
            int bw = PYLONG_BITS_IN_DIGIT.intValue();
            int len = (PInt.bitLength(object.abs()) + bw - 1) / bw;
            return toLvTag(len, object.isNegative());
        }
    }

    /**
     * Depending on the object's type, the size may need to be computed in very different ways. E.g.
     * any PyVarObject usually returns the number of contained elements.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class ObSizeNode extends PNodeWithContext {

        public abstract long execute(Node inliningTarget, Object object);

        @Specialization
        static long doClass(@SuppressWarnings("unused") PythonManagedClass object) {
            return 0; // dummy value
        }

        @Fallback
        static long doOther(Node inliningTarget, Object object,
                        @Cached PyObjectSizeNode sizeNode) {
            try {
                return sizeNode.execute(null, inliningTarget, object);
            } catch (PException e) {
                return -1;
            }
        }
    }

    // according to definitions in 'moduleobject.h' (for Python 3.15: defined in 'slots.toml')
    private static final int SLOT_PY_MOD_CREATE = 1;
    private static final int SLOT_PY_MOD_EXEC = 2;
    private static final int SLOT_PY_MOD_MULTIPLE_INTERPRETERS = 3;
    private static final int SLOT_PY_MOD_GIL = 4;

    /*
     * The following slot values are the "new" values required for abi3t.
     * They are defined in the future Python version 3.15. Eventually, they will become the default and the above values will be legacy.
     */
    private static final int SLOT_ABI3T_PY_MOD_CREATE = 84;
    private static final int SLOT_ABI3T_PY_MOD_EXEC = 85;
    private static final int SLOT_ABI3T_PY_MOD_MULTIPLE_INTERPRETERS = 86;
    private static final int SLOT_ABI3T_PY_MOD_GIL = 87;
    private static final int SLOT_ABI3T_PY_MOD_NAME = 100;
    private static final int SLOT_ABI3T_PY_MOD_DOC = 101;
    private static final int SLOT_ABI3T_PY_MOD_STATE_SIZE = 102;
    private static final int SLOT_ABI3T_PY_MOD_METHODS = 103;
    private static final int SLOT_ABI3T_PY_MOD_STATE_TRAVERSE = 104;
    private static final int SLOT_ABI3T_PY_MOD_STATE_CLEAR = 105;
    private static final int SLOT_ABI3T_PY_MOD_STATE_FREE = 106;
    private static final int SLOT_ABI3T_PY_MOD_ABI = 109;
    private static final int SLOT_ABI3T_PY_MOD_TOKEN = 110;

    private static final int PY_ABI_INFO_STABLE = 0x0001;
    private static final int PY_ABI_INFO_GIL = 0x0002;
    private static final int PY_ABI_INFO_FREETHREADED = 0x0004;
    private static final int PY_ABI_INFO_INTERNAL = 0x0008;
    private static final int ABI3T_VERSION_HEX = 0x030f0000;
    private static final int MINIMUM_STABLE_ABI_VERSION_HEX = 0x03020000;

    // for Py_mod_gil
    private static final int Py_MOD_GIL_USED = 0;
    private static final int Py_MOD_GIL_NOT_USED = 1;

    // for Py_mod_multiple_interpreters
    private static final int Py_MOD_MULTIPLE_INTERPRETERS_NOT_SUPPORTED = 0;
    private static final int Py_MOD_MULTIPLE_INTERPRETERS_SUPPORTED = 1;
    private static final int Py_MOD_PER_INTERPRETER_GIL_SUPPORTED = 2;

    private static final CApiTiming TIMING_MOD_CREATE = CApiTiming.create(true, "Py_mod_create");
    private static final CApiTiming TIMING_MOD_EXEC = CApiTiming.create(true, "Py_mod_exec");

    /**
     * Equivalent of {@code PyModule_FromDefAndSpec}. Creates a Python module from a module
     * definition structure:
     *
     * <pre>
     * typedef struct PyModuleDef {
     *     PyModuleDef_Base m_base;
     *     const char* m_name;
     *     const char* m_doc;
     *     Py_ssize_t m_size;
     *     PyMethodDef *m_methods;
     *     struct PyModuleDef_Slot* m_slots;
     *     traverseproc m_traverse;
     *     inquiry m_clear;
     *     freefunc m_free;
     * } PyModuleDef
     * </pre>
     */
    @TruffleBoundary
    static Object createModuleFromDefAndSpec(Node node, CApiContext capiContext, ModuleSpec moduleSpec, long moduleDefPtr) {
        /*
         * The name of the module is taken from the module spec and *NOT* from the module
         * definition.
         */
        TruffleString mName = moduleSpec.name;
        NativeModuleDefinition definition = new NativeModuleDefinition(moduleDefPtr, true);
        definition.name = readPtrField(moduleDefPtr, PyModuleDef__m_name);
        definition.doc = readPtrField(moduleDefPtr, PyModuleDef__m_doc);
        definition.stateSize = readLongField(moduleDefPtr, PyModuleDef__m_size);

        long slotDefinitions = readPtrField(moduleDefPtr, PyModuleDef__m_slots);
        definition.methods = readPtrField(moduleDefPtr, PyModuleDef__m_methods);
        definition.traverseFunction = readPtrField(moduleDefPtr, PyModuleDef__m_traverse);
        definition.clearFunction = readPtrField(moduleDefPtr, PyModuleDef__m_clear);
        definition.freeFunction = readPtrField(moduleDefPtr, PyModuleDef__m_free);
        definition.token = moduleDefPtr;
        return createModule(node, capiContext, moduleSpec,
                        parseModuleSlots(node, PySlotIterator.initLegacy(node, mName, slotDefinitions, PySlotIterator.SlotKind.MODULE), definition, mName).getDefinition());
    }

    /**
     * Equivalent of {@code moduleobject.c: PyModule_FromSlotsAndSpec}. Creates a Python module from slots definition.
     *
     * <pre>
     * struct PySlot {
     *     uint16_t sl_id;
     *     uint16_t sl_flags;
     *     _Py_ANONYMOUS union {
     *         uint32_t sl_reserved; // must be 0
     *     };
     *     _Py_ANONYMOUS union {
     *         void *sl_ptr;
     *         _Py_funcptr_t sl_func;
     *         Py_ssize_t sl_size;
     *         int64_t sl_int64;
     *         uint64_t sl_uint64;
     *     };
     * };
     * </pre>
     */
    @TruffleBoundary
    public static Object createModuleFromSlotsAndSpec(Node node, CApiContext capiContext, long slots, ModuleSpec moduleSpec) {
        PythonContext context = capiContext.getContext();
        ParsedModuleSlots parsed = parseModuleSlots(node, PySlotIterator.init(node, moduleSpec.name, slots, PySlotIterator.SlotKind.MODULE), null, moduleSpec.name);
        if (!parsed.sawAbi) {
            throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_DOES_NOT_DEFINE_ABI, moduleSpec.name);
        }

        if (parsed.definition.requiresGil) {
            // TODO(fa): enable gil
        }

        // By default, multi-phase init modules are expected to work under multiple interpreters.
        if (parsed.multipleInterpreters == Py_MOD_MULTIPLE_INTERPRETERS_NOT_SUPPORTED) {
            if (!context.isMainInterpreter()) {
                checkSubinterpIncompatibleExtensionAllowed(node, context, moduleSpec.name);
            }
        } else if (parsed.multipleInterpreters != Py_MOD_PER_INTERPRETER_GIL_SUPPORTED && context.ownsGil() && !context.isMainInterpreter()) {
            checkSubinterpIncompatibleExtensionAllowed(node, context, moduleSpec.name);
        }

        return createModule(node, capiContext, moduleSpec, parsed.getDefinition());
    }

    private static final class ParsedModuleSlots {
        private final NativeModuleDefinition definition;
        private long multipleInterpreters = Py_MOD_MULTIPLE_INTERPRETERS_SUPPORTED;
        private boolean sawAbi;

        private ParsedModuleSlots(NativeModuleDefinition definition) {
            this.definition = definition;
        }

        private NativeModuleDefinition getDefinition() {
            return definition;
        }
    }

    private static final class NativeModuleDefinition {
        private final long moduleDef;
        private final boolean tokenIsDef;
        private long createFunction;
        private long execFunction;
        private boolean hasExecSlots;
        private long name;
        private long stateSize;
        private long doc;
        private long methods;
        private long traverseFunction;
        private long clearFunction;
        private long freeFunction;
        private long token;
        private boolean requiresGil = true;

        private NativeModuleDefinition(long moduleDef, boolean tokenIsDef) {
            this.moduleDef = moduleDef;
            this.tokenIsDef = tokenIsDef;
        }
    }

    private static ParsedModuleSlots parseModuleSlots(Node node, PySlotIterator iterator, NativeModuleDefinition originalDefinition, TruffleString moduleName) {
        ParsedModuleSlots parsed = new ParsedModuleSlots(originalDefinition == null ? new NativeModuleDefinition(NULLPTR, false) : originalDefinition);
        while (iterator.next()) {
            PySlotIterator.Slot slot = iterator.current();
            long value = slot.pointer();
            switch (slot.id()) {
                case SLOT_ABI3T_PY_MOD_CREATE:
                    parsed.definition.createFunction = value;
                    break;
                case SLOT_ABI3T_PY_MOD_EXEC:
                    if (originalDefinition == null) {
                        if (parsed.definition.hasExecSlots) {
                            throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_EXEC_SLOTS, moduleName);
                        }
                        parsed.definition.execFunction = value;
                    }
                    parsed.definition.hasExecSlots = true;
                    break;
                case SLOT_ABI3T_PY_MOD_MULTIPLE_INTERPRETERS:
                    parsed.multipleInterpreters = value;
                    break;
                case SLOT_ABI3T_PY_MOD_GIL:
                    parsed.definition.requiresGil = value != Py_MOD_GIL_NOT_USED;
                    break;
                case SLOT_ABI3T_PY_MOD_ABI:
                    checkAbiInfo(node, value, moduleName);
                    parsed.sawAbi = true;
                    break;
                case SLOT_ABI3T_PY_MOD_TOKEN:
                    if (originalDefinition != null && originalDefinition.moduleDef != value) {
                        throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_ARBITRARY_TOKEN_WITH_MODULE_DEF, moduleName);
                    }
                    parsed.definition.token = value;
                    break;
                case SLOT_ABI3T_PY_MOD_NAME:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.name, value, moduleName, "m_name");
                    parsed.definition.name = value;
                    break;
                case SLOT_ABI3T_PY_MOD_DOC:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.doc, value, moduleName, "m_doc");
                    parsed.definition.doc = value;
                    break;
                case SLOT_ABI3T_PY_MOD_STATE_SIZE:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.stateSize, slot.size(), moduleName, "m_size");
                    parsed.definition.stateSize = slot.size();
                    break;
                case SLOT_ABI3T_PY_MOD_METHODS:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.methods, value, moduleName, "m_methods");
                    parsed.definition.methods = value;
                    break;
                case SLOT_ABI3T_PY_MOD_STATE_TRAVERSE:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.traverseFunction, value, moduleName, "m_traverse");
                    parsed.definition.traverseFunction = value;
                    break;
                case SLOT_ABI3T_PY_MOD_STATE_CLEAR:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.clearFunction, value, moduleName, "m_clear");
                    parsed.definition.clearFunction = value;
                    break;
                case SLOT_ABI3T_PY_MOD_STATE_FREE:
                    checkDefinitionField(node, originalDefinition, originalDefinition == null ? 0 : originalDefinition.freeFunction, value, moduleName, "m_free");
                    parsed.definition.freeFunction = value;
                    break;
            }
        }
        parsed.sawAbi = iterator.sawSlot(SLOT_ABI3T_PY_MOD_ABI);
        return parsed;
    }

    private static void checkDefinitionField(Node node, NativeModuleDefinition originalDefinition, long originalValue, long newValue, TruffleString moduleName, String field) {
        if (originalDefinition != null && originalValue != newValue) {
            throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_SLOT_CONFLICTS_WITH_MODULE_DEF, moduleName, field);
        }
    }

    private static Object createModule(Node node, CApiContext capiContext, ModuleSpec moduleSpec, NativeModuleDefinition definition) {

        if (definition.stateSize < 0) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.M_SIZE_CANNOT_BE_NEGATIVE, moduleSpec.name);
        }

        PythonContext context = capiContext.getContext();
        Object module;
        if (definition.createFunction != NULLPTR) {
            module = callCreateAndCheckResult(node, moduleSpec, context, definition);
        } else {
            module = PFactory.createPythonModule(moduleSpec.name);
        }
        if (module instanceof PythonModule pythonModule) {
            initializeNativeModule(pythonModule, definition);
        }

        if (definition.methods != NULLPTR) {
            PythonCextMethodBuiltins.addMethodsToObject(context.getLanguage(), definition.methods, module, moduleSpec.name);
        }

        Object mDoc;
        if (definition.doc == NULLPTR) {
            mDoc = NO_VALUE;
        } else {
            mDoc = FromCharPointerNode.executeUncached(definition.doc);
        }
        WriteAttributeToObjectNode.getUncached().execute(module, SpecialAttributeNames.T___DOC__, mDoc);

        return module;

    }

    private static Object callCreateAndCheckResult(Node node, ModuleSpec moduleSpec, PythonContext context, NativeModuleDefinition definition) {
        PythonThreadState threadState = context.getThreadState(context.getLanguage());
        NativeFunctionPointer modCreate = ExternalFunctionSignature.MODCREATE.bind(context.ensureNativeContext(), definition.createFunction);
        long result = ExternalFunctionInvoker.invokeMODCREATE(null, TIMING_MOD_CREATE, context.ensureNativeContext(),
                        BoundaryCallData.getUncached(), threadState, modCreate,
                        PythonToNativeInternalNode.executeUncached(moduleSpec.originalModuleSpec, false), definition.moduleDef);
        TransformExceptionFromNativeNode.getUncached().execute(null, threadState, moduleSpec.name, result == NULLPTR, true,
                        ErrorMessages.CREATION_FAILD_WITHOUT_EXCEPTION, ErrorMessages.CREATION_RAISED_EXCEPTION);
        Object module = NativeToPythonInternalNode.executeUncached(result, true);

        /*
         * We are stricter than CPython and require this to be a PythonModule object. This
         * means, if the custom 'create' function uses a native subtype of the module type, then
         * we require it to call our new function.
         */
        if (!(module instanceof PythonModule)) {
            if (definition.stateSize > 0 || definition.traverseFunction != NULLPTR || definition.clearFunction != NULLPTR || definition.freeFunction != NULLPTR) {
                throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.NOT_A_MODULE_OBJECT_BUT_REQUESTS_MODULE_STATE, moduleSpec.name);
            }
            if (definition.hasExecSlots) {
                throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_SPECIFIES_EXEC_SLOTS_BUT_DIDNT_CREATE_INSTANCE, moduleSpec.name);
            }
            if (!definition.tokenIsDef && definition.token != NULLPTR) {
                throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_SPECIFIES_TOKEN_BUT_DIDNT_CREATE_INSTANCE, moduleSpec.name);
            }
            // otherwise CPython is just fine
        }
        return module;
    }

    public static int checkAbiInfo(Node node, long info, TruffleString moduleName) {
        if (info == NULLPTR) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_NULL_ABI_INFO, moduleName);
        }
        int majorVersion = com.oracle.graal.python.runtime.nativeaccess.NativeMemory.readByte(info) & 0xff;
        if (majorVersion == 0) {
            return 0;
        }
        if (majorVersion > 1) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_ABI_INFO_VERSION_TOO_HIGH, moduleName);
        }
        int flags = com.oracle.graal.python.runtime.nativeaccess.NativeMemory.readShort(info + 2) & 0xffff;
        long abiVersion = com.oracle.graal.python.runtime.nativeaccess.NativeMemory.readInt(info + 8) & 0xffffffffL;
        if ((flags & PY_ABI_INFO_INTERNAL) != 0) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_INTERNAL_ABI_UNSUPPORTED, moduleName);
        }
        if ((flags & PY_ABI_INFO_STABLE) == 0) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_NON_STABLE_ABI_UNSUPPORTED, moduleName);
        }
        long majorMinorMask = 0xffff0000L;
        if (abiVersion != 0 && (abiVersion & majorMinorMask) > (ABI3T_VERSION_HEX & majorMinorMask)) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_FUTURE_STABLE_ABI, moduleName,
                            (abiVersion >> 24) & 0xff, (abiVersion >> 16) & 0xff);
        }
        if (abiVersion != 0 && abiVersion < MINIMUM_STABLE_ABI_VERSION_HEX) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_INVALID_STABLE_ABI, moduleName,
                            (abiVersion >> 24) & 0xff, (abiVersion >> 16) & 0xff);
        }
        int gilFlags = flags & (PY_ABI_INFO_GIL | PY_ABI_INFO_FREETHREADED);
        if (gilFlags == PY_ABI_INFO_FREETHREADED) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.ImportError, ErrorMessages.MODULE_ONLY_FREETHREADED, moduleName);
        }
        return 0;
    }

    private static void initializeNativeModule(PythonModule module, NativeModuleDefinition definition) {
        // Equivalent to CPython's module_copy_members_from_deflike() plus token/exec setup.
        module.setNativeModuleDef(definition.moduleDef);
        module.setNativeModuleStateSize(definition.stateSize);
        module.setNativeModuleTraverse(definition.traverseFunction);
        module.setNativeModuleClear(definition.clearFunction);
        module.setNativeModuleFree(definition.freeFunction);
        module.setNativeModuleToken(definition.token);
        module.setNativeModuleTokenIsDef(definition.tokenIsDef);
        module.setNativeModuleExec(definition.moduleDef == NULLPTR ? definition.execFunction : NULLPTR);
        module.setNativeModuleRequiresGil(definition.requiresGil);
    }

    // similar to 'import.c: _PyImport_CheckSubinterpIncompatibleExtensionAllowed'
    private static void checkSubinterpIncompatibleExtensionAllowed(Node location, PythonContext context, TruffleString name) {
        // similar to 'import.c: check_multi_interp_extensions'
        if (context.getOverrideMultiInterpExtensionsCheck() > 0) {
            assert !context.isMainInterpreter();
            throw PRaiseNode.raiseStatic(location, PythonBuiltinClassType.ImportError,
                            ErrorMessages.MODULE_S_DOES_NOT_SUPPORT_LOADING_IN_SUBINTERPRETERS, name);
        }
    }

    /**
     * Equivalent of {@code PyModule_ExecDef}.
     */
    @TruffleBoundary
    public static int execModule(Node node, CApiContext capiContext, PythonModule module) {
        TruffleString mName = ModuleGetNameNode.executeUncached(module);
        long mSize = module.getNativeModuleStateSize();

        // allocate md_state if necessary
        if (mSize >= 0 && module.getNativeModuleState() == NULLPTR) {
            // TODO(fa): We currently leak 'md_state' and need to use something like a NativeStorageReference.
            long mdState = calloc(mSize == 0 ? 1 : mSize); // ensure non-null value
            assert mdState != NULLPTR;
            module.setNativeModuleState(mdState);
        }

        long directExecFunction = module.getNativeModuleExec();
        if (directExecFunction != NULLPTR) {
            invokeModuleExec(node, capiContext, module, mName, directExecFunction);
            return 0;
        }

        // Legacy definitions may contain multiple Py_mod_exec slots.
        long moduleDef = module.getNativeModuleDef();
        if (moduleDef == NULLPTR) {
            return 0;
        }
        long slotDefinitions = readPtrField(moduleDef, PyModuleDef__m_slots);
        if (slotDefinitions == NULLPTR) {
            return 0;
        }
        PySlotIterator iterator = PySlotIterator.initLegacy(node, mName, slotDefinitions, PySlotIterator.SlotKind.MODULE);
        while (iterator.next()) {
            if (iterator.current().id() == SLOT_ABI3T_PY_MOD_EXEC) {
                invokeModuleExec(node, capiContext, module, mName, iterator.current().function());
            }
        }

        return 0;
    }

    private static void invokeModuleExec(Node node, CApiContext capiContext, PythonModule module, TruffleString moduleName, long execFunction) {
        PythonContext context = capiContext.getContext();
        PythonThreadState threadState = context.getThreadState(context.getLanguage());
        NativeFunctionPointer boundFunction = ExternalFunctionSignature.MODEXEC.bind(context.ensureNativeContext(), execFunction);
        int result = ExternalFunctionInvoker.invokeMODEXEC(null, TIMING_MOD_EXEC, context.ensureNativeContext(), BoundaryCallData.getUncached(), threadState, boundFunction,
                        PythonToNativeInternalNode.executeUncached(module, false));
        TransformExceptionFromNativeNode.getUncached().execute(node, threadState, moduleName, result != 0, true,
                        ErrorMessages.EXECUTION_FAILED_WITHOUT_EXCEPTION, ErrorMessages.EXECUTION_RAISED_EXCEPTION);
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class HasNativeBufferNode extends PNodeWithContext {
        public abstract boolean execute(Node inliningTarget, PythonAbstractNativeObject object);

        @Specialization
        static boolean readTpAsBuffer(PythonAbstractNativeObject object) {
            long type = readPtrField(object.getPtr(), PyObject__ob_type);
            long result = readPtrField(type, PyTypeObject__tp_as_buffer);
            return result != NULLPTR;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CreateMemoryViewFromNativeNode extends PNodeWithContext {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT);

        public abstract PMemoryView execute(Node inliningTarget, PythonNativeObject object, int flags);

        @Specialization
        static PMemoryView fromNative(Node inliningTarget, PythonNativeObject buf, int flags,
                        @Cached PythonToNativeInternalNode toNativeNode,
                        @Cached NativeToPythonInternalNode asPythonObjectNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkFunctionResultNode) {
            long bufPointer = toNativeNode.execute(inliningTarget, buf);
            try {
                PythonContext context = PythonContext.get(inliningTarget);
                var callable = CApiContext.getNativeSymbol(inliningTarget, FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT);
                long result = ExternalFunctionInvoker.invokeGRAALPY_MEMORYVIEW_FROM_OBJECT(null, C_API_TIMING, context.ensureNativeContext(), BoundaryCallData.getUncached(),
                                context.getThreadState(PythonLanguage.get(inliningTarget)), callable, bufPointer, flags);
                return (PMemoryView) checkFunctionResultNode.execute(context, FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT.getTsName(), asPythonObjectNode.executeTransfer(inliningTarget, result));
            } finally {
                Reference.reachabilityFence(buf);
            }
        }
    }

    /**
     * Special helper node that promotes primitive values to {@link PythonObject} such that they can
     * be connected with a native companion.
     */
    @GenerateInline(false)
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiContext.class, PythonToNativeInternalNode.class})
    public abstract static class EnsurePythonObjectNode extends Node {

        @TruffleBoundary
        public static PythonAbstractObject executeUncached(PythonContext context, Object object) {
            return (PythonAbstractObject) EnsurePythonObjectNodeGen.getUncached().execute(context, object, true);
        }

        @TruffleBoundary
        public static boolean doesNotNeedPromotion(Object object) {
            return EnsurePythonObjectNodeGen.getUncached().execute(PythonContext.get(null), object, false) == object;
        }

        @TruffleBoundary
        public static Object executeUncached(PythonContext context, Object object, boolean promoteBoxable) {
            return EnsurePythonObjectNodeGen.getUncached().execute(context, object, promoteBoxable);
        }

        public abstract Object execute(PythonContext context, Object object, boolean promoteBoxable);

        @Specialization
        static Object doGeneric(PythonContext context, Object obj, boolean promoteBoxable,
                        @Bind Node inliningTarget,
                        @Cached InlinedExactClassProfile classProfile,
                        @Cached GetClassNode getClassNode) {
            CompilerAsserts.partialEvaluationConstant(promoteBoxable);

            Object profiled = classProfile.profile(inliningTarget, obj);
            if (profiled instanceof PythonObject pythonObject) {
                return pythonObject;
            } else if (profiled instanceof Integer i) {
                return promoteBoxable ? PFactory.createInt(context.getLanguage(), i) : i;
            } else if (profiled instanceof Long l) {
                return promoteBoxable || !PInt.fitsInInt(l) ? PFactory.createInt(context.getLanguage(), l) : l;
            } else if (profiled instanceof Float f) {
                return promoteBoxable ? PFactory.createFloat(context.getLanguage(), f) : f;
            } else if (profiled instanceof Double d && Double.isNaN(d)) {
                return d;
            } else if (profiled instanceof Double d) {
                return promoteBoxable || !PFloat.fitsInFloat(d) ? PFactory.createFloat(context.getLanguage(), d) : d;
            } else if (profiled instanceof Boolean b) {
                return b ? context.getTrue() : context.getFalse();
            } else if (profiled instanceof TruffleString s) {
                return PFactory.createString(context.getLanguage(), s);
            } else if (profiled instanceof PythonBuiltinClassType pbct) {
                return context.lookupType(pbct);
            } else if (CApiContext.isSpecialSingleton(profiled) || profiled instanceof PythonAbstractNativeObject || PythonToNativeInternalNode.mapsToNull(profiled)) {
                return profiled;
            } else if (PGuards.isForeignObject(profiled)) {
                assert profiled != null : "attempting to wrap Java null";
                Object clazz = getClassNode.execute(inliningTarget, profiled);
                return PFactory.createPythonForeignObject(context.getLanguage(), clazz, profiled);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("unexpected object for promotion: " + profiled);
        }

        @NeverDefault
        public static EnsurePythonObjectNode create() {
            return EnsurePythonObjectNodeGen.create();
        }

        @NeverDefault
        public static EnsurePythonObjectNode getUncached() {
            return EnsurePythonObjectNodeGen.getUncached();
        }
    }
}
