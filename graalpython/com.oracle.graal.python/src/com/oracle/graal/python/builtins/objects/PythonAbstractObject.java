/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_TYPE_A_NOT_TYPE_B;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_A_S_TUPLE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
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
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.lib.PySequenceDelItemNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode;
import com.oracle.graal.python.lib.PySequenceSetItemNode;
import com.oracle.graal.python.lib.PySequenceSizeNode;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.keywords.MappingToKeywordsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListInteropNode;
import com.oracle.graal.python.nodes.interop.GetInteropBehaviorNode;
import com.oracle.graal.python.nodes.interop.GetInteropBehaviorValueNode;
import com.oracle.graal.python.nodes.interop.InteropBehavior;
import com.oracle.graal.python.nodes.interop.InteropBehaviorMethod;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaShortNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
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
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.RegionEqualNode;
import com.oracle.truffle.api.utilities.TriState;

@ImportStatic(SpecialMethodNames.class)
@ExportLibrary(InteropLibrary.class)
public abstract class PythonAbstractObject extends DynamicObject implements TruffleObject, Comparable<Object> {

    private static final TruffleString T_PRIVATE_PREFIX = tsLiteral("__");
    private static final int PRIVATE_PREFIX_LENGTH = T_PRIVATE_PREFIX.codePointLengthUncached(TS_ENCODING);
    private PythonAbstractObjectNativeWrapper nativeWrapper;

    protected static final Shape ABSTRACT_SHAPE = Shape.newBuilder().build();

    private Object[] indexedSlots;

    protected PythonAbstractObject(Shape shape) {
        super(shape);
    }

    protected PythonAbstractObject() {
        super(ABSTRACT_SHAPE);
    }

    public final PythonAbstractObjectNativeWrapper getNativeWrapper() {
        return nativeWrapper;
    }

    public final void setNativeWrapper(PythonAbstractObjectNativeWrapper nativeWrapper) {
        assert this.nativeWrapper == null;

        // we must not set the native wrapper for one of the context-insensitive singletons
        assert !CApiGuards.isSpecialSingleton(this);

        this.nativeWrapper = nativeWrapper;
    }

    public final void clearNativeWrapper() {
        nativeWrapper = null;
    }

    public Object[] getIndexedSlots() {
        return indexedSlots;
    }

    public void setIndexedSlots(Object[] indexedSlots) {
        this.indexedSlots = indexedSlots;
    }

