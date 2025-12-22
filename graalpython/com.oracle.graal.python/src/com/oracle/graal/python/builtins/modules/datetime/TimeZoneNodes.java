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
package com.oracle.graal.python.builtins.modules.datetime;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class TimeZoneNodes {
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewNode extends Node {

        public abstract PTimeZone execute(Node inliningTarget, PythonContext context, Object cls, Object offset, Object name);

        public static NewNode getUncached() {
            return TimeZoneNodesFactory.NewNodeGen.getUncached();
        }

        @Specialization
        static PTimeZone newTimezone(Node inliningTarget, PythonContext context, Object cls, Object offsetObj, Object nameObject,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode timeDeltaCheckNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            if (!timeDeltaCheckNode.execute(inliningTarget, offsetObj)) {
                throw raiseNode.raise(inliningTarget,
                                TypeError,
                                ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                "timezone()",
                                1,
                                "datetime.timedelta",
                                offsetObj);
            }
            PTimeDelta offset = asManagedTimeDeltaNode.execute(inliningTarget, offsetObj);
            final TruffleString name;
            if (nameObject == PNone.NO_VALUE) {
                name = null;
            } else {
                try {
                    name = castToTruffleStringNode.execute(inliningTarget, nameObject);
                } catch (CannotCastException e) {
                    throw raiseNode.raise(inliningTarget,
                                    TypeError,
                                    ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                    "timezone()",
                                    2,
                                    "str",
                                    nameObject);
                }
            }

            DatetimeModuleBuiltins.validateUtcOffset(offset, inliningTarget);

            if (name == null && offset.isZero()) {
                return DatetimeModuleBuiltins.getUtcTimeZone(context);
            }

            Shape shape = getInstanceShape.execute(cls);
            return new PTimeZone(cls, shape, offset, name);
        }
    }
}
