/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromModuleNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false)       // footprint reduction 48 -> 30
public abstract class ReadGlobalOrBuiltinNode extends PNodeWithContext {
    public final Object execute(VirtualFrame frame, TruffleString name) {
        CompilerAsserts.partialEvaluationConstant(name);
        return executeWithGlobals(frame, PArguments.getGlobals(frame), name);
    }

    protected abstract Object executeWithGlobals(VirtualFrame frame, Object globals, TruffleString name);

    public Object read(Frame frame, Object globals, TruffleString name) {
        CompilerAsserts.partialEvaluationConstant(name);
        return executeWithGlobals((VirtualFrame) frame, globals, name);
    }

    @NeverDefault
    public static ReadGlobalOrBuiltinNode create() {
        return ReadGlobalOrBuiltinNodeGen.create();
    }

    public static ReadGlobalOrBuiltinNode getUncached() {
        return ReadGlobalOrBuiltinNodeGen.getUncached();
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals"}, limit = "1")
    protected static Object readGlobalCached(@SuppressWarnings("unused") PythonModule globals, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Shared("readFromModule") @Cached ReadAttributeFromModuleNode readFromModuleNode,
                    @Cached(value = "globals", weak = true) PythonModule cachedGlobals) {
        Object result = readFromModuleNode.execute(cachedGlobals, attributeId);
        return returnGlobalOrBuiltin(result, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
    }

    @InliningCutoff
    @Specialization(replaces = "readGlobalCached")
    protected static Object readGlobal(PythonModule globals, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Shared("readFromModule") @Cached ReadAttributeFromModuleNode readFromModuleNode) {
        Object result = readFromModuleNode.execute(globals, attributeId);
        return returnGlobalOrBuiltin(result, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
    }

    static final class GlobalsDictStorageChanged extends RuntimeException {
        private static final GlobalsDictStorageChanged INSTANCE = new GlobalsDictStorageChanged();
        private static final long serialVersionUID = 2982918866373996561L;

        GlobalsDictStorageChanged() {
            super(null, null);
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals", "isBuiltinDict(cachedGlobals)"}, limit = "1", rewriteOn = GlobalsDictStorageChanged.class)
    protected static Object readGlobalBuiltinDictCachedUnchangedStorage(@SuppressWarnings("unused") PDict globals, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @SuppressWarnings("unused") @Cached(value = "globals", weak = true) PDict cachedGlobals,
                    @Cached(value = "globals.getDictStorage()", weak = true) HashingStorage cachedStorage,
                    @Exclusive @Cached HashingStorageGetItem getItem) {
        if (cachedGlobals.getDictStorage() != cachedStorage) {
            throw GlobalsDictStorageChanged.INSTANCE;
        }
        Object result = getItem.execute(inliningTarget, cachedStorage, attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
    }

    @InliningCutoff
    @Specialization(guards = {"isSingleContext()", "globals == cachedGlobals",
                    "isBuiltinDict(cachedGlobals)"}, replaces = "readGlobalBuiltinDictCachedUnchangedStorage", limit = "1")
    protected static Object readGlobalBuiltinDictCached(@SuppressWarnings("unused") PDict globals, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Cached(value = "globals", weak = true) PDict cachedGlobals,
                    @Exclusive @Cached HashingStorageGetItem getItem) {
        Object result = getItem.execute(inliningTarget, cachedGlobals.getDictStorage(), attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
    }

    @InliningCutoff
    @Specialization(guards = "isBuiltinDict(globals)", replaces = {"readGlobalBuiltinDictCached", "readGlobalBuiltinDictCachedUnchangedStorage"})
    protected static Object readGlobalBuiltinDict(@SuppressWarnings("unused") PDict globals, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Bind("globals.getDictStorage()") HashingStorage storage,
                    @Exclusive @Cached HashingStorageGetItem getItem) {
        Object result = getItem.execute(inliningTarget, storage, attributeId);
        return returnGlobalOrBuiltin(result == null ? PNone.NO_VALUE : result, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
    }

    @InliningCutoff
    @Specialization
    protected static Object readGlobalDictGeneric(VirtualFrame frame, PDict globals, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Cached PyObjectGetItem getItemNode,
                    @Cached IsBuiltinObjectProfile errorProfile) {
        try {
            Object result = getItemNode.execute(frame, inliningTarget, globals, attributeId);
            return returnGlobalOrBuiltin(result, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
        } catch (PException e) {
            e.expect(inliningTarget, KeyError, errorProfile);
            return returnGlobalOrBuiltin(PNone.NO_VALUE, attributeId, readFromBuiltinsNode, inliningTarget, wasReadFromModule);
        }
    }

    @Fallback
    protected Object syserr(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") TruffleString attributeId) {
        throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
    }

    private static Object returnGlobalOrBuiltin(Object result, TruffleString attributeId, ReadBuiltinNode readFromBuiltinsNode, Node inliningTarget, InlinedBranchProfile wasReadFromModule) {
        if (result != PNone.NO_VALUE) {
            wasReadFromModule.enter(inliningTarget);
            return result;
        } else {
            return readFromBuiltinsNode.execute(attributeId);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class Lazy extends Node {
        public final ReadGlobalOrBuiltinNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        public abstract ReadGlobalOrBuiltinNode execute(Node inliningTarget);

        @Specialization
        static ReadGlobalOrBuiltinNode doIt(@Cached(inline = false) ReadGlobalOrBuiltinNode node) {
            return node;
        }
    }
}
