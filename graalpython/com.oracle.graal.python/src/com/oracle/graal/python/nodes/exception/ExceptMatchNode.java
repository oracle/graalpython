/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.exception;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 44 -> 25
public abstract class ExceptMatchNode extends Node {
    public abstract boolean executeMatch(Frame frame, Object exception, Object clause);

    private static void raiseIfNoException(VirtualFrame frame, Object clause, ValidExceptionNode isValidException, PRaiseNode raiseNode) {
        if (!isValidException.execute(frame, clause)) {
            raiseNoException(raiseNode);
        }
    }

    private static void raiseNoException(PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.CATCHING_CLS_NOT_ALLOWED);
    }

    @Specialization(guards = "isClass(inliningTarget, clause, isTypeNode)", limit = "1")
    static boolean matchPythonSingle(VirtualFrame frame, PException e, Object clause,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached IsTypeNode isTypeNode,
                    @Shared @Cached ValidExceptionNode isValidException,
                    @Cached GetClassNode getClassNode,
                    @Cached IsSubtypeNode isSubtype,
                    @Shared @Cached PRaiseNode raiseNode) {
        raiseIfNoException(frame, clause, isValidException, raiseNode);
        return isSubtype.execute(frame, getClassNode.execute(inliningTarget, e.getUnreifiedException()), clause);
    }

    @Specialization(guards = {"eLib.isException(e)", "clauseLib.isMetaObject(clause)"}, limit = "3", replaces = "matchPythonSingle")
    @SuppressWarnings("unused")
    static boolean matchJava(VirtualFrame frame, AbstractTruffleException e, Object clause,
                    @Shared @Cached ValidExceptionNode isValidException,
                    @CachedLibrary("e") InteropLibrary eLib,
                    @CachedLibrary("clause") InteropLibrary clauseLib,
                    @Shared @Cached PRaiseNode raiseNode) {
        // n.b.: we can only allow Java exceptions in clauses, because we cannot tell for other
        // foreign exception types if they *are* exception types
        raiseIfNoException(frame, clause, isValidException, raiseNode);
        try {
            return clauseLib.isMetaInstance(clause, e);
        } catch (UnsupportedMessageException e1) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Specialization
    static boolean matchTuple(VirtualFrame frame, Object e, PTuple clause,
                    @Bind("this") Node inliningTarget,
                    @Cached ExceptMatchNode recursiveNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
        // check for every type in the tuple
        SequenceStorage storage = clause.getSequenceStorage();
        int length = storage.length();
        for (int i = 0; i < length; i++) {
            Object clauseType = getItemNode.execute(inliningTarget, storage, i);
            if (recursiveNode.executeMatch(frame, e, clauseType)) {
                return true;
            }
        }
        return false;
    }

    @Fallback
    @SuppressWarnings("unused")
    static boolean fallback(VirtualFrame frame, Object e, Object clause,
                    @Shared @Cached PRaiseNode raiseNode) {
        raiseNoException(raiseNode);
        return false;
    }

    @NeverDefault
    public static ExceptMatchNode create() {
        return ExceptMatchNodeGen.create();
    }

    public static ExceptMatchNode getUncached() {
        return ExceptMatchNodeGen.getUncached();
    }
}
