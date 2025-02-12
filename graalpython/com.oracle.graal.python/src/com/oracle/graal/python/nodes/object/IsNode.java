/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.expression.BinaryOp;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsAnyBuiltinObjectProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.ComparisonOp;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PythonOptions.class)
@GenerateUncached
@OperationProxy.Proxyable
@SuppressWarnings("truffle-inlining")       // footprint reduction 44 -> 26
public abstract class IsNode extends Node implements BinaryOp {

    protected abstract boolean executeInternal(Object left, Object right);

    protected abstract boolean executeInternal(boolean left, Object right);

    @Override
    public final Object executeObject(VirtualFrame frame, Object left, Object right) {
        return execute(left, right);
    }

    public final boolean execute(Object left, Object right) {
        return left == right || executeInternal(left, right);
    }

    public boolean isTrue(Object object) {
        return executeInternal(true, object);
    }

    public boolean isFalse(Object object) {
        return executeInternal(false, object);
    }

    // Primitives
    @Specialization
    public static boolean doBB(boolean left, boolean right) {
        return left == right;
    }

    @Specialization
    public static boolean doBP(boolean left, PInt right,
                    @Bind("this") Node inliningTarget) {
        Python3Core core = PythonContext.get(inliningTarget);
        if (left) {
            return right == core.getTrue();
        } else {
            return right == core.getFalse();
        }
    }

    @Specialization
    public static boolean doII(int left, int right) {
        return left == right;
    }

    @Specialization
    public static boolean doIL(int left, long right) {
        return left == right;
    }

