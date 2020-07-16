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
package com.oracle.graal.python.builtins.objects;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.FILENO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FSPATH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.HasInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListInteropNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.utilities.TriState;

@ImportStatic(SpecialMethodNames.class)
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonObjectLibrary.class)
public abstract class PythonAbstractObject extends DynamicObject implements TruffleObject, Comparable<Object> {
    private static final String PRIVATE_PREFIX = "__";
    private DynamicObjectNativeWrapper nativeWrapper;

    public static final Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    protected static final Shape ABSTRACT_SHAPE = Shape.newBuilder().build();

    protected PythonAbstractObject(Shape shape) {
        super(shape);
    }

    protected PythonAbstractObject() {
        super(ABSTRACT_SHAPE);
    }

    public final DynamicObjectNativeWrapper getNativeWrapper() {
        return nativeWrapper;
    }

    public final void setNativeWrapper(DynamicObjectNativeWrapper nativeWrapper) {
        assert this.nativeWrapper == null;

        // we must not set the native wrapper for one of the context-insensitive singletons
        assert !CApiGuards.isSpecialSingleton(this);

        this.nativeWrapper = nativeWrapper;
    }

    public final void clearNativeWrapper(ConditionProfile hasHandleValidAssumptionProfile) {
        // The null check is important because it might be that we actually never got a to-native
        // message but still modified the reference count.
        if (hasHandleValidAssumptionProfile.profile(nativeWrapper != null && nativeWrapper.getHandleValidAssumption() != null)) {
            PythonNativeWrapper.invalidateAssumption(nativeWrapper.getHandleValidAssumption());
        }
        nativeWrapper = null;
    }

    /**
     * Checks if the object is a Mapping as described in the
     * <a href="https://docs.python.org/3/reference/datamodel.html">Python Data Model</a>. Mappings
     * are treated differently to other containers in some interop messages.
     */
    private static boolean isAbstractMapping(Object receiver, PythonObjectLibrary lib) {
        return lib.isSequence(receiver) && lib.lookupAttribute(receiver, KEYS, true) != PNone.NO_VALUE && //
                        lib.lookupAttribute(receiver, ITEMS, true) != PNone.NO_VALUE && //
                        lib.lookupAttribute(receiver, VALUES, true) != PNone.NO_VALUE;
    }

    private boolean isAbstractMapping(PythonObjectLibrary thisLib) {
        return isAbstractMapping(this, thisLib);
    }

    @ExportMessage
    public void writeMember(String key, Object value,
                    @Exclusive @Cached PInteropSubscriptAssignNode setItemNode,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached KeyForAttributeAccess getAttributeKey,
                    @Exclusive @Cached KeyForItemAccess getItemKey,
                    @Cached PInteropSetAttributeNode writeNode,
                    @Exclusive @Cached IsBuiltinClassProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
        try {
            String attrKey = getAttributeKey.execute(key);
            if (attrKey != null) {
                writeNode.execute(this, attrKey, value);
                return;
            }

            String itemKey = getItemKey.execute(key);
            if (itemKey != null) {
                setItemNode.execute(this, itemKey, value);
                return;
            }

            if (this instanceof PythonObject) {
                if (objectHasAttribute(this, key)) {
                    writeNode.execute(this, key, value);
                    return;
                }
            }
            if (isAbstractMapping(dataModelLibrary)) {
                setItemNode.execute(this, key, value);
            } else {
                writeNode.execute(this, key, value);
            }
        } catch (PException e) {
            e.expectAttributeError(attrErrorProfile);
            // TODO(fa) not accurate; distinguish between read-only and non-existing
            throw UnknownIdentifierException.create(key);
        }
    }

