/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.GetForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.RemoveForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.SetForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

abstract class AccessForeignItemNodes {

    @ImportStatic(Message.class)
    protected abstract static class AccessForeignItemBaseNode extends PBaseNode {

        protected static boolean isSlice(Object o) {
            return o instanceof PSlice;
        }

        protected int getForeignSize(TruffleObject object, Node getSizeNode, PForeignToPTypeNode foreign2PTypeNode) throws UnsupportedMessageException {
            // TODO type check
            Object foreignSizeObj = foreign2PTypeNode.executeConvert(ForeignAccess.sendGetSize(getSizeNode, object));
            if (PGuards.isInteger(foreignSizeObj)) {
                return ((Number) foreignSizeObj).intValue();
            }
            throw raise(TypeError, "list indices must be integers, not %p", foreignSizeObj);
        }

        protected SliceInfo materializeSlice(PSlice idxSlice, TruffleObject object, Node getSizeNode, PForeignToPTypeNode foreign2PTypeNode) throws UnsupportedMessageException {

            // determine start
            boolean isStartMissing = false;
            int start = idxSlice.getStart();
            if (start == SequenceUtil.MISSING_INDEX) {
                start = 0;
                isStartMissing = true;
            }

            // determine stop
            int end = idxSlice.getStop();
            int foreignSize = -1;
            if (end == SequenceUtil.MISSING_INDEX) {
                foreignSize = getForeignSize(object, getSizeNode, foreign2PTypeNode);
                end = foreignSize;
            } else if (isStartMissing) {
                foreignSize = getForeignSize(object, getSizeNode, foreign2PTypeNode);
            }

            // determine length (foreignSize is only required if start or stop is missing)
            return idxSlice.computeActualIndices(foreignSize);
        }
    }

    protected abstract static class GetForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(TruffleObject object, Object idx);

        @Specialization
        public Object doForeignObjectSlice(TruffleObject object, PSlice idxSlice,
                        @Cached("READ.createNode()") Node foreignRead,
                        @Cached("GET_SIZE.createNode()") Node getSizeNode,
                        @Cached("create()") PForeignToPTypeNode foreign2PTypeNode,
                        @Cached("create()") PythonObjectFactory factory) {
            SliceInfo mslice;
            try {
                mslice = materializeSlice(idxSlice, object, getSizeNode, foreign2PTypeNode);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw raise(RuntimeError, e.getMessage());
            }
            Object[] values = new Object[mslice.length];
            for (int i = mslice.start, j = 0; i < mslice.stop; i += mslice.step, j++) {
                values[j] = readForeignValue(object, i, foreignRead, foreign2PTypeNode);
            }
            return factory.createList(values);
        }

        @Specialization(guards = "!isSlice(idx)")
        public Object doForeignObject(TruffleObject object, Object idx,
                        @Cached("READ.createNode()") Node foreignRead,
                        @Cached("create()") PTypeToForeignNode ptypeToForeignNode,
                        @Cached("create()") PForeignToPTypeNode foreign2PTypeNode) {
            Object convertedIdx = ptypeToForeignNode.executeConvert(idx);
            return readForeignValue(object, convertedIdx, foreignRead, foreign2PTypeNode);
        }

        private Object readForeignValue(TruffleObject object, Object idx, Node foreignRead, PForeignToPTypeNode foreign2PTypeNode) {
            try {
                return foreign2PTypeNode.executeConvert(ForeignAccess.sendRead(foreignRead, object, idx));
            } catch (UnsupportedMessageException ex) {
                throw raise(AttributeError, "%s instance has no attribute '__getitem__'", object);
            } catch (UnknownIdentifierException ex) {
                throw raise(KeyError, "%s instance has no attribute '__getitem__'", object);
            }
        }

