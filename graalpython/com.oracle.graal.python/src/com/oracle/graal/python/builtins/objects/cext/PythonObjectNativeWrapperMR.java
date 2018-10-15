/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MaterializeDelegateNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.BoolNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PyUnicodeData;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PyUnicodeState;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassInitNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.PAsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.PIsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.ReadNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.ToPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.WriteNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@MessageResolution(receiverType = PythonNativeWrapper.class)
public class PythonObjectNativeWrapperMR {
    private static final String GP_OBJECT = "gp_object";

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child GetClassNode getClass = GetClassNode.create();
        @Child AsPythonObjectNode getDelegate = AsPythonObjectNode.create();

        public Object access(PythonNativeWrapper object) {
            PythonClass klass = getClass.execute(getDelegate.execute(object));
            Object sulongType = klass.getSulongType();
            if (sulongType == null) {
                CompilerDirectives.transferToInterpreter();
                sulongType = findBuiltinClass(klass);
                if (sulongType == null) {
                    throw new IllegalStateException("sulong type for " + klass.getName() + " was not registered");
                }
            }
            return sulongType;
        }

        private static Object findBuiltinClass(PythonClass klass) {
            PythonClass[] mro = klass.getMethodResolutionOrder();
            Object sulongType = null;
            for (PythonClass superClass : mro) {
                sulongType = superClass.getSulongType();
                if (sulongType != null) {
                    klass.setSulongType(sulongType);
                    break;
                }
            }
            return sulongType;
        }
    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadNativeMemberNode readNativeMemberNode;
        @Child private AsPythonObjectNode getDelegate;

        @CompilationFinal private String cachedObBase;