    @ExportMessage
    public Object readMember(String key,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached CallBinaryMethodNode callGetattributeNode,
                    @Exclusive @Cached KeyForItemAccess getItemKey,
                    @Exclusive @Cached KeyForAttributeAccess getAttributeKey,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Shared("toForeign") @Cached PTypeToForeignNode toForeign,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary) throws UnknownIdentifierException {
        String attrKey = getAttributeKey.execute(key);
        Object attrGetattribute = null;
        if (attrKey != null) {
            try {
                attrGetattribute = lookupGetattributeNode.execute(this, __GETATTRIBUTE__);
                return toForeign.executeConvert(callGetattributeNode.executeObject(attrGetattribute, this, attrKey));
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
            return toForeign.executeConvert(callGetattributeNode.executeObject(attrGetattribute, this, key));
        } catch (PException e) {
            // pass
        }
        if (dataModelLibrary.isSequence(this)) {
            try {
                return toForeign.executeConvert(getItemNode.execute(this, key));
            } catch (PException e) {
                // pass
            }
        }

        throw UnknownIdentifierException.create(key);
    }

    @ExportMessage
    public boolean hasArrayElements(
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary) {
        return dataModelLibrary.isSequence(this) && !isAbstractMapping(dataModelLibrary);
    }

    @ExportMessage
    public Object readArrayElement(long key,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Shared("toForeign") @Cached PTypeToForeignNode toForeign) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (dataModelLibrary.isSequence(this)) {
            try {
                return toForeign.executeConvert(getItemNode.execute(this, key));
            } catch (PException e) {
                if (isAbstractMapping(dataModelLibrary)) {
                    throw UnsupportedMessageException.create();
                } else {
                    // TODO(fa) refine exception handling
                    // it's a sequence, so we assume the index is wrong
                    throw InvalidArrayIndexException.create(key);
                }
            }
        }

        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public void writeArrayElement(long key, Object value,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached PInteropSubscriptAssignNode setItemNode) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (dataModelLibrary.isSequence(this)) {
            try {
                setItemNode.execute(this, key, value);
            } catch (PException e) {
                // TODO(fa) refine exception handling
                // it's a sequence, so we assume the index is wrong
                throw InvalidArrayIndexException.create(key);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public void removeArrayElement(long key,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached PInteropDeleteItemNode deleteItemNode) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (dataModelLibrary.isSequence(this)) {
            try {
                deleteItemNode.execute(this, key);
            } catch (PException e) {
                // TODO(fa) refine exception handling
                // it's a sequence, so we assume the index is wrong
                throw InvalidArrayIndexException.create(key);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public long getArraySize(@CachedLibrary("this") PythonObjectLibrary lib) throws UnsupportedMessageException {
        // since a call to this method must be preceded by a call to 'hasArrayElements', we just
        // assume that a length exists
        long len = lib.length(this);
        if (len >= 0) {
            return len;
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isArrayElementReadable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode) {
        return isInBounds(lib.length(this), getItemNode, idx);
    }

    @ExportMessage
    public boolean isArrayElementModifiable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode) {
        return !(this instanceof PTuple) && !(this instanceof PBytes) && isInBounds(lib.length(this), getItemNode, idx);
    }

    @ExportMessage
    public boolean isArrayElementInsertable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode) {
        return !(this instanceof PTuple) && !(this instanceof PBytes) && !isInBounds(lib.length(this), getItemNode, idx);
    }

    @ExportMessage
    public boolean isArrayElementRemovable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode) {
        return !(this instanceof PTuple) && !(this instanceof PBytes) && isInBounds(lib.length(this), getItemNode, idx);
    }

    private boolean isInBounds(int len, PInteropSubscriptNode getItemNode, long idx) {
        if (0 <= idx && idx < len) {
            try {
                getItemNode.execute(this, idx);
                return true;
            } catch (PException e) {
                return false;
            }
        } else {
            return false;
        }
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
    public boolean isMemberRemovable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.REMOVABLE) != 0;
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.READ_SIDE_EFFECTS) != 0;
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return (keyInfoNode.execute(this, member) & PKeyInfoNode.WRITE_SIDE_EFFECTS) != 0;
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached CallBinaryMethodNode callGetattributeNode,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile profileGetattribute,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile profileMember,
                    @Exclusive @Cached IsBuiltinClassProfile attributeErrorProfile) throws UnknownIdentifierException, UnsupportedMessageException {
        Object memberObj;
        try {
            Object attrGetattribute = lookupGetattributeNode.execute(this, __GETATTRIBUTE__);
            if (profileGetattribute.profile(attrGetattribute == PNone.NO_VALUE)) {
                throw UnknownIdentifierException.create(member);
            }
            memberObj = callGetattributeNode.executeObject(attrGetattribute, this, member);
            if (profileMember.profile(memberObj == PNone.NO_VALUE)) {
                throw UnknownIdentifierException.create(member);
            }
        } catch (PException e) {
            e.expect(AttributeError, attributeErrorProfile);
            throw UnknownIdentifierException.create(member);
        }
        return executeNode.execute(memberObj, arguments);
    }

    @ExportMessage
    public boolean isExecutable(
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary) {
        return dataModelLibrary.isCallable(this);
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
                    @Cached CastToListInteropNode castToList,
                    @Shared("getClassThis") @Cached GetClassNode getClass,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Cached SequenceNodes.LenNode lenNode,
                    @Cached TypeNodes.GetMroNode getMroNode) {

        HashSet<String> keys = new HashSet<>();
        Object klass = getClass.execute(this);
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
            if (isAbstractMapping(dataModelLibrary)) {
                PList mapKeys = castToList.executeWithGlobalState(keysNode.executeObject(this, KEYS));
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

    @ExportMessage
    public void removeMember(String member,
                    @Exclusive @Cached KeyForItemAccess getItemKey,
                    @Exclusive @Cached KeyForAttributeAccess getAttributeKey,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic getDelItemNode,
                    @Cached PInteropDeleteAttributeNode deleteAttributeNode,
                    @Exclusive @Cached PInteropDeleteItemNode delItemNode,
                    @Exclusive @Cached IsBuiltinClassProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
        try {
            String attrKey = getAttributeKey.execute(member);
            if (attrKey != null) {
                deleteAttributeNode.execute(this, attrKey);
                return;
            }

            String itemKey = getItemKey.execute(member);
            if (itemKey != null) {
                delItemNode.execute(this, itemKey);
                return;
            }

            if (this instanceof PythonObject) {
                if (objectHasAttribute(this, member)) {
                    deleteAttributeNode.execute(this, member);
                    return;
                }
            }
            if (isAbstractMapping(dataModelLibrary) && getDelItemNode.execute(this, __DELITEM__) != PNone.NO_VALUE) {
                delItemNode.execute(this, member);
            } else {
                deleteAttributeNode.execute(this, member);
            }
        } catch (PException e) {
            e.expectAttributeError(attrErrorProfile);
            // TODO(fa) not accurate; distinguish between read-only and non-existing
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    public Object getLazyPythonClass() {
        CompilerDirectives.bailout("Abstract method");
        throw new AbstractMethodError(getClass().getCanonicalName());
    }

    public Object getInternalLazyPythonClass() {
        CompilerDirectives.bailout("Abstract method");
        throw new AbstractMethodError(getClass().getCanonicalName());
    }

    @ExportMessage
    public boolean isInstantiable(
                    @Cached TypeNodes.IsTypeNode isTypeNode) {
        return isTypeNode.execute(this);
    }

    @ExportMessage
    public Object instantiate(Object[] arguments,
                    @Exclusive @Cached PExecuteNode executeNode) throws UnsupportedMessageException {
        return executeNode.execute(this, arguments);
    }

    private static void addKeysFromObject(HashSet<String> keys, PythonObject o, boolean includeInternal) {
        for (Object k : o.getStorage().getShape().getKeys()) {
            String strKey;
            if (k instanceof String) {
                strKey = (String) k;
            } else {
                continue;
            }
            if (includeInternal || !strKey.startsWith(PRIVATE_PREFIX)) {
                keys.add(strKey);
            }
        }
    }

    @ExportMessage
    public boolean isSequence(@CachedLibrary("this") PythonObjectLibrary plib,
                    @CachedLibrary(limit = "1") PythonObjectLibrary pythonTypeLibrary) {
        return pythonTypeLibrary.isSequenceType(plib.getLazyPythonClass(this));
    }

    @ExportMessage
    public int lengthWithState(ThreadState state,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile hasLen,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile ltZero,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic getLenNode,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
        Object lenFunc = getLenNode.execute(this, __LEN__);
        if (hasLen.profile(lenFunc != PNone.NO_VALUE)) {
            Object lenResult;
            int len;
            if (gotState.profile(state == null)) {
                lenResult = callNode.executeObject(lenFunc, this);
                len = lib.asSize(lenResult);
            } else {
                lenResult = callNode.executeObject(PArguments.frameForCall(state), lenFunc, this);
                len = lib.asSizeWithState(lenResult, state);
            }
            if (ltZero.profile(len < 0)) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, ErrorMessages.LEN_SHOULD_RETURN_MT_ZERO);
            } else {
                return len;
            }
        } else {
            throw raiseNode.raiseHasNoLength(this);
        }
    }

    @ExportMessage
    public boolean isMapping(@CachedLibrary("this") PythonObjectLibrary plib,
                    @CachedLibrary(limit = "1") PythonObjectLibrary pythonTypeLibrary) {
        return pythonTypeLibrary.isMappingType(plib.getLazyPythonClass(this));
    }

    @ExportMessage
    public boolean isSequenceType(
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("hasGetItemNode") @Cached LookupAttributeInMRONode.Dynamic hasGetItemNode,
                    @Exclusive @Cached LookupAttributeInMRONode.Dynamic hasLenNode,
                    @Shared("isLazyClass") @Cached("createBinaryProfile()") ConditionProfile isLazyClass,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile lenProfile,
                    @Shared("getItemProfile") @Cached("createBinaryProfile()") ConditionProfile getItemProfile) {
        if (isLazyClass.profile(lib.isLazyPythonClass(this))) {
            if (lenProfile.profile(hasLenNode.execute(this, __LEN__) != PNone.NO_VALUE)) {
                return getItemProfile.profile(hasGetItemNode.execute(this, __GETITEM__) != PNone.NO_VALUE);
            }
        }
        return false;
    }

    @ExportMessage
    public boolean isMappingType(
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("hasGetItemNode") @Cached LookupAttributeInMRONode.Dynamic hasGetItemNode,
                    @Shared("isLazyClass") @Cached("createBinaryProfile()") ConditionProfile isLazyClass,
                    @Shared("getItemProfile") @Cached("createBinaryProfile()") ConditionProfile getItemProfile) {
        if (isLazyClass.profile(lib.isLazyPythonClass(this))) {
            return getItemProfile.profile(hasGetItemNode.execute(this, __GETITEM__) != PNone.NO_VALUE);
        }
        return false;
    }

    @ExportMessage
    public final boolean isIterable(@CachedLibrary("this") PythonObjectLibrary plib,
                    @Exclusive @Cached LookupAttributeInMRONode.Dynamic getIterNode,
                    @Shared("hasGetItemNode") @Cached LookupAttributeInMRONode.Dynamic getGetItemNode,
                    @Exclusive @Cached LookupAttributeInMRONode.Dynamic hasNextNode,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile profileIter,
                    @Shared("getItemProfile") @Cached("createBinaryProfile()") ConditionProfile profileGetItem,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile profileNext) {
        Object klass = plib.getLazyPythonClass(this);
        Object iterMethod = getIterNode.execute(klass, __ITER__);
        if (profileIter.profile(iterMethod != PNone.NO_VALUE && iterMethod != PNone.NONE)) {
            return true;
        } else {
            Object getItemMethod = getGetItemNode.execute(klass, __GETITEM__);
            if (profileGetItem.profile(getItemMethod != PNone.NO_VALUE)) {
                return true;
            } else if (dataModelLibrary.isCallable(this)) {
                return profileNext.profile(hasNextNode.execute(klass, __NEXT__) != PNone.NO_VALUE);
            }
        }
        return false;
    }

    @ExportMessage
    public final boolean isCallable(@Exclusive @Cached LookupInheritedAttributeNode.Dynamic callAttrGetterNode) {
        Object call = callAttrGetterNode.execute(this, __CALL__);
        return PGuards.isCallable(call);
    }

    @ExportMessage
    public boolean isTrueWithState(ThreadState state,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile hasBool,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile hasLen,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupAttrs,
                    @Exclusive @Cached CastToJavaBooleanNode castToBoolean,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @CachedLibrary("this") PythonObjectLibrary lib,
                    @Exclusive @Cached CallUnaryMethodNode callNode) {
        // n.b.: CPython's early returns for PyTrue/PyFalse/PyNone are handled
        // in the message impls in PNone and PInt
        Object boolAttr = lookupAttrs.execute(this, __BOOL__);
        if (hasBool.profile(boolAttr != PNone.NO_VALUE)) {
            // this inlines the work done in sq_nb_bool when __bool__ is used.
            // when __len__ would be used, this is the same as the branch below
            // calling __len__
            Object result;
            if (gotState.profile(state == null)) {
                result = callNode.executeObject(boolAttr, this);
            } else {
                result = callNode.executeObject(PArguments.frameForCall(state), boolAttr, this);
            }
            try {
                return castToBoolean.execute(result);
            } catch (CannotCastException e) {
                // cast node will act as a branch profile already for the compiler
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.BOOL_SHOULD_RETURN_BOOL, result);
            }
        } else {
            Object lenAttr = lookupAttrs.execute(this, __LEN__);
            if (hasLen.profile(lenAttr != PNone.NO_VALUE)) {
                if (gotState.profile(state == null)) {
                    return lib.length(this) > 0;
                } else {
                    return lib.lengthWithState(this, state) > 0;
                }
            } else {
                // like CPython, anything else is true-ish
                return true;
            }
        }
    }

    @ExportMessage
    public final boolean isHashable(@Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupHashAttributeNode,
                    @CachedLibrary(limit = "1") PythonObjectLibrary dataModelLibrary) {
        Object hashAttr = lookupHashAttributeNode.execute(this, __HASH__);
        return dataModelLibrary.isCallable(hashAttr);
    }

    @ExportMessage
    public long hashWithState(ThreadState state,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupHashAttributeNode,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached CastToJavaLongExactNode castToLong,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
        Object hashAttr = getHashAttr(lookupHashAttributeNode, raise, lib);
        Object result;
        if (gotState.profile(state == null)) {
            result = callNode.executeObject(hashAttr, this);
        } else {
            result = callNode.executeObject(PArguments.frameForCall(state), hashAttr, this);
        }
        // see PyObject_GetHash and slot_tp_hash in CPython. The result of the
        // hash call is always a plain long, forcibly and lossy read from memory.
        try {
            return castToLong.execute(result);
        } catch (CannotCastException e) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.HASH_SHOULD_RETURN_INTEGER);
        }
    }

