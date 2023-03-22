/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATETIME;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRUCT_TIME;
import static com.oracle.graal.python.nodes.StringLiterals.T_TIME;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObjectFactory.PInteropGetAttributeNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListInteropNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.RegionEqualNode;
import com.oracle.truffle.api.utilities.TriState;

@ImportStatic(SpecialMethodNames.class)
@ExportLibrary(InteropLibrary.class)
public abstract class PythonAbstractObject extends DynamicObject implements TruffleObject, Comparable<Object> {

    private static final TruffleString T_PRIVATE_PREFIX = tsLiteral("__");
    private static final int PRIVATE_PREFIX_LENGTH = T_PRIVATE_PREFIX.codePointLengthUncached(TS_ENCODING);
    private PythonNativeWrapper nativeWrapper;

    // @ImportStatic doesn't work for this for some reason
    protected static final SpecialMethodSlot Iter = SpecialMethodSlot.Iter;
    protected static final SpecialMethodSlot Next = SpecialMethodSlot.Next;
    protected static final SpecialMethodSlot Len = SpecialMethodSlot.Len;

    protected static final Shape ABSTRACT_SHAPE = Shape.newBuilder().build();

    protected PythonAbstractObject(Shape shape) {
        super(shape);
    }

    protected PythonAbstractObject() {
        super(ABSTRACT_SHAPE);
    }

    public final PythonNativeWrapper getNativeWrapper() {
        return nativeWrapper;
    }

    public final void setNativeWrapper(PythonNativeWrapper nativeWrapper) {
        assert this.nativeWrapper == null;

        // we must not set the native wrapper for one of the context-insensitive singletons
        assert !CApiGuards.isSpecialSingleton(this);

        this.nativeWrapper = nativeWrapper;
    }

    public final void clearNativeWrapper() {
        nativeWrapper = null;
    }

