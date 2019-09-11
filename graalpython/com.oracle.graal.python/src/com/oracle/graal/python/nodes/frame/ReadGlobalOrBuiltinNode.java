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
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "read_global")
public abstract class ReadGlobalOrBuiltinNode extends ExpressionNode implements ReadNode, GlobalNode {
    @Child private ReadAttributeFromObjectNode readFromModuleNode = ReadAttributeFromObjectNode.create();
    @Child private ReadBuiltinNode readFromBuiltinsNode;

    protected final String attributeId;
    protected final ConditionProfile isGlobalProfile = ConditionProfile.createBinaryProfile();
    protected final Assumption singleContextAssumption = PythonLanguage.getCurrent().singleContextAssumption;

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

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isModule(cachedGlobals)"}, assumptions = "singleContextAssumption", limit = "1")
    protected Object readGlobalCached(@SuppressWarnings("unused") VirtualFrame frame,
                    @Cached("getGlobals(frame)") Object cachedGlobals) {
        Object result = readFromModuleNode.execute(cachedGlobals, attributeId);
        return returnGlobalOrBuiltin(result);
    }

    @Specialization(guards = "isModule(getGlobals(frame))", replaces = "readGlobalCached")
    protected Object readGlobal(VirtualFrame frame) {
        Object result = readFromModuleNode.execute(PArguments.getGlobals(frame), attributeId);
        return returnGlobalOrBuiltin(result);
    }

    protected static HashingStorage getStorage(Object cachedGlobals) {
        return ((PDict) cachedGlobals).getDictStorage();
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isBuiltinDict(cachedGlobals, builtinProfile)",
                    "getStorage(cachedGlobals) == cachedStorage"}, assumptions = "singleContextAssumption", limit = "1")
    protected Object readGlobalBuiltinDictCachedUnchangedStorage(VirtualFrame frame,
                    @SuppressWarnings("unused") @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached("getStorage(cachedGlobals)") HashingStorage cachedStorage,
                    @Cached HashingStorageNodes.GetItemNode getItemNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile builtinProfile) {
        Object result = getItemNode.execute(frame, cachedStorage, attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals",
                    "isBuiltinDict(cachedGlobals, builtinProfile)"}, assumptions = "singleContextAssumption", limit = "1", replaces = "readGlobalBuiltinDictCachedUnchangedStorage")
    protected Object readGlobalBuiltinDictCached(VirtualFrame frame,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached HashingStorageNodes.GetItemNode getItemNode,
                    @Cached @SuppressWarnings("unused") IsBuiltinClassProfile builtinProfile) {
        Object result = getItemNode.execute(frame, ((PDict) cachedGlobals).getDictStorage(), attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization(guards = "isBuiltinDict(getGlobals(frame), builtinProfile)", replaces = {"readGlobalBuiltinDictCached", "readGlobalBuiltinDictCachedUnchangedStorage"})
    protected Object readGlobalBuiltinDict(VirtualFrame frame,
                    @Cached HashingStorageNodes.GetItemNode getItemNode,
                    @Cached @SuppressWarnings("unused") IsBuiltinClassProfile builtinProfile) {
        PythonObject globals = PArguments.getGlobals(frame);
        Object result = getItemNode.execute(frame, ((PDict) globals).getDictStorage(), attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result);
    }

    @Specialization(guards = {"getGlobals(frame) == cachedGlobals", "isDict(cachedGlobals)"}, rewriteOn = PException.class, assumptions = "singleContextAssumption", limit = "1")
    protected Object readGlobalDictCached(VirtualFrame frame,
                    @Cached("getGlobals(frame)") Object cachedGlobals,
                    @Cached GetItemNode getItemNode) {
        return returnGlobalOrBuiltin(getItemNode.execute(frame, cachedGlobals, attributeId));
    }

    @Specialization(guards = "isDict(getGlobals(frame))", rewriteOn = PException.class, replaces = "readGlobalDictCached")
    protected Object readGlobalDict(VirtualFrame frame,
                    @Cached GetItemNode getItemNode) {
        return returnGlobalOrBuiltin(getItemNode.execute(frame, PArguments.getGlobals(frame), attributeId));
    }

    @Specialization(guards = "isDict(getGlobals(frame))", replaces = {"readGlobalDict", "readGlobalDictCached"})
    protected Object readGlobalDictWithException(VirtualFrame frame,
                    @Cached GetItemNode getItemNode,
                    @Cached IsBuiltinClassProfile errorProfile) {
        try {
            Object result = getItemNode.execute(frame, PArguments.getGlobals(frame), attributeId);
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
                readFromBuiltinsNode = insert(ReadBuiltinNodeGen.create(attributeId));
            }
            return readFromBuiltinsNode.execute();
        }
    }

    public String getAttributeId() {
        return attributeId;
    }
}

abstract class ReadBuiltinNode extends Node {
    protected static final Assumption singleCoreNotInitialized = Truffle.getRuntime().createAssumption();

    protected final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();
    protected final Assumption singleContextAssumption = PythonLanguage.getCurrent().singleContextAssumption;
    protected final String attributeId;

    @CompilationFinal private ConditionProfile isCoreInitializedProfile;

    @Child protected ReadAttributeFromObjectNode readFromBuiltinsNode = ReadAttributeFromObjectNode.create();
    @Child private PRaiseNode raiseNode;

    public abstract Object execute();

    protected ReadBuiltinNode(String attributeId) {
        this.attributeId = attributeId;
    }

    // TODO: (tfel) Think about how we can globally catch writes to the builtin
    // module so we can treat anything read from it as constant here.
    @Specialization(assumptions = "singleContextAssumption")
    Object returnBuiltinFromConstantModule(
                    @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                    @SuppressWarnings("unused") @Cached("getBuiltins(context)") PythonModule builtins) {
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

    @Specialization
    Object returnBuiltin(
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonModule builtins = getBuiltins(context);
        return returnBuiltinFromConstantModule(context, builtins);
    }

    protected PythonModule getBuiltins(PythonContext context) {
        PythonCore core = context.getCore();
        if (ensureIsCoreInitializedProfile().profile(core.isInitialized())) {
            return context.getBuiltins();
        } else {
            return core.lookupBuiltinModule(BuiltinNames.BUILTINS);
        }
    }

    private ConditionProfile ensureIsCoreInitializedProfile() {
        if (isCoreInitializedProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isCoreInitializedProfile = ConditionProfile.createBinaryProfile();
        }
        return isCoreInitializedProfile;
    }
}