    @ExportMessage
    public boolean isSame(Object other,
                    @Shared("isNode") @Cached IsNode isNode) {
        return isNode.execute(this, other);
    }

    @ExportMessage
    public int equalsInternal(Object other, ThreadState state,
                    @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                    @Shared("isNode") @Cached IsNode isNode,
                    @Exclusive @Cached CallBinaryMethodNode callNode,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupEqAttrNode) {
        Object eqAttr = lookupEqAttrNode.execute(this, __EQ__);
        if (eqAttr == PNone.NO_VALUE) {
            // isNode specialization represents the branch profile
            // c.f.: Python always falls back to identity comparison in this case
            return isNode.execute(this, other) ? 1 : -1;
        } else {
            Object result;
            if (gotState.profile(state == null)) {
                result = callNode.executeObject(eqAttr, this, other);
            } else {
                result = callNode.executeObject(PArguments.frameForCall(state), eqAttr, this, other);
            }
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return -1;
            } else {
                if (gotState.profile(state == null)) {
                    return lib.isTrue(result) ? 1 : 0;
                } else {
                    return lib.isTrueWithState(result, state) ? 1 : 0;
                }
            }
        }
    }

    private Object getHashAttr(LookupInheritedAttributeNode.Dynamic lookupHashAttributeNode, PRaiseNode raise, PythonObjectLibrary lib) {
        Object hashAttr = lookupHashAttributeNode.execute(this, __HASH__);
        if (!lib.isCallable(hashAttr)) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNHASHABLE_TYPE, this);
        }
        return hashAttr;
    }

    @ExportMessage
    public final boolean canBeIndex(@Shared("asIndexLookup") @Cached LookupInheritedAttributeNode.Dynamic lookupIndex) {
        return lookupIndex.execute(this, __INDEX__) != PNone.NO_VALUE;
    }

    @ExportMessage
    public Object asIndexWithState(ThreadState state,
                    @CachedLibrary(limit = "5") PythonObjectLibrary lib,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached IsSubtypeNode isSubtype,
                    @Shared("asIndexLookup") @Cached LookupInheritedAttributeNode.Dynamic lookupIndex,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile noIndex,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile resultProfile,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        // n.b.: the CPython shortcut "if (PyLong_Check(item)) return item;" is
        // implemented in the specific Java classes PInt, PythonNativeVoidPtr,
        // and PythonAbstractNativeObject and dispatched polymorphically
        Object indexAttr = lookupIndex.execute(this, __INDEX__);
        if (noIndex.profile(indexAttr == PNone.NO_VALUE)) {
            throw raise.raiseIntegerInterpretationError(this);
        }

        Object result;
        if (gotState.profile(state == null)) {
            result = callNode.executeObject(indexAttr, this);
        } else {
            result = callNode.executeObject(PArguments.frameForCall(state), indexAttr, this);
        }

        if (resultProfile.profile(!isSubtype.execute(lib.getLazyPythonClass(result), PythonBuiltinClassType.PInt))) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
        }
        return result;
    }

    @ExportMessage
    public String asPathWithState(ThreadState state,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached PRaiseNode raise,
                    @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached ConditionProfile gotState) {
        Object func = lookup.execute(this, __FSPATH__);
        if (func == PNone.NO_VALUE) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_STR_BYTE_OSPATHLIKE_OBJ, this);
        }
        Object pathObject;
        if (gotState.profile(state == null)) {
            pathObject = callNode.executeObject(func, this);
        } else {
            pathObject = callNode.executeObject(PArguments.frameForCall(state), func, this);
        }
        try {
            return castToJavaStringNode.execute(pathObject);
        } catch (CannotCastException e) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, this, pathObject);
        }
    }

    @ExportMessage
    public Object asPStringWithState(ThreadState state,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached ConditionProfile gotState) {
        return asPString(this, lookup, gotState, state, callNode, isSubtypeNode, lib, raise);
    }

    @Ignore
    public static Object asPString(Object receiver, LookupInheritedAttributeNode.Dynamic lookup, ConditionProfile gotState, ThreadState state, CallUnaryMethodNode callNode,
                    IsSubtypeNode isSubtypeNode, PythonObjectLibrary lib, PRaiseNode raise) throws PException {
        Object func = lookup.execute(receiver, __STR__);

        Object result;
        if (gotState.profile(state == null)) {
            result = callNode.executeObject(func, receiver);
        } else {
            result = callNode.executeObject(PArguments.frameForCall(state), func, receiver);
        }

        if (!isSubtypeNode.execute(lib.getLazyPythonClass(result), PythonBuiltinClassType.PString)) {
            throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_STRING, "__str__", result);
        }
        return result;
    }

    @ExportMessage
    public int asFileDescriptorWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary libThis,
                    @CachedLibrary(limit = "1") PythonObjectLibrary libResult,
                    @Exclusive @Cached CallNode callFileNoNode,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached BranchProfile noFilenoMethodProfile,
                    @Exclusive @Cached IsBuiltinClassProfile isIntProfile,
                    @Exclusive @Cached ConditionProfile gotState,
                    @Exclusive @Cached CastToJavaIntExactNode castToJavaIntNode,
                    @Exclusive @Cached IsBuiltinClassProfile isAttrError) {

        Object filenoFunc = libThis.lookupAttribute(this, FILENO);
        if (filenoFunc == PNone.NO_VALUE) {
            noFilenoMethodProfile.enter();
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_INT_OR_HAVE_FILENO_METHOD);
        }

        Object result;
        if (gotState.profile(state == null)) {
            result = callFileNoNode.execute(filenoFunc);
        } else {
            result = callFileNoNode.execute(PArguments.frameForCall(state), filenoFunc);
        }

        if (isIntProfile.profileClass(libResult.getLazyPythonClass(result), PythonBuiltinClassType.PInt)) {
            try {
                return castToJavaIntNode.execute(result);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.TypeError, isAttrError);
                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
            }
        } else {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.RETURNED_NON_INTEGER, "fileno()");
        }
    }

    @ExportMessage
    public Object lookupAttribute(String name, boolean inheritedOnly,
                    @Exclusive @Cached LookupAttributeNode lookup) {
        return lookup.execute(this, name, inheritedOnly);
    }

    @GenerateUncached
    public abstract static class LookupAttributeNode extends Node {
        public abstract Object execute(Object receiver, String name, boolean inheritedOnly);

        @Specialization
        public static Object lookupAttributeImpl(Object receiver, String name, boolean inheritedOnly,
                        @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Cached ConditionProfile isInheritedOnlyProfile,
                        @Cached ConditionProfile noValueProfile,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinClassProfile isAttrErrorProfile) {
            if (isInheritedOnlyProfile.profile(inheritedOnly)) {
                return lookup.execute(receiver, name);
            } else {
                Object getAttrFunc = lookup.execute(receiver, __GETATTRIBUTE__);
                try {
                    return callNode.execute(getAttrFunc, receiver, name);
                } catch (PException pe) {
                    pe.expect(AttributeError, isAttrErrorProfile);
                    getAttrFunc = lookup.execute(receiver, __GETATTR__);
                    if (noValueProfile.profile(getAttrFunc == PNone.NO_VALUE)) {
                        return PNone.NO_VALUE;
                    }
                    return callNode.execute(getAttrFunc, receiver, name);
                }
            }
        }
    }

    @ExportMessage
    public boolean canBePInt(@Shared("asPIntLookupAttr") @Cached LookupInheritedAttributeNode.Dynamic lookup) {
        return lookup.execute(this, __INDEX__) != PNone.NO_VALUE || lookup.execute(this, __INT__) != PNone.NO_VALUE;
    }

    @ExportMessage
    public Object asPIntWithState(ThreadState state,
                    @Shared("asPIntLookupAttr") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached ConditionProfile gotState,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Exclusive @Cached ConditionProfile hasIndexFunc,
                    @Exclusive @Cached ConditionProfile hasIntFunc) {
        Object result = PNone.NO_VALUE;
        if (hasIndexFunc.profile(lib.canBeIndex(this))) {
            result = lib.asIndex(this);
        }
        if (result == PNone.NO_VALUE) {
            Object func = lookup.execute(this, __INT__);
            if (hasIntFunc.profile(func != PNone.NO_VALUE)) {
                if (gotState.profile(state == null)) {
                    result = callNode.executeObject(func, this);
                } else {
                    result = callNode.executeObject(PArguments.frameForCall(state), func, this);
                }
            }
            if (result == PNone.NO_VALUE) {
                throw raise.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, this);
            }
        }
        if (!PGuards.isInteger(result) && !PGuards.isPInt(result) && !(result instanceof Boolean)) {
            throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_INT, "__index__", result);
        }
        return result;
    }

    @ExportMessage
    public int asSizeWithState(Object type, ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Exclusive @Cached BranchProfile overflow,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile ignoreOverflow,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached CastToJavaLongLossyNode castToLong) {
        Object result = lib.asIndexWithState(this, state);
        long longResult;
        try {
            longResult = castToLong.execute(result); // this is a lossy cast
        } catch (CannotCastException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("cannot cast index to long - must not happen because then the #asIndex message impl should have raised");
        }
        try {
            return PInt.intValueExact(longResult);
        } catch (OverflowException e) {
            overflow.enter();
            if (ignoreOverflow.profile(type != null)) {
                throw raise.raiseNumberTooLarge(type, result);
            } else {
                // If no error-handling desired then the default clipping is done as in CPython.
                if (longResult < 0) {
                    return Integer.MIN_VALUE;
                } else {
                    return Integer.MAX_VALUE;
                }
            }
        }
    }

    @ExportMessage
    public boolean canBeJavaDouble(@Shared("asJavaLookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                    @CachedLibrary("this") PythonObjectLibrary lib) {
        return lookup.execute(this, __FLOAT__) != PNone.NO_VALUE || lib.canBeIndex(this);
    }

    @ExportMessage
    public double asJavaDoubleWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Exclusive @Cached() ConditionProfile hasIndexFunc,
                    @Shared("asJavaLookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached ConditionProfile gotState,
                    @Exclusive @Cached CastToJavaDoubleNode castToDouble,
                    @Exclusive @Cached() ConditionProfile hasFloatFunc,
                    @Exclusive @Cached PRaiseNode raise) {

        assert !MathGuards.isNumber(this) : this.getClass().getSimpleName();

        Object func = lookup.execute(this, __FLOAT__);
        if (hasFloatFunc.profile(func != PNone.NO_VALUE)) {
            Object result;
            if (gotState.profile(state == null)) {
                result = callNode.executeObject(func, this);
            } else {
                result = callNode.executeObject(PArguments.frameForCall(state), func, this);
            }
            if (result != PNone.NO_VALUE) {
                try {
                    return castToDouble.execute(result);
                } catch (CannotCastException e) {
                    throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_FLOAT, this, "__float__", result);
                }
            }
        }

        if (hasIndexFunc.profile(lib.canBeIndex(this))) {
            return castToDouble.execute(lib.asIndex(this));
        }

        throw raise.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, this);
    }

    @ExportMessage
    public boolean canBeJavaLong(
                    @Shared("asJavaLongLookup") @Cached LookupInheritedAttributeNode.Dynamic lookup) {
        return lookup.execute(this, __INT__) != PNone.NO_VALUE;
    }

    @ExportMessage
    public long asJavaLongWithState(ThreadState state,
                    @Shared("asJavaLongLookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached ConditionProfile gotState,
                    @Exclusive @Cached CastToJavaLongExactNode castToLong,
                    @Exclusive @Cached PRaiseNode raise) {

        assert !MathGuards.isNumber(this) : this.getClass().getSimpleName();

        Object func = lookup.execute(this, __INT__);
        if (func == PNone.NO_VALUE) {
            throw raise.raise(TypeError, ErrorMessages.MUST_BE_NUMERIC, this);
        }
        Object result;
        if (gotState.profile(state == null)) {
            result = callNode.executeObject(func, this);
        } else {
            result = callNode.executeObject(PArguments.frameForCall(state), func, this);
        }
        try {
            return castToLong.execute(result);
        } catch (CannotCastException e) {
            throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_LONG, this, "__int__", result);
        }
    }

    @ExportMessage
    public final boolean isContextManager(@Exclusive @Cached HasInheritedAttributeNode.Dynamic hasEnterNode,
                    @Exclusive @Cached HasInheritedAttributeNode.Dynamic hasExitNode,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
        return profile.profile(hasEnterNode.execute(this, __ENTER__) && hasExitNode.execute(this, __EXIT__));
    }

    private static final String DATETIME_MODULE_NAME = "datetime";
    private static final String TIME_MODULE_NAME = "time";
    private static final String DATE_TYPE = "date";
    private static final String DATETIME_TYPE = "datetime";
    private static final String TIME_TYPE = "time";
    private static final String STRUCT_TIME_TYPE = "struct_time";

    private static Object readType(ReadAttributeFromObjectNode readTypeNode, Object module, String typename, PythonObjectLibrary plib) {
        Object type = readTypeNode.execute(module, typename);
        if (plib.isLazyPythonClass(type)) {
            return type;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, ErrorMessages.PATCHED_DATETIME_CLASS, type);
        }
    }

    @ExportMessage
    public boolean isDate(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("dateTimeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile timeModuleLoaded) {
        Object objType = plib.getLazyPythonClass(this);
        PDict importedModules = PythonLanguage.getContext().getImportedModules();
        Object module = importedModules.getItem(DATETIME_MODULE_NAME);
        if (dateTimeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, DATETIME_TYPE, plib)) || isSubtypeNode.execute(objType, readType(readTypeNode, module, DATE_TYPE, plib))) {
                return true;
            }
        }
        module = importedModules.getItem(TIME_MODULE_NAME);
        if (timeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, STRUCT_TIME_TYPE, plib))) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    public LocalDate asDate(
                    @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary("this") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile timeModuleLoaded) throws UnsupportedMessageException {
        Object objType = plib.getLazyPythonClass(this);
        PDict importedModules = PythonLanguage.getContext().getImportedModules();
        Object module = importedModules.getItem(DATETIME_MODULE_NAME);
        if (dateTimeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, DATETIME_TYPE, plib)) || isSubtypeNode.execute(objType, readType(readTypeNode, module, DATE_TYPE, plib))) {
                try {
                    int year = castToIntNode.execute(lib.readMember(this, "year"));
                    int month = castToIntNode.execute(lib.readMember(this, "month"));
                    int day = castToIntNode.execute(lib.readMember(this, "day"));
                    return createLocalDate(year, month, day);
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }
        module = importedModules.getItem(TIME_MODULE_NAME);
        if (timeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, STRUCT_TIME_TYPE, plib))) {
                try {
                    int year = castToIntNode.execute(lib.readMember(this, "tm_year"));
                    int month = castToIntNode.execute(lib.readMember(this, "tm_mon"));
                    int day = castToIntNode.execute(lib.readMember(this, "tm_mday"));
                    return createLocalDate(year, month, day);
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isTime(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtype,
                    @Shared("dateTimeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile timeModuleLoaded) {
        Object objType = plib.getLazyPythonClass(this);
        PDict importedModules = PythonLanguage.getContext().getImportedModules();
        Object module = importedModules.getItem(DATETIME_MODULE_NAME);
        if (dateTimeModuleLoaded.profile(module != null)) {
            if (isSubtype.execute(objType, readType(readTypeNode, module, DATETIME_TYPE, plib)) || isSubtype.execute(objType, readType(readTypeNode, module, TIME_TYPE, plib))) {
                return true;
            }
        }
        module = importedModules.getItem(TIME_MODULE_NAME);
        if (timeModuleLoaded.profile(module != null)) {
            if (isSubtype.execute(objType, readType(readTypeNode, module, STRUCT_TIME_TYPE, plib))) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    public LocalTime asTime(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary("this") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile timeModuleLoaded) throws UnsupportedMessageException {
        Object objType = plib.getLazyPythonClass(this);
        PDict importedModules = PythonLanguage.getContext().getImportedModules();
        Object module = importedModules.getItem(DATETIME_MODULE_NAME);
        if (dateTimeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, DATETIME_TYPE, plib)) || isSubtypeNode.execute(objType, readType(readTypeNode, module, TIME_TYPE, plib))) {
                try {
                    int hour = castToIntNode.execute(lib.readMember(this, "hour"));
                    int min = castToIntNode.execute(lib.readMember(this, "minute"));
                    int sec = castToIntNode.execute(lib.readMember(this, "second"));
                    int micro = castToIntNode.execute(lib.readMember(this, "microsecond"));
                    return createLocalTime(hour, min, sec, micro);
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }
        module = importedModules.getItem(TIME_MODULE_NAME);
        if (timeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, STRUCT_TIME_TYPE, plib))) {
                try {
                    int hour = castToIntNode.execute(lib.readMember(this, "tm_hour"));
                    int min = castToIntNode.execute(lib.readMember(this, "tm_min"));
                    int sec = castToIntNode.execute(lib.readMember(this, "tm_sec"));
                    return createLocalTime(hour, min, sec, 0);
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isTimeZone(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtype,
                    @CachedLibrary(limit = "2") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile timeModuleLoaded) {
        Object objType = plib.getLazyPythonClass(this);
        PDict importedModules = PythonLanguage.getContext().getImportedModules();
        Object module = importedModules.getItem(DATETIME_MODULE_NAME);
        if (dateTimeModuleLoaded.profile(module != null)) {
            if (isSubtype.execute(objType, readType(readTypeNode, module, DATETIME_TYPE, plib))) {
                try {
                    Object tzinfo = lib.readMember(this, "tzinfo");
                    if (tzinfo != PNone.NONE) {
                        Object delta = lib.invokeMember(tzinfo, "utcoffset", new Object[]{this});
                        if (delta != PNone.NONE) {
                            return true;
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException ex) {
                    return false;
                }
            } else if (isSubtype.execute(objType, readType(readTypeNode, module, TIME_TYPE, plib))) {
                try {
                    Object tzinfo = lib.readMember(this, "tzinfo");
                    if (tzinfo != PNone.NONE) {
                        Object delta = lib.invokeMember(tzinfo, "utcoffset", new Object[]{PNone.NONE});
                        if (delta != PNone.NONE) {
                            return true;
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException ex) {
                    return false;
                }
            }
        }
        module = importedModules.getItem(TIME_MODULE_NAME);
        if (timeModuleLoaded.profile(module != null)) {
            if (isSubtype.execute(objType, readType(readTypeNode, module, STRUCT_TIME_TYPE, plib))) {
                try {
                    Object tm_zone = lib.readMember(this, "tm_zone");
                    if (tm_zone != PNone.NONE) {
                        return true;
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    return false;
                }
            }
        }
        return false;
    }

    @ExportMessage
    public ZoneId asTimeZone(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary(limit = "3") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached("createBinaryProfile()") ConditionProfile timeModuleLoaded) throws UnsupportedMessageException {
        if (!lib.isTimeZone(this)) {
            throw UnsupportedMessageException.create();
        }
        Object objType = plib.getLazyPythonClass(this);
        PDict importedModules = PythonLanguage.getContext().getImportedModules();
        Object module = importedModules.getItem(DATETIME_MODULE_NAME);
        if (dateTimeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, DATETIME_TYPE, plib))) {
                try {
                    Object tzinfo = lib.readMember(this, "tzinfo");
                    if (tzinfo != PNone.NONE) {
                        Object delta = lib.invokeMember(tzinfo, "utcoffset", new Object[]{this});
                        if (delta != PNone.NONE) {
                            int seconds = castToIntNode.execute(lib.readMember(delta, "seconds"));
                            return createZoneId(seconds);
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException ex) {
                    throw UnsupportedMessageException.create();
                }
            } else if (isSubtypeNode.execute(objType, readType(readTypeNode, module, TIME_TYPE, plib))) {
                try {
                    Object tzinfo = lib.readMember(this, "tzinfo");
                    if (tzinfo != PNone.NONE) {
                        Object delta = lib.invokeMember(tzinfo, "utcoffset", new Object[]{PNone.NONE});
                        if (delta != PNone.NONE) {
                            int seconds = castToIntNode.execute(lib.readMember(delta, "seconds"));
                            return createZoneId(seconds);
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }
        module = importedModules.getItem(TIME_MODULE_NAME);
        if (timeModuleLoaded.profile(module != null)) {
            if (isSubtypeNode.execute(objType, readType(readTypeNode, module, STRUCT_TIME_TYPE, plib))) {
                try {
                    Object tm_zone = lib.readMember(this, "tm_zone");
                    if (tm_zone != PNone.NONE) {
                        Object tm_gmtoffset = lib.readMember(this, "tm_gmtoff");
                        if (tm_gmtoffset != PNone.NONE) {
                            int seconds = castToIntNode.execute(tm_gmtoffset);
                            return createZoneId(seconds);
                        }
                        if (tm_zone instanceof String) {
                            return createZoneId((String) tm_zone);
                        }
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                    throw UnsupportedMessageException.create();
                }
            }
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    private static ZoneId createZoneId(int utcDeltaInSeconds) {
        return ZoneId.ofOffset("UTC", ZoneOffset.ofTotalSeconds(utcDeltaInSeconds));
    }

    @TruffleBoundary
    private static ZoneId createZoneId(String zone) {
        return ZoneId.of(zone);
    }

    @TruffleBoundary
    private static LocalTime createLocalTime(int hour, int min, int sec, int micro) {
        return LocalTime.of(hour, min, sec, micro);
    }

    @TruffleBoundary
    private static LocalDate createLocalDate(int year, int month, int day) {
        return LocalDate.of(year, month, day);
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
                        @Cached("createForceType()") ReadAttributeFromObjectNode readTypeAttrNode,
                        @Cached ReadAttributeFromObjectNode readObjectAttrNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary dataModelLibrary,
                        @Cached LookupInheritedAttributeNode.Dynamic getGetNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getSetNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getDeleteNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsImmutable isImmutable,
                        @Cached KeyForItemAccess itemKey,
                        @Cached KeyForAttributeAccess attrKey,
                        @Cached GetMroNode getMroNode) {

            String itemFieldName = itemKey.execute(fieldName);
            if (itemFieldName != null) {
                return READABLE | MODIFIABLE | REMOVABLE;
            }

            Object owner = object;
            int info = NONE;
            Object attr = PNone.NO_VALUE;

            Object klass = getClassNode.execute(object);

            String attrKeyName = attrKey.execute(fieldName);
            if (attrKeyName == null) {
                attrKeyName = fieldName;
            }

            for (PythonAbstractClass c : getMroNode.execute(klass)) {
                // n.b. we need to use a different node because it makes a difference if the type is
                // native
                attr = readTypeAttrNode.execute(c, attrKeyName);
                if (attr != PNone.NO_VALUE) {
                    owner = c;
                    break;
                }
            }

            if (attr == PNone.NO_VALUE) {
                attr = readObjectAttrNode.execute(owner, attrKeyName);
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
                        if (getGetNode.execute(attr, __GET__) != PNone.NO_VALUE) {
                            // is a getter, read may have side effects
                            info |= READ_SIDE_EFFECTS;
                        }
                        if (getSetNode.execute(attr, __SET__) != PNone.NO_VALUE || getDeleteNode.execute(attr, __DELETE__) != PNone.NO_VALUE) {
                            info |= WRITE_SIDE_EFFECTS;
                        }
                    }
                }
            }

            if (attr != PNone.NO_VALUE) {
                if (!isImmutable.execute(owner)) {
                    info |= REMOVABLE;
                    info |= MODIFIABLE;
                }
            } else if (!isImmutable.execute(object) || isAbstractMapping(object, dataModelLibrary)) {
                // If the member does not exist yet, it is insertable if this object is mutable,
                // i.e., it's not a builtin object or it is a mapping.
                info |= INSERTABLE;
            }

            if ((info & READ_SIDE_EFFECTS) == 0 && (info & INVOCABLE) == 0) {
                // if this is not a getter, we check if the value inherits a __call__ attr
                // if it is a getter, we just cannot really tell if the attr is invocable
                if (dataModelLibrary.isCallable(attr)) {
                    info |= INVOCABLE;
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

        @Specialization(limit = "3")
        public boolean isImmutable(Object object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            // TODO(fa) The first condition is too general; we should check if the object's type is
            // 'type'
            if (object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject || PGuards.isNativeClass(object) || PGuards.isNativeObject(object)) {
                return true;
            } else if (object instanceof PythonClass || object instanceof PythonModule) {
                return false;
            } else {
                Object klass = lib.getLazyPythonClass(object);
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
                        @Cached CallBinaryMethodNode callGetItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object attrGetItem = lookupGetItemNode.execute(primary, __GETITEM__);
            if (profile.profile(attrGetItem == PNone.NO_VALUE)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, primary);
            }
            return callGetItemNode.executeObject(attrGetItem, primary, index);
        }

        public static PInteropSubscriptNode create() {
            return PythonAbstractObjectFactory.PInteropSubscriptNodeGen.create();
        }

        public static PInteropSubscriptNode getUncached() {
            return PythonAbstractObjectFactory.PInteropSubscriptNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ReportPolymorphism
    public abstract static class PExecuteNode extends Node {

        public abstract Object execute(Object receiver, Object[] arguments) throws UnsupportedMessageException;

        @Specialization(guards = {"isBuiltinFunctionOrMethod(receiver)"})
        Object doVarargsBuiltinMethod(Object receiver, Object[] arguments,
                        @Exclusive @Cached PTypeToForeignNode toForeign,
                        @Cached CallVarargsMethodNode callVarargsMethodNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode) {
            Object[] convertedArgs = convertArgsNode.execute(arguments);
            return toForeign.executeConvert(callVarargsMethodNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS));
        }

        @Specialization(limit = "1", replaces = "doVarargsBuiltinMethod")
        Object doExecute(Object receiver, Object[] arguments,
                        @Exclusive @Cached PTypeToForeignNode toForeign,
                        @CachedLibrary("receiver") PythonObjectLibrary dataModelLibrary,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode) throws UnsupportedMessageException {
            if (!dataModelLibrary.isCallable(receiver)) {
                throw UnsupportedMessageException.create();
            }
            Object[] convertedArgs = convertArgsNode.execute(arguments);
            return toForeign.executeConvert(callNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS));
        }

        static boolean isBuiltinFunctionOrMethod(Object object) {
            return object instanceof PBuiltinMethod || object instanceof PBuiltinFunction;
        }

        public static PExecuteNode create() {
            return PythonAbstractObjectFactory.PExecuteNodeGen.create();
        }

        public static PExecuteNode getUncached() {
            return PythonAbstractObjectFactory.PExecuteNodeGen.getUncached();
        }

        @Override
        public Node copy() {
            return create();
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

        public Keys(Object[] keys) {
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

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.attributes.GetAttributeNode' but with an
     * uncached version.
     */
    @GenerateUncached
    public abstract static class PInteropGetAttributeNode extends Node {

        public abstract Object execute(Object object, Object attrName);

        @Specialization(limit = "2")
        static Object doIt(Object object, Object attrName,
                        @CachedLibrary("attrName") InteropLibrary libAttrName,
                        @Cached PRaiseNode raiseNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                        @Cached CallBinaryMethodNode callGetattributeNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetattrNode,
                        @Cached CallBinaryMethodNode callGetattrNode,
                        @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Cached("createBinaryProfile()") ConditionProfile hasGetattrProfile) {
            if (!libAttrName.isString(attrName)) {
                throw raiseNode.raise(TypeError, "attribute name must be string, not '%p'", attrName);
            }

            String attrNameStr;
            try {
                attrNameStr = libAttrName.asString(attrName);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }

            try {
                Object attrGetattribute = lookupGetattributeNode.execute(object, __GETATTRIBUTE__);
                return callGetattributeNode.executeObject(attrGetattribute, object, attrNameStr);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                Object attrGetattr = lookupGetattrNode.execute(object, __GETATTR__);
                if (hasGetattrProfile.profile(attrGetattr != PNone.NO_VALUE)) {
                    return callGetattrNode.executeObject(attrGetattr, object, attrNameStr);
                }
                throw pe;
            }
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.subscript.SetItemNode' but with an
     * uncached version.
     */
    @GenerateUncached
    public abstract static class PInteropSubscriptAssignNode extends Node {

        public abstract void execute(Object primary, Object key, Object value) throws UnsupportedMessageException;

        @Specialization
        static void doSpecialObject(PythonAbstractObject primary, Object key, Object value,
                        @Cached PInteropGetAttributeNode getAttributeNode,
                        @Cached CallBinaryMethodNode callSetItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) throws UnsupportedMessageException {

            Object attrSetitem = getAttributeNode.execute(primary, __SETITEM__);
            if (profile.profile(attrSetitem != PNone.NO_VALUE)) {
                callSetItemNode.executeObject(attrSetitem, key, value);
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.attributes.SetAttributeNode' but with an
     * uncached version.
     */
    @GenerateUncached
    public abstract static class PInteropSetAttributeNode extends Node {

        public abstract void execute(Object primary, String attrName, Object value) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization
        public void doSpecialObject(PythonAbstractObject primary, String attrName, Object value,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached IsBuiltinClassProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
            Object attrGetattribute = lookupSetAttrNode.execute(primary, SpecialMethodNames.__SETATTR__);
            if (profile.profile(attrGetattribute != PNone.NO_VALUE)) {
                try {
                    callSetAttrNode.execute(null, attrGetattribute, primary, attrName, value);
                } catch (PException e) {
                    e.expectAttributeError(attrErrorProfile);
                    // TODO(fa) not accurate; distinguish between read-only and non-existing
                    throw UnknownIdentifierException.create(attrName);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        public static PInteropSetAttributeNode create() {
            return PythonAbstractObjectFactory.PInteropSetAttributeNodeGen.create();
        }

        public static PInteropSetAttributeNode getUncached() {
            return PythonAbstractObjectFactory.PInteropSetAttributeNodeGen.getUncached();
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.subscript.DelItemNode' but with an
     * uncached version.
     */
    @GenerateUncached
    public abstract static class PInteropDeleteItemNode extends Node {

        public abstract void execute(Object primary, Object key) throws UnsupportedMessageException;

        @Specialization
        public void doSpecialObject(PythonAbstractObject primary, Object key,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallBinaryMethodNode callSetAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) throws UnsupportedMessageException {
            Object attrDelattr = lookupSetAttrNode.execute(primary, __DELITEM__);
            if (profile.profile(attrDelattr != PNone.NO_VALUE)) {
                callSetAttrNode.executeObject(attrDelattr, primary, key);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        public static PInteropDeleteItemNode create() {
            return PythonAbstractObjectFactory.PInteropDeleteItemNodeGen.create();
        }

        public static PInteropDeleteItemNode getUncached() {
            return PythonAbstractObjectFactory.PInteropDeleteItemNodeGen.getUncached();
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.attributes.DeleteAttributeNode' but with
     * an uncached version.
     */
    @GenerateUncached
    public abstract static class PInteropDeleteAttributeNode extends Node {

        public abstract void execute(Object primary, String attrName) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization
        public void doSpecialObject(PythonAbstractObject primary, String attrName,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallBinaryMethodNode callSetAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached IsBuiltinClassProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
            Object attrDelattr = lookupSetAttrNode.execute(primary, SpecialMethodNames.__DELATTR__);
            if (profile.profile(attrDelattr != PNone.NO_VALUE)) {
                try {
                    callSetAttrNode.executeObject(attrDelattr, primary, attrName);
                } catch (PException e) {
                    e.expectAttributeError(attrErrorProfile);
                    // TODO(fa) not accurate; distinguish between read-only and non-existing
                    throw UnknownIdentifierException.create(attrName);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        public static PInteropDeleteAttributeNode create() {
            return PythonAbstractObjectFactory.PInteropDeleteAttributeNodeGen.create();
        }

        public static PInteropDeleteAttributeNode getUncached() {
            return PythonAbstractObjectFactory.PInteropDeleteAttributeNodeGen.getUncached();
        }
    }

    @TruffleBoundary
    private static boolean objectHasAttribute(Object object, Object field) {
        return ((PythonObject) object).getAttributeNames().contains(field);
    }

    @ExportMessage
    public boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    public Class<? extends TruffleLanguage<?>> getLanguage() {
        return PythonLanguage.class;
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    public abstract static class ToDisplaySideEffectingNode extends Node {

        public abstract String execute(PythonAbstractObject receiver);

        @Specialization
        public String doDefault(PythonAbstractObject receiver,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached ReadAttributeFromObjectNode readStr,
                        @Cached CallNode callNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached ConditionProfile toStringUsed) {
            Object toStrAttr;
            String names;
            if (context.getOption(PythonOptions.UseReprForPrintString)) {
                names = BuiltinNames.REPR;
            } else {
                names = BuiltinNames.STR;
            }

            String result = null;
            PythonModule builtins = context.getBuiltins();
            if (toStringUsed.profile(builtins != null)) {
                toStrAttr = readStr.execute(builtins, names);
                try {
                    result = castStr.execute(callNode.execute(toStrAttr, receiver));
                } catch (CannotCastException e) {
                    // do nothing
                }
            }
            if (toStringUsed.profile(result != null)) {
                return result;
            } else {
                return receiver.toStringBoundary();
            }
        }

    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static class ToDisplayString {

        @Specialization(guards = "allowSideEffects")
        public static String doSideEffecting(PythonAbstractObject receiver, boolean allowSideEffects,
                        @Cached ToDisplaySideEffectingNode toDisplayCallnode) {
            return toDisplayCallnode.execute(receiver);
        }

        @Specialization(guards = "!allowSideEffects")
        public static String doNonSideEffecting(PythonAbstractObject receiver, boolean allowSideEffects) {
            return receiver.toStringBoundary();
        }

    }

    @TruffleBoundary
    final String toStringBoundary() {
        return toString();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public Object getMetaObject(@Shared("getClassThis") @Cached GetClassNode getClass) {
        return getClass.execute(this);
    }

    @ExportMessage
    public int identityHashCode(@Cached BuiltinFunctions.IdExpressionNode idNode) {
        return Long.hashCode(idNode.executeLong(0));
    }

    @TruffleBoundary
    public static int systemHashCode(Object value) {
        return System.identityHashCode(value);
    }

    @ExportMessage
    public TriState isIdenticalOrUndefined(Object other,
                    @CachedLibrary(limit = "3") InteropLibrary otherLib,
                    @CachedLibrary("this") PythonObjectLibrary objectLib) {
        if (this == other) {
            return TriState.TRUE;
        } else if (otherLib.hasIdentity(other)) {
            return objectLib.isSame(this, other) ? TriState.TRUE : TriState.FALSE;
        } else {
            return TriState.UNDEFINED;
        }
    }
}
