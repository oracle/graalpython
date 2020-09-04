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
package com.oracle.graal.python.nodes.builtins;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CreateStorageFromIteratorInteropNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.AppendNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.FastConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.IndexNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorWithoutFrameNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class ListNodes {

    @GenerateUncached
    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructListNode extends PNodeWithContext {

        public final PList execute(Object value) {
            return execute(PythonBuiltinClassType.PList, value);
        }

        protected abstract PList execute(Object cls, Object value);

        @Specialization
        PList listString(Object cls, String arg,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createList(cls, StringUtils.toCharacterArray(arg));
        }

        @Specialization(guards = "isNoValue(none)")
        PList listIterable(Object cls, @SuppressWarnings("unused") PNone none,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createList(cls);
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"})
        PList listIterable(Object cls, Object iterable,
                        @Cached GetIteratorWithoutFrameNode getIteratorNode,
                        @Cached CreateStorageFromIteratorInteropNode createStorageFromIteratorNode,
                        @Cached PythonObjectFactory factory) {

            Object iterObj = getIteratorNode.executeWithGlobalState(iterable);
            SequenceStorage storage = createStorageFromIteratorNode.execute(iterObj);
            return factory.createList(cls, storage);
        }

        @Fallback
        PList listObject(@SuppressWarnings("unused") Object cls, Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("list does not support iterable object " + value);
        }

        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }

        public static ConstructListNode getUncached() {
            return ConstructListNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class FastConstructListNode extends PNodeWithContext {

        @Child private ConstructListNode constructListNode;

        public abstract PSequence execute(Object value);

        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(value))", limit = "2")
        protected PSequence doPList(PSequence value,
                        @SuppressWarnings("unused") @CachedLibrary("value") PythonObjectLibrary lib) {
            return value;
        }

        @Fallback
        protected PSequence doGeneric(Object value) {
            if (constructListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructListNode = insert(ConstructListNode.create());
            }
            return constructListNode.execute(PythonBuiltinClassType.PList, value);
        }

        public static FastConstructListNode create() {
            return FastConstructListNodeGen.create();
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IndexNode extends PNodeWithContext {
        @Child private PRaiseNode raise;
        private static final String DEFAULT_ERROR_MSG = "list " + ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES;
        @Child LookupAndCallUnaryNode getIndexNode;
        private final CheckType checkType;
        private final String errorMessage;

        protected static enum CheckType {
            SUBSCRIPT,
            INTEGER,
            NUMBER;
        }

        protected IndexNode(String message, CheckType type) {
            checkType = type;
            getIndexNode = LookupAndCallUnaryNode.create(__INDEX__);
            errorMessage = message;
        }

        public static IndexNode create(String message) {
            return IndexNodeGen.create(message, CheckType.SUBSCRIPT);
        }

        public static IndexNode create() {
            return IndexNodeGen.create(DEFAULT_ERROR_MSG, CheckType.SUBSCRIPT);
        }

        public static IndexNode createInteger(String msg) {
            return IndexNodeGen.create(msg, CheckType.INTEGER);
        }

        public static IndexNode createNumber(String msg) {
            return IndexNodeGen.create(msg, CheckType.NUMBER);
        }

        public abstract Object execute(VirtualFrame frame, Object object);

        protected boolean isSubscript() {
            return checkType == CheckType.SUBSCRIPT;
        }

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
     * and {@link ListLiteralNode#reportUpdatedCapacity}. Thus, there's a chance that the compiled
     * code will only see lists of the correct size and storage type.
     */
    @GenerateUncached
    public abstract static class AppendNode extends PNodeWithContext {
        private static final BranchProfile[] DISABLED = new BranchProfile[]{BranchProfile.getUncached()};

        public abstract void execute(PList list, Object value);

        static BranchProfile[] getUpdateStoreProfile() {
            return new BranchProfile[1];
        }

        static BranchProfile[] getUpdateStoreProfileUncached() {
            return DISABLED;
        }

        @Specialization
        public void appendObjectGeneric(PList list, Object value,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached(value = "getUpdateStoreProfile()", uncached = "getUpdateStoreProfileUncached()", dimensions = 1) BranchProfile[] updateStoreProfile) {
            if (updateStoreProfile[0] == null) {
                // Executed for the first time. We don't pollute the AppendNode specializations,
                // yet, in case we're transitioning exactly once, because we'll pontentially pass
                // that information on to the list origin and it'll never happen again.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SequenceStorage newStore = SequenceStorageNodes.AppendNode.getUncached().execute(list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                updateStoreProfile[0] = BranchProfile.create();
                list.setSequenceStorage(newStore);
                if (list.getOrigin() != null && newStore instanceof BasicSequenceStorage) {
                    list.getOrigin().reportUpdatedCapacity((BasicSequenceStorage) newStore);
                }
            } else {
                SequenceStorage newStore = appendNode.execute(list.getSequenceStorage(), value, ListGeneralizationNode.SUPPLIER);
                if (CompilerDirectives.inInterpreter() && list.getOrigin() != null && newStore instanceof BasicSequenceStorage) {
                    list.setSequenceStorage(newStore);
                    list.getOrigin().reportUpdatedCapacity((BasicSequenceStorage) newStore);
                } else if (list.getSequenceStorage() != newStore) {
                    updateStoreProfile[0].enter();
                    list.setSequenceStorage(newStore);
                }
            }
        }

        public static AppendNode create() {
            return AppendNodeGen.create();
        }

        public static AppendNode getUncached() {
            return AppendNodeGen.getUncached();
        }
    }
}
