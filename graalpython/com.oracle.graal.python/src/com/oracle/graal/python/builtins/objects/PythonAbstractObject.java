/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.FILENO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
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
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
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
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListInteropNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastUnsignedToJavaLongHashNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
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
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
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
        return lib.isSequence(receiver) && lib.lookupAttribute(receiver, null, KEYS) != PNone.NO_VALUE && //
                        lib.lookupAttribute(receiver, null, ITEMS) != PNone.NO_VALUE && //
                        lib.lookupAttribute(receiver, null, VALUES) != PNone.NO_VALUE;
    }

    private boolean isAbstractMapping(PythonObjectLibrary thisLib) {
        return isAbstractMapping(this, thisLib);
    }

    @ExportMessage
    public void writeMember(String key, Object value,
                    @Cached PInteropSetAttributeNode setAttributeNode,
                    @Shared("attributeErrorProfile") @Cached IsBuiltinClassProfile attrErrorProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            setAttributeNode.execute(this, key, value);
        } catch (PException e) {
            e.expectAttributeError(attrErrorProfile);
            // TODO(fa) not accurate; distinguish between read-only and non-existing
            throw UnknownIdentifierException.create(key);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object readMember(String key,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        Object value = null;
        try {
            value = lib.lookupAttribute(this, null, key);
        } finally {
            gil.release(mustRelease);
        }
        if (value != PNone.NO_VALUE) {
            return value;
        } else {
            throw UnknownIdentifierException.create(key);
        }
    }

    @ExportMessage
    public boolean hasArrayElements(
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return dataModelLibrary.isSequence(this) && !isAbstractMapping(dataModelLibrary);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object readArrayElement(long key,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            if (dataModelLibrary.isSequence(this)) {
                try {
                    return getItemNode.execute(this, key);
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void writeArrayElement(long key, Object value,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Cached PInteropSubscriptAssignNode setItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            if (dataModelLibrary.isSequence(this)) {
                try {
                    setItemNode.execute(this, key, value);
                } catch (PException e) {
                    // TODO(fa) refine exception handling
                    // it's a sequence, so we assume the index is wrong
                    throw InvalidArrayIndexException.create(key);
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void removeArrayElement(long key,
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary,
                    @Exclusive @Cached PInteropDeleteItemNode deleteItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
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
        } finally {
            gil.release(mustRelease);
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
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isInBounds(lib.length(this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementModifiable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return !(this instanceof PTuple) && !(this instanceof PBytes) && isInBounds(lib.length(this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementInsertable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return !(this instanceof PTuple) && !(this instanceof PBytes) && !isInBounds(lib.length(this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementRemovable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return !(this instanceof PTuple) && !(this instanceof PBytes) && isInBounds(lib.length(this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
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
        return keyInfoNode.execute(this, member, PKeyInfoNode.READABLE);
    }

    @ExportMessage
    public boolean isMemberModifiable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, member, PKeyInfoNode.MODIFIABLE);
    }

    @ExportMessage
    public boolean isMemberInsertable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, member, PKeyInfoNode.INSERTABLE);
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, member, PKeyInfoNode.INVOCABLE);
    }

    @ExportMessage
    public boolean isMemberRemovable(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, member, PKeyInfoNode.REMOVABLE);
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, member, PKeyInfoNode.READ_SIDE_EFFECTS);
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String member,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, member, PKeyInfoNode.WRITE_SIDE_EFFECTS);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached CallBinaryMethodNode callGetattributeNode,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached ConditionProfile profileGetattribute,
                    @Exclusive @Cached ConditionProfile profileMember,
                    @Shared("attributeErrorProfile") @Cached IsBuiltinClassProfile attributeErrorProfile,
                    @Exclusive @Cached GilNode gil)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isExecutable(
                    @CachedLibrary("this") PythonObjectLibrary dataModelLibrary) {
        return dataModelLibrary.isCallable(this);
    }

    @ExportMessage
    public Object execute(Object[] arguments,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            return executeNode.execute(this, arguments);
        } finally {
            gil.release(mustRelease);
        }
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
                    @Cached TypeNodes.GetMroNode getMroNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
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

            return new Keys(keys.toArray(PythonUtils.EMPTY_STRING_ARRAY));
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void removeMember(String member,
                    @Cached PInteropDeleteAttributeNode deleteAttributeNode,
                    @Shared("attributeErrorProfile") @Cached IsBuiltinClassProfile attrErrorProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            deleteAttributeNode.execute(this, member);
        } catch (PException e) {
            e.expectAttributeError(attrErrorProfile);
            // TODO(fa) not accurate; distinguish between read-only and non-existing
            throw UnknownIdentifierException.create(member);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isInstantiable(
                    @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isTypeNode.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object instantiate(Object[] arguments,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            return executeNode.execute(this, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    private static void addKeysFromObject(HashSet<String> keys, PythonObject o, boolean includeInternal) {
        HashingStorage dict;
        if (PythonObjectLibrary.getUncached().hasDict(o)) {
            dict = PythonObjectLibrary.getUncached().getDict(o).getDictStorage();
        } else {
            dict = new DynamicObjectStorage(o); // temporary wrapper makes the rest of the code
                                                // easier
        }
        for (HashingStorage.DictEntry e : HashingStorageLibrary.getUncached().entries(dict)) {
            String strKey;
            if (e.getKey() instanceof String) {
                strKey = (String) e.getKey();
            } else {
                continue;
            }
            if (includeInternal || !strKey.startsWith(PRIVATE_PREFIX)) {
                keys.add(strKey);
            }
        }
    }

    @ExportMessage
    public int lengthWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Exclusive @Cached ConditionProfile hasLen,
                    @Exclusive @Cached ConditionProfile ltZero,
                    @Shared("raise") @Cached PRaiseNode raiseNode,
                    @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Exclusive @Cached CastToJavaLongLossyNode toLong,
                    @Exclusive @Cached ConditionProfile ignoreOverflow,
                    @Exclusive @Cached BranchProfile overflow) {
        Object lenFunc = plib.lookupAttributeOnType(this, __LEN__);
        if (hasLen.profile(lenFunc != PNone.NO_VALUE)) {
            Object lenResult = methodLib.callUnboundMethodWithState(lenFunc, state, this);
            // the following mimics typeobject.c#slot_sq_length()
            // - PyNumber_Index is called first
            // - checked if negative
            // - PyNumber_AsSsize_t is called, in scope of which PyNumber_Index is called again
            lenResult = indexNode.execute(gotState.profile(state != null) ? PArguments.frameForCall(state) : null, lenResult);
            long longResult;
            try {
                longResult = toLong.execute(lenResult); // this is a lossy cast
                if (ltZero.profile(longResult < 0)) {
                    throw raiseNode.raise(PythonBuiltinClassType.ValueError, ErrorMessages.LEN_SHOULD_RETURN_MT_ZERO);
                }
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("cannot cast index to long - must not happen because then the #asIndex message impl should have raised");
            }
            return longToInt(longResult, overflow, ignoreOverflow, OverflowError, raiseNode, lenResult);
        } else {
            throw raiseNode.raiseHasNoLength(this);
        }
    }

    @ExportMessage
    public boolean isTrueWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Exclusive @Cached ConditionProfile hasBool,
                    @Exclusive @Cached ConditionProfile hasLen,
                    @Exclusive @Cached CastToJavaBooleanNode castToBoolean,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        // n.b.: CPython's early returns for PyTrue/PyFalse/PyNone are handled
        // in the message impls in PNone and PInt
        Object boolMethod = lib.lookupAttributeOnType(this, __BOOL__);
        if (hasBool.profile(boolMethod != PNone.NO_VALUE)) {
            // this inlines the work done in sq_nb_bool when __bool__ is used.
            // when __len__ would be used, this is the same as the branch below
            // calling __len__
            Object result = methodLib.callUnboundMethodWithState(boolMethod, state, this);
            try {
                return castToBoolean.execute(result);
            } catch (CannotCastException e) {
                // cast node will act as a branch profile already for the compiler
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.BOOL_SHOULD_RETURN_BOOL, result);
            }
        } else {
            Object lenAttr = lib.lookupAttributeOnType(this, __LEN__);
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
    public long hashWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Cached LookupInheritedAttributeNode.Dynamic lookupGet,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Exclusive @Cached CastUnsignedToJavaLongHashNode castUnsignedToJavaLongHashNode) {
        Object hashMethod = lib.lookupAttributeOnType(this, __HASH__);
        if (!methodLib.isCallable(hashMethod) && lookupGet.execute(hashMethod, __GET__) == PNone.NO_VALUE) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNHASHABLE_TYPE, this);
        }
        Object result = methodLib.callUnboundMethodIgnoreGetExceptionWithState(hashMethod, state, this);
        // see PyObject_GetHash and slot_tp_hash in CPython. The result of the
        // hash call is always a plain long, forcibly and lossy read from memory.
        try {
            return castUnsignedToJavaLongHashNode.execute(result);
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
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @CachedLibrary(limit = "3") PythonObjectLibrary resultLib,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Shared("isNode") @Cached IsNode isNode) {
        Object eqMethod = lib.lookupAttributeOnType(this, __EQ__);
        if (eqMethod == PNone.NO_VALUE) {
            // isNode specialization represents the branch profile
            // c.f.: Python always falls back to identity comparison in this case
            return isNode.execute(this, other) ? 1 : -1;
        } else {
            Object result = methodLib.callUnboundMethodIgnoreGetExceptionWithState(eqMethod, state, this, other);
            if (result == PNotImplemented.NOT_IMPLEMENTED || result == PNone.NO_VALUE) {
                return -1;
            } else {
                if (gotState.profile(state == null)) {
                    return resultLib.isTrue(result) ? 1 : 0;
                } else {
                    return resultLib.isTrueWithState(result, state) ? 1 : 0;
                }
            }
        }
    }

    @ExportMessage
    public String asPathWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Cached CastToJavaStringNode castToJavaStringNode) {
        Object func = lib.lookupAttributeOnType(this, __FSPATH__);
        if (func == PNone.NO_VALUE) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_STR_BYTE_OSPATHLIKE_OBJ, this);
        }
        Object pathObject = methodLib.callUnboundMethodWithState(func, state, this);
        try {
            return castToJavaStringNode.execute(pathObject);
        } catch (CannotCastException e) {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, this, pathObject);
        }
    }

    @ExportMessage
    public Object asPStringWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("raise") @Cached PRaiseNode raise) {
        return asPString(lib, this, state, isSubtypeNode, getClassNode, raise);
    }

    @Ignore
    public static Object asPString(PythonObjectLibrary lib, Object receiver, ThreadState state,
                    IsSubtypeNode isSubtypeNode, GetClassNode getClassNode, PRaiseNode raise) throws PException {
        Object result = lib.lookupAndCallSpecialMethodWithState(receiver, state, __STR__);

        if (!isSubtypeNode.execute(getClassNode.execute(result), PythonBuiltinClassType.PString)) {
            throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_STRING, "__str__", result);
        }
        return result;
    }

    @ExportMessage
    public int asFileDescriptorWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Shared("raise") @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached BranchProfile noFilenoMethodProfile,
                    @Exclusive @Cached IsBuiltinClassProfile isIntProfile,
                    @Exclusive @Cached CastToJavaIntExactNode castToJavaIntNode,
                    @Exclusive @Cached IsBuiltinClassProfile isAttrError) {
        Object filenoFunc = lib.lookupAttributeWithState(this, state, FILENO);
        if (filenoFunc == PNone.NO_VALUE) {
            noFilenoMethodProfile.enter();
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_INT_OR_HAVE_FILENO_METHOD);
        }

        Object result = methodLib.callObjectWithState(filenoFunc, state);
        if (isIntProfile.profileObject(result, PythonBuiltinClassType.PInt)) {
            try {
                return PInt.asFileDescriptor(castToJavaIntNode.execute(result), raiseNode);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.TypeError, isAttrError);
                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "int");
            }
        } else {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.RETURNED_NON_INTEGER, "fileno()");
        }
    }

    @ExportMessage
    public Object lookupAttributeInternal(ThreadState state, String name, boolean strict,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Exclusive @Cached LookupAttributeNode lookup) {
        VirtualFrame frame = null;
        if (gotState.profile(state != null)) {
            frame = PArguments.frameForCall(state);
        }
        return lookup.execute(frame, this, name, strict);
    }

    @GenerateUncached
    public abstract static class LookupAttributeNode extends Node {
        public abstract Object execute(Frame frame, Object receiver, String name, boolean strict);

        @Specialization
        public static Object lookupAttributeImpl(VirtualFrame frame, Object receiver, String name, boolean strict,
                        @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Cached ConditionProfile noValueProfile,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinClassProfile isAttrErrorProfile1,
                        @Cached IsBuiltinClassProfile isAttrErrorProfile2) {
            try {
                Object getAttrFunc = lookup.execute(receiver, __GETATTRIBUTE__);
                try {
                    return callNode.execute(frame, getAttrFunc, receiver, name);
                } catch (PException pe) {
                    pe.expect(AttributeError, isAttrErrorProfile1);
                    getAttrFunc = lookup.execute(receiver, __GETATTR__);
                    if (noValueProfile.profile(getAttrFunc == PNone.NO_VALUE)) {
                        if (strict) {
                            throw pe;
                        } else {
                            return PNone.NO_VALUE;
                        }
                    }
                    return callNode.execute(frame, getAttrFunc, receiver, name);
                }
            } catch (PException pe) {
                pe.expect(AttributeError, isAttrErrorProfile2);
                if (strict) {
                    throw pe;
                } else {
                    return PNone.NO_VALUE;
                }
            }
        }
    }

    @ExportMessage
    public Object lookupAttributeOnTypeInternal(String name, boolean strict,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Exclusive @Cached LookupAttributeOnTypeNode lookup) {
        return lookup.execute(getClassNode.execute(this), name, strict);
    }

    @GenerateUncached
    public abstract static class LookupAttributeOnTypeNode extends Node {
        public abstract Object execute(Object type, String name, boolean strict);

        @Specialization
        public static Object lookupAttributeImpl(Object type, String name, boolean strict,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached PRaiseNode raiseNode) {
            Object result = lookup.execute(type, name);
            if (strict && result == PNone.NO_VALUE) {
                throw raiseNode.raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, type, name);
            }
            return result;
        }
    }

    @ExportMessage
    public Object callObjectWithState(ThreadState state, Object[] arguments,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Exclusive @Cached CallNode callNode) {
        VirtualFrame frame = null;
        if (gotState.profile(state != null)) {
            frame = PArguments.frameForCall(state);
        }
        return callNode.execute(frame, this, arguments);
    }

    @ExportMessage
    public Object callUnboundMethodWithState(ThreadState state, Object receiver, Object[] arguments,
                    @Exclusive @Cached CallUnboundMethodNode call) {
        return call.execute(state, this, false, receiver, arguments);
    }

    @ExportMessage
    public Object callUnboundMethodIgnoreGetExceptionWithState(ThreadState state, Object receiver, Object[] arguments,
                    @Exclusive @Cached CallUnboundMethodNode call) {
        return call.execute(state, this, true, receiver, arguments);
    }

    @GenerateUncached
    public abstract static class CallUnboundMethodNode extends Node {
        public abstract Object execute(ThreadState state, Object method, boolean ignoreGetException, Object receiver, Object[] arguments);

        @Specialization
        Object getAndCall(ThreadState state, Object method, boolean ignoreGetException, Object receiver, Object[] arguments,
                        @Cached GetClassNode getClassNode,
                        @Cached ConditionProfile gotState,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGet,
                        @Cached CallNode callGet,
                        @Cached CallNode callMethod) {
            VirtualFrame frame = null;
            if (gotState.profile(state != null)) {
                frame = PArguments.frameForCall(state);
            }
            Object get = lookupGet.execute(method, __GET__);
            Object callable = method;
            if (get != PNone.NO_VALUE) {
                try {
                    callable = callGet.execute(frame, get, method, receiver, getClassNode.execute(receiver));
                } catch (PException pe) {
                    if (ignoreGetException) {
                        return PNone.NO_VALUE;
                    }
                    throw pe;
                }
            }
            return callMethod.execute(frame, callable, arguments);
        }
    }

    @ExportMessage
    public Object lookupAndCallSpecialMethodWithState(ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("this") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeOnTypeStrict(this, methodName);
        return methodLib.callUnboundMethodWithState(method, state, this, arguments);
    }

    @ExportMessage
    public Object lookupAndCallRegularMethodWithState(ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("this") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeStrictWithState(this, state, methodName);
        return methodLib.callObjectWithState(method, state, arguments);
    }

    @ExportMessage
    public Object asPIntWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Shared("indexCheckNode") @Cached PyIndexCheckNode indexCheckNode,
                    @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                    @Exclusive @Cached ConditionProfile hasIntFunc) {
        Object result = PNone.NO_VALUE;
        if (indexCheckNode.execute(this)) {
            result = indexNode.execute(gotState.profile(state != null) ? PArguments.frameForCall(state) : null, this);
        }
        if (result == PNone.NO_VALUE) {
            Object func = lib.lookupAttributeOnType(this, __INT__);
            if (hasIntFunc.profile(func != PNone.NO_VALUE)) {
                result = methodLib.callUnboundMethodWithState(func, state, this);
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

    private static int longToInt(long longResult, BranchProfile overflow, ConditionProfile ignoreOverflow, PythonBuiltinClassType type, PRaiseNode raise, Object result) throws PException {
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
    public double asJavaDoubleWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Exclusive @Cached CastToJavaDoubleNode castToDouble,
                    @Exclusive @Cached ConditionProfile hasFloatFunc,
                    @Shared("indexCheckNode") @Cached PyIndexCheckNode indexCheckNode,
                    @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                    @Shared("gotState") @Cached ConditionProfile gotState,
                    @Shared("raise") @Cached PRaiseNode raise) {
        assert !MathGuards.isNumber(this) : this.getClass().getSimpleName();

        Object func = lib.lookupAttributeOnType(this, __FLOAT__);
        if (hasFloatFunc.profile(func != PNone.NO_VALUE)) {
            Object result = methodLib.callUnboundMethodWithState(func, state, this);
            if (result != PNone.NO_VALUE) {
                try {
                    return castToDouble.execute(result);
                } catch (CannotCastException e) {
                    throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_FLOAT, this, "__float__", result);
                }
            }
        }

        if (indexCheckNode.execute(this)) {
            return castToDouble.execute(indexNode.execute(gotState.profile(state != null) ? PArguments.frameForCall(state) : null, this));
        }

        throw raise.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, this);
    }

    @ExportMessage
    public long asJavaLongWithState(ThreadState state,
                    @CachedLibrary("this") PythonObjectLibrary lib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                    @Exclusive @Cached CastToJavaLongExactNode castToLong,
                    @Shared("raise") @Cached PRaiseNode raise) {
        assert !MathGuards.isNumber(this) : this.getClass().getSimpleName();

        Object func = lib.lookupAttributeOnType(this, __INDEX__);
        if (func == PNone.NO_VALUE) {
            func = lib.lookupAttributeOnType(this, __INT__);
            if (func == PNone.NO_VALUE) {
                throw raise.raise(TypeError, ErrorMessages.MUST_BE_NUMERIC, this);
            }
        }
        Object result = methodLib.callUnboundMethodWithState(func, state, this);
        try {
            return castToLong.execute(result);
        } catch (CannotCastException e) {
            throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_LONG, this, "__int__", result);
        }
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
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("dateTimeModuleProfile") @Cached ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached ConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.execute(this);
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public LocalDate asDate(
                    @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary("this") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached ConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.execute(this);
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isTime(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtype,
                    @Shared("dateTimeModuleProfile") @Cached ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached ConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.execute(this);
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public LocalTime asTime(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary("this") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached ConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.execute(this);
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isTimeZone(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtype,
                    @CachedLibrary(limit = "2") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached ConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.execute(this);
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
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public ZoneId asTimeZone(@CachedLibrary(limit = "3") PythonObjectLibrary plib,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary(limit = "3") InteropLibrary lib,
                    @Shared("dateTimeModuleProfile") @Cached ConditionProfile dateTimeModuleLoaded,
                    @Shared("timeModuleProfile") @Cached ConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!lib.isTimeZone(this)) {
                throw UnsupportedMessageException.create();
            }
            Object objType = getClassNode.execute(this);
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
        } finally {
            gil.release(mustRelease);
        }
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
        return LocalTime.of(hour, min, sec, micro * 1000);
    }

    @TruffleBoundary
    private static LocalDate createLocalDate(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @GenerateUncached
    public abstract static class PKeyInfoNode extends Node {
        private static final int READABLE = 0x1;
        private static final int READ_SIDE_EFFECTS = 0x2;
        private static final int WRITE_SIDE_EFFECTS = 0x4;
        private static final int MODIFIABLE = 0x8;
        private static final int REMOVABLE = 0x10;
        private static final int INVOCABLE = 0x20;
        private static final int INSERTABLE = 0x40;

        public abstract boolean execute(Object receiver, String fieldName, int infoType);

        @Specialization
        static boolean access(Object object, String attrKeyName, int type,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readTypeAttrNode,
                        @Cached ReadAttributeFromObjectNode readObjectAttrNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary dataModelLibrary,
                        @Cached LookupInheritedAttributeNode.Dynamic getGetNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getSetNode,
                        @Cached LookupInheritedAttributeNode.Dynamic getDeleteNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsImmutable isImmutable,
                        @Cached GetMroNode getMroNode,
                        @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                Object owner = object;
                Object attr = PNone.NO_VALUE;

                Object klass = getClassNode.execute(object);
                for (PythonAbstractClass c : getMroNode.execute(klass)) {
                    // n.b. we need to use a different node because it makes a difference if the
                    // type is native
                    attr = readTypeAttrNode.execute(c, attrKeyName);
                    if (attr != PNone.NO_VALUE) {
                        owner = c;
                        break;
                    }
                }
                if (attr == PNone.NO_VALUE) {
                    attr = readObjectAttrNode.execute(owner, attrKeyName);
                }

                switch (type) {
                    case READABLE:
                        return attr != PNone.NO_VALUE;
                    case INSERTABLE:
                        return attr == PNone.NO_VALUE && !isImmutable.execute(object);
                    case REMOVABLE:
                        return attr != PNone.NO_VALUE && !isImmutable.execute(owner);
                    case MODIFIABLE:
                        if (attr != PNone.NO_VALUE) {
                            if (owner == object) {
                                // can only modify if the object is not immutable
                                return !isImmutable.execute(owner);
                            } else if (getSetNode.execute(attr, __SET__) == PNone.NO_VALUE) {
                                // an inherited attribute may be overridable unless it's a setter
                                return !isImmutable.execute(object);
                            } else if (getSetNode.execute(attr, __SET__) != PNone.NO_VALUE) {
                                return true;
                            }
                        }
                        return false;
                    case INVOCABLE:
                        if (attr != PNone.NO_VALUE) {
                            if (owner != object) {
                                if (attr instanceof PFunction || attr instanceof PBuiltinFunction) {
                                    // if the attr is a known getter, we mark it invocable
                                    // for other attributes, we look for a __call__ method later
                                    return true;
                                }
                                if (getGetNode.execute(attr, __GET__) != PNone.NO_VALUE) {
                                    // is a getter, read may have side effects, we cannot tell if
                                    // the result will be invocable
                                    return false;
                                }
                            }
                            return dataModelLibrary.isCallable(attr);
                        }
                        return false;
                    case READ_SIDE_EFFECTS:
                        if (attr != PNone.NO_VALUE && owner != object && !(attr instanceof PFunction || attr instanceof PBuiltinFunction)) {
                            // attr is inherited and might be a descriptor object other than a
                            // function
                            return getGetNode.execute(attr, __GET__) != PNone.NO_VALUE;
                        }
                        return false;
                    case WRITE_SIDE_EFFECTS:
                        if (attr != PNone.NO_VALUE && owner != object && !(attr instanceof PFunction || attr instanceof PBuiltinFunction)) {
                            // attr is inherited and might be a descriptor object other than a
                            // function
                            return getSetNode.execute(attr, __SET__) != PNone.NO_VALUE || getDeleteNode.execute(attr, __DELETE__) != PNone.NO_VALUE;
                        }
                        return false;
                    default:
                        return false;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @GenerateUncached
    public abstract static class IsImmutable extends Node {

        public abstract boolean execute(Object object);

        @Specialization
        public boolean isImmutable(Object object,
                        @Cached GetClassNode getClassNode) {
            // TODO(fa) The first condition is too general; we should check if the object's type is
            // 'type'
            if (object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject || PGuards.isNativeClass(object) || PGuards.isNativeObject(object)) {
                return true;
            } else if (object instanceof PythonClass || object instanceof PythonModule) {
                return false;
            } else {
                Object klass = getClassNode.execute(object);
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
                        @Cached ConditionProfile profile) {
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
                        @Cached CallVarargsMethodNode callVarargsMethodNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode) {
            Object[] convertedArgs = convertArgsNode.execute(arguments);
            return callVarargsMethodNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(limit = "1", replaces = "doVarargsBuiltinMethod")
        Object doExecute(Object receiver, Object[] arguments,
                        @CachedLibrary("receiver") PythonObjectLibrary dataModelLibrary,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode) throws UnsupportedMessageException {
            if (!dataModelLibrary.isCallable(receiver)) {
                throw UnsupportedMessageException.create();
            }
            Object[] convertedArgs = convertArgsNode.execute(arguments);
            return callNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS);
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
        long getArraySize(@Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return keys.length;
            } finally {
                gil.release(mustRelease);
            }
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
                        @Cached ConditionProfile hasGetattrProfile) {
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
                        @Cached PForeignToPTypeNode convert,
                        @Cached PInteropGetAttributeNode getAttributeNode,
                        @Cached CallBinaryMethodNode callSetItemNode,
                        @Cached ConditionProfile profile) throws UnsupportedMessageException {

            Object attrSetitem = getAttributeNode.execute(primary, __SETITEM__);
            if (profile.profile(attrSetitem != PNone.NO_VALUE)) {
                callSetItemNode.executeObject(attrSetitem, key, convert.executeConvert(value));
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
        public static void doSpecialObject(PythonAbstractObject primary, String attrName, Object value,
                        @Cached PForeignToPTypeNode convert,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached ConditionProfile profile,
                        @Cached IsBuiltinClassProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
            Object attrSetattr = lookupSetAttrNode.execute(primary, SpecialMethodNames.__SETATTR__);
            if (profile.profile(attrSetattr != PNone.NO_VALUE)) {
                try {
                    callSetAttrNode.execute(null, attrSetattr, primary, attrName, convert.executeConvert(value));
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
                        @Cached ConditionProfile profile) throws UnsupportedMessageException {
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
                        @Cached ConditionProfile profile,
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
                        @Cached ToDisplaySideEffectingNode toDisplayCallnode,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return toDisplayCallnode.execute(receiver);
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(guards = "!allowSideEffects")
        public static String doNonSideEffecting(PythonAbstractObject receiver,
                        boolean allowSideEffects, @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return receiver.toStringBoundary();
            } finally {
                gil.release(mustRelease);
            }
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
    public Object getMetaObject(@Shared("getClassThis") @Cached GetClassNode getClass,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getClass.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public int identityHashCode(@Cached ObjectNodes.GetIdentityHashNode getIdentityHashNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getIdentityHashNode.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    public static int systemHashCode(Object value) {
        return System.identityHashCode(value);
    }

    @ExportMessage
    public TriState isIdenticalOrUndefined(Object otherInterop,
                    @Cached PForeignToPTypeNode convert,
                    @CachedLibrary(limit = "3") InteropLibrary otherLib,
                    @CachedLibrary("this") PythonObjectLibrary objectLib,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object other = convert.executeConvert(otherInterop);
            if (this == other) {
                return TriState.TRUE;
            } else if (otherLib.hasIdentity(other)) {
                return objectLib.isSame(this, other) ? TriState.TRUE : TriState.FALSE;
            } else {
                return TriState.UNDEFINED;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    /**
     * Unfortunately, this must be defined on the abstract type and we can only have special
     * implementations on types that cannot be subclassed. This is because we don't do inheritance
     * in the same way as CPython. They just install function {@code typeobject.c:slot_tp_iter} to
     * {@code tp_iter} for every user class.
     */
    @ExportMessage
    public static class GetIteratorWithState {
        public static ValueProfile createIterMethodProfile() {
            if (singleContextAssumption().isValid()) {
                return ValueProfile.createIdentityProfile();
            } else {
                return ValueProfile.createClassProfile();
            }
        }

        @Specialization
        public static Object getIteratorWithState(PythonAbstractObject self, ThreadState state,
                        @Cached("createIterMethodProfile()") ValueProfile iterMethodProfile,
                        @CachedLibrary("self") PythonObjectLibrary plib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                        @Cached IteratorNodes.IsIteratorObjectNode isIteratorObjectNode,
                        @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            Object v = plib.getDelegatedValue(self);
            Object iterMethod = iterMethodProfile.profile(plib.lookupAttributeOnType(self, __ITER__));
            if (iterMethod != PNone.NONE) {
                if (iterMethod != PNone.NO_VALUE) {
                    Object iterObj = methodLib.callUnboundMethodIgnoreGetExceptionWithState(iterMethod, state, v);
                    if (iterObj != PNone.NO_VALUE && isIteratorObjectNode.execute(iterObj)) {
                        return iterObj;
                    }
                } else {
                    Object getItemAttrObj = plib.lookupAttributeOnType(self, __GETITEM__);
                    if (getItemAttrObj != PNone.NO_VALUE) {
                        return factory.createSequenceIterator(v);
                    }
                }
            }
            throw raise.raise(PythonErrorType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, self);
        }
    }

    @ExportMessage
    public boolean hasIterator(
                    @CachedLibrary("this") PythonObjectLibrary lib) {
        return lib.isIterable(this);
    }

    @ExportMessage
    public Object getIterator(
                    @CachedLibrary("this") PythonObjectLibrary lib) throws UnsupportedMessageException {
        if (lib.isIterable(this)) {
            return lib.getIterator(this);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public boolean isIterator(
                    @CachedLibrary("this") PythonObjectLibrary lib) {
        return lib.lookupAttributeOnType(this, __NEXT__) != PNone.NO_VALUE;
    }

    private static final HiddenKey NEXT_ELEMENT = new HiddenKey("next_element");

    @ExportMessage
    public boolean hasIteratorNextElement(
                    @CachedLibrary("this") InteropLibrary ilib,
                    @CachedLibrary("this") PythonObjectLibrary plib,
                    @Shared("dylib") @CachedLibrary(limit = "2") DynamicObjectLibrary dylib,
                    @Exclusive @Cached IsBuiltinClassProfile exceptionProfile) throws UnsupportedMessageException {
        if (ilib.isIterator(this)) {
            Object nextElement = dylib.getOrDefault(this, NEXT_ELEMENT, null);
            if (nextElement != null) {
                return true;
            }
            try {
                nextElement = plib.lookupAndCallSpecialMethod(this, null, __NEXT__);
                dylib.put(this, NEXT_ELEMENT, nextElement);
                return true;
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.StopIteration, exceptionProfile);
                return false;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public Object getIteratorNextElement(
                    @CachedLibrary("this") InteropLibrary ilib,
                    @Shared("dylib") @CachedLibrary(limit = "2") DynamicObjectLibrary dylib) throws StopIterationException, UnsupportedMessageException {
        if (ilib.hasIteratorNextElement(this)) {
            Object nextElement = dylib.getOrDefault(this, NEXT_ELEMENT, null);
            dylib.put(this, NEXT_ELEMENT, null);
            return nextElement;
        } else {
            throw StopIterationException.create();
        }
    }
}
