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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "rhs", type = ExpressionNode.class)
public abstract class WriteGlobalNode extends StatementNode implements GlobalNode, WriteNode {
    protected final String attributeId;
    protected final IsBuiltinClassProfile builtinProfile = IsBuiltinClassProfile.create();
    protected final Assumption singleContextAssumption = PythonLanguage.getCurrent().singleContextAssumption;

    WriteGlobalNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static WriteGlobalNode create(String attributeId) {
        return create(attributeId, null);
    }

    public static WriteGlobalNode create(String attributeId, ExpressionNode rhs) {
        return WriteGlobalNodeGen.create(attributeId, rhs);
    }

    public void doWrite(VirtualFrame frame, boolean value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, int value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, long value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, double value) {
        executeWithValue(frame, value);
    }

    public void doWrite(VirtualFrame frame, Object value) {
        executeWithValue(frame, value);
    }

    public abstract void executeWithValue(VirtualFrame frame, boolean value);

    public abstract void executeWithValue(VirtualFrame frame, int value);

    public abstract void executeWithValue(VirtualFrame frame, long value);

    public abstract void executeWithValue(VirtualFrame frame, double value);

    public abstract void executeWithValue(VirtualFrame frame, Object value);

    private static PDict getGlobalsDict(VirtualFrame frame) {
        return (PDict) PArguments.getGlobals(frame);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isBuiltinDict(cachedGlobals, builtinProfile)"}, assumptions = "singleContextAssumption", limit = "1")
    void writeDictBooleanCached(VirtualFrame frame, boolean value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, (PDict) cachedGlobals, attributeId, value);
    }

    @Specialization(guards = "isBuiltinDict(getGlobals(frame), builtinProfile)", replaces = "writeDictBooleanCached")
    void writeDictBoolean(VirtualFrame frame, boolean value,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, getGlobalsDict(frame), attributeId, value);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isBuiltinDict(cachedGlobals, builtinProfile)"}, assumptions = "singleContextAssumption", limit = "1")
    void writeDictIntCached(VirtualFrame frame, int value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, (PDict) cachedGlobals, attributeId, value);
    }

    @Specialization(guards = "isBuiltinDict(getGlobals(frame), builtinProfile)", replaces = "writeDictIntCached")
    void writeDictInt(VirtualFrame frame, int value,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, getGlobalsDict(frame), attributeId, value);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isBuiltinDict(cachedGlobals, builtinProfile)"}, assumptions = "singleContextAssumption", limit = "1")
    void writeDictLongCached(VirtualFrame frame, long value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, (PDict) cachedGlobals, attributeId, value);
    }

    @Specialization(guards = "isBuiltinDict(getGlobals(frame), builtinProfile)", replaces = "writeDictLongCached")
    void writeDictLong(VirtualFrame frame, long value,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, getGlobalsDict(frame), attributeId, value);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isBuiltinDict(cachedGlobals, builtinProfile)"}, assumptions = "singleContextAssumption", limit = "1")
    void writeDictDoubleCached(VirtualFrame frame, double value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, (PDict) cachedGlobals, attributeId, value);
    }

    @Specialization(guards = "isBuiltinDict(getGlobals(frame), builtinProfile)", replaces = "writeDictDoubleCached")
    void writeDictDouble(VirtualFrame frame, double value,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, getGlobalsDict(frame), attributeId, value);
    }

    @Specialization(replaces = {
                    "writeDictBooleanCached", "writeDictBoolean", "writeDictInt", "writeDictIntCached", "writeDictLong", "writeDictLongCached", "writeDictDouble", "writeDictDoubleCached"
    }, guards = {"getGlobals(frame) == cachedGlobals", "isBuiltinDict(cachedGlobals, builtinProfile)"}, assumptions = "singleContextAssumption", limit = "1")
    void writeDictObjectCached(VirtualFrame frame, Object value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, (PDict) cachedGlobals, attributeId, value);
    }

    @Specialization(replaces = {
                    "writeDictBooleanCached", "writeDictBoolean", "writeDictInt", "writeDictIntCached", "writeDictLong", "writeDictLongCached", "writeDictDouble", "writeDictDoubleCached",
                    "writeDictObjectCached"
    }, guards = "isBuiltinDict(getGlobals(frame), builtinProfile)")
    void writeDictObject(VirtualFrame frame, Object value,
                    @Cached("create()") HashingCollectionNodes.SetItemNode storeNode) {
        storeNode.execute(frame, getGlobalsDict(frame), attributeId, value);
    }

    @Specialization(replaces = {
                    "writeDictBooleanCached", "writeDictBoolean", "writeDictInt", "writeDictIntCached", "writeDictLong", "writeDictLongCached", "writeDictDouble", "writeDictDoubleCached",
                    "writeDictObject", "writeDictObjectCached"
    }, guards = {"getGlobals(frame) == cachedGlobals", "isDict(cachedGlobals)"}, assumptions = "singleContextAssumption", limit = "1")
    void writeGenericDictCached(VirtualFrame frame, Object value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create()") SetItemNode storeNode) {
        storeNode.executeWith(frame, cachedGlobals, attributeId, value);
    }

    @Specialization(replaces = {
                    "writeDictBooleanCached", "writeDictBoolean", "writeDictInt", "writeDictIntCached", "writeDictLong", "writeDictLongCached", "writeDictDouble", "writeDictDoubleCached",
                    "writeDictObject", "writeDictObjectCached", "writeGenericDictCached",
    }, guards = "isDict(getGlobals(frame))")
    void writeGenericDict(VirtualFrame frame, Object value,
                    @Cached("create()") SetItemNode storeNode) {
        storeNode.executeWith(frame, PArguments.getGlobals(frame), attributeId, value);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isModule(getGlobals(frame))"}, assumptions = "singleContextAssumption", limit = "1")
    void writeModuleCached(VirtualFrame frame, Object value,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("create(attributeId)") SetAttributeNode storeNode) {
        storeNode.executeVoid(frame, cachedGlobals, value);
    }

    @Specialization(guards = "isModule(getGlobals(frame))", replaces = "writeModuleCached")
    void writeModule(VirtualFrame frame, Object value,
                    @Cached("create(attributeId)") SetAttributeNode storeNode) {
        storeNode.executeVoid(frame, PArguments.getGlobals(frame), value);
    }

    @Override
    public String getAttributeId() {
        return attributeId;
    }
}
