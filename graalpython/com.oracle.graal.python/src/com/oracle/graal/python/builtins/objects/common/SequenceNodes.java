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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A_SEQUENCE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.CachedGetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.SetSequenceStorageNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ForeignSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public abstract class SequenceNodes {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    public abstract static class IsInBoundsNode extends Node {
        public abstract boolean execute(Node inliningTarget, PSequence seq, long idx);

        @Specialization
        static boolean doWithStorage(Node inliningTarget, PSequence seq, long idx,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage) {
            int length = getStorage.execute(inliningTarget, seq).length();
            return 0 <= idx && idx < length;
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetPSequenceStorageNode extends PNodeWithContext {
        public abstract SequenceStorage execute(Node inliningTarget, PSequence seq);

        @Specialization
        static SequenceStorage doTuple(PTuple seq) {
            return seq.getSequenceStorage();
        }

        @Specialization
        static SequenceStorage doBytesLike(PBytesLike seq) {
            return seq.getSequenceStorage();
        }

        @Specialization
        static SequenceStorage doList(PList seq) {
            return seq.getSequenceStorage();
        }

        @Specialization
        static SequenceStorage doSequence(@SuppressWarnings("unused") PSequence seq) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetSequenceStorageNode extends PNodeWithContext {

        public abstract SequenceStorage execute(Node inliningTarget, Object seq);

        public static SequenceStorage executeUncached(Object seq) {
            return SequenceNodesFactory.GetSequenceStorageNodeGen.getUncached().execute(null, seq);
        }

        public final SequenceStorage executeCached(Object seq) {
            return execute(this, seq);
        }

        @Specialization
        static SequenceStorage doSequence(Node inliningTarget, PSequence seq,
                        @Cached GetPSequenceStorageNode getPSequenceStorageNode) {
            return getPSequenceStorageNode.execute(inliningTarget, seq);
        }

        // Note: this does not seem currently used but is good to accept foreign lists in more
        // places
        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, seq)", "interop.hasArrayElements(seq)"}, limit = "1")
        static SequenceStorage doForeign(Node inliningTarget, Object seq,
                        @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached InlinedBranchProfile errorProfile) {
            try {
                long size = interop.getArraySize(seq);
                return new ForeignSequenceStorage(seq, PInt.long2int(inliningTarget, size, errorProfile));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        static SequenceStorage doFallback(Node inliningTarget, Object seq) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, IS_NOT_A_SEQUENCE, seq);
        }

        @NeverDefault
        public static GetSequenceStorageNode create() {
            return SequenceNodesFactory.GetSequenceStorageNodeGen.create();
        }

        @NeverDefault
        public static GetSequenceStorageNode getUncached() {
            return SequenceNodesFactory.GetSequenceStorageNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetObjectArrayNode extends Node {

        public abstract Object[] execute(Node inliningTarget, Object seq);

        public static Object[] executeUncached(Object seq) {
            return GetObjectArrayNodeGen.getUncached().execute(null, seq);
        }

        @Specialization
        static Object[] doGeneric(Node inliningTarget, Object seq,
                        @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
            return toArrayNode.execute(inliningTarget, getSequenceStorageNode.execute(inliningTarget, seq));
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CachedGetObjectArrayNode extends Node {
        public abstract Object[] execute(Object seq);

        @Specialization
        Object[] doIt(Object seq,
                        @Cached GetObjectArrayNode node) {
            return node.execute(this, seq);
        }

        public static CachedGetObjectArrayNode create() {
            return CachedGetObjectArrayNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetSequenceStorageNode extends Node {

        public abstract void execute(Node inliningTarget, PSequence s, SequenceStorage storage);

        public static void executeUncached(PSequence s, SequenceStorage storage) {
            SetSequenceStorageNodeGen.getUncached().execute(null, s, storage);
        }

        @Specialization(guards = "s.getClass() == cachedClass", limit = "1")
        static void doSpecial(PSequence s, SequenceStorage storage,
                        @Cached("s.getClass()") Class<? extends PSequence> cachedClass) {
            cachedClass.cast(s).setSequenceStorage(storage);
        }

        @Specialization(replaces = "doSpecial")
        @TruffleBoundary
        static void doGeneric(PSequence s, SequenceStorage storage) {
            s.setSequenceStorage(storage);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CheckIsSequenceNode extends Node {

        public abstract void execute(Node inliningTarget, Object seq);

        @Specialization
        static void check(Node inliningTarget, Object obj,
                        @Cached PySequenceCheckNode sequenceCheckNode,
                        @Cached PRaiseNode raiseNode) {
            if (!sequenceCheckNode.execute(inliningTarget, obj)) {
                throw raiseNode.raise(inliningTarget, TypeError, IS_NOT_A_SEQUENCE, obj);
            }
        }
    }
}
