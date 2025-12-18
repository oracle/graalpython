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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETINITARGS__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetInstanceShape;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetStateNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PTzInfo, PythonBuiltinClassType.PTimezone})
public final class TzInfoBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = TzInfoBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TzInfoBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "datetime.tzinfo", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static PTzInfo newTzInfo(Object cls, Object[] arguments, PKeyword[] keywords) {
            return new PTzInfo(cls, GetInstanceShape.executeUncached(cls));
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class UtcOffsetNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object utcOffset(Object self, Object dt,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget,
                            NotImplementedError,
                            ErrorMessages.A_TZINFO_SUBCLASS_MUST_IMPLEMENT_S,
                            "utcoffset");
        }
    }

    @Builtin(name = "dst", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class DstNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object dst(Object self, Object dt,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget,
                            NotImplementedError,
                            ErrorMessages.A_TZINFO_SUBCLASS_MUST_IMPLEMENT_S,
                            "dst");
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class TzNameNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object tzName(Object self, Object dt,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget,
                            NotImplementedError,
                            ErrorMessages.A_TZINFO_SUBCLASS_MUST_IMPLEMENT_S,
                            "tzname");
        }
    }

    @Builtin(name = "fromutc", minNumOfPositionalArgs = 1, parameterNames = {"$self", "dt"})
    @GenerateNodeFactory
    public abstract static class FromUtcNode extends PythonBinaryBuiltinNode {

        private static final TruffleString T_UTCOFFSET = tsLiteral("utcoffset");
        private static final TruffleString T_DST = tsLiteral("dst");

        @Specialization
        static Object fromUtc(VirtualFrame frame, PTzInfo self, Object dateTime,
                        @Bind Node inliningTarget,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile profile,
                        @Cached DateTimeNodes.TzInfoNode tzInfoNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode,
                        @Cached TimeDeltaNodes.NewNode newTimeDeltaNode,
                        @Cached DateTimeNodes.SubclassNewNode dateTimeSubclassNewNode) {
            if (!profile.profileObject(inliningTarget, dateTime, PythonBuiltinClassType.PDateTime)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.FROMUTC_ARGUMENT_MUST_BE_A_DATETIME);
            }
            Object tzInfo = tzInfoNode.execute(inliningTarget, dateTime);
            if (tzInfo != self) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FROMUTC_DT_TZINFO_IS_NOT_SELF);
            }

            Object offsetObject = callMethodObjArgs.execute(frame, inliningTarget, dateTime, T_UTCOFFSET);
            if (offsetObject instanceof PNone) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FROMUTC_NON_NONE_UTCOFFSET_RESULT_REQUIRED);
            }

            Object dstObject = callMethodObjArgs.execute(frame, inliningTarget, dateTime, T_DST);
            if (dstObject instanceof PNone) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FROMUTC_NON_NONE_DST_RESULT_REQUIRED);
            }

            PTimeDelta offset = (PTimeDelta) offsetObject;
            PTimeDelta dst = (PTimeDelta) dstObject;

            // calculate `offset - dst` (that's standard utc offset)
            PTimeDelta offsetStandard = newTimeDeltaNode.execute(inliningTarget,
                            PythonBuiltinClassType.PTimeDelta,
                            offset.days - dst.days,
                            offset.seconds - dst.seconds,
                            offset.microseconds - dst.microseconds,
                            0,
                            0,
                            0,
                            0);

            Object dateTimeInTimeZone = DatetimeModuleBuiltins.addOffsetToDateTime(dateTime, offsetStandard, dateTimeSubclassNewNode, inliningTarget);

            if (tzInfo == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FROMUTC_TZ_DST_GAVE_INCONSISTENT_RESULT_CANNOT_CONVERT);
            }

            PTimeDelta dstNew = DatetimeModuleBuiltins.callDst(self, dateTimeInTimeZone, frame, inliningTarget, callMethodObjArgs, raiseNode);

            if (dstNew == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FROMUTC_TZ_DST_GAVE_INCONSISTENT_RESULT_CANNOT_CONVERT);
            }

            if (dstNew.isZero()) {
                return dateTimeInTimeZone;
            } else {
                return DatetimeModuleBuiltins.addOffsetToDateTime(dateTimeInTimeZone, dstNew, dateTimeSubclassNewNode, inliningTarget);
            }
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(VirtualFrame frame, Object self,
                        @Cached PyObjectLookupAttr objectLookupAttr,
                        @Cached PyObjectGetStateNode getStateNode,
                        @Cached CallNode callNode,
                        @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language,
                        @Bind Node inliningTarget) {
            Object type = getClassNode.execute(inliningTarget, self);

            final Object arguments;
            Object func = objectLookupAttr.execute(frame, inliningTarget, self, T___GETINITARGS__);
            if (func == PNone.NO_VALUE) {
                arguments = PFactory.createEmptyTuple(language);
            } else {
                arguments = callNode.execute(frame, func);
            }

            Object state = getStateNode.execute(frame, inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{type, arguments, state});
        }
    }
}
