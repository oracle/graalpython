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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class DeleteNameNode extends StatementNode implements AccessNameNode {
    @Child private DeleteGlobalNode deleteGlobalNode;
    @Child protected IsBuiltinClassProfile keyError = IsBuiltinClassProfile.create();
    protected final String attributeId;

    protected DeleteNameNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static DeleteNameNode create(String attributeId) {
        return DeleteNameNodeGen.create(attributeId);
    }

    private DeleteGlobalNode getDeleteGlobalNode() {
        if (deleteGlobalNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            deleteGlobalNode = insert(DeleteGlobalNode.create(attributeId));
        }
        return deleteGlobalNode;
    }

    private void deleteGlobalIfKeyError(VirtualFrame frame, PException e) {
        e.expect(PythonBuiltinClassType.KeyError, keyError);
        getDeleteGlobalNode().executeVoid(frame);
    }

    @Specialization(guards = "hasLocalsDict(frame, isBuiltin)")
    protected void readFromLocalsDict(VirtualFrame frame,
                    @Cached BranchProfile updatedStorage,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltin,
                    @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                    @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
        PDict frameLocals = (PDict) PArguments.getSpecialArgument(frame);
        HashingStorage storage = frameLocals.getDictStorage();
        Object key = attributeId;
        HashingStorage newStore = null;
        // TODO: FIXME: this might call __hash__ twice
        boolean hasKey = lib.hasKeyWithFrame(storage, key, hasFrame, frame);
        if (hasKey) {
            newStore = lib.delItemWithFrame(storage, key, hasFrame, frame);
        }

        if (hasKey) {
            if (newStore != storage) {
                updatedStorage.enter();
                frameLocals.setDictStorage(newStore);
            }
        } else {
            getDeleteGlobalNode().executeVoid(frame);
        }
    }

    @Specialization(guards = "hasLocals(frame)", replaces = "readFromLocalsDict")
    protected void readFromLocals(VirtualFrame frame,
                    @Cached("create()") DeleteItemNode delItem) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        try {
            delItem.executeWith(frame, frameLocals, attributeId);
        } catch (PException e) {
            deleteGlobalIfKeyError(frame, e);
        }
    }

    @Specialization(guards = "!hasLocals(frame)")
    protected void readFromLocals(VirtualFrame frame) {
        getDeleteGlobalNode().executeVoid(frame);
    }

    public String getAttributeId() {
        return attributeId;
    }
}
