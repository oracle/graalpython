/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.ReadNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.ToPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.WriteNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ValueProfile;

@MessageResolution(receiverType = PythonObjectNativeWrapper.class)
public class PythonObjectNativeWrapperMR {
    protected static String GP_OBJECT = "gp_object";

    @Resolve(message = "com.oracle.truffle.llvm.runtime.interop.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child GetClassNode getClass = GetClassNode.create();

        public Object access(PythonObjectNativeWrapper object) {
            PythonClass klass = getClass.execute(object.getPythonObject());
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

        public Object access(Object object, Object key) {
            if (key.equals(GP_OBJECT)) {
                return ((PythonObjectNativeWrapper) object).getPythonObject();
            }
            if (readNativeMemberNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeMemberNode = insert(ReadNativeMemberNode.create());
            }
            return readNativeMemberNode.execute(((PythonObjectNativeWrapper) object).getPythonObject(), key);
        }
    }

    @ImportStatic({NativeMemberNames.class, SpecialMethodNames.class})
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ReadNativeMemberNode extends PBaseNode {
        @Child GetClassNode getClass = GetClassNode.create();
        @Child private ToSulongNode toSulongNode;

        @CompilationFinal long wcharSize = -1;

        abstract Object execute(Object receiver, Object key);

        @Specialization(guards = "eq(OB_BASE, key)")
        Object doObBase(PythonAbstractObject o, @SuppressWarnings("unused") String key) {
            return getToSulongNode().execute(o);
        }

        @Specialization(guards = "eq(_BASE, key)")
        Object doObBase(PString o, @SuppressWarnings("unused") String key) {
            return getToSulongNode().execute(o);
        }

        @Specialization(guards = "eq(_BASE, key)")
        Object doObBase(String o, @SuppressWarnings("unused") String key) {
            return getToSulongNode().execute(o);
        }

        @Specialization(guards = "eq(OB_REFCNT, key)")
        int doObRefcnt(@SuppressWarnings("unused") PythonAbstractObject o, @SuppressWarnings("unused") String key) {
            return 0;
        }

        @Specialization(guards = "eq(OB_TYPE, key)")
        Object doObType(PythonAbstractObject object, @SuppressWarnings("unused") String key) {
            return getToSulongNode().execute(getClass.execute(object));
        }

        @Specialization(guards = "eq(OB_SIZE, key)")
        long doObSize(PythonObject object, @SuppressWarnings("unused") String key,
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
        Object doObSval(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached("createClassProfile()") ValueProfile profile) {
            Object profiled = profile.profile(object);
            if (profiled instanceof PBytes) {
                return new PySequenceArrayWrapper(profiled);
            }
            throw UnsupportedMessageException.raise(Message.READ);
        }

        @Specialization(guards = "eq(OB_FVAL, key)")
        Object doObFval(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached("createClassProfile()") ValueProfile profile) {
            Object profiled = profile.profile(object);
            if (profiled instanceof PFloat) {
                return ((PFloat) profiled).getValue();
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
                return PythonObjectNativeWrapper.wrap(superClass);
            }
            return getToSulongNode().execute(object);
        }

        @Specialization(guards = "eq(TP_ALLOC, key)")
        Object doTpAlloc(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached("create()") LookupAttributeInMRONode getAllocNode) {
            Object result = getAllocNode.execute(object, SpecialMethodNames.__ALLOC__);
            return getToSulongNode().execute(result);
        }

        @Specialization(guards = "eq(TP_AS_NUMBER, key)")
        Object doTpAsNumber(PythonClass object, @SuppressWarnings("unused") String key) {
            // TODO check for type and return 'NULL'
            return new PyNumberMethodsWrapper(object);
        }

        @Specialization(guards = "eq(TP_AS_BUFFER, key)")
        Object doTpAsBuffer(PythonClass object, @SuppressWarnings("unused") String key) {
            if (object == getCore().lookupType(PBytes.class) || object == getCore().lookupType(PByteArray.class) || object == getCore().lookupType(PBuffer.class)) {
                return new PyBufferProcsWrapper(object);
            }

            // NULL pointer
            return getToSulongNode().execute(PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(TP_NEW, key)")
        Object doTpNew(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached("create()") LookupAttributeInMRONode getAttrNode) {
            return ManagedMethodWrappers.createKeywords(getAttrNode.execute(object, SpecialAttributeNames.__NEW__));
        }

        @Specialization(guards = "eq(TP_HASH, key)")
        Object doTpHash(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached("create()") LookupInheritedAttributeNode getHashNode) {
            return getToSulongNode().execute(getHashNode.execute(object, SpecialMethodNames.__HASH__));
        }

        @Specialization(guards = "eq(TP_BASICSIZE, key)")
        Object doTpBasicsize(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached("create()") LookupInheritedAttributeNode getAttrNode) {
            return getAttrNode.execute(object, SpecialAttributeNames.__BASICSIZE__);
        }

        @Specialization(guards = "eq(TP_RICHCOMPARE, key)")
        Object doTpRichcompare(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached("create()") LookupInheritedAttributeNode getCmpNode) {
            return getToSulongNode().execute(getCmpNode.execute(object, SpecialMethodNames.RICHCMP));
        }

        @Specialization(guards = "eq(TP_SUBCLASSES, key)")
        Object doTpSubclasses(@SuppressWarnings("unused") PythonClass object, @SuppressWarnings("unused") String key) {
            // TODO create dict view on subclasses set
            return PythonObjectNativeWrapper.wrap(factory().createDict());
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
                        @Cached("create()") LookupInheritedAttributeNode lookupAttrNode) {
            return PyAttributeProcsWrapper.createGetAttrWrapper(lookupAttrNode.execute(object, __GETATTRIBUTE__));
        }

        @Specialization(guards = "eq(TP_SETATTRO, key)")
        Object doTpSetattro(PythonClass object, @SuppressWarnings("unused") String key,
                        @Cached("create()") LookupInheritedAttributeNode lookupAttrNode) {
            return PyAttributeProcsWrapper.createSetAttrWrapper(lookupAttrNode.execute(object, __SETATTR__));
        }

        @Specialization(guards = "eq(OB_ITEM, key)")
        Object doObItem(PSequence object, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object);
        }

        @Specialization(guards = "eq(UNICODE_WSTR, key)")
        Object doWstr(String object, @SuppressWarnings("unused") String key,
                        @Cached("create()") UnicodeAsWideCharNode asWideCharNode) {
            return new PySequenceArrayWrapper(asWideCharNode.execute(object, sizeofWchar(), object.length()));
        }

        @Specialization(guards = "eq(UNICODE_WSTR_LENGTH, key)")
        long doWstrLength(String object, @SuppressWarnings("unused") String key,
                        @Cached("create()") UnicodeAsWideCharNode asWideCharNode) {
            long sizeofWchar = sizeofWchar();
            PBytes result = asWideCharNode.execute(object, sizeofWchar, object.length());
            return result.len() / sizeofWchar;
        }

        @Specialization(guards = "eq(UNICODE_STATE, key)")
        Object doState(PString object, @SuppressWarnings("unused") String key) {
            // TODO also support bare 'String' ?
            return new PyUnicodeState(object);
        }

        @Specialization(guards = "eq(MD_DICT, key)")
        Object doMdDict(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached("create()") GetAttributeNode getDictNode) {
            return getToSulongNode().execute(getDictNode.execute(object, SpecialAttributeNames.__DICT__));
        }

        @Specialization(guards = "eq(BUF_DELEGATE, key)")
        Object doObSval(PBuffer object, @SuppressWarnings("unused") String key) {
            return new PySequenceArrayWrapper(object.getDelegate());
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object receiver, Object key) {
            throw UnknownIdentifierException.raise(key.toString());
        }

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        public static ReadNativeMemberNode create() {
            return ReadNativeMemberNodeGen.create();
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        private long sizeofWchar() {
            if (wcharSize < 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                TruffleObject boxed = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_WHCAR_SIZE);
                try {
                    wcharSize = (long) ForeignAccess.sendExecute(Message.createExecute(0).createNode(), boxed);
                    assert wcharSize >= 0L;
                } catch (InteropException e) {
                    throw e.raise();
                }
            }
            return wcharSize;
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private WriteNativeMemberNode readNativeMemberNode;

        public Object access(Object object, Object key, Object value) {
            if (readNativeMemberNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeMemberNode = insert(WriteNativeMemberNode.create());
            }
            return readNativeMemberNode.execute(((PythonObjectNativeWrapper) object).getPythonObject(), key, value);
        }
    }

    @ImportStatic({NativeMemberNames.class, PGuards.class})
    abstract static class WriteNativeMemberNode extends Node {

        abstract Object execute(Object receiver, Object key, Object value);

        @Specialization(guards = "eq(OB_TYPE, key)")
        Object doObType(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") PythonClass value) {
            // At this point, we do not support changing the type of an object.
            return PythonObjectNativeWrapper.wrap(object);
        }

        @Specialization(guards = "eq(TP_FLAGS, key)")
        long doTpFlags(PythonClass object, @SuppressWarnings("unused") String key, long flags) {
            object.setFlags(flags);
            return flags;
        }

        @Specialization(guards = {"eq(TP_BASICSIZE, key)", "isPythonBuiltinClass(object)"})
        long doTpBasicsize(PythonBuiltinClass object, @SuppressWarnings("unused") String key, long basicsize) {
            // We have to use the 'setAttributeUnsafe' because this properly cannot be modified by
            // the user and we need to initialize it.
            object.setAttributeUnsafe(SpecialAttributeNames.__BASICSIZE__, basicsize);
            return basicsize;
        }

        @Specialization(guards = {"eq(TP_BASICSIZE, key)", "isPythonUserClass(object)"})
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

        @Fallback
        Object doGeneric(Object object, Object key, @SuppressWarnings("unused") Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Cannot modify member '" + key + "' of " + object);
        }

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
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

        public Object access(PythonObjectNativeWrapper object, Object[] arguments) {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(new PythonMessageResolution.ExecuteNode());
            }
            // convert args
            Object[] converted = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                converted[i] = getToJavaNode().execute(arguments[i]);
            }
            return getToSulongNode().execute(executeNode.execute(object.getPythonObject(), converted));
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
            return obj instanceof PythonObjectNativeWrapper;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class PForeignKeysNode extends Node {
        @Child Node objKeys = Message.KEYS.createNode();

        public Object access(Object object) {
            if (object instanceof PythonObjectNativeWrapper) {
                return PythonLanguage.getContext().getEnv().asGuestValue(new String[]{GP_OBJECT});
            } else {
                throw UnsupportedMessageException.raise(Message.KEYS);
            }
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode = ToPyObjectNodeGen.create();

        Object access(PythonObjectNativeWrapper obj) {
            if (!obj.isNative()) {
                Object ptr = toPyObjectNode.execute(obj.getPythonObject());
                obj.setNativePointer(ptr);
            }
            return obj;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        Object access(PythonObjectNativeWrapper obj) {
            return obj.isNative();
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private Node asPointerNode;

        long access(PythonObjectNativeWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (nativePointer instanceof TruffleObject) {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
            return (long) nativePointer;

        }
    }

    abstract static class ToPyObjectNode extends TransformToNativeNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaType;
        @CompilationFinal private TruffleObject PyNoneHandle;
        @Child private PCallNativeNode callNativeUnary;
        @Child private PCallNativeNode callNativeBinary;
        @Child private GetClassNode getClassNode;

        public abstract Object execute(PythonAbstractObject value);

        @Specialization
        Object runNativeClass(PythonNativeClass object) {
            return ensureIsPointer(object.object);
        }

        @Specialization
        Object runNativeObject(PythonNativeObject object) {
            return ensureIsPointer(object.object);
        }

        @Specialization
        Object runNone(PNone object) {
            PythonClass clazz = getClassNode().execute(object);
            return ensureIsPointer(callBinaryIntoCapi(getPyObjectHandle_ForJavaObject(), object, clazz.getFlags()));
        }

        @Specialization(guards = "isNonNative(object)")
        Object runClass(PythonClass object) {
            return ensureIsPointer(callUnaryIntoCapi(object, getPyObjectHandle_ForJavaType()));
        }

        @Fallback
        Object runObject(PythonAbstractObject object) {
            PythonClass clazz = getClassNode().execute(object);
            return ensureIsPointer(callBinaryIntoCapi(getPyObjectHandle_ForJavaObject(), object, clazz.getFlags()));
        }

        private TruffleObject getPyObjectHandle_ForJavaType() {
            if (PyObjectHandle_FromJavaType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaType = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE);
            }
            return PyObjectHandle_FromJavaType;
        }

        private TruffleObject getPyObjectHandle_ForJavaObject() {
            if (PyObjectHandle_FromJavaObject == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaObject = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT);
            }
            return PyObjectHandle_FromJavaObject;
        }

        protected boolean isNonNative(PythonClass klass) {
            return !(klass instanceof PythonNativeClass);
        }

        private Object callUnaryIntoCapi(PythonAbstractObject arg, TruffleObject fun) {
            if (callNativeUnary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeUnary = insert(PCallNativeNode.create(1));
            }
            return callNativeUnary.execute(fun, new Object[]{arg});
        }

        private Object callBinaryIntoCapi(TruffleObject fun, Object arg0, Object arg1) {
            if (callNativeBinary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeBinary = insert(PCallNativeNode.create(1));
            }
            return callNativeBinary.execute(fun, new Object[]{arg0, arg1});
        }

        private GetClassNode getClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode;
        }
    }
}
