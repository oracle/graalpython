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
package com.oracle.graal.python.nodes.builtins;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTupleObject__ob_item;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyVarObject__ob_size;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CreateStorageFromIteratorNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class TupleNodes {

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 40 -> 21
    public abstract static class ConstructTupleNode extends PNodeWithContext {
        public abstract PTuple execute(Frame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        static PTuple tuple(@SuppressWarnings("unused") PNone none,
                        @Bind PythonLanguage language) {
            return PFactory.createEmptyTuple(language);
        }

        @Specialization(guards = "isBuiltinTuple(iterable)")
        static PTuple tuple(PTuple iterable) {
            return iterable;
        }

        @Specialization(guards = "isBuiltinList(iterable)")
        static PTuple list(PList iterable,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached SequenceStorageNodes.CopyNode copyNode) {
            return PFactory.createTuple(language, copyNode.execute(inliningTarget, iterable.getSequenceStorage()));
        }

        @Fallback
        @InliningCutoff
        static PTuple generic(VirtualFrame frame, Object iterable,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CreateStorageFromIteratorNode storageNode,
                        @Cached PyObjectGetIter getIter) {
            Object iterObj = getIter.execute(frame, inliningTarget, iterable);
            return PFactory.createTuple(language, storageNode.execute(frame, iterObj));
        }

        @NeverDefault
        public static ConstructTupleNode create() {
            return TupleNodesFactory.ConstructTupleNodeGen.create();
        }

        public static ConstructTupleNode getUncached() {
            return TupleNodesFactory.ConstructTupleNodeGen.getUncached();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetTupleStorage extends Node {
        public abstract SequenceStorage execute(Node inliningTarget, Object tuple);

        @Specialization
        SequenceStorage getManaged(PTuple tuple) {
            return tuple.getSequenceStorage();
        }

        @Specialization
        SequenceStorage getNative(PythonAbstractNativeObject tuple,
                        @Cached(inline = false) GetNativeTupleStorage getNativeTupleStorage) {
            return getNativeTupleStorage.execute(tuple);
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class GetNativeTupleStorage extends Node {
        public abstract NativeObjectSequenceStorage execute(PythonAbstractNativeObject tuple);

        @Specialization
        NativeObjectSequenceStorage getNative(PythonAbstractNativeObject tuple,
                        @Cached CStructAccess.ReadPointerNode getContents,
                        @Cached CStructAccess.ReadI64Node readI64Node) {
            assert PyTupleCheckNode.executeUncached(tuple);
            Object array = getContents.readFromObj(tuple, PyTupleObject__ob_item);
            int size = (int) readI64Node.readFromObj(tuple, PyVarObject__ob_size);
            return NativeObjectSequenceStorage.create(array, size, size, false);
        }
    }
}
