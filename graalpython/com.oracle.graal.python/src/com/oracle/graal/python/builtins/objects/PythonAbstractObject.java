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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.datamodel.IsCallableNode;
import com.oracle.graal.python.nodes.datamodel.IsIterableNode;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(InteropLibrary.class)
public abstract class PythonAbstractObject implements TruffleObject, Comparable<Object> {
    private DynamicObjectNativeWrapper nativeWrapper;

    public DynamicObjectNativeWrapper getNativeWrapper() {
        return nativeWrapper;
    }

    public void setNativeWrapper(DynamicObjectNativeWrapper nativeWrapper) {
        assert this.nativeWrapper == null;
        this.nativeWrapper = nativeWrapper;
    }

    @ExportMessage
    protected Object readMember(String key,
                    @Cached ReadNode readNode) throws UnknownIdentifierException {
        return readNode.execute(this, key);
    }

    @ImportStatic(SpecialMethodNames.class)
    @GenerateUncached
    abstract static class ReadNode extends Node {

        public abstract Object execute(Object object, String key) throws UnknownIdentifierException;

        @Specialization
        Object doRead(Object object, String key,
                        @Cached KeyForAttributeAccess getAttributeKey,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                        @Cached CallNode callGetattributeNode,
                        @Cached KeyForItemAccess getItemKey,
                        // TODO TRUFFLE LIBRARY MIGRATION: is 'allowUncached = true' safe?
                        @Cached(allowUncached = true) GetItemNode getItemNode,
                        @Cached PTypeToForeignNode toForeign) throws UnknownIdentifierException {
            String attrKey = getAttributeKey.execute(key);
            Object attrGetattribute = null;
            if (attrKey != null) {
                try {
                    attrGetattribute = lookupGetattributeNode.execute(object, __GETATTRIBUTE__);
                    return toForeign.executeConvert(callGetattributeNode.execute(null, attrGetattribute, attrKey));
                } catch (PException e) {
                    // pass, we might be reading an item that starts with "@"
                }
            }

            String itemKey = getItemKey.execute(key);
            if (itemKey != null) {
                return toForeign.executeConvert(getItemNode.execute(object, itemKey));
            }

            try {
                if (attrGetattribute == null) {
                    attrGetattribute = lookupGetattributeNode.execute(object, __GETATTRIBUTE__);
                }
                return toForeign.executeConvert(callGetattributeNode.execute(null, attrGetattribute, key));
            } catch (PException e) {
                // pass
            }

            throw UnknownIdentifierException.create(key);
        }

    }

    @ExportMessage
    protected boolean hasArrayElements(
                    @Shared("isSequenceNode") @Cached IsSequenceNode isSequenceNode,
                    @Shared("isIterableNode") @Cached IsIterableNode isIterableNode) {
        return isSequenceNode.execute(this) || isIterableNode.execute(this);
    }

    @ExportMessage(name = "readArrayElement")
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PReadArrayElementNode {

        @Specialization
        static Object doRead(PythonAbstractObject object, long key,
                        @Shared("isSequenceNode") @Cached IsSequenceNode isSequenceNode,
                        @Shared("isIterableNode") @Cached IsIterableNode isIterableNode,
                        // TODO TRUFFLE LIBRARY MIGRATION: is 'allowUncached = true' safe?
                        @Cached(allowUncached = true) GetItemNode getItemNode,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupIterNode,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupNextNode,
                        @Exclusive @Cached CallNode callIterNode,
                        @Exclusive @Cached CallNode callNextNode,
                        @Cached PTypeToForeignNode toForeign) throws UnsupportedMessageException, InvalidArrayIndexException {
            if (isSequenceNode.execute(object)) {
                try {
                    return toForeign.executeConvert(getItemNode.execute(object, key));
                } catch (PException e) {
                    // TODO(fa) refine exception handling
                    // it's a sequence, so we assume the index is wrong
                    throw InvalidArrayIndexException.create(key);
                }
            }

            if (isIterableNode.execute(object)) {
                Object attrIter = lookupIterNode.execute(object, SpecialMethodNames.__ITER__);
                Object iter = callIterNode.execute(null, attrIter);
                if (iter != object) {
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
    }

    @ExportMessage
    protected long getArraySize(
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupLenNode,
                    @Exclusive @Cached CallNode callLenNode) throws UnsupportedMessageException {
        // since a call to this method must be preceded by a call to 'hasArrayElements', we just
        // assume that a length exists
        Object attrLen = lookupLenNode.execute(this, SpecialMethodNames.__LEN__);
        Object lenObj = callLenNode.execute(null, attrLen);
        if (lenObj instanceof Number) {
            return ((Number) lenObj).longValue();
        } else if (lenObj instanceof PInt) {
            return ((PInt) lenObj).longValueExact();
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean isArrayElementReadable(@SuppressWarnings("unused") long idx) {
        // We can't actually determine (in general) except of actually reading it which might have
        // side-effects.
        return true;
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.READABLE) != 0;
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.MODIFIABLE) != 0;
    }

    @ExportMessage
    protected boolean isMemberInsertable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.INSERTABLE) != 0;
    }

    @GenerateUncached
    abstract static class PKeyInfoNode extends Node {
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
                        @Cached KeyForItemAccess itemKey) {

            String itemFieldName = itemKey.execute(fieldName);
            if (itemFieldName != null) {
                return READABLE | MODIFIABLE | REMOVABLE;
            }

            Object owner = object;
            int info = NONE;
            Object attr = PNone.NO_VALUE;

            PythonClass klass = getClassNode.execute(object);
            for (PythonClass c : klass.getMethodResolutionOrder()) {
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
    }

    @GenerateUncached
    abstract static class IsImmutable extends Node {

        public abstract boolean execute(Object object);

        @Specialization
        public boolean isImmutable(Object object,
                        @Exclusive @Cached GetLazyClassNode getClass) {
            if (object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject || object instanceof PythonNativeClass || object instanceof PythonNativeObject) {
                return true;
            } else if (object instanceof PythonClass) {
                return false;
            } else {
                LazyPythonClass klass = getClass.execute(object);
                return klass instanceof PythonBuiltinClassType || klass instanceof PythonBuiltinClass || klass instanceof PythonNativeClass;
            }
        }
    }

    @GenerateUncached
    abstract static class KeyForAttributeAccess extends Node {

        public abstract String execute(String object);

        @Specialization
        String doIt(String object,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            if (profile.profile(object.length() > 1 && object.charAt(0) == '@')) {
                return object.substring(1);
            }
            return null;
        }
    }

    @GenerateUncached
    abstract static class KeyForItemAccess extends Node {

        public abstract String execute(String object);

        @Specialization
        String doIt(String object,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            if (profile.profile(object.length() > 1 && object.charAt(0) == '[')) {
                return object.substring(1);
            }
            return null;
        }
    }

}
