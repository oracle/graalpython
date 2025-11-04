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
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.PBaseExceptionGroup;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

/**
 * Final exception group raised from the try-except* block (if there is one) needs to adhere to
 * certain form. This node will merge all reraised and [by any handler] unmatched exceptions into
 * one group.
 */
@OperationProxy.Proxyable(storeBytecodeIndex = true)
@GenerateInline(false)
public abstract class EncapsulateExceptionGroupNode extends Node {
    public abstract Object execute(Frame frame, Object exceptionGroup, Object exceptionOrig);

    public static final TruffleString T_DERIVE = tsLiteral("derive");

    @Specialization
    public static Object matchExceptions(VirtualFrame frame,
                    PBaseExceptionGroup exceptionGroup,
                    PException exceptionOrig,
                    @Bind Node inliningTarget,
                    @Cached PyObjectCallMethodObjArgs deriveExceptionGroup,
                    @Cached InlinedConditionProfile baseExceptionProfile,
                    @Cached InlinedConditionProfile isExceptionGroupOuterProfile,
                    @Cached InlinedConditionProfile isBaseExceptionGroupProfile,
                    @Cached InlinedConditionProfile groupContainsReraisesProfile,
                    @Cached InlinedConditionProfile reraisedOrUnhandledSizeProfile,
                    @Cached InlinedConditionProfile exceptionGroupListSizeProfile,
                    @Cached InlinedConditionProfile exceptionGroupListFirstInstanceProfile) {
        // all groups gathered during whole try-except*
        ArrayBuilder<Object> exceptionGroupList = new ArrayBuilder<>();
        // reraised or unhandled exceptions
        ArrayBuilder<Object> reraisedUnhandledExceptions = new ArrayBuilder<>();
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        if (baseExceptionProfile.profile(inliningTarget, exceptionOrig.getUnreifiedException() instanceof PBaseException)) {
            PBaseException exceptionOrigUnreified = (PBaseException) exceptionOrig.getUnreifiedException();
            if (isExceptionGroupOuterProfile.profile(inliningTarget, exceptionGroup.getIsOuter())) {
                for (Object exceptionObj : exceptionGroup.getExceptions()) {
                    if (isBaseExceptionGroupProfile.profile(inliningTarget, exceptionObj instanceof PBaseExceptionGroup)) {
                        PBaseExceptionGroup group = (PBaseExceptionGroup) exceptionObj;
                        if (groupContainsReraisesProfile.profile(inliningTarget, group.getContainsReraises())) {
                            for (Object e : group.getExceptions()) {
                                reraisedUnhandledExceptions.add(e);
                            }
                        } else {
                            exceptionGroupList.add(group);
                        }
                    } else {
                        exceptionGroupList.add(exceptionObj);
                    }
                }
            } else {
                exceptionGroup.setTraceback(exceptionOrigUnreified.getTraceback());
                return PException.fromExceptionInfo(exceptionGroup, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
            if (reraisedOrUnhandledSizeProfile.profile(inliningTarget, reraisedUnhandledExceptions.size() != 0)) {
                PBaseExceptionGroup reraisedGroup = (PBaseExceptionGroup) deriveExceptionGroup.execute(
                                frame,
                                inliningTarget,
                                exceptionOrigUnreified,
                                T_DERIVE,
                                PFactory.createTuple(language, reraisedUnhandledExceptions.toArray(new Object[0])));
                reraisedGroup.setParent(exceptionGroup.getParent());
                exceptionGroupList.add(reraisedGroup);
            }

            PBaseExceptionGroup finalGroup;
            if (exceptionGroupListSizeProfile.profile(inliningTarget, exceptionGroupList.size() == 1)) {
                if (exceptionGroupListFirstInstanceProfile.profile(inliningTarget, exceptionGroupList.get(0) instanceof PBaseExceptionGroup)) {
                    finalGroup = (PBaseExceptionGroup) exceptionGroupList.get(0);
                    finalGroup.setParent(exceptionGroup.getParent());
                    finalGroup.setTraceback(exceptionOrigUnreified.getTraceback());
                } else {
                    return PException.fromExceptionInfo(exceptionGroupList.get(0), PythonOptions.isPExceptionWithJavaStacktrace(language));
                }
            } else {
                finalGroup = (PBaseExceptionGroup) deriveExceptionGroup.execute(
                                frame,
                                inliningTarget,
                                exceptionGroup,
                                T_DERIVE,
                                PFactory.createTuple(language, exceptionGroupList.toArray(new Object[0])));
                finalGroup.setParent(exceptionGroup.getParent());
                finalGroup.setTraceback(exceptionOrigUnreified.getTraceback());
            }
            return PException.fromExceptionInfo(finalGroup, PythonOptions.isPExceptionWithJavaStacktrace(language));
        }

        return PNone.NONE;
    }

    @Fallback
    public static Object doNothing(VirtualFrame frame, Object exceptionGroup, Object exceptionOrig,
                    @Cached ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode) {
        return getCaughtExceptionNode.execute(frame);
    }

    @NeverDefault
    public static EncapsulateExceptionGroupNode create() {
        return EncapsulateExceptionGroupNodeGen.create();
    }
}
