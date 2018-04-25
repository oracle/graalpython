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
package com.oracle.graal.python.builtins.objects.cpyobject;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cpyobject.PythonObjectNativeWrapperMRFactory.ReadNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cpyobject.PythonObjectNativeWrapperMRFactory.ToPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cpyobject.PythonObjectNativeWrapperMRFactory.WriteNativeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ValueProfile;

@MessageResolution(receiverType = PythonObjectNativeWrapper.class)
public class PythonObjectNativeWrapperMR {

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadNativeMemberNode readNativeMemberNode;

        public Object access(Object object, Object key) {
            if (readNativeMemberNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeMemberNode = insert(ReadNativeMemberNode.create());
            }
            return readNativeMemberNode.execute(((PythonObjectNativeWrapper) object).getPythonObject(), key);
        }
    }

    @ImportStatic(NativeMemberNames.class)
    abstract static class ReadNativeMemberNode extends Node {
        @Child GetClassNode getClass = GetClassNode.create();
        @Child LookupAndCallUnaryNode callLenNode = LookupAndCallUnaryNode.create(SpecialMethodNames.__LEN__);

        abstract Object execute(Object receiver, Object key);

        @Specialization(guards = "eq(OB_BASE, key)")
        PythonObject doObBase(PythonObject o, @SuppressWarnings("unused") String key) {
            return o;
        }

        @Specialization(guards = "eq(OB_REFCNT, key)")
        int doObRefcnt(@SuppressWarnings("unused") PythonObject o, @SuppressWarnings("unused") String key) {
            return 0;
        }

        @Specialization(guards = "eq(OB_TYPE, key)")
        PythonClass doObType(PythonObject object, @SuppressWarnings("unused") String key) {
            return getClass.execute(object);
        }

        @Specialization(guards = "eq(OB_SIZE, key)")
        int doObSize(PythonObject object, @SuppressWarnings("unused") String key) {
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
                return ((PBytes) profiled).getInternalByteArray();
            }
            throw UnsupportedMessageException.raise(Message.READ);
        }

        @Specialization(guards = "eq(TP_FLAGS, key)")
        long doTpFlags(PythonClass object, @SuppressWarnings("unused") String key) {
            return object.getFlags();
        }

        @Specialization(guards = "eq(TP_NAME, key)")
        String doTpName(PythonClass object, @SuppressWarnings("unused") String key) {
            return object.getName();
        }

        @Specialization(guards = "eq(TP_BASE, key)")
        PythonClass doTpBase(PythonClass object, @SuppressWarnings("unused") String key) {
            PythonClass superClass = object.getSuperClass();
            if (superClass != null) {
                return superClass;
            }
            return object;
        }

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        public static ReadNativeMemberNode create() {
            return ReadNativeMemberNodeGen.create();
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

    @ImportStatic(NativeMemberNames.class)
    abstract static class WriteNativeMemberNode extends Node {

        abstract Object execute(Object receiver, Object key, Object value);

        @Specialization(guards = "eq(OB_TYPE, key)")
        Object doObType(PythonObject object, @SuppressWarnings("unused") String key, @SuppressWarnings("unused") PythonClass value) {
            // At this point, we do not support changing the type of an object.
            return object;
        }

        @Specialization(guards = "eq(TP_FLAGS, key)")
        long doTpFlags(PythonClass object, @SuppressWarnings("unused") String key, long flags) {
            object.setFlags(flags);
            return flags;
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

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {
        public int access(Object object, Object fieldName) {
            assert object instanceof PythonObjectNativeWrapper;
            int info = KeyInfo.NONE;
            if (fieldName instanceof String && NativeMemberNames.isValid((String) fieldName)) {
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
        public Object access(@SuppressWarnings("unused") Object object) {
            return null;
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode = ToPyObjectNodeGen.create();

        Object access(PythonObjectNativeWrapper obj) {
            assert !obj.isNative();
            Object ptr = toPyObjectNode.execute(obj.getPythonObject());
            obj.setNativePointer(ptr);
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

    abstract static class ToPyObjectNode extends PBaseNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaType;
        @CompilationFinal private TruffleObject PyNoneHandle;
        @Child private Node executeNode;
        @Child private Node isPointerNode;
        @Child private Node toNativeNode;

        public abstract Object execute(PythonAbstractObject value);

        @Specialization
        Object runNativeClass(PythonNativeClass object) {
            return ensureIsPointer(object.object);
        }

        @Specialization
        Object runNativeObject(PythonNativeObject object) {
            return ensureIsPointer(object.object);
        }

        private TruffleObject getPyNoneHandle() {
            if (PyNoneHandle == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyNoneHandle = (TruffleObject) getContext().getEnv().importSymbol("PyNoneHandle");
            }
            return PyNoneHandle;
        }

        @Specialization
        Object runNone(PNone object) {
            return ensureIsPointer(callIntoCapi(object, getPyNoneHandle()));
        }

        @Specialization(guards = "isNonNative(object)")
        Object runClass(PythonClass object) {
            return ensureIsPointer(callIntoCapi(object, getPyObjectHandle_ForJavaType()));
        }

        @Fallback
        Object runObject(PythonAbstractObject object) {
            return ensureIsPointer(callIntoCapi(object, getPyObjectHandle_ForJavaObject()));
        }

        private TruffleObject getPyObjectHandle_ForJavaType() {
            if (PyObjectHandle_FromJavaType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaType = (TruffleObject) getContext().getEnv().importSymbol("PyObjectHandle_ForJavaType");
            }
            return PyObjectHandle_FromJavaType;
        }

        private TruffleObject getPyObjectHandle_ForJavaObject() {
            if (PyObjectHandle_FromJavaObject == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaObject = (TruffleObject) getContext().getEnv().importSymbol("PyObjectHandle_ForJavaObject");
            }
            return PyObjectHandle_FromJavaObject;
        }

        private Node getExecuteNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.createExecute(1).createNode());
            }
            return executeNode;
        }

        private Object ensureIsPointer(Object value) {
            if (value instanceof TruffleObject) {
                TruffleObject truffleObject = (TruffleObject) value;
                if (isPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isPointerNode = insert(Message.IS_POINTER.createNode());
                }
                if (!ForeignAccess.sendIsPointer(isPointerNode, truffleObject)) {
                    if (toNativeNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        toNativeNode = insert(Message.TO_NATIVE.createNode());
                    }
                    try {
                        return ForeignAccess.sendToNative(toNativeNode, truffleObject);
                    } catch (UnsupportedMessageException e) {
                        throw e.raise();
                    }
                }
            }
            return value;
        }

        protected boolean isNonNative(PythonClass klass) {
            return !(klass instanceof PythonNativeClass);
        }

        private Object callIntoCapi(PythonAbstractObject object, TruffleObject func) {
            try {
                return ForeignAccess.sendExecute(getExecuteNode(), func, object);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }
    }

}
