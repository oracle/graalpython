/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@ImportStatic(PGuards.class)
public abstract class BinarySubscrSeq extends Node {
    public static final QuickeningGeneralizeException GENERALIZE_RESULT = new QuickeningGeneralizeException(0);
    public static final QuickeningGeneralizeException GENERALIZE_COLLECTION = new QuickeningGeneralizeException(0);

    @GenerateInline(false) // used in BCI root node
    public abstract static class ONode extends BinarySubscrSeq {
        public abstract Object execute(Object sequence, int index);

        @Specialization(guards = "isBuiltinList(sequence)")
        Object doList(PList sequence, int index,
                        @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(sequence.getSequenceStorage(), index);
        }

        @Specialization(guards = "isBuiltinTuple(sequence)")
        Object doList(PTuple sequence, int index,
                        @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(sequence.getSequenceStorage(), index);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneralize(Object sequence, int index) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw GENERALIZE_COLLECTION;
        }

        public static ONode create() {
            return BinarySubscrSeqFactory.ONodeGen.create();
        }
    }

    @GenerateInline(false) // used in BCI root node
    public abstract static class INode extends BinarySubscrSeq {
        public abstract int execute(Object sequence, int index) throws QuickeningGeneralizeException;

        @Specialization(guards = "isBuiltinList(sequence)")
        int doList(PList sequence, int index,
                        @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) {
            try {
                return getItemNode.executeInt(sequence.getSequenceStorage(), index);
            } catch (UnexpectedResultException e) {
                // Truffle doesn't let us throw UnexpectedResultException without rewriteOn
                throw GENERALIZE_RESULT;
            }
        }

        @Specialization(guards = "isBuiltinTuple(sequence)")
        int doList(PTuple sequence, int index,
                        @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            try {
                return getItemNode.executeInt(sequence.getSequenceStorage(), index);
            } catch (UnexpectedResultException e) {
                // Truffle doesn't let us throw UnexpectedResultException without rewriteOn
                throw GENERALIZE_RESULT;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        int doGeneralize(Object sequence, int index) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw GENERALIZE_COLLECTION;
        }

        public static INode create() {
            return BinarySubscrSeqFactory.INodeGen.create();
        }
    }

    @GenerateInline(false) // used in BCI root node
    public abstract static class DNode extends BinarySubscrSeq {
        public abstract double execute(Object sequence, int index);

        @Specialization(guards = "isBuiltinList(sequence)")
        double doList(PList sequence, int index,
                        @Cached("createForList()") SequenceStorageNodes.GetItemNode getItemNode) {
            try {
                return getItemNode.executeDouble(sequence.getSequenceStorage(), index);
            } catch (UnexpectedResultException e) {
                // Truffle doesn't let us throw UnexpectedResultException without rewriteOn
                throw GENERALIZE_RESULT;
            }
        }

        @Specialization(guards = "isBuiltinTuple(sequence)")
        double doList(PTuple sequence, int index,
                        @Cached("createForTuple()") SequenceStorageNodes.GetItemNode getItemNode) {
            try {
                return getItemNode.executeDouble(sequence.getSequenceStorage(), index);
            } catch (UnexpectedResultException e) {
                // Truffle doesn't let us throw UnexpectedResultException without rewriteOn
                throw GENERALIZE_RESULT;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        double doGeneralize(Object sequence, int index) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw GENERALIZE_COLLECTION;
        }

        public static DNode create() {
            return BinarySubscrSeqFactory.DNodeGen.create();
        }
    }
}