        public static GetForeignItemNode create() {
            return GetForeignItemNodeGen.create();
        }
    }

    protected abstract static class SetForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(TruffleObject object, Object idx, Object value);

        @Specialization
        public Object doForeignObjectSlice(TruffleObject object, PSlice idxSlice, PSequence pvalues,
                        @Cached("WRITE.createNode()") Node foreignWrite,
                        @Cached("KEY_INFO.createNode()") Node keyInfoNode,
                        @Cached("HAS_SIZE.createNode()") Node hasSizeNode,
                        @Cached("GET_SIZE.createNode()") Node getSizeNode,
                        @Cached("create()") PTypeToForeignNode valueToForeignNode,
                        @Cached("create()") PForeignToPTypeNode foreign2PTypeNode) {

            try {
                SliceInfo mslice = materializeSlice(idxSlice, object, getSizeNode, foreign2PTypeNode);
                for (int i = mslice.start; i < mslice.stop; i += mslice.step) {
                    Object convertedValue = valueToForeignNode.executeConvert(pvalues.getItem(i));
                    writeForeignValue(object, i, convertedValue, foreignWrite, keyInfoNode, hasSizeNode, foreign2PTypeNode);
                }
                return PNone.NONE;
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw raise(RuntimeError, e.getMessage());
            }
        }

        @Specialization(guards = "!isSlice(idx)")
        public Object doForeignObject(TruffleObject object, Object idx, Object value,
                        @Cached("WRITE.createNode()") Node foreignWrite,
                        @Cached("KEY_INFO.createNode()") Node keyInfoNode,
                        @Cached("HAS_SIZE.createNode()") Node hasSizeNode,
                        @Cached("create()") PTypeToForeignNode indexToForeignNode,
                        @Cached("create()") PTypeToForeignNode valueToForeignNode,
                        @Cached("create()") PForeignToPTypeNode foreign2PTypeNode) {
            try {
                Object convertedIdx = indexToForeignNode.executeConvert(idx);
                Object convertedValue = valueToForeignNode.executeConvert(value);
                return writeForeignValue(object, convertedIdx, convertedValue, foreignWrite, keyInfoNode, hasSizeNode, foreign2PTypeNode);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw raise(RuntimeError, e.getMessage());
            }
        }

        private Object writeForeignValue(TruffleObject object, Object idx, Object value, Node foreignWrite, Node keyInfoNode, Node hasSizeNode, PForeignToPTypeNode foreign2PTypeNode)
                        throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException {
            int info = ForeignAccess.sendKeyInfo(keyInfoNode, object, idx);
            if (KeyInfo.isWritable(info) || ForeignAccess.sendHasSize(hasSizeNode, object)) {
                return foreign2PTypeNode.executeConvert(ForeignAccess.sendWrite(foreignWrite, object, idx, value));
            }
            // TODO error message
            CompilerDirectives.transferToInterpreter();
            throw raise(AttributeError, "%s instance has no attribute '__setitem__'", object);
        }

        public static SetForeignItemNode create() {
            return SetForeignItemNodeGen.create();
        }
    }

    protected abstract static class RemoveForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(TruffleObject object, Object idx);

        @Specialization
        public Object doForeignObjectSlice(TruffleObject object, PSlice idxSlice,
                        @Cached("REMOVE.createNode()") Node foreignRemove,
                        @Cached("KEY_INFO.createNode()") Node keyInfoNode,
                        @Cached("HAS_SIZE.createNode()") Node hasSizeNode,
                        @Cached("GET_SIZE.createNode()") Node getSizeNode,
                        @Cached("create()") PForeignToPTypeNode foreign2PTypeNode) {

            try {
                SliceInfo mslice = materializeSlice(idxSlice, object, getSizeNode, foreign2PTypeNode);
                for (int i = mslice.start; i < mslice.stop; i += mslice.step) {
                    removeForeignValue(object, i, foreignRemove, keyInfoNode, hasSizeNode);
                }
                return PNone.NONE;
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw raise(RuntimeError, e.getMessage());
            }
        }

        @Specialization(guards = "!isSlice(idx)")
        public Object doForeignObject(TruffleObject object, Object idx,
                        @Cached("REMOVE.createNode()") Node foreignRemove,
                        @Cached("KEY_INFO.createNode()") Node keyInfoNode,
                        @Cached("HAS_SIZE.createNode()") Node hasSizeNode,
                        @Cached("create()") PTypeToForeignNode indexToForeignNode) {
            try {
                Object convertedIdx = indexToForeignNode.executeConvert(idx);
                return removeForeignValue(object, convertedIdx, foreignRemove, keyInfoNode, hasSizeNode);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw raise(RuntimeError, e.getMessage());
            }
        }

        private Object removeForeignValue(TruffleObject object, Object idx, Node foreignRemove, Node keyInfoNode, Node hasSizeNode)
                        throws UnknownIdentifierException, UnsupportedMessageException {
            int info = ForeignAccess.sendKeyInfo(keyInfoNode, object, idx);
            if (KeyInfo.isRemovable(info) || ForeignAccess.sendHasSize(hasSizeNode, object)) {
                return ForeignAccess.sendRemove(foreignRemove, object, idx);
            }
            // TODO error message
            CompilerDirectives.transferToInterpreter();
            throw raise(AttributeError, "%s instance has no attribute '__delitem__'", object);
        }

        public static RemoveForeignItemNode create() {
            return RemoveForeignItemNodeGen.create();
        }
    }
}
