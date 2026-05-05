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
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_meth;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMethodDef__ml_name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef_Slot__slot;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef_Slot__value;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_doc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_methods;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_size;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyModuleDef__m_slots;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_buffer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readDoubleField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readStructArrayIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readStructArrayPtrField;
import static com.oracle.graal.python.builtins.objects.object.PythonObject.MANAGED_REFCNT;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.calloc;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.mallocByteArray;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.writeByteArrayElement;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.writeByteArrayElements;
import static com.oracle.graal.python.nodes.HiddenAttr.METHOD_DEF_PTR;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.lang.ref.Reference;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.EnsurePythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonClassInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.UpdateStrongRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformPExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
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
import com.oracle.graal.python.runtime.nativeaccess.NativeFunctionPointer;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
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
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
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
        static Object doGeneric(Object object, double arg,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeNode toNativeNode,
                        @Cached NativeToPythonTransferNode toJavaNode,
                        @Cached PyObjectCheckFunctionResultNode checkFunctionResultNode) {
            assert TypeNodes.NeedsNativeAllocationNode.executeUncached(object);
            NativeFunctionPointer callable = CApiContext.getNativeSymbol(inliningTarget, NativeCAPISymbol.FUN_FLOAT_SUBTYPE_NEW);
            try {
                long result = ExternalFunctionInvoker.invokeFLOAT_SUBTYPE_NEW(callable.getAddress(), toNativeNode.executeLong(object), arg);
                return checkFunctionResultNode.execute(PythonContext.get(inliningTarget), NativeCAPISymbol.FUN_FLOAT_SUBTYPE_NEW.getTsName(), toJavaNode.execute(result));
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
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
                                toNativeNode.execute(inliningTarget, object, false),
                                toNativeNode.execute(inliningTarget, arg, false));
                return toJavaNode.execute(inliningTarget, result, true);
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
                                toNativeNode.execute(inliningTarget, object, false),
                                toNativeNode.execute(inliningTarget, promotedArg, false));
                Reference.reachabilityFence(promotedArg);
                return toJavaNode.execute(inliningTarget, result, true);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateInline(true)
    @GenerateCached(false)
    @GenerateUncached(false)
    public abstract static class DictSubtypeNew extends Node {
        public abstract PDict execute(Node node, Object cls, PDict managedSide);

        @Specialization
        static PDict allocateNativePart(Object cls, PDict managedSide,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeNode toNative) {
            assert !managedSide.isNative();
            assert EnsurePythonObjectNode.doesNotNeedPromotion(cls);
            long clsPointer = toNative.executeLong(cls);
            long nativeObject;
            try {
                nativeObject = ExternalFunctionInvoker.invokePY_TYPE_GENERIC_NEW_RAW(CApiContext.getNativeSymbol(inliningTarget, NativeCAPISymbol.FUN_PY_TYPE_GENERIC_NEW_RAW).getAddress(),
                                clsPointer, 0L, 0L);
            } catch (Throwable t) {
                throw CompilerDirectives.shouldNotReachHere(t);
            } finally {
                Reference.reachabilityFence(cls);
            }
            CApiTransitions.writeNativeRefCount(nativeObject, MANAGED_REFCNT);
            CApiTransitions.createReference(managedSide, nativeObject);
            assert managedSide.isNative();
            return managedSide;
        }
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
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
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
                        @Cached UpdateStrongRefNode updateRefNode) {
            if (pointer == NULLPTR) {
                return;
            }
            if (HandlePointerConverter.pointsToPyFloatHandle(pointer) || HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                return;
            }
            Object object = toPythonNode.execute(inliningTarget, pointer, false);
            if (object instanceof PythonObject pythonObject) {
                isWrapperProfile.enter(inliningTarget);
                updateRefNode.execute(inliningTarget, pythonObject, pythonObject.decRef());
            } else {
                assert object instanceof PythonAbstractNativeObject;
                if (CApiTransitions.subNativeRefCount(pointer, 1) == 0) {
                    PythonContext context = PythonContext.get(inliningTarget);
                    var callable = CApiContext.getNativeSymbol(inliningTarget, FUN_PY_DEALLOC);
                    ExternalFunctionInvoker.invokePY_DEALLOC(null, C_API_TIMING, context.ensureNativeContext(), BoundaryCallData.getUncached(),
                                    context.getThreadState(PythonLanguage.get(inliningTarget)), callable, pointer);
                }
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

    // according to definitions in 'moduleobject.h'
    private static final int SLOT_PY_MOD_CREATE = 1;
    private static final int SLOT_PY_MOD_EXEC = 2;
    private static final int SLOT_PY_MOD_MULTIPLE_INTERPRETERS = 3;

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
    static Object createModule(Node node, CApiContext capiContext, ModuleSpec moduleSpec, long moduleDefPtr, Object library) {
        /*
         * The name of the module is taken from the module spec and *NOT* from the module
         * definition.
         */
        TruffleString mName = moduleSpec.name;
        Object mDoc;
        long mSize;
        // do not eagerly read the doc string; this turned out to be unnecessarily expensive
        long docPtr = readPtrField(moduleDefPtr, PyModuleDef__m_doc);
        if (docPtr == NULLPTR) {
            mDoc = NO_VALUE;
        } else {
            mDoc = FromCharPointerNode.executeUncached(docPtr);
        }

        mSize = readLongField(moduleDefPtr, PyModuleDef__m_size);

        if (mSize < 0) {
            throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.M_SIZE_CANNOT_BE_NEGATIVE, mName);
        }

        // parse slot definitions
        long createFunction = NULLPTR;
        boolean hasExecutionSlots = false;
        long slotDefinitions = readPtrField(moduleDefPtr, PyModuleDef__m_slots);
        if (slotDefinitions != NULLPTR) {
            loop: for (int i = 0;; i++) {
                int slotId = readStructArrayIntField(slotDefinitions, i, PyModuleDef_Slot__slot);
                switch (slotId) {
                    case 0:
                        break loop;
                    case SLOT_PY_MOD_CREATE:
                        if (createFunction != NULLPTR) {
                            throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_CREATE_SLOTS, mName);
                        }
                        createFunction = readStructArrayPtrField(slotDefinitions, i, PyModuleDef_Slot__value);
                        break;
                    case SLOT_PY_MOD_EXEC:
                        hasExecutionSlots = true;
                        break;
                    case SLOT_PY_MOD_MULTIPLE_INTERPRETERS:
                        // ignored
                        // (mq) TODO: handle multiple interpreter cases
                        break;
                    default:
                        throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_USES_UNKNOW_SLOT_ID, mName, slotId);
                }
            }
        }

        PythonContext context = capiContext.getContext();
        Object module;
        if (createFunction != NULLPTR) {
            PythonThreadState threadState = context.getThreadState(context.getLanguage());
            NativeFunctionPointer modCreate = ExternalFunctionSignature.MODCREATE.bind(context.ensureNativeContext(), createFunction);
            long result = ExternalFunctionInvoker.invokeMODCREATE(null, TIMING_MOD_CREATE, context.ensureNativeContext(),
                            BoundaryCallData.getUncached(), threadState, modCreate,
                            PythonToNativeInternalNode.executeUncached(moduleSpec.originalModuleSpec, false), moduleDefPtr);
            TransformExceptionFromNativeNode.getUncached().execute(null, threadState, mName, result == NULLPTR, true,
                            ErrorMessages.CREATION_FAILD_WITHOUT_EXCEPTION, ErrorMessages.CREATION_RAISED_EXCEPTION);
            module = NativeToPythonTransferNode.executeRawUncached(result);

            /*
             * We are more strict than CPython and require this to be a PythonModule object. This
             * means, if the custom 'create' function uses a native subtype of the module type, then
             * we require it to call our new function.
             */
            if (!(module instanceof PythonModule)) {
                if (mSize > 0) {
                    throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.NOT_A_MODULE_OBJECT_BUT_REQUESTS_MODULE_STATE, mName);
                }
                if (hasExecutionSlots) {
                    throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_SPECIFIES_EXEC_SLOTS_BUT_DIDNT_CREATE_INSTANCE, mName);
                }
                // otherwise CPython is just fine
            } else {
                ((PythonModule) module).setNativeModuleDef(moduleDefPtr);
            }
        } else {
            PythonModule pythonModule = PFactory.createPythonModule(mName);
            pythonModule.setNativeModuleDef(moduleDefPtr);
            module = pythonModule;
        }

        long methodDefinitions = readPtrField(moduleDefPtr, PyModuleDef__m_methods);
        if (methodDefinitions != NULLPTR) {
            for (int i = 0;; i++) {
                PBuiltinFunction fun = createLegacyMethod(methodDefinitions, i, context.getLanguage());
                if (fun == null) {
                    break;
                }
                PBuiltinMethod method = PFactory.createBuiltinMethod(context.getLanguage(), module, fun);
                WriteAttributeToPythonObjectNode.getUncached().execute(method, SpecialAttributeNames.T___MODULE__, mName);
                WriteAttributeToObjectNode.getUncached().execute(module, fun.getName(), method);
            }
        }

        WriteAttributeToObjectNode.getUncached().execute(module, SpecialAttributeNames.T___DOC__, mDoc);
        capiContext.addLoadedExtensionLibrary(library);
        return module;
    }

    /**
     * Equivalent of {@code PyModule_ExecDef}.
     */
    @TruffleBoundary
    public static int execModule(Node node, CApiContext capiContext, PythonModule module, long moduleDef) {
        TruffleString mName = ModuleGetNameNode.executeUncached(module);
        long mSize = readLongField(moduleDef, PyModuleDef__m_size);

        // allocate md_state if necessary
        if (mSize >= 0) {
            /*
             * TODO(fa): We currently leak 'md_state' and need to use a shared finalizer or similar.
             * We ignore that for now since the size will usually be very small and/or we could also
             * use a Truffle buffer object.
             */
            long mdState = calloc(mSize == 0 ? 1 : mSize); // ensure non-null value
            assert mdState != NULLPTR;
            module.setNativeModuleState(mdState);
        }

        // parse slot definitions
        long slotDefinitions = readPtrField(moduleDef, PyModuleDef__m_slots);
        if (slotDefinitions == NULLPTR) {
            return 0;
        }
        loop: for (int i = 0;; i++) {
            int slotId = readStructArrayIntField(slotDefinitions, i, PyModuleDef_Slot__slot);
            switch (slotId) {
                case 0:
                    break loop;
                case SLOT_PY_MOD_CREATE:
                    // handled in CreateModuleNode
                    break;
                case SLOT_PY_MOD_EXEC:
                    long execFunction = readStructArrayPtrField(slotDefinitions, i, PyModuleDef_Slot__value);
                    PythonContext context = capiContext.getContext();
                    PythonThreadState threadState = context.getThreadState(context.getLanguage());
                    NativeFunctionPointer boundFunction = ExternalFunctionSignature.MODEXEC.bind(context.ensureNativeContext(), execFunction);
                    int iResult = ExternalFunctionInvoker.invokeMODEXEC(null, TIMING_MOD_EXEC, context.ensureNativeContext(),
                                    BoundaryCallData.getUncached(), threadState, boundFunction,
                                    PythonToNativeInternalNode.executeUncached(module, false));
                    /*
                     * It's a bit counterintuitive that we use 'isPrimitiveValue = false' but the
                     * function's return value is actually not a result but a status code. So, if
                     * the status code is '!=0' we know that an error occurred and won't ignore this
                     * if no error is set. This is then the same behaviour if we would have a
                     * pointer return type and got 'NULL'.
                     */
                    TransformExceptionFromNativeNode.getUncached().execute(node, threadState, mName, iResult != 0, true,
                                    ErrorMessages.EXECUTION_FAILED_WITHOUT_EXCEPTION, ErrorMessages.EXECUTION_RAISED_EXCEPTION);
                    break;
                case SLOT_PY_MOD_MULTIPLE_INTERPRETERS:
                    // ignored
                    // (mq) TODO: handle multiple interpreter cases
                    break;
                default:
                    throw PRaiseNode.raiseStatic(node, SystemError, ErrorMessages.MODULE_INITIALIZED_WITH_UNKNOWN_SLOT, mName, slotId);
            }
        }

        return 0;
    }

    /**
     * TODO(fa): overlaps with
     * {@link com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltins#GraalPyPrivate_Module_AddFunctions(long, long)}.
     *
     * <pre>
     *     struct PyMethodDef {
     *         const char * ml_name;
     *         PyCFunction  ml_meth;
     *         int          ml_flags;
     *         const char * ml_doc;
     *     };
     * </pre>
     */
    @TruffleBoundary
    static PBuiltinFunction createLegacyMethod(long methodDefPtr, int element, PythonLanguage language) {
        long methodNamePtr = readStructArrayPtrField(methodDefPtr, element, PyMethodDef__ml_name);
        if (methodNamePtr == NULLPTR) {
            return null;
        }
        TruffleString methodName = FromCharPointerNode.executeUncached(methodNamePtr);
        // note: 'ml_doc' may be NULL; in this case, we would store 'None'
        Object methodDoc = PNone.NONE;
        long methodDocPtr = readStructArrayPtrField(methodDefPtr, element, PyMethodDef__ml_doc);
        if (methodDocPtr != NULLPTR) {
            methodDoc = FromCharPointerNode.executeUncached(methodDocPtr, false);
        }

        int flags = readStructArrayIntField(methodDefPtr, element, PyMethodDef__ml_flags);
        long mlMethObj = readStructArrayPtrField(methodDefPtr, element, PyMethodDef__ml_meth);
        // CPy-style methods
        // TODO(fa) support static and class methods
        MethodDescriptorWrapper sig = MethodDescriptorWrapper.fromMethodFlags(flags);
        RootCallTarget callTarget = MethodDescriptorWrapper.getOrCreateCallTarget(language, sig, methodName, CExtContext.isMethStatic(flags));
        NativeFunctionPointer fun = CExtCommonNodes.bindFunctionPointer(mlMethObj, sig);
        PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(fun);
        PBuiltinFunction function = PFactory.createBuiltinFunction(language, methodName, null, PythonUtils.EMPTY_OBJECT_ARRAY, kwDefaults, flags, callTarget);
        HiddenAttr.WriteLongNode.executeUncached(function, METHOD_DEF_PTR, methodDefPtr);

        // write doc string; we need to directly write to the storage otherwise it is disallowed
        // writing to builtin types.
        WriteAttributeToPythonObjectNode.executeUncached(function, SpecialAttributeNames.T___DOC__, methodDoc);

        return function;
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
        static PMemoryView fromNative(PythonNativeObject buf, int flags,
                        @Bind Node inliningTarget,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Cached(inline = false) NativeToPythonTransferNode asPythonObjectNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkFunctionResultNode) {
            long bufPointer = toNativeNode.executeLong(buf);
            try {
                PythonContext context = PythonContext.get(inliningTarget);
                var callable = CApiContext.getNativeSymbol(inliningTarget, FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT);
                long result = ExternalFunctionInvoker.invokeGRAALPY_MEMORYVIEW_FROM_OBJECT(null, C_API_TIMING, context.ensureNativeContext(), BoundaryCallData.getUncached(),
                                context.getThreadState(PythonLanguage.get(inliningTarget)), callable, bufPointer, flags);
                return (PMemoryView) checkFunctionResultNode.execute(context, FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT.getTsName(), asPythonObjectNode.executeRaw(result));
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
