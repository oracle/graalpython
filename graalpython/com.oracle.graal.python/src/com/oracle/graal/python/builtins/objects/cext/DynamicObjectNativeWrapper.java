/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.MD_DEF;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.TP_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.TP_DICT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.TP_DICTOFFSET;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.TP_FLAGS;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.TP_SUBCLASSES;
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

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetSpecialSingletonPtrNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.SetSpecialSingletonPtrNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapperFactory.ReadTypeNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectHybridDictStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.Equivalence;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.PythonEquivalence;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
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
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode.IsSubtypeWithoutFrameNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.string.StringLenNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
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

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public abstract class DynamicObjectNativeWrapper extends PythonNativeWrapper {
    static final String GP_OBJECT = "gp_object";
    private static final Layout OBJECT_LAYOUT = Layout.newLayout().build();
    private static final Shape SHAPE = OBJECT_LAYOUT.createShape(new ObjectType());

    private PythonObjectDictStorage nativeMemberStore;

    public DynamicObjectNativeWrapper() {
    }

    public DynamicObjectNativeWrapper(Object delegate) {
        super(delegate);
    }

    public PythonObjectDictStorage createNativeMemberStore() {
        if (nativeMemberStore == null) {
            nativeMemberStore = new PythonObjectDictStorage(SHAPE.newInstance());
        }
        return nativeMemberStore;
    }

    public PythonObjectDictStorage getNativeMemberStore() {
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
            return readNativeMemberNode.execute(delegate, key);
        }

        protected static boolean isObBase(String key) {
            return NativeMemberNames.OB_BASE.equals(key);
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class ReadNativeMemberDispatchNode extends Node {

        abstract Object execute(Object receiver, String key) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization
        Object doClass(PythonManagedClass clazz, String key,
                        @Cached ReadTypeNativeMemberNode readTypeMemberNode) throws UnsupportedMessageException, UnknownIdentifierException {
            return readTypeMemberNode.execute(clazz, key);
        }

        @Specialization(guards = "!isManagedClass(clazz)")
        Object doObject(Object clazz, String key,
                        @Cached ReadObjectNativeMemberNode readObjectMemberNode) throws UnsupportedMessageException, UnknownIdentifierException {
            return readObjectMemberNode.execute(clazz, key);
        }
    }

    @ImportStatic({NativeMemberNames.class, SpecialMethodNames.class, SpecialAttributeNames.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNativeMemberNode extends Node {

        abstract Object execute(Object receiver, String key) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization(guards = "eq(OB_BASE, key)")
        Object doObBase(Object o, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(OB_REFCNT, key)")
        int doObRefcnt(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") String key) {
            return 0;
        }

        @Specialization(guards = "eq(OB_TYPE, key)")
        Object doObType(Object object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetClassNode getClassNode) {
            return toSulongNode.execute(getClassNode.execute(object));
        }

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
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
        long doTpFlags(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached GetTypeFlagsNode getTypeFlagsNode) {
            return getTypeFlagsNode.execute(object);
        }

        @Specialization(guards = "eq(TP_NAME, key)")
        Object doTpName(PythonManagedClass object, @SuppressWarnings("unused") String key) {
            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            return object.getClassNativeWrapper().getNameWrapper();
        }

        @Specialization(guards = "eq(TP_DOC, key)")
        Object doTpDoc(PythonManagedClass object, @SuppressWarnings("unused") String key,
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
        Object doTpBase(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GetSuperClassNode getSuperClassNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            LazyPythonClass superClass = getSuperClassNode.execute(object);
            if (superClass != null) {
                if (superClass instanceof PythonBuiltinClassType) {
                    return toSulongNode.execute(context.getCore().lookupType((PythonBuiltinClassType) superClass));
                } else {
                    return toSulongNode.execute(superClass);
                }
            }
            return toSulongNode.execute(object);
        }

        @Specialization(guards = "eq(TP_ALLOC, key)")
        Object doTpAlloc(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object result = lookupNativeMemberNode.execute(object, NativeMemberNames.TP_ALLOC, TypeBuiltins.TYPE_ALLOC);
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "eq(TP_AS_NUMBER, key)")
        Object doTpAsNumber(PythonManagedClass object, @SuppressWarnings("unused") String key) {
            // TODO check for type and return 'NULL'
            return new PyNumberMethodsWrapper(object);
        }

        @Specialization(guards = "eq(TP_AS_BUFFER, key)")
        Object doTpAsBuffer(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached IsSubtypeWithoutFrameNode isSubtype,
                        @Cached BranchProfile notBytes,
                        @Cached BranchProfile notBytearray,
                        @Cached BranchProfile notMemoryview,
                        @Cached BranchProfile notBuffer,
                        @Cached BranchProfile notMmap,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            PythonBuiltinClass pBytes = context.getCore().lookupType(PythonBuiltinClassType.PBytes);
            if (isSubtype.passState().execute(object, pBytes)) {
                return new PyBufferProcsWrapper(pBytes);
            }
            notBytes.enter();
            PythonBuiltinClass pBytearray = context.getCore().lookupType(PythonBuiltinClassType.PByteArray);
            if (isSubtype.passState().execute(object, pBytearray)) {
                return new PyBufferProcsWrapper(pBytearray);
            }
            notBytearray.enter();
            PythonBuiltinClass pMemoryview = context.getCore().lookupType(PythonBuiltinClassType.PMemoryView);
            if (isSubtype.passState().execute(object, pMemoryview)) {
                return new PyBufferProcsWrapper(pMemoryview);
            }
            notMemoryview.enter();
            PythonBuiltinClass pBuffer = context.getCore().lookupType(PythonBuiltinClassType.PBuffer);
            if (isSubtype.passState().execute(object, pBuffer)) {
                return new PyBufferProcsWrapper(pBuffer);
            }
            notBuffer.enter();
            PythonBuiltinClass pMmap = context.getCore().lookupType(PythonBuiltinClassType.PMMap);
            if (isSubtype.passState().execute(object, pMmap)) {
                return new PyBufferProcsWrapper(pMmap);
            }
            notMmap.enter();
            // NULL pointer
            return getNativeNullNode.execute();
        }

        @Specialization(guards = "eq(TP_AS_SEQUENCE, key)")
        Object doTpAsSequence(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            if (getAttrNode.execute(object, __LEN__) != PNone.NO_VALUE) {
                return new PySequenceMethodsWrapper(object);
            } else {
                return toSulongNode.execute(PNone.NO_VALUE);
            }
        }

        @Specialization(guards = "eq(TP_NEW, key)")
        Object doTpNew(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode) {
            return ManagedMethodWrappers.createKeywords(getAttrNode.execute(object, __NEW__));
        }

        @Specialization(guards = "eq(TP_HASH, key)")
        Object doTpHash(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getHashNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getHashNode.execute(object, __HASH__));
        }

        @Specialization(guards = "eq(TP_BASICSIZE, key)")
        long doTpBasicsize(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached CastToIndexNode castToIntNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, __BASICSIZE__);
            return val != PNone.NO_VALUE ? castToIntNode.execute(val) : 0L;
        }

        @Specialization(guards = "eq(TP_ITEMSIZE, key)")
        long doTpItemsize(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached CastToIndexNode castToIntNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, __ITEMSIZE__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return 0L;
            }
            return val != PNone.NO_VALUE ? castToIntNode.execute(val) : 0L;
        }

        @Specialization(guards = "eq(TP_DICTOFFSET, key)")
        long doTpDictoffset(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached CastToIndexNode castToIntNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (object instanceof PythonBuiltinClass) {
                return 0L;
            }
            Object dictoffset = getAttrNode.execute(object, __DICTOFFSET__);
            return dictoffset != PNone.NO_VALUE ? castToIntNode.execute(dictoffset) : 0L;
        }

        @Specialization(guards = "eq(TP_WEAKLISTOFFSET, key)")
        Object doTpWeaklistoffset(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            Object val = getAttrNode.execute(object, __WEAKLISTOFFSET__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return getNativeNullNode.execute();
            }
            return val;
        }

        @Specialization(guards = "eq(TP_RICHCOMPARE, key)")
        Object doTpRichcompare(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic getCmpNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getCmpNode.execute(object, RICHCMP));
        }

        @Specialization(guards = "eq(TP_SUBCLASSES, key)")
        Object doTpSubclasses(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            // TODO create dict view on subclasses set
            return DynamicObjectNativeWrapper.PythonObjectNativeWrapper.wrap(factory.createDict(), noWrapperProfile);
        }

        @Specialization(guards = "eq(TP_GETATTR, key)")
        Object doTpGetattr(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            // we do not provide 'tp_getattr'; code will usually then use 'tp_getattro'
            return toSulongNode.execute(PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(TP_SETATTR, key)")
        Object doTpSetattr(@SuppressWarnings("unused") PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            // we do not provide 'tp_setattr'; code will usually then use 'tp_setattro'
            return toSulongNode.execute(PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(TP_GETATTRO, key)")
        Object doTpGetattro(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createGetAttrWrapper(lookupAttrNode.execute(object, __GETATTRIBUTE__));
        }

        @Specialization(guards = "eq(TP_SETATTRO, key)")
        Object doTpSetattro(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return PyProcsWrapper.createSetAttrWrapper(lookupAttrNode.execute(object, __SETATTR__));
        }

        @Specialization(guards = "eq(TP_ITERNEXT, key)")
        Object doTpIternext(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(lookupAttrNode.execute(object, __NEXT__));
        }

        @Specialization(guards = "eq(TP_REPR, key)")
        Object doTpRepr(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(lookupAttrNode.execute(object, __REPR__));
        }

        @Specialization(guards = "eq(TP_DICT, key)", limit = "1")
        Object doTpDict(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached(value = "createEquivalence()", uncached = "getSlowPathEquivalence()") Equivalence equivalence) throws UnsupportedMessageException {
            // TODO(fa): we could cache the dict instance on the class' native wrapper
            PHashingCollection dict = lib.getDict(object);
            HashingStorage dictStorage = dict != null ? dict.getDictStorage() : null;
            if (dictStorage instanceof PythonObjectHybridDictStorage) {
                // reuse the existing and modifiable storage
                return toSulongNode.execute(factory.createDict(dict.getDictStorage()));
            }
            PythonObjectHybridDictStorage storage = new PythonObjectHybridDictStorage(object.getStorage());
            if (dictStorage != null) {
                // copy all mappings to the new storage
                storage.addAll(dictStorage, equivalence);
            }
            lib.setDict(object, factory.createMappingproxy(storage));
            return toSulongNode.execute(factory.createDict(storage));
        }

        @Specialization(guards = "eq(TP_TRAVERSE, key) || eq(TP_CLEAR, key)")
        Object doTpTraverse(PythonManagedClass object, @SuppressWarnings("unused") String key,
                        @Cached IsBuiltinClassProfile isTupleProfile,
                        @Cached IsBuiltinClassProfile isDictProfile,
                        @Cached IsBuiltinClassProfile isListProfile,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            if (isTupleProfile.profileClass(object, PythonBuiltinClassType.PTuple) || isDictProfile.profileClass(object, PythonBuiltinClassType.PDict) ||
                            isListProfile.profileClass(object, PythonBuiltinClassType.PList)) {
                // We do not actually return the traverse or clear method since we will never need
                // it. It is just important to return something != NULL.
                return toSulongNode.execute(PNone.NONE);
            }
            return getNativeNullNode.execute();
        }

        public static ReadTypeNativeMemberNode create() {
            return ReadTypeNativeMemberNodeGen.create();
        }

        protected static Equivalence createEquivalence() {
            return PythonEquivalence.create();
        }

        protected static Equivalence getSlowPathEquivalence() {
            return HashingStorage.getSlowPathEquivalence(null);
        }
    }

    @GenerateUncached
    abstract static class ReadObjectNativeMemberNode extends ReadNativeMemberNode {

        @Specialization(guards = "eq(D_COMMON, key)")
        Object doDCommon(Object o, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(_BASE, key)")
        Object doObBase(PString o, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(o);
        }

        @Specialization(guards = "eq(OB_SIZE, key)")
        long doObSize(Object object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached LookupAndCallUnaryDynamicNode callLenNode) {
            Object res = callLenNode.passState().executeObject(object, SpecialMethodNames.__LEN__);
            if (res instanceof Number) {
                return ((Number) res).intValue();
            }
            return -1;
        }

        @Specialization(guards = "eq(MA_USED, key)")
        int doMaUsed(PDict object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached LookupAndCallUnaryDynamicNode callLenNode) {
            Object res = callLenNode.passState().executeObject(object, SpecialMethodNames.__LEN__);
            if (res instanceof Number) {
                return ((Number) res).intValue();
            }
            return -1;
        }

        @Specialization(guards = "eq(OB_SVAL, key)")
        Object doObSval(PBytes object, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object, 1);
        }

        @Specialization(guards = "eq(OB_START, key)")
        Object doObStart(PByteArray object, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object, 1);
        }

        @Specialization(guards = "eq(OB_FVAL, key)")
        Object doObFval(Object object, @SuppressWarnings("unused") String key,
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
        Object doObItem(PSequence object, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object, 4);
        }

        @Specialization(guards = "eq(UNICODE_WSTR, key)")
        Object doWstr(PString object, @SuppressWarnings("unused") String key,
                        @Shared("asWideCharNode") @Cached(value = "createNativeOrder()", uncached = "getUncachedNativeOrder()") UnicodeAsWideCharNode asWideCharNode,
                        @Shared("sizeofWcharNode") @Cached CExtNodes.SizeofWCharNode sizeofWcharNode,
                        @Shared("strLen") @Cached StringLenNode stringLenNode) {
            int elementSize = (int) sizeofWcharNode.execute();
            return new PySequenceArrayWrapper(asWideCharNode.execute(object, elementSize, stringLenNode.execute(object)), elementSize);
        }

        @Specialization(guards = "eq(UNICODE_WSTR_LENGTH, key)")
        long doWstrLength(PString object, @SuppressWarnings("unused") String key,
                        @Shared("asWideCharNode") @Cached(value = "createNativeOrder()", uncached = "getUncachedNativeOrder()") UnicodeAsWideCharNode asWideCharNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Shared("sizeofWcharNode") @Cached CExtNodes.SizeofWCharNode sizeofWcharNode,
                        @Shared("strLen") @Cached StringLenNode stringLenNode) {
            long sizeofWchar = sizeofWcharNode.execute();
            PBytes result = asWideCharNode.execute(object, sizeofWchar, stringLenNode.execute(object));
            return lenNode.execute(result.getSequenceStorage()) / sizeofWchar;
        }

        @Specialization(guards = "eq(UNICODE_LENGTH, key)")
        long doUnicodeLength(PString object, @SuppressWarnings("unused") String key,
                        @Shared("strLen") @Cached StringLenNode stringLenNode) {
            return stringLenNode.execute(object);
        }

        @Specialization(guards = "eq(UNICODE_DATA, key)")
        Object doUnicodeData(PString object, @SuppressWarnings("unused") String key) {
            return new PyUnicodeWrappers.PyUnicodeData(object);
        }

        @Specialization(guards = "eq(UNICODE_STATE, key)")
        Object doState(PString object, @SuppressWarnings("unused") String key) {
            // TODO also support bare 'String' ?
            return new PyUnicodeWrappers.PyUnicodeState(object);
        }

        @Specialization(guards = "eq(UNICODE_HASH, key)")
        @TruffleBoundary
        long doUnicodeHash(PString object, @SuppressWarnings("unused") String key) {
            // TODO also support bare 'String' ?
            return object.hashCode();
        }

        @Specialization(guards = "eq(MD_DICT, key)")
        Object doMdDict(Object object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getDictNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getDictNode.execute(object, SpecialAttributeNames.__DICT__));
        }

        @Specialization(guards = "eq(TP_DICT, key)", limit = "1")
        Object doTpDict(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) throws UnsupportedMessageException {
            PHashingCollection dict = lib.getDict(object);
            if (!(dict instanceof PDict)) {
                assert dict instanceof PMappingproxy || dict == null;
                // If 'dict instanceof PMappingproxy', it seems that someone already used
                // '__dict__'
                // on this type and created a mappingproxy object. We need to replace it by a
                // dict.
                dict = factory.createDictFixedStorage(object);
                lib.setDict(object, dict);
            }
            assert dict instanceof PDict;
            return toSulongNode.execute(dict);
        }

        @Specialization(guards = "eq(MD_DEF, key)")
        Object doMdDef(PythonObject object, @SuppressWarnings("unused") String key,
                        @Shared("getItemNode") @Cached HashingStorageNodes.GetItemInteropNode getItemNode) {
            DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
            assert nativeWrapper != null;
            return getItemNode.passState().execute(nativeWrapper.getNativeMemberStore(), MD_DEF);
        }

        @Specialization(guards = "eq(BUF_DELEGATE, key)")
        Object doBufDelegate(PBuffer object, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object.getDelegate(), 1);
        }

        @Specialization(guards = "eq(BUF_READONLY, key)")
        int doBufReadonly(PBuffer object, @SuppressWarnings("unused") String key) {
            return object.isReadOnly() ? 1 : 0;
        }

        @Specialization(guards = "eq(START, key)")
        Object doStart(PSlice object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getSliceComponent(object.getStart()));
        }

        @Specialization(guards = "eq(STOP, key)")
        Object doStop(PSlice object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getSliceComponent(object.getStop()));
        }

        @Specialization(guards = "eq(STEP, key)")
        Object doStep(PSlice object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getSliceComponent(object.getStep()));
        }

        @Specialization(guards = "eq(IM_SELF, key)")
        Object doImSelf(PMethod object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(IM_SELF, key)")
        Object doImSelf(PBuiltinMethod object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getSelf());
        }

        @Specialization(guards = "eq(IM_FUNC, key)")
        Object doImFunc(PMethod object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFunction());
        }

        @Specialization(guards = "eq(IM_FUNC, key)")
        Object doImFunc(PBuiltinMethod object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(object.getFunction());
        }

        @Specialization(guards = "eq(D_MEMBER, key)")
        Object doDMember(PythonObject object, @SuppressWarnings("unused") String key) {
            return new PyMemberDefWrapper(object);
        }

        @Specialization(guards = "eq(D_GETSET, key)")
        Object doDGetSet(PythonObject object, @SuppressWarnings("unused") String key) {
            return new PyGetSetDefWrapper(object);
        }

        @Specialization(guards = "eq(D_METHOD, key)")
        Object doDBase(PythonObject object, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        Object doDBase(PBuiltinFunction object, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        Object doDBase(PFunction object, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        Object doDBase(PBuiltinMethod object, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(M_ML, key)")
        Object doDBase(PMethod object, @SuppressWarnings("unused") String key) {
            return new PyMethodDescrWrapper(object);
        }

        @Specialization(guards = "eq(D_QUALNAME, key)")
        Object doDQualname(PythonObject object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getAttributeNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getAttributeNode.execute(object, SpecialAttributeNames.__QUALNAME__));
        }

        @Specialization(guards = "eq(SET_USED, key)")
        long doSetUsed(PSet object, @SuppressWarnings("unused") String key,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStorageNode,
                        @Cached HashingStorageNodes.LenNode lenNode) {
            return lenNode.execute(getStorageNode.execute(object));
        }

        @Specialization
        Object doMemoryview(PMemoryView object, String key,
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
            throw new IllegalStateException("delegate of memoryview object is not native");
        }

        @Specialization(guards = "eq(MMAP_DATA, key)")
        Object doMmapData(PMMap object, @SuppressWarnings("unused") String key) {
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
        Object doDatetimeCAPI(PythonObject object, String key,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getNameNode") @Cached @SuppressWarnings("unused") GetNameNode getNameNode,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            return toSulongNode.execute(getAttrNode.execute(getClassNode.execute(object), key));
        }

        @Specialization(guards = "isPyDateTime(object, getClassNode, getNameNode)", limit = "1")
        Object doDatetimeData(PythonObject object, @SuppressWarnings("unused") String key,
                        @Shared("getNameNode") @Cached @SuppressWarnings("unused") GetNameNode getNameNode,
                        @Shared("getClassNode") @Cached @SuppressWarnings("unused") GetClassNode getClassNode,
                        @Cached PyDateTimeMRNode pyDateTimeMRNode) {
            return pyDateTimeMRNode.execute(object, key);
        }

        // TODO fallback guard
        @Specialization
        Object doGeneric(Object object, String key,
                        @Shared("getItemNode") @Cached HashingStorageNodes.GetItemInteropNode getItemNode) throws UnknownIdentifierException {
            // This is the preliminary generic case: There are native members we know that they
            // exist but we do currently not represent them. So, store them into a dynamic object
            // such that native code at least reads the value that was written before.
            if (object instanceof PythonAbstractObject) {
                DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
                assert nativeWrapper != null;
                logGeneric(key);
                return getItemNode.passState().execute(nativeWrapper.getNativeMemberStore(), key);
            }
            throw UnknownIdentifierException.create(key);
        }

        @TruffleBoundary(allowInlining = true)
        private static void logGeneric(String key) {
            PythonLanguage.getLogger().log(Level.FINE, "read of Python struct native member " + key);
        }
    }

    // WRITE
    @GenerateUncached
    @ImportStatic({NativeMemberNames.class, PGuards.class, SpecialMethodNames.class, SpecialAttributeNames.class})
    abstract static class WriteNativeMemberNode extends Node {

        abstract Object execute(Object receiver, String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;

        @Specialization(guards = "eq(OB_TYPE, key)")
        Object doObType(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") PythonManagedClass value,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            // At this point, we do not support changing the type of an object.
            return DynamicObjectNativeWrapper.PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
        }

        @Specialization(guards = "eq(TP_FLAGS, key)")
        long doTpFlags(PythonManagedClass object, @SuppressWarnings("unused") String key, long flags) {
            object.setFlags(flags);
            return flags;
        }

        @Specialization(guards = "eq(TP_BASICSIZE, key)")
        long doTpBasicsize(PythonAbstractClass object, @SuppressWarnings("unused") String key, long basicsize,
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
        Object doTpAlloc(PythonAbstractClass object, @SuppressWarnings("unused") String key, Object allocFunc,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_ALLOC, asPythonObjectNode.execute(allocFunc));
            return allocFunc;
        }

        @Specialization(guards = "eq(TP_SUBCLASSES, key)")
        @TruffleBoundary
        Object doTpSubclasses(PythonClass object, @SuppressWarnings("unused") String key, DynamicObjectNativeWrapper.PythonObjectNativeWrapper value) {
            // TODO more type checking; do fast path
            PDict dict = (PDict) value.getPythonObject();
            for (Object item : dict.items()) {
                GetSubclassesNode.doSlowPath(object).add((PythonClass) item);
            }
            return value;
        }

        @Specialization(guards = "eq(MD_DEF, key)")
        Object doMdDef(PythonObject object, @SuppressWarnings("unused") String key, Object value,
                        @Shared("setItemNode") @Cached HashingStorageNodes.DynamicObjectSetItemNode setItemNode) {
            DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
            assert nativeWrapper != null;
            setItemNode.passState().execute(nativeWrapper.createNativeMemberStore(), MD_DEF, value);
            return value;
        }

        @Specialization(guards = "eq(TP_DICT, key)", limit = "1")
        Object doTpDict(PythonManagedClass object, @SuppressWarnings("unused") String key, Object nativeValue,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Cached HashingStorageNodes.GetItemInteropNode getItem,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached IsBuiltinClassProfile isPrimitiveDictProfile) throws UnsupportedMessageException {
            Object value = asPythonObjectNode.execute(nativeValue);
            if (value instanceof PDict && isPrimitiveDictProfile.profileObject((PDict) value, PythonBuiltinClassType.PDict)) {
                // special and fast case: commit items and change store
                PDict d = (PDict) value;
                for (Object k : d.keys()) {
                    writeAttrNode.execute(object, k, getItem.passState().execute(d.getDictStorage(), k));
                }
                PHashingCollection existing = lib.getDict(object);
                if (existing != null) {
                    d.setDictStorage(existing.getDictStorage());
                } else {
                    d.setDictStorage(new DynamicObjectStorage.PythonObjectDictStorage(object.getStorage()));
                }
                lib.setDict(object, d);
            } else {
                // TODO custom mapping object
            }
            return value;
        }

        @Specialization(guards = "eq(TP_DICTOFFSET, key)")
        Object doTpDictoffset(PythonManagedClass object, @SuppressWarnings("unused") String key, Object value,
                        @Cached CastToIntegerFromIntNode.Dynamic castToIntNode,
                        @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode) throws UnsupportedMessageException, UnknownIdentifierException {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (object instanceof PythonBuiltinClass) {
                return 0L;
            }
            setAttrNode.execute(object, __DICTOFFSET__, castToIntNode.execute(value));
            return value;
        }

        @Specialization
        Object doMemoryview(PMemoryView object, String key, Object value,
                        @Cached ReadAttributeFromObjectNode readAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile isNativeObject,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            Object delegateObj = readAttrNode.execute(object, "__c_memoryview");
            if (isNativeObject.profile(PythonNativeObject.isInstance(delegateObj))) {
                interopLib.writeMember(PythonNativeObject.cast(delegateObj).getPtr(), key, value);
            }
            throw new IllegalStateException("delegate of memoryview object is not native");
        }

        @Specialization
        Object doGeneric(Object object, String key, Object value,
                        @Shared("setItemNode") @Cached HashingStorageNodes.DynamicObjectSetItemNode setItemNode) throws UnknownIdentifierException {
            // This is the preliminary generic case: There are native members we know that they
            // exist but we do currently not represent them. So, store them into a dynamic object
            // such that native code at least reads the value that was written before.
            if (object instanceof PythonAbstractObject) {
                DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
                assert nativeWrapper != null;
                logGeneric(key);
                setItemNode.passState().execute(nativeWrapper.createNativeMemberStore(), key, value);
                return value;
            }
            throw UnknownIdentifierException.create(key);
        }

        @TruffleBoundary(allowInlining = true)
        private static void logGeneric(String key) {
            PythonLanguage.getLogger().log(Level.FINE, "write of Python struct native member " + key);
        }

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        switch (member) {
            case OB_TYPE:
            case TP_FLAGS:
            case TP_BASICSIZE:
            case TP_SUBCLASSES:
            case MD_DEF:
            case TP_DICT:
            case TP_DICTOFFSET:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @Cached WriteNativeMemberNode writeNativeMemberNode) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        writeNativeMemberNode.execute(getDelegate(), member, value);
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
                    @Cached.Exclusive @Cached PythonAbstractObject.PExecuteNode executeNode,
                    @Cached.Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                    @Cached.Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) throws UnsupportedMessageException {
        // convert args
        Object[] converted = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            converted[i] = toJavaNode.execute(arguments[i]);
        }
        Object result;
        try {
            result = executeNode.execute(getDelegate(), converted);
        } catch (PException e) {
            result = PNone.NO_VALUE;
        }
        return toSulongNode.execute(result);
    }

    // TO NATIVE, IS POINTER, AS POINTER
    @GenerateUncached
    abstract static class ToNativeNode extends Node {
        public abstract void execute(PythonNativeWrapper obj);

        protected static boolean isClassInitNativeWrapper(PythonNativeWrapper obj) {
            return obj instanceof PythonClassInitNativeWrapper;
        }

        @Specialization
        public void executeClsInit(PythonClassInitNativeWrapper obj,
                        @Shared("toPyObjectNode") @Cached ToPyObjectNode toPyObjectNode,
                        @Shared("invalidateNode") @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
        }

        @Specialization(guards = "!isClassInitNativeWrapper(obj)")
        public void execute(PythonNativeWrapper obj,
                        @Shared("toPyObjectNode") @Cached ToPyObjectNode toPyObjectNode,
                        @Cached SetSpecialSingletonPtrNode setSpecialSingletonPtrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Shared("invalidateNode") @Cached InvalidateNativeObjectsAllManagedNode invalidateNode,
                        @Cached IsPointerNode isPointerNode) {
            invalidateNode.execute();
            if (!isPointerNode.execute(obj)) {
                Object ptr = toPyObjectNode.execute(obj);
                Object delegate = obj.getDelegate();
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

        @Specialization(guards = {"obj.isBool()", "!obj.isNative()"})
        long doBoolNotNative(PrimitiveNativeWrapper obj,
                        @Cached CExtNodes.MaterializeDelegateNode materializeNode,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            // special case for True and False singletons
            PInt boxed = (PInt) materializeNode.execute(obj);
            assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
            return ensureLong(interopLib, obj.getNativePointer());
        }

        @Specialization(guards = {"obj.isBool()", "obj.isNative()"})
        long doBoolNative(PrimitiveNativeWrapper obj,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
            return ensureLong(interopLib, obj.getNativePointer());
        }

        @Specialization(guards = "!isBoolNativeWrapper(obj)")
        long doFast(PythonNativeWrapper obj,
                        @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached GetSpecialSingletonPtrNode getSpecialSingletonPtrNode) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (profile.profile(nativePointer == null)) {
                // We assume that before someone calls 'asPointer' on the wrapper, 'isPointer' was
                // checked and returned true. So, for this case we assume that it is one of the
                // special singletons where we store the pointer in the context.
                nativePointer = getSpecialSingletonPtrNode.execute(obj.getDelegate());
                assert nativePointer != null : createAssertionMessage(obj.getDelegate());
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
    abstract static class ToPyObjectNode extends CExtNodes.CExtBaseNode {
        public abstract Object execute(PythonNativeWrapper wrapper);

        @Specialization(guards = "isManagedPythonClass(wrapper)")
        Object doClass(PythonClassNativeWrapper wrapper,
                        @Exclusive @Cached PCallCapiFunction callNativeUnary) {
            return callNativeUnary.call(FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE, wrapper);
        }

        @Specialization(guards = "!isManagedPythonClass(wrapper)")
        Object doObject(PythonNativeWrapper wrapper,
                        @Exclusive @Cached PCallCapiFunction callNativeUnary) {
            return callNativeUnary.call(FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT, wrapper);
        }

        protected static boolean isManagedPythonClass(PythonNativeWrapper wrapper) {
            assert !(wrapper instanceof PythonClassNativeWrapper) || PGuards.isManagedClass(wrapper.getDelegate());
            return wrapper instanceof PythonClassNativeWrapper && !(PGuards.isNativeClass(wrapper.getDelegate()));
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

        public PythonAbstractObject getPythonObject() {
            return (PythonAbstractObject) getDelegate();
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

        public static DynamicObjectNativeWrapper wrapSlowPath(PythonAbstractObject obj) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (nativeWrapper == null) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
        }

        @ExportMessage
        protected boolean isMemberReadable(String member,
                        @Cached GetLazyClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            return DynamicObjectNativeWrapper.GP_OBJECT.equals(member) || NativeMemberNames.isValid(member) ||
                            ReadObjectNativeMemberNode.isPyDateTimeCAPIType(getNameNode.execute(getClassNode.execute(getDelegate())));
        }

        @ExportMessage
        @Override
        public boolean isMemberModifiable(String member) {
            return NativeMemberNames.isValid(member);
        }
    }

    public static final class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

        public static final byte PRIMITIVE_STATE_BOOL = 1 << 0;
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
        public Object getMaterializedObject() {
            return getDelegate();
        }

        // this method exists just for readability
        public void setMaterializedObject(Object materializedPrimitive) {
            setDelegate(materializedPrimitive);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PrimitiveNativeWrapper && ((PrimitiveNativeWrapper) obj).state == state && ((PrimitiveNativeWrapper) obj).value == value &&
                            ((PrimitiveNativeWrapper) obj).dvalue == dvalue;
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

        @Override
        @ExportMessage
        protected boolean isMemberReadable(String member) {
            return member.equals(DynamicObjectNativeWrapper.GP_OBJECT) || NativeMemberNames.isValid(member);
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
}
