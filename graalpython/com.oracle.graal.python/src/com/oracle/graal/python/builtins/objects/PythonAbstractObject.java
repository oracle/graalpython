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
package com.oracle.graal.python.builtins.objects;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;

import java.util.HashSet;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.datamodel.IsCallableNode;
import com.oracle.graal.python.nodes.datamodel.IsIterableNode;
import com.oracle.graal.python.nodes.datamodel.IsMappingNode;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.expression.CastToListNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(InteropLibrary.class)
public abstract class PythonAbstractObject implements TruffleObject, Comparable<Object> {
    private static final String PRIVATE_PREFIX = "__";
    private DynamicObjectNativeWrapper nativeWrapper;

    public DynamicObjectNativeWrapper getNativeWrapper() {
        return nativeWrapper;
    }

    public void setNativeWrapper(DynamicObjectNativeWrapper nativeWrapper) {
        assert this.nativeWrapper == null;
        this.nativeWrapper = nativeWrapper;
    }

    @ExportMessage
    public void writeMember(String key, Object value) {
        // TODO
    }

    @ExportMessage
    public Object readMember(String key,
                    @Cached KeyForAttributeAccess getAttributeKey,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached CallNode callGetattributeNode,
                    @Cached KeyForItemAccess getItemKey,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Shared("toForeign") @Cached PTypeToForeignNode toForeign) throws UnknownIdentifierException {
        String attrKey = getAttributeKey.execute(key);
        Object attrGetattribute = null;
        if (attrKey != null) {
            try {
                attrGetattribute = lookupGetattributeNode.execute(this, __GETATTRIBUTE__);
                return toForeign.executeConvert(callGetattributeNode.execute(null, attrGetattribute, this, attrKey));
            } catch (PException e) {
                // pass, we might be reading an item that starts with "@"
            }
        }

        String itemKey = getItemKey.execute(key);
        if (itemKey != null) {
            return toForeign.executeConvert(getItemNode.execute(this, itemKey));
        }

        try {
            if (attrGetattribute == null) {
                attrGetattribute = lookupGetattributeNode.execute(this, __GETATTRIBUTE__);
            }
            return toForeign.executeConvert(callGetattributeNode.execute(null, attrGetattribute, this, key));
        } catch (PException e) {
            // pass
        }

        throw UnknownIdentifierException.create(key);
    }

    @ExportMessage
    public boolean hasArrayElements(
                    @Shared("isSequenceNode") @Cached IsSequenceNode isSequenceNode,
                    @Shared("isIterableNode") @Cached IsIterableNode isIterableNode) {
        return isSequenceNode.execute(this) || isIterableNode.execute(this);
    }

    @ExportMessage
    public Object readArrayElement(long key,
                    @Shared("isSequenceNode") @Cached IsSequenceNode isSequenceNode,
                    @Shared("isIterableNode") @Cached IsIterableNode isIterableNode,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupIterNode,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupNextNode,
                    @Exclusive @Cached CallNode callIterNode,
                    @Exclusive @Cached CallNode callNextNode,
                    @Shared("toForeign") @Cached PTypeToForeignNode toForeign) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (isSequenceNode.execute(this)) {
            try {
                return toForeign.executeConvert(getItemNode.execute(this, key));
            } catch (PException e) {
                // TODO(fa) refine exception handling
                // it's a sequence, so we assume the index is wrong
                throw InvalidArrayIndexException.create(key);
            }
        }

        if (isIterableNode.execute(this)) {
            Object attrIter = lookupIterNode.execute(this, SpecialMethodNames.__ITER__);
            Object iter = callIterNode.execute(null, attrIter);
            if (iter != this) {
                // there is a separate iterator for this object, should be safe to consume
                Object result = iterateToKey(lookupNextNode, callNextNode, iter, key);
                if (result != PNone.NO_VALUE) {
                    return result;
                }
                // TODO(fa) refine exception handling
                // it's an iterable, so we assume the index is wrong
                throw InvalidArrayIndexException.create(key);
            }
        }

