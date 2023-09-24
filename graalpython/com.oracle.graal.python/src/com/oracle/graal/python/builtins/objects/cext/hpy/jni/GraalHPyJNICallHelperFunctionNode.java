/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.jni;

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCallHelperFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNINodes.HPyJNIFromCharPointerNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.NodeCost;

/**
 * This is the implementation of {@link HPyCallHelperFunctionNode} for the JNI backend. This node
 * calls helper functions also via JNI. The goal of this node is minimal footprint and best
 * uncached/interpreter performance.
 */
final class GraalHPyJNICallHelperFunctionNode extends HPyCallHelperFunctionNode {

    static final GraalHPyJNICallHelperFunctionNode UNCACHED = new GraalHPyJNICallHelperFunctionNode();

    private GraalHPyJNICallHelperFunctionNode() {
    }

    @Override
    protected Object execute(GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args) {
        assert context.getBackend() instanceof GraalHPyJNIContext;
        return switch (name) {
            case GRAAL_HPY_GET_ERRNO -> GraalHPyJNIContext.getErrno();
            case GRAAL_HPY_GET_STRERROR -> GraalHPyJNIContext.getStrerror((int) args[0]);
            case GRAAL_HPY_STRLEN -> HPyJNIFromCharPointerNode.strlen((long) args[0]);
            default -> throw CompilerDirectives.shouldNotReachHere("not yet implemented");
        };
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.MONOMORPHIC;
    }

    @Override
    public boolean isAdoptable() {
        return false;
    }
}