    @ExportMessage
    public void writeMember(String key, Object value,
                    @Bind("$node") Node inliningTarget,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Cached PInteropSetAttributeNode setAttributeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached IsBuiltinObjectProfile attrErrorProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            setAttributeNode.execute(this, fromJavaStringNode.execute(key, TS_ENCODING), value);
        } catch (PException e) {
            e.expectAttributeError(inliningTarget, attrErrorProfile);
            // TODO(fa) not accurate; distinguish between read-only and non-existing
            throw UnknownIdentifierException.create(key);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object readMember(String key,
                    @Bind("$node") Node inliningTarget,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectLookupAttr lookup,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        Object value;
        try {
            value = lookup.execute(null, inliningTarget, this, fromJavaStringNode.execute(key, TS_ENCODING));
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
                    @Bind("$node") Node inliningTarget,
                    @Cached PySequenceCheckNode check,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Cached(parameters = "Len") LookupCallableSlotInMRONode lookupLen,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return check.execute(inliningTarget, this) && lookupLen.execute(getClassNode.executeCached(this)) != PNone.NO_VALUE;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object readArrayElement(long key,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            if (interopLib.hasArrayElements(this)) {
                try {
                    return getItemNode.execute(this, key);
                } catch (PException e) {
                    throw InvalidArrayIndexException.create(key);
                }
            }
            throw UnsupportedMessageException.create();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void writeArrayElement(long key, Object value,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Cached PInteropSubscriptAssignNode setItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            if (interopLib.hasArrayElements(this)) {
                try {
                    setItemNode.execute(this, key, value);
                } catch (PException e) {
                    throw InvalidArrayIndexException.create(key);
                }
            }
            throw UnsupportedMessageException.create();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void removeArrayElement(long key,
                    @Bind("$node") Node inliningTarget,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Exclusive @Cached PInteropDeleteItemNode deleteItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            if (interopLib.hasArrayElements(this)) {
                try {
                    deleteItemNode.execute(inliningTarget, this, key);
                } catch (PException e) {
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
    public long getArraySize(
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectSizeNode sizeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        if (!interopLib.hasArrayElements(this)) {
            throw UnsupportedMessageException.create();
        }
        try {
            long len = sizeNode.execute(null, inliningTarget, this);
            if (len >= 0) {
                return len;
            }
        } finally {
            gil.release(mustRelease);
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isArrayElementReadable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectSizeNode sizeNode,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        if (!interopLib.hasArrayElements(this)) {
            return false;
        }
        try {
            return isInBounds(sizeNode.execute(null, inliningTarget, this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementModifiable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectSizeNode sizeNode,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        if (!interopLib.hasArrayElements(this)) {
            return false;
        }
        try {
            return !(this instanceof PTuple) && !(this instanceof PBytes) && isInBounds(sizeNode.execute(null, inliningTarget, this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementInsertable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectSizeNode sizeNode,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        if (!interopLib.hasArrayElements(this)) {
            return false;
        }
        try {
            return !(this instanceof PTuple) && !(this instanceof PBytes) && !isInBounds(sizeNode.execute(null, inliningTarget, this), getItemNode, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementRemovable(@SuppressWarnings("unused") long idx,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectSizeNode sizeNode,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        if (!interopLib.hasArrayElements(this)) {
            return false;
        }
        try {
            return !(this instanceof PTuple) && !(this instanceof PBytes) && isInBounds(sizeNode.execute(null, inliningTarget, this), getItemNode, idx);
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
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.READABLE);
    }

    @ExportMessage
    public boolean isMemberModifiable(String member,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.MODIFIABLE);
    }

    @ExportMessage
    public boolean isMemberInsertable(String member,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.INSERTABLE);
    }

    @ExportMessage
    public boolean isMemberInvocable(String member,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.INVOCABLE);
    }

    @ExportMessage
    public boolean isMemberRemovable(String member,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.REMOVABLE);
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String member,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.READ_SIDE_EFFECTS);
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String member,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared("keyInfoNode") @Cached PKeyInfoNode keyInfoNode) {
        // TODO write specialized nodes for the appropriate property
        return keyInfoNode.execute(this, fromJavaStringNode.execute(member, TS_ENCODING), PKeyInfoNode.WRITE_SIDE_EFFECTS);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Bind("$node") Node inliningTarget,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached CallBinaryMethodNode callGetattributeNode,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached InlinedConditionProfile profileGetattribute,
                    @Exclusive @Cached InlinedConditionProfile profileMember,
                    // GR-44020: make shared:
                    @Exclusive @Cached IsBuiltinObjectProfile attributeErrorProfile,
                    @Exclusive @Cached GilNode gil)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object memberObj;
            try {
                Object attrGetattribute = lookupGetattributeNode.execute(inliningTarget, this, T___GETATTRIBUTE__);
                if (profileGetattribute.profile(inliningTarget, attrGetattribute == PNone.NO_VALUE)) {
                    throw UnknownIdentifierException.create(member);
                }
                memberObj = callGetattributeNode.executeObject(attrGetattribute, this, fromJavaStringNode.execute(member, TS_ENCODING));
                if (profileMember.profile(inliningTarget, memberObj == PNone.NO_VALUE)) {
                    throw UnknownIdentifierException.create(member);
                }
            } catch (PException e) {
                e.expect(inliningTarget, AttributeError, attributeErrorProfile);
                throw UnknownIdentifierException.create(member);
            }
            return executeNode.execute(memberObj, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isExecutable(
                    @Bind("$node") Node inliningTarget,
                    @Cached PyCallableCheckNode callableCheck) {
        return callableCheck.execute(inliningTarget, this);
    }

    @ExportMessage
    public Object execute(Object[] arguments,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            return executeNode.execute(this, arguments);
        } catch (PException e) {
            throw e;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            e.printStackTrace();
            throw e;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @TruffleBoundary
    public Object getMembers(boolean includeInternal,
                    @Bind("$node") Node inliningTarget,
                    @Cached CastToListInteropNode castToList,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClass,
                    @Cached PyMappingCheckNode checkMapping,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectLookupAttr lookupKeys,
                    @Cached CallNode callKeys,
                    @Shared("getItemNode") @Cached PInteropSubscriptNode getItemNode,
                    @Cached SequenceNodes.LenNode lenNode,
                    @Cached TypeNodes.GetMroNode getMroNode,
                    @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                    @Cached TruffleString.RegionEqualNode regionEqualNode,
                    @Cached TruffleString.ConcatNode concatNode,
                    @Cached StringMaterializeNode materializeNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            HashSet<TruffleString> keys = new HashSet<>();
            Object klass = getClass.executeCached(this);
            for (PythonAbstractClass o : getMroNode.execute(inliningTarget, klass)) {
                if (o instanceof PythonManagedClass) {
                    addKeysFromObject(keys, (PythonManagedClass) o, includeInternal, codePointLengthNode, regionEqualNode);
                }
                // TODO handle native class
            }
            if (this instanceof PythonObject) {
                addKeysFromObject(keys, (PythonObject) this, includeInternal, codePointLengthNode, regionEqualNode);
            }
            if (includeInternal) {
                // we use the internal flag to also return dictionary keys for mappings
                if (checkMapping.execute(inliningTarget, this)) {
                    Object keysMethod = lookupKeys.execute(null, inliningTarget, this, T_KEYS);
                    if (keysMethod != PNone.NO_VALUE) {
                        PList mapKeys = castToList.executeWithGlobalState(callKeys.execute(keysMethod));
                        int len = lenNode.execute(inliningTarget, mapKeys);
                        for (int i = 0; i < len; i++) {
                            Object key = getItemNode.execute(mapKeys, i);
                            TruffleString tsKey = null;
                            if (key instanceof TruffleString) {
                                tsKey = (TruffleString) key;
                            } else if (isJavaString(key)) {
                                tsKey = toTruffleStringUncached((String) key);
                            } else if (key instanceof PString) {
                                tsKey = materializeNode.execute(inliningTarget, (PString) key);
                            }
                            if (tsKey != null) {
                                keys.add(concatNode.execute(tsKey, T_LBRACKET, TS_ENCODING, false));
                            }
                        }
                    }
                }
            }

            return new Keys(keys.toArray(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY));
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void removeMember(String member,
                    @Bind("$node") Node inliningTarget,
                    @Cached PInteropDeleteAttributeNode deleteAttributeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached IsBuiltinObjectProfile attrErrorProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            deleteAttributeNode.execute(this, member);
        } catch (PException e) {
            e.expectAttributeError(inliningTarget, attrErrorProfile);
            // TODO(fa) not accurate; distinguish between read-only and non-existing
            throw UnknownIdentifierException.create(member);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isInstantiable(
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isTypeNode.execute(inliningTarget, this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object instantiate(Object[] arguments,
                    @CachedLibrary("this") InteropLibrary interopLib,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        if (!interopLib.isInstantiable(this)) {
            throw UnsupportedMessageException.create();
        }
        try {
            return executeNode.execute(this, arguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    private static void addKeysFromObject(HashSet<TruffleString> keys, PythonObject o, boolean includeInternal, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.RegionEqualNode regionEqualNode) {
        HashingStorage dictStorage;
        PDict dict = GetDictIfExistsNode.getUncached().execute(o);
        if (dict != null) {
            dictStorage = dict.getDictStorage();
        } else {
            dictStorage = new DynamicObjectStorage(o); // temporary wrapper makes the rest of the
                                                       // code easier
        }
        HashingStorageIterator it = HashingStorageGetIterator.executeUncached(dictStorage);
        while (HashingStorageIteratorNext.executeUncached(dictStorage, it)) {
            TruffleString strKey;
            Object key = HashingStorageIteratorKey.executeUncached(dictStorage, it);
            if (key instanceof TruffleString) {
                strKey = (TruffleString) key;
            } else if (isJavaString(key)) {
                strKey = toTruffleStringUncached((String) key);
            } else {
                continue;
            }
            if (includeInternal || !startsWithPrivatePrefix(strKey, codePointLengthNode, regionEqualNode)) {
                keys.add(strKey);
            }
        }
    }

    private static boolean startsWithPrivatePrefix(TruffleString strKey, TruffleString.CodePointLengthNode codePointLengthNode, RegionEqualNode regionEqualNode) {
        int strLen = codePointLengthNode.execute(strKey, TS_ENCODING);
        return strLen >= PRIVATE_PREFIX_LENGTH && regionEqualNode.execute(strKey, 0, T_PRIVATE_PREFIX, 0, PRIVATE_PREFIX_LENGTH, TS_ENCODING);
    }

    private static final TruffleString T_DATETIME_MODULE_NAME = T_DATETIME;
    private static final TruffleString T_TIME_MODULE_NAME = T_TIME;
    private static final TruffleString T_DATE_TYPE = T_DATE;
    private static final TruffleString T_DATETIME_TYPE = T_DATETIME;
    private static final TruffleString T_TIME_TYPE = T_TIME;
    private static final TruffleString T_STRUCT_TIME_TYPE = T_STRUCT_TIME;

    private static Object readType(Node inliningTarget, ReadAttributeFromObjectNode readTypeNode, Object module, TruffleString typename, TypeNodes.IsTypeNode isTypeNode) {
        Object type = readTypeNode.execute(module, typename);
        if (isTypeNode.execute(inliningTarget, type)) {
            return type;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, ErrorMessages.PATCHED_DATETIME_CLASS, type);
        }
    }

    @ExportMessage
    public boolean isDate(
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    // GR-44020: use inlined:
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile dateTimeModuleLoaded,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.executeCached(this);
            PDict importedModules = PythonContext.get(getClassNode).getSysModules();
            Object module = importedModules.getItem(T_DATETIME_MODULE_NAME);
            if (dateTimeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATETIME_TYPE, isTypeNode)) ||
                                isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATE_TYPE, isTypeNode))) {
                    return true;
                }
            }
            module = importedModules.getItem(T_TIME_MODULE_NAME);
            if (timeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_STRUCT_TIME_TYPE, isTypeNode))) {
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
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary("this") InteropLibrary lib,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile dateTimeModuleLoaded,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.executeCached(this);
            PDict importedModules = PythonContext.get(getClassNode).getSysModules();
            Object module = importedModules.getItem(T_DATETIME_MODULE_NAME);
            if (dateTimeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATETIME_TYPE, isTypeNode)) ||
                                isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATE_TYPE, isTypeNode))) {
                    try {
                        int year = castToIntNode.execute(inliningTarget, lib.readMember(this, "year"));
                        int month = castToIntNode.execute(inliningTarget, lib.readMember(this, "month"));
                        int day = castToIntNode.execute(inliningTarget, lib.readMember(this, "day"));
                        return createLocalDate(year, month, day);
                    } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                        throw UnsupportedMessageException.create();
                    }
                }
            }
            module = importedModules.getItem(T_TIME_MODULE_NAME);
            if (timeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_STRUCT_TIME_TYPE, isTypeNode))) {
                    try {
                        int year = castToIntNode.execute(inliningTarget, lib.readMember(this, "tm_year"));
                        int month = castToIntNode.execute(inliningTarget, lib.readMember(this, "tm_mon"));
                        int day = castToIntNode.execute(inliningTarget, lib.readMember(this, "tm_mday"));
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
    public boolean isTime(
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    // GR-44020: use inlined:
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtype,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile dateTimeModuleLoaded,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.executeCached(this);
            PDict importedModules = PythonContext.get(getClassNode).getSysModules();
            Object module = importedModules.getItem(T_DATETIME_MODULE_NAME);
            if (dateTimeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtype.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATETIME_TYPE, isTypeNode)) ||
                                isSubtype.execute(objType, readType(inliningTarget, readTypeNode, module, T_TIME_TYPE, isTypeNode))) {
                    return true;
                }
            }
            module = importedModules.getItem(T_TIME_MODULE_NAME);
            if (timeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtype.execute(objType, readType(inliningTarget, readTypeNode, module, T_STRUCT_TIME_TYPE, isTypeNode))) {
                    return true;
                }
            }
            return false;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public LocalTime asTime(
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    // GR-44020: use inlined:
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode,
                    @CachedLibrary("this") InteropLibrary lib,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile dateTimeModuleLoaded,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.executeCached(this);
            PDict importedModules = PythonContext.get(getClassNode).getSysModules();
            Object module = importedModules.getItem(T_DATETIME_MODULE_NAME);
            if (dateTimeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATETIME_TYPE, isTypeNode)) ||
                                isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_TIME_TYPE, isTypeNode))) {
                    try {
                        int hour = castToIntNode.execute(inliningTarget, lib.readMember(this, "hour"));
                        int min = castToIntNode.execute(inliningTarget, lib.readMember(this, "minute"));
                        int sec = castToIntNode.execute(inliningTarget, lib.readMember(this, "second"));
                        int micro = castToIntNode.execute(inliningTarget, lib.readMember(this, "microsecond"));
                        return createLocalTime(hour, min, sec, micro);
                    } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                        throw UnsupportedMessageException.create();
                    }
                }
            }
            module = importedModules.getItem(T_TIME_MODULE_NAME);
            if (timeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_STRUCT_TIME_TYPE, isTypeNode))) {
                    try {
                        int hour = castToIntNode.execute(inliningTarget, lib.readMember(this, "tm_hour"));
                        int min = castToIntNode.execute(inliningTarget, lib.readMember(this, "tm_min"));
                        int sec = castToIntNode.execute(inliningTarget, lib.readMember(this, "tm_sec"));
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
    public boolean isTimeZone(
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtype,
                    @CachedLibrary(limit = "2") InteropLibrary lib,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile dateTimeModuleLoaded,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile timeModuleLoaded,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object objType = getClassNode.executeCached(this);
            PDict importedModules = PythonContext.get(getClassNode).getSysModules();
            Object module = importedModules.getItem(T_DATETIME_MODULE_NAME);
            if (dateTimeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtype.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATETIME_TYPE, isTypeNode))) {
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
                } else if (isSubtype.execute(objType, readType(inliningTarget, readTypeNode, module, T_TIME_TYPE, isTypeNode))) {
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
            module = importedModules.getItem(T_TIME_MODULE_NAME);
            if (timeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtype.execute(objType, readType(inliningTarget, readTypeNode, module, T_STRUCT_TIME_TYPE, isTypeNode))) {
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
    public ZoneId asTimeZone(
                    @Bind("$node") Node inliningTarget,
                    // GR-44020: use inlined:
                    @Shared("isTypeNode") @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Shared("readTypeNode") @Cached ReadAttributeFromObjectNode readTypeNode,
                    @Shared("isSubtypeNode") @Cached IsSubtypeNode isSubtypeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode,
                    @Exclusive @CachedLibrary(limit = "3") InteropLibrary lib,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile dateTimeModuleLoaded,
                    // GR-44020: make shared:
                    @Exclusive @Cached InlinedConditionProfile timeModuleLoaded,
                    @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!lib.isTimeZone(this)) {
                throw UnsupportedMessageException.create();
            }
            Object objType = getClassNode.executeCached(this);
            PDict importedModules = PythonContext.get(getClassNode).getSysModules();
            Object module = importedModules.getItem(T_DATETIME_MODULE_NAME);
            if (dateTimeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_DATETIME_TYPE, isTypeNode))) {
                    try {
                        Object tzinfo = lib.readMember(this, "tzinfo");
                        if (tzinfo != PNone.NONE) {
                            Object delta = lib.invokeMember(tzinfo, "utcoffset", new Object[]{this});
                            if (delta != PNone.NONE) {
                                int seconds = castToIntNode.execute(inliningTarget, lib.readMember(delta, "seconds"));
                                return createZoneId(seconds);
                            }
                        }
                    } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException ex) {
                        throw UnsupportedMessageException.create();
                    }
                } else if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_TIME_TYPE, isTypeNode))) {
                    try {
                        Object tzinfo = lib.readMember(this, "tzinfo");
                        if (tzinfo != PNone.NONE) {
                            Object delta = lib.invokeMember(tzinfo, "utcoffset", new Object[]{PNone.NONE});
                            if (delta != PNone.NONE) {
                                int seconds = castToIntNode.execute(inliningTarget, lib.readMember(delta, "seconds"));
                                return createZoneId(seconds);
                            }
                        }
                    } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException ex) {
                        throw UnsupportedMessageException.create();
                    }
                }
            }
            module = importedModules.getItem(T_TIME_MODULE_NAME);
            if (timeModuleLoaded.profile(inliningTarget, module != null)) {
                if (isSubtypeNode.execute(objType, readType(inliningTarget, readTypeNode, module, T_STRUCT_TIME_TYPE, isTypeNode))) {
                    try {
                        Object tm_zone = lib.readMember(this, "tm_zone");
                        if (tm_zone != PNone.NONE) {
                            Object tm_gmtoffset = lib.readMember(this, "tm_gmtoff");
                            if (tm_gmtoffset != PNone.NONE) {
                                int seconds = castToIntNode.execute(inliningTarget, tm_gmtoffset);
                                return createZoneId(seconds);
                            }
                            if (tm_zone instanceof TruffleString) {
                                return createZoneId(toJavaStringNode.execute((TruffleString) tm_zone));
                            }
                            if (isJavaString(tm_zone)) {
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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 80 -> 62
    public abstract static class PKeyInfoNode extends Node {
        private static final int READABLE = 0x1;
        private static final int READ_SIDE_EFFECTS = 0x2;
        private static final int WRITE_SIDE_EFFECTS = 0x4;
        private static final int MODIFIABLE = 0x8;
        private static final int REMOVABLE = 0x10;
        private static final int INVOCABLE = 0x20;
        private static final int INSERTABLE = 0x40;

        public abstract boolean execute(Object receiver, TruffleString fieldName, int infoType);

        @Specialization
        static boolean access(Object object, TruffleString attrKeyName, int type,
                        @Bind("this") Node inliningTarget,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readTypeAttrNode,
                        @Cached ReadAttributeFromObjectNode readObjectAttrNode,
                        @Cached PyCallableCheckNode callableCheck,
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

                Object klass = getClassNode.execute(inliningTarget, object);
                for (PythonAbstractClass c : getMroNode.execute(inliningTarget, klass)) {
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
                        return attr == PNone.NO_VALUE && !isImmutable.execute(inliningTarget, object);
                    case REMOVABLE:
                        return attr != PNone.NO_VALUE && !isImmutable.execute(inliningTarget, owner);
                    case MODIFIABLE:
                        if (attr != PNone.NO_VALUE) {
                            if (owner == object) {
                                // can only modify if the object is not immutable
                                return !isImmutable.execute(inliningTarget, owner);
                            } else if (getSetNode.execute(inliningTarget, attr, T___SET__) == PNone.NO_VALUE) {
                                // an inherited attribute may be overridable unless it's a setter
                                return !isImmutable.execute(inliningTarget, object);
                            } else if (getSetNode.execute(inliningTarget, attr, T___SET__) != PNone.NO_VALUE) {
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
                                if (getGetNode.execute(inliningTarget, attr, T___GET__) != PNone.NO_VALUE) {
                                    // is a getter, read may have side effects, we cannot tell if
                                    // the result will be invocable
                                    return false;
                                }
                            }
                            return callableCheck.execute(inliningTarget, attr);
                        }
                        return false;
                    case READ_SIDE_EFFECTS:
                        if (attr != PNone.NO_VALUE && owner != object && !(attr instanceof PFunction || attr instanceof PBuiltinFunction)) {
                            // attr is inherited and might be a descriptor object other than a
                            // function
                            return getGetNode.execute(inliningTarget, attr, T___GET__) != PNone.NO_VALUE;
                        }
                        return false;
                    case WRITE_SIDE_EFFECTS:
                        if (attr != PNone.NO_VALUE && owner != object && !(attr instanceof PFunction || attr instanceof PBuiltinFunction)) {
                            // attr is inherited and might be a descriptor object other than a
                            // function
                            return getSetNode.execute(inliningTarget, attr, T___SET__) != PNone.NO_VALUE || getDeleteNode.execute(inliningTarget, attr, T___DELETE__) != PNone.NO_VALUE;
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class IsImmutable extends Node {

        public abstract boolean execute(Node inliningTarget, Object object);

        @Specialization
        public static boolean isImmutable(Node inliningTarget, Object object,
                        @Cached GetClassNode getClassNode) {
            // TODO(fa) The first condition is too general; we should check if the object's type is
            // 'type'
            if (object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject || PGuards.isNativeClass(object) || PGuards.isNativeObject(object)) {
                return true;
            } else if (object instanceof PythonClass || object instanceof PythonModule) {
                return false;
            } else {
                Object klass = getClassNode.execute(inliningTarget, object);
                return klass instanceof PythonBuiltinClassType || klass instanceof PythonBuiltinClass || PGuards.isNativeClass(object);
            }
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.subscript.GetItemNode' but with an
     * uncached version.
     */
    @GenerateUncached
    @ImportStatic(SpecialMethodSlot.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 19
    public abstract static class PInteropSubscriptNode extends Node {

        public abstract Object execute(Object primary, Object index);

        @Specialization
        static Object doSpecialObject(Object primary, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupInMRONode,
                        @Cached CallBinaryMethodNode callGetItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached InlinedConditionProfile profile) {
            Object attrGetItem = lookupInMRONode.execute(getClassNode.execute(inliningTarget, primary));
            if (profile.profile(inliningTarget, attrGetItem == PNone.NO_VALUE)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, primary);
            }
            return callGetItemNode.executeObject(attrGetItem, primary, index);
        }

        public static PInteropSubscriptNode getUncached() {
            return PythonAbstractObjectFactory.PInteropSubscriptNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @ReportPolymorphism
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
    public abstract static class PExecuteNode extends Node {

        public abstract Object execute(Object receiver, Object[] arguments) throws UnsupportedMessageException;

        @Specialization(guards = {"isBuiltinFunctionOrMethod(receiver)"})
        Object doVarargsBuiltinMethod(Object receiver, Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached CallVarargsMethodNode callVarargsMethodNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode) {
            Object[] convertedArgs = convertArgsNode.execute(inliningTarget, arguments);
            return callVarargsMethodNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(replaces = "doVarargsBuiltinMethod")
        static Object doExecute(Object receiver, Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode) throws UnsupportedMessageException {
            if (!callableCheck.execute(inliningTarget, receiver)) {
                throw UnsupportedMessageException.create();
            }
            Object[] convertedArgs = convertArgsNode.execute(inliningTarget, arguments);
            return callNode.execute(null, receiver, convertedArgs, PKeyword.EMPTY_KEYWORDS);
        }

        static boolean isBuiltinFunctionOrMethod(Object object) {
            return object instanceof PBuiltinMethod || object instanceof PBuiltinFunction;
        }

        public static PExecuteNode getUncached() {
            return PythonAbstractObjectFactory.PExecuteNodeGen.getUncached();
        }

        @Override
        public Node copy() {
            return PythonAbstractObjectFactory.PExecuteNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ArgumentsFromForeignNode extends Node {

        public abstract Object[] execute(Node inliningTarget, Object[] arguments);

        @Specialization(guards = {"arguments.length == cachedLen", "cachedLen < 6"}, limit = "3")
        @ExplodeLoop
        static Object[] cached(Object[] arguments,
                        @Shared @Cached(inline = false) PForeignToPTypeNode fromForeign,
                        @Cached("arguments.length") int cachedLen) {
            Object[] convertedArgs = new Object[cachedLen];
            for (int i = 0; i < cachedLen; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }
            return convertedArgs;
        }

        @Specialization(replaces = "cached")
        static Object[] generic(Object[] arguments,
                        @Shared @Cached(inline = false) PForeignToPTypeNode fromForeign) {
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }
            return convertedArgs;
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PInteropGetAttributeNode extends Node {

        public abstract Object execute(Node inliningTarget, Object object, Object attrName);

        public static Object executeUncached(Object object, Object attrName) {
            return PInteropGetAttributeNodeGen.getUncached().execute(null, object, attrName);
        }

        @Specialization(limit = "2")
        static Object doIt(Node inliningTarget, Object object, Object attrName,
                        @CachedLibrary("attrName") InteropLibrary libAttrName,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                        @Cached(inline = false) CallBinaryMethodNode callGetattributeNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetattrNode,
                        @Cached(inline = false) CallBinaryMethodNode callGetattrNode,
                        @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached IsBuiltinObjectProfile isBuiltinClassProfile,
                        @Cached InlinedConditionProfile hasGetattrProfile) {
            if (!libAttrName.isString(attrName)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, attrName);
            }

            TruffleString attrNameStr;
            try {
                attrNameStr = switchEncodingNode.execute(libAttrName.asTruffleString(attrName), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }

            try {
                Object attrGetattribute = lookupGetattributeNode.execute(inliningTarget, object, T___GETATTRIBUTE__);
                return callGetattributeNode.executeObject(attrGetattribute, object, attrNameStr);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, isBuiltinClassProfile);
                Object attrGetattr = lookupGetattrNode.execute(inliningTarget, object, T___GETATTR__);
                if (hasGetattrProfile.profile(inliningTarget, attrGetattr != PNone.NO_VALUE)) {
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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
    public abstract static class PInteropSubscriptAssignNode extends Node {

        public abstract void execute(PythonAbstractObject primary, Object key, Object value) throws UnsupportedMessageException;

        @Specialization
        static void doSpecialObject(PythonAbstractObject primary, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PForeignToPTypeNode convert,
                        @Cached PInteropGetAttributeNode getAttributeNode,
                        @Cached CallBinaryMethodNode callSetItemNode,
                        @Cached InlinedConditionProfile profile) throws UnsupportedMessageException {

            Object attrSetitem = getAttributeNode.execute(inliningTarget, primary, T___SETITEM__);
            if (profile.profile(inliningTarget, attrSetitem != PNone.NO_VALUE)) {
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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 19
    public abstract static class PInteropSetAttributeNode extends Node {

        public abstract void execute(Object primary, TruffleString attrName, Object value) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization
        public static void doSpecialObject(PythonAbstractObject primary, TruffleString attrName, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PForeignToPTypeNode convert,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached InlinedConditionProfile profile,
                        @Cached IsBuiltinObjectProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
            Object attrSetattr = lookupSetAttrNode.execute(inliningTarget, primary, SpecialMethodNames.T___SETATTR__);
            if (profile.profile(inliningTarget, attrSetattr != PNone.NO_VALUE)) {
                try {
                    callSetAttrNode.execute(null, attrSetattr, primary, attrName, convert.executeConvert(value));
                } catch (PException e) {
                    e.expectAttributeError(inliningTarget, attrErrorProfile);
                    // TODO(fa) not accurate; distinguish between read-only and non-existing
                    throw UnknownIdentifierException.create(attrName.toJavaStringUncached());
                }
            } else {
                throw UnsupportedMessageException.create();
            }
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PInteropDeleteItemNode extends Node {

        public abstract void execute(Node inliningTarget, Object primary, Object key) throws UnsupportedMessageException;

        @Specialization
        public static void doSpecialObject(Node inliningTarget, PythonAbstractObject primary, Object key,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached(inline = false) CallBinaryMethodNode callSetAttrNode,
                        @Cached InlinedConditionProfile profile) throws UnsupportedMessageException {
            Object attrDelattr = lookupSetAttrNode.execute(inliningTarget, primary, T___DELITEM__);
            if (profile.profile(inliningTarget, attrDelattr != PNone.NO_VALUE)) {
                callSetAttrNode.executeObject(attrDelattr, primary, key);
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }

    /*
     * Basically the same as 'com.oracle.graal.python.nodes.attributes.DeleteAttributeNode' but with
     * an uncached version.
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 19
    public abstract static class PInteropDeleteAttributeNode extends Node {

        public abstract void execute(Object primary, String attrName) throws UnsupportedMessageException, UnknownIdentifierException;

        @Specialization
        public void doSpecialObject(PythonAbstractObject primary, String attrName,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallBinaryMethodNode callSetAttrNode,
                        @Cached InlinedConditionProfile profile,
                        @Cached IsBuiltinObjectProfile attrErrorProfile) throws UnsupportedMessageException, UnknownIdentifierException {
            Object attrDelattr = lookupSetAttrNode.execute(inliningTarget, primary, SpecialMethodNames.T___DELATTR__);
            if (profile.profile(inliningTarget, attrDelattr != PNone.NO_VALUE)) {
                try {
                    callSetAttrNode.executeObject(attrDelattr, primary, fromJavaStringNode.execute(attrName, TS_ENCODING));
                } catch (PException e) {
                    e.expectAttributeError(inliningTarget, attrErrorProfile);
                    // TODO(fa) not accurate; distinguish between read-only and non-existing
                    throw UnknownIdentifierException.create(attrName);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        public static PInteropDeleteAttributeNode getUncached() {
            return PythonAbstractObjectFactory.PInteropDeleteAttributeNodeGen.getUncached();
        }
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToDisplaySideEffectingNode extends Node {

        public abstract TruffleString execute(Node inliningTarget, PythonAbstractObject receiver);

        @Specialization
        public static TruffleString doDefault(Node inliningTarget, PythonAbstractObject receiver,
                        @Cached(inline = false) ReadAttributeFromObjectNode readStr,
                        @Cached(inline = false) CallNode callNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached InlinedConditionProfile toStringUsed) {
            Object toStrAttr;
            TruffleString names;
            PythonContext context = PythonContext.get(inliningTarget);
            if (context.getOption(PythonOptions.UseReprForPrintString)) {
                names = BuiltinNames.T_REPR;
            } else {
                names = BuiltinNames.T_STR;
            }

            TruffleString result = null;
            PythonModule builtins = context.getBuiltins();
            if (toStringUsed.profile(inliningTarget, builtins != null)) {
                toStrAttr = readStr.execute(builtins, names);
                try {
                    result = castStr.execute(inliningTarget, callNode.execute(toStrAttr, receiver));
                } catch (CannotCastException e) {
                    // do nothing
                }
            }
            if (toStringUsed.profile(inliningTarget, result != null)) {
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
        public static TruffleString doSideEffecting(PythonAbstractObject receiver, boolean allowSideEffects,
                        @Bind("$node") Node inliningTarget,
                        @Cached ToDisplaySideEffectingNode toDisplayCallNode,
                        @Exclusive @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return toDisplayCallNode.execute(inliningTarget, receiver);
            } finally {
                gil.release(mustRelease);
            }
        }

        @Specialization(guards = "!allowSideEffects")
        public static TruffleString doNonSideEffecting(PythonAbstractObject receiver,
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
    final TruffleString toStringBoundary() {
        return toTruffleStringUncached(toString());
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public Object getMetaObject(
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClass,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getClass.executeCached(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public int identityHashCode(
                    @Bind("$node") Node inliningTarget,
                    @Cached ObjectNodes.GetIdentityHashNode getIdentityHashNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getIdentityHashNode.execute(inliningTarget, this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    public static int systemHashCode(Object value) {
        return System.identityHashCode(value);
    }

    @TruffleBoundary
    public static String systemHashCodeAsHexString(Object value) {
        return Integer.toHexString(System.identityHashCode(value));
    }

    @TruffleBoundary
    public static int objectHashCode(Object value) {
        return value.hashCode();
    }

    @TruffleBoundary
    public static String objectHashCodeAsHexString(Object value) {
        return Integer.toHexString(value.hashCode());
    }

    @ExportMessage
    public TriState isIdenticalOrUndefined(Object otherInterop,
                    @Cached PForeignToPTypeNode convert,
                    @Exclusive @CachedLibrary(limit = "3") InteropLibrary otherLib,
                    @Cached IsNode isNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object other = convert.executeConvert(otherInterop);
            if (this == other) {
                return TriState.TRUE;
            } else if (otherLib.hasIdentity(other)) {
                return isNode.execute(this, other) ? TriState.TRUE : TriState.FALSE;
            } else {
                return TriState.UNDEFINED;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean hasIterator(
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter) {
        return !(lookupIter.execute(getClassNode.executeCached(this)) instanceof PNone);
    }

    @ExportMessage
    public Object getIterator(
                    @CachedLibrary("this") InteropLibrary lib,
                    @Bind("$node") Node inliningTarget,
                    @Cached PyObjectGetIter getIter,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        if (lib.hasIterator(this)) {
            boolean mustRelease = gil.acquire();
            try {
                return getIter.execute(null, inliningTarget, this);
            } finally {
                gil.release(mustRelease);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public boolean isIterator(
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Cached(parameters = "Next") LookupCallableSlotInMRONode lookupNext) {
        return lookupNext.execute(getClassNode.executeCached(this)) != PNone.NO_VALUE;
    }

    private static final HiddenKey NEXT_ELEMENT = new HiddenKey("next_element");

    @ExportMessage
    public boolean hasIteratorNextElement(
                    @Bind("$node") Node inliningTarget,
                    @CachedLibrary("this") InteropLibrary ilib,
                    @Shared("dylib") @CachedLibrary(limit = "2") DynamicObjectLibrary dylib,
                    @Cached GetNextNode getNextNode,
                    @Exclusive @Cached IsBuiltinObjectProfile exceptionProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        if (ilib.isIterator(this)) {
            Object nextElement = dylib.getOrDefault(this, NEXT_ELEMENT, null);
            if (nextElement != null) {
                return true;
            }
            boolean mustRelease = gil.acquire();
            try {
                nextElement = getNextNode.execute(null, this);
                dylib.put(this, NEXT_ELEMENT, nextElement);
                return true;
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.StopIteration, exceptionProfile);
                return false;
            } finally {
                gil.release(mustRelease);
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