        throw UnsupportedMessageException.create();
    }

    private static Object iterateToKey(LookupInheritedAttributeNode.Dynamic lookupNextNode, CallNode callNextNode, Object iter, long key) {
        Object value = PNone.NO_VALUE;
        for (long i = 0; i <= key; i++) {
            Object attrNext = lookupNextNode.execute(iter, SpecialMethodNames.__NEXT__);
            value = callNextNode.execute(null, attrNext);
        }
        return value;
    }

    @ExportMessage
    public long getArraySize(
                    @Exclusive @Cached LookupAndCallUnaryDynamicNode callLenNode) throws UnsupportedMessageException {
        // since a call to this method must be preceded by a call to 'hasArrayElements', we just
        // assume that a length exists
        Object lenObj = callLenNode.executeObject(this, SpecialMethodNames.__LEN__);
        if (lenObj instanceof Number) {
            return ((Number) lenObj).longValue();
        } else if (lenObj instanceof PInt) {
            return ((PInt) lenObj).longValueExact();
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isArrayElementReadable(@SuppressWarnings("unused") long idx) {
        // We can't actually determine (in general) except of actually reading it which might have
        // side-effects.
        return true;
    }

    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    public boolean isMemberReadable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.READABLE) != 0;
    }

    @ExportMessage
    public boolean isMemberModifiable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.MODIFIABLE) != 0;
    }

    @ExportMessage
    public boolean isMemberInsertable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.INSERTABLE) != 0;
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.INVOCABLE) != 0;
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached CallNode callGetattributeNode,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Cached("createBinaryProfile()") ConditionProfile profileGetattribute,
                    @Cached("createBinaryProfile()") ConditionProfile profileMember) throws UnknownIdentifierException, UnsupportedMessageException {
        Object attrGetattribute = lookupGetattributeNode.execute(this, __GETATTRIBUTE__);
        if (profileGetattribute.profile(attrGetattribute != PNone.NO_VALUE)) {
            Object memberObj = callGetattributeNode.execute(null, attrGetattribute, this, member);
            if (profileMember.profile(memberObj != PNone.NO_VALUE)) {
                return executeNode.execute(memberObj, arguments);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    public boolean isExecutable(
                    @Cached IsCallableNode isCallableNode) {
        return isCallableNode.execute(this);
    }

    @ExportMessage
    public Object execute(Object[] arguments,
                    @Exclusive @Cached PExecuteNode executeNode) throws UnsupportedMessageException {
        return executeNode.execute(this, arguments);
    }

    @ExportMessage
    @TruffleBoundary
    public Object getMembers(boolean includeInternal,
                    @Exclusive @Cached LookupAndCallUnaryDynamicNode keysNode,
                    // TODO TRUFFLE LIBRARY MIGRATION: is 'allowUncached = true' safe?
                    @Cached(allowUncached = true) CastToListNode castToList,
                    @Cached GetClassNode getClass,
                    @Cached IsMappingNode isMapping,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Cached SequenceNodes.LenNode lenNode,
                    @Cached TypeNodes.GetMroNode getMroNode) {

        HashSet<String> keys = new HashSet<>();
        PythonAbstractClass klass = getClass.execute(this);
        for (PythonAbstractClass o : getMroNode.execute(klass)) {
            if (o instanceof PythonManagedClass) {
                addKeysFromObject(keys, (PythonManagedClass) o, includeInternal);
            }
            // TODO handle native class
        }
        if (this instanceof PythonObject) {
            addKeysFromObject(keys, (PythonObject) this, includeInternal);
        }
        if (includeInternal) {
            // we use the internal flag to also return dictionary keys for mappings
            if (isMapping.execute(this)) {
                PList mapKeys = castToList.executeWith(keysNode.executeObject(this, SpecialMethodNames.KEYS));
                int len = lenNode.execute(mapKeys);
                for (int i = 0; i < len; i++) {
                    Object key = getItemNode.execute(mapKeys, i);
                    if (key instanceof String) {
                        keys.add("[" + (String) key);
                    } else if (key instanceof PString) {
                        keys.add("[" + ((PString) key).getValue());
                    }
                }
            }
        }

        return new Keys(keys.toArray(new String[0]));
    }

    private static void addKeysFromObject(HashSet<String> keys, PythonObject o, boolean includeInternal) {
        for (Object k : o.getStorage().getShape().getKeys()) {
            String strKey;
            if (k instanceof String) {
                strKey = (String) k;
            } else if (k instanceof PString) {
                strKey = ((PString) k).getValue();
            } else {
                continue;
            }
            if (includeInternal || !strKey.startsWith(PRIVATE_PREFIX)) {
                keys.add(strKey);
            }
        }
    }

    @GenerateUncached
    public abstract static class PKeyInfoNode extends Node {
        private static final int NONE = 0;
        private static final int READABLE = 0x1;
        private static final int READ_SIDE_EFFECTS = 0x2;
        private static final int WRITE_SIDE_EFFECTS = 0x4;
        private static final int MODIFIABLE = 0x8;
        private static final int REMOVABLE = 0x10;
        private static final int INVOCABLE = 0x20;
        private static final int INSERTABLE = 0x40;

        public abstract int execute(Object receiver, String fieldName);

        @Specialization
        int access(Object object, String fieldName,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached IsCallableNode isCallableNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getGetNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getSetNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getDeleteNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsImmutable isImmutable,
                        @Cached KeyForItemAccess itemKey,
                        @Cached GetMroNode getMroNode) {

            String itemFieldName = itemKey.execute(fieldName);
            if (itemFieldName != null) {
                return READABLE | MODIFIABLE | REMOVABLE;
            }

            Object owner = object;
            int info = NONE;
            Object attr = PNone.NO_VALUE;

            PythonAbstractClass klass = getClassNode.execute(object);

            for (PythonAbstractClass c : getMroNode.execute(klass)) {
                attr = readNode.execute(c, fieldName);
                if (attr != PNone.NO_VALUE) {
                    owner = c;
                    break;
                }
            }

            if (attr == PNone.NO_VALUE) {
                attr = readNode.execute(owner, fieldName);
            }

            if (attr != PNone.NO_VALUE) {
                info |= READABLE;

                if (owner != object) {
                    if (attr instanceof PFunction || attr instanceof PBuiltinFunction) {
                        // if the attr is a known getter, we mark it invocable
                        // for other attributes, we look for a __call__ method later
                        info |= INVOCABLE;
                    } else {
                        // attr is inherited and might be a descriptor object other than a function
                        if (getGetNode.execute(attr, SpecialMethodNames.__GET__) != PNone.NO_VALUE) {
                            // is a getter, read may have side effects
                            info |= READ_SIDE_EFFECTS;
                        }
                        if (getSetNode.execute(attr, SpecialMethodNames.__SET__) != PNone.NO_VALUE || getDeleteNode.execute(attr, SpecialMethodNames.__DELETE__) != PNone.NO_VALUE) {
                            info |= WRITE_SIDE_EFFECTS;
                        }
                    }
                }

                if (attr != PNone.NO_VALUE) {
                    if (!isImmutable.execute(owner)) {
                        info |= REMOVABLE;
                        info |= MODIFIABLE;
                    }
                } else if (!isImmutable.execute(object)) {
                    info |= INSERTABLE;
                }

                if ((info & READ_SIDE_EFFECTS) == 0 && (info & INVOCABLE) == 0) {
                    // if this is not a getter, we check if the value inherits a __call__ attr
                    // if it is a getter, we just cannot really tell if the attr is invocable
                    if (isCallableNode.execute(attr)) {
                        info |= INVOCABLE;
                    }
                }
            }

            return info;
        }

        public static PKeyInfoNode create() {
            return PythonAbstractObjectFactory.PKeyInfoNodeGen.create();
        }

        public static PKeyInfoNode getUncached() {
            return PythonAbstractObjectFactory.PKeyInfoNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class IsImmutable extends Node {

        public abstract boolean execute(Object object);

        @Specialization
        public boolean isImmutable(Object object,
                        @Exclusive @Cached GetLazyClassNode getClass) {
            // TODO(fa) The first condition is too general; we should check if the object's type is
            // 'type'
            if (object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject || PGuards.isNativeClass(object) || PGuards.isNativeObject(object)) {
                return true;
            } else if (object instanceof PythonClass) {
                return false;
            } else {
                LazyPythonClass klass = getClass.execute(object);
                return klass instanceof PythonBuiltinClassType || klass instanceof PythonBuiltinClass || PGuards.isNativeClass(object);
            }
        }

        public static IsImmutable create() {
            return PythonAbstractObjectFactory.IsImmutableNodeGen.create();
        }

        public static IsImmutable getUncached() {
            return PythonAbstractObjectFactory.IsImmutableNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class KeyForAttributeAccess extends Node {

        public abstract String execute(String object);

        @Specialization
        String doIt(String object,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            if (profile.profile(object.length() > 1 && object.charAt(0) == '@')) {
                return object.substring(1);
            }
            return null;
        }

        public static KeyForAttributeAccess create() {
            return PythonAbstractObjectFactory.KeyForAttributeAccessNodeGen.create();
        }

        public static KeyForAttributeAccess getUncached() {
            return PythonAbstractObjectFactory.KeyForAttributeAccessNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class KeyForItemAccess extends Node {

        public abstract String execute(String object);

        @Specialization
        String doIt(String object,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            if (profile.profile(object.length() > 1 && object.charAt(0) == '[')) {
                return object.substring(1);
            }
            return null;
        }

        public static KeyForItemAccess create() {
            return PythonAbstractObjectFactory.KeyForItemAccessNodeGen.create();
        }

        public static KeyForItemAccess getUncached() {
            return PythonAbstractObjectFactory.KeyForItemAccessNodeGen.getUncached();
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.subscript.GetItemNode' but with an
     * uncached version.
     */
    @GenerateUncached
    public abstract static class PInteropSubscriptNode extends Node {

        public abstract Object execute(Object primary, Object index);

        @Specialization
        Object doSpecialObject(Object primary, Object index,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetItemNode,
                        @Cached CallNode callGetItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object attrGetItem = lookupGetItemNode.execute(primary, SpecialMethodNames.__GETITEM__);
            if (profile.profile(attrGetItem == PNone.NO_VALUE)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "'%p' object is not subscriptable", primary);
            }
            return callGetItemNode.execute(null, attrGetItem, primary, index);
        }

        public static PInteropSubscriptNode create() {
            return PythonAbstractObjectFactory.PInteropSubscriptNodeGen.create();
        }

        public static PInteropSubscriptNode getUncached() {
            return PythonAbstractObjectFactory.PInteropSubscriptNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class PExecuteNode extends Node {
        public abstract Object execute(Object receiver, Object[] arguments) throws UnsupportedMessageException;

        @Specialization
        Object doExecute(Object receiver, Object[] arguments,
                        @Cached PTypeToForeignNode toForeign,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic callAttrGetterNode,
                        @Cached ArgumentsFromForeignNode convertArgsNode) throws UnsupportedMessageException {
            Object isCallable = callAttrGetterNode.execute(receiver, SpecialMethodNames.__CALL__);
            if (isCallable == PNone.NO_VALUE) {
                throw UnsupportedMessageException.create();
            }
            Object[] convertedArgs = convertArgsNode.execute(arguments);
            return toForeign.executeConvert(callNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS));
        }

        public static PExecuteNode create() {
            return PythonAbstractObjectFactory.PExecuteNodeGen.create();
        }

        public static PExecuteNode getUncached() {
            return PythonAbstractObjectFactory.PExecuteNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class ArgumentsFromForeignNode extends Node {

        public abstract Object[] execute(Object[] arguments);

        @Specialization(guards = {"arguments.length == cachedLen", "cachedLen < 6"}, limit = "3")
        @ExplodeLoop
        static Object[] cached(Object[] arguments,
                        @Cached PForeignToPTypeNode fromForeign,
                        @Cached("arguments.length") int cachedLen) {
            Object[] convertedArgs = new Object[cachedLen];
            for (int i = 0; i < cachedLen; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }
            return convertedArgs;
        }

        @Specialization(replaces = "cached")
        static Object[] generic(Object[] arguments,
                        @Cached PForeignToPTypeNode fromForeign) {
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }
            return convertedArgs;
        }

        public static ArgumentsFromForeignNode create() {
            return PythonAbstractObjectFactory.ArgumentsFromForeignNodeGen.create();
        }

        public static ArgumentsFromForeignNode getUncached() {
            return PythonAbstractObjectFactory.ArgumentsFromForeignNodeGen.getUncached();
        }
    }

    @Override
    public String toString() {
        return "<an abstract python object>";
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class Keys implements TruffleObject {

        private final Object[] keys;

        Keys(Object[] keys) {
            this.keys = keys;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            try {
                return keys[(int) index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }
    }
}
