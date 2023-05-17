/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.builtins;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyListObject__allocated;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyListObject__ob_item;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyVarObject__ob_size;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LIST;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.AppendNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.IndexNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class ListNodes {

    @GenerateUncached
    @ImportStatic({PGuards.class, PythonOptions.class})
    @GenerateInline(false) // footprint reduction 40 -> 21
    public abstract static class ConstructListNode extends PNodeWithContext {

        public final PList execute(Frame frame, Object value) {
            return execute(frame, PythonBuiltinClassType.PList, value);
        }

        protected abstract PList execute(Frame frame, Object cls, Object value);

        @Specialization
        static PList listString(Object cls, TruffleString arg,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            return factory.createList(cls, StringUtils.toCharacterArray(arg, codePointLengthNode, createCodePointIteratorNode, nextNode, fromCodePointNode));
        }

        @Specialization
        static PList listString(Object cls, PString arg,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            return listString(cls, castToStringNode.execute(inliningTarget, arg), factory, codePointLengthNode, createCodePointIteratorNode, nextNode, fromCodePointNode);
        }

        @Specialization(guards = "isNoValue(none)")
        static PList none(Object cls, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createList(cls);
        }

        @Specialization(guards = "cannotBeOverriddenForImmutableType(list)")
        // Don't use PSequence, that might copy storages that we don't allow for lists
        static PList fromList(Object cls, PList list,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.CopyNode copyNode) {
            return factory.createList(cls, copyNode.execute(inliningTarget, getSequenceStorageNode.execute(inliningTarget, list)));
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"})
        static PList listIterable(VirtualFrame frame, Object cls, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode createStorageFromIteratorNode,
                        @Shared @Cached PythonObjectFactory factory) {
            Object iterObj = getIter.execute(frame, inliningTarget, iterable);
            SequenceStorage storage = createStorageFromIteratorNode.execute(frame, iterObj);
            return factory.createList(cls, storage);
        }

        @Fallback
        static PList listObject(@SuppressWarnings("unused") Object cls, Object value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere("list does not support iterable object " + value);
        }

        @NeverDefault
        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }

        public static ConstructListNode getUncached() {
            return ConstructListNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FastConstructListNode extends PNodeWithContext {

        public abstract PSequence execute(Frame frame, Node inliningTarget, Object value);

        @Specialization(guards = "cannotBeOverridden(value, inliningTarget, getClassNode)", limit = "1")
        protected static PSequence doPList(@SuppressWarnings("unused") Node inliningTarget, PSequence value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return value;
        }

        @Fallback
        protected PSequence doGeneric(VirtualFrame frame, Object value,
                        @Cached(inline = false) ConstructListNode constructListNode) {
            return constructListNode.execute(frame, PythonBuiltinClassType.PList, value);
        }

    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IndexNode extends PNodeWithContext {
        @Child private PRaiseNode raise;
        private static final TruffleString DEFAULT_ERROR_MSG = cat(T_LIST, T_SPACE, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES);
        @Child LookupAndCallUnaryNode getIndexNode;
        private final CheckType checkType;
        private final TruffleString errorMessage;

        protected static enum CheckType {
            SUBSCRIPT,
            INTEGER,
            NUMBER;
        }

        protected IndexNode(TruffleString message, CheckType type) {
            checkType = type;
            getIndexNode = LookupAndCallUnaryNode.create(SpecialMethodSlot.Index);
            errorMessage = message;
        }

        @NeverDefault
        public static IndexNode create(TruffleString message) {
            return IndexNodeGen.create(message, CheckType.SUBSCRIPT);
        }

        @NeverDefault
        public static IndexNode create() {
            return IndexNodeGen.create(DEFAULT_ERROR_MSG, CheckType.SUBSCRIPT);
        }

        @NeverDefault
        public static IndexNode createInteger(TruffleString msg) {
            return IndexNodeGen.create(msg, CheckType.INTEGER);
        }

        @NeverDefault
        public static IndexNode createNumber(TruffleString msg) {
            return IndexNodeGen.create(msg, CheckType.NUMBER);
        }

        public abstract Object execute(VirtualFrame frame, Object object);

        @Idempotent
        protected boolean isSubscript() {
            return checkType == CheckType.SUBSCRIPT;
        }

        @Idempotent
        protected boolean isNumber() {
            return checkType == CheckType.NUMBER;
        }

        @Specialization
        long doLong(long slice) {
            return slice;
        }

        @Specialization
        PInt doPInt(PInt slice) {
            return slice;
        }

        @Specialization(guards = "isSubscript()")
        PSlice doSlice(PSlice slice) {
            return slice;
        }

        @Specialization(guards = "isNumber()")
        float doFloat(float slice) {
            return slice;
        }

        @Specialization(guards = "isNumber()")
        double doDouble(double slice) {
            return slice;
        }

        @Fallback
        @InliningCutoff
        Object doGeneric(VirtualFrame frame, Object object) {
            Object idx = getIndexNode.executeObject(frame, object);
            boolean valid = false;
            switch (checkType) {
                case SUBSCRIPT:
                    valid = MathGuards.isInteger(idx) || idx instanceof PSlice;
                    break;
                case NUMBER:
                    valid = MathGuards.isNumber(idx);
                    break;
                case INTEGER:
                    valid = MathGuards.isInteger(idx);
                    break;
            }
            if (valid) {
                return idx;
            } else {
                if (raise == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raise = insert(PRaiseNode.create());
                }
                throw raise.raise(TypeError, errorMessage, idx);
            }
        }
    }

    /**
     * This node takes a bit of care to avoid compiling code for switching storages. In the
     * interpreter, it will use a different {@link AppendNode} than in the compiled code, so the
     * specializations for the compiler are different. This is useful, because in the interpreter we
     * will report back type and size changes to the origin of the list via {@link PList#getOrigin}
     * and {@link PList.ListOrigin#reportUpdatedCapacity}. Thus, there's a chance that the compiled
     * code will only see lists of the correct size and storage type.
     */
    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 17
    @OperationProxy.Proxyable
    public abstract static class AppendNode extends PNodeWithContext {
        private static final BranchProfile[] DISABLED = new BranchProfile[]{BranchProfile.getUncached()};

        public abstract void execute(PList list, Object value);

        @NeverDefault
        public static BranchProfile[] getUpdateStoreProfile() {
            return new BranchProfile[1];
        }

        public static BranchProfile[] getUpdateStoreProfileUncached() {
            return DISABLED;
        }

        @Specialization
        public static void appendObjectGeneric(PList list, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached(value = "getUpdateStoreProfile()", uncached = "getUpdateStoreProfileUncached()", dimensions = 1) BranchProfile[] updateStoreProfile) {
            if (updateStoreProfile[0] == null) {
                // Executed for the first time. We don't pollute the AppendNode specializations,
                // yet, in case we're transitioning exactly once, because we'll pontentially pass
                // that information on to the list origin and it'll never happen again.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SequenceStorage newStore = SequenceStorageNodes.AppendNode.executeUncached(list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                updateStoreProfile[0] = BranchProfile.create();
                list.setSequenceStorage(newStore);
                if (list.getOrigin() != null && newStore instanceof BasicSequenceStorage) {
                    list.getOrigin().reportUpdatedCapacity((BasicSequenceStorage) newStore);
                }
            } else {
                SequenceStorage newStore = appendNode.execute(inliningTarget, list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                if (list.getSequenceStorage() != newStore) {
                    updateStoreProfile[0].enter();
                    list.setSequenceStorage(newStore);
                }
                if (CompilerDirectives.inInterpreter() && list.getOrigin() != null && newStore instanceof BasicSequenceStorage) {
                    list.getOrigin().reportUpdatedCapacity((BasicSequenceStorage) newStore);
                }
            }
        }

        @NeverDefault
        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }

    @GenerateInline(false)
    public abstract static class GetNativeListStorage extends Node {
        public abstract NativeSequenceStorage execute(PythonAbstractNativeObject list);

        @Specialization
        NativeSequenceStorage getNative(PythonAbstractNativeObject list,
                        @Cached CStructAccess.ReadPointerNode getContents,
                        @Cached CStructAccess.ReadI64Node readI64Node) {
            assert IsSubtypeNode.getUncached().execute(GetClassNode.executeUncached(list), PythonBuiltinClassType.PList);
            Object array = getContents.readFromObj(list, PyListObject__ob_item);
            int size = (int) readI64Node.readFromObj(list, PyVarObject__ob_size);
            int allocated = (int) readI64Node.readFromObj(list, PyListObject__allocated);
            return NativeObjectSequenceStorage.create(array, size, allocated, false);
        }
    }
}
