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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
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
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public abstract class NativeWrappers {
    private static final String GP_OBJECT = "gp_object";

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public abstract static class PythonNativeWrapper implements TruffleObject {

        private Object delegate;
        private Object nativePointer;

        public PythonNativeWrapper() {
        }

        public PythonNativeWrapper(Object delegate) {
            this.delegate = delegate;
        }

        public final Object getDelegate() {
            return delegate;
        }

        protected void setDelegate(Object delegate) {
            this.delegate = delegate;
        }

        public Object getNativePointer() {
            return nativePointer;
        }

        public void setNativePointer(Object nativePointer) {
            // we should set the pointer just once
            assert this.nativePointer == null || this.nativePointer.equals(nativePointer) || nativePointer == null;
            this.nativePointer = nativePointer;
        }

        public boolean isNative() {
            return nativePointer != null;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof DynamicObjectNativeWrapper || o instanceof TruffleObjectNativeWrapper;
        }

        // READ
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        protected boolean isMemberReadable(String member) {
            // TODO: op on the keys from ReadNativeMemberNode - although there are too many in there
            return true;
        }

        @ExportMessage
        protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return PythonLanguage.getContextRef().get().getEnv().asGuestValue(new String[]{GP_OBJECT});
        }

        @ExportMessage
        protected Object readMember(String member,
                        @Cached.Exclusive @Cached(allowUncached = true) ReadNode readNode) {
            return readNode.execute(this, member);
        }

        abstract static class ReadNode extends Node {
            public abstract Object execute(PythonNativeWrapper object, String key);

            protected boolean isObBase(String key) {
                return key.equals(NativeMemberNames.OB_BASE);
            }

            @Specialization(guards = {"key == cachedObBase", "isObBase(key)"})
            public Object execute(PythonNativeWrapper object, String key,
                            @Cached("key") String cachedObBase) {
                // TODO: TRUFFLELIB REFACTORING REVISIT
                // -------------------------------------------------------
                // original code:
                // if (key == cachedObBase) {
                // return object;
                // } else if (cachedObBase == null && key.equals(NativeMemberNames.OB_BASE)) {
                // CompilerDirectives.transferToInterpreterAndInvalidate();
                // cachedObBase = key;
                // return object;
                // }
                // -------------------------------------------------------
                // The very common case: directly return native wrapper.
                // This is in particular important for PrimitiveNativeWrappers, since they are not
                // cached.
                return object;
            }

            @Specialization
            public Object execute(PythonNativeWrapper object, String key,
                            @Cached.Exclusive @Cached ReadNativeMemberNode readNativeMemberNode,
                            @Cached.Exclusive @Cached CExtNodes.AsPythonObjectNode getDelegate) {
                Object delegate = getDelegate.execute(object);

                // special key for the debugger
                if (key.equals(GP_OBJECT)) {
                    return delegate;
                }
                return readNativeMemberNode.execute(delegate, key);
            }
        }

        @ImportStatic({NativeMemberNames.class, SpecialMethodNames.class, SpecialAttributeNames.class})
        @TypeSystemReference(PythonArithmeticTypes.class)
        abstract static class ReadNativeMemberNode extends PNodeWithContext {
            @Child GetClassNode getClassNode;
            @Child private CExtNodes.ToSulongNode toSulongNode;
            @Child private HashingStorageNodes.GetItemNode getItemNode;
            @Child private CExtNodes.SizeofWCharNode sizeofWcharNode;

            abstract Object execute(Object receiver, String key);

            @Specialization(guards = "eq(OB_BASE, key)")
            Object doObBase(Object o, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(o);
            }

            @Specialization(guards = "eq(D_COMMON, key)")
            Object doDCommon(Object o, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(o);
            }

            @Specialization(guards = "eq(_BASE, key)")
            Object doObBase(PString o, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(o);
            }

            @Specialization(guards = "eq(OB_REFCNT, key)")
            int doObRefcnt(@SuppressWarnings("unused") Object o, @SuppressWarnings("unused") String key) {
                return 0;
            }

            @Specialization(guards = "eq(OB_TYPE, key)")
            Object doObType(Object object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(getClass(object));
            }

            @Specialization(guards = "eq(OB_SIZE, key)")
            long doObSize(Object object, @SuppressWarnings("unused") String key,
                            @Cached("create(__LEN__)") LookupAndCallUnaryNode callLenNode) {
                try {
                    return callLenNode.executeInt(object);
                } catch (UnexpectedResultException e) {
                    return -1;
                }
            }

            @Specialization(guards = "eq(MA_USED, key)")
            int doMaUsed(PDict object, @SuppressWarnings("unused") String key,
                            @Cached("create(__LEN__)") LookupAndCallUnaryNode callLenNode) {
                try {
                    return callLenNode.executeInt(object);
                } catch (UnexpectedResultException e) {
                    return -1;
                }
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
                            @Cached("createClassProfile()") ValueProfile profile) {
                Object profiled = profile.profile(object);
                if (profiled instanceof PFloat) {
                    return ((PFloat) profiled).getValue();
                } else if (profiled instanceof Double) {
                    return object;
                }
                throw UnsupportedMessageException.raise(Message.READ);
            }

            @Specialization(guards = "eq(TP_FLAGS, key)")
            long doTpFlags(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create()") GetTypeFlagsNode getTypeFlagsNode) {
                return getTypeFlagsNode.execute(object);
            }

            @Specialization(guards = "eq(TP_NAME, key)")
            Object doTpName(PythonClass object, @SuppressWarnings("unused") String key) {
                // return a C string wrapper that really allocates 'char*' on TO_NATIVE
                return object.getNativeWrapper().getNameWrapper();
            }

            @Specialization(guards = "eq(TP_BASE, key)")
            Object doTpBase(PythonClass object, @SuppressWarnings("unused") String key) {
                PythonClass superClass = object.getSuperClass();
                if (superClass != null) {
                    return getToSulongNode().execute(superClass);
                }
                return getToSulongNode().execute(object);
            }

            @Specialization(guards = "eq(TP_ALLOC, key)")
            Object doTpAlloc(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__ALLOC__)") LookupAttributeInMRONode getAllocNode) {
                Object result = getAllocNode.execute(object);
                return getToSulongNode().execute(result);
            }

            @Specialization(guards = "eq(TP_AS_NUMBER, key)")
            Object doTpAsNumber(PythonClass object, @SuppressWarnings("unused") String key) {
                // TODO check for type and return 'NULL'
                return new PyNumberMethodsWrapper(object);
            }

            @Specialization(guards = "eq(TP_AS_BUFFER, key)")
            Object doTpAsBuffer(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create()") IsSubtypeNode isSubtype,
                            @Cached("create()") BranchProfile notBytes,
                            @Cached("create()") BranchProfile notBytearray,
                            @Cached("create()") BranchProfile notMemoryview,
                            @Cached("create()") BranchProfile notBuffer) {
                PythonCore core = getCore();
                PythonBuiltinClass pBytes = core.lookupType(PythonBuiltinClassType.PBytes);
                if (isSubtype.execute(object, pBytes)) {
                    return new PyBufferProcsWrapper(pBytes);
                }
                notBytes.enter();
                PythonBuiltinClass pBytearray = core.lookupType(PythonBuiltinClassType.PByteArray);
                if (isSubtype.execute(object, pBytearray)) {
                    return new PyBufferProcsWrapper(pBytearray);
                }
                notBytearray.enter();
                PythonBuiltinClass pMemoryview = core.lookupType(PythonBuiltinClassType.PMemoryView);
                if (isSubtype.execute(object, pMemoryview)) {
                    return new PyBufferProcsWrapper(pMemoryview);
                }
                notMemoryview.enter();
                PythonBuiltinClass pBuffer = core.lookupType(PythonBuiltinClassType.PBuffer);
                if (isSubtype.execute(object, pBuffer)) {
                    return new PyBufferProcsWrapper(pBuffer);
                }
                notBuffer.enter();
                // NULL pointer
                return getToSulongNode().execute(PNone.NO_VALUE);
            }

            @Specialization(guards = "eq(TP_AS_SEQUENCE, key)")
            Object doTpAsSequence(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__LEN__)") LookupAttributeInMRONode getAttrNode) {
                if (getAttrNode.execute(object) != PNone.NO_VALUE) {
                    return new PySequenceMethodsWrapper(object);
                } else {
                    return getToSulongNode().execute(PNone.NO_VALUE);
                }
            }

            @Specialization(guards = "eq(TP_NEW, key)")
            Object doTpNew(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__NEW__)") LookupAttributeInMRONode getAttrNode) {
                return ManagedMethodWrappers.createKeywords(getAttrNode.execute(object));
            }

            @Specialization(guards = "eq(TP_HASH, key)")
            Object doTpHash(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__HASH__)") LookupAttributeInMRONode getHashNode) {
                return getToSulongNode().execute(getHashNode.execute(object));
            }

            @Specialization(guards = "eq(TP_BASICSIZE, key)")
            Object doTpBasicsize(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__BASICSIZE__)") LookupAttributeInMRONode getAttrNode) {
                return getAttrNode.execute(object);
            }

            @Specialization(guards = "eq(TP_ITEMSIZE, key)")
            Object doTpItemsize(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__ITEMSIZE__)") LookupAttributeInMRONode getAttrNode) {
                return getAttrNode.execute(object);
            }

            @Specialization(guards = "eq(TP_DICTOFFSET, key)")
            Object doTpDictoffset(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create()") CastToIndexNode castToIntNode,
                            @Cached("create(__DICTOFFSET__)") LookupAttributeInMRONode getAttrNode) {
                // TODO properly implement 'tp_dictoffset' for builtin classes
                if (object instanceof PythonBuiltinClass) {
                    return 0L;
                }
                Object dictoffset = getAttrNode.execute(object);
                return castToIntNode.execute(dictoffset);
            }

            @Specialization(guards = "eq(TP_RICHCOMPARE, key)")
            Object doTpRichcompare(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(RICHCMP)") LookupAttributeInMRONode getCmpNode) {
                return getToSulongNode().execute(getCmpNode.execute(object));
            }

            @Specialization(guards = "eq(TP_SUBCLASSES, key)")
            Object doTpSubclasses(@SuppressWarnings("unused") PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
                // TODO create dict view on subclasses set
                return PythonObjectNativeWrapper.wrap(factory().createDict(), noWrapperProfile);
            }

            @Specialization(guards = "eq(TP_GETATTR, key)")
            Object doTpGetattr(@SuppressWarnings("unused") PythonClass object, @SuppressWarnings("unused") String key) {
                // we do not provide 'tp_getattr'; code will usually then use 'tp_getattro'
                return getToSulongNode().execute(PNone.NO_VALUE);
            }

            @Specialization(guards = "eq(TP_SETATTR, key)")
            Object doTpSetattr(@SuppressWarnings("unused") PythonClass object, @SuppressWarnings("unused") String key) {
                // we do not provide 'tp_setattr'; code will usually then use 'tp_setattro'
                return getToSulongNode().execute(PNone.NO_VALUE);
            }

            @Specialization(guards = "eq(TP_GETATTRO, key)")
            Object doTpGetattro(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__GETATTRIBUTE__)") LookupAttributeInMRONode lookupAttrNode) {
                return PyProcsWrapper.createGetAttrWrapper(lookupAttrNode.execute(object));
            }

            @Specialization(guards = "eq(TP_SETATTRO, key)")
            Object doTpSetattro(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__SETATTR__)") LookupAttributeInMRONode lookupAttrNode) {
                return PyProcsWrapper.createSetAttrWrapper(lookupAttrNode.execute(object));
            }

            @Specialization(guards = "eq(TP_ITERNEXT, key)")
            Object doTpIternext(PythonClass object, @SuppressWarnings("unused") String key,
                            @Cached("create(__NEXT__)") LookupAttributeInMRONode lookupAttrNode) {
                return getToSulongNode().execute(lookupAttrNode.execute(object));
            }

            @Specialization(guards = "eq(OB_ITEM, key)")
            Object doObItem(PSequence object, @SuppressWarnings("unused") String key) {
                return new PySequenceArrayWrapper(object, 4);
            }

            @Specialization(guards = "eq(UNICODE_WSTR, key)")
            Object doWstr(PString object, @SuppressWarnings("unused") String key,
                            @Cached("create(0)") UnicodeObjectNodes.UnicodeAsWideCharNode asWideCharNode) {
                int elementSize = sizeofWchar();
                return new PySequenceArrayWrapper(asWideCharNode.execute(object, elementSize, object.len()), elementSize);
            }

            @Specialization(guards = "eq(UNICODE_WSTR_LENGTH, key)")
            long doWstrLength(PString object, @SuppressWarnings("unused") String key,
                            @Cached("create(0)") UnicodeObjectNodes.UnicodeAsWideCharNode asWideCharNode,
                            @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
                long sizeofWchar = sizeofWchar();
                PBytes result = asWideCharNode.execute(object, sizeofWchar, object.len());
                return lenNode.execute(result.getSequenceStorage()) / sizeofWchar;
            }

            @Specialization(guards = "eq(UNICODE_LENGTH, key)")
            long doUnicodeLength(PString object, @SuppressWarnings("unused") String key) {
                return object.len();
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
                            @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getDictNode) {
                return getToSulongNode().execute(getDictNode.executeObject(object, SpecialAttributeNames.__DICT__));
            }

            @Specialization(guards = "eq(TP_DICT, key)")
            Object doTpDict(PythonClass object, @SuppressWarnings("unused") String key) {
                PHashingCollection dict = object.getDict();
                if (!(dict instanceof PDict)) {
                    assert dict instanceof PMappingproxy || dict == null;
                    // If 'dict instanceof PMappingproxy', it seems that someone already used
                    // '__dict__'
                    // on this type and created a mappingproxy object. We need to replace it by a
                    // dict.
                    dict = factory().createDictFixedStorage(object);
                    object.setDict(dict);
                }
                assert dict instanceof PDict;
                return getToSulongNode().execute(dict);
            }

            @Specialization(guards = "eq(MD_DEF, key)")
            Object doMdDef(PythonObject object, @SuppressWarnings("unused") String key) {
                DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
                assert nativeWrapper != null;
                return getGetItemNode().execute(nativeWrapper.getNativeMemberStore(), MD_DEF);
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
            Object doStart(PSlice object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(getSliceComponent(object.getStart()));
            }

            @Specialization(guards = "eq(STOP, key)")
            Object doStop(PSlice object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(getSliceComponent(object.getStop()));
            }

            @Specialization(guards = "eq(STEP, key)")
            Object doStep(PSlice object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(getSliceComponent(object.getStep()));
            }

            @Specialization(guards = "eq(IM_SELF, key)")
            Object doImSelf(PMethod object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(object.getSelf());
            }

            @Specialization(guards = "eq(IM_SELF, key)")
            Object doImSelf(PBuiltinMethod object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(object.getSelf());
            }

            @Specialization(guards = "eq(IM_FUNC, key)")
            Object doImFunc(PMethod object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(object.getFunction());
            }

            @Specialization(guards = "eq(IM_FUNC, key)")
            Object doImFunc(PBuiltinMethod object, @SuppressWarnings("unused") String key) {
                return getToSulongNode().execute(object.getFunction());
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
                            @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getQualnameNode) {
                return getToSulongNode().execute(getQualnameNode.executeObject(object, SpecialAttributeNames.__QUALNAME__));
            }

            @Specialization(guards = "eq(SET_USED, key)")
            long doSetUsed(PSet object, @SuppressWarnings("unused") String key,
                            @Cached("create()") HashingCollectionNodes.GetDictStorageNode getStorageNode,
                            @Cached("create()") HashingStorageNodes.LenNode lenNode) {
                return lenNode.execute(getStorageNode.execute(object));
            }

            @Specialization
            Object doMemoryview(PMemoryView object, String key,
                            @Cached("create()") ReadAttributeFromObjectNode readAttrNode,
                            @Cached("createReadNode()") Node readNode,
                            @Cached("createBinaryProfile()") ConditionProfile isNativeObject) {
                Object delegateObj = readAttrNode.execute(object, "__c_memoryview");
                if (isNativeObject.profile(delegateObj instanceof PythonNativeObject)) {
                    try {
                        return ForeignAccess.sendRead(readNode, ((PythonNativeObject) delegateObj).object, key);
                    } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                        throw e.raise();
                    }
                }
                throw new IllegalStateException("delegate of memoryview object is not native");
            }

            protected boolean isPyDateTimeCAPI(PythonObject object) {
                return getClass(object).getName().equals("PyDateTime_CAPI");
            }

            protected boolean isPyDateTime(PythonObject object) {
                return getClass(object).getName().equals("datetime");
            }

            @Specialization(guards = "isPyDateTimeCAPI(object)")
            Object doDatetimeCAPI(PythonObject object, String key,
                            @Cached("create()") LookupAttributeInMRONode.Dynamic getAttrNode) {
                return getToSulongNode().execute(getAttrNode.execute(getClassNode.execute(object), key));
            }

            @Specialization(guards = "isPyDateTime(object)")
            Object doDatetimeData(PythonObject object, @SuppressWarnings("unused") String key,
                            @Cached("create()") PyDateTimeMRNode pyDateTimeMRNode) {
                return pyDateTimeMRNode.execute(object, key);
            }

            @Fallback
            Object doGeneric(Object object, String key) {
                // This is the preliminary generic case: There are native members we know that they
                // exist but we do currently not represent them. So, store them into a dynamic
                // object
                // such that native code at least reads the value that was written before.
                if (object instanceof PythonAbstractObject) {
                    DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
                    assert nativeWrapper != null;
                    logGeneric(key);
                    return getGetItemNode().execute(nativeWrapper.getNativeMemberStore(), key);
                }
                throw UnknownIdentifierException.raise(key);
            }

            @TruffleBoundary(allowInlining = true)
            private static void logGeneric(String key) {
                PythonLanguage.getLogger().log(Level.FINE, "read of Python struct native member " + key);
            }

            protected boolean eq(String expected, String actual) {
                return expected.equals(actual);
            }

            public static ReadNativeMemberNode create() {
                return NativeWrappersFactory.PythonNativeWrapperFactory.ReadNativeMemberNodeGen.create();
            }

            private HashingStorageNodes.GetItemNode getGetItemNode() {
                if (getItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getItemNode = insert(HashingStorageNodes.GetItemNode.create());
                }
                return getItemNode;
            }

            private CExtNodes.ToSulongNode getToSulongNode() {
                if (toSulongNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    toSulongNode = insert(CExtNodes.ToSulongNode.create());
                }
                return toSulongNode;
            }

            private int sizeofWchar() {
                if (sizeofWcharNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    sizeofWcharNode = insert(CExtNodes.SizeofWCharNode.create());
                }
                return (int) sizeofWcharNode.execute();
            }

            private PythonClass getClass(Object obj) {
                if (getClassNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getClassNode = insert(GetClassNode.create());
                }
                return getClassNode.execute(obj);
            }

            private static Object getSliceComponent(int sliceComponent) {
                if (sliceComponent == PSlice.MISSING_INDEX) {
                    return PNone.NONE;
                }
                return sliceComponent;
            }

            protected Node createReadNode() {
                return Message.READ.createNode();
            }
        }

        // WRITE
        abstract static class WriteNode extends Node {
            public abstract Object execute(PythonNativeWrapper object, String key, Object value);

            @Specialization
            public Object execute(PythonNativeWrapper object, String key, Object value,
                            @Cached.Exclusive @Cached WriteNativeMemberNode writeNativeMemberNode) {
                return writeNativeMemberNode.execute(object.getDelegate(), key, value);
            }
        }

        @ImportStatic({NativeMemberNames.class, PGuards.class, SpecialMethodNames.class})
        abstract static class WriteNativeMemberNode extends PNodeWithContext {
            @Child private HashingStorageNodes.SetItemNode setItemNode;

            abstract Object execute(Object receiver, String key, Object value);

            @Specialization(guards = "eq(OB_TYPE, key)")
            Object doObType(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") PythonClass value,
                            @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
                // At this point, we do not support changing the type of an object.
                return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
            }

            @Specialization(guards = "eq(TP_FLAGS, key)")
            long doTpFlags(PythonClass object, @SuppressWarnings("unused") String key, long flags) {
                object.setFlags(flags);
                return flags;
            }

            @Specialization(guards = {"eq(TP_BASICSIZE, key)", "isPythonBuiltinClass(object)"})
            @TruffleBoundary
            long doTpBasicsize(PythonBuiltinClass object, @SuppressWarnings("unused") String key, long basicsize) {
                // We have to use the 'setAttributeUnsafe' because this properly cannot be modified
                // by
                // the user and we need to initialize it.
                object.setAttributeUnsafe(SpecialAttributeNames.__BASICSIZE__, basicsize);
                return basicsize;
            }

            @Specialization(guards = {"eq(TP_BASICSIZE, key)", "isPythonUserClass(object)"})
            @TruffleBoundary
            long doTpBasicsize(PythonClass object, @SuppressWarnings("unused") String key, long basicsize) {
                // Do deliberately not use "SetAttributeNode" because we want to directly set the
                // attribute an bypass any user code.
                object.setAttribute(SpecialAttributeNames.__BASICSIZE__, basicsize);
                return basicsize;
            }

            @Specialization(guards = "eq(TP_SUBCLASSES, key)")
            @TruffleBoundary
            Object doTpSubclasses(PythonClass object, @SuppressWarnings("unused") String key, PythonObjectNativeWrapper value) {
                // TODO more type checking; do fast path
                PDict dict = (PDict) value.getPythonObject();
                for (Object item : dict.items()) {
                    object.getSubClasses().add((PythonClass) item);
                }
                return value;
            }

            @Specialization(guards = "eq(MD_DEF, key)")
            Object doMdDef(PythonObject object, @SuppressWarnings("unused") String key, Object value) {
                DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
                assert nativeWrapper != null;
                getSetItemNode().execute(nativeWrapper.createNativeMemberStore(object.getDictUnsetOrSameAsStorageAssumption()), MD_DEF, value);
                return value;
            }

            @Specialization(guards = "eq(TP_DICT, key)")
            Object doTpDict(PythonClass object, @SuppressWarnings("unused") String key, Object nativeValue,
                            @Cached("create()") CExtNodes.AsPythonObjectNode asPythonObjectNode,
                            @Cached("create()") HashingStorageNodes.GetItemNode getItem,
                            @Cached("create()") WriteAttributeToObjectNode writeAttrNode,
                            @Cached("create()") IsBuiltinClassProfile isPrimitiveDictProfile) {
                Object value = asPythonObjectNode.execute(nativeValue);
                if (value instanceof PDict && isPrimitiveDictProfile.profileObject((PDict) value, PythonBuiltinClassType.PDict)) {
                    // special and fast case: commit items and change store
                    PDict d = (PDict) value;
                    for (Object k : d.keys()) {
                        writeAttrNode.execute(object, k, getItem.execute(d.getDictStorage(), k));
                    }
                    PHashingCollection existing = object.getDict();
                    if (existing != null) {
                        d.setDictStorage(existing.getDictStorage());
                    } else {
                        d.setDictStorage(new DynamicObjectStorage.PythonObjectDictStorage(object.getStorage(), object.getDictUnsetOrSameAsStorageAssumption()));
                    }
                    object.setDict(d);
                } else {
                    // TODO custom mapping object
                }
                return value;
            }

            @Specialization(guards = "eq(TP_DICTOFFSET, key)")
            Object doTpDictoffset(PythonClass object, @SuppressWarnings("unused") String key, Object value,
                            @Cached("create()") CastToIntegerFromIntNode castToIntNode,
                            @Cached("create(__SETATTR__)") LookupAndCallTernaryNode call) {
                // TODO properly implement 'tp_dictoffset' for builtin classes
                if (object instanceof PythonBuiltinClass) {
                    return 0L;
                }
                call.execute(object, __DICTOFFSET__, castToIntNode.execute(value));
                return value;
            }

            @Specialization
            Object doMemoryview(PMemoryView object, String key, Object value,
                            @Cached("create()") ReadAttributeFromObjectNode readAttrNode,
                            @Cached("createWriteNode()") Node writeNode,
                            @Cached("createBinaryProfile()") ConditionProfile isNativeObject) {
                Object delegateObj = readAttrNode.execute(object, "__c_memoryview");
                if (isNativeObject.profile(delegateObj instanceof PythonNativeObject)) {
                    try {
                        return ForeignAccess.sendWrite(writeNode, ((PythonNativeObject) delegateObj).object, key, value);
                    } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                        throw e.raise();
                    }
                }
                throw new IllegalStateException("delegate of memoryview object is not native");
            }

            @Fallback
            Object doGeneric(Object object, String key, Object value) {
                // This is the preliminary generic case: There are native members we know that they
                // exist but we do currently not represent them. So, store them into a dynamic
                // object
                // such that native code at least reads the value that was written before.
                if (object instanceof PythonAbstractObject) {
                    DynamicObjectNativeWrapper nativeWrapper = ((PythonAbstractObject) object).getNativeWrapper();
                    assert nativeWrapper != null;
                    logGeneric(key);
                    getSetItemNode().execute(nativeWrapper.createNativeMemberStore(), key, value);
                    return value;
                }
                throw UnknownIdentifierException.raise(key);
            }

            @TruffleBoundary(allowInlining = true)
            private static void logGeneric(String key) {
                PythonLanguage.getLogger().log(Level.FINE, "write of Python struct native member " + key);
            }

            protected boolean eq(String expected, String actual) {
                return expected.equals(actual);
            }

            private HashingStorageNodes.SetItemNode getSetItemNode() {
                if (setItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setItemNode = insert(HashingStorageNodes.SetItemNode.create());
                }
                return setItemNode;
            }

            protected Node createWriteNode() {
                return Message.WRITE.createNode();
            }

            public static WriteNativeMemberNode create() {
                return NativeWrappersFactory.PythonNativeWrapperFactory.WriteNativeMemberNodeGen.create();
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
        protected boolean isMemberInsertable(String member) {
            // TODO: cbasca, fangerer is this true ?
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
        protected void writeMember(String member, Object value,
                        @Cached.Exclusive @Cached(allowUncached = true) WriteNode writeNode) {
            writeNode.execute(this, member, value);
        }

        @ExportMessage
        protected boolean isMemberRemovable(String member) {
            return false;
        }

        @ExportMessage
        protected void removeMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            throw UnsupportedMessageException.create();
        }

        // EXECUTE
        abstract static class ExecuteNode extends Node {
            public abstract Object execute(PythonNativeWrapper object, Object[] arguments);

            @Specialization
            public Object execute(PythonNativeWrapper object, Object[] arguments,
                            @Cached.Exclusive @Cached PythonMessageResolution.ExecuteNode executeNode,
                            @Cached.Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                            @Cached.Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) {
                // convert args
                Object[] converted = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    converted[i] = toJavaNode.execute(arguments[i]);
                }
                Object result;
                try {
                    result = executeNode.execute(object.getDelegate(), converted);
                } catch (PException e) {
                    result = PNone.NO_VALUE;
                }
                return toSulongNode.execute(result);
            }
        }

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @Cached.Exclusive @Cached(allowUncached = true) ExecuteNode executeNode) {
            return executeNode.execute(this, arguments);
        }

        // TO NATIVE, IS POINTER, AS POINTER
        abstract static class ToNativeNode extends Node {
            public abstract Object execute(PythonNativeWrapper obj);

            protected boolean isClassInitNativeWrapper(PythonNativeWrapper obj) {
                return obj instanceof PythonClassInitNativeWrapper;
            }

            @Specialization
            public Object executeClsInit(PythonClassInitNativeWrapper obj,
                            @Cached.Shared("toPyObjectNode") @Cached ToPyObjectNode toPyObjectNode,
                            @Cached.Shared("invalidateNode") @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
                invalidateNode.execute();
                if (!obj.isNative()) {
                    obj.setNativePointer(toPyObjectNode.execute(obj));
                }
                return obj;
            }

            @Specialization(guards = "!isClassInitNativeWrapper(obj)")
            public Object execute(PythonNativeWrapper obj,
                            @Cached.Shared("toPyObjectNode") @Cached ToPyObjectNode toPyObjectNode,
                            @Cached.Shared("invalidateNode") @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
                invalidateNode.execute();
                if (!obj.isNative()) {
                    obj.setNativePointer(toPyObjectNode.execute(obj));
                }
                return obj;
            }
        }

        abstract static class IsPointerNode extends Node {
            public abstract boolean execute(PythonNativeWrapper obj);

            @Specialization
            public boolean execute(PythonNativeWrapper obj,
                            @Cached.Exclusive @Cached CExtNodes.IsPointerNode pIsPointerNode) {
                return pIsPointerNode.execute(obj);
            }
        }

        abstract static class InvalidateNativeObjectsAllManagedNode extends PNodeWithContext {

            public abstract void execute();

            @Specialization(assumptions = {"singleContextAssumption()", "nativeObjectsAllManagedAssumption()"})
            void doValid() {
                nativeObjectsAllManagedAssumption().invalidate();
            }

            @Specialization
            void doInvalid() {
            }

            protected Assumption nativeObjectsAllManagedAssumption() {
                return getContext().getNativeObjectsAllManagedAssumption();
            }

            public static InvalidateNativeObjectsAllManagedNode create() {
                return NativeWrappersFactory.PythonNativeWrapperFactory.InvalidateNativeObjectsAllManagedNodeGen.create();
            }
        }

        abstract static class AsPointerNode extends Node {
            public abstract long execute(PythonNativeWrapper obj);

            @Specialization
            long execute(PythonNativeWrapper obj,
                            @Cached.Exclusive @Cached PAsPointerNode pAsPointerNode) {
                return pAsPointerNode.execute(obj);
            }
        }

        abstract static class PAsPointerNode extends PNodeWithContext {
            @Child private Node asPointerNode;

            public abstract long execute(PythonNativeWrapper o);

            @Specialization(guards = {"obj.isBool()", "!obj.isNative()"})
            long doBoolNotNative(PrimitiveNativeWrapper obj,
                            @Cached("create()") CExtNodes.MaterializeDelegateNode materializeNode) {
                // special case for True and False singletons
                PInt boxed = (PInt) materializeNode.execute(obj);
                assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
                return doFast(obj);
            }

            @Specialization(guards = {"obj.isBool()", "obj.isNative()"})
            long doBoolNative(PrimitiveNativeWrapper obj) {
                return doFast(obj);
            }

            @Specialization(guards = "!isBoolNativeWrapper(obj)")
            long doFast(PythonNativeWrapper obj) {
                // the native pointer object must either be a TruffleObject or a primitive
                return ensureLong(obj.getNativePointer());
            }

            private long ensureLong(Object nativePointer) {
                if (nativePointer instanceof Long) {
                    return (long) nativePointer;
                } else {
                    if (asPointerNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        asPointerNode = insert(Message.AS_POINTER.createNode());
                    }
                    try {
                        return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw e.raise();
                    }
                }
            }

            protected static boolean isBoolNativeWrapper(Object obj) {
                return obj instanceof PrimitiveNativeWrapper && ((PrimitiveNativeWrapper) obj).isBool();
            }

            public static PAsPointerNode create() {
                return NativeWrappersFactory.PythonNativeWrapperFactory.PAsPointerNodeGen.create();
            }
        }

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
                assert !(wrapper instanceof PythonClassNativeWrapper) || wrapper.getDelegate() instanceof PythonClass;
                return !(wrapper.getDelegate() instanceof PythonNativeClass);
            }

            public static ToPyObjectNode create() {
                return NativeWrappersFactory.PythonNativeWrapperFactory.ToPyObjectNodeGen.create();
            }
        }

        @ExportMessage
        protected boolean isPointer(@Cached.Exclusive @Cached(allowUncached = true) IsPointerNode isPointerNode) {
            return isPointerNode.execute(this);
        }

        @ExportMessage
        protected long asPointer(@Cached.Exclusive @Cached(allowUncached = true) AsPointerNode asPointerNode) {
            return asPointerNode.execute(this);
        }

        @ExportMessage
        protected void toNative(@Cached.Exclusive @Cached(allowUncached = true) ToNativeNode toNativeNode) {
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

    public abstract static class DynamicObjectNativeWrapper extends PythonNativeWrapper {
        private static final Layout OBJECT_LAYOUT = Layout.newLayout().build();
        private static final Shape SHAPE = OBJECT_LAYOUT.createShape(new ObjectType());

        private PythonObjectDictStorage nativeMemberStore;

        public DynamicObjectNativeWrapper() {
        }

        public DynamicObjectNativeWrapper(Object delegate) {
            super(delegate);
        }

        public PythonObjectDictStorage createNativeMemberStore() {
            return createNativeMemberStore(null);
        }

        public PythonObjectDictStorage createNativeMemberStore(Assumption dictStableAssumption) {
            if (nativeMemberStore == null) {
                nativeMemberStore = new PythonObjectDictStorage(SHAPE.newInstance(), dictStableAssumption);
            }
            return nativeMemberStore;
        }

        public PythonObjectDictStorage getNativeMemberStore() {
            return nativeMemberStore;
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

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
        }

        @ExportMessage
        @Override
        protected boolean isMemberReadable(String member) {
            return member.equals(GP_OBJECT) || NativeMemberNames.isValid(member);
        }

        @ExportMessage
        @Override
        public boolean isMemberModifiable(String member) {
            return NativeMemberNames.isValid(member);
        }
    }

    public static class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

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
    }

    /**
     * Used to wrap {@link PythonClass} when used in native code. This wrapper mimics the correct
     * shape of the corresponding native type {@code struct _typeobject}.
     */
    public static class PythonClassNativeWrapper extends PythonObjectNativeWrapper {
        private final CStringWrapper nameWrapper;
        private Object getBufferProc;
        private Object releaseBufferProc;

        public PythonClassNativeWrapper(PythonClass object) {
            super(object);
            this.nameWrapper = new CStringWrapper(object.getName());
        }

        public CStringWrapper getNameWrapper() {
            return nameWrapper;
        }

        public Object getGetBufferProc() {
            return getBufferProc;
        }

        public void setGetBufferProc(Object getBufferProc) {
            this.getBufferProc = getBufferProc;
        }

        public Object getReleaseBufferProc() {
            return releaseBufferProc;
        }

        public void setReleaseBufferProc(Object releaseBufferProc) {
            this.releaseBufferProc = releaseBufferProc;
        }

        public static PythonClassNativeWrapper wrap(PythonClass obj) {
            // important: native wrappers are cached
            PythonClassNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (nativeWrapper == null) {
                nativeWrapper = new PythonClassNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            return String.format("PythonClassNativeWrapper(%s, isNative=%s)", getPythonObject(), isNative());
        }
    }

    /**
     * Used to wrap {@link PythonClass} just for the time when a natively defined type is processed
     * in {@code PyType_Ready} and we need to pass the mirroring managed class to native to marry
     * these two objects.
     */
    public static class PythonClassInitNativeWrapper extends PythonObjectNativeWrapper {

        public PythonClassInitNativeWrapper(PythonClass object) {
            super(object);
        }

        @Override
        public String toString() {
            return String.format("PythonClassNativeInitWrapper(%s, isNative=%s)", getPythonObject(), isNative());
        }
    }

    public static class TruffleObjectNativeWrapper extends PythonNativeWrapper {

        public TruffleObjectNativeWrapper(TruffleObject foreignObject) {
            super(foreignObject);
        }

        public static TruffleObjectNativeWrapper wrap(TruffleObject foreignObject) {
            assert !(foreignObject instanceof PythonNativeWrapper) : "attempting to wrap a native wrapper";
            return new TruffleObjectNativeWrapper(foreignObject);
        }
    }
}