        public Object access(PythonNativeWrapper object, String key) {
            // The very common case: directly return native wrapper.
            // This is in particular important for PrimitiveNativeWrappers, since they are not
            // cached.
            if (key == cachedObBase) {
                return object;
            } else if (cachedObBase == null && key.equals(NativeMemberNames.OB_BASE)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedObBase = key;
                return object;
            }

            if (getDelegate == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDelegate = insert(AsPythonObjectNode.create());
            }
            Object delegate = getDelegate.execute(object);

            // special key for the debugger
            if (key.equals(GP_OBJECT)) {
                return delegate;
            }
            if (readNativeMemberNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeMemberNode = insert(ReadNativeMemberNode.create());
            }
            return readNativeMemberNode.execute(delegate, key);
        }
    }

    @ImportStatic({NativeMemberNames.class, SpecialMethodNames.class, SpecialAttributeNames.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNativeMemberNode extends PNodeWithContext {
        @Child GetClassNode getClassNode;
        @Child private ToSulongNode toSulongNode;
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
        long doTpFlags(PythonClass object, @SuppressWarnings("unused") String key) {
            return object.getFlags();
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
                        @Cached("create(__BASICSIZE__)") LookupInheritedAttributeNode getAttrNode) {
            return getAttrNode.execute(object);
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
                        @Cached("create(0)") UnicodeAsWideCharNode asWideCharNode) {
            int elementSize = sizeofWchar();
            return new PySequenceArrayWrapper(asWideCharNode.execute(object, elementSize, object.len()), elementSize);
        }

        @Specialization(guards = "eq(UNICODE_WSTR_LENGTH, key)")
        long doWstrLength(PString object, @SuppressWarnings("unused") String key,
                        @Cached("create(0)") UnicodeAsWideCharNode asWideCharNode,
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
            return new PyUnicodeData(object);
        }

        @Specialization(guards = "eq(UNICODE_STATE, key)")
        Object doState(PString object, @SuppressWarnings("unused") String key) {
            // TODO also support bare 'String' ?
            return new PyUnicodeState(object);
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
                // If 'dict instanceof PMappingproxy', it seems that someone already used '__dict__'
                // on this type and created a mappingproxy object. We need to replace it by a dict.
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
            return getGetItemNode().execute(nativeWrapper.getNativeMemberStore(), NativeMemberNames.MD_DEF);
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
        int doStart(PSlice object, @SuppressWarnings("unused") String key) {
            return object.getStart();
        }

        @Specialization(guards = "eq(STOP, key)")
        int doStop(PSlice object, @SuppressWarnings("unused") String key) {
            return object.getStop();
        }

        @Specialization(guards = "eq(STEP, key)")
        int doStep(PSlice object, @SuppressWarnings("unused") String key) {
            return object.getStep();
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

        @Specialization
        Object doMemoryview(PMemoryView object, String key,
                        @Cached("create()") ReadAttributeFromObjectNode readAttrNode,
                        @Cached("createReadNode()") Node readNode,
                        @Cached("createBinaryProfile()") ConditionProfile isNativeObject) {
            Object delegateObj = readAttrNode.execute(object, "__c_memoryview");
            if (isNativeObject.profile(delegateObj instanceof PythonNativeObject)) {
                try {
                    return ForeignAccess.sendRead(readNode, (TruffleObject) ((PythonNativeObject) delegateObj).object, key);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    throw e.raise();
                }
            }
            throw new IllegalStateException("delegate of memoryview object is not native");
        }

        protected static boolean isPyDateTimeCAPI(PythonObject object) {
            return object.getLazyPythonClass().getName().equals("PyDateTime_CAPI");
        }

        @Specialization(guards = "isPyDateTimeCAPI(object)")
        Object doDatetimeCAPI(PythonObject object, String key,
                        @Cached("create()") GetLazyClassNode getClass,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic getAttrNode) {
            return getToSulongNode().execute(getAttrNode.execute(getClass.execute(object), key));
        }

        @Fallback
        Object doGeneric(Object object, String key) {
            // This is the preliminary generic case: There are native members we know that they
            // exist but we do currently not represent them. So, store them into a dynamic object
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
            return ReadNativeMemberNodeGen.create();
        }

        private HashingStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemNode.create());
            }
            return getItemNode;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
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

        protected Node createReadNode() {
            return Message.READ.createNode();
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private WriteNativeMemberNode writeNativeMemberNode;

        public Object access(PythonNativeWrapper object, String key, Object value) {
            if (writeNativeMemberNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNativeMemberNode = insert(WriteNativeMemberNode.create());
            }
            return writeNativeMemberNode.execute(object.getDelegate(), key, value);
        }
    }

    @ImportStatic({NativeMemberNames.class, PGuards.class})
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
            // We have to use the 'setAttributeUnsafe' because this properly cannot be modified by
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
            getSetItemNode().execute(nativeWrapper.createNativeMemberStore(), NativeMemberNames.MD_DEF, value);
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
                    d.setDictStorage(new DynamicObjectStorage.PythonObjectDictStorage(object.getStorage()));
                }
                object.setDict(d);
            } else {
                // TODO custom mapping object
            }
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
                    return ForeignAccess.sendWrite(writeNode, (TruffleObject) ((PythonNativeObject) delegateObj).object, key, value);
                } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                    throw e.raise();
                }
            }
            throw new IllegalStateException("delegate of memoryview object is not native");
        }

        @Fallback
        Object doGeneric(Object object, String key, Object value) {
            // This is the preliminary generic case: There are native members we know that they
            // exist but we do currently not represent them. So, store them into a dynamic object
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
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

        protected Node createWriteNode() {
            return Message.WRITE.createNode();
        }

        public static WriteNativeMemberNode create() {
            return WriteNativeMemberNodeGen.create();
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {
        @Child PythonMessageResolution.ExecuteNode executeNode;
        @Child private ToJavaNode toJavaNode;
        @Child private ToSulongNode toSulongNode;

        public Object access(PythonNativeWrapper object, Object[] arguments) {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(new PythonMessageResolution.ExecuteNode());
            }
            // convert args
            Object[] converted = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                converted[i] = getToJavaNode().execute(arguments[i]);
            }
            Object result;
            try {
                result = executeNode.execute(object.getDelegate(), converted);
            } catch (PException e) {
                result = PNone.NO_VALUE;
            }
            return getToSulongNode().execute(result);
        }

        private ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNode.create());
            }
            return toJavaNode;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {
        public int access(Object object, Object fieldName) {
            assert object instanceof PythonObjectNativeWrapper;
            int info = KeyInfo.NONE;
            if (fieldName.equals(GP_OBJECT)) {
                info |= KeyInfo.READABLE;
            } else if (fieldName instanceof String && NativeMemberNames.isValid((String) fieldName)) {
                info |= KeyInfo.READABLE;

                // TODO be more specific
                info |= KeyInfo.MODIFIABLE;
            }
            return info;
        }
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {
        public Object access(Object obj) {
            return obj instanceof PythonNativeWrapper;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class PForeignKeysNode extends Node {
        @Child Node objKeys = Message.KEYS.createNode();

        public Object access(Object object) {
            if (object instanceof PythonNativeWrapper) {
                return PythonLanguage.getContextRef().get().getEnv().asGuestValue(new String[]{GP_OBJECT});
            } else {
                throw UnsupportedMessageException.raise(Message.KEYS);
            }
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode;
        @Child private MaterializeDelegateNode materializeNode;
        @Child private PIsPointerNode pIsPointerNode = PIsPointerNode.create();

        Object access(PythonClassInitNativeWrapper obj) {
            if (!pIsPointerNode.execute(obj)) {
                obj.setNativePointer(getToPyObjectNode().getHandleForObject(getMaterializeDelegateNode().execute(obj)));
            }
            return obj;
        }

        Object access(PythonNativeWrapper obj) {
            assert !(obj instanceof PythonClassInitNativeWrapper);
            if (!pIsPointerNode.execute(obj)) {
                obj.setNativePointer(getToPyObjectNode().execute(getMaterializeDelegateNode().execute(obj)));
            }
            return obj;
        }

        private MaterializeDelegateNode getMaterializeDelegateNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeDelegateNode.create());
            }
            return materializeNode;
        }

        private ToPyObjectNode getToPyObjectNode() {
            if (toPyObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toPyObjectNode = insert(ToPyObjectNode.create());
            }
            return toPyObjectNode;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private PIsPointerNode pIsPointerNode = PIsPointerNode.create();

        boolean access(PythonNativeWrapper obj) {
            return pIsPointerNode.execute(obj);
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private PAsPointerNode pAsPointerNode = PAsPointerNode.create();

        long access(PythonNativeWrapper obj) {
            return pAsPointerNode.execute(obj);
        }
    }

    abstract static class PIsPointerNode extends PNodeWithContext {

        public abstract boolean execute(PythonNativeWrapper obj);

        @Specialization(guards = "!obj.isNative()")
        boolean doBool(BoolNativeWrapper obj) {
            // Special case: Booleans are singletons, so we need to check if the singletons have
            // native wrappers associated and if they are already native.
            PInt boxed = factory().createInt(obj.getValue());
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            return nativeWrapper != null && nativeWrapper.isNative();
        }

        @Fallback
        boolean doBool(PythonNativeWrapper obj) {
            return obj.isNative();
        }

        private static PIsPointerNode create() {
            return PIsPointerNodeGen.create();
        }
    }

    abstract static class PAsPointerNode extends PNodeWithContext {
        @Child private Node asPointerNode;

        public abstract long execute(PythonNativeWrapper o);

        @Specialization(assumptions = "getSingleNativeContextAssumption()", guards = "!obj.isNative()")
        long doBoolNotNative(BoolNativeWrapper obj,
                        @Cached("create()") MaterializeDelegateNode materializeNode) {
            // special case for True and False singletons
            PInt boxed = (PInt) materializeNode.execute(obj);
            assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
            return doFast(obj);
        }

        @Specialization(assumptions = "getSingleNativeContextAssumption()", guards = "obj.isNative()")
        long doBoolNative(BoolNativeWrapper obj) {
            return doFast(obj);
        }

        @Specialization(assumptions = "getSingleNativeContextAssumption()", guards = "!isBoolNativeWrapper(obj)")
        long doFast(PythonNativeWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            return ensureLong(obj.getNativePointer());
        }

        @Specialization(replaces = {"doFast", "doBoolNotNative", "doBoolNative"})
        long doGenericSlow(PythonNativeWrapper obj,
                        @Cached("create()") MaterializeDelegateNode materializeNode,
                        @Cached("create()") ToPyObjectNode toPyObjectNode) {
            Object materialized = materializeNode.execute(obj);
            return ensureLong(toPyObjectNode.execute(materialized));
        }

        private long ensureLong(Object nativePointer) {
            if (nativePointer instanceof TruffleObject) {
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
            return (long) nativePointer;
        }

        protected static boolean isBoolNativeWrapper(Object obj) {
            return obj instanceof BoolNativeWrapper;
        }

        protected Assumption getSingleNativeContextAssumption() {
            return PythonContext.getSingleNativeContextAssumption();
        }

        public static PAsPointerNode create() {
            return PAsPointerNodeGen.create();
        }
    }

    abstract static class PToNativeNode extends PNodeWithContext {
        @Child private ToPyObjectNode toPyObjectNode;
        @Child private MaterializeDelegateNode materializeNode;
        @Child private PIsPointerNode pIsPointerNode = PIsPointerNode.create();

        public abstract PythonNativeWrapper execute(PythonNativeWrapper obj);

        @Specialization
        PythonNativeWrapper doInitClass(PythonClassInitNativeWrapper obj) {
            if (!pIsPointerNode.execute(obj)) {
                obj.setNativePointer(getToPyObjectNode().getHandleForObject(getMaterializeDelegateNode().execute(obj)));
            }
            return obj;
        }

        @Specialization
        PythonNativeWrapper doBool(BoolNativeWrapper obj) {
            if (!pIsPointerNode.execute(obj)) {
                PInt materialized = (PInt) getMaterializeDelegateNode().execute(obj);
                obj.setNativePointer(getToPyObjectNode().execute(materialized));
                if (!materialized.getNativeWrapper().isNative()) {
                    assert materialized.getNativeWrapper() != obj;
                    materialized.getNativeWrapper().setNativePointer(obj.getNativePointer());
                }
                assert obj.getNativePointer() == materialized.getNativeWrapper().getNativePointer();
            }
            return obj;
        }

        @Fallback
        PythonNativeWrapper doGeneric(PythonNativeWrapper obj) {
            assert !(obj instanceof PythonClassInitNativeWrapper);
            if (!pIsPointerNode.execute(obj)) {
                obj.setNativePointer(getToPyObjectNode().execute(getMaterializeDelegateNode().execute(obj)));
            }
            return obj;
        }

        private MaterializeDelegateNode getMaterializeDelegateNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeDelegateNode.create());
            }
            return materializeNode;
        }

        private ToPyObjectNode getToPyObjectNode() {
            if (toPyObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toPyObjectNode = insert(ToPyObjectNode.create());
            }
            return toPyObjectNode;
        }
    }

    abstract static class ToPyObjectNode extends CExtBaseNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaType;
        @CompilationFinal private TruffleObject PyNoneHandle;
        @Child private PCallNativeNode callNativeUnary;
        @Child private PCallNativeNode callNativeBinary;
        @Child private CExtNodes.ToSulongNode toSulongNode;

        public abstract Object execute(PythonNativeWrapper wrapper, Object value);

        @Specialization(guards = "isManagedPythonClass(wrapper)")
        Object doClass(PythonClassNativeWrapper wrapper, PythonClass object) {
            return callUnaryIntoCapi(getPyObjectHandle_ForJavaType(), wrapper);
        }

        @Fallback
        Object doObject(PythonNativeWrapper wrapper, Object object) {
            return callUnaryIntoCapi(getPyObjectHandle_ForJavaObject(), wrapper);
        }

        private TruffleObject getPyObjectHandle_ForJavaType() {
            if (PyObjectHandle_FromJavaType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaType = importCAPISymbol(NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE);
            }
            return PyObjectHandle_FromJavaType;
        }

        private TruffleObject getPyObjectHandle_ForJavaObject() {
            if (PyObjectHandle_FromJavaObject == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaObject = importCAPISymbol(NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT);
            }
            return PyObjectHandle_FromJavaObject;
        }

        protected static boolean isManagedPythonClass(PythonClassNativeWrapper wrapper) {
            assert wrapper.getDelegate() instanceof PythonClass;
            return !(wrapper.getDelegate() instanceof PythonNativeClass);
        }

        private Object callUnaryIntoCapi(TruffleObject fun, Object arg) {
            if (callNativeUnary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeUnary = insert(PCallNativeNode.create());
            }
            return callNativeUnary.execute(fun, new Object[]{arg});
        }

        private CExtNodes.ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(CExtNodes.ToSulongNode.create());
            }
            return toSulongNode;
        }

        public static ToPyObjectNode create() {
            return ToPyObjectNodeGen.create();
        }
    }
}
