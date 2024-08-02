/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.expression;

import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@GenerateInline(false)
@OperationProxy.Proxyable
public abstract class ContainsNode extends BinaryOpNode {
    @NeverDefault
    public static ContainsNode create() {
        return ContainsNodeGen.create();
    }

    @NeverDefault
    public static LookupAndCallBinaryNode createCallNode() {
        return LookupAndCallBinaryNode.create(SpecialMethodSlot.Contains);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    public static boolean doBoolean(VirtualFrame frame, boolean item, Object iter,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached CoerceToBooleanNode.YesNode castBool,
                    @Shared("callNode") @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                    @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                    @Shared("nextNode") @Cached GetNextNode nextNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            Object iterator = getIter.execute(frame, inliningTarget, iter);
            return sequenceContains(frame, iterator, item, eqNode, nextNode, inliningTarget, errorProfile);
        }
        return castBool.executeBoolean(frame, inliningTarget, result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    public static boolean doInt(VirtualFrame frame, int item, Object iter,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached CoerceToBooleanNode.YesNode castBool,
                    @Shared("callNode") @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                    @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                    @Shared("nextNode") @Cached GetNextNode nextNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(frame, getIter.execute(frame, inliningTarget, iter), item, eqNode, nextNode, inliningTarget, errorProfile);
        }
        return castBool.executeBoolean(frame, inliningTarget, result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    public static boolean doLong(VirtualFrame frame, long item, Object iter,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached CoerceToBooleanNode.YesNode castBool,
                    @Shared("callNode") @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                    @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                    @Shared("nextNode") @Cached GetNextNode nextNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(frame, getIter.execute(frame, inliningTarget, iter), item, eqNode, nextNode, inliningTarget, errorProfile);
        }
        return castBool.executeBoolean(frame, inliningTarget, result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    public static boolean doDouble(VirtualFrame frame, double item, Object iter,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached CoerceToBooleanNode.YesNode castBool,
                    @Shared("callNode") @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                    @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                    @Shared("nextNode") @Cached GetNextNode nextNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(frame, getIter.execute(frame, inliningTarget, iter), item, eqNode, nextNode, inliningTarget, errorProfile);
        }
        return castBool.executeBoolean(frame, inliningTarget, result);
    }

    @Specialization
    public static boolean doGeneric(VirtualFrame frame, Object item, Object iter,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached CoerceToBooleanNode.YesNode castBool,
                    @Shared("callNode") @Cached("createCallNode()") LookupAndCallBinaryNode callNode,
                    @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                    @Shared("nextNode") @Cached GetNextNode nextNode) {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContainsObject(frame, getIter.execute(frame, inliningTarget, iter), item, eqNode, nextNode, inliningTarget, errorProfile);
        }
        return castBool.executeBoolean(frame, inliningTarget, result);
    }

    private static void handleUnexpectedResult(VirtualFrame frame, Object iterator, Object item, UnexpectedResultException e, PyObjectRichCompareBool.EqNode eqNode, GetNextNode nextNode,
                    Node inliningTarget, IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
        // If we got an unexpected (non-primitive) result from the iterator, we need to compare it
        // and continue iterating with "next" through the generic case. However, we also want the
        // specialization to go away, so we wrap the boolean result in a new
        // UnexpectedResultException. This will cause the DSL to disable the specialization with the
        // primitive value and return the result we got, without iterating again.
        Object result = e.getResult();
        if (eqNode.compare(frame, inliningTarget, result, item)) {
            result = true;
        } else {
            result = sequenceContainsObject(frame, iterator, item, eqNode, nextNode, inliningTarget, errorProfile);
        }
        throw new UnexpectedResultException(result);
    }

    private static boolean sequenceContains(VirtualFrame frame, Object iterator, boolean item, PyObjectRichCompareBool.EqNode eqNode, GetNextNode nextNode,
                    Node inliningTarget, IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
        while (true) {
            try {
                if (nextNode.executeBoolean(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode, nextNode, inliningTarget, errorProfile);
            }
        }
    }

    private static boolean sequenceContains(VirtualFrame frame, Object iterator, int item, PyObjectRichCompareBool.EqNode eqNode, GetNextNode nextNode,
                    Node inliningTarget, IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
        while (true) {
            try {
                if (nextNode.executeInt(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode, nextNode, inliningTarget, errorProfile);
            }
        }
    }

    private static boolean sequenceContains(VirtualFrame frame, Object iterator, long item, PyObjectRichCompareBool.EqNode eqNode, GetNextNode nextNode,
                    Node inliningTarget, IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
        while (true) {
            try {
                if (nextNode.executeLong(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode, nextNode, inliningTarget, errorProfile);
            }
        }
    }

    private static boolean sequenceContains(VirtualFrame frame, Object iterator, double item, PyObjectRichCompareBool.EqNode eqNode, GetNextNode nextNode,
                    Node inliningTarget, IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
        while (true) {
            try {
                if (nextNode.executeDouble(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode, nextNode, inliningTarget, errorProfile);
            }
        }
    }

    private static boolean sequenceContainsObject(VirtualFrame frame, Object iterator, Object item, PyObjectRichCompareBool.EqNode eqNode, GetNextNode nextNode,
                    Node inliningTarget, IsBuiltinObjectProfile errorProfile) {
        while (true) {
            try {
                if (eqNode.compare(frame, inliningTarget, nextNode.execute(frame, iterator), item)) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                return false;
            }
        }
    }
}
