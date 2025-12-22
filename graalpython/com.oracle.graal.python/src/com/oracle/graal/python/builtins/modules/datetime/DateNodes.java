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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MAX_YEAR;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MIN_YEAR;

import java.time.YearMonth;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public class DateNodes {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewNode extends Node {

        public abstract Object execute(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject);

        @Specialization
        static Object newDate(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject) {
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                return newDateBoundary(inliningTarget, cls, yearObject, monthObject, dayObject);
            } finally {
                // Some uncached nodes (e.g. PyLongAsLongNode) may raise exceptions
                // that are not connected to a current node. Set the current node
                // manually.
                encapsulating.set(encapsulatingNode);
            }
        }

        @TruffleBoundary
        private static Object newDateBoundary(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject) {
            int year = PyLongAsIntNode.executeUncached(yearObject);
            int month = PyLongAsIntNode.executeUncached(monthObject);
            int day = PyLongAsIntNode.executeUncached(dayObject);

            if (year < MIN_YEAR || year > MAX_YEAR) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.YEAR_D_IS_OUT_OF_RANGE, year);
            }

            if (month <= 0 || month >= 13) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.MONTH_MUST_BE_IN);
            }

            if (day <= 0 || day > getMaxDayOfMonth(year, month)) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.DAY_IS_OUT_OF_RANGE_FOR_MONTH);
            }

            return NewUnsafeNode.executeUncached(cls, year, month, day);
        }

        @TruffleBoundary
        private static int getMaxDayOfMonth(int year, int month) {
            var date = YearMonth.of(year, month).atEndOfMonth();
            return date.getDayOfMonth();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewUnsafeNode extends Node {

        public abstract Object execute(Node inliningTarget, Object cls, int year, int month, int day);

        public static Object executeUncached(Object cls, int year, int month, int day) {
            return DateNodesFactory.NewUnsafeNodeGen.getUncached().execute(null, cls, year, month, day);
        }

        @Specialization
        static Object newDate(Node inliningTarget, Object cls, int year, int month, int day,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Cached ExternalFunctionNodes.DefaultCheckFunctionResultNode checkFunctionResultNode,
                        @Cached CApiTransitions.PythonToNativeNode toNativeNode,
                        @Cached CApiTransitions.NativeToPythonTransferNode fromNativeNode) {
            if (!needsNativeAllocationNode.execute(inliningTarget, cls)) {
                Shape shape = getInstanceShape.execute(cls);
                return new PDate(cls, shape, year, month, day);
            } else {
                Object nativeResult = callCapiFunction.call(NativeCAPISymbol.FUN_DATE_SUBTYPE_NEW, toNativeNode.execute(cls), year, month, day);
                checkFunctionResultNode.execute(PythonContext.get(inliningTarget), NativeCAPISymbol.FUN_DATE_SUBTYPE_NEW.getTsName(), nativeResult);
                return fromNativeNode.execute(nativeResult);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SubclassNewNode extends Node {

        public abstract Object execute(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject);

        public static SubclassNewNode getUncached() {
            return DateNodesFactory.SubclassNewNodeGen.getUncached();
        }

        @Specialization(guards = {"isBuiltinClass(cls)"})
        static Object newDateBuiltin(Node inliningTarget, Object cls, Object yearObject, Object monthObject, Object dayObject,
                        @Cached NewNode newNode) {
            return newNode.execute(inliningTarget, cls, yearObject, monthObject, dayObject);
        }

        @Fallback
        @TruffleBoundary
        static Object newDateGeneric(Object cls, Object yearObject, Object monthObject, Object dayObject) {
            return CallNode.executeUncached(cls, yearObject, monthObject, dayObject);
        }

        static boolean isBuiltinClass(Object cls) {
            return PGuards.isBuiltinClass(cls, PythonBuiltinClassType.PDate);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AsManagedDateNode extends Node {
        public abstract PDate execute(Node inliningTarget, Object obj);

        public static PDate executeUncached(Object obj) {
            return DateNodesFactory.AsManagedDateNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static PDate asManaged(PDate obj) {
            return obj;
        }

        @Specialization
        static PDate asManagedNative(PythonAbstractNativeObject obj,
                        @Bind PythonLanguage language,
                        @Cached CStructAccess.ReadByteNode readByteNode) {
            int year = getYear(obj, readByteNode);
            int month = getMonth(obj, readByteNode);
            int day = getDay(obj, readByteNode);
            PythonBuiltinClassType cls = PythonBuiltinClassType.PDate;
            return new PDate(cls, cls.getInstanceShape(language), year, month, day);
        }

        static int getYear(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            int b0 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_Date__data, 0);
            int b1 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_Date__data, 1);
            return b0 << 8 | b1;
        }

        static int getMonth(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_Date__data, 2);
        }

        static int getDay(PythonAbstractNativeObject self, CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_Date__data, 3);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class DateCheckNode extends Node {
        public abstract boolean execute(Node inliningTarget, Object obj);

        public static boolean executeUncached(Object obj) {
            return DateNodesFactory.DateCheckNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static boolean doManaged(@SuppressWarnings("unused") PDate value) {
            return true;
        }

        @Specialization
        static boolean doNative(Node inliningTarget, PythonAbstractNativeObject value,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile profile) {
            return profile.profileObject(inliningTarget, value, PythonBuiltinClassType.PDate);
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }
}