    @ExportMessage
    public void writeMember(String key, Object value,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Shared @Cached PForeignToPTypeNode convert,
                    @Exclusive @Cached PyObjectSetAttr setAttributeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached IsBuiltinObjectProfile attrErrorProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            setAttributeNode.execute(null, inliningTarget, this, fromJavaStringNode.execute(key, TS_ENCODING), convert.executeConvert(value));
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
                    @Bind Node inliningTarget,
                    @Exclusive @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
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
    @SuppressWarnings("truffle-inlining")
    public boolean hasArrayElements(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Exclusive @Cached PySequenceCheckNode sequenceCheck,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.has_array_elements;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return sequenceCheck.execute(inliningTarget, this) && getSlotsNode.execute(inliningTarget, this).combined_sq_mp_length() != null;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object readArrayElement(long key,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Cached PySequenceGetItemNode sequenceGetItem,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.read_array_element;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.execute(inliningTarget, behavior, method, this, key);
            } else {
                try {
                    return sequenceGetItem.execute(this, PInt.intValueExact(key));
                } catch (OverflowException cce) {
                    throw InvalidArrayIndexException.create(key);
                } catch (PException pe) {
                    throw UnsupportedMessageException.create();
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeArrayElement(long key, Object value,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Shared @Cached PForeignToPTypeNode convert,
                    @Cached PySequenceSetItemNode sequenceSetItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            value = convert.executeConvert(value);
            InteropBehaviorMethod method = InteropBehaviorMethod.write_array_element;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                getValue.execute(inliningTarget, behavior, method, this, key, value);
            } else {
                try {
                    sequenceSetItemNode.execute(null, inliningTarget, this, PInt.intValueExact(key), value);
                } catch (OverflowException cce) {
                    throw InvalidArrayIndexException.create(key);
                } catch (PException pe) {
                    throw UnsupportedMessageException.create();
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void removeArrayElement(long key,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Cached PySequenceDelItemNode sequenceDelItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.remove_array_element;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                getValue.execute(inliningTarget, behavior, method, this, key);
            } else {
                try {
                    sequenceDelItemNode.execute(null, inliningTarget, this, PInt.intValueExact(key));
                } catch (OverflowException cce) {
                    throw InvalidArrayIndexException.create(key);
                } catch (PException pe) {
                    throw UnsupportedMessageException.create();
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public long getArraySize(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Exclusive @Cached PySequenceSizeNode sequenceSizeNode,
                    @Exclusive @Cached PySequenceCheckNode sequenceCheck,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaLongExactNode toLongNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.get_array_size;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeLong(inliningTarget, behavior, method, toLongNode, raiseNode, this);
            } else {
                if (!sequenceCheck.execute(inliningTarget, this)) {
                    throw UnsupportedMessageException.create();
                }
                try {
                    return sequenceSizeNode.execute(null, inliningTarget, this);
                } catch (PException pe) {
                    throw UnsupportedMessageException.create();
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    private boolean isInBounds(Node inliningTarget, long idx, PySequenceSizeNode sequenceSizeNode) {
        long length = sequenceSizeNode.execute(null, inliningTarget, this);
        return 0 <= idx && idx < length;
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isArrayElementReadable(long idx,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached PySequenceSizeNode sequenceSizeNode,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_array_element_readable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, idx);
            } else {
                try {
                    return isInBounds(inliningTarget, idx, sequenceSizeNode);
                } catch (PException pe) {
                    return false;
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isArrayElementModifiable(long idx,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached PySequenceSizeNode sequenceSizeNode,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_array_element_modifiable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, idx);
            } else {
                try {
                    return isInBounds(inliningTarget, idx, sequenceSizeNode);
                } catch (PException pe) {
                    return false;
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isArrayElementInsertable(long idx,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached PySequenceSizeNode sequenceSizeNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_array_element_insertable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, idx);
            } else {
                try {
                    return !isInBounds(inliningTarget, idx, sequenceSizeNode);
                } catch (PException pe) {
                    return false;
                }
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isArrayElementRemovable(long idx,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached PySequenceSizeNode sequenceSizeNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_array_element_removable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, idx);
            } else {
                try {
                    return isInBounds(inliningTarget, idx, sequenceSizeNode);
                } catch (PException pe) {
                    return false;
                }
            }
        } finally {
            gil.release(mustRelease);
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
                    @Bind Node inliningTarget,
                    @Exclusive @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
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
    @SuppressWarnings("truffle-inlining")
    public boolean isExecutable(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Cached PyCallableCheckNode callableCheck,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_executable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return callableCheck.execute(inliningTarget, this);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object execute(Object[] arguments,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Exclusive @Cached PExecuteNode executeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.execute;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.execute(inliningTarget, behavior, method, this, arguments);
            } else {
                return executeNode.execute(this, arguments);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @TruffleBoundary
    public Object getMembers(boolean includeInternal,
                    @Bind Node inliningTarget,
                    @Cached CastToListInteropNode castToList,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClass,
                    @Cached PyMappingCheckNode checkMapping,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyObjectLookupAttr lookupKeys,
                    @Cached CallNode callKeys,
                    @Cached PyObjectGetItem getItemNode,
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
                        PList mapKeys = castToList.executeWithGlobalState(callKeys.executeWithoutFrame(keysMethod));
                        int len = lenNode.execute(inliningTarget, mapKeys);
                        for (int i = 0; i < len; i++) {
                            Object key = getItemNode.execute(null, inliningTarget, mapKeys, i);
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
                    @Bind Node inliningTarget,
                    @Exclusive @Cached PyObjectSetAttr deleteAttributeNode,
                    @Exclusive @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached IsBuiltinObjectProfile attrErrorProfile,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            deleteAttributeNode.delete(null, inliningTarget, this, fromJavaStringNode.execute(member, TS_ENCODING));
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
                    @Bind Node inliningTarget,
                    // GR-44020: use inlined:
                    @Cached(inline = false) TypeNodes.IsTypeNode isTypeNode,
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
        try {
            if (!interopLib.isInstantiable(this)) {
                throw UnsupportedMessageException.create();
            }
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

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isDate(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_date;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public LocalDate asDate(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyTupleSizeNode pyTupleSizeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_date;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                Object value = getValue.execute(inliningTarget, behavior, method, this);
                if (value instanceof PTuple tuple) {
                    if (pyTupleSizeNode.execute(inliningTarget, tuple) != 3) {
                        throw raiseNode.raise(inliningTarget, ValueError, S_MUST_BE_A_S_TUPLE, "return value", "3");
                    }
                    SequenceStorage storage = tuple.getSequenceStorage();
                    int year = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 0), raiseNode);
                    int month = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 1), raiseNode);
                    int day = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 2), raiseNode);
                    try {
                        return createLocalDate(year, month, day);
                    } catch (Exception e) {
                        throw raiseNode.raise(inliningTarget, SystemError, e);
                    }
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "return value", "tuple", value);
                }

            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isTime(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_time;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public LocalTime asTime(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyTupleSizeNode pyTupleSizeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_time;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                Object value = getValue.execute(inliningTarget, behavior, method, this);
                if (value instanceof PTuple tuple) {
                    if (pyTupleSizeNode.execute(inliningTarget, tuple) != 4) {
                        throw raiseNode.raise(inliningTarget, ValueError, S_MUST_BE_A_S_TUPLE, "return value", "4");
                    }
                    SequenceStorage storage = tuple.getSequenceStorage();
                    int hour = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 0), raiseNode);
                    int min = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 1), raiseNode);
                    int sec = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 2), raiseNode);
                    int micro = castToIntNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 3), raiseNode);
                    try {
                        return createLocalTime(hour, min, sec, micro);
                    } catch (Exception e) {
                        throw raiseNode.raise(inliningTarget, SystemError, e);
                    }
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "return value", "tuple", value);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }

    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isTimeZone(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_time_zone;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public ZoneId asTimeZone(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode castToIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_time_zone;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                Object value = getValue.execute(inliningTarget, behavior, method, this);
                try {
                    try {
                        if (value instanceof TruffleString tsValue) {
                            String tmZone = toJavaStringNode.execute(tsValue);
                            return createZoneId(tmZone);
                        } else {
                            int utcDeltaInSeconds = castToIntNode.execute(inliningTarget, value);
                            return createZoneId(utcDeltaInSeconds);
                        }
                    } catch (Exception e) {
                        throw raiseNode.raise(inliningTarget, SystemError, e);
                    }
                } catch (CannotCastException cce) {
                    throw raiseNode.raise(inliningTarget, TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "return value", "str or int", value);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
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

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isDuration(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_duration;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Duration asDuration(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaLongExactNode castToLongNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PyTupleSizeNode pyTupleSizeNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_duration;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                Object value = getValue.execute(inliningTarget, behavior, method, this);
                if (value instanceof PTuple tuple) {
                    if (pyTupleSizeNode.execute(inliningTarget, tuple) != 2) {
                        throw raiseNode.raise(inliningTarget, ValueError, S_MUST_BE_A_S_TUPLE, "return value", "2");
                    }
                    SequenceStorage storage = tuple.getSequenceStorage();
                    long sec = castToLongNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 0), raiseNode);
                    long nano = castToLongNode.executeWithThrowSystemError(inliningTarget, getItemNode.execute(inliningTarget, storage, 1), raiseNode);
                    try {
                        return createDuration(sec, nano);
                    } catch (Exception e) {
                        throw raiseNode.raise(inliningTarget, SystemError, e);
                    }
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, MUST_BE_TYPE_A_NOT_TYPE_B, "return value", "tuple", value);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @TruffleBoundary
    private static Duration createDuration(long seconds, long nanoAdjustment) {
        return Duration.ofSeconds(seconds, nanoAdjustment);
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
                        @Bind Node inliningTarget,
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
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached GetClassNode getClassNode) {
            // TODO(fa) The first condition is too general; we should check if the object's type is
            // 'type'
            if (object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject || PGuards.isNativeClass(object) || PGuards.isNativeObject(object)) {
                return true;
            } else if (object instanceof PythonClass || object instanceof PythonModule) {
                return false;
            } else if (getSlotsNode.execute(inliningTarget, object).combined_tp_setattro_setattr() != null) {
                return false;
            } else {
                Object klass = getClassNode.execute(inliningTarget, object);
                return klass instanceof PythonBuiltinClassType || klass instanceof PythonBuiltinClass || PGuards.isNativeClass(object);
            }
        }
    }

    @GenerateUncached
    @ReportPolymorphism
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
    public abstract static class PExecuteNode extends Node {

        public abstract Object execute(Object receiver, Object[] arguments) throws UnsupportedMessageException;

        private static String POSARGS_MEMBER = "org.graalvm.python.embedding.PositionalArguments.is_positional_arguments";
        private static String KWARGS_MEMBER = "org.graalvm.python.embedding.KeywordArguments.is_keyword_arguments";

        @Specialization
        static Object doExecute(Object receiver, Object[] arguments,
                        @Bind Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ArgumentsFromForeignNode convertArgsNode,
                        @Cached MappingToKeywordsNode toKeywordsNode,
                        @CachedLibrary(limit = "1") InteropLibrary iLibKwArgs,
                        @CachedLibrary(limit = "1") InteropLibrary iLibPosArgs,
                        @CachedLibrary(limit = "1") InteropLibrary iLibIterator,
                        @Cached InlinedConditionProfile argsLenProfile,
                        @Cached InlinedConditionProfile isKwargsProfile,
                        @Cached InlinedConditionProfile isIndexZeroProfile,
                        @Cached InlinedConditionProfile isStarargsProfile1,
                        @Cached InlinedConditionProfile isStarargsProfile2,
                        @Cached InlinedConditionProfile isStarargsDefinedProfile,
                        @Cached InlinedConditionProfile hasMembersProfile,
                        @Cached InlinedConditionProfile isIndexNotZeroProfile,
                        @Cached InlinedLoopConditionProfile loopProfile) throws UnsupportedMessageException {
            if (!callableCheck.execute(inliningTarget, receiver)) {
                throw UnsupportedMessageException.create();
            }

            PKeyword[] kwArgs = PKeyword.EMPTY_KEYWORDS;
            Object[] newArgs = arguments;
            int index = arguments.length - 1;
            if (argsLenProfile.profile(inliningTarget, index >= 0)) {
                Object last = arguments[index];
                Object posArgs = null;
                try {
                    boolean lastHasMembers = hasMembersProfile.profile(inliningTarget, iLibKwArgs.hasMembers(last));
                    if (lastHasMembers && isKwargsProfile.profile(inliningTarget, iLibKwArgs.isMemberReadable(last, KWARGS_MEMBER) && iLibKwArgs.readMember(last, KWARGS_MEMBER) == Boolean.TRUE)) {
                        kwArgs = toKeywordsNode.execute(null, inliningTarget, last);
                        --index;
                        if (isIndexZeroProfile.profile(inliningTarget, index >= 0)) {
                            last = arguments[index];
                            if (isStarargsProfile1.profile(inliningTarget,
                                            iLibPosArgs.hasMembers(last) && iLibPosArgs.isMemberReadable(last, POSARGS_MEMBER) && iLibPosArgs.readMember(last, POSARGS_MEMBER) == Boolean.TRUE)) {
                                posArgs = last;
                            } else {
                                // no starargs are in arguments
                                newArgs = PythonUtils.arrayCopyOf(arguments, arguments.length - 1);
                            }
                        } else {
                            // only kwargs are in arguments
                            newArgs = new Object[0];
                        }
                    } else if (lastHasMembers &&
                                    isStarargsProfile2.profile(inliningTarget, iLibPosArgs.isMemberReadable(last, POSARGS_MEMBER) && iLibPosArgs.readMember(last, POSARGS_MEMBER) == Boolean.TRUE)) {
                        posArgs = last;
                    }

                    if (isStarargsDefinedProfile.profile(inliningTarget, posArgs != null)) {
                        long length = iLibPosArgs.getArraySize(posArgs);
                        Object iterator = iLibPosArgs.getIterator(posArgs);
                        loopProfile.profileCounted(inliningTarget, length);
                        Object[] starArgs = new Object[(int) length];
                        for (int i = 0; loopProfile.inject(inliningTarget, i < length); i++) {
                            starArgs[i] = iLibIterator.getIteratorNextElement(iterator);
                        }
                        if (isIndexNotZeroProfile.profile(inliningTarget, index > 0)) {
                            newArgs = new Object[index + starArgs.length];
                            PythonUtils.arraycopy(arguments, 0, newArgs, 0, index);
                            PythonUtils.arraycopy(starArgs, 0, newArgs, index, starArgs.length);
                        } else {
                            // only starargs and kwargs are in arguments
                            newArgs = starArgs;
                        }
                    }
                } catch (UnknownIdentifierException | SameDictKeyException | NonMappingException | StopIterationException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            Object[] convertedArgs = convertArgsNode.execute(inliningTarget, newArgs);
            return callNode.execute(null, receiver, convertedArgs, kwArgs);
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
        Object readArrayElement(long index,
                        @Cached InlinedBranchProfile outOfBoundsProfile,
                        @Bind Node inliningTarget) throws InvalidArrayIndexException {
            if (Long.compareUnsigned(index, keys.length) < 0) {
                return keys[(int) index];
            } else {
                outOfBoundsProfile.enter(inliningTarget);
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
                    result = castStr.execute(inliningTarget, callNode.executeWithoutFrame(toStrAttr, receiver));
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
                        @Bind Node inliningTarget,
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
    public boolean hasMetaParents(
                    @Bind Node inliningTarget,
                    @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Exclusive @Cached TypeNodes.GetBaseClassesNode getBaseClassNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isTypeNode.execute(inliningTarget, this) && getBaseClassNode.execute(inliningTarget, this).length > 0;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object getMetaParents(
                    @Bind Node inliningTarget,
                    @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Exclusive @Cached TypeNodes.GetBaseClassesNode getBaseClassNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (isTypeNode.execute(inliningTarget, this)) {
                var bases = getBaseClassNode.execute(inliningTarget, this);
                if (bases.length > 0) {
                    return PFactory.createTuple(PythonLanguage.get(inliningTarget), bases);
                }
            }
            throw UnsupportedMessageException.create();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public int identityHashCode(
                    @Bind Node inliningTarget,
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
                    @Shared @Cached PForeignToPTypeNode convert,
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
    @SuppressWarnings("truffle-inlining")
    public boolean hasIterator(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("getClass") @Cached(inline = false) GetClassNode getClassNode,
                    @Cached GetCachedTpSlotsNode getSlots,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.has_iterator;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                TpSlots slots = getSlots.execute(inliningTarget, getClassNode.executeCached(this));
                return slots.tp_iter() != null;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object getIterator(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Cached PyObjectGetIter getIter,
                    @Exclusive @Cached GilNode gil,
                    @CachedLibrary("this") InteropLibrary lib) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (lib.hasIterator(this)) {
                InteropBehaviorMethod method = InteropBehaviorMethod.get_iterator;
                InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
                if (behavior != null) {
                    return getValue.execute(inliningTarget, behavior, method, this);
                } else {
                    return getIter.execute(null, inliningTarget, this);
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isIterator(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Cached PyIterCheckNode checkNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_iterator;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return checkNode.execute(inliningTarget, this);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean hasIteratorNextElement(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Cached PyIterNextNode nextNode,
                    @Exclusive @Cached GilNode gil,
                    @CachedLibrary("this") InteropLibrary ilib,
                    // GR-44020: make shared:
                    @Exclusive @Cached HiddenAttr.ReadNode readHiddenAttrNode,
                    @Exclusive @Cached HiddenAttr.WriteNode writeHiddenAttrNode) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (ilib.isIterator(this)) {
                InteropBehaviorMethod method = InteropBehaviorMethod.has_iterator_next_element;
                InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
                if (behavior != null) {
                    return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
                } else {
                    Object nextElement = readHiddenAttrNode.execute(inliningTarget, this, HiddenAttr.NEXT_ELEMENT, null);
                    if (nextElement != null) {
                        return true;
                    }
                    try {
                        nextElement = nextNode.execute(null, inliningTarget, this);
                    } catch (IteratorExhausted e) {
                        return false;
                    }
                    writeHiddenAttrNode.execute(inliningTarget, this, HiddenAttr.NEXT_ELEMENT, nextElement);
                    return true;
                }
            }
            throw UnsupportedMessageException.create();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object getIteratorNextElement(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @CachedLibrary("this") InteropLibrary ilib,
                    // GR-44020: make shared:
                    @Exclusive @Cached HiddenAttr.ReadNode readHiddenAttrNode,
                    @Exclusive @Cached HiddenAttr.WriteNode writeHiddenAttrNode,
                    @Exclusive @Cached GilNode gil) throws StopIterationException, UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (ilib.hasIteratorNextElement(this)) {
                InteropBehaviorMethod method = InteropBehaviorMethod.get_iterator_next_element;
                InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
                if (behavior != null) {
                    return getValue.execute(inliningTarget, behavior, method, this);
                } else {
                    Object nextElement = readHiddenAttrNode.execute(inliningTarget, this, HiddenAttr.NEXT_ELEMENT, null);
                    writeHiddenAttrNode.execute(inliningTarget, this, HiddenAttr.NEXT_ELEMENT, null);
                    return nextElement;
                }
            } else {
                throw StopIterationException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isBoolean(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_boolean;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isNumber(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_number;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isString(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_string;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInByte(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_byte;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInShort(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_short;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInInt(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_int;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInLong(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_long;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInFloat(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_float;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInDouble(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_double;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean fitsInBigInteger(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.fits_in_big_integer;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean asBoolean(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_boolean;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public byte asByte(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaByteNode toByteNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_byte;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeByte(inliningTarget, behavior, method, toByteNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public short asShort(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaShortNode toShortNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_short;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeShort(inliningTarget, behavior, method, toShortNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public int asInt(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_int;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeInt(inliningTarget, behavior, method, toIntNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public long asLong(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaLongExactNode toLongNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_long;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeLong(inliningTarget, behavior, method, toLongNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public float asFloat(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaDoubleNode toDoubleNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_float;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return (float) getValue.executeDouble(inliningTarget, behavior, method, toDoubleNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public double asDouble(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaDoubleNode toDoubleNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_double;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeDouble(inliningTarget, behavior, method, toDoubleNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public BigInteger asBigInteger(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Cached CastToJavaBigIntegerNode toBigIntegerNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_big_integer;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                Object value = getValue.execute(inliningTarget, behavior, method, this);
                return toBigIntegerNode.execute(inliningTarget, value);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public String asString(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    @Cached CastToJavaStringNode toStringNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.as_string;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeString(inliningTarget, behavior, method, toStringNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean hasHashEntries(@Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.has_hash_entries;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public long getHashSize(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaLongExactNode toLongNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.get_hash_size;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeLong(inliningTarget, behavior, method, toLongNode, raiseNode, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object getHashEntriesIterator(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.get_hash_entries_iterator;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.execute(inliningTarget, behavior, method, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object getHashKeysIterator(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.get_hash_entries_iterator;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.execute(inliningTarget, behavior, method, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object getHashValuesIterator(
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.get_hash_values_iterator;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.execute(inliningTarget, behavior, method, this);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public Object readHashValue(Object key,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.read_hash_value;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.execute(inliningTarget, behavior, method, this, key);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isHashEntryReadable(Object key,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_hash_entry_readable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, key);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isHashEntryRemovable(Object key,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_hash_entry_removable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, key);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void removeHashEntry(Object key,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.remove_hash_entry;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                getValue.execute(inliningTarget, behavior, method, this, key);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isHashEntryModifiable(Object key,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_hash_entry_modifiable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, key);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public boolean isHashEntryInsertable(Object key,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaBooleanNode toBooleanNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.is_hash_entry_insertable;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                return getValue.executeBoolean(inliningTarget, behavior, method, toBooleanNode, raiseNode, this, key);
            } else {
                return false;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeHashEntry(Object key, Object value,
                    @Bind Node inliningTarget,
                    @Shared("getBehavior") @Cached GetInteropBehaviorNode getBehavior,
                    @Shared("getValue") @Cached GetInteropBehaviorValueNode getValue,
                    // GR-44020: make shared:
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            InteropBehaviorMethod method = InteropBehaviorMethod.write_hash_entry;
            InteropBehavior behavior = getBehavior.execute(inliningTarget, this, method);
            if (behavior != null) {
                getValue.execute(inliningTarget, behavior, method, this, key, value);
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean hasBufferElements(@Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.isBuffer(this);
    }

    @ExportMessage
    public boolean isBufferWritable(@Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException {
        if (bufferLib.isBuffer(this)) {
            return !bufferLib.isReadonly(this);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public long getBufferSize(@Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException {
        if (bufferLib.isBuffer(this)) {
            return bufferLib.getBufferLength(this);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public byte readBufferByte(long byteOffset,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            return bufferLib.readByte(this, offset);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeBufferByte(long byteOffset, byte value, @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.writeByte(this, offset, value);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public short readBufferShort(ByteOrder order, long byteOffset,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            return bufferLib.readShortByteOrder(this, offset, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeBufferShort(ByteOrder order, long byteOffset, short value, @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.writeShortByteOrder(this, offset, value, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public int readBufferInt(ByteOrder order, long byteOffset,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            return bufferLib.readIntByteOrder(this, offset, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeBufferInt(ByteOrder order, long byteOffset, int value, @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.writeIntByteOrder(this, offset, value, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public long readBufferLong(ByteOrder order, long byteOffset,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            return bufferLib.readLongByteOrder(this, offset, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeBufferLong(ByteOrder order, long byteOffset, long value, @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.writeLongByteOrder(this, offset, value, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public float readBufferFloat(ByteOrder order, long byteOffset,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            return bufferLib.readFloatByteOrder(this, offset, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeBufferFloat(ByteOrder order, long byteOffset, float value,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.writeFloatByteOrder(this, offset, value, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public double readBufferDouble(ByteOrder order, long byteOffset,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            return bufferLib.readDoubleByteOrder(this, offset, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void writeBufferDouble(ByteOrder order, long byteOffset, double value,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.writeDoubleByteOrder(this, offset, value, order);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("truffle-inlining")
    public void readBuffer(long byteOffset, byte[] destination, int destinationOffset, int length,
                    @Bind Node inliningTarget,
                    // GR-44020: make shared:
                    @Exclusive @Cached CastToJavaIntExactNode toIntNode,
                    // GR-44020: make shared:
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Shared("bufferLib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
        if (bufferLib.isBuffer(this)) {
            if (length < 0 || (destination.length - destinationOffset > length)) {
                throw InvalidBufferOffsetException.create(byteOffset, length);
            }
            int offset = toIntNode.executeWithThrow(inliningTarget, byteOffset, raiseNode, PythonBuiltinClassType.OverflowError);
            bufferLib.readIntoByteArray(this, offset, destination, destinationOffset, length);
        } else {
            throw UnsupportedMessageException.create();
        }
    }
}
