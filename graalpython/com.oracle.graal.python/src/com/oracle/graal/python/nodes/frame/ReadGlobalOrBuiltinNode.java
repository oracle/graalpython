/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NameError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "read_global")
public abstract class ReadGlobalOrBuiltinNode extends ExpressionNode implements ReadNode, GlobalNode {
    @Child private ReadAttributeFromObjectNode readFromModuleNode = ReadAttributeFromObjectNode.create();
    @Child private ReadAttributeFromObjectNode readFromBuiltinsNode;
    @Child private PRaiseNode raiseNode;

    protected final String attributeId;
    protected final ConditionProfile isGlobalProfile = ConditionProfile.createBinaryProfile();
    protected final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();

    @CompilationFinal private ContextReference<PythonContext> contextRef;
    @CompilationFinal private boolean coreInitialized;

    protected ReadGlobalOrBuiltinNode(String attributeId) {
        this.attributeId = attributeId;
    }

    public static ReadGlobalOrBuiltinNode create(String attributeId) {
        return ReadGlobalOrBuiltinNodeGen.create(attributeId);
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteGlobalNode.create(attributeId, rhs);
    }

    @Specialization(guards = "isInModule(frame)")
    protected Object readGlobal(VirtualFrame frame) {
        Object result = readFromModuleNode.execute(PArguments.getGlobals(frame), attributeId);
        return returnGlobalOrBuiltin(result);
    }

    @Specialization(guards = "isInBuiltinDict(frame, builtinProfile)")
    protected Object readGlobalDict(VirtualFrame frame,
                    @Cached("create()") HashingStorageNodes.GetItemNode getItemNode,
                    @Cached("create()") @SuppressWarnings("unused") IsBuiltinClassProfile builtinProfile) {
        PythonObject globals = PArguments.getGlobals(frame);
        Object result = getItemNode.execute(((PDict) globals).getDictStorage(), attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization(guards = "isInDict(frame)", rewriteOn = PException.class)
    protected Object readGlobalDict(VirtualFrame frame,
                    @Cached("create()") GetItemNode getItemNode) {
        return returnGlobalOrBuiltin(getItemNode.execute(PArguments.getGlobals(frame), attributeId));
    }

    @Specialization(guards = "isInDict(frame)")
    protected Object readGlobalDictWithException(VirtualFrame frame,
                    @Cached("create()") GetItemNode getItemNode,
                    @Cached("create()") IsBuiltinClassProfile errorProfile) {
        try {
            Object result = getItemNode.execute(PArguments.getGlobals(frame), attributeId);
            return returnGlobalOrBuiltin(result);
        } catch (PException e) {
            e.expect(KeyError, errorProfile);
            return returnGlobalOrBuiltin(PNone.NO_VALUE);
        }
    }

    private Object returnGlobalOrBuiltin(Object result) {
        if (isGlobalProfile.profile(result != PNone.NO_VALUE)) {
            return result;
        } else {
            if (readFromBuiltinsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readFromBuiltinsNode = insert(ReadAttributeFromObjectNode.create());
            }
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = PythonLanguage.getContextRef();
            }
            PythonContext context = contextRef.get();
            PythonModule builtins = coreInitialized(context);
            Object builtin = readFromBuiltinsNode.execute(builtins, attributeId);
            if (isBuiltinProfile.profile(builtin != PNone.NO_VALUE)) {
                return builtin;
            } else {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                throw raiseNode.raise(NameError, "name '%s' is not defined", attributeId);
            }
        }
    }

    private PythonModule coreInitialized(PythonContext context) {
        PythonModule builtins;
        PythonCore core;
        if (!coreInitialized) {
            core = context.getCore();
            if (core.isInitialized()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                coreInitialized = true;
                builtins = context.getBuiltins();
            } else {
                builtins = core.lookupBuiltinModule("builtins");
            }
        } else {
            builtins = context.getBuiltins();
        }
        return builtins;
    }

    public String getAttributeId() {
        return attributeId;
    }
}
