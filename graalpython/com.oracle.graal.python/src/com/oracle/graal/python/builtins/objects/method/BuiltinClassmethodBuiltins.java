/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OBJCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OBJCLASS__;
import static com.oracle.graal.python.nodes.StringLiterals.T_QUESTIONMARK;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBuiltinClassMethod)
public final class BuiltinClassmethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinClassmethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NAME__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object name(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getCallable(), T___NAME__);
        }
    }

    @Builtin(name = J___QUALNAME__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object qualname(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getCallable(), T___QUALNAME__);
        }
    }

    @Builtin(name = J___OBJCLASS__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ObjclassNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object objclass(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getCallable(), T___OBJCLASS__);
        }
    }

    @Builtin(name = J___DOC__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DocNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doc(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getCallable(), T___DOC__);
        }
    }

    @Builtin(name = J___TEXT_SIGNATURE__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TextSignatureNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object textSignature(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getCallable(), T___TEXT_SIGNATURE__);
        }
    }

    @Builtin(name = J___REPR__, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectStrAsTruffleStringNode asTruffleStringNode,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached PyObjectLookupAttr lookupName,
                        @Cached PyObjectGetAttr getObjClass,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object mayBeName = lookupName.execute(frame, inliningTarget, self.getCallable(), T___NAME__);
            TruffleString name = mayBeName != PNone.NO_VALUE ? asTruffleStringNode.execute(frame, inliningTarget, mayBeName) : T_QUESTIONMARK;
            TruffleString typeName = getNameNode.execute(inliningTarget, getObjClass.execute(frame, inliningTarget, self.getCallable(), T___OBJCLASS__));
            return simpleTruffleStringFormatNode.format("<method '%s' of '%s' objects>", name, typeName);
        }
    }
}
