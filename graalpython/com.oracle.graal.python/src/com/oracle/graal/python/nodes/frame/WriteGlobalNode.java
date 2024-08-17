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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 60 -> 42
public abstract class WriteGlobalNode extends PNodeWithContext {
    public static WriteGlobalNode getUncached() {
        return WriteGlobalNodeGen.getUncached();
    }

    @NeverDefault
    public static WriteGlobalNode create() {
        return WriteGlobalNodeGen.create();
    }

    public final void executeObject(VirtualFrame frame, TruffleString name, Object value) {
        CompilerAsserts.compilationConstant(name);
        executeObjectWithGlobals(frame, PArguments.getGlobals(frame), name, value);
    }

    public final void write(Frame frame, Object globals, TruffleString name, Object value) {
        CompilerAsserts.compilationConstant(name);
        executeObjectWithGlobals((VirtualFrame) frame, globals, name, value);
    }

    public abstract void executeObjectWithGlobals(VirtualFrame frame, Object globals, TruffleString name, Object value);

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals", "isBuiltinDict(cachedGlobals)"}, limit = "1")
    void writeDictObjectCached(VirtualFrame frame, @SuppressWarnings("unused") PDict globals, TruffleString attributeId, Object value,
                    @Bind("this") Node inliningTarget,
                    @Cached(value = "globals", weak = true) PDict cachedGlobals,
                    @Shared("setItemDict") @Cached HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, inliningTarget, cachedGlobals, attributeId, value);
    }

    @Specialization(replaces = "writeDictObjectCached", guards = "isBuiltinDict(globals)")
    void writeDictObject(VirtualFrame frame, PDict globals, TruffleString attributeId, Object value,
                    @Bind("this") Node inliningTarget,
                    @Shared("setItemDict") @Cached HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, inliningTarget, globals, attributeId, value);
    }

    @Specialization(replaces = {"writeDictObject", "writeDictObjectCached"})
    void writeGenericDict(VirtualFrame frame, PDict globals, TruffleString attributeId, Object value,
                    @Bind("this") Node inliningTarget,
                    @Cached PyObjectSetItem storeNode) {
        storeNode.execute(frame, inliningTarget, globals, attributeId, value);
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals"}, limit = "1")
    void writeModuleCached(@SuppressWarnings("unused") PythonModule globals, TruffleString attributeId, Object value,
                    @Cached(value = "globals", weak = true) PythonModule cachedGlobals,
                    @Shared("write") @Cached WriteAttributeToObjectNode write) {
        write.execute(cachedGlobals, attributeId, value);
    }

    @Specialization(replaces = "writeModuleCached")
    void writeModule(PythonModule globals, TruffleString attributeId, Object value,
                    @Shared("write") @Cached WriteAttributeToObjectNode write) {
        write.execute(globals, attributeId, value);
    }
}
