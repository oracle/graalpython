/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyObjectGetItem.PyObjectGetItemOrNull;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy.Proxyable;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false)       // footprint reduction 48 -> 30
@Proxyable(storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class)
@ImportStatic(PGuards.class)
public abstract class ReadGlobalOrBuiltinNode extends Node {
    public abstract Object execute(VirtualFrame frame, TruffleString name);

    public Object read(Frame frame, Object globals, TruffleString name) {
        CompilerAsserts.partialEvaluationConstant(name);
        // reloading globals is not efficient, but this entry point is here just because it is used
        // from the manual interpreter and only for the time being until the manual interpreter is
        // removed
        assert PArguments.getGlobals(frame) == globals;
        return execute((VirtualFrame) frame, name);
    }

    @NeverDefault
    public static ReadGlobalOrBuiltinNode create() {
        return ReadGlobalOrBuiltinNodeGen.create();
    }

    public static ReadGlobalOrBuiltinNode getUncached() {
        return ReadGlobalOrBuiltinNodeGen.getUncached();
    }

    public static Shape getGlobalsStorageShape(VirtualFrame frame) {
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom) {
            return dom.getStore().getShape();
        }
        return null;
    }

    public static Shape getGlobalsStorageShapeIfPropMissing(VirtualFrame frame, TruffleString name) {
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom) {
            Shape shape = dom.getStore().getShape();
            if (!shape.hasProperty(name)) {
                return shape;
            }
        }
        return null;
    }

    @ForceQuickening
    @Specialization(guards = {"cachedGlobalsShape != null", "cachedGlobalsShape == getGlobalsStorageShape(frame)"}, //
                    excludeForUncached = true, limit = "1")
    public static Object readBuiltinFastPath(VirtualFrame frame, TruffleString attributeId,
                    @Cached("getGlobalsStorageShapeIfPropMissing(frame, attributeId)") Shape cachedGlobalsShape,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode) {
        return readFromBuiltinsNode.execute(attributeId);
    }

    public static Object readFastFromGlobalStore(VirtualFrame frame, TruffleString name, ReadAttributeFromPythonObjectNode readNode) {
        Object obj = PArguments.getGlobals(frame);
        if (obj instanceof PDict dict && dict.getDictStorage() instanceof DynamicObjectStorage dom) {
            return readNode.execute(dom.getStore(), name, PNone.NO_VALUE);
        }
        return PNone.NO_VALUE;
    }

    @ForceQuickening
    @Specialization(guards = "!isNoValue(result)", replaces = "readBuiltinFastPath", excludeForUncached = true, limit = "1")
    public static Object readGlobalFastPath(VirtualFrame frame, TruffleString attributeId,
                    @Cached ReadAttributeFromPythonObjectNode readNode,
                    @Bind("readFastFromGlobalStore(frame, attributeId, readNode)") Object result) {
        return result;
    }

    @StoreBytecodeIndex
    @Specialization(replaces = {"readBuiltinFastPath", "readGlobalFastPath"})
    public static Object readGlobalOrBuiltinGeneric(VirtualFrame frame, TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Shared("readFromBuiltinsNode") @Cached ReadBuiltinNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedBranchProfile wasReadFromModule,
                    @Cached PyObjectGetItemOrNull getItemNode) {
        PythonObject globalsObj = PArguments.getGlobals(frame);
        if (!(globalsObj instanceof PDict globals)) {
            throw raiseSystemError(inliningTarget);
        }
        Object result = getItemNode.execute(frame, inliningTarget, globals, attributeId);
        if (result != null) {
            wasReadFromModule.enter(inliningTarget);
            return result;
        } else {
            return readFromBuiltinsNode.execute(attributeId);
        }
    }

    @InliningCutoff
    private static PException raiseSystemError(Node inliningTarget) {
        CompilerDirectives.transferToInterpreter();
        throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
    }
}
