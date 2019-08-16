/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

@TypeSystemReference(PythonTypes.class)
@ImportStatic(PythonOptions.class)
@ReportPolymorphism
abstract class CallSpecialMethodNode extends Node {

    /** for interpreter performance: cache if we exceeded the max caller size */
    private boolean maxSizeExceeded = false;

    /**
     * Returns a new instanceof the builtin if it's a subclass of the given class, and null
     * otherwise.
     */
    private <T extends PythonBuiltinBaseNode> T getBuiltin(PBuiltinFunction func, Class<T> clazz) {
        CompilerAsserts.neverPartOfCompilation();
        NodeFactory<? extends PythonBuiltinBaseNode> builtinNodeFactory = func.getBuiltinNodeFactory();
        if (builtinNodeFactory != null && !callerExceedsMaxSize()) {
            return clazz.isAssignableFrom(builtinNodeFactory.getNodeClass()) ? clazz.cast(func.getBuiltinNodeFactory().createNode()) : null;
        } else {
            return null;
        }
    }

    private boolean callerExceedsMaxSize() {
        CompilerAsserts.neverPartOfCompilation();
        if (!maxSizeExceeded) {
            int n = NodeUtil.countNodes(getRootNode());
            // nb: option 'BuiltinsInliningMaxCallerSize' is defined as a compatible option, i.e.,
            // ASTs
            // will only we shared between contexts that have the same value for this option.
            int maxSize = PythonOptions.getOption(lookupContextReference(PythonLanguage.class).get(), PythonOptions.BuiltinsInliningMaxCallerSize);
            if (n >= maxSize) {
                maxSizeExceeded = true;
                return true;
            }
            return false;
        }
        return true;
    }

    protected Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    PythonUnaryBuiltinNode getUnary(Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin((PBuiltinFunction) func, PythonUnaryBuiltinNode.class);
        }
        return null;
    }

    PythonBinaryBuiltinNode getBinary(Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin((PBuiltinFunction) func, PythonBinaryBuiltinNode.class);
        }
        return null;
    }

    PythonTernaryBuiltinNode getTernary(Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin((PBuiltinFunction) func, PythonTernaryBuiltinNode.class);
        }
        return null;
    }

    PythonQuaternaryBuiltinNode getQuaternary(Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin((PBuiltinFunction) func, PythonQuaternaryBuiltinNode.class);
        }
        return null;
    }

    PythonVarargsBuiltinNode getVarargs(Object func) {
        if (func instanceof PBuiltinFunction) {
            return getBuiltin((PBuiltinFunction) func, PythonVarargsBuiltinNode.class);
        }
        return null;
    }

    protected static boolean takesSelfArg(Object func) {
        if (func instanceof PBuiltinFunction) {
            RootNode functionRootNode = ((PBuiltinFunction) func).getFunctionRootNode();
            if (functionRootNode instanceof BuiltinFunctionRootNode) {
                return ((BuiltinFunctionRootNode) functionRootNode).declaresExplicitSelf();
            }
        } else if (func instanceof PBuiltinMethod) {
            return takesSelfArg(((PBuiltinMethod) func).getFunction());
        }
        return true;
    }

    protected static RootCallTarget getCallTarget(PMethod meth) {
        return getCallTargetOfFunction(meth.getFunction());
    }

    protected static RootCallTarget getCallTarget(PBuiltinMethod meth) {
        return getCallTargetOfFunction(meth.getFunction());
    }

    private static RootCallTarget getCallTargetOfFunction(Object func) {
        if (func instanceof PFunction) {
            return ((PFunction) func).getCallTarget();
        } else if (func instanceof PBuiltinFunction) {
            return ((PBuiltinFunction) func).getCallTarget();
        }
        return null;
    }
}
