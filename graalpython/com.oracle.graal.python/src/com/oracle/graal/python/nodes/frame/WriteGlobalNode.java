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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class WriteGlobalNode extends PNodeWithContext {
    private static final WriteGlobalNode UNCACHED = new WriteGlobalNode(null) {
        @Override
        public void executeObjectWithGlobals(VirtualFrame frame, Object globals, Object value) {
            throw CompilerDirectives.shouldNotReachHere("uncached WriteGlobalNode must be used with #write");
        }

        @Override
        public void write(Frame frame, Object globals, TruffleString name, Object value) {
            if (globals instanceof PythonModule) {
                WriteAttributeToObjectNode.getUncached().execute(globals, name, value);
            } else {
                PyObjectSetItem.getUncached().execute(frame, globals, name, value);
            }
        }
    };

    protected final TruffleString attributeId;

    WriteGlobalNode(TruffleString attributeId) {
        this.attributeId = attributeId;
    }

    public static WriteGlobalNode create(TruffleString attributeId) {
        return WriteGlobalNodeGen.create(attributeId);
    }

    public static WriteGlobalNode getUncached() {
        return UNCACHED;
    }

    public final void executeObject(VirtualFrame frame, Object value) {
        executeObjectWithGlobals(frame, PArguments.getGlobals(frame), value);
    }

    public void write(Frame frame, Object globals, TruffleString name, Object value) {
        assert name == attributeId : "cached WriteGlobalNode can only be used with cached attributeId";
        executeObjectWithGlobals((VirtualFrame) frame, globals, value);
    }

    public abstract void executeObjectWithGlobals(VirtualFrame frame, Object globals, Object value);

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals", "isBuiltinDict(cachedGlobals)"}, limit = "1")
    void writeDictObjectCached(VirtualFrame frame, @SuppressWarnings("unused") PDict globals, Object value,
                    @Cached(value = "globals", weak = true) PDict cachedGlobals,
                    @Shared("setItemDict") @Cached HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, cachedGlobals, attributeId, value);
    }

    @Specialization(replaces = "writeDictObjectCached", guards = "isBuiltinDict(globals)")
    void writeDictObject(VirtualFrame frame, PDict globals, Object value,
                    @Shared("setItemDict") @Cached HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, globals, attributeId, value);
    }

    @Specialization(replaces = {"writeDictObject", "writeDictObjectCached"})
    void writeGenericDict(VirtualFrame frame, PDict globals, Object value,
                    @Cached PyObjectSetItem storeNode) {
        storeNode.execute(frame, globals, attributeId, value);
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals"}, limit = "1")
    void writeModuleCached(@SuppressWarnings("unused") PythonModule globals, Object value,
                    @Cached(value = "globals", weak = true) PythonModule cachedGlobals,
                    @Shared("write") @Cached WriteAttributeToObjectNode write) {
        write.execute(cachedGlobals, attributeId, value);
    }

    @Specialization(replaces = "writeModuleCached")
    void writeModule(PythonModule globals, Object value,
                    @Shared("write") @Cached WriteAttributeToObjectNode write) {
        write.execute(globals, attributeId, value);
    }
}