    @Specialization
    public static boolean doIP(int left, PInt right,
                    @Bind("this") Node inliningTarget,
                    @Shared("isBuiltin") @Cached IsAnyBuiltinObjectProfile isBuiltin) {
        if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, right)) {
            try {
                return right.intValueExact() == left;
            } catch (OverflowException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Specialization
    public static boolean doLI(long left, int right) {
        return left == right;
    }

    @Specialization
    public static boolean doLL(long left, long right) {
        return left == right;
    }

    @Specialization
    public static boolean doLP(long left, PInt right,
                    @Bind("this") Node inliningTarget,
                    @Shared("isBuiltin") @Cached IsAnyBuiltinObjectProfile isBuiltin) {
        if (isBuiltin.profileIsAnyBuiltinObject(inliningTarget, right)) {
            try {
                return left == right.longValueExact();
            } catch (OverflowException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Specialization
    public static boolean doDD(double left, double right) {
        // n.b. we simulate that the primitive NaN is a singleton; this is required to make
        // 'nan = float("nan"); nan is nan' work
        return left == right || (Double.isNaN(left) && Double.isNaN(right));
    }

    @Specialization
    public static boolean doPB(PInt left, boolean right,
                    @Bind("this") Node inliningTarget) {
        return doBP(right, left, inliningTarget);
    }

    @Specialization
    public static boolean doPI(PInt left, int right,
                    @Bind("this") Node inliningTarget,
                    @Shared("isBuiltin") @Cached IsAnyBuiltinObjectProfile isBuiltin) {
        return doIP(right, left, inliningTarget, isBuiltin);
    }

    @Specialization
    public static boolean doPL(PInt left, long right,
                    @Bind("this") Node inliningTarget,
                    @Shared("isBuiltin") @Cached IsAnyBuiltinObjectProfile isBuiltin) {
        return doLP(right, left, inliningTarget, isBuiltin);
    }

    // types
    @Specialization
    public static boolean doCT(PythonBuiltinClass left, PythonBuiltinClassType right) {
        return left.getType() == right;
    }

    @Specialization
    public static boolean doTC(PythonBuiltinClassType left, PythonBuiltinClass right) {
        return right.getType() == left;
    }

    // native objects
    @Specialization
    public static boolean doNative(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                    @Bind("this") Node inliningTarget,
                    @Cached CExtNodes.PointerCompareNode pointerCompareNode) {
        return pointerCompareNode.execute(inliningTarget, ComparisonOp.EQ, left, right);
    }

    // code
    @Specialization
    public static boolean doCode(PCode left, PCode right,
                    @Bind("this") Node inliningTarget,
                    @Cached CodeNodes.GetCodeCallTargetNode getCt) {
        // Special case for code objects: Frames create them on-demand even if they refer to the
        // same function. So we need to compare the root nodes.
        if (left != right) {
            RootCallTarget leftCt = getCt.execute(inliningTarget, left);
            RootCallTarget rightCt = getCt.execute(inliningTarget, right);
            if (leftCt != null && rightCt != null) {
                RootNode leftRootNode = leftCt.getRootNode();
                RootNode rightRootNode = rightCt.getRootNode();
                if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                    if (leftRootNode instanceof PBytecodeDSLGeneratorFunctionRootNode l) {
                        leftRootNode = l.getBytecodeRootNode();
                    }
                    if (rightRootNode instanceof PBytecodeDSLGeneratorFunctionRootNode r) {
                        rightRootNode = r.getBytecodeRootNode();
                    }

                    if (leftRootNode instanceof PBytecodeDSLRootNode l && rightRootNode instanceof PBytecodeDSLRootNode r) {
                        return l.getCodeUnit() == r.getCodeUnit();
                    }
                } else {
                    if (leftRootNode instanceof PBytecodeGeneratorFunctionRootNode l) {
                        leftRootNode = l.getBytecodeRootNode();
                    }
                    if (rightRootNode instanceof PBytecodeGeneratorFunctionRootNode r) {
                        rightRootNode = r.getBytecodeRootNode();
                    }

                    if (leftRootNode instanceof PBytecodeRootNode l && rightRootNode instanceof PBytecodeRootNode r) {
                        return l.getCodeUnit() == r.getCodeUnit();
                    }
                }
                return leftRootNode == rightRootNode;
            } else {
                return false;
            }
        }
        return true;
    }

    // none
    @Specialization
    public static boolean doObjectPNone(Object left, PNone right,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached IsForeignObjectNode isForeignObjectNode,
                    @Shared @CachedLibrary(limit = "3") InteropLibrary lib) {
        if (left == right) {
            return true;
        }
        if (isForeignObjectNode.execute(inliningTarget, left)) {
            return lib.isNull(left);
        }
        return false;
    }

    @Specialization
    public static boolean doPNoneObject(PNone left, Object right,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached IsForeignObjectNode isForeignObjectNode,
                    @Shared @CachedLibrary(limit = "3") InteropLibrary lib) {
        return doObjectPNone(right, left, inliningTarget, isForeignObjectNode, lib);
    }

    // pstring (may be interned)
    @Specialization
    public static boolean doPString(PString left, PString right,
                    @Bind("this") Node inliningTarget,
                    @Cached StringNodes.StringMaterializeNode materializeNode,
                    @Cached StringNodes.IsInternedStringNode isInternedStringNode,
                    @Cached TruffleString.EqualNode equalNode) {
        if (isInternedStringNode.execute(inliningTarget, left) && isInternedStringNode.execute(inliningTarget, right)) {
            return equalNode.execute(materializeNode.execute(inliningTarget, left), materializeNode.execute(inliningTarget, right), TS_ENCODING);
        }
        return left == right;
    }

    // everything else
    @Fallback
    public static boolean doOther(Object left, Object right,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached IsForeignObjectNode isForeignObjectNode,
                    @Shared @CachedLibrary(limit = "3") InteropLibrary lib) {
        if (left == right) {
            return true;
        }
        if (isForeignObjectNode.execute(inliningTarget, left)) {
            return lib.isIdentical(left, right, lib);
        }
        return false;
    }

    @NeverDefault
    public static IsNode create() {
        return IsNodeGen.create();
    }

    public static IsNode getUncached() {
        return IsNodeGen.getUncached();
    }
}
