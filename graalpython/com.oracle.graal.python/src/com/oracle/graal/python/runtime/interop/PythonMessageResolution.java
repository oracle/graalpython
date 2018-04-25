/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.runtime.interop;

import static com.oracle.graal.python.nodes.SpecialPyObjectAttributes.pyobjectKey;

import java.util.Arrays;

import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cpyobject.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cpyobject.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ArityCheckNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.CastToListNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNodeGen;
import com.oracle.graal.python.nodes.interop.PTypeUnboxNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMessageResolutionFactory.ToPyObjectNodeGen;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@MessageResolution(receiverType = PythonObject.class)
public class PythonMessageResolution {
    private static final class IsSequence extends Node {
        @Child private LookupInheritedAttributeNode getGetItemNode = LookupInheritedAttributeNode.create();
        @Child private LookupInheritedAttributeNode getLenNode = LookupInheritedAttributeNode.create();
        final ConditionProfile lenProfile = ConditionProfile.createBinaryProfile();
        final ConditionProfile getItemProfile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            Object len = getLenNode.execute(object, SpecialMethodNames.__LEN__);
            if (lenProfile.profile(len != PNone.NO_VALUE)) {
                return getItemProfile.profile(getGetItemNode.execute(object, SpecialMethodNames.__GETITEM__) != PNone.NO_VALUE);
            }
            return false;
        }
    }

    private static final class HasSetItem extends Node {
        @Child private LookupInheritedAttributeNode getSetItemNode = LookupInheritedAttributeNode.create();
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            return profile.profile(getSetItemNode.execute(object, SpecialMethodNames.__SETITEM__) != PNone.NO_VALUE);
        }
    }

    private static final class HasDelItem extends Node {
        @Child private LookupInheritedAttributeNode getDelItemNode = LookupInheritedAttributeNode.create();
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            return profile.profile(getDelItemNode.execute(object, SpecialMethodNames.__DELITEM__) != PNone.NO_VALUE);
        }
    }

    private static final class IsImmutable extends Node {
        @Child private IsSequence isSequence = new IsSequence();
        @Child private HasSetItem hasSetItem = new HasSetItem();
        final ConditionProfile builtinProfile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            if (builtinProfile.profile(object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject)) {
                return true;
            }
            return isSequence.execute(object) && !hasSetItem.execute(object);
        }
    }

    private static final class IsMapping extends Node {
        @Child private LookupInheritedAttributeNode getKeysNode = LookupInheritedAttributeNode.create();
        @Child private LookupInheritedAttributeNode getItemsNode = LookupInheritedAttributeNode.create();
        @Child private LookupInheritedAttributeNode getValuesNode = LookupInheritedAttributeNode.create();
        @Child private IsSequence isSequence = new IsSequence();
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            if (isSequence.execute(object)) {
                return profile.profile((getKeysNode.execute(object, SpecialMethodNames.KEYS) != PNone.NO_VALUE) &&
                                (getItemsNode.execute(object, SpecialMethodNames.ITEMS) != PNone.NO_VALUE) &&
                                (getValuesNode.execute(object, SpecialMethodNames.VALUES) != PNone.NO_VALUE));
            }
            return false;
        }
    }

    private abstract static class KeyForForcedAccess extends Node {
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();
        final char prefix;

        KeyForForcedAccess(char prefix) {
            this.prefix = prefix;
        }

        public String execute(Object object) {
            if (object instanceof String) {
                if (profile.profile(((String) object).length() > 1 && ((String) object).charAt(0) == prefix)) {
                    return ((String) object).substring(1);
                }
            }
            return null;
        }
    }

    private static final class KeyForAttributeAccess extends KeyForForcedAccess {
        KeyForAttributeAccess() {
            super('@');
        }
    }

    private static final class KeyForItemAccess extends KeyForForcedAccess {
        KeyForItemAccess() {
            super('[');
        }
    }

    private static final class ReadNode extends Node {
        private static final Object NONEXISTING_IDENTIFIER = new Object();

        @Child private IsSequence isSequence = new IsSequence();
        @Child private GetAttributeNode readNode = GetAttributeNode.create();
        @Child private GetItemNode getItemNode = GetItemNode.create();
        @Child private KeyForAttributeAccess getAttributeKey = new KeyForAttributeAccess();
        @Child private KeyForItemAccess getItemKey = new KeyForItemAccess();
        final ConditionProfile strProfile = ConditionProfile.createBinaryProfile();
        @Child private PTypeToForeignNode toForeign = PTypeToForeignNodeGen.create();

        public Object execute(Object object, Object key) {
            String attrKey = getAttributeKey.execute(key);
            if (attrKey != null) {
                try {
                    return toForeign.executeConvert(readNode.execute(object, attrKey));
                } catch (PException e) {
                    // pass, we might be reading an item that starts with "@"
                }
            }

            String itemKey = getItemKey.execute(key);
            if (itemKey != null) {
                return toForeign.executeConvert(getItemNode.execute(object, itemKey));
            }

            if (strProfile.profile(key instanceof String)) {
                try {
                    return toForeign.executeConvert(readNode.execute(object, key));
                } catch (PException e) {
                    // pass
                }
            }
            if (isSequence.execute(object)) {
                try {
                    return toForeign.executeConvert(getItemNode.execute(object, key));
                } catch (PException e) {
                    // pass
                }
            }
            return NONEXISTING_IDENTIFIER;
        }
    }

    private static final class KeysNode extends Node {
        @Child private IsMapping isMapping = new IsMapping();
        @Child private LookupAndCallUnaryNode keysNode = LookupAndCallUnaryNode.create(SpecialMethodNames.KEYS);
        @Child private CastToListNode castToList = CastToListNode.create();
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        public Object execute(Object obj) {
            if (obj instanceof PythonNativeObject || !(obj instanceof PythonObject)) {
                return factory.createTuple(new Object[0]);
            }
            PythonObject object = (PythonObject) obj;
            Object[] attributeNames = object.getAttributeNames().toArray();
            if (isMapping.execute(object)) {
                PList keys = castToList.executeWith(keysNode.executeObject(object));
                Object[] keysArray = keys.getSequenceStorage().getCopyOfInternalArray();
                Object[] retVal = Arrays.copyOf(attributeNames, keysArray.length + attributeNames.length);
                for (int i = 0; i < keysArray.length; i++) {
                    Object key = keysArray[i];
                    if (key instanceof String || key instanceof PString) {
                        retVal[i + attributeNames.length] = key.toString();
                    } else {
                        return factory.createTuple(attributeNames);
                    }
                }
                return factory.createTuple(retVal);
            } else {
                return factory.createTuple(attributeNames);
            }
        }
    }

    private static final class ExecuteNode extends Node {
        @Child private PTypeToForeignNode toForeign = PTypeToForeignNodeGen.create();
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();
        @Child private LookupInheritedAttributeNode getCall = LookupInheritedAttributeNode.create();
        @Child private CallDispatchNode dispatch;
        @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();
        @Child private ArityCheckNode arityCheckNode = ArityCheckNode.create();
        final ValueProfile classProfile = ValueProfile.createClassProfile();

        private CallDispatchNode getDispatchNode() {
            if (dispatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatch = insert(CallDispatchNode.create("<foreign-invoke>"));
            }
            return dispatch;
        }

        public Object execute(Object receiver, Object[] arguments) {
            Object callable = getCall.execute(receiver, SpecialMethodNames.__CALL__);

            // convert foreign argument values to Python values
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }

            Object profiledCallable = classProfile.profile(callable);
            if (profiledCallable == PNone.NO_VALUE) {
                throw UnsupportedMessageException.raise(Message.createExecute(convertedArgs.length));
            }

            PKeyword[] emptyKeywords = new PKeyword[0];
            Object[] pArguments = createArgs.executeWithSelf(receiver, convertedArgs);

            return toForeign.executeConvert(getDispatchNode().executeCall(profiledCallable, pArguments, emptyKeywords));
        }
    }

    @Resolve(message = "READ")
    abstract static class PForeignReadNode extends Node {
        @Child private ReadNode readNode = new ReadNode();

        public Object access(Object object, Object key) {
            Object result = readNode.execute(object, key);
            if (result != ReadNode.NONEXISTING_IDENTIFIER) {
                return result;
            }
            throw UnknownIdentifierException.raise(key.toString());
        }
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        @Child private PTypeUnboxNode unboxNode = PTypeUnboxNode.create();

        Object access(Object object) {
            Object result = unboxNode.execute(object);
            if (result == object) {
                throw UnsupportedTypeException.raise(new Object[]{object});
            } else {
                return result;
            }
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private SetItemNode setItemNode = SetItemNode.create();
        @Child private SetAttributeNode writeNode = SetAttributeNode.create();
        @Child private IsMapping isMapping = new IsMapping();
        @Child private HasSetItem hasSetItem = new HasSetItem();
        @Child private KeyForAttributeAccess getAttributeKey = new KeyForAttributeAccess();
        @Child private KeyForItemAccess getItemKey = new KeyForItemAccess();
        final ConditionProfile strProfile = ConditionProfile.createBinaryProfile();

        public Object access(Object object, Object field, Object value) {
            String attrKey = getAttributeKey.execute(field);
            if (attrKey != null) {
                return writeNode.execute(object, attrKey, value);
            }

            String itemKey = getItemKey.execute(field);
            if (itemKey != null) {
                return setItemNode.executeWith(object, itemKey, value);
            }

            if (object instanceof PythonObject) {
                if (((PythonObject) object).getAttributeNames().contains(field)) {
                    writeNode.execute(object, field, value);
                    return value;
                }
            }
            if (strProfile.profile(field instanceof String)) {
                if (isMapping.execute(object)) {
                    setItemNode.executeWith(object, field, value);
                } else {
                    writeNode.execute(object, field, value);
                }
            } else {
                if (hasSetItem.execute(object)) {
                    setItemNode.executeWith(object, field, value);
                } else {
                    writeNode.execute(object, field, value);
                }
            }
            return value;
        }
    }

    @Resolve(message = "REMOVE")
    abstract static class PRemoveNode extends Node {
        @Child private DeleteItemNode delItemNode = DeleteItemNode.create();
        @Child private DeleteAttributeNode delNode = DeleteAttributeNode.create();
        @Child private IsMapping isMapping = new IsMapping();
        @Child private HasDelItem hasDelItem = new HasDelItem();
        @Child private KeyForAttributeAccess getAttributeKey = new KeyForAttributeAccess();
        @Child private KeyForItemAccess getItemKey = new KeyForItemAccess();
        final ConditionProfile strProfile = ConditionProfile.createBinaryProfile();

        public boolean access(Object object, Object field) {
            String attrKey = getAttributeKey.execute(field);
            if (attrKey != null) {
                delNode.execute(object, attrKey);
                return true;
            }

            String itemKey = getItemKey.execute(field);
            if (itemKey != null) {
                delItemNode.executeWith(object, itemKey);
                return true;
            }

            if (object instanceof PythonObject) {
                if (((PythonObject) object).getAttributeNames().contains(field)) {
                    delNode.execute(object, field);
                    return true;
                }
            }
            if (strProfile.profile(field instanceof String)) {
                if (isMapping.execute(object) && hasDelItem.execute(object)) {
                    delItemNode.executeWith(object, field);
                } else {
                    delNode.execute(object, field);
                }
            } else {
                if (hasDelItem.execute(object)) {
                    delItemNode.executeWith(object, field);
                } else {
                    delNode.execute(object, field);
                }
            }
            return true;
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class PForeignFunctionExecuteNode extends Node {
        @Child private ExecuteNode execNode = new ExecuteNode();

        public Object access(Object receiver, Object[] arguments) {
            return execNode.execute(receiver, arguments);
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class PForeignIsExecutableNode extends Node {
        @Child private LookupInheritedAttributeNode getCall = LookupInheritedAttributeNode.create();

        public Object access(Object receiver) {
            return getCall.execute(receiver, SpecialMethodNames.__CALL__) != PNone.NO_VALUE;
        }
    }

    @Resolve(message = "IS_INSTANTIABLE")
    abstract static class IsInstantiableNode extends Node {
        public Object access(Object obj) {
            if (obj instanceof PythonClass) {
                return true;
            } else {
                return false;
            }
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class PForeignInvokeNode extends Node {
        @Child private GetAttributeNode getattr = GetAttributeNode.create();
        @Child private ExecuteNode execNode = new ExecuteNode();

        public Object access(Object receiver, String name, Object[] arguments) {
            Object attribute = getattr.execute(receiver, name);
            return execNode.execute(attribute, arguments);
        }
    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {
        @Child private ExecuteNode execNode = new ExecuteNode();

        public Object access(Object receiver, Object[] arguments) {
            if (receiver instanceof PythonClass) {
                return execNode.execute(receiver, arguments);
            } else {
                throw UnsupportedTypeException.raise(new Object[]{receiver});
            }
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class PForeignIsNullNode extends Node {
        public Object access(Object object) {
            return object == PNone.NONE || object == PNone.NO_VALUE;
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class PForeignHasSizeNode extends Node {
        public Object access(Object object) {
            return object instanceof PSequence;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class PForeignGetSizeNode extends Node {
        @Child IsSequence isSeq = new IsSequence();
        @Child private BuiltinFunctions.LenNode lenNode = BuiltinFunctionsFactory.LenNodeFactory.create(null);

        public Object access(Object object) {
            if (isSeq.execute(object)) {
                return lenNode.executeWith(object);
            }
            throw UnsupportedMessageException.raise(Message.GET_SIZE);
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNode extends Node {
        public Object access(Object object) {
            return PTypeToForeignNode.isBoxed(object);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class PKeyInfoNode extends Node {
        @Child private GetAttributeNode getCallNode = GetAttributeNode.create();
        ReadNode readNode = new ReadNode();
        IsImmutable isImmutable = new IsImmutable();

        public int access(Object object, Object fieldName) {
            Object attr = readNode.execute(object, fieldName);
            int info = KeyInfo.NONE;
            if (attr != ReadNode.NONEXISTING_IDENTIFIER) {
                info |= KeyInfo.READABLE;
                try {
                    getCallNode.execute(attr, SpecialMethodNames.__CALL__);
                    info |= KeyInfo.INVOCABLE;
                } catch (PException e) {
                }
            }
            if (!isImmutable.execute(object)) {
                if (KeyInfo.isReadable(info)) {
                    info |= KeyInfo.REMOVABLE;
                    info |= KeyInfo.MODIFIABLE;
                } else {
                    info |= KeyInfo.INSERTABLE;
                }
            }
            return info;
        }
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {
        public Object access(Object obj) {
            return obj instanceof PythonAbstractObject;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class PForeignKeysNode extends Node {
        @Child KeysNode keysNode = new KeysNode();
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @SuppressWarnings("unused")
        public Object access(PNone object) {
            return factory.createTuple(new Object[0]);
        }

        public Object access(Object object) {
            return keysNode.execute(object);
        }
    }

    public abstract static class ToPyObjectNode extends PBaseNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaType;
        @CompilationFinal private TruffleObject PyNoneHandle;
        @Child private Node executeNode;
        @Child private Node isPointerNode;
        @Child private Node toNativeNode;
        @Child private ReadAttributeFromObjectNode readAttr;
        @Child private WriteAttributeToObjectNode writeAttr;

        public abstract Object execute(Object value);

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
            try {
                ensureIsPointer(ForeignAccess.sendExecute(getExecuteNode(), getPyNoneHandle(), object));
                return object;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        @Specialization(guards = "isNonNative(object)")
        Object runClass(PythonClass object) {
            ensureIsPointer(callIntoCapi(object, getPyObjectHandle_ForJavaType()));
            return object;
        }

        @Fallback
        Object runObject(Object object) {
            ensureIsPointer(callIntoCapi(object, getPyObjectHandle_ForJavaObject()));
            return object;
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

        private ReadAttributeFromObjectNode getReadAttr() {
            if (readAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttr = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttr;
        }

        private WriteAttributeToObjectNode getWriteAttr() {
            if (writeAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeAttr = insert(WriteAttributeToObjectNode.create());
            }
            return writeAttr;
        }

        protected boolean isNonNative(PythonClass klass) {
            return !(klass instanceof PythonNativeClass);
        }

        private Object callIntoCapi(Object object, TruffleObject func) {
            Object pyobject = getReadAttr().execute(object, pyobjectKey);
            if (pyobject == PNone.NO_VALUE) {
                try {
                    pyobject = ensureIsPointer(ForeignAccess.sendExecute(getExecuteNode(), func, object));
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw e.raise();
                }
                getWriteAttr().execute(object, pyobjectKey, pyobject);
            }
            return pyobject;
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyTypeNode = ToPyObjectNodeGen.create();

        Object access(Object obj) {
            return toPyTypeNode.execute(obj);
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private PointerBaseNode pointerNode = PointerBaseNode.create();
        private final ValueProfile profile = ValueProfile.createClassProfile();

        Object access(Object obj) {
            Object profiledObj = profile.profile(obj);
            if (profiledObj instanceof PythonClass) {
                return pointerNode.execute(obj) != PNone.NO_VALUE;
            }
            return false;
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private PointerBaseNode pointerNode = PointerBaseNode.create();
        @Child private Node asPointerNode;

        long access(Object obj) {
            Object ptr = pointerNode.execute(obj);
            if (asPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPointerNode = insert(Message.AS_POINTER.createNode());
            }
            try {
                return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) ptr);
            } catch (UnsupportedMessageException e) {
                throw e.raise();
            }
        }
    }

    private static class PointerBaseNode extends Node {

        @Child private ReadAttributeFromObjectNode readAttr;

        private ReadAttributeFromObjectNode getReadAttr() {
            if (readAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttr = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttr;
        }

        public static PointerBaseNode create() {
            return new PointerBaseNode();
        }

        Object execute(Object obj) {
            return getReadAttr().execute(obj, pyobjectKey);
        }
    }

    @CanResolve
    abstract static class CheckFunction extends Node {
        protected static boolean test(TruffleObject receiver) {
            return receiver instanceof PythonAbstractObject;
        }
    }
}
