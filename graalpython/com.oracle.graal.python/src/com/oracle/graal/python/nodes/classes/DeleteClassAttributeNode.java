/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "delete_class_member")
public abstract class DeleteClassAttributeNode extends StatementNode {
    private final String identifier;

    DeleteClassAttributeNode(String identifier) {
        this.identifier = identifier;
    }

    protected StatementNode createDeleteNsItem() {
        ReadIndexedArgumentNode namespace = ReadIndexedArgumentNode.create(0);
        return PythonLanguage.getCurrent().getNodeFactory().createDeleteItem(namespace.asExpression(), identifier);
    }

    public static DeleteClassAttributeNode create(String name) {
        return DeleteClassAttributeNodeGen.create(name);
    }

    Object getLocalsDict(VirtualFrame frame) {
        assert !PArguments.isGeneratorFrame(frame);
        PFrame pFrame = PArguments.getCurrentFrameInfo(frame).getPyFrame();
        if (pFrame != null) {
            return pFrame.getLocalsDict();
        }
        return null;
    }

    @Specialization(guards = {"localsDict != null", "getLocalsDict(frame) == localsDict"}, //
                    assumptions = "singleContextAssumption()", limit = "2")
    void deleteFromLocalsSingleCtx(VirtualFrame frame,
                    @Cached(value = "getLocalsDict(frame)", weak = true) Object localsDict,
                    @Cached("create()") DeleteItemNode delItemNode) {
        // class namespace overrides closure
        delItemNode.executeWith(frame, localsDict, identifier);
    }

    @Specialization(guards = "localsDict != null", replaces = "deleteFromLocalsSingleCtx")
    void deleteFromLocals(VirtualFrame frame,
                    @Bind("getLocalsDict(frame)") Object localsDict,
                    @Cached("create()") DeleteItemNode delItemNode) {
        // class namespace overrides closure
        delItemNode.executeWith(frame, localsDict, identifier);
    }

    @Specialization(guards = "localsDict == null")
    void deleteSingleCtx(VirtualFrame frame,
                    @SuppressWarnings("unused") @Bind("getLocalsDict(frame)") Object localsDict,
                    @Cached("createDeleteNsItem()") StatementNode deleteNsItem) {
        // delete attribute actual attribute
        deleteNsItem.executeVoid(frame);
    }
}
