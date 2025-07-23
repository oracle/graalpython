/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PTR_COMPARE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_DEALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_FREE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TYPE_GENERIC_ALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SUBTYPE_TRAVERSE;
import static com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT;
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
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_buffer;
import static com.oracle.graal.python.nodes.HiddenAttr.METHOD_DEF_PTR;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COMPLEX__;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ResolvePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.UnicodeFromFormatNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.DefaultCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.ResolveHandleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.UpdateStrongRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.GetNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureExecutableNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureTruffleStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.GetNextVaArgNode;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
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
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public abstract class CExtNodes {

    private static final String J_UNICODE = "unicode";
    private static final String J_SUBTYPE_NEW = "_subtype_new";
    private static final long SIZEOF_PY_OBJECT_PTR = Long.BYTES;

    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    @ImportStatic({PGuards.class})
    @GenerateInline(false) // footprint reduction 44 -> 25
    public abstract static class SubtypeNew extends Node {

        /**
         * tget the <code>typename_subtype_new</code> function
         */
        protected NativeCAPISymbol getFunction() {
            throw shouldNotReachHere();
        }

        protected abstract Object execute(Object object, Object arg);

        @Specialization
        Object callNativeConstructor(Object object, Object arg,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached NativeToPythonTransferNode toJavaNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
            assert TypeNodes.NeedsNativeAllocationNode.executeUncached(object);
            try {
                Object callable = CApiContext.getNativeSymbol(inliningTarget, getFunction());
                Object result = interopLibrary.execute(callable, toSulongNode.execute(object), arg);
                return toJavaNode.execute(result);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw shouldNotReachHere("C subtype_new function failed", e);
            }
        }
    }

    @GenerateInline(false) // footprint reduction 44 -> 25
    public abstract static class FloatSubtypeNew extends SubtypeNew {

        @Override
        protected final NativeCAPISymbol getFunction() {
            return NativeCAPISymbol.FUN_FLOAT_SUBTYPE_NEW;
        }

        public final Object call(Object object, double arg) {
            return execute(object, arg);
        }

        public static FloatSubtypeNew create() {
            return CExtNodesFactory.FloatSubtypeNewNodeGen.create();
        }
    }

    public abstract static class TupleSubtypeNew extends SubtypeNew {

        @Child private PythonToNativeNode toNativeNode;

        @Override
        protected final NativeCAPISymbol getFunction() {
            return NativeCAPISymbol.FUN_TUPLE_SUBTYPE_NEW;
        }

        public final Object call(Object object, Object arg) {
            if (toNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNativeNode = insert(PythonToNativeNodeGen.create());
            }
            return execute(object, toNativeNode.execute(arg));
        }

        @NeverDefault
        public static TupleSubtypeNew create() {
            return CExtNodesFactory.TupleSubtypeNewNodeGen.create();
        }
    }

    public abstract static class StringSubtypeNew extends SubtypeNew {

        @Child private PythonToNativeNode toNativeNode;

        @Override
        protected final NativeCAPISymbol getFunction() {
            return NativeCAPISymbol.FUN_UNICODE_SUBTYPE_NEW;
        }

        public final Object call(Object object, Object arg) {
            if (toNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toNativeNode = insert(PythonToNativeNodeGen.create());
            }
            return execute(object, toNativeNode.execute(arg));
        }

        public static StringSubtypeNew create() {
            return CExtNodesFactory.StringSubtypeNewNodeGen.create();
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
                        @Cached IsSubtypeNode isSubtype,
                        @Cached CStructAccess.ReadDoubleNode read) {
            if (isFloatSubtype(inliningTarget, object, getClass, isSubtype)) {
                return read.readFromObj(object, PyFloatObject__ob_fval);
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

    /**
     * Materializes a primitive value of a primitive native wrapper to ensure pointer equality.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class MaterializeDelegateNode extends Node {

        public abstract Object execute(Node inliningTarget, PythonNativeWrapper object);

        @Specialization(guards = {"!isMaterialized(object)", "object.isBool()"})
        static PInt doBoolNativeWrapper(Node inliningTarget, PrimitiveNativeWrapper object) {
            // Special case for True and False: use singletons
            Python3Core core = PythonContext.get(inliningTarget);
            PInt materializedInt = object.getBool() ? core.getTrue() : core.getFalse();
            object.setMaterializedObject(materializedInt);

            // If the singleton already has a native wrapper, we may need to update the pointer
            // of wrapper 'object' since the native could code see the same pointer.
            if (materializedInt.getNativeWrapper() != null) {
                object.setNativePointer(materializedInt.getNativeWrapper().getNativePointer());
            } else {
                materializedInt.setNativeWrapper(object);
            }
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isInt()"})
        static PInt doIntNativeWrapper(PrimitiveNativeWrapper object,
                        @Bind PythonLanguage language) {
            PInt materializedInt = PFactory.createInt(language, object.getInt());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isLong()"})
        static PInt doLongNativeWrapper(PrimitiveNativeWrapper object,
                        @Bind PythonLanguage language) {
            PInt materializedInt = PFactory.createInt(language, object.getLong());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isDouble()", "!isNaN(object)"})
        static PFloat doDoubleNativeWrapper(PrimitiveNativeWrapper object,
                        @Bind PythonLanguage language) {
            PFloat materializedInt = PFactory.createFloat(language, object.getDouble());
            materializedInt.setNativeWrapper(object);
            object.setMaterializedObject(materializedInt);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isDouble()", "isNaN(object)"})
        static PFloat doDoubleNativeWrapperNaN(Node inliningTarget, PrimitiveNativeWrapper object) {
            // Special case for double NaN: use singleton
            PFloat materializedFloat = PythonContext.get(inliningTarget).getNaN();
            object.setMaterializedObject(materializedFloat);

            // If the NaN singleton already has a native wrapper, we may need to update the
            // pointer
            // of wrapper 'object' since the native code should see the same pointer.
            if (materializedFloat.getNativeWrapper() != null) {
                object.setNativePointer(materializedFloat.getNativeWrapper().getNativePointer());
            } else {
                materializedFloat.setNativeWrapper(object);
            }
            return materializedFloat;
        }

        @Specialization(guards = "isMaterialized(object)")
        static Object doMaterialized(PrimitiveNativeWrapper object) {
            return object.getDelegate();
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)")
        static Object doNativeWrapperGeneric(PythonNativeWrapper object) {
            return object.getDelegate();
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof PrimitiveNativeWrapper;
        }

        protected static boolean isNaN(PrimitiveNativeWrapper object) {
            assert object.isDouble();
            return Double.isNaN(object.getDouble());
        }

        static boolean isMaterialized(PrimitiveNativeWrapper wrapper) {
            return wrapper.getDelegate() != null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 60 -> 41
    public abstract static class AsCharPointerNode extends Node {
        public abstract Object execute(Object obj, boolean allocatePyMem);

        public abstract Object execute(TruffleString obj, boolean allocatePyMem);

        public final Object execute(Object obj) {
            return execute(obj, false);
        }

        @Specialization
        static Object doPString(PString str, boolean allocatePyMem,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @Cached TruffleString.CopyToByteArrayNode toBytes,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncoding,
                        @Shared @Cached CStructAccess.AllocateNode alloc,
                        @Shared @Cached CStructAccess.WriteByteNode write) {
            TruffleString value = castToStringNode.execute(inliningTarget, str);
            byte[] bytes = toBytes.execute(switchEncoding.execute(value, Encoding.UTF_8), Encoding.UTF_8);
            Object mem = alloc.alloc(bytes.length + 1, allocatePyMem);
            write.writeByteArray(mem, bytes);
            return mem;
        }

        @Specialization
        static Object doString(TruffleString str, boolean allocatePyMem,
                        @Shared @Cached TruffleString.CopyToByteArrayNode toBytes,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncoding,
                        @Shared @Cached CStructAccess.AllocateNode alloc,
                        @Shared @Cached CStructAccess.WriteByteNode write) {
            byte[] bytes = toBytes.execute(switchEncoding.execute(str, Encoding.UTF_8), Encoding.UTF_8);
            Object mem = alloc.alloc(bytes.length + 1, allocatePyMem);
            write.writeByteArray(mem, bytes);
            return mem;
        }

        @Specialization
        static Object doBytes(PBytes bytes, boolean allocatePyMem,
                        @Bind Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ToByteArrayNode toBytesNode,
                        @Shared @Cached CStructAccess.AllocateNode alloc,
                        @Shared @Cached CStructAccess.WriteByteNode write) {
            return doByteArray(toBytesNode.execute(inliningTarget, bytes.getSequenceStorage()), allocatePyMem, alloc, write);
        }

        @Specialization
        static Object doBytes(PByteArray bytes, boolean allocatePyMem,
                        @Bind Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.ToByteArrayNode toBytesNode,
                        @Shared @Cached CStructAccess.AllocateNode alloc,
                        @Shared @Cached CStructAccess.WriteByteNode write) {
            return doByteArray(toBytesNode.execute(inliningTarget, bytes.getSequenceStorage()), allocatePyMem, alloc, write);
        }

        @Specialization
        static Object doByteArray(byte[] arr, boolean allocatePyMem,
                        @Shared @Cached CStructAccess.AllocateNode alloc,
                        @Shared @Cached CStructAccess.WriteByteNode write) {
            Object mem = alloc.alloc(arr.length + 1, allocatePyMem);
            write.writeByteArray(mem, arr);
            return mem;
        }

        public static AsCharPointerNode getUncached() {
            return AsCharPointerNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 17
    public abstract static class FromCharPointerNode extends Node {
        public final TruffleString execute(Object charPtr) {
            return execute(charPtr, true);
        }

        public static TruffleString executeUncached(Object charPtr) {
            return FromCharPointerNodeGen.getUncached().execute(charPtr);
        }

        public abstract TruffleString execute(Object charPtr, boolean copy);

        @Specialization
        static TruffleString doCStringWrapper(CStringWrapper cStringWrapper, @SuppressWarnings("unused") boolean copy) {
            return cStringWrapper.getString();
        }

        @Specialization
        static TruffleString doCByteArrayWrapper(CByteArrayWrapper cByteArrayWrapper, boolean copy,
                        @Shared @Cached TruffleString.FromByteArrayNode fromBytes,
                        @Shared("switchEncoding") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            CompilerAsserts.partialEvaluationConstant(copy);
            byte[] byteArray = cByteArrayWrapper.getByteArray();
            return switchEncodingNode.execute(fromBytes.execute(byteArray, 0, byteArray.length, Encoding.UTF_8, copy), TS_ENCODING);
        }

        @Specialization(guards = "!isCArrayWrapper(charPtr)", limit = "3")
        static TruffleString doPointer(Object charPtr, boolean copy,
                        @Cached CStructAccess.ReadByteNode read,
                        @CachedLibrary("charPtr") InteropLibrary lib,
                        @Cached TruffleString.FromNativePointerNode fromNative,
                        @Shared @Cached TruffleString.FromByteArrayNode fromBytes,
                        @Shared("switchEncoding") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {

            int length = 0;
            while (read.readArrayElement(charPtr, length) != 0) {
                length++;
            }

            if (lib.isPointer(charPtr)) {
                return switchEncodingNode.execute(fromNative.execute(charPtr, 0, length, Encoding.UTF_8, copy), TS_ENCODING);
            }
            byte[] result = read.readByteArray(charPtr, length);
            return switchEncodingNode.execute(fromBytes.execute(result, Encoding.UTF_8, false), TS_ENCODING);
        }

        static boolean isCArrayWrapper(Object object) {
            return object instanceof CArrayWrapper || object instanceof PySequenceArrayWrapper;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetNativeClassNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, PythonAbstractNativeObject object);

        @Specialization
        static Object getNativeClass(Node inliningTarget, PythonAbstractNativeObject object,
                        @Cached(inline = false) CStructAccess.ReadObjectNode callGetObTypeNode,
                        @Cached ProfileClassNode classProfile) {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return classProfile.profile(inliningTarget, callGetObTypeNode.readFromObj(object, PyObject__ob_type));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PointerCompareNode extends Node {

        public abstract boolean execute(Node inliningTarget, RichCmpOp op, Object a, Object b);

        @Specialization
        static boolean doGeneric(Node inliningTarget, RichCmpOp op, Object a, Object b,
                        @Cached NormalizePtrNode normalizeA,
                        @Cached NormalizePtrNode normalizeB,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
            CompilerAsserts.partialEvaluationConstant(op);
            Object ptrA = normalizeA.execute(inliningTarget, a);
            Object ptrB = normalizeB.execute(inliningTarget, b);
            try {
                Object sym = CApiContext.getNativeSymbol(inliningTarget, FUN_PTR_COMPARE);
                return (int) interopLibrary.execute(sym, ptrA, ptrB, op.asNative()) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TypeSystemReference(PythonIntegerTypes.class)
        @GenerateInline
        @GenerateCached(false)
        @GenerateUncached
        abstract static class NormalizePtrNode extends Node {
            abstract Object execute(Node inliningTarget, Object ptr);

            @Specialization
            static Object doLong(long l) {
                return l;
            }

            @Specialization
            static Object doLong(PythonNativeObject o) {
                return o.getPtr();
            }

            @Specialization
            static Object doLong(PythonNativeVoidPtr o) {
                return o.getNativePointer();
            }
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
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @SuppressWarnings("unused") @Cached(inline = false) TruffleString.CodePointLengthNode lengthNode) {
            return codepointAtIndexNode.execute(value, 0, TS_ENCODING);
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

        @Specialization
        static Object doPythonNativeVoidPtr(PythonNativeVoidPtr object) {
            return object.getPointerObject();
        }

        @Specialization(guards = "!object.isDouble()")
        static long doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static long doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return (long) object.getDouble();
        }

        @Specialization
        static Object run(Node inliningTarget, PythonNativeWrapper value,
                        @Cached(inline = false) CastToNativeLongNode recursive) {
            // TODO(fa) this specialization should eventually go away
            return recursive.execute(inliningTarget, value.getDelegate());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 40 -> 21
    public abstract static class PCallCapiFunction extends Node {

        public static Object callUncached(NativeCAPISymbol symbol, Object... args) {
            return PCallCapiFunction.getUncached().execute(symbol, args);
        }

        public final Object call(NativeCAPISymbol symbol, Object... args) {
            return execute(symbol, args);
        }

        protected abstract Object execute(NativeCAPISymbol symbol, Object[] args);

        @Specialization
        static Object doWithoutContext(NativeCAPISymbol symbol, Object[] args,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached EnsureTruffleStringNode ensureTruffleStringNode) {
            try {
                PythonContext pythonContext = PythonContext.get(inliningTarget);
                if (!pythonContext.hasCApiContext()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    CApiContext.ensureCapiWasLoaded("call internal native GraalPy function");
                }
                // TODO review EnsureTruffleStringNode with GR-37896
                Object callable = CApiContext.getNativeSymbol(inliningTarget, symbol);
                return ensureTruffleStringNode.execute(inliningTarget, interopLibrary.execute(callable, args));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                // consider these exceptions to be fatal internal errors
                throw shouldNotReachHere(e);
            }
        }

        @NeverDefault
        public static PCallCapiFunction create() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.create();
        }

        public static PCallCapiFunction getUncached() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

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
    public static Object lookupNativeMemberInMRO(PythonManagedClass cls, @SuppressWarnings("unused") CFields nativeMemberName, HiddenAttr managedMemberName) {
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
            Object func = HiddenAttr.ReadNode.executeUncached(cls, managedMemberName, null);
            if (func != null) {
                return func;
            }
            return CApiContext.getNativeSymbol(null, symbol);
        }
        MroSequenceStorage mroStorage = GetMroStorageNode.executeUncached(cls);
        int n = mroStorage.length();
        for (int i = 0; i < n; i++) {
            PythonAbstractClass mroCls = (PythonAbstractClass) SequenceStorageNodes.GetItemDynamicNode.executeUncached(mroStorage, i);
            if (PGuards.isManagedClass(mroCls)) {
                Object result = HiddenAttr.ReadNode.executeUncached((PythonObject) mroCls, managedMemberName, null);
                if (result != null) {
                    return result;
                }
            } else {
                assert PGuards.isNativeClass(mroCls) : "invalid class inheritance structure; expected native class";
                Object result = CStructAccess.ReadPointerNode.getUncached().readFromObj((PythonNativeClass) mroCls, nativeMemberName);
                if (!PGuards.isNullOrZero(result, InteropLibrary.getUncached())) {
                    return result;
                }
            }
        }
        if (managedMemberName == HiddenAttr.CLEAR && (TypeNodes.GetTypeFlagsNode.executeUncached(cls) & TypeFlags.HAVE_GC) != 0) {
            return CApiContext.getNativeSymbol(null, FUN_NO_OP_CLEAR);
        }
        return HiddenAttr.ReadNode.executeUncached(PythonContext.get(null).lookupType(PythonBuiltinClassType.PythonObject), managedMemberName, NO_VALUE);
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
                    attr = ReadAttributeFromObjectNode.getUncachedForceType().execute(mroCls, CompilerDirectives.castExact(managedMemberName, TruffleString.class));
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
                return CStructAccess.ReadI64Node.getUncached().readFromObj((PythonNativeClass) mroCls, nativeMemberName);
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

        public final long execute(Object cls, CFields nativeMemberName, HiddenAttr managedMemberName) {
            return execute(cls, nativeMemberName, managedMemberName, null);
        }

        public abstract long execute(Object cls, CFields nativeMemberName, HiddenAttr managedMemberName, Function<PythonBuiltinClassType, Integer> builtinCallback);

        @Specialization
        static long doSingleContext(Object cls, CFields nativeMember, HiddenAttr managedMemberName, Function<PythonBuiltinClassType, Integer> builtinCallback,
                        @Bind Node inliningTarget,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached HiddenAttr.ReadNode readAttrNode,
                        @Cached CStructAccess.ReadI64Node getTypeMemberNode,
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
                    return getTypeMemberNode.readFromObj((PythonNativeClass) current, nativeMember);
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
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(inliningTarget, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        @Specialization
        static Object doObject(Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(inliningTarget, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        private static void raiseNative(Node inliningTarget, PythonBuiltinClassType errType, TruffleString format, Object[] arguments, PRaiseNode raiseNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode) {
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

        public static void executeUncached(Object pointer) {
            CExtNodesFactory.XDecRefPointerNodeGen.getUncached().execute(null, pointer);
        }

        public abstract void execute(Node inliningTarget, Object pointer);

        @Specialization
        static void doDecref(Node inliningTarget, Object pointerObj,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached(inline = false) CApiTransitions.ToPythonWrapperNode toPythonWrapperNode,
                        @Cached InlinedBranchProfile isWrapperProfile,
                        @Cached InlinedBranchProfile isNativeObject,
                        @Cached UpdateStrongRefNode updateRefNode,
                        @Cached(inline = false) CStructAccess.ReadI64Node readRefcount,
                        @Cached(inline = false) CStructAccess.WriteLongNode writeRefcount,
                        @Cached(inline = false) PCallCapiFunction callDealloc) {
            long pointer;
            if (pointerObj instanceof Long longPointer) {
                pointer = longPointer;
            } else {
                if (lib.isPointer(pointerObj)) {
                    try {
                        pointer = lib.asPointer(pointerObj);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                } else {
                    // No refcounting in managed mode
                    return;
                }
            }
            if (pointer == 0) {
                return;
            }
            PythonNativeWrapper wrapper = toPythonWrapperNode.executeWrapper(pointer, false);
            if (wrapper instanceof PythonAbstractObjectNativeWrapper objectWrapper) {
                isWrapperProfile.enter(inliningTarget);
                updateRefNode.execute(inliningTarget, objectWrapper, objectWrapper.decRef());
            } else if (wrapper == null) {
                isNativeObject.enter(inliningTarget);
                assert NativeToPythonNode.executeUncached(new NativePointer(pointer)) instanceof PythonAbstractNativeObject;
                long refcount = readRefcount.read(pointer, PyObject__ob_refcnt);
                if (refcount != IMMORTAL_REFCNT) {
                    refcount--;
                    writeRefcount.write(pointer, PyObject__ob_refcnt, refcount);
                    if (refcount == 0) {
                        callDealloc.call(FUN_PY_DEALLOC, pointer);
                    }
                }
            } else {
                throw CompilerDirectives.shouldNotReachHere("Cannot DECREF non-object");
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class ClearNativeWrapperNode extends Node {

        public abstract void execute(Node inliningTarget, Object delegate, PythonNativeWrapper nativeWrapper);

        @Specialization(guards = "!isPrimitiveNativeWrapper(nativeWrapper)")
        static void doPythonAbstractObject(PythonAbstractObject delegate, PythonNativeWrapper nativeWrapper) {
            // For non-temporary wrappers (all wrappers that need to preserve identity):
            // If this assertion fails, it indicates that the native code still uses a free'd native
            // wrapper.
            // TODO(fa): explicitly mark native wrappers to be identity preserving
            assert !(nativeWrapper instanceof PythonObjectNativeWrapper) || delegate.getNativeWrapper() == nativeWrapper : "inconsistent native wrappers";
            delegate.clearNativeWrapper();
        }

        @Specialization(guards = "delegate == null")
        static void doPrimitiveNativeWrapper(Node inliningTarget, @SuppressWarnings("unused") Object delegate, PrimitiveNativeWrapper nativeWrapper) {
            assert !isSmallIntegerWrapperSingleton(nativeWrapper, PythonContext.get(inliningTarget)) : "clearing primitive native wrapper singleton of small integer";
        }

        @Specialization(guards = "delegate != null")
        static void doPrimitiveNativeWrapperMaterialized(Node inliningTarget, PythonAbstractObject delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached InlinedConditionProfile profile) {
            if (profile.profile(inliningTarget, delegate.getNativeWrapper() == nativeWrapper)) {
                assert !isSmallIntegerWrapperSingleton(nativeWrapper, PythonContext.get(inliningTarget)) : "clearing primitive native wrapper singleton of small integer";
                delegate.clearNativeWrapper();
            }
        }

        @Specialization(guards = {"delegate != null", "!isAnyPythonObject(delegate)"})
        static void doOther(@SuppressWarnings("unused") Object delegate, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper) {
            assert !isPrimitiveNativeWrapper(nativeWrapper);
            // ignore
        }

        static boolean isPrimitiveNativeWrapper(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper instanceof PrimitiveNativeWrapper;
        }

        private static boolean isSmallIntegerWrapperSingleton(PrimitiveNativeWrapper nativeWrapper, PythonContext context) {
            return CApiGuards.isSmallIntegerWrapper(nativeWrapper) && GetNativeWrapperNode.doLongSmall(nativeWrapper.getLong(), context) == nativeWrapper;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ResolvePointerNode extends PNodeWithContext {

        public static Object executeUncached(Object pointerObject) {
            return ResolvePointerNodeGen.getUncached().execute(null, pointerObject);
        }

        public abstract Object execute(Node inliningTarget, Object pointerObject);

        public abstract Object executeLong(Node inliningTarget, long pointer);

        @Specialization
        static Object resolveLongCached(Node inliningTarget, long pointer,
                        @Exclusive @Cached ResolveHandleNode resolveHandleNode,
                        @Exclusive @Cached UpdateStrongRefNode updateRefNode) {
            Object lookup = CApiTransitions.lookupNative(pointer);
            if (lookup != null) {
                if (lookup instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
                    updateRefNode.execute(inliningTarget, objectNativeWrapper, objectNativeWrapper.incRef());
                }
                return lookup;
            }
            if (HandlePointerConverter.pointsToPyHandleSpace(pointer)) {
                return resolveHandleNode.execute(inliningTarget, pointer);
            }
            return pointer;
        }

        @Specialization(guards = "!isLong(pointerObject)")
        static Object resolveGeneric(Node inliningTarget, Object pointerObject,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Exclusive @Cached ResolveHandleNode resolveHandleNode,
                        @Exclusive @Cached UpdateStrongRefNode updateRefNode) {
            if (lib.isPointer(pointerObject)) {
                Object lookup;
                long pointer;
                try {
                    pointer = lib.asPointer(pointerObject);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
                lookup = CApiTransitions.lookupNative(pointer);
                if (lookup != null) {
                    if (lookup instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
                        updateRefNode.execute(inliningTarget, objectNativeWrapper, objectNativeWrapper.incRef());
                    }
                    return lookup;
                }
                if (HandlePointerConverter.pointsToPyHandleSpace(pointer)) {
                    return resolveHandleNode.execute(inliningTarget, pointer);
                }
            }
            // In this case, it cannot be a handle so we can just return the pointer object. It
            // could, of course, still be a native pointer.
            return pointerObject;
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
        static long doPythonNativeVoidPtr(@SuppressWarnings("unused") PythonNativeVoidPtr object) {
            return ((Long.SIZE - 1) / PYLONG_BITS_IN_DIGIT.intValue() + 1);
        }

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

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class UnicodeFromFormatNode extends Node {
        private static Pattern pattern;

        public static Object executeUncached(TruffleString format, Object vaList) {
            return UnicodeFromFormatNodeGen.getUncached().execute(null, format, vaList);
        }

        private static Matcher match(String formatStr) {
            if (pattern == null) {
                pattern = Pattern.compile("%(?<flags>[-+ #0])?(?<width>\\d+)?(\\.(?<prec>\\d+))?(?<len>(l|ll|z))?(?<spec>[%cduixspAUVSR])");
            }
            return pattern.matcher(formatStr);
        }

        public abstract Object execute(Node inliningTarget, TruffleString format, Object vaList);

        @Specialization
        @TruffleBoundary
        Object doGeneric(TruffleString f, Object vaList) {
            // TODO use TruffleString [GR-38103]
            String format = f.toJavaStringUncached();

            // helper nodes
            NativeToPythonNode toJavaNode = NativeToPythonNodeGen.getUncached();
            CastToJavaStringNode castToJavaStringNode = CastToJavaStringNodeGen.getUncached();
            FromCharPointerNode fromCharPointerNode = FromCharPointerNodeGen.getUncached();
            InteropLibrary interopLibrary = InteropLibrary.getUncached();

            StringBuilder result = new StringBuilder();
            int vaArgIdx = 0;
            Object unicodeObj;
            try {
                Matcher matcher = match(format);
                int cur = 0;
                while (matcher.find(cur)) {
                    // not all combinations are valid
                    boolean valid = false;

                    // add anything before the match
                    result.append(format, cur, matcher.start());

                    cur = matcher.end();

                    String spec = matcher.group("spec");
                    String len = matcher.group("len");
                    int prec = getPrec(matcher.group("prec"));
                    assert spec.length() == 1;
                    char la = spec.charAt(0);
                    PythonContext context = PythonContext.get(null);
                    switch (la) {
                        case '%':
                            // %%
                            result.append('%');
                            valid = true;
                            break;
                        case 'c':
                            int ordinal = getAndCastToInt(interopLibrary, vaList);
                            if (ordinal < 0 || ordinal > 0x110000) {
                                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.OverflowError, ErrorMessages.CHARACTER_ARG_NOT_IN_RANGE);
                            }
                            result.append((char) ordinal);
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'd':
                        case 'i':
                            // %d, %i, %ld, %li, %lld, %lli, %zd, %zi
                            if (len != null) {
                                switch (len) {
                                    case "ll":
                                    case "l":
                                    case "z":
                                        vaArgIdx++;
                                        result.append(castToLong(interopLibrary, GetNextVaArgNode.executeUncached(vaList)));
                                        valid = true;
                                        break;
                                }
                            } else {
                                result.append(getAndCastToInt(interopLibrary, vaList));
                                vaArgIdx++;
                                valid = true;
                            }
                            break;
                        case 'u':
                            // %u, %lu, %llu, %zu
                            if (len != null) {
                                switch (len) {
                                    case "ll":
                                    case "l":
                                    case "z":
                                        vaArgIdx++;
                                        result.append(castToLong(interopLibrary, GetNextVaArgNode.executeUncached(vaList)));
                                        valid = true;
                                        break;
                                }
                            } else {
                                result.append(Integer.toUnsignedString(getAndCastToInt(interopLibrary, vaList)));
                                vaArgIdx++;
                                valid = true;
                            }
                            break;
                        case 'x':
                            // %x
                            result.append(Integer.toHexString(getAndCastToInt(interopLibrary, vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 's':
                            // %s
                            Object charPtr = GetNextVaArgNode.executeUncached(vaList);
                            String sValue;
                            if (interopLibrary.isNull(charPtr)) {
                                // CPython would segfault. Let's make debugging easier for ourselves
                                sValue = "(NULL)";
                            } else {
                                unicodeObj = fromCharPointerNode.execute(charPtr);
                                sValue = castToJavaStringNode.execute(unicodeObj);
                            }
                            try {
                                if (prec == -1) {
                                    result.append(sValue);
                                } else {
                                    result.append(sValue, 0, Math.min(sValue.length(), prec));
                                }
                            } catch (CannotCastException e) {
                                // That should really not happen because we created the unicode
                                // object with FromCharPointerNode which guarantees to return a
                                // String/PString.
                                throw shouldNotReachHere();
                            }
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'p':
                            // %p
                            Object ptr = GetNextVaArgNode.executeUncached(vaList);
                            long value;
                            if (interopLibrary.isPointer(ptr)) {
                                value = interopLibrary.asPointer(ptr);
                            } else if (interopLibrary.hasIdentity(ptr)) {
                                value = interopLibrary.identityHashCode(ptr);
                            } else {
                                value = System.identityHashCode(ptr);
                            }
                            result.append(PythonUtils.formatJString("0x%x", value));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'A':
                            // %A
                            result.append(callBuiltin(context, BuiltinNames.T_ASCII, getPyObject(vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'U':
                            // %U
                            result.append(castToJavaStringNode.execute(getPyObject(vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'V':
                            // %V
                            Object pyObjectPtr = GetNextVaArgNode.executeUncached(vaList);
                            if (InteropLibrary.getUncached().isNull(pyObjectPtr)) {
                                unicodeObj = fromCharPointerNode.execute(GetNextVaArgNode.executeUncached(vaList));
                            } else {
                                unicodeObj = toJavaNode.execute(pyObjectPtr);
                            }
                            result.append(castToJavaStringNode.execute(unicodeObj));
                            vaArgIdx += 2;
                            valid = true;
                            break;
                        case 'S':
                            // %S
                            result.append(callBuiltin(context, BuiltinNames.T_STR, getPyObject(vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'R':
                            // %R
                            result.append(callBuiltin(context, BuiltinNames.T_REPR, getPyObject(vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                    }
                    // this means, we did not detect a valid format specifier, so add the whole
                    // group
                    if (!valid) {
                        result.append(matcher.group());
                    }
                }
                // add anything after the last matched group (or the whole format string if nothing
                // matched)
                result.append(format, cur, format.length());
            } catch (InteropException e) {
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.SystemError, ErrorMessages.ERROR_WHEN_ACCESSING_VAR_ARG_AT_POS, vaArgIdx);
            }
            return toTruffleStringUncached(result.toString());
        }

        private static int getPrec(String prec) {
            if (prec == null) {
                return -1;
            }
            return Integer.parseInt(prec);
        }

        /**
         * Read an element from the {@code va_list} with the specified type and cast it to a Java
         * {@code int}. Throws a {@code SystemError} if this is not possible.
         */
        private int getAndCastToInt(InteropLibrary lib, Object vaList) throws InteropException {
            Object value = GetNextVaArgNode.executeUncached(vaList);
            if (lib.fitsInInt(value)) {
                try {
                    return lib.asInt(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere();
                }
            }
            if (!lib.isPointer(value)) {
                lib.toNative(value);
            }
            if (lib.isPointer(value)) {
                try {
                    return (int) lib.asPointer(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere();
                }
            }
            throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.SystemError, ErrorMessages.P_OBJ_CANT_BE_INTEPRETED_AS_INTEGER, value);
        }

        /**
         * Cast a value to a Java {@code long}. Throws a {@code SystemError} if this is not
         * possible.
         */
        private long castToLong(InteropLibrary lib, Object value) {
            if (lib.fitsInLong(value)) {
                try {
                    return lib.asLong(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere();
                }
            }
            if (!lib.isPointer(value)) {
                lib.toNative(value);
            }
            if (lib.isPointer(value)) {
                try {
                    return lib.asPointer(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere();
                }
            }
            throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.SystemError, ErrorMessages.P_OBJ_CANT_BE_INTEPRETED_AS_INTEGER, value);
        }

        private static Object getPyObject(Object vaList) throws InteropException {
            return NativeToPythonNode.executeUncached(GetNextVaArgNode.executeUncached(vaList));
        }

        @TruffleBoundary
        private static Object callBuiltin(PythonContext context, TruffleString builtinName, Object object) {
            Object attribute = PyObjectLookupAttr.executeUncached(context.getBuiltins(), builtinName);
            return CastToJavaStringNodeGen.getUncached().execute(CallNode.executeUncached(attribute, object));
        }
    }

    abstract static class MultiPhaseExtensionModuleInitNode extends Node {

        // according to definitions in 'moduleobject.h'
        static final int SLOT_PY_MOD_CREATE = 1;
        static final int SLOT_PY_MOD_EXEC = 2;
        static final int SLOT_PY_MOD_MULTIPLE_INTERPRETERS = 3;

    }

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
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 68 -> 49
    public abstract static class CreateModuleNode extends MultiPhaseExtensionModuleInitNode {
        private static final String NFI_CREATE_NAME = "create";
        private static final String NFI_CREATE_SRC = "(POINTER,POINTER):POINTER";
        private static final Source NFI_LIBFFI_CREATE = Source.newBuilder(J_NFI_LANGUAGE, NFI_CREATE_SRC, NFI_CREATE_NAME).build();
        private static final Source NFI_PANAMA_CREATE = Source.newBuilder(J_NFI_LANGUAGE, "with panama " + NFI_CREATE_SRC, NFI_CREATE_NAME).build();

        public abstract Object execute(CApiContext capiContext, ModuleSpec moduleSpec, Object moduleDef, Object library);

        @Specialization
        @TruffleBoundary
        static Object doGeneric(CApiContext capiContext, ModuleSpec moduleSpec, Object moduleDefWrapper, Object library,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CStructAccess.ReadPointerNode readPointer,
                        @Cached CStructAccess.ReadI64Node readI64,
                        @CachedLibrary(limit = "3") InteropLibrary interopLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToPythonObjectNode writeAttrToMethodNode,
                        @Cached CreateMethodNode addLegacyMethodNode,
                        @Cached NativeToPythonTransferNode toJavaNode,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached CStructAccess.ReadI32Node readI32Node,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode,
                        @Cached PRaiseNode raiseNode) {
            // call to type the pointer
            Object moduleDef = moduleDefWrapper instanceof PythonAbstractNativeObject ? ((PythonAbstractNativeObject) moduleDefWrapper).getPtr() : moduleDefWrapper;

            /*
             * The name of the module is taken from the module spec and *NOT* from the module
             * definition.
             */
            TruffleString mName = moduleSpec.name;
            Object mDoc;
            long mSize;
            // do not eagerly read the doc string; this turned out to be unnecessarily expensive
            Object docPtr = readPointer.read(moduleDef, PyModuleDef__m_doc);
            if (PGuards.isNullOrZero(docPtr, interopLib)) {
                mDoc = NO_VALUE;
            } else {
                mDoc = fromCharPointerNode.execute(docPtr);
            }

            mSize = readI64.read(moduleDef, PyModuleDef__m_size);

            if (mSize < 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.M_SIZE_CANNOT_BE_NEGATIVE, mName);
            }

            // parse slot definitions
            Object createFunction = null;
            boolean hasExecutionSlots = false;
            Object slotDefinitions = readPointerNode.read(moduleDef, PyModuleDef__m_slots);
            if (!interopLib.isNull(slotDefinitions)) {
                loop: for (int i = 0;; i++) {
                    int slotId = readI32Node.readStructArrayElement(slotDefinitions, i, PyModuleDef_Slot__slot);
                    switch (slotId) {
                        case 0:
                            break loop;
                        case SLOT_PY_MOD_CREATE:
                            if (createFunction != null) {
                                throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_CREATE_SLOTS, mName);
                            }
                            createFunction = readPointerNode.readStructArrayElement(slotDefinitions, i, PyModuleDef_Slot__value);
                            break;
                        case SLOT_PY_MOD_EXEC:
                            hasExecutionSlots = true;
                            break;
                        case SLOT_PY_MOD_MULTIPLE_INTERPRETERS:
                            // ignored
                            // (mq) TODO: handle multiple interpreter cases
                            break;
                        default:
                            throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.MODULE_USES_UNKNOW_SLOT_ID, mName, slotId);
                    }
                }
            }

            Object module;
            if (createFunction != null && !interopLib.isNull(createFunction)) {
                Object[] cArguments = new Object[]{PythonToNativeNode.executeUncached(moduleSpec.originalModuleSpec), moduleDef};
                try {
                    Object result;
                    PythonContext context = capiContext.getContext();
                    if (!interopLib.isExecutable(createFunction)) {
                        boolean panama = context.getOption(PythonOptions.UsePanama);
                        Object signature = context.getEnv().parseInternal(panama ? NFI_PANAMA_CREATE : NFI_LIBFFI_CREATE).call();
                        result = interopLib.execute(SignatureLibrary.getUncached().bind(signature, createFunction), cArguments);
                    } else {
                        result = interopLib.execute(createFunction, cArguments);
                    }
                    PythonThreadState threadState = getThreadStateNode.execute(inliningTarget);
                    transformExceptionFromNativeNode.execute(inliningTarget, threadState, mName, interopLib.isNull(result), true, ErrorMessages.CREATION_FAILD_WITHOUT_EXCEPTION,
                                    ErrorMessages.CREATION_RAISED_EXCEPTION);
                    module = toJavaNode.execute(result);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }

                /*
                 * We are more strict than CPython and require this to be a PythonModule object.
                 * This means, if the custom 'create' function uses a native subtype of the module
                 * type, then we require it to call our new function.
                 */
                if (!(module instanceof PythonModule)) {
                    if (mSize > 0) {
                        throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.NOT_A_MODULE_OBJECT_BUT_REQUESTS_MODULE_STATE, mName);
                    }
                    if (hasExecutionSlots) {
                        throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.MODULE_SPECIFIES_EXEC_SLOTS_BUT_DIDNT_CREATE_INSTANCE, mName);
                    }
                    // otherwise CPython is just fine
                } else {
                    ((PythonModule) module).setNativeModuleDef(moduleDef);
                }
            } else {
                PythonModule pythonModule = PFactory.createPythonModule(language, mName);
                pythonModule.setNativeModuleDef(moduleDef);
                module = pythonModule;
            }

            Object methodDefinitions = readPointerNode.read(moduleDef, PyModuleDef__m_methods);
            if (!interopLib.isNull(methodDefinitions)) {
                for (int i = 0;; i++) {
                    PBuiltinFunction fun = addLegacyMethodNode.execute(inliningTarget, methodDefinitions, i);
                    if (fun == null) {
                        break;
                    }
                    PBuiltinMethod method = PFactory.createBuiltinMethod(language, module, fun);
                    writeAttrToMethodNode.execute(method, SpecialAttributeNames.T___MODULE__, mName);
                    writeAttrNode.execute(module, fun.getName(), method);
                }
            }

            writeAttrNode.execute(module, SpecialAttributeNames.T___DOC__, mDoc);
            writeAttrNode.execute(module, SpecialAttributeNames.T___LIBRARY__, library);
            capiContext.addLoadedExtensionLibrary(library);
            return module;
        }
    }

    /**
     * Equivalent of {@code PyModule_ExecDef}.
     */
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 60 -> 42
    public abstract static class ExecModuleNode extends MultiPhaseExtensionModuleInitNode {
        private static final String NFI_EXEC_SRC = "(POINTER):SINT32";
        private static final Source NFI_LIBFFI_EXEC = Source.newBuilder(J_NFI_LANGUAGE, NFI_EXEC_SRC, "exec").build();
        private static final Source NFI_PANAMA_EXEC = Source.newBuilder(J_NFI_LANGUAGE, "with panama " + NFI_EXEC_SRC, "exec").build();

        public abstract int execute(CApiContext capiContext, PythonModule module, Object moduleDef);

        @Specialization
        @TruffleBoundary
        static int doGeneric(CApiContext capiContext, PythonModule module, Object moduleDef,
                        @Bind Node inliningTarget,
                        @Cached ModuleGetNameNode getNameNode,
                        @Cached CStructAccess.ReadI64Node readI64,
                        @Cached CStructAccess.AllocateNode alloc,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached CStructAccess.ReadI32Node readI32Node,
                        @CachedLibrary(limit = "3") InteropLibrary interopLib,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode,
                        @Cached PRaiseNode raiseNode) {
            InteropLibrary U = InteropLibrary.getUncached();
            // call to type the pointer

            TruffleString mName = getNameNode.execute(inliningTarget, module);
            long mSize = readI64.read(moduleDef, PyModuleDef__m_size);

            try {
                // allocate md_state if necessary
                if (mSize >= 0) {
                    /*
                     * TODO(fa): We currently leak 'md_state' and need to use a shared finalizer or
                     * similar. We ignore that for now since the size will usually be very small
                     * and/or we could also use a Truffle buffer object.
                     */
                    Object mdState = alloc.alloc(mSize == 0 ? 1 : mSize); // ensure non-null value
                    assert mdState != null && !InteropLibrary.getUncached().isNull(mdState);
                    module.setNativeModuleState(mdState);
                }

                // parse slot definitions
                Object slotDefinitions = readPointerNode.read(moduleDef, PyModuleDef__m_slots);
                if (interopLib.isNull(slotDefinitions)) {
                    return 0;
                }
                loop: for (int i = 0;; i++) {
                    int slotId = readI32Node.readStructArrayElement(slotDefinitions, i, PyModuleDef_Slot__slot);
                    switch (slotId) {
                        case 0:
                            break loop;
                        case SLOT_PY_MOD_CREATE:
                            // handled in CreateModuleNode
                            break;
                        case SLOT_PY_MOD_EXEC:
                            Object execFunction = readPointerNode.readStructArrayElement(slotDefinitions, i, PyModuleDef_Slot__value);
                            PythonContext context = capiContext.getContext();
                            if (!U.isExecutable(execFunction)) {
                                boolean panama = context.getOption(PythonOptions.UsePanama);
                                Object signature = context.getEnv().parseInternal(panama ? NFI_PANAMA_EXEC : NFI_LIBFFI_EXEC).call();
                                execFunction = signatureLibrary.bind(signature, execFunction);
                            }
                            Object result = interopLib.execute(execFunction, PythonToNativeNode.executeUncached(module));
                            int iResult = interopLib.asInt(result);
                            /*
                             * It's a bit counterintuitive that we use 'isPrimitiveValue = false'
                             * but the function's return value is actually not a result but a status
                             * code. So, if the status code is '!=0' we know that an error occurred
                             * and won't ignore this if no error is set. This is then the same
                             * behaviour if we would have a pointer return type and got 'NULL'.
                             */
                            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget);
                            transformExceptionFromNativeNode.execute(inliningTarget, threadState, mName, iResult != 0, true, ErrorMessages.EXECUTION_FAILED_WITHOUT_EXCEPTION,
                                            ErrorMessages.EXECUTION_RAISED_EXCEPTION);
                            break;
                        case SLOT_PY_MOD_MULTIPLE_INTERPRETERS:
                            // ignored
                            // (mq) TODO: handle multiple interpreter cases
                            break;
                        default:
                            throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.MODULE_INITIALIZED_WITH_UNKNOWN_SLOT, mName, slotId);
                    }
                }
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw shouldNotReachHere();
            }

            return 0;
        }
    }

    /**
     * <pre>
     *     struct PyMethodDef {
     *         const char * ml_name;
     *         PyCFunction  ml_meth;
     *         int          ml_flags;
     *         const char * ml_doc;
     *     };
     * </pre>
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CreateMethodNode extends PNodeWithContext {

        public abstract PBuiltinFunction execute(Node inliningTarget, Object legacyMethodDef, int element);

        @Specialization
        static PBuiltinFunction doIt(Node inliningTarget, Object methodDef, int element,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached(inline = false) CStructAccess.ReadPointerNode readPointerNode,
                        @Cached(inline = false) CStructAccess.ReadI32Node readI32Node,
                        @Cached(inline = false) FromCharPointerNode fromCharPointerNode,
                        @Bind PythonLanguage language,
                        @Cached EnsureExecutableNode ensureCallableNode,
                        @Cached HiddenAttr.WriteNode writeHiddenAttrNode,
                        @Cached(inline = false) WriteAttributeToPythonObjectNode writeAttributeToPythonObjectNode) {
            Object methodNamePtr = readPointerNode.readStructArrayElement(methodDef, element, PyMethodDef__ml_name);
            if (resultLib.isNull(methodNamePtr) || (methodNamePtr instanceof Long && ((long) methodNamePtr) == 0)) {
                return null;
            }
            TruffleString methodName = fromCharPointerNode.execute(methodNamePtr);
            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            Object methodDocPtr = readPointerNode.readStructArrayElement(methodDef, element, PyMethodDef__ml_doc);
            if (!resultLib.isNull(methodDocPtr)) {
                methodDoc = fromCharPointerNode.execute(methodDocPtr, false);
            }

            int flags = readI32Node.readStructArrayElement(methodDef, element, PyMethodDef__ml_flags);
            Object mlMethObj = readPointerNode.readStructArrayElement(methodDef, element, PyMethodDef__ml_meth);
            // CPy-style methods
            // TODO(fa) support static and class methods
            PExternalFunctionWrapper sig = PExternalFunctionWrapper.fromMethodFlags(flags);
            RootCallTarget callTarget = PExternalFunctionWrapper.getOrCreateCallTarget(sig, PythonLanguage.get(inliningTarget), methodName, CExtContext.isMethStatic(flags));
            mlMethObj = ensureCallableNode.execute(inliningTarget, mlMethObj, sig);
            PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(mlMethObj);
            PBuiltinFunction function = PFactory.createBuiltinFunction(language, methodName, null, PythonUtils.EMPTY_OBJECT_ARRAY, kwDefaults, flags, callTarget);
            writeHiddenAttrNode.execute(inliningTarget, function, METHOD_DEF_PTR, methodDef);

            // write doc string; we need to directly write to the storage otherwise it is disallowed
            // writing to builtin types.
            writeAttributeToPythonObjectNode.execute(function, SpecialAttributeNames.T___DOC__, methodDoc);

            return function;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class HasNativeBufferNode extends PNodeWithContext {
        public abstract boolean execute(Node inliningTarget, PythonAbstractNativeObject object);

        @Specialization
        static boolean readTpAsBuffer(PythonAbstractNativeObject object,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached(inline = false) CStructAccess.ReadPointerNode readType,
                        @Cached(inline = false) CStructAccess.ReadPointerNode readAsBuffer) {
            Object type = readType.readFromObj(object, PyObject__ob_type);
            Object result = readAsBuffer.read(type, PyTypeObject__tp_as_buffer);
            return !PGuards.isNullOrZero(result, lib);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CreateMemoryViewFromNativeNode extends PNodeWithContext {
        public abstract PMemoryView execute(Node inliningTarget, PythonNativeObject object, int flags);

        @Specialization
        static PMemoryView fromNative(PythonNativeObject buf, int flags,
                        @Cached(inline = false) PythonToNativeNode toSulongNode,
                        @Cached(inline = false) NativeToPythonTransferNode asPythonObjectNode,
                        @Cached(inline = false) PCallCapiFunction callCapiFunction,
                        @Cached(inline = false) DefaultCheckFunctionResultNode checkFunctionResultNode) {
            Object result = callCapiFunction.call(FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT, toSulongNode.execute(buf), flags);
            checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_GRAALPY_MEMORYVIEW_FROM_OBJECT.getTsName(), result);
            return (PMemoryView) asPythonObjectNode.execute(result);
        }
    }

    /**
     * Decrements the ref count by one of any {@link PythonNativeWrapper} object.
     * <p>
     * This node avoids memory leaks for arguments given to native.<br>
     * Problem description:<br>
     * {@link PythonNativeWrapper} objects given to C code may go to native, i.e., a handle will be
     * allocated. In this case, no ref count manipulation is done since the C code considers the
     * reference to be borrowed and the Python code just doesn't do it because we have a GC. This
     * means that the handle will stay allocated and we are leaking the wrapper object.
     * </p>
     */
    @GenerateInline(false)
    @ImportStatic(CApiGuards.class)
    abstract static class ReleaseNativeWrapperNode extends Node {

        public abstract void execute(Object pythonObject);

        @Specialization
        static void doNativeWrapper(@SuppressWarnings("unused") PythonAbstractObjectNativeWrapper nativeWrapper) {
            /*
             * TODO(fa): this is the place where we should decrease the wrapper's refcount by 1 and
             * also make the ref weak
             */
        }

        @Specialization(guards = "!isNativeWrapper(object)")
        @SuppressWarnings("unused")
        static void doOther(Object object) {
            // just do nothing; this is an implicit profile
        }
    }

    /**
     * Similar to CPython's macro {@code Py_VISIT}, this node will call the provided visit function
     * on the item if that item is a native object. This is because we assume that the traverse and
     * visit functions are only used in the Python GC to determine reference cycles due to reference
     * counting. If a reference cycle is <it>interrupted</it> by a managed reference, we are fine
     * because the Java GC will correctly handle that.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class VisitNode extends Node {

        /**
         * Calls the visit function on the given item.
         *
         * @param frame The virtual frame (may be {code null}).
         * @param inliningTarget The inlining target.
         * @param threadState The Python thread state (must not be {@code null}).
         * @param item The item to visit (may be {@code null}). Only a native object will be
         *            visited.
         * @param visitFunction The visit function to call. This is expected to be an
         *            {@link InteropLibrary#isExecutable(Object) executable} interop object (must
         *            not be {@code null}).
         * @param visitArg The argument for the visit function as provided by the root caller.
         * @return {@code 0} on success, {@code !=0} on error
         */
        public abstract int execute(VirtualFrame frame, Node inliningTarget, PythonThreadState threadState, Object item, Object visitFunction, Object visitArg);

        @Specialization
        static int doGeneric(VirtualFrame frame, Node inliningTarget, PythonThreadState threadState, Object item, Object visitFunction, Object visitArg,
                        @Cached InlinedConditionProfile isNativeObjectProfile,
                        @Cached ExternalFunctionInvokeNode externalFunctionInvokeNode,
                        @Cached(inline = false) CheckPrimitiveFunctionResultNode checkPrimitiveFunctionResultNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode) {
            assert InteropLibrary.getUncached().isExecutable(visitFunction);
            if (isNativeObjectProfile.profile(inliningTarget, item instanceof PythonAbstractNativeObject)) {
                Object result = externalFunctionInvokeNode.call(frame, inliningTarget, threadState, CApiGCSupport.VISIT_TIMING, StringLiterals.T_VISIT, visitFunction,
                                toNativeNode.execute(item), visitArg);
                return (int) checkPrimitiveFunctionResultNode.executeLong(threadState, StringLiterals.T_VISIT, result);
            }
            return 0;
        }
    }
}
