/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.METHOD_DEF_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_DEREF_HANDLE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.MA_VERSION_TAG;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.MD_DEF;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.MD_STATE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.MEMORYVIEW_EXPORTS;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_BASE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_EXPORTS;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_REFCNT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_ALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_AS_BUFFER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_DEALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_DEL;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_DICT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_DICTOFFSET;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_FLAGS;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_FREE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_ITEMSIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_NAME;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_SUBCLASSES;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_VECTORCALL_OFFSET;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___WEAKLISTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Set;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AllToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.LookupNativeMemberInMRONode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.MaterializeDelegateNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ObSizeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.WrapVoidPtrNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapperFactory.ReadTypeNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PyDateTimeMRNode.DateTimeMode;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.SizeofWCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.property.PProperty;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSuperClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToBuiltinTypeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionCodeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public abstract class DynamicObjectNativeWrapper extends PythonNativeWrapper {
    static final String J_GP_OBJECT = "gp_object";
    static final TruffleString T_GP_OBJECT = tsLiteral(J_GP_OBJECT);
    static final TruffleString T_VALUE = tsLiteral("value");
    private DynamicObjectStorage nativeMemberStore;

    public DynamicObjectNativeWrapper() {
    }

    public DynamicObjectNativeWrapper(Object delegate) {
        super(delegate);
    }

    public DynamicObjectStorage createNativeMemberStore(PythonLanguage lang) {
        if (nativeMemberStore == null) {
            nativeMemberStore = new DynamicObjectStorage(lang);
        }
        return nativeMemberStore;
    }

    public DynamicObjectStorage getNativeMemberStore() {
        return nativeMemberStore;
    }

    @ExportMessage
    protected boolean isNull(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
        return lib.getDelegate(this) == PNone.NO_VALUE;
    }

    // READ
    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(@SuppressWarnings("unused") String member) {
        // TODO(fa) should that be refined?
        return true;
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(new String[]{J_GP_OBJECT});
    }

    @ExportMessage(name = "readMember")
    abstract static class ReadNode {

        @Specialization(guards = {"key == cachedObBase", "isObBase(cachedObBase)"}, limit = "1")
        static Object doObBaseCached(DynamicObjectNativeWrapper object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedObBase,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return object;
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(guards = {"key == cachedObRefcnt", "isObRefcnt(cachedObRefcnt)"}, limit = "1")
        static Object doObRefcnt(DynamicObjectNativeWrapper object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedObRefcnt,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return object.getRefCount();
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization
        static Object execute(DynamicObjectNativeWrapper object, String key,
                        @Exclusive @Cached ReadNativeMemberDispatchNode readNativeMemberNode,
                        @Exclusive @Cached AsPythonObjectNode getDelegate,
                        @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
            boolean mustRelease = gil.acquire();
            try {
                Object delegate = getDelegate.execute(object);

                // special key for the debugger
                if (J_GP_OBJECT.equals(key)) {
                    return delegate;
                }
                return readNativeMemberNode.execute(delegate, object, key);
            } finally {
                gil.release(mustRelease);
            }
        }

        protected static boolean isObBase(String key) {
            return OB_BASE.getMemberNameJavaString().equals(key);
        }

        protected static boolean isObRefcnt(String key) {
            return OB_REFCNT.getMemberNameJavaString().equals(key);
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class ReadNativeMemberDispatchNode extends Node {

        abstract Object execute(Object receiver, PythonNativeWrapper nativeWrapper, String key) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization
        static Object doClass(PythonManagedClass clazz, PythonNativeWrapper nativeWrapper, String key,
                        @Cached ReadTypeNativeMemberNode readTypeMemberNode) throws UnsupportedMessageException, UnknownIdentifierException {
            return readTypeMemberNode.execute(clazz, nativeWrapper, key);
        }

        @Specialization(guards = "!isManagedClass(clazz)")
        static Object doObject(Object clazz, PythonNativeWrapper nativeWrapper, String key,
                        @Cached ReadObjectNativeMemberNode readObjectMemberNode) throws UnsupportedMessageException, UnknownIdentifierException {
            return readObjectMemberNode.execute(clazz, nativeWrapper, key);
        }
    }

    static class UnknownMemberException extends Exception {
        private static final long serialVersionUID = 123L;
        public static final UnknownMemberException INSTANCE = new UnknownMemberException();

        private UnknownMemberException() {
            super(null, null);
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    @ImportStatic({NativeMember.class, SpecialMethodNames.class, SpecialAttributeNames.class, PythonOptions.class, SpecialMethodSlot.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNativeMemberNode extends Node {

        abstract Object execute(Object receiver, PythonNativeWrapper nativeWrapper, String key) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization(guards = "eq(OB_BASE, key)")
        static Object doObBase(Object o, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(OB_REFCNT, key)")
        static long doObRefcnt(@SuppressWarnings("unused") Object o, PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return nativeWrapper.getRefCount();
        }

        @Specialization(guards = "eq(OB_TYPE, key)")
        static Object doObType(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Cached GetClassNode getClassNode) {
            return toSulongNode.execute(getClassNode.execute(object));
        }

        protected static boolean eq(NativeMember expected, String actual) {
            return expected.getMemberNameJavaString().equals(actual);
        }

    }

    @GenerateUncached
    abstract static class ReadTypeNativeMemberNode extends ReadNativeMemberNode {

        public static final TruffleString T_SEQUENCE_CLEAR = tsLiteral("sequence_clear");

        @Specialization(guards = "eq(TP_FLAGS, key)")
        static long doTpFlags(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached GetTypeFlagsNode getTypeFlagsNode) {
            return getTypeFlagsNode.execute(object);
        }

        @Specialization(guards = "eq(TP_NAME, key)")
        static Object doTpName(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            return object.getClassNativeWrapper().getNameWrapper();
        }

        @Specialization(guards = "eq(TP_DOC, key)")
        static Object doTpDoc(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            Object docObj = getAttrNode.execute(object, SpecialAttributeNames.T___DOC__);
            docObj = assertNoJavaString(docObj);
            if (docObj instanceof TruffleString) {
                return new CStringWrapper((TruffleString) docObj);
            } else if (docObj instanceof PString) {
                return new CStringWrapper(castToStringNode.execute(docObj));
            }
            return PythonContext.get(getAttrNode).getNativeNull();
        }

        @Specialization(guards = "eq(TP_BASE, key)")
        static Object doTpBase(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached GetSuperClassNode getSuperClassNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object superClass = getSuperClassNode.execute(object);
            PythonNativeNull nativeNull = PythonContext.get(toSulongNode).getNativeNull();
            if (superClass != null) {
                return toSulongNode.execute(ensureClassObject(PythonContext.get(toSulongNode), superClass));
            }
            return toSulongNode.execute(nativeNull);
        }

        private static Object ensureClassObject(PythonContext context, Object klass) {
            if (klass instanceof PythonBuiltinClassType) {
                return context.lookupType((PythonBuiltinClassType) klass);
            }
            return klass;
        }

        @Specialization(guards = "eq(TP_ALLOC, key)")
        static Object doTpAlloc(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object result = lookupNativeMemberNode.execute(object, TP_ALLOC, TypeBuiltins.TYPE_ALLOC);
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_DEALLOC, key)")
        static Object doTpDealloc(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object result = lookupNativeMemberNode.execute(object, TP_DEALLOC, TypeBuiltins.TYPE_DEALLOC);
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_DEL, key)")
        @SuppressWarnings("unused")
        static Object doTpDel(PythonManagedClass object, PythonNativeWrapper nativeWrapper, String key,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("nullToSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(lookupNativeMemberNode.execute(object, TP_DEALLOC, TypeBuiltins.TYPE_DEALLOC));
        }

        @Specialization(guards = "eq(TP_FREE, key)")
        static Object doTpFree(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object result = lookupNativeMemberNode.execute(object, TP_FREE, TypeBuiltins.TYPE_FREE);
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_AS_NUMBER, key)")
        static Object doTpAsNumber(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            // TODO check for type and return 'NULL'
            return new PyNumberMethodsWrapper(object);
        }

        @Specialization(guards = "eq(TP_AS_BUFFER, key)")
        static Object doTpAsBuffer(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupNativeMemberInMRONode lookupTpAsBufferNode,
                        @Shared("nullToSulongNode") @Cached ToSulongNode toSulongNode) {
            Object result = lookupTpAsBufferNode.execute(object, NativeMember.TP_AS_BUFFER, TypeBuiltins.TYPE_AS_BUFFER);
            if (result == PNone.NO_VALUE) {
                // NULL pointer
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_AS_SEQUENCE, key)")
        static Object doTpAsSequence(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("lookupLen") @Cached(parameters = "Len") LookupCallableSlotInMRONode lookupLen,
                        @Shared("nullToSulongNode") @Cached ToSulongNode toSulongNode) {
            if (lookupLen.execute(object) != PNone.NO_VALUE) {
                return new PySequenceMethodsWrapper(object);
            } else {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
        }

        @Specialization(guards = "eq(TP_AS_MAPPING, key)")
        static Object doTpAsMapping(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupGetitem,
                        @Shared("lookupLen") @Cached(parameters = "Len") LookupCallableSlotInMRONode lookupLen,
                        @Shared("nullToSulongNode") @Cached ToSulongNode toSulongNode) {
            if (lookupGetitem.execute(object) != PNone.NO_VALUE && lookupLen.execute(object) != PNone.NONE) {
                return new PyMappingMethodsWrapper(object);
            } else {
                return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
            }
        }

        @Specialization(guards = "eq(TP_NEW, key)")
        static Object doTpNew(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ConditionProfile profileNewType,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Cached PCallCapiFunction callGetNewfuncTypeidNode) {
            // __new__ is magically a staticmethod for Python types. The tp_new slot lookup expects
            // to get the function
            Object newFunction = getAttrNode.execute(object, T___NEW__);
            if (profileNewType.profile(newFunction instanceof PDecoratedMethod)) {
                newFunction = ((PDecoratedMethod) newFunction).getCallable();
            }
            return ManagedMethodWrappers.createKeywords(newFunction, callGetNewfuncTypeidNode.call(NativeCAPISymbol.FUN_GET_NEWFUNC_TYPE_ID));
        }

        @Specialization(guards = "eq(TP_INIT, key)")
        static Object doTpInit(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode) {
            return PyProcsWrapper.createInitWrapper(getAttrNode.execute(object, T___INIT__));
        }

        @Specialization(guards = "eq(TP_HASH, key)")
        static Object doTpHash(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getHashNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getHashNode.execute(object, T___HASH__));
        }

        @Specialization(guards = "eq(TP_BASICSIZE, key)")
        static long doTpBasicsize(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asSizeNode") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, T___BASICSIZE__);
            return val != PNone.NO_VALUE ? asSizeNode.executeExact(null, val) : 0L;
        }

        @Specialization(guards = "eq(TP_ITEMSIZE, key)")
        static long doTpItemsize(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asSizeNode") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, T___ITEMSIZE__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return 0L;
            }
            return asSizeNode.executeExact(null, val);
        }

        @Specialization(guards = "eq(TP_DICTOFFSET, key)")
        static long doTpDictoffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asSizeNode") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (object instanceof PythonBuiltinClass) {
                return 0L;
            }
            Object dictoffset = getAttrNode.execute(object, T___DICTOFFSET__);
            return dictoffset != PNone.NO_VALUE ? asSizeNode.executeExact(null, dictoffset) : 0L;
        }

        @Specialization(guards = "eq(TP_WEAKLISTOFFSET, key)")
        static long doTpWeaklistoffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("asSizeNode") @Cached PyNumberAsSizeNode asSizeNode) {
            Object val = getAttrNode.execute(object, T___WEAKLISTOFFSET__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return 0L;
            }
            return asSizeNode.executeExact(null, val);
        }

        @Specialization(guards = "eq(TP_VECTORCALL_OFFSET, key)")
        static long doTpVectorcallOffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("asSizeNode") @Cached PyNumberAsSizeNode asSizeNode) {
            Object val = lookupNativeMemberNode.execute(object, TP_VECTORCALL_OFFSET, TypeBuiltins.TYPE_VECTORCALL_OFFSET);
            return val == PNone.NO_VALUE ? 0L : asSizeNode.executeExact(null, val);
        }

        @Specialization(guards = "eq(TP_RICHCOMPARE, key)")
        static Object doTpRichcompare(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getCmpNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getCmpNode.execute(object, T_RICHCMP));
        }

        @Specialization(guards = "eq(TP_SUBCLASSES, key)")
        static Object doTpSubclasses(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper,
                        @SuppressWarnings("unused") String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Cached ConditionProfile noWrapperProfile) {
            // TODO create dict view on subclasses set
            return PythonObjectNativeWrapper.wrap(factory.createDict(), noWrapperProfile);
        }

        @Specialization(guards = "eq(TP_GETATTR, key)")
        static Object doTpGetattr(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("nullToSulongNode") @Cached ToSulongNode toSulongNode) {
            // we do not provide 'tp_getattr'; code will usually then use 'tp_getattro'
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(TP_SETATTR, key)")
        static Object doTpSetattr(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("nullToSulongNode") @Cached ToSulongNode toSulongNode) {
            // we do not provide 'tp_setattr'; code will usually then use 'tp_setattro'
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(TP_GETATTRO, key)")
        static Object doTpGetattro(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createGetAttrWrapper(lookupAttrNode.execute(object, T___GETATTRIBUTE__));
        }

        @Specialization(guards = "eq(TP_SETATTRO, key)")
        static Object doTpSetattro(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createSetAttrWrapper(lookupAttrNode.execute(object, T___SETATTR__));
        }

        @Specialization(guards = "eq(TP_ITER, key)")
        static Object doTpIter(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ToSulongNode toSulongNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            Object method = lookupAttrNode.execute(object, T___ITER__);
            if (method instanceof PNone) {
                return toSulongNode.execute(method);
            }
            return PyProcsWrapper.createUnaryFuncWrapper(method);
        }

        @Specialization(guards = "eq(TP_ITERNEXT, key)")
        static Object doTpIternext(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ToSulongNode toSulongNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            Object method = lookupAttrNode.execute(object, T___NEXT__);
            if (method instanceof PNone) {
                return toSulongNode.execute(method);
            }
            return PyProcsWrapper.createUnaryFuncWrapper(method);
        }

        @Specialization(guards = "eq(TP_STR, key)")
        static Object doTpStr(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createUnaryFuncWrapper(lookupAttrNode.execute(object, T___STR__));
        }

        @Specialization(guards = "eq(TP_REPR, key)")
        static Object doTpRepr(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createUnaryFuncWrapper(lookupAttrNode.execute(object, T___REPR__));
        }

        @Specialization(guards = "eq(TP_DICT, key)")
        static Object doTpDict(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            // TODO(fa): we could cache the dict instance on the class' native wrapper
            PDict dict = getDict.execute(object);
            if (dict instanceof StgDictObject) {
                return dict.getNativeWrapper();
            }
            HashingStorage dictStorage = dict.getDictStorage();
            if (dictStorage instanceof DynamicObjectStorage) {
                // reuse the existing and modifiable storage
                return toSulongNode.execute(factory.createDict(dict.getDictStorage()));
            }
            HashingStorage storage = new DynamicObjectStorage(object.getStorage());
            dict.setDictStorage(storage);
            if (dictStorage != null) {
                // copy all mappings to the new storage
                addAllToOtherNode.execute(null, dictStorage, dict);
            }
            return toSulongNode.execute(dict);
        }

        @Specialization(guards = "eq(TP_TRAVERSE, key) || eq(TP_CLEAR, key)")
        static Object doTpTraverse(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached IsBuiltinClassProfile isTupleProfile,
                        @Cached IsBuiltinClassProfile isDictProfile,
                        @Cached IsBuiltinClassProfile isListProfile,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
            if (isTupleProfile.profileClass(object, PythonBuiltinClassType.PTuple) || isDictProfile.profileClass(object, PythonBuiltinClassType.PDict) ||
                            isListProfile.profileClass(object, PythonBuiltinClassType.PList)) {
                // We do not actually return _the_ traverse or clear function since we will never
                // need
                // it. It is just important to return a function.
                PythonModule pythonCextModule = PythonContext.get(toSulongNode).lookupBuiltinModule(PythonCextBuiltins.T_PYTHON_CEXT);
                Object sequenceClearMethod = readAttrNode.execute(pythonCextModule, T_SEQUENCE_CLEAR);
                return toSulongNode.execute(sequenceClearMethod);
            }
            return PythonContext.get(toSulongNode).getNativeNull();
        }

        @Specialization(guards = "eq(TP_CALL, key)")
        @SuppressWarnings("unused")
        static Object doTpCall(PythonManagedClass object, PythonNativeWrapper nativeWrapper, String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object callMethod = lookupAttrNode.execute(object, T___CALL__);
            if (callMethod != PNone.NO_VALUE) {
                return PyProcsWrapper.createTernaryFunctionWrapper(callMethod);
            }
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(TP_MRO, key)")
        @SuppressWarnings("unused")
        static Object doTpMro(PythonManagedClass object, PythonNativeWrapper nativeWrapper, String key,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(factory.createTuple(getMroStorageNode.execute(object)));
        }

        public static ReadTypeNativeMemberNode create() {
            return ReadTypeNativeMemberNodeGen.create();
        }
    }

    @GenerateUncached
    abstract static class ReadObjectNativeMemberNode extends ReadNativeMemberNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(ReadObjectNativeMemberNode.class);

        @Specialization(guards = "eq(D_COMMON, key)")
        static Object doDCommon(Object o, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(_BASE, key)")
        static Object doObBase(PString o, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(OB_SIZE, key)")
        static long doObSize(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ObSizeNode obSizeNode) {
            return obSizeNode.execute(object);
        }

        @Specialization(guards = "eq(MA_USED, key)")
        static int doMaUsed(PDict object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached PyObjectSizeNode sizeNode) {
            try {
                return sizeNode.execute(null, object);
            } catch (PException e) {
                return -1;
            }
        }

        @Specialization(guards = "eq(MA_VERSION_TAG, key)")
        @TruffleBoundary
        static long doMaVersionTag(PDict object, PythonObjectNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            if (HashingStorageLen.executeUncached(object.getDictStorage()) == 0) {
                return 0;
            }

            DynamicObjectStorage nativeMemberStore = nativeWrapper.getNativeMemberStore();
            if (nativeMemberStore == null) {
                nativeMemberStore = nativeWrapper.createNativeMemberStore(PythonLanguage.get(null));
            }
            Object item = HashingStorageGetItem.executeUncached(nativeMemberStore, MA_VERSION_TAG.getMemberNameTruffleString());
            long value = 1;
            if (item != null) {
                value = (long) item;
            }
            HashingStorage newStorage = HashingStorageSetItem.executeUncached(nativeMemberStore, MA_VERSION_TAG.getMemberNameTruffleString(), value + 1);
            assert newStorage == nativeMemberStore;
            return value;
        }

        @Specialization(guards = "eq(OB_SVAL, key)")
        static Object doObSval(PBytes object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            if (sequenceStorage instanceof NativeSequenceStorage) {
                return ((NativeSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 1);
        }

        @Specialization(guards = "eq(OB_START, key)")
        static Object doObStart(PByteArray object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            if (sequenceStorage instanceof NativeSequenceStorage) {
                return ((NativeSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 1);
        }

        @Specialization(guards = "eq(OB_EXPORTS, key)")
        static long doObExports(PByteArray object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.getExports();
        }

        @Specialization(guards = "eq(OB_FVAL, key)")
        static Object doObFval(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached("createClassProfile()") ValueProfile profile) throws UnsupportedMessageException {
            Object profiled = profile.profile(object);
            if (profiled instanceof PFloat) {
                return ((PFloat) profiled).getValue();
            } else if (profiled instanceof Double) {
                return object;
            }
            throw UnsupportedMessageException.create();
        }

        @Specialization(guards = "eq(OB_ITEM, key)")
        static Object doObItem(PSequence object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            if (sequenceStorage instanceof NativeSequenceStorage) {
                return ((NativeSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 4);
        }

        @Specialization(guards = "eq(OB_DIGIT, key)")
        static Object doObDigit(int object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyLongDigitsWrapper(object);
        }

        @Specialization(guards = "eq(OB_DIGIT, key)")
        static Object doObDigit(long object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyLongDigitsWrapper(object);
        }

        @Specialization(guards = "eq(OB_DIGIT, key)")
        static Object doObDigit(PInt object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyLongDigitsWrapper(object);
        }

        @Specialization(guards = "eq(COMPLEX_CVAL, key)")
        static Object doComplexCVal(PComplex object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyComplexWrapper(object);
        }

        @Specialization(guards = "eq(UNICODE_WSTR, key)")
        static Object doWstr(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asWideCharNode") @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Shared("sizeofWcharNode") @Cached SizeofWCharNode sizeofWcharNode) {
            int elementSize = (int) sizeofWcharNode.execute(CApiContext.LAZY_CONTEXT);
            return new PySequenceArrayWrapper(asWideCharNode.executeNativeOrder(object, elementSize), elementSize);
        }

        @Specialization(guards = "eq(UNICODE_WSTR_LENGTH, key)")
        static long doWstrLength(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asWideCharNode") @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Shared("sizeofWcharNode") @Cached SizeofWCharNode sizeofWcharNode) {
            long sizeofWchar = sizeofWcharNode.execute(CApiContext.LAZY_CONTEXT);
            PBytes result = asWideCharNode.executeNativeOrder(object, sizeofWchar);
            return result.getSequenceStorage().length() / sizeofWchar;
        }

        @Specialization(guards = "eq(UNICODE_LENGTH, key)")
        static long doUnicodeLength(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached StringLenNode stringLenNode) {
            return stringLenNode.execute(object);
        }

        @Specialization(guards = "eq(UNICODE_DATA, key)")
        static Object doUnicodeData(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyUnicodeWrappers.PyUnicodeData(object);
        }

        @Specialization(guards = "eq(UNICODE_STATE, key)")
        static Object doState(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            // TODO also support bare 'String' ?
            return new PyUnicodeWrappers.PyUnicodeState(object);
        }

        @Specialization(guards = "eq(UNICODE_HASH, key)")
        @TruffleBoundary
        static long doUnicodeHash(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            // TODO also support bare 'String' ?
            return object.hashCode();
        }

        @Specialization(guards = "eq(MD_DICT, key)")
        static Object doMdDict(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getDictNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getDictNode.execute(object, SpecialAttributeNames.T___DICT__));
        }

        @Specialization(guards = "eq(TP_DICT, key)")
        static Object doTpDict(PythonClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached GetOrCreateDictNode getDict,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getDict.execute(object));
        }

        @Specialization(guards = "eq(MD_DEF, key)")
        static Object doMdDef(PythonModule object, @SuppressWarnings("unused") DynamicObjectNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.getNativeModuleDef();
        }

        @Specialization(guards = "eq(BUF_DELEGATE, key)")
        static Object doBufDelegate(PBuffer object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object.getDelegate(), 1);
        }

        @Specialization(guards = "eq(BUF_READONLY, key)")
        static int doBufReadonly(PBuffer object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.isReadOnly() ? 1 : 0;
        }

        @Specialization(guards = "eq(MEMORYVIEW_FLAGS, key)")
        static int doMemoryViewFlags(PMemoryView object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.getFlags();
        }

        @Specialization(guards = "eq(MEMORYVIEW_EXPORTS, key)")
        static long doMemoryViewExports(PMemoryView object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.getExports().get();
        }

        @Specialization(guards = "eq(MEMORYVIEW_VIEW, key)")
        static Object doMemoryViewView(PMemoryView object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMemoryViewBufferWrapper(object);
        }

        @Specialization(guards = "eq(START, key)")
        static Object doStart(PSlice object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getStart());
        }

        @Specialization(guards = "eq(STOP, key)")
        static Object doStop(PSlice object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getStop());
        }

        @Specialization(guards = "eq(STEP, key)")
        static Object doStep(PSlice object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getStep());
        }

        @Specialization(guards = "eq(IM_SELF, key)")
        static Object doImSelf(PMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(IM_SELF, key)")
        static Object doImSelf(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(IM_FUNC, key)")
        static Object doImFunc(PMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFunction());
        }

        @Specialization(guards = "eq(IM_FUNC, key)")
        static Object doImFunc(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFunction());
        }

        @Specialization(guards = "eq(D_MEMBER, key)")
        static Object doDMember(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMemberDefWrapper(object);
        }

        @Specialization(guards = "eq(D_GETSET, key)")
        static Object doDGetSet(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyGetSetDefWrapper(object);
        }

        @Specialization(guards = "eq(D_NAME, key)")
        static Object doDName(PBuiltinFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getName());
        }

        @Specialization(guards = "eq(D_TYPE, key)")
        static Object doDType(PBuiltinFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object enclosingType = object.getEnclosingType();
            return toSulongNode.execute(enclosingType != null ? enclosingType : PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(D_NAME, key)")
        static Object doDName(GetSetDescriptor object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getName());
        }

        @Specialization(guards = "eq(D_TYPE, key)")
        static Object doDType(GetSetDescriptor object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getType());
        }

        @Specialization(guards = "eq(D_METHOD, key)")
        static Object doDMethod(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMethodDefWrapper(object);
        }

        @Specialization(guards = "eq(D_BASE, key)")
        static Object doDBase(PBuiltinFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new StructWrapperBaseWrapper(object);
        }

        static boolean isAnyFunctionObject(Object object) {
            return object instanceof PBuiltinFunction || object instanceof PBuiltinMethod || object instanceof PFunction || object instanceof PMethod;
        }

        @Specialization(guards = {"eq(M_ML, key)", "isAnyFunctionObject(object)"}, limit = "1")
        static Object doPyCFunctionObjectMMl(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary("object") DynamicObjectLibrary dylib) {
            Object methodDefPtr = dylib.getOrDefault(object, METHOD_DEF_PTR, null);
            if (methodDefPtr != null) {
                return methodDefPtr;
            }
            return new PyMethodDefWrapper(object);
        }

        @Specialization(guards = {"eq(M_MODULE, key)", "isAnyFunctionObject(object)"})
        static Object doPyCFunctionObjectMModule(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached ToSulongNode toSulongNode) {
            Object module = lookup.execute(null, object, T___MODULE__);
            return toSulongNode.execute(module != PNone.NO_VALUE ? module : PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(M_SELF, key)")
        static Object doPyCFunctionObjectMSelf(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(M_SELF, key)")
        static Object doPyCFunctionObjectMSelf(PMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(MM_CLASS, key)")
        static Object doPyCMethodObjectMMClass(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getClassObject());
        }

        @Specialization(guards = "eq(FUNC, key)")
        static Object doPyCMethodObjectFunc(@SuppressWarnings("unused") PBuiltinMethod object, PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return nativeWrapper;
        }

        @Specialization(guards = "eq(D_QUALNAME, key)")
        static Object doDQualname(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getAttributeNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getAttributeNode.execute(object, SpecialAttributeNames.T___QUALNAME__));
        }

        @Specialization(guards = "eq(SET_USED, key)")
        static long doSetUsed(PBaseSet object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(object.getDictStorage());
        }

        @Specialization(guards = "eq(MMAP_DATA, key)")
        Object doMmapData(PMMap object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.mmapGetPointer(getPosixSupport(), object.getPosixSupportHandle());
            } catch (PosixSupportLibrary.UnsupportedPosixFeatureException e) {
                return new PySequenceArrayWrapper(object, 1);
            }
        }

        @Specialization(guards = "eq(PROP_GET, key)")
        static Object doPropertyPropGet(PProperty object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFget());
        }

        @Specialization(guards = "eq(PROP_SET, key)")
        static Object doPropertyPropSet(PProperty object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFset());
        }

        @Specialization(guards = "eq(PROP_DEL, key)")
        static Object doPropertyPropDel(PProperty object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFdel());
        }

        @Specialization(guards = "eq(PROP_DOC, key)")
        static Object doPropertyPropDoc(PProperty object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getDoc());
        }

        @Specialization(guards = "eq(PROP_GETTERDOC, key)")
        static int doPropertyPropGetterDoc(PProperty object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return PInt.intValue(object.getGetterDoc());
        }

        protected static boolean isPyDateTimeCAPI(PythonObject object, GetClassNode getClassNode, GetNameNode getNameNode, TruffleString.EqualNode eqNode) {
            return isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(object)), eqNode);
        }

        protected static boolean isPyDateTimeCAPIType(TruffleString className, TruffleString.EqualNode eqNode) {
            return eqNode.execute(className, PyDateTimeCAPIWrapper.T_DATETIME_CAPI, TS_ENCODING);

        }

        protected static DateTimeMode getDateTimeMode(PythonObject object, GetClassNode getClassNode, GetNameNode getNameNode, TruffleString.EqualNode eqNode) {
            return PyDateTimeMRNode.getModeFromTypeName(getNameNode.execute(getClassNode.execute(object)), eqNode);
        }

        @Specialization(guards = "isPyDateTimeCAPI(object, getClassNode, getNameNode, eqNode)", limit = "1")
        static Object doDatetimeCAPI(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, String key,
                        @Cached @SuppressWarnings("unused") TruffleString.EqualNode eqNode,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Shared("getNameNode") @Cached @SuppressWarnings("unused") GetNameNode getNameNode,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            return toSulongNode.execute(getAttrNode.execute(getClassNode.execute(object), key));
        }

        @Specialization(guards = "mode != null", limit = "1")
        static Object doDatetimeData(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached @SuppressWarnings("unused") TruffleString.EqualNode eqNode,
                        @Shared("getNameNode") @Cached @SuppressWarnings("unused") GetNameNode getNameNode,
                        @Shared("getClassNode") @Cached @SuppressWarnings("unused") GetClassNode getClassNode,
                        @Bind("getDateTimeMode(object, getClassNode, getNameNode, eqNode)") DateTimeMode mode,
                        @Cached PyDateTimeMRNode pyDateTimeMRNode) {
            return pyDateTimeMRNode.execute(object, key, mode);
        }

        @Specialization(guards = "eq(F_LINENO, key)")
        static int doFLineno(PFrame object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.getLine();
        }

        @Specialization(guards = "eq(F_CODE, key)")
        static Object doFCode(PFrame object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            RootCallTarget ct = object.getTarget();
            if (ct != null) {
                return toSulongNode.execute(factory.createCode(ct));
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "eq(FUNC_CODE, key)")
        static Object doPFunctionCode(PFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getFunctionCodeNode.execute(object));
        }

        @Specialization(guards = "eq(FUNC_GLOBALS, key)")
        static Object doPFunctionGlobals(PFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getGlobals());
        }

        @Specialization(guards = "eq(FUNC_DEFAULTS, key)")
        static Object doPFunctionDefaults(PFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object[] defaults = object.getDefaults();
            if (defaults.length > 0) {
                return toSulongNode.execute(factory.createTuple(defaults));
            }
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(FUNC_KWDEFAULTS, key)")
        static Object doPFunctionKwDefaults(PFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            PKeyword[] kwDefaults = object.getKwDefaults();
            if (kwDefaults.length > 0) {
                return toSulongNode.execute(factory.createDict(kwDefaults));
            }
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = "eq(FUNC_CLOSURE, key)")
        static Object doPFunctionClosure(PFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            PCell[] closure = object.getClosure();
            if (closure != null) {
                return toSulongNode.execute(factory.createTuple(closure));
            }
            return toSulongNode.execute(factory.createEmptyTuple());
        }

        @Specialization(guards = "eq(cachedMember, key)", limit = "1")
        static Object doPCodeCached(PCode object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached("getNativeMember(key)") NativeMember cachedMember,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            switch (cachedMember) {
                case CO_ARGCOUNT:
                    return object.co_argcount();
                case CO_POSONLYARGCOUNT:
                    return object.co_posonlyargcount();
                case CO_KWONLYCOUNT:
                    return object.co_kwonlyargcount();
                case CO_NLOCALS:
                    return object.co_nlocals();
                case CO_STACKSIZE:
                    return object.co_stacksize();
                case CO_FLAGS:
                    return object.getFlags();
                case CO_FIRSTLINENO:
                    return toSulongNode.execute(object.co_firstlineno());
                case CO_CODE:
                    return toSulongNode.execute(object.co_code(factory));
                case CO_CONSTS:
                    return toSulongNode.execute(object.co_consts(factory));
                case CO_NAMES:
                    return toSulongNode.execute(object.co_names(factory));
                case CO_VARNAMES:
                    return toSulongNode.execute(object.co_varnames(factory));
                case CO_FREEVARS:
                    return toSulongNode.execute(object.co_freevars(factory));
                case CO_CELLVARS:
                    return toSulongNode.execute(object.co_cellvars(factory));
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(replaces = "doPCodeCached")
        static Object doPCode(PCode object, PythonNativeWrapper nativeWrapper, String key,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return doPCodeCached(object, nativeWrapper, key, NativeMember.byName(key), factory, toSulongNode);
        }

        @Specialization(guards = {"eq(VALUE, key)", "isStopIteration(exception, getClassNode, isSubtypeNode)"}, limit = "1")
        static Object doException(PBaseException exception, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("getClassNode") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getAttr.execute(null, exception, T_VALUE));
        }

        protected boolean isStopIteration(PBaseException exception, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(exception), PythonBuiltinClassType.StopIteration);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object object, PythonNativeWrapper nativeWrapper, String key,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) throws UnknownIdentifierException {
            if (nativeWrapper instanceof DynamicObjectNativeWrapper) {
                DynamicObjectNativeWrapper dynamicWrapper = (DynamicObjectNativeWrapper) nativeWrapper;
                // This is the preliminary generic case: There are native members we know that they
                // exist but we do currently not represent them. So, store them into a dynamic
                // object
                // such that native code at least reads the value that was written before.
                if (dynamicWrapper.isMemberReadable(key)) {
                    logGeneric(key);
                    DynamicObjectStorage nativeMemberStore = dynamicWrapper.getNativeMemberStore();
                    if (nativeMemberStore != null) {
                        TruffleString tKey = fromJavaStringNode.execute(key, TS_ENCODING);
                        return getItem.execute(nativeMemberStore, tKey);
                    }
                    return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
                }
            }
            throw UnknownIdentifierException.create(key);
        }

        @TruffleBoundary(allowInlining = true)
        private static void logGeneric(String key) {
            LOGGER.log(Level.FINE, "read of Python struct native member " + key);
        }

        static NativeMember getNativeMember(String key) {
            return NativeMember.byName(key);
        }

        protected final Object getPosixSupport() {
            return PythonContext.get(this).getPosixSupport();
        }
    }

    // WRITE
    @GenerateUncached
    abstract static class WriteNativeMemberNode extends Node {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(WriteNativeMemberNode.class);

        abstract void execute(Object receiver, PythonNativeWrapper nativeWrapper, String key, Object value)
                        throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;

        @GenerateUncached
        @ImportStatic({NativeMember.class, PGuards.class, SpecialMethodNames.class, SpecialAttributeNames.class})
        abstract static class WriteKnownNativeMemberNode extends Node {
            abstract void execute(Object receiver, PythonNativeWrapper nativeWrapper, String key, Object value)
                            throws UnknownMemberException, UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;

            @Specialization(guards = "eq(OB_TYPE, key)")
            static void doObType(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                            @SuppressWarnings("unused") PythonManagedClass value,
                            @Cached ConditionProfile noWrapperProfile) {
                // At this point, we do not support changing the type of an object.
                PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
            }

            @Specialization(guards = "eq(OB_REFCNT, key)")
            static void doObRefcnt(@SuppressWarnings("unused") Object o, PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long value) {
                nativeWrapper.setRefCount(value);
            }

            @Specialization(guards = "eq(OB_EXPORTS, key)")
            static void doObExports(PByteArray array, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long value) {
                array.setExports(value);
            }

            @Specialization(guards = "eq(TP_NAME, key)")
            static void doTpName(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value,
                            @Cached CExtNodes.FromCharPointerNode fromCharPointerNode,
                            @Cached CastToTruffleStringNode cast) {
                object.setName(cast.execute(fromCharPointerNode.execute(value)));
            }

            @Specialization(guards = "eq(TP_FLAGS, key)")
            static void doTpFlags(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long flags,
                            @Cached TypeNodes.SetTypeFlagsNode setTypeFlagsNode) {
                if (object instanceof PythonBuiltinClass) {
                    /*
                     * Assert that we try to set the same flags, except the abc flags for sequence
                     * and mapping. If there is a difference, this means we did not properly
                     * maintain our flag definition in TypeNodes.GetTypeFlagsNode.
                     */
                    assert assertFlagsInSync(object, flags);
                }
                setTypeFlagsNode.execute(object, flags);
            }

            @TruffleBoundary
            private static boolean assertFlagsInSync(PythonManagedClass object, long newFlags) {
                long expected = GetTypeFlagsNode.getUncached().execute(object) & ~TypeFlags.COLLECTION_FLAGS;
                long actual = newFlags & ~TypeFlags.COLLECTION_FLAGS;
                assert expected == actual : "type flags of " + object.getName() + " definitions are out of sync: expected " + expected + " vs. actual " + actual;
                return true;
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_BASICSIZE, key)"})
            static void doTpBasicsize(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long basicsize,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached WriteAttributeToBuiltinTypeNode writeAttrToBuiltinNode,
                            @Cached ConditionProfile isBuiltinProfile,
                            @Cached IsBuiltinClassProfile profile) {
                if (profile.profileClass(object, PythonBuiltinClassType.PythonClass)) {
                    writeAttrNode.execute(object, TypeBuiltins.TYPE_BASICSIZE, basicsize);
                } else if (isBuiltinProfile.profile(object instanceof PythonBuiltinClass || object instanceof PythonBuiltinClassType)) {
                    writeAttrToBuiltinNode.execute(object, T___BASICSIZE__, basicsize);
                } else {
                    writeAttrNode.execute(object, T___BASICSIZE__, basicsize);
                }
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_ITEMSIZE, key)"})
            static void doTpItemsize(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long itemsize,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached ConditionProfile profile) {
                if (!profile.profile(object instanceof PythonBuiltinClass)) {
                    // not expected to happen ...
                    writeAttrNode.execute(object, T___ITEMSIZE__, itemsize);
                }
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_ALLOC, key)"})
            static void doTpAlloc(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object allocFunc,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached WrapVoidPtrNode asPythonObjectNode) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_ALLOC, asPythonObjectNode.execute(allocFunc));
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_VECTORCALL_OFFSET, key)"})
            static void doTpVectorcallOffset(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long offset,
                            @Cached WriteAttributeToObjectNode writeAttrNode) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_VECTORCALL_OFFSET, offset);
            }

            @ValueType
            private static final class SubclassAddState {
                private final HashingStorage storage;
                private final HashingStorageGetItem getItemNode;
                private final Set<PythonAbstractClass> subclasses;

                private SubclassAddState(HashingStorage storage, HashingStorageGetItem getItemNode, Set<PythonAbstractClass> subclasses) {
                    this.storage = storage;
                    this.getItemNode = getItemNode;
                    this.subclasses = subclasses;
                }
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_DEALLOC, key)"})
            static void doTpDelloc(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object deallocFunc,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached WrapVoidPtrNode asPythonObjectNode) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_DEALLOC, asPythonObjectNode.execute(deallocFunc));
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_DEL, key)"})
            static void doTpDel(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object delFunc,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached WrapVoidPtrNode asPythonObjectNode) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_DEL, asPythonObjectNode.execute(delFunc));
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_FREE, key)"})
            static void doTpFree(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object freeFunc,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached WrapVoidPtrNode asPythonObjectNode) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_FREE, asPythonObjectNode.execute(freeFunc));
            }

            @Specialization(guards = {"isPythonClass(object)", "eq(TP_AS_BUFFER, key)"})
            static void doTpAsBuffer(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object bufferProcs,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached WrapVoidPtrNode asPythonObjectNode) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_AS_BUFFER, asPythonObjectNode.execute(bufferProcs));
            }

            @GenerateUncached
            abstract static class EachSubclassAdd extends HashingStorageForEachCallback<Set<PythonAbstractClass>> {

                @Override
                public abstract Set<PythonAbstractClass> execute(Frame frame, HashingStorage storage, HashingStorageIterator it, Set<PythonAbstractClass> subclasses);

                @Specialization
                public Set<PythonAbstractClass> doIt(Frame frame, HashingStorage storage, HashingStorageIterator it, Set<PythonAbstractClass> subclasses,
                                @Cached HashingStorageIteratorKey itKey,
                                @Cached HashingStorageIteratorKeyHash itKeyHash,
                                @Cached HashingStorageGetItemWithHash getItemNode) {
                    long hash = itKeyHash.execute(storage, it);
                    Object key = itKey.execute(storage, it);
                    setAdd(subclasses, (PythonClass) getItemNode.execute(frame, storage, key, hash));
                    return subclasses;
                }

                @TruffleBoundary
                protected static void setAdd(Set<PythonAbstractClass> set, PythonClass cls) {
                    set.add(cls);
                }
            }

            @Specialization(guards = "eq(TP_SUBCLASSES, key)", limit = "1")
            static void doTpSubclasses(PythonClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, PythonObjectNativeWrapper value,
                            @Cached GetSubclassesNode getSubclassesNode,
                            @Cached EachSubclassAdd eachNode,
                            @CachedLibrary("value") PythonNativeWrapperLibrary lib,
                            @Cached HashingStorageForEach forEachNode) {
                PDict dict = (PDict) lib.getDelegate(value);
                HashingStorage storage = dict.getDictStorage();
                Set<PythonAbstractClass> subclasses = getSubclassesNode.execute(object);
                forEachNode.execute(null, storage, eachNode, subclasses);
            }

            @Specialization(guards = "eq(MD_DEF, key)")
            static void doMdDef(PythonModule object, @SuppressWarnings("unused") DynamicObjectNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value) {
                object.setNativeModuleDef(value);
            }

            private static boolean isBuiltinDict(IsBuiltinClassProfile isPrimitiveDictProfile, Object value) {
                return value instanceof PDict &&
                                (isPrimitiveDictProfile.profileObject(value, PythonBuiltinClassType.PDict) ||
                                                isPrimitiveDictProfile.profileObject(value, PythonBuiltinClassType.StgDict));
            }

            @Specialization(guards = "eq(TP_DICT, key)")
            static void doTpDict(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object nativeValue,
                            @Cached GetDictIfExistsNode getDict,
                            @Cached SetDictNode setDict,
                            @Cached AsPythonObjectNode asPythonObjectNode,
                            @Cached WriteAttributeToObjectNode writeAttrNode,
                            @Cached HashingStorageGetIterator getIterator,
                            @Cached HashingStorageIteratorNext itNext,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorValue itValue,
                            @Cached IsBuiltinClassProfile isPrimitiveDictProfile) {
                Object value = asPythonObjectNode.execute(nativeValue);
                if (isBuiltinDict(isPrimitiveDictProfile, value)) {
                    // special and fast case: commit items and change store
                    PDict d = (PDict) value;
                    HashingStorage storage = d.getDictStorage();
                    HashingStorageIterator it = getIterator.execute(storage);
                    while (itNext.execute(storage, it)) {
                        writeAttrNode.execute(object, itKey.execute(storage, it), itValue.execute(storage, it));
                    }
                    PDict existing = getDict.execute(object);
                    if (existing != null) {
                        d.setDictStorage(existing.getDictStorage());
                    } else {
                        d.setDictStorage(new DynamicObjectStorage(object.getStorage()));
                    }
                    setDict.execute(object, d);
                } else {
                    // TODO custom mapping object
                }
            }

            @Specialization(guards = "eq(TP_DICTOFFSET, key)")
            static void doTpDictoffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value,
                            @Cached CastToJavaLongExactNode cast,
                            @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode) throws UnsupportedMessageException, UnknownIdentifierException {
                // TODO properly implement 'tp_dictoffset' for builtin classes
                if (!(object instanceof PythonBuiltinClass)) {
                    try {
                        setAttrNode.execute(object, T___DICTOFFSET__, cast.execute(value));
                    } catch (CannotCastException e) {
                        throw CompilerDirectives.shouldNotReachHere("non-integer passed to tp_dictoffset assignment");
                    }
                }
            }

            @Specialization(guards = "eq(MEMORYVIEW_EXPORTS, key)")
            static void doMemoryViewExports(PMemoryView object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value,
                            @Cached CastToJavaLongExactNode cast) {
                try {
                    object.getExports().set(cast.execute(value));
                } catch (CannotCastException | PException e) {
                    throw CompilerDirectives.shouldNotReachHere("Failed to set memoryview exports: invalid type");
                }
            }

            @Specialization(guards = "eq(F_LINENO, key)")
            static void doFLineno(PFrame object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value,
                            @Cached CastToJavaIntLossyNode castToJavaIntNode) {
                try {
                    int lineno = castToJavaIntNode.execute(value);
                    object.setLine(lineno);
                } catch (PException e) {
                    // Ignore
                }
            }

            @Fallback
            @SuppressWarnings("unused")
            static void fallback(Object object, PythonNativeWrapper wrapper, String key, Object value) throws UnknownMemberException {
                throw UnknownMemberException.INSTANCE;
            }

            protected static boolean eq(NativeMember expected, String actual) {
                return expected.getMemberNameJavaString().equals(actual);
            }
        }

        @Specialization(rewriteOn = UnknownMemberException.class)
        static void doKnown(Object object, PythonNativeWrapper nativeWrapper, String key, Object value,
                        @Cached WriteKnownNativeMemberNode writeKnownNativeMemberNode)
                        throws UnknownMemberException, UnsupportedMessageException, UnsupportedTypeException, UnknownIdentifierException {
            writeKnownNativeMemberNode.execute(object, nativeWrapper, key, value);
        }

        @Specialization(replaces = "doKnown")
        static void doGeneric(Object object, PythonNativeWrapper nativeWrapper, String key, Object value,
                        @Cached WriteKnownNativeMemberNode writeKnownNativeMemberNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached HashingStorageSetItem setItem)
                        throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
            try {
                writeKnownNativeMemberNode.execute(object, nativeWrapper, key, value);
            } catch (UnknownMemberException e) {
                if (nativeWrapper instanceof DynamicObjectNativeWrapper) {
                    // This is the preliminary generic case: There are native members we know that
                    // they exist but we do currently not represent them. So, store them into a
                    // dynamic object such that native code at least reads the value that was
                    // written before.
                    if (((DynamicObjectNativeWrapper) nativeWrapper).isMemberModifiable(key)) {
                        logGeneric(key);
                        TruffleString tKey = fromJavaStringNode.execute(key, TS_ENCODING);
                        DynamicObjectStorage storage = ((DynamicObjectNativeWrapper) nativeWrapper).createNativeMemberStore(PythonLanguage.get(writeKnownNativeMemberNode));
                        HashingStorage newStorage = setItem.execute(null, storage, tKey, value);
                        assert newStorage == storage;
                    } else {
                        throw UnknownIdentifierException.create(key);
                    }
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static void logGeneric(String key) {
            LOGGER.log(Level.FINE, "write of Python struct native member " + key);
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return OB_TYPE.getMemberNameJavaString().equals(member) ||
                        OB_REFCNT.getMemberNameJavaString().equals(member) ||
                        OB_EXPORTS.getMemberNameJavaString().equals(member) ||
                        TP_NAME.getMemberNameJavaString().equals(member) ||
                        TP_FLAGS.getMemberNameJavaString().equals(member) ||
                        TP_BASICSIZE.getMemberNameJavaString().equals(member) ||
                        TP_ITEMSIZE.getMemberNameJavaString().equals(member) ||
                        TP_ALLOC.getMemberNameJavaString().equals(member) ||
                        TP_DEALLOC.getMemberNameJavaString().equals(member) ||
                        TP_DEL.getMemberNameJavaString().equals(member) ||
                        TP_FREE.getMemberNameJavaString().equals(member) ||
                        TP_SUBCLASSES.getMemberNameJavaString().equals(member) ||
                        MD_DEF.getMemberNameJavaString().equals(member) ||
                        MD_STATE.getMemberNameJavaString().equals(member) ||
                        TP_DICT.getMemberNameJavaString().equals(member) ||
                        TP_DICTOFFSET.getMemberNameJavaString().equals(member) ||
                        TP_AS_BUFFER.getMemberNameJavaString().equals(member) ||
                        MEMORYVIEW_EXPORTS.getMemberNameJavaString().equals(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached WriteNativeMemberNode writeNativeMemberNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        boolean mustRelease = gil.acquire();
        try {
            writeNativeMemberNode.execute(lib.getDelegate(this), this, member, value);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    protected boolean isMemberRemovable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void removeMember(@SuppressWarnings("unused") String member) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    protected Object execute(Object[] arguments,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached PythonAbstractObject.PExecuteNode executeNode,
                    @Cached AllToJavaNode allToJavaNode,
                    @Cached ToJavaNode selfToJava,
                    @Cached ToNewRefNode toNewRefNode,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object[] converted;
            Object function = lib.getDelegate(this);
            if (function instanceof PBuiltinFunction && CExtContext.isMethNoArgs(((PBuiltinFunction) function).getFlags()) && arguments.length == 2) {
                /*
                 * The C function signature for METH_NOARGS is: methNoArgs(PyObject* self, PyObject*
                 * dummy); So we need to trim away the dummy argument, otherwise we will get an
                 * error.
                 */
                converted = new Object[]{selfToJava.execute(arguments[0])};
            } else if (function instanceof PBuiltinFunction && CExtContext.isMethVarargs(((PBuiltinFunction) function).getFlags()) && arguments.length == 2) {
                converted = allToJavaNode.execute(arguments);
                assert converted[1] instanceof PTuple;
                SequenceStorage argsStorage = ((PTuple) converted[1]).getSequenceStorage();
                Object[] wrapArgs = new Object[argsStorage.length() + 1];
                wrapArgs[0] = converted[0];
                PythonUtils.arraycopy(argsStorage.getInternalArray(), 0, wrapArgs, 1, argsStorage.length());
                converted = wrapArgs;
            } else {
                converted = allToJavaNode.execute(arguments);
            }

            Object result = executeNode.execute(function, converted);

            /*
             * If a native wrapper is executed, we directly wrap some managed function and assume
             * that new references are returned. So, we increase the ref count for each native
             * object here.
             */
            return toNewRefNode.execute(result);
        } catch (PException e) {
            transformExceptionToNativeNode.execute(e);
            return toNewRefNode.execute(PythonContext.get(gil).getNativeNull());
        } finally {
            gil.release(mustRelease);
        }
    }

    // TO NATIVE, IS POINTER, AS POINTER
    @GenerateUncached
    abstract static class ToNativeNode extends Node {
        public abstract void execute(PythonNativeWrapper obj);

        @Specialization
        static void doPythonNativeWrapper(PythonNativeWrapper obj,
                        @Cached ToPyObjectNode toPyObjectNode,
                        @Cached InvalidateNativeObjectsAllManagedNode invalidateNode,
                        @Cached IsPointerNode isPointerNode) {
            invalidateNode.execute();
            if (!isPointerNode.execute(obj)) {
                Object ptr = toPyObjectNode.execute(obj);
                obj.setNativePointer(ptr);
            }
        }
    }

    @GenerateUncached
    abstract static class PAsPointerNode extends Node {

        public abstract long execute(PythonNativeWrapper o);

        @Specialization(guards = {"obj.isBool()", "!lib.isNative(obj)"}, limit = "1")
        long doBoolNotNative(PrimitiveNativeWrapper obj,
                        @Shared("longProfile") @Cached ConditionProfile isLongProfile,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Cached MaterializeDelegateNode materializeNode,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            // special case for True and False singletons
            PInt boxed = (PInt) materializeNode.execute(obj);
            assert lib.getNativePointer(obj) == lib.getNativePointer(boxed.getNativeWrapper());
            return ensureLong(interopLib, lib.getNativePointer(obj), isLongProfile);
        }

        @Specialization(guards = {"obj.isBool()", "lib.isNative(obj)"}, limit = "1")
        long doBoolNative(PrimitiveNativeWrapper obj,
                        @Shared("longProfile") @Cached ConditionProfile isLongProfile,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            return ensureLong(interopLib, lib.getNativePointer(obj), isLongProfile);
        }

        @Specialization(guards = "!isBoolNativeWrapper(obj)", limit = "1")
        long doFast(PythonNativeWrapper obj,
                        @Shared("longProfile") @Cached ConditionProfile isLongProfile,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = lib.getNativePointer(obj);
            return ensureLong(interopLib, nativePointer, isLongProfile);
        }

        private static long ensureLong(InteropLibrary interopLib, Object nativePointer, ConditionProfile isLongProfile) {
            if (isLongProfile.profile(nativePointer instanceof Long)) {
                return (long) nativePointer;
            } else {
                try {
                    return interopLib.asPointer(nativePointer);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_PTR_OBJ, nativePointer);
                }
            }
        }

        protected static boolean isBoolNativeWrapper(Object obj) {
            return obj instanceof PrimitiveNativeWrapper && ((PrimitiveNativeWrapper) obj).isBool();
        }
    }

    @GenerateUncached
    abstract static class ToPyObjectNode extends Node {
        public abstract Object execute(PythonNativeWrapper wrapper);

        @Specialization
        static Object doObject(PythonNativeWrapper wrapper,
                        @Cached PCallCapiFunction callNativeUnary) {
            return callNativeUnary.call(FUN_DEREF_HANDLE, wrapper);
        }
    }

    /**
     * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
     * correct shape of the corresponding native type {@code struct _object}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class PythonObjectNativeWrapper extends DynamicObjectNativeWrapper {

        public PythonObjectNativeWrapper(PythonAbstractObject object) {
            super(object);
        }

        public static DynamicObjectNativeWrapper wrap(PythonAbstractObject obj, ConditionProfile noWrapperProfile) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        public static DynamicObjectNativeWrapper wrapNewRef(PythonAbstractObject obj, ConditionProfile noWrapperProfile) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            } else {
                // it already existed, so we need to increase the reference count
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            PythonNativeWrapperLibrary lib = PythonNativeWrapperLibrary.getUncached();
            return PythonUtils.formatJString("PythonObjectNativeWrapper(%s, isNative=%s)", lib.getDelegate(this), lib.isNative(this));
        }

        @ExportMessage
        @ImportStatic({PGuards.class, NativeMember.class, DynamicObjectNativeWrapper.class, PythonUtils.class})
        abstract static class IsMemberReadable {

            @SuppressWarnings("unused")
            @Specialization(guards = {"stringEquals(cachedName, name, stringProfile)", "isValid(cachedName)"})
            static boolean isReadableNativeMembers(PythonObjectNativeWrapper receiver, String name,
                            @Cached ConditionProfile stringProfile,
                            @Cached(value = "name", allowUncached = true) String cachedName) {
                return true;
            }

            @SuppressWarnings("unused")
            @Specialization(guards = "stringEquals(J_GP_OBJECT, name, stringProfile)")
            static boolean isReadableCachedGP(PythonObjectNativeWrapper receiver, String name,
                            @Cached ConditionProfile stringProfile) {
                return true;
            }

            static boolean isPyTimeMemberReadable(PythonObjectNativeWrapper receiver, PythonNativeWrapperLibrary lib, GetClassNode getClassNode, GetNameNode getNameNode,
                            TruffleString.EqualNode eqNode) {
                return ReadObjectNativeMemberNode.isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(lib.getDelegate(receiver))), eqNode);
            }

            @SuppressWarnings("unused")
            @Specialization(guards = "isPyTimeMemberReadable(receiver, lib, getClassNode, getNameNode, eqNode)")
            static boolean isReadablePyTime(PythonObjectNativeWrapper receiver, String name,
                            @Cached TruffleString.EqualNode eqNode,
                            @CachedLibrary("receiver") PythonNativeWrapperLibrary lib,
                            @Cached GetClassNode getClassNode,
                            @Cached GetNameNode getNameNode) {
                return true;
            }

            @Specialization
            @TruffleBoundary
            static boolean isReadableFallback(PythonObjectNativeWrapper receiver, String name,
                            @CachedLibrary("receiver") PythonNativeWrapperLibrary lib,
                            @Cached TruffleString.EqualNode eqNode,
                            @Cached GetClassNode getClassNode,
                            @Cached GetNameNode getNameNode) {
                return J_GP_OBJECT.equals(name) || NativeMember.isValid(name) ||
                                ReadObjectNativeMemberNode.isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(lib.getDelegate(receiver))), eqNode);
            }
        }

        @ExportMessage
        @ImportStatic({PGuards.class, NativeMember.class, DynamicObjectNativeWrapper.class})
        abstract static class IsMemberModifiable {

            @SuppressWarnings("unused")
            @Specialization(guards = "stringEquals(cachedName, name, stringProfile)")
            static boolean isModifiableCached(PythonObjectNativeWrapper receiver, String name,
                            @Cached ConditionProfile stringProfile,
                            @Cached(value = "name", allowUncached = true) String cachedName,
                            @Cached(value = "isValid(name)", allowUncached = true) boolean isValid) {
                return isValid;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

        public static final byte PRIMITIVE_STATE_BOOL = 1;
        public static final byte PRIMITIVE_STATE_BYTE = 1 << 1;
        public static final byte PRIMITIVE_STATE_INT = 1 << 2;
        public static final byte PRIMITIVE_STATE_LONG = 1 << 3;
        public static final byte PRIMITIVE_STATE_DOUBLE = 1 << 4;

        private final byte state;
        private final long value;
        private final double dvalue;

        private PrimitiveNativeWrapper(byte state, long value) {
            assert state != PRIMITIVE_STATE_DOUBLE;
            this.state = state;
            this.value = value;
            this.dvalue = 0.0;
        }

        private PrimitiveNativeWrapper(double dvalue) {
            this.state = PRIMITIVE_STATE_DOUBLE;
            this.value = 0;
            this.dvalue = dvalue;
        }

        public byte getState() {
            return state;
        }

        public boolean getBool() {
            return value != 0;
        }

        public byte getByte() {
            return (byte) value;
        }

        public int getInt() {
            return (int) value;
        }

        public long getLong() {
            return value;
        }

        public double getDouble() {
            return dvalue;
        }

        public boolean isBool() {
            return state == PRIMITIVE_STATE_BOOL;
        }

        public boolean isByte() {
            return state == PRIMITIVE_STATE_BYTE;
        }

        public boolean isInt() {
            return state == PRIMITIVE_STATE_INT;
        }

        public boolean isLong() {
            return state == PRIMITIVE_STATE_LONG;
        }

        public boolean isDouble() {
            return state == PRIMITIVE_STATE_DOUBLE;
        }

        public boolean isIntLike() {
            return (state & (PRIMITIVE_STATE_BYTE | PRIMITIVE_STATE_INT | PRIMITIVE_STATE_LONG)) != 0;
        }

        public boolean isSubtypeOfInt() {
            return !isDouble();
        }

        // this method exists just for readability
        public Object getMaterializedObject(PythonNativeWrapperLibrary lib) {
            return lib.getDelegate(this);
        }

        // this method exists just for readability
        public void setMaterializedObject(Object materializedPrimitive) {
            setDelegate(materializedPrimitive);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            CompilerAsserts.neverPartOfCompilation();

            PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
            if (other.state == state && other.value == value && other.dvalue == dvalue) {
                // n.b.: in the equals, we also require the native pointer to be the same. The
                // reason for this is to avoid native pointer sharing. Handles are shared if the
                // objects are equal but in this case we must not share because otherwise we would
                // mess up the reference counts.
                return GetNativePointer.getGenericPtr(this) == GetNativePointer.getGenericPtr(other);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (Long.hashCode(value) ^ Long.hashCode(Double.doubleToRawLongBits(dvalue)) ^ state);
        }

        @Override
        public String toString() {
            String typeName;
            if (isIntLike()) {
                typeName = "int";
            } else if (isDouble()) {
                typeName = "float";
            } else if (isBool()) {
                typeName = "bool";
            } else {
                typeName = "unknown";
            }
            return "PrimitiveNativeWrapper(" + typeName + "(" + value + ")" + ')';
        }

        public static PrimitiveNativeWrapper createBool(boolean val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BOOL, PInt.intValue(val));
        }

        public static PrimitiveNativeWrapper createByte(byte val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BYTE, val);
        }

        public static PrimitiveNativeWrapper createInt(int val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_INT, val);
        }

        public static PrimitiveNativeWrapper createLong(long val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_LONG, val);
        }

        public static PrimitiveNativeWrapper createDouble(double val) {
            return new PrimitiveNativeWrapper(val);
        }

        @ExportMessage
        @ImportStatic({PGuards.class, NativeMember.class, DynamicObjectNativeWrapper.class, PythonUtils.class})
        abstract static class IsMemberReadable {

            @SuppressWarnings("unused")
            @Specialization(guards = {"stringEquals(cachedName, name, stringProfile)", "isValid(cachedName)"})
            static boolean isReadableNativeMembers(PrimitiveNativeWrapper receiver, String name,
                            @Cached ConditionProfile stringProfile,
                            @Cached(value = "name", allowUncached = true) String cachedName) {
                return true;
            }

            @SuppressWarnings("unused")
            @Specialization(guards = "stringEquals(J_GP_OBJECT, name, stringProfile)")
            static boolean isReadableCachedGP(PrimitiveNativeWrapper receiver, String name,
                            @Cached ConditionProfile stringProfile) {
                return true;
            }

            @Specialization
            @TruffleBoundary
            static boolean isReadableFallback(@SuppressWarnings("unused") PrimitiveNativeWrapper receiver, String name) {
                return J_GP_OBJECT.equals(name) || NativeMember.isValid(name);
            }

        }

        @ExportMessage
        abstract static class ReadMember {

            @Specialization(guards = {"key == cachedObBase", "isObBase(cachedObBase)"}, limit = "1")
            static Object doObBaseCached(PrimitiveNativeWrapper object, @SuppressWarnings("unused") String key,
                            @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedObBase) {
                return object;
            }

            @Specialization(guards = {"key == cachedObRefcnt", "isObRefcnt(cachedObRefcnt)"}, limit = "1")
            static Object doObRefcnt(PrimitiveNativeWrapper object, @SuppressWarnings("unused") String key,
                            @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedObRefcnt,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                try {
                    return object.getRefCount();
                } finally {
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = {"key == cachedObType", "isObType(cachedObType)"}, limit = "1")
            static Object doObType(PrimitiveNativeWrapper object, @SuppressWarnings("unused") String key,
                            @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedObType,
                            @Exclusive @Cached ToSulongNode toSulongNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                try {
                    Object clazz;
                    if (object.isBool()) {
                        clazz = PythonBuiltinClassType.Boolean;
                    } else if (object.isByte() || object.isInt() || object.isLong()) {
                        clazz = PythonBuiltinClassType.PInt;
                    } else if (object.isDouble()) {
                        clazz = PythonBuiltinClassType.PFloat;
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("should not reach");
                    }

                    return toSulongNode.execute(clazz);
                } finally {
                    gil.release(mustRelease);
                }
            }

            @Specialization
            static Object execute(PrimitiveNativeWrapper object, String key,
                            @Exclusive @Cached BranchProfile isNotObRefcntProfile,
                            @Exclusive @Cached ReadNativeMemberDispatchNode readNativeMemberNode,
                            @Exclusive @Cached AsPythonObjectNode getDelegate,
                            @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
                boolean mustRelease = gil.acquire();
                try {
                    // avoid materialization of primitive native wrappers if we only ask for the
                    // reference
                    // count
                    if (isObRefcnt(key)) {
                        return object.getRefCount();
                    }
                    isNotObRefcntProfile.enter();

                    Object delegate = getDelegate.execute(object);

                    // special key for the debugger
                    if (J_GP_OBJECT.equals(key)) {
                        return delegate;
                    }
                    return readNativeMemberNode.execute(delegate, object, key);
                } finally {
                    gil.release(mustRelease);
                }
            }

            protected static boolean isObBase(String key) {
                return OB_BASE.getMemberNameJavaString().equals(key);
            }

            protected static boolean isObRefcnt(String key) {
                return OB_REFCNT.getMemberNameJavaString().equals(key);
            }

            protected static boolean isObType(String key) {
                return OB_TYPE.getMemberNameJavaString().equals(key);
            }
        }

        @ExportMessage
        @TruffleBoundary
        int identityHashCode() {
            int val = Byte.hashCode(state) ^ Long.hashCode(value);
            if (Double.isNaN(dvalue)) {
                return val;
            } else {
                return val ^ Double.hashCode(dvalue);
            }
        }

        @ExportMessage
        TriState isIdenticalOrUndefined(Object obj) {
            if (obj instanceof PrimitiveNativeWrapper) {
                /*
                 * This basically emulates singletons for boxed values. However, we need to do so to
                 * preserve the invariant that storing an object into a list and getting it out (in
                 * the same critical region) returns the same object.
                 */
                PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
                if (other.state == state && other.value == value && (other.dvalue == dvalue || Double.isNaN(dvalue) && Double.isNaN(other.dvalue))) {
                    /*
                     * n.b.: in the equals, we also require the native pointer to be the same. The
                     * reason for this is to avoid native pointer sharing. Handles are shared if the
                     * objects are equal but in this case we must not share because otherwise we
                     * would mess up the reference counts.
                     */
                    return TriState.valueOf(GetNativePointer.getGenericPtr(this) == GetNativePointer.getGenericPtr(other));
                }
                return TriState.FALSE;
            } else {
                return TriState.UNDEFINED;
            }
        }
    }

    @ExportMessage
    protected boolean isPointer(
                    @Cached IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    protected long asPointer(
                    @Cached PAsPointerNode pAsPointerNode) {
        return pAsPointerNode.execute(this);
    }

    @ExportMessage
    protected void toNative(
                    @Cached ToNativeNode toNativeNode) {
        toNativeNode.execute(this);
    }

    @ExportMessage
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    protected Object getNativeType(
                    @Cached PGetDynamicTypeNode getDynamicTypeNode) {
        return getDynamicTypeNode.execute(this);
    }
}
