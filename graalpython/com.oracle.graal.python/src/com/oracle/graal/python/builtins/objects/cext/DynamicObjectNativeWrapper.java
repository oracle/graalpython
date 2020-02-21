/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.MD_DEF;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.OB_REFCNT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_ALLOC;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_DEALLOC;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_DICT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_DICTOFFSET;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_FLAGS;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_FREE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.TP_SUBCLASSES;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__WEAKLISTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.Set;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AllToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetSpecialSingletonPtrNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.SetSpecialSingletonPtrNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapperFactory.ReadTypeNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSuperClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CoerceToIntegerNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public abstract class DynamicObjectNativeWrapper extends PythonNativeWrapper {
    static final String GP_OBJECT = "gp_object";
    private static final Layout OBJECT_LAYOUT = Layout.newLayout().build();
    private static final Shape SHAPE = OBJECT_LAYOUT.createShape(new ObjectType());

    private DynamicObjectStorage nativeMemberStore;

    public DynamicObjectNativeWrapper() {
    }

    public DynamicObjectNativeWrapper(Object delegate) {
        super(delegate);
    }

    public DynamicObjectStorage createNativeMemberStore() {
        if (nativeMemberStore == null) {
            nativeMemberStore = new DynamicObjectStorage(SHAPE.newInstance());
        }
        return nativeMemberStore;
    }

    public DynamicObjectStorage getNativeMemberStore() {
        return nativeMemberStore;
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
        return new InteropArray(new String[]{DynamicObjectNativeWrapper.GP_OBJECT});
    }

    @ExportMessage(name = "readMember")
    abstract static class ReadNode {

        @Specialization(guards = {"key == cachedObBase", "isObBase(cachedObBase)"}, limit = "1")
        static Object doObBaseCached(DynamicObjectNativeWrapper object, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedObBase) {
            return object;
        }

        @Specialization
        static Object execute(DynamicObjectNativeWrapper object, String key,
                        @Exclusive @Cached ReadNativeMemberDispatchNode readNativeMemberNode,
                        @Exclusive @Cached CExtNodes.AsPythonObjectNode getDelegate) throws UnsupportedMessageException, UnknownIdentifierException {
            Object delegate = getDelegate.execute(object);

            // special key for the debugger
            if (key.equals(DynamicObjectNativeWrapper.GP_OBJECT)) {
                return delegate;
            }
            return readNativeMemberNode.execute(delegate, object, key);
        }

        protected static boolean isObBase(String key) {
            return NativeMember.OB_BASE.getMemberName().equals(key);
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

    @ImportStatic({NativeMember.class, SpecialMethodNames.class, SpecialAttributeNames.class, PythonOptions.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNativeMemberNode extends Node {

        abstract Object execute(Object receiver, PythonNativeWrapper nativeWrapper, String key) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization(guards = "eq(OB_BASE, key)")
        static Object doObBase(Object o, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(OB_REFCNT, key)")
        static long doObRefcnt(@SuppressWarnings("unused") Object o, PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return nativeWrapper.getRefCount();
        }

        @Specialization(guards = "eq(OB_TYPE, key)")
        static Object doObType(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetClassNode getClassNode) {
            return toSulongNode.execute(getClassNode.execute(object));
        }

        protected static boolean eq(NativeMember expected, String actual) {
            return expected.getMemberName().equals(actual);
        }

        protected static Object getSliceComponent(int sliceComponent) {
            if (sliceComponent == PSlice.MISSING_INDEX) {
                return PNone.NONE;
            }
            return sliceComponent;
        }
    }

    @GenerateUncached
    abstract static class ReadTypeNativeMemberNode extends ReadNativeMemberNode {

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
                        @Cached PInteropGetAttributeNode getAttrNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            Object docObj = getAttrNode.execute(object, SpecialAttributeNames.__DOC__);
            if (docObj instanceof String) {
                return new CStringWrapper((String) docObj);
            } else if (docObj instanceof PString) {
                return new CStringWrapper(((PString) docObj).getValue());
            }
            return getNativeNullNode.execute();
        }

        @Specialization(guards = "eq(TP_BASE, key)")
        static Object doTpBase(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GetSuperClassNode getSuperClassNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            LazyPythonClass superClass = getSuperClassNode.execute(object);
            if (superClass != null) {
                return toSulongNode.execute(ensureClassObject(context, superClass));
            }
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        private static PythonAbstractClass ensureClassObject(PythonContext context, LazyPythonClass klass) {
            if (klass instanceof PythonBuiltinClassType) {
                return context.getCore().lookupType((PythonBuiltinClassType) klass);
            }
            return (PythonAbstractClass) klass;
        }

        @Specialization(guards = "eq(TP_ALLOC, key)")
        static Object doTpAlloc(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object result = lookupNativeMemberNode.execute(object, TP_ALLOC, TypeBuiltins.TYPE_ALLOC);
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_DEALLOC, key)")
        static Object doTpDealloc(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object result = lookupNativeMemberNode.execute(object, TP_DEALLOC, TypeBuiltins.TYPE_DEALLOC);
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_FREE, key)")
        static Object doTpFree(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
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
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached BranchProfile notBytes,
                        @Cached BranchProfile notBytearray,
                        @Cached BranchProfile notMemoryview,
                        @Cached BranchProfile notBuffer,
                        @Cached BranchProfile notMmap,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("nullToSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            PythonBuiltinClass pBytes = context.getCore().lookupType(PythonBuiltinClassType.PBytes);
            if (isSubtype.execute(object, pBytes)) {
                return new PyBufferProcsWrapper(pBytes);
            }
            notBytes.enter();
            PythonBuiltinClass pBytearray = context.getCore().lookupType(PythonBuiltinClassType.PByteArray);
            if (isSubtype.execute(object, pBytearray)) {
                return new PyBufferProcsWrapper(pBytearray);
            }
            notBytearray.enter();
            PythonBuiltinClass pMemoryview = context.getCore().lookupType(PythonBuiltinClassType.PMemoryView);
            if (isSubtype.execute(object, pMemoryview)) {
                return new PyBufferProcsWrapper(pMemoryview);
            }
            notMemoryview.enter();
            PythonBuiltinClass pBuffer = context.getCore().lookupType(PythonBuiltinClassType.PBuffer);
            if (isSubtype.execute(object, pBuffer)) {
                return new PyBufferProcsWrapper(pBuffer);
            }
            notBuffer.enter();
            PythonBuiltinClass pMmap = context.getCore().lookupType(PythonBuiltinClassType.PMMap);
            if (isSubtype.execute(object, pMmap)) {
                return new PyBufferProcsWrapper(pMmap);
            }
            notMmap.enter();
            // NULL pointer
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        @Specialization(guards = "eq(TP_AS_SEQUENCE, key)")
        static Object doTpAsSequence(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("nullToSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            if (getAttrNode.execute(object, __LEN__) != PNone.NO_VALUE) {
                return new PySequenceMethodsWrapper(object);
            } else {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "eq(TP_AS_MAPPING, key)", limit = "1")
        static Object doTpAsMapping(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary("object") PythonObjectLibrary pythonTypeLibrary,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("nullToSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            if (pythonTypeLibrary.isSequenceType(object)) {
                return new PyMappingMethodsWrapper(object);
            } else {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "eq(TP_NEW, key)")
        static Object doTpNew(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Cached PCallCapiFunction callGetNewfuncTypeidNode) {
            return ManagedMethodWrappers.createKeywords(getAttrNode.execute(object, __NEW__), callGetNewfuncTypeidNode.call(NativeCAPISymbols.FUN_GET_NEWFUNC_TYPE_ID));
        }

        @Specialization(guards = "eq(TP_HASH, key)")
        static Object doTpHash(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getHashNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getHashNode.execute(object, __HASH__));
        }

        @Specialization(guards = "eq(TP_BASICSIZE, key)")
        static long doTpBasicsize(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, __BASICSIZE__);
            return val != PNone.NO_VALUE ? lib.asSize(val) : 0L;
        }

        @Specialization(guards = "eq(TP_ITEMSIZE, key)")
        static long doTpItemsize(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, __ITEMSIZE__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return 0L;
            }
            return lib.asSize(val);
        }

        @Specialization(guards = "eq(TP_DICTOFFSET, key)")
        static long doTpDictoffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (object instanceof PythonBuiltinClass) {
                return 0L;
            }
            Object dictoffset = getAttrNode.execute(object, __DICTOFFSET__);
            return dictoffset != PNone.NO_VALUE ? lib.asSize(dictoffset) : 0L;
        }

        @Specialization(guards = "eq(TP_WEAKLISTOFFSET, key)")
        static Object doTpWeaklistoffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("nullToSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object val = getAttrNode.execute(object, __WEAKLISTOFFSET__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return toSulongNode.execute(getNativeNullNode.execute());
            }
            return val;
        }

        @Specialization(guards = "eq(TP_RICHCOMPARE, key)")
        static Object doTpRichcompare(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getCmpNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getCmpNode.execute(object, RICHCMP));
        }

        @Specialization(guards = "eq(TP_SUBCLASSES, key)")
        static Object doTpSubclasses(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper,
                        @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            // TODO create dict view on subclasses set
            return PythonObjectNativeWrapper.wrap(factory.createDict(), noWrapperProfile);
        }

        @Specialization(guards = "eq(TP_GETATTR, key)")
        static Object doTpGetattr(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("nullToSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            // we do not provide 'tp_getattr'; code will usually then use 'tp_getattro'
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        @Specialization(guards = "eq(TP_SETATTR, key)")
        static Object doTpSetattr(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("nullToSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            // we do not provide 'tp_setattr'; code will usually then use 'tp_setattro'
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        @Specialization(guards = "eq(TP_GETATTRO, key)")
        static Object doTpGetattro(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createGetAttrWrapper(lookupAttrNode.execute(object, __GETATTRIBUTE__));
        }

        @Specialization(guards = "eq(TP_SETATTRO, key)")
        static Object doTpSetattro(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createSetAttrWrapper(lookupAttrNode.execute(object, __SETATTR__));
        }

        @Specialization(guards = "eq(TP_ITERNEXT, key)")
        static Object doTpIternext(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(lookupAttrNode.execute(object, __NEXT__));
        }

        @Specialization(guards = "eq(TP_STR, key)")
        static Object doTpStr(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(lookupAttrNode.execute(object, __STR__));
        }

        @Specialization(guards = "eq(TP_REPR, key)")
        static Object doTpRepr(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(lookupAttrNode.execute(object, __REPR__));
        }

        @Specialization(guards = "eq(TP_DICT, key)", limit = "1")
        static Object doTpDict(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") HashingStorageLibrary storageLib,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) throws UnsupportedMessageException {
            // TODO(fa): we could cache the dict instance on the class' native wrapper
            PHashingCollection dict = lib.getDict(object);
            HashingStorage dictStorage = dict != null ? dict.getDictStorage() : null;
            if (dictStorage instanceof DynamicObjectStorage) {
                // reuse the existing and modifiable storage
                return toSulongNode.execute(factory.createDict(dict.getDictStorage()));
            }
            HashingStorage storage = new DynamicObjectStorage(object.getStorage());
            if (dictStorage != null) {
                // copy all mappings to the new storage
                storage = storageLib.addAllToOther(dictStorage, storage);
            }
            lib.setDict(object, factory.createMappingproxy(storage));
            return toSulongNode.execute(factory.createDict(storage));
        }

        @Specialization(guards = "eq(TP_TRAVERSE, key) || eq(TP_CLEAR, key)")
        static Object doTpTraverse(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached IsBuiltinClassProfile isTupleProfile,
                        @Cached IsBuiltinClassProfile isDictProfile,
                        @Cached IsBuiltinClassProfile isListProfile,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached ReadAttributeFromObjectNode readAttrNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            if (isTupleProfile.profileClass(object, PythonBuiltinClassType.PTuple) || isDictProfile.profileClass(object, PythonBuiltinClassType.PDict) ||
                            isListProfile.profileClass(object, PythonBuiltinClassType.PList)) {
                // We do not actually return _the_ traverse or clear function since we will never
                // need
                // it. It is just important to return a function.
                PythonModule pythonCextModule = context.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT);
                Object sequenceClearMethod = readAttrNode.execute(pythonCextModule, "sequence_clear");
                return toSulongNode.execute(sequenceClearMethod);
            }
            return getNativeNullNode.execute();
        }

        public static ReadTypeNativeMemberNode create() {
            return ReadTypeNativeMemberNodeGen.create();
        }
    }

    @GenerateUncached
    abstract static class ReadObjectNativeMemberNode extends ReadNativeMemberNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(ReadObjectNativeMemberNode.class);

        @Specialization(guards = "eq(D_COMMON, key)")
        static Object doDCommon(Object o, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(_BASE, key)")
        static Object doObBase(PString o, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(OB_SIZE, key)")
        static long doObSize(Object object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached ObSizeNode obSizeNode) {
            return obSizeNode.execute(object);
        }

        @Specialization(guards = "eq(MA_USED, key)", limit = "getCallSiteInlineCacheMaxDepth()")
        static int doMaUsed(PDict object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            try {
                return lib.length(object);
            } catch (PException e) {
                return -1;
            }
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

        @Specialization(guards = "eq(UNICODE_WSTR, key)")
        static Object doWstr(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asWideCharNode") @Cached(value = "createNativeOrder()", uncached = "getUncachedNativeOrder()") UnicodeAsWideCharNode asWideCharNode,
                        @Shared("sizeofWcharNode") @Cached CExtNodes.SizeofWCharNode sizeofWcharNode,
                        @Shared("strLen") @Cached StringLenNode stringLenNode) {
            int elementSize = (int) sizeofWcharNode.execute();
            return new PySequenceArrayWrapper(asWideCharNode.execute(object, elementSize, stringLenNode.execute(object)), elementSize);
        }

        @Specialization(guards = "eq(UNICODE_WSTR_LENGTH, key)")
        static long doWstrLength(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("asWideCharNode") @Cached(value = "createNativeOrder()", uncached = "getUncachedNativeOrder()") UnicodeAsWideCharNode asWideCharNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Shared("sizeofWcharNode") @Cached CExtNodes.SizeofWCharNode sizeofWcharNode,
                        @Shared("strLen") @Cached StringLenNode stringLenNode) {
            long sizeofWchar = sizeofWcharNode.execute();
            PBytes result = asWideCharNode.execute(object, sizeofWchar, stringLenNode.execute(object));
            return lenNode.execute(result.getSequenceStorage()) / sizeofWchar;
        }

        @Specialization(guards = "eq(UNICODE_LENGTH, key)")
        static long doUnicodeLength(PString object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("strLen") @Cached StringLenNode stringLenNode) {
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
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getDictNode.execute(object, SpecialAttributeNames.__DICT__));
        }

        @Specialization(guards = "eq(TP_DICT, key)", limit = "1")
        static Object doTpDict(PythonClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) throws UnsupportedMessageException {
            PHashingCollection dict = lib.getDict(object);
            if (!(dict instanceof PDict)) {
                assert dict instanceof PMappingproxy || dict == null;
                // If 'dict instanceof PMappingproxy', it seems that someone already used '__dict__'
                // on this type and created a mappingproxy object. We need to replace it by a dict.
                dict = factory.createDictFixedStorage(object);
                lib.setDict(object, dict);
            }
            assert dict != null;
            return toSulongNode.execute(dict);
        }

        @Specialization(guards = "eq(MD_DEF, key)", limit = "1")
        static Object doMdDef(@SuppressWarnings("unused") PythonObject object, DynamicObjectNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @CachedLibrary("nativeWrapper.getNativeMemberStore()") HashingStorageLibrary lib) {
            return lib.getItem(nativeWrapper.getNativeMemberStore(), MD_DEF);
        }

        @Specialization(guards = "eq(BUF_DELEGATE, key)")
        static Object doBufDelegate(PBuffer object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object.getDelegate(), 1);
        }

        @Specialization(guards = "eq(BUF_READONLY, key)")
        static int doBufReadonly(PBuffer object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.isReadOnly() ? 1 : 0;
        }

        @Specialization(guards = "eq(START, key)")
        static Object doStart(PSlice object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getSliceComponent(object.getStart()));
        }

        @Specialization(guards = "eq(STOP, key)")
        static Object doStop(PSlice object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getSliceComponent(object.getStop()));
        }

        @Specialization(guards = "eq(STEP, key)")
        static Object doStep(PSlice object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getSliceComponent(object.getStep()));
        }

        @Specialization(guards = "eq(IM_SELF, key)")
        static Object doImSelf(PMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(IM_SELF, key)")
        static Object doImSelf(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(IM_FUNC, key)")
        static Object doImFunc(PMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFunction());
        }

        @Specialization(guards = "eq(IM_FUNC, key)")
        static Object doImFunc(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
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

        @Specialization(guards = "eq(D_METHOD, key)")
        static Object doDBase(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        static Object doDBase(PBuiltinFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        static Object doDBase(PFunction object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        static Object doDBase(PBuiltinMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        static Object doDBase(PMethod object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(D_QUALNAME, key)")
        static Object doDQualname(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getAttributeNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getAttributeNode.execute(object, SpecialAttributeNames.__QUALNAME__));
        }

        @Specialization(guards = "eq(SET_USED, key)", limit = "1")
        static long doSetUsed(PSet object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStorageNode,
                        @CachedLibrary("getStorageNode.execute(object)") HashingStorageLibrary lib) {
            return lib.length(getStorageNode.execute(object));
        }

        @Specialization
        static Object doMemoryview(PMemoryView object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, String key,
                        @Cached PRaiseNode raise,
                        @Cached ReadAttributeFromObjectNode readAttrNode,
                        @CachedLibrary(limit = "1") InteropLibrary read,
                        @Cached("createBinaryProfile()") ConditionProfile isNativeObject) {
            Object delegateObj = readAttrNode.execute(object, "__c_memoryview");
            if (isNativeObject.profile(PythonNativeObject.isInstance(delegateObj))) {
                try {
                    return read.readMember(PythonNativeObject.cast(delegateObj).getPtr(), key);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    throw raise.raise(PythonBuiltinClassType.TypeError, e);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("delegate of memoryview object is not native");
        }

        @Specialization(guards = "eq(MMAP_DATA, key)")
        static Object doMmapData(PMMap object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object, 1);
        }

        protected static boolean isPyDateTimeCAPI(PythonObject object, GetClassNode getClassNode, GetNameNode getNameNode) {
            return isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(object)));
        }

        protected static boolean isPyDateTime(PythonObject object, GetClassNode getClassNode, GetNameNode getNameNode) {
            return "datetime".equals(getNameNode.execute(getClassNode.execute(object)));
        }

        protected static boolean isPyDateTimeCAPIType(String className) {
            return "PyDateTime_CAPI".equals(className);

        }

        @Specialization(guards = "isPyDateTimeCAPI(object, getClassNode, getNameNode)", limit = "1")
        static Object doDatetimeCAPI(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNameNode") @Cached @SuppressWarnings("unused") GetNameNode getNameNode,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            return toSulongNode.execute(getAttrNode.execute(getClassNode.execute(object), key));
        }

        @Specialization(guards = "isPyDateTime(object, getClassNode, getNameNode)", limit = "1")
        static Object doDatetimeData(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Shared("getNameNode") @Cached @SuppressWarnings("unused") GetNameNode getNameNode,
                        @Shared("getClassNode") @Cached @SuppressWarnings("unused") GetClassNode getClassNode,
                        @Cached PyDateTimeMRNode pyDateTimeMRNode) {
            return pyDateTimeMRNode.execute(object, key);
        }

        @Specialization(guards = "eq(F_LINENO, key)")
        static int doFLineno(PFrame object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key) {
            return object.getLine();
        }

        @Specialization(guards = "eq(F_CODE, key)")
        static Object doFCode(PFrame object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            RootCallTarget ct = object.getTarget();
            if (ct != null) {
                return toSulongNode.execute(factory.createCode(ct));
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("should not be reached");
        }

        // TODO fallback guard
        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object object, DynamicObjectNativeWrapper nativeWrapper, String key,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) throws UnknownIdentifierException {
            // This is the preliminary generic case: There are native members we know that they
            // exist but we do currently not represent them. So, store them into a dynamic object
            // such that native code at least reads the value that was written before.
            if (nativeWrapper.isMemberReadable(key)) {
                logGeneric(key);
                return lib.getItem(nativeWrapper.getNativeMemberStore(), key);
            }
            throw UnknownIdentifierException.create(key);
        }

        @TruffleBoundary(allowInlining = true)
        private static void logGeneric(String key) {
            LOGGER.log(Level.FINE, "read of Python struct native member " + key);
        }
    }

    // WRITE
    @GenerateUncached
    @ImportStatic({NativeMember.class, PGuards.class, SpecialMethodNames.class, SpecialAttributeNames.class})
    abstract static class WriteNativeMemberNode extends Node {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(WriteNativeMemberNode.class);

        abstract Object execute(Object receiver, PythonNativeWrapper nativeWrapper, String key, Object value)
                        throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;

        @Specialization(guards = "eq(OB_TYPE, key)")
        static Object doObType(PythonObject object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key,
                        @SuppressWarnings("unused") PythonManagedClass value,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            // At this point, we do not support changing the type of an object.
            return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
        }

        @Specialization(guards = "eq(OB_REFCNT, key)")
        static long doObRefcnt(@SuppressWarnings("unused") Object o, PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long value) {
            nativeWrapper.setRefCount(value);
            return value;
        }

        @Specialization(guards = "eq(TP_FLAGS, key)")
        static long doTpFlags(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long flags) {
            object.setFlags(flags);
            return flags;
        }

        @Specialization(guards = "eq(TP_BASICSIZE, key)")
        static long doTpBasicsize(PythonAbstractClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, long basicsize,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached IsBuiltinClassProfile profile) {
            if (profile.profileClass(object, PythonBuiltinClassType.PythonClass)) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_BASICSIZE, basicsize);
            } else {
                writeAttrNode.execute(object, SpecialAttributeNames.__BASICSIZE__, basicsize);
            }
            return basicsize;
        }

        @Specialization(guards = "eq(TP_ALLOC, key)")
        static Object doTpAlloc(PythonAbstractClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object allocFunc,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_ALLOC, asPythonObjectNode.execute(allocFunc));
            return allocFunc;
        }

        @ValueType
        private static final class SubclassAddState {
            private final HashingStorage storage;
            private final HashingStorageLibrary lib;
            private final Set<PythonAbstractClass> subclasses;

            private SubclassAddState(HashingStorage storage, HashingStorageLibrary lib, Set<PythonAbstractClass> subclasses) {
                this.storage = storage;
                this.lib = lib;
                this.subclasses = subclasses;
            }
        }

        @Specialization(guards = "eq(TP_DEALLOC, key)")
        static Object doTpDelloc(PythonAbstractClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object deallocFunc,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_DEALLOC, asPythonObjectNode.execute(deallocFunc));
            return deallocFunc;
        }

        @Specialization(guards = "eq(TP_FREE, key)")
        static Object doTpFree(PythonAbstractClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object freeFunc,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_FREE, asPythonObjectNode.execute(freeFunc));
            return freeFunc;
        }

        @GenerateUncached
        static final class EachSubclassAdd extends HashingStorageLibrary.ForEachNode<SubclassAddState> {

            private static final EachSubclassAdd UNCACHED = new EachSubclassAdd();

            @Override
            public SubclassAddState execute(Object key, SubclassAddState s) {
                setAdd(s.subclasses, (PythonClass) s.lib.getItem(s.storage, key));
                return s;
            }

            @TruffleBoundary
            protected static void setAdd(Set<PythonAbstractClass> set, PythonClass cls) {
                set.add(cls);
            }

            static EachSubclassAdd create() {
                return new EachSubclassAdd();
            }

            static EachSubclassAdd getUncached() {
                return UNCACHED;
            }
        }

        @Specialization(guards = "eq(TP_SUBCLASSES, key)", limit = "1")
        static Object doTpSubclasses(PythonClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, PythonObjectNativeWrapper value,
                        @Cached GetSubclassesNode getSubclassesNode,
                        @Cached EachSubclassAdd eachNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStorage,
                        @CachedLibrary("value") PythonNativeWrapperLibrary lib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hashLib) {
            PDict dict = (PDict) lib.getDelegate(value);
            HashingStorage storage = getStorage.execute(dict);
            Set<PythonAbstractClass> subclasses = getSubclassesNode.execute(object);
            hashLib.forEach(storage, eachNode, new SubclassAddState(storage, hashLib, subclasses));
            return value;
        }

        @Specialization(guards = "eq(MD_DEF, key)", limit = "1")
        static Object doMdDef(@SuppressWarnings("unused") PythonObject object, DynamicObjectNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value,
                        @CachedLibrary("nativeWrapper.createNativeMemberStore()") HashingStorageLibrary lib) {
            lib.setItem(nativeWrapper.createNativeMemberStore(), MD_DEF.getMemberName(), value);
            return value;
        }

        @Specialization(guards = "eq(TP_DICT, key)", limit = "1")
        static Object doTpDict(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object nativeValue,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached IsBuiltinClassProfile isPrimitiveDictProfile) throws UnsupportedMessageException {
            Object value = asPythonObjectNode.execute(nativeValue);
            if (value instanceof PDict && isPrimitiveDictProfile.profileObject((PDict) value, PythonBuiltinClassType.PDict)) {
                // special and fast case: commit items and change store
                PDict d = (PDict) value;
                for (HashingStorage.DictEntry entry : d.entries()) {
                    writeAttrNode.execute(object, entry.getKey(), entry.getValue());
                }
                PHashingCollection existing = lib.getDict(object);
                if (existing != null) {
                    d.setDictStorage(existing.getDictStorage());
                } else {
                    d.setDictStorage(new DynamicObjectStorage(object.getStorage()));
                }
                lib.setDict(object, d);
            } else {
                // TODO custom mapping object
            }
            return value;
        }

        @Specialization(guards = "eq(TP_DICTOFFSET, key)")
        static Object doTpDictoffset(PythonManagedClass object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, @SuppressWarnings("unused") String key, Object value,
                        @Cached CoerceToIntegerNode.Dynamic castToIntNode,
                        @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode) throws UnsupportedMessageException, UnknownIdentifierException {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (object instanceof PythonBuiltinClass) {
                return 0L;
            }
            setAttrNode.execute(object, __DICTOFFSET__, castToIntNode.execute(value));
            return value;
        }

        @Specialization
        static Object doMemoryview(PMemoryView object, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper, String key, Object value,
                        @Cached ReadAttributeFromObjectNode readAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile isNativeObject,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            Object delegateObj = readAttrNode.execute(object, "__c_memoryview");
            if (isNativeObject.profile(PythonNativeObject.isInstance(delegateObj))) {
                interopLib.writeMember(PythonNativeObject.cast(delegateObj).getPtr(), key, value);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("delegate of memoryview object is not native");
        }

        @Specialization(guards = "isGenericCase(object, key)", limit = "1")
        static Object doGeneric(@SuppressWarnings("unused") Object object, DynamicObjectNativeWrapper nativeWrapper, String key, Object value,
                        @CachedLibrary("nativeWrapper.createNativeMemberStore()") HashingStorageLibrary lib) throws UnknownIdentifierException {
            // This is the preliminary generic case: There are native members we know that they
            // exist but we do currently not represent them. So, store them into a dynamic object
            // such that native code at least reads the value that was written before.
            if (nativeWrapper.isMemberModifiable(key)) {
                logGeneric(key);
                lib.setItem(nativeWrapper.createNativeMemberStore(), key, value);
                return value;
            }
            throw UnknownIdentifierException.create(key);
        }

        @TruffleBoundary(allowInlining = true)
        private static void logGeneric(String key) {
            LOGGER.log(Level.FINE, "write of Python struct native member " + key);
        }

        protected static boolean eq(NativeMember expected, String actual) {
            return expected.getMemberName().equals(actual);
        }

        protected static boolean isGenericCase(Object object, String key) {
            if (object instanceof PMemoryView) {
                return false;
            }
            return !(OB_TYPE.getMemberName().equals(key) ||
                            OB_REFCNT.getMemberName().equals(key) || TP_FLAGS.getMemberName().equals(key) || TP_BASICSIZE.getMemberName().equals(key) || TP_ALLOC.getMemberName().equals(key) ||
                            TP_DEALLOC.getMemberName().equals(key) || TP_FREE.getMemberName().equals(key) || TP_SUBCLASSES.getMemberName().equals(key) || MD_DEF.getMemberName().equals(key) ||
                            TP_DICT.getMemberName().equals(key));
        }

    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return OB_TYPE.getMemberName().equals(member) ||
                        OB_REFCNT.getMemberName().equals(member) ||
                        TP_FLAGS.getMemberName().equals(member) ||
                        TP_BASICSIZE.getMemberName().equals(member) ||
                        TP_ALLOC.getMemberName().equals(member) ||
                        TP_DEALLOC.getMemberName().equals(member) ||
                        TP_FREE.getMemberName().equals(member) ||
                        TP_SUBCLASSES.getMemberName().equals(member) ||
                        MD_DEF.getMemberName().equals(member) ||
                        TP_DICT.getMemberName().equals(member) ||
                        TP_DICTOFFSET.getMemberName().equals(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached WriteNativeMemberNode writeNativeMemberNode) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        writeNativeMemberNode.execute(lib.getDelegate(this), this, member, value);
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
                    @Cached CExtNodes.ToNewRefNode toNewRefNode,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                    @Cached GetNativeNullNode getNativeNullNode) throws UnsupportedMessageException {

        Object[] converted = allToJavaNode.execute(arguments);
        try {
            Object result = executeNode.execute(lib.getDelegate(this), converted);

            // If a native wrapper is executed, we directly wrap some managed function and assume
            // that new references are returned. So, we increase the ref count for each native
            // object here.
            return toNewRefNode.execute(result);
        } catch (PException e) {
            transformExceptionToNativeNode.execute(e);
            return toNewRefNode.execute(getNativeNullNode.execute());
        }
    }

    // TO NATIVE, IS POINTER, AS POINTER
    @GenerateUncached
    abstract static class ToNativeNode extends Node {
        public abstract void execute(PythonNativeWrapper obj);

        protected static boolean isClassInitNativeWrapper(PythonNativeWrapper obj) {
            return obj instanceof PythonClassInitNativeWrapper;
        }

        @Specialization(limit = "1")
        public void executeClsInit(PythonClassInitNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Shared("toPyObjectNode") @Cached ToPyObjectNode toPyObjectNode,
                        @Shared("invalidateNode") @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!lib.isNative(obj)) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
        }

        @Specialization(guards = "!isClassInitNativeWrapper(obj)", limit = "1")
        public void execute(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Shared("toPyObjectNode") @Cached ToPyObjectNode toPyObjectNode,
                        @Cached SetSpecialSingletonPtrNode setSpecialSingletonPtrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Shared("invalidateNode") @Cached InvalidateNativeObjectsAllManagedNode invalidateNode,
                        @Cached IsPointerNode isPointerNode) {
            invalidateNode.execute();
            if (!isPointerNode.execute(obj)) {
                Object ptr = toPyObjectNode.execute(obj);
                Object delegate = lib.getDelegate(obj);
                if (profile.profile(PythonLanguage.getSingletonNativePtrIdx(delegate) != -1)) {
                    setSpecialSingletonPtrNode.execute(delegate, ptr);
                } else {
                    obj.setNativePointer(ptr);
                }
            }
        }
    }

    @GenerateUncached
    abstract static class PAsPointerNode extends Node {

        public abstract long execute(PythonNativeWrapper o);

        @Specialization(guards = {"obj.isBool()", "!lib.isNative(obj)"}, limit = "1")
        long doBoolNotNative(PrimitiveNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Cached CExtNodes.MaterializeDelegateNode materializeNode,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            // special case for True and False singletons
            PInt boxed = (PInt) materializeNode.execute(obj);
            assert lib.getNativePointer(obj) == lib.getNativePointer(boxed.getNativeWrapper());
            return ensureLong(interopLib, lib.getNativePointer(obj));
        }

        @Specialization(guards = {"obj.isBool()", "lib.isNative(obj)"}, limit = "1")
        long doBoolNative(PrimitiveNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            return ensureLong(interopLib, lib.getNativePointer(obj));
        }

        @Specialization(guards = "!isBoolNativeWrapper(obj)", limit = "1")
        long doFast(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached GetSpecialSingletonPtrNode getSpecialSingletonPtrNode) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = lib.getNativePointer(obj);
            if (profile.profile(nativePointer == null)) {
                // We assume that before someone calls 'asPointer' on the wrapper, 'isPointer' was
                // checked and returned true. So, for this case we assume that it is one of the
                // special singletons where we store the pointer in the context.
                nativePointer = getSpecialSingletonPtrNode.execute(lib.getDelegate(obj));
                assert nativePointer != null : createAssertionMessage(lib.getDelegate(obj));
            }
            return ensureLong(interopLib, nativePointer);
        }

        private static long ensureLong(InteropLibrary interopLib, Object nativePointer) {
            if (nativePointer instanceof Long) {
                return (long) nativePointer;
            } else {
                try {
                    return interopLib.asPointer(nativePointer);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.SystemError, "invalid pointer object: %s", nativePointer);
                }
            }
        }

        protected static boolean isBoolNativeWrapper(Object obj) {
            return obj instanceof PrimitiveNativeWrapper && ((PrimitiveNativeWrapper) obj).isBool();
        }

        private static String createAssertionMessage(Object delegate) {
            CompilerAsserts.neverPartOfCompilation();
            int singletonNativePtrIdx = PythonLanguage.getSingletonNativePtrIdx(delegate);
            if (singletonNativePtrIdx == -1) {
                return "invalid special singleton object " + delegate;
            }
            return "expected special singleton '" + delegate + "' to have a native pointer";
        }

    }

    @GenerateUncached
    abstract static class ToPyObjectNode extends Node {
        public abstract Object execute(PythonNativeWrapper wrapper);

        @Specialization
        static Object doObject(PythonNativeWrapper wrapper,
                        @Cached PCallCapiFunction callNativeUnary) {
            return callNativeUnary.call(FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT, wrapper);
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
            return String.format("PythonObjectNativeWrapper(%s, isNative=%s)", lib.getDelegate(this), lib.isNative(this));
        }

        @ExportMessage
        @ImportStatic({PGuards.class, NativeMember.class, DynamicObjectNativeWrapper.class})
        abstract static class IsMemberReadable {

            @SuppressWarnings("unused")
            @Specialization(guards = {"stringEquals(cachedName, name, stringProfile)", "isValid(cachedName)"})
            static boolean isReadableNativeMembers(PythonObjectNativeWrapper receiver, String name,
                            @Cached("createBinaryProfile()") ConditionProfile stringProfile,
                            @Cached(value = "name", allowUncached = true) String cachedName) {
                return true;
            }

            @SuppressWarnings("unused")
            @Specialization(guards = "stringEquals(GP_OBJECT, name, stringProfile)")
            static boolean isReadableCachedGP(PythonObjectNativeWrapper receiver, String name,
                            @Cached("createBinaryProfile()") ConditionProfile stringProfile) {
                return true;
            }

            static boolean isPyTimeMemberReadable(PythonObjectNativeWrapper receiver, PythonNativeWrapperLibrary lib, GetLazyClassNode getClassNode, GetNameNode getNameNode) {
                return ReadObjectNativeMemberNode.isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(lib.getDelegate(receiver))));
            }

            @SuppressWarnings("unused")
            @Specialization(guards = "isPyTimeMemberReadable(receiver, lib, getClassNode, getNameNode)")
            static boolean isReadablePyTime(PythonObjectNativeWrapper receiver, String name,
                            @CachedLibrary("receiver") PythonNativeWrapperLibrary lib,
                            @Cached GetLazyClassNode getClassNode,
                            @Cached GetNameNode getNameNode) {
                return true;
            }

            @Specialization
            @TruffleBoundary
            static boolean isReadableFallback(PythonObjectNativeWrapper receiver, String name,
                            @CachedLibrary("receiver") PythonNativeWrapperLibrary lib,
                            @Cached GetLazyClassNode getClassNode,
                            @Cached GetNameNode getNameNode) {
                return DynamicObjectNativeWrapper.GP_OBJECT.equals(name) || NativeMember.isValid(name) ||
                                ReadObjectNativeMemberNode.isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(lib.getDelegate(receiver))));
            }
        }

        @ExportMessage
        @ImportStatic({PGuards.class, NativeMember.class, DynamicObjectNativeWrapper.class})
        abstract static class IsMemberModifiable {

            @SuppressWarnings("unused")
            @Specialization(guards = "stringEquals(cachedName, name, stringProfile)")
            static boolean isModifiableCached(PythonObjectNativeWrapper receiver, String name,
                            @Cached("createBinaryProfile()") ConditionProfile stringProfile,
                            @Cached(value = "name", allowUncached = true) String cachedName,
                            @Cached(value = "isValid(name)", allowUncached = true) boolean isValid) {
                return isValid;
            }
        }
    }

    @ExportLibrary(ReferenceLibrary.class)
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
            return (int) (value ^ Double.doubleToRawLongBits(dvalue) ^ state);
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
        @ImportStatic({PGuards.class, NativeMember.class, DynamicObjectNativeWrapper.class})
        abstract static class IsMemberReadable {

            @SuppressWarnings("unused")
            @Specialization(guards = {"stringEquals(cachedName, name, stringProfile)", "isValid(cachedName)"})
            static boolean isReadableNativeMembers(PrimitiveNativeWrapper receiver, String name,
                            @Cached("createBinaryProfile()") ConditionProfile stringProfile,
                            @Cached(value = "name", allowUncached = true) String cachedName) {
                return true;
            }

            @SuppressWarnings("unused")
            @Specialization(guards = "stringEquals(GP_OBJECT, name, stringProfile)")
            static boolean isReadableCachedGP(PrimitiveNativeWrapper receiver, String name,
                            @Cached("createBinaryProfile()") ConditionProfile stringProfile) {
                return true;
            }

            @Specialization
            @TruffleBoundary
            static boolean isReadableFallback(@SuppressWarnings("unused") PrimitiveNativeWrapper receiver, String name) {
                return DynamicObjectNativeWrapper.GP_OBJECT.equals(name) || NativeMember.isValid(name);
            }

        }

        @ExportMessage
        static class IsSame {

            @Specialization
            static boolean doPrimitiveWrapper(PrimitiveNativeWrapper receiver, PrimitiveNativeWrapper other) {
                // This basically emulates singletons for boxed values. However, we need to do so to
                // preserve the invariant that storing an object into a list and getting it out (in
                // the same critical region) returns the same object.
                return other.state == receiver.state && other.value == receiver.value && (other.dvalue == receiver.dvalue || Double.isNaN(receiver.dvalue) && Double.isNaN(other.dvalue));
            }

            @Fallback
            @SuppressWarnings("unused")
            static boolean doGeneric(PrimitiveNativeWrapper receiver, Object other) {
                return false;
            }
        }
    }

    @ExportMessage
    protected boolean isPointer(
                    @Cached CExtNodes.IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    protected long asPointer(
                    @Cached.Exclusive @Cached PAsPointerNode pAsPointerNode) {
        return pAsPointerNode.execute(this);
    }

    @ExportMessage
    protected void toNative(
                    @Cached.Exclusive @Cached ToNativeNode toNativeNode) {
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

    /**
     * Depending on the object's type, the size may need to be computed in very different ways. E.g.
     * any PyVarObject usually returns the number of contained elements.
     */
    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    abstract static class ObSizeNode extends Node {

        public abstract long execute(Object object);

        @Specialization
        static long doInteger(@SuppressWarnings("unused") int object) {
            return 1;
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") long object) {
            return 2;
        }

        @Specialization
        static long doPInt(PInt object) {
            return object.bitCount() / 32;
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static long doOther(Object object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            try {
                return lib.length(object);
            } catch (PException e) {
                return -1;
            }
        }

        static boolean isFallback(Object object) {
            return !(object instanceof PInt);
        }
    }

}
