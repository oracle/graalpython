/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class IteratorNodes {

    /**
     * Implements the logic from {@code PyObject_LengthHint}. This returns a non-negative value from
     * o.__len__() or o.__length_hint__(). If those methods aren't found the defaultvalue is
     * returned. If either has no viable or defaultvalue implementation then this node returns -1.
     */
    @GenerateNodeFactory
    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class GetLength extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, Object iterable);

        @Specialization(guards = {"isString(iterable)"}, limit = "2")
        int length(@SuppressWarnings({"unused"}) VirtualFrame frame, Object iterable,
                        @CachedLibrary("iterable") PythonObjectLibrary plib) {
            return plib.length(iterable);
        }

        @Specialization(guards = {"isNoValue(iterable)"})
        static int length(@SuppressWarnings({"unused"}) VirtualFrame frame, @SuppressWarnings({"unused"}) Object iterable) {
            return -1;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"}, limit = "4")
        int length(VirtualFrame frame, Object iterable,
                        @CachedLibrary("iterable") PythonObjectLibrary plib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary toInt,
                        @Cached("create(__LEN__)") LookupAttributeInMRONode lenNode,
                        @Cached("create(__LENGTH_HINT__)") LookupAttributeInMRONode lenHintNode,
                        @Cached CallUnaryMethodNode dispatchGetattribute,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached ConditionProfile hasLenProfile,
                        @Cached ConditionProfile hasLenghtHintProfile,
                        @Cached PRaiseNode raiseNode) {
            Object clazz = plib.getLazyPythonClass(iterable);
            Object attrLenObj = lenNode.execute(clazz);
            if (hasLenProfile.profile(attrLenObj != PNone.NO_VALUE)) {
                Object len = null;
                try {
                    len = dispatchGetattribute.executeObject(frame, attrLenObj, iterable);
                } catch (PException e) {
                    e.expect(TypeError, errorProfile);
                }
                if (len != null && len != PNotImplemented.NOT_IMPLEMENTED) {
                    if (toInt.canBeIndex(len)) {
                        int intLen = toInt.asSize(len);
                        if (intLen < 0) {
                            throw raiseNode.raise(TypeError, ErrorMessages.LEN_SHOULD_RETURN_MT_ZERO);
                        }
                        return intLen;
                    } else {
                        throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_INTEGER, __LEN__, len);
                    }
                }
            }
            Object attrLenHintObj = lenHintNode.execute(clazz);
            if (hasLenghtHintProfile.profile(attrLenHintObj != PNone.NO_VALUE)) {
                Object len = null;
                try {
                    len = dispatchGetattribute.executeObject(frame, attrLenHintObj, iterable);
                } catch (PException e) {
                    e.expect(TypeError, errorProfile);
                }
                if (len != null && len != PNotImplemented.NOT_IMPLEMENTED) {
                    if (toInt.canBeIndex(len)) {
                        int intLen = toInt.asSize(len);
                        if (intLen < 0) {
                            throw raiseNode.raise(TypeError, ErrorMessages.LENGTH_HINT_SHOULD_RETURN_MT_ZERO);
                        }
                        return intLen;
                    } else {
                        throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_INTEGER, __LENGTH_HINT__, len);
                    }
                }
            }
            return -1;
        }
    }

    @GenerateUncached
    public abstract static class IsIteratorObjectNode extends Node {

        public abstract boolean execute(Object o);

        @Specialization
        static boolean doPIterator(@SuppressWarnings("unused") PBuiltinIterator it) {
            // a PIterator object is guaranteed to be an iterator object
            return true;
        }

        @Specialization
        static boolean doGeneric(Object it,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupAttributeNode) {
            return lookupAttributeNode.execute(it, SpecialMethodNames.__NEXT__) != PNone.NO_VALUE;
        }
    }
}
