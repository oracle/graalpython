/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationPythonTypes.isJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Iterator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageFactory.InitNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.InjectIntoNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(HashingStorageLibrary.class)
public abstract class HashingStorage {
    @ValueType
    public static final class DictEntry {
        public final Object key;
        public final Object value;

        protected DictEntry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }
    }

    public abstract static class InitNode extends PNodeWithContext implements IndirectCallNode {

        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        public abstract HashingStorage execute(VirtualFrame frame, Object mapping, PKeyword[] kwargs);

        @Child private LookupInheritedAttributeNode lookupKeysAttributeNode;

        protected boolean isEmpty(PKeyword[] kwargs) {
            return kwargs.length == 0;
        }

        @Specialization(guards = {"isNoValue(iterable)", "isEmpty(kwargs)"})
        static HashingStorage doEmpty(@SuppressWarnings("unused") PNone iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return EmptyStorage.INSTANCE;
        }

        @Specialization(guards = {"isNoValue(iterable)", "!isEmpty(kwargs)"})
        static HashingStorage doKeywords(@SuppressWarnings("unused") PNone iterable, PKeyword[] kwargs) {
            return new KeywordsStorage(kwargs);
        }

        protected static boolean isPDict(Object o) {
            return o instanceof PHashingCollection;
        }

        protected boolean hasKeysAttribute(Object o) {
            if (lookupKeysAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupKeysAttributeNode = insert(LookupInheritedAttributeNode.create(T_KEYS));
            }
            return lookupKeysAttributeNode.execute(o) != PNone.NO_VALUE;
        }

        @Specialization(guards = {"isEmpty(kwargs)", "!hasIterAttrButNotBuiltin(dictLike, getClassNode, lookupIter)"}, limit = "1")
        static HashingStorage doPDict(PHashingCollection dictLike, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            return lib.copy(dictLike.getDictStorage());
        }

        @Specialization(guards = {"!isEmpty(kwargs)", "!hasIterAttrButNotBuiltin(iterable, getClassNode, lookupIter)"}, limit = "1")
        HashingStorage doPDictKwargs(VirtualFrame frame, PHashingCollection iterable, PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            PythonContext contextRef = PythonContext.get(this);
            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, contextRef, this);
            try {
                HashingStorage iterableDictStorage = iterable.getDictStorage();
                HashingStorage dictStorage = lib.copy(iterableDictStorage);
                return lib.addAllToOther(new KeywordsStorage(kwargs), dictStorage);
            } finally {
                IndirectCallContext.exit(frame, language, contextRef, state);
            }
        }

        @Specialization(guards = "hasIterAttrButNotBuiltin(col, getClassNode, lookupIter)", limit = "1")
        HashingStorage doNoBuiltinKeysAttr(VirtualFrame frame, PHashingCollection col, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared("lookupIter") @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Shared("callKeys") @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Shared("getItem") @Cached PyObjectGetItem getItemNode,
                        @Shared("getNext") @Cached GetNextNode nextNode,
                        @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile) {
            HashingStorage curStorage = PDict.createNewStorage(false, 0);
            return copyToStorage(frame, col, kwargs, curStorage, callKeysNode, getItemNode, getIter, nextNode, errorProfile, lib);
        }

        protected static boolean hasIterAttrButNotBuiltin(PHashingCollection col, GetClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
            Object attr = lookupIter.execute(getClassNode.execute(col));
            return attr != PNone.NO_VALUE && !(attr instanceof PBuiltinMethod || attr instanceof PBuiltinFunction);
        }

        @Specialization(guards = {"!isPDict(mapping)", "hasKeysAttribute(mapping)"})
        HashingStorage doMapping(VirtualFrame frame, Object mapping, PKeyword[] kwargs,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared("callKeys") @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Shared("getItem") @Cached PyObjectGetItem getItemNode,
                        @Shared("getNext") @Cached GetNextNode nextNode,
                        @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile) {
            HashingStorage curStorage = PDict.createNewStorage(false, 0);
            return copyToStorage(frame, mapping, kwargs, curStorage, callKeysNode, getItemNode, getIter, nextNode, errorProfile, lib);
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPDict(iterable)", "!hasKeysAttribute(iterable)"})
        HashingStorage doSequence(VirtualFrame frame, Object iterable, PKeyword[] kwargs,
                        @Shared("hlib") @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PRaiseNode raise,
                        @Shared("getNext") @Cached GetNextNode nextNode,
                        @Cached FastConstructListNode createListNode,
                        @Shared("getItem") @Cached PyObjectGetItem getItemNode,
                        @Cached SequenceNodes.LenNode seqLenNode,
                        @Cached ConditionProfile lengthTwoProfile,
                        @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile,
                        @Cached IsBuiltinClassProfile isTypeErrorProfile) {

            return addSequenceToStorage(frame, iterable, kwargs, (isStringKey, expectedSize) -> PDict.createNewStorage(isStringKey, expectedSize), getIter, nextNode, createListNode,
                            seqLenNode, lengthTwoProfile, raise, getItemNode, isTypeErrorProfile,
                            errorProfile, lib);
        }

        public static InitNode create() {
            return InitNodeGen.create();
        }
    }

    @ExportMessage
    int length() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.length");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    Object getItemWithState(Object key, ThreadState state) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.getItem");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage setItemWithState(Object key, Object value, ThreadState state) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.setItemWithState");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.delItemWithState");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    Object forEachUntyped(ForEachNode<Object> node, Object arg) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.injectInto");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage clear() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.clear");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorage copy() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.copy");
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    HashingStorageIterable<Object> keys() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.keys");
    }

    @ExportMessage
    public HashingStorageIterable<Object> reverseKeys() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new AbstractMethodError("HashingStorage.reverseKeys");
    }

    @GenerateUncached
    protected abstract static class AddToOtherInjectNode extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage self = accumulator[0];
            HashingStorage other = accumulator[1];
            HashingStorage newOther = lib.setItem(other, key, lib.getItem(self, key));
            if (CompilerDirectives.inInterpreter() && other == newOther) {
                // Avoid the allocation in interpreter if possible
                return accumulator;
            } else {
                return new HashingStorage[]{self, newOther};
            }
        }
    }

    @ExportMessage
    public HashingStorage addAllToOther(HashingStorage other,
                    @CachedLibrary("this") HashingStorageLibrary libSelf,
                    @Cached AddToOtherInjectNode injectNode) {
        return libSelf.injectInto(this, new HashingStorage[]{this, other}, injectNode)[1];
    }

    @ExportMessage
    public boolean equalsWithState(HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
        if (this == other) {
            return true;
        }
        if (lib.length(this) == lib.length(other)) {
            return lib.compareEntries(this, other) == 0;
        }
        return false;
    }

    @GenerateUncached
    protected abstract static class HasKeyNodeForSubsetKeys extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] other, Object key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            if (!lib.hasKey(other[0], key)) {
                throw AbortIteration.INSTANCE;
            }
            return other;
        }
    }

    private static final class AbortIteration extends ControlFlowException {
        private static final long serialVersionUID = 1L;
        private static final AbortIteration INSTANCE = new AbortIteration();
    }

    @ExportMessage
    public int compareKeys(HashingStorage other,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Cached HasKeyNodeForSubsetKeys hasKeyNode) {
        if (this == other) {
            return 0;
        }
        int otherLen = lib.length(other);
        int selfLen = lib.length(this);
        if (selfLen > otherLen) {
            return 1;
        }
        try {
            lib.injectInto(this, new HashingStorage[]{other}, hasKeyNode);
        } catch (AbortIteration e) {
            return 1;
        }
        if (selfLen == otherLen) {
            return 0;
        } else {
            return -1;
        }
    }

    @GenerateUncached
    protected abstract static class TestKeyValueEqual extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @Cached PRaiseNode raise,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage self = accumulator[0];
            HashingStorage other = accumulator[1];
            Object otherValue = lib.getItem(other, key);
            if (otherValue == null) {
                throw AbortIteration.INSTANCE;
            }
            Object selfValue = lib.getItem(self, key);
            if (selfValue == null) {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.DICT_CHANGED_DURING_COMPARISON);
            }
            if (eqNode.execute(null, selfValue, otherValue)) {
                return accumulator;
            } else {
                throw AbortIteration.INSTANCE;
            }
        }
    }

    @ExportMessage
    public int compareEntriesWithState(HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Cached TestKeyValueEqual testNode) {
        if (this == other) {
            return 0;
        }
        int otherLen = lib.length(other);
        int selfLen = lib.length(this);
        if (selfLen > otherLen) {
            return 1;
        }
        try {
            lib.injectInto(this, new HashingStorage[]{this, other}, testNode);
        } catch (AbortIteration e) {
            return 1;
        }
        if (selfLen == otherLen) {
            return 0;
        } else {
            return -1;
        }
    }

    @GenerateUncached
    protected abstract static class IntersectInjectionNode extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage other = accumulator[0];
            HashingStorage output = accumulator[1];
            Object value = lib.getItem(other, key);
            if (value != null) {
                output = lib.setItem(output, key, value);
            }
            if (CompilerDirectives.inInterpreter() && output == accumulator[1]) {
                // Avoid the allocation in interpreter if possible
                return accumulator;
            } else {
                return new HashingStorage[]{other, output};
            }
        }
    }

    @ExportMessage
    public HashingStorage intersect(HashingStorage other,
                    @CachedLibrary("this") HashingStorageLibrary libSelf,
                    @Cached IntersectInjectionNode injectNode) {
        HashingStorage newStore = EmptyStorage.INSTANCE;
        return libSelf.injectInto(this, new HashingStorage[]{other, newStore}, injectNode)[1];
    }

    protected static final class IsDisjoinForEachAcc {
        private final HashingStorage other;
        private final HashingStorageLibrary libOther;
        private final ThreadState state;

        public IsDisjoinForEachAcc(HashingStorage other, HashingStorageLibrary libOther, ThreadState state) {
            this.other = other;
            this.libOther = libOther;
            this.state = state;
        }
    }

    @GenerateUncached
    protected abstract static class IsDisjointForEachNode extends ForEachNode<IsDisjoinForEachAcc> {
        @Override
        public abstract IsDisjoinForEachAcc execute(Object key, IsDisjoinForEachAcc arg);

        @Specialization
        IsDisjoinForEachAcc doit(Object key, IsDisjoinForEachAcc acc) {
            if (acc.libOther.hasKeyWithState(acc.other, key, acc.state)) {
                throw AbortIteration.INSTANCE;
            }
            return acc;
        }
    }

    @ExportMessage
    public boolean isDisjointWithState(HashingStorage other, ThreadState state,
                    @CachedLibrary("this") HashingStorageLibrary libSelf,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary libOther,
                    @Cached IsDisjointForEachNode isDisjointForEachNode) {
        try {
            int selfLen = libSelf.length(this);
            int otherLen = libOther.length(other);
            if (selfLen < otherLen) {
                libSelf.forEach(this, isDisjointForEachNode, new IsDisjoinForEachAcc(other, libOther, state));
            } else {
                libOther.forEach(other, isDisjointForEachNode, new IsDisjoinForEachAcc(this, libSelf, state));
            }
            return true;
        } catch (AbortIteration e) {
            // iteration is aborted iff we found a key that is in both sets
            return false;
        }
    }

    @GenerateUncached
    protected abstract static class DiffInjectNode extends InjectIntoNode {
        @Specialization
        HashingStorage[] doit(HashingStorage[] accumulator, Object key,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage self = accumulator[0];
            HashingStorage other = accumulator[1];
            HashingStorage output = accumulator[2];
            if (!lib.hasKey(other, key)) {
                output = lib.setItem(output, key, lib.getItem(self, key));
            }
            if (CompilerDirectives.inInterpreter() && output == accumulator[2]) {
                // Avoid the allocation in interpreter if possible
                return accumulator;
            } else {
                return new HashingStorage[]{self, other, output};
            }
        }
    }

    @ExportMessage
    public HashingStorage xor(HashingStorage other,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Shared("diffInjectNode") @Cached DiffInjectNode injectNode) {
        // could also be done with lib.union(lib.diff(self, other),
        // lib.diff(other, self)), but that uses one more iteration.
        HashingStorage newStore = EmptyStorage.INSTANCE;
        // add all keys in self that are not in other
        newStore = lib.injectInto(this, new HashingStorage[]{this, other, newStore}, injectNode)[2];
        // add all keys in other that are not in self
        return lib.injectInto(other, new HashingStorage[]{other, this, newStore}, injectNode)[2];
    }

    @ExportMessage
    public HashingStorage union(HashingStorage other,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
        HashingStorage newStore = lib.copy(this);
        return lib.addAllToOther(other, newStore);
    }

    @ExportMessage
    public HashingStorage diffWithState(HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                    @CachedLibrary("this") HashingStorageLibrary libSelf,
                    @Shared("diffInjectNode") @Cached DiffInjectNode diffNode) {
        HashingStorage newStore = EmptyStorage.INSTANCE;
        return libSelf.injectInto(this, new HashingStorage[]{this, other, newStore}, diffNode)[2];
    }

    @ExportMessage
    public HashingStorageIterable<Object> values(@CachedLibrary("this") HashingStorageLibrary lib) {
        return new HashingStorageIterable<>(new ValuesIterator(this, lib.keys(this).iterator(), lib));
    }

    @ExportMessage
    public HashingStorageIterable<Object> reverseValues(@CachedLibrary("this") HashingStorageLibrary lib) {
        return new HashingStorageIterable<>(new ValuesIterator(this, lib.reverseKeys(this).iterator(), lib));
    }

    protected static final class ValuesIterator implements Iterator<Object> {
        private final Iterator<DictEntry> entriesIterator;

        ValuesIterator(HashingStorage self, HashingStorageIterator<Object> keysIterator, HashingStorageLibrary lib) {
            this.entriesIterator = new EntriesIterator(self, keysIterator, lib);
        }

        protected Iterator<DictEntry> getIterator() {
            return entriesIterator;
        }

        @Override
        public boolean hasNext() {
            return entriesIterator.hasNext();
        }

        @Override
        public Object next() {
            return entriesIterator.next().getValue();
        }
    }

    @ExportMessage
    public final HashingStorageIterable<DictEntry> entries(@CachedLibrary("this") HashingStorageLibrary lib) {
        return new HashingStorageIterable<>(new EntriesIterator(this, lib.keys(this).iterator(), lib));
    }

    @ExportMessage
    public final HashingStorageIterable<DictEntry> reverseEntries(@CachedLibrary("this") HashingStorageLibrary lib) {
        return new HashingStorageIterable<>(new EntriesIterator(this, lib.reverseKeys(this).iterator(), lib));
    }

    protected static final class EntriesIterator implements Iterator<DictEntry> {
        private final HashingStorageIterator<Object> keysIterator;
        private final HashingStorage self;
        private final HashingStorageLibrary lib;

        EntriesIterator(HashingStorage self, HashingStorageIterator<Object> keysIterator, HashingStorageLibrary lib) {
            this.self = self;
            this.lib = lib;
            this.keysIterator = keysIterator;
        }

        protected HashingStorageIterator<Object> getKeysIterator() {
            return keysIterator;
        }

        @Override
        public boolean hasNext() {
            return keysIterator.hasNext();
        }

        @Override
        public DictEntry next() {
            Object key = keysIterator.next();
            Object value = lib.getItem(self, key);
            return new DictEntry(key, value);
        }
    }

    /**
     * Adds all items from the given mapping object to storage. It is the caller responsibility to
     * ensure, that mapping has the 'keys' attribute.
     */
    public static HashingStorage copyToStorage(VirtualFrame frame, Object mapping, PKeyword[] kwargs, HashingStorage storage,
                    LookupAndCallUnaryNode callKeysNode, PyObjectGetItem callGetItemNode,
                    PyObjectGetIter getIter, GetNextNode nextNode,
                    IsBuiltinClassProfile errorProfile, HashingStorageLibrary lib) {
        Object keysIterable = callKeysNode.executeObject(frame, mapping);
        Object keysIt = getIter.execute(frame, keysIterable);
        HashingStorage curStorage = storage;
        while (true) {
            try {
                Object keyObj = nextNode.execute(frame, keysIt);
                Object valueObj = callGetItemNode.execute(frame, mapping, keyObj);

                curStorage = lib.setItem(curStorage, keyObj, valueObj);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                break;
            }
        }
        if (kwargs.length > 0) {
            curStorage = lib.addAllToOther(new KeywordsStorage(kwargs), curStorage);
        }
        return curStorage;
    }

    @FunctionalInterface
    public interface StorageSupplier {
        HashingStorage get(boolean isStringKey, int length);
    }

    public static HashingStorage addSequenceToStorage(VirtualFrame frame, Object iterable, PKeyword[] kwargs, StorageSupplier storageSupplier,
                    PyObjectGetIter getIter, GetNextNode nextNode, FastConstructListNode createListNode, LenNode seqLenNode,
                    ConditionProfile lengthTwoProfile, PRaiseNode raise, PyObjectGetItem getItemNode, IsBuiltinClassProfile isTypeErrorProfile,
                    IsBuiltinClassProfile errorProfile, HashingStorageLibrary lib) throws PException {
        Object it = getIter.execute(frame, iterable);
        ArrayBuilder<PSequence> elements = new ArrayBuilder<>();
        boolean isStringKey = false;
        try {
            while (true) {
                Object next = nextNode.execute(frame, it);
                PSequence element = createListNode.execute(frame, next);
                assert element != null;
                // This constructs a new list using the builtin type. So, the object cannot
                // be subclassed and we can directly call 'len()'.
                int len = seqLenNode.execute(element);

                if (lengthTwoProfile.profile(len != 2)) {
                    throw raise.raise(ValueError, ErrorMessages.DICT_UPDATE_SEQ_ELEM_HAS_LENGTH_2_REQUIRED, elements.size(), len);
                }

                // really check for Java String since PString can be subclassed
                isStringKey = isStringKey || isJavaString(getItemNode.execute(frame, element, 0));

                elements.add(element);
            }
        } catch (PException e) {
            if (isTypeErrorProfile.profileException(e, TypeError)) {
                throw raise.raise(TypeError, ErrorMessages.CANNOT_CONVERT_DICT_UPDATE_SEQ, elements.size());
            } else {
                e.expectStopIteration(errorProfile);
            }
        }
        HashingStorage storage = storageSupplier.get(isStringKey, elements.size() + kwargs.length);
        for (int j = 0; j < elements.size(); j++) {
            PSequence element = elements.get(j);
            Object key = getItemNode.execute(frame, element, 0);
            Object value = getItemNode.execute(frame, element, 1);
            storage = lib.setItem(storage, key, value);
        }
        if (kwargs.length > 0) {
            storage = lib.addAllToOther(new KeywordsStorage(kwargs), storage);
        }
        return storage;
    }

}
