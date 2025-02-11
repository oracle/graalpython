/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NameError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 21
public abstract class ReadBuiltinNode extends PNodeWithContext {
    public abstract Object execute(TruffleString attributeId);

    // TODO: (tfel) Think about how we can globally catch writes to the builtin
    // module so we can treat anything read from it as constant here.
    @Specialization(guards = "isSingleContext(this)")
    Object returnBuiltinFromConstantModule(TruffleString attributeId,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached InlinedConditionProfile isBuiltinProfile,
                    @Shared @Cached ReadAttributeFromObjectNode readFromBuiltinsNode,
                    @SuppressWarnings("unused") @Cached("getBuiltins()") PythonModule builtins) {
        return readBuiltinFromModule(attributeId, raiseNode, inliningTarget, isBuiltinProfile, builtins, readFromBuiltinsNode);
    }

    @InliningCutoff
    private static PException raiseNameNotDefined(Node inliningTarget, PRaiseNode raiseNode, TruffleString attributeId) {
        throw raiseNode.raise(inliningTarget, NameError, ErrorMessages.NAME_NOT_DEFINED, attributeId);
    }

    @InliningCutoff
    @Specialization(replaces = "returnBuiltinFromConstantModule")
    Object returnBuiltin(TruffleString attributeId,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached InlinedConditionProfile isBuiltinProfile,
                    @Shared @Cached ReadAttributeFromObjectNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedConditionProfile ctxInitializedProfile) {
        PythonModule builtins = getBuiltins(inliningTarget, ctxInitializedProfile);
        return returnBuiltinFromConstantModule(attributeId, inliningTarget, raiseNode, isBuiltinProfile, readFromBuiltinsNode, builtins);
    }

    private static Object readBuiltinFromModule(TruffleString attributeId, PRaiseNode raiseNode, Node inliningTarget,
                    InlinedConditionProfile isBuiltinProfile, PythonModule builtins,
                    ReadAttributeFromObjectNode readFromBuiltinsNode) {
        Object builtin = readFromBuiltinsNode.execute(builtins, attributeId);
        if (isBuiltinProfile.profile(inliningTarget, builtin != PNone.NO_VALUE)) {
            return builtin;
        } else {
            throw raiseNameNotDefined(inliningTarget, raiseNode, attributeId);
        }
    }

    @NeverDefault
    protected PythonModule getBuiltins() {
        CompilerAsserts.neverPartOfCompilation();
        return getBuiltins(null, InlinedConditionProfile.getUncached());
    }

    protected PythonModule getBuiltins(Node inliningTarget, InlinedConditionProfile ctxInitializedProfile) {
        PythonContext context = PythonContext.get(this);
        if (ctxInitializedProfile.profile(inliningTarget, context.isInitialized())) {
            return context.getBuiltins();
        } else {
            return context.lookupBuiltinModule(BuiltinNames.T_BUILTINS);
        }
    }

    @NeverDefault
    public static ReadBuiltinNode create() {
        return ReadBuiltinNodeGen.create();
    }

    public static ReadBuiltinNode getUncached() {
        return ReadBuiltinNodeGen.getUncached();
    }
}
