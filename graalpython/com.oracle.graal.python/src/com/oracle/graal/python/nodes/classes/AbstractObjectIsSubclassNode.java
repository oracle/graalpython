/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.NeverDefault;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

@GenerateUncached
@ImportStatic(PythonOptions.class)
@NodeInfo(shortName = "cpython://Objects/abstract.c/abstract_issubclass")
public abstract class AbstractObjectIsSubclassNode extends PNodeWithContext {
    static final int MAX_RECURSION = 3; // Don't use PythonOptions to avoid language reference

    @NeverDefault
    public static AbstractObjectIsSubclassNode create() {
        return AbstractObjectIsSubclassNodeGen.create();
    }

    protected abstract boolean executeInternal(Frame frame, Object derived, Object cls, int depth);

    public final boolean execute(VirtualFrame frame, Object derived, Object cls) {
        return executeInternal(frame, derived, cls, 0);
    }

    public final boolean execute(Object derived, Object cls) {
        return execute(null, derived, cls);
    }

    @Specialization(guards = "isSameMetaObject(isSameTypeNode, derived, cls)")
    @SuppressWarnings("unused")
    static boolean doSameClass(Object derived, Object cls, @SuppressWarnings("unused") int depth,
                    @Shared("isSameType") @Cached IsSameTypeNode isSameTypeNode) {
        return true;
    }

    protected static int[] observedSize() {
        int[] ary = new int[1];
        ary[0] = 0;
        return ary;
    }

    @Specialization(guards = {"depth < MAX_RECURSION", "!isSameMetaObject(isSameTypeNode, derived, cls)", "derived == cachedDerived", "cls == cachedCls"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static boolean doSubclass(VirtualFrame frame, @SuppressWarnings("unused") Object derived, @SuppressWarnings("unused") Object cls, int depth,
                    @Bind("this") Node inliningTarget,
                    @Cached(value = "observedSize()", dimensions = 1) int[] observedSizeArray,
                    @Cached("derived") Object cachedDerived,
                    @Cached("cls") Object cachedCls,
                    @Cached @SuppressWarnings("unused") IsSameTypeNode isSameTypeNode,
                    @Cached AbstractObjectGetBasesNode getBasesNode,
                    @Cached AbstractObjectIsSubclassNode isSubclassNode,
                    @Cached GetObjectArrayNode getObjectArrayNode) {
        CompilerAsserts.partialEvaluationConstant(depth);
        PTuple bases = getBasesNode.execute(frame, cachedDerived);
        if (bases == null || isEmpty(bases)) {
            return false;
        }
        Object[] basesAry = getObjectArrayNode.execute(inliningTarget, bases);
        if (observedSizeArray[0] == 0) {
            // first time, the array isn't length 0! (guard above)
            CompilerDirectives.transferToInterpreterAndInvalidate();
            observedSizeArray[0] = basesAry.length;
        }
        if (observedSizeArray[0] > 0 && observedSizeArray[0] < (32 >> depth) && observedSizeArray[0] == basesAry.length) {
            // we observe a short constant size
            return loopBases(frame, cachedCls, basesAry, isSubclassNode, depth);
        } else if (observedSizeArray[0] > 0) {
            // the observed size is too large or not constant, disable explosion
            CompilerDirectives.transferToInterpreterAndInvalidate();
            observedSizeArray[0] = -1;
        }
        return loopUnexploded(frame, cachedCls, isSubclassNode, basesAry, depth);
    }

    private static boolean loopUnexploded(VirtualFrame frame, Object cachedCls, AbstractObjectIsSubclassNode isSubclassNode, Object[] basesAry, int depth) {
        for (Object baseCls : basesAry) {
            if (isSubclassNode.executeInternal(frame, baseCls, cachedCls, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    @ExplodeLoop
    private static boolean loopBases(VirtualFrame frame, Object cachedCls, Object[] bases, AbstractObjectIsSubclassNode isSubclassNode, int depth) {
        for (int i = 0; i < bases.length; i++) {
            if (isSubclassNode.executeInternal(frame, bases[i], cachedCls, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(replaces = {"doSubclass", "doSameClass"})
    static boolean doGeneric(VirtualFrame frame, Object derived, Object cls, int depth,
                    @Bind("this") Node inliningTarget,
                    @Cached AbstractObjectGetBasesNode getBasesNode,
                    @Cached("createRecursive(depth)") AbstractObjectIsSubclassNode isSubclassNode,
                    @Shared("isSameType") @Cached IsSameTypeNode isSameTypeNode,
                    @Cached GetObjectArrayNode getObjectArrayNode) {
        CompilerAsserts.partialEvaluationConstant(depth);
        if (isSameMetaObject(isSameTypeNode, derived, cls)) {
            return true;
        }

        PTuple bases = getBasesNode.execute(frame, derived);
        if (bases == null || isEmpty(bases)) {
            return false;
        }

        for (Object baseCls : getObjectArrayNode.execute(inliningTarget, bases)) {
            if (isSubclassNode.executeInternal(frame, baseCls, cls, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    protected AbstractObjectIsSubclassNode createRecursive(int depth) {
        if (depth >= MAX_RECURSION) {
            return AbstractObjectIsSubclassNodeGen.getUncached();
        }
        return AbstractObjectIsSubclassNodeGen.create();
    }

    private static boolean isEmpty(PTuple bases) {
        return bases.getSequenceStorage().length() == 0;
    }

    /**
     * Tests if the two meta objects {@code derived} and {@code cls} are the same. This differs from
     * {@link IsSameTypeNode} because it will also accept meta objects that are not classes.
     */
    @SuppressWarnings("javadoc")
    static boolean isSameMetaObject(IsSameTypeNode isSameTypeNode, Object derived, Object cls) {
        return derived == cls || isSameTypeNode.execute(derived, cls);
    }
}
