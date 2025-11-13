/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionGroupBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.PBaseExceptionGroup;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTION_GROUP;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

/**
 * If any exception is explicitly raised or reraised, (including naked exceptions) in a try-except*
 * handler, this node will take that exception and add it into the exception accumulator. Reraises
 * have their own group, and so does every Exception type of explicit raise. Raised naked exceptions
 * will reside in the outermost "final" exception group.
 */
@ImportStatic(PGuards.class)
@OperationProxy.Proxyable(storeBytecodeIndex = true)
@GenerateInline(false)
public abstract class HandleExceptionsInHandlerNode extends Node {
    public abstract Object execute(Frame frame, Object exception, Object exceptionsAcc, Object context, Object clause);

    public static final TruffleString T_DERIVE = tsLiteral("derive");

    @Specialization
    public static Object addToAcc(
                    VirtualFrame frame,
                    PException exceptionToAdd,
                    PBaseExceptionGroup exceptionGroupAcc,
                    PException exceptionOrig,
                    Object clause,
                    @Bind Node inliningTarget,
                    @Shared @Cached BaseExceptionGroupBuiltins.BaseExceptionGroupNode exceptionGroupNode,
                    @Cached PyObjectCallMethodObjArgs deriveExceptionGroup) {
        PBaseExceptionGroup outerExceptionGroup = null;
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        if (exceptionOrig.getUnreifiedException() instanceof PBaseExceptionGroup exceptionOrigUnreified) {
            if (exceptionToAdd.getUnreifiedException() instanceof PBaseExceptionGroup exceptionGroupToAdd) {
                Object parent = exceptionGroupToAdd.getParent();
                if (parent == exceptionOrig) {
                    // there are at most two levels of exception groups created when handling
                    // exception groups:
                    // 1. inner groups, formed by re-throws (of which each have a separate group)
                    // and coupling of all reraised and unhandled exception groups
                    // 2. singular outer group, which encapsulates all inner groups iff there are
                    // more than one of such groups.
                    // However, if there was only one inner group created, no outer group is to be
                    // created...
                    // We will normalize this at the end.

                    if (exceptionGroupAcc.getIsOuter()) {
                        // two layers of exception groups already exists; no more layers need to be
                        // created

                        // copy over all exception groups in acc
                        ArrayBuilder<Object> exceptionGroupsInAcc = new ArrayBuilder<>();
                        for (Object exc : exceptionGroupAcc.getExceptions()) {
                            exceptionGroupsInAcc.add(exc);
                        }

                        if (clause == PNone.NONE) {
                            // uncaught grouped exceptions should be bundled together with
                            // reraised ones
                            exceptionGroupToAdd.setContainsReraises(true);
                        }
                        exceptionGroupsInAcc.add(exceptionGroupToAdd);

                        outerExceptionGroup = (PBaseExceptionGroup) deriveExceptionGroup.execute(
                                        frame,
                                        inliningTarget,
                                        exceptionGroupAcc,
                                        T_DERIVE,
                                        PFactory.createTuple(language, exceptionGroupsInAcc.toArray(new Object[0])));

                        outerExceptionGroup.setIsOuter(true);
                    } else {
                        // only one layer of exception groups; new layer needs to be created
                        if (clause == PNone.NONE) {
                            exceptionGroupToAdd.setContainsReraises(true);
                        }

                        outerExceptionGroup = exceptionGroupNode.execute(
                                        frame,
                                        exceptionOrigUnreified.getPythonClass(),
                                        StringLiterals.T_EMPTY_STRING,
                                        PFactory.createList(
                                                        PythonLanguage.get(inliningTarget),
                                                        new Object[]{exceptionGroupAcc, exceptionToAdd.getUnreifiedException()}));
                        outerExceptionGroup.setIsOuter(true);
                    }
                }
            } else {
                // we are adding non-group exception
                ArrayBuilder<Object> exceptionGroupsInAcc = new ArrayBuilder<>();
                for (Object exc : exceptionGroupAcc.getExceptions()) {
                    exceptionGroupsInAcc.add(exc);
                }
                exceptionGroupsInAcc.add(exceptionToAdd.getUnreifiedException());
                outerExceptionGroup = (PBaseExceptionGroup) deriveExceptionGroup.execute(
                                frame,
                                inliningTarget,
                                exceptionGroupAcc,
                                T_DERIVE,
                                PFactory.createTuple(language, exceptionGroupsInAcc.toArray(new Object[0])));
            }
            if (outerExceptionGroup != null) {
                outerExceptionGroup.setParent(exceptionGroupAcc.getParent());
                outerExceptionGroup.setTraceback(exceptionOrigUnreified.getTraceback());
                return outerExceptionGroup;
            }
        }

        return PNone.NONE;
    }

    @Specialization(guards = {"isNone(exceptionsAcc)", "containsPBaseExceptionGroup(exception)"})
    public static Object init(
                    VirtualFrame frame,
                    PException exception,
                    Object exceptionsAcc,
                    PException context,
                    Object clause,
                    @Bind Node inliningTarget) {
        if (exception.getUnreifiedException() instanceof PBaseExceptionGroup exceptionGroup && context.getUnreifiedException() instanceof PBaseException contextUnreified) {
            if (clause == PNone.NONE) {
                // unmatched exceptions
                exceptionGroup.setParent(exceptionGroup.getParent());
            }
            exceptionGroup.setTraceback(contextUnreified.getTraceback());
            return exceptionGroup;
        }
        return PNone.NONE;
    }

    @Specialization(guards = {"isNone(exceptionsAcc)", "!containsPBaseExceptionGroup(exception)"})
    public static Object makePythonSingle(VirtualFrame frame,
                    PException exception,
                    Object exceptionsAcc,
                    PException context,
                    Object clause,
                    @Bind Node inliningTarget,
                    @Cached.Shared @Cached BaseExceptionGroupBuiltins.BaseExceptionGroupNode exceptionGroupNode,
                    @Cached PyObjectGetAttr getAttr) {
        PBaseExceptionGroup group;
        group = exceptionGroupNode.execute(frame,
                        getAttr.execute(inliningTarget, PythonContext.get(inliningTarget).getBuiltins(), T_EXCEPTION_GROUP),
                        StringLiterals.T_EMPTY_STRING,
                        PFactory.createList(PythonLanguage.get(inliningTarget), new Object[]{exception.getUnreifiedException()}));
        group.setIsOuter(true);
        return group;
    }

    @Fallback
    public static Object doNothing(VirtualFrame frame, Object exceptionObj, Object exceptionsAcc, Object exceptionOrig, Object clause) {
        return exceptionsAcc;
    }
}
