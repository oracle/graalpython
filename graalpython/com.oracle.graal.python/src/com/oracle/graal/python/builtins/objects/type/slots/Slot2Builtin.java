/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type.slots;

import java.lang.annotation.Annotation;

import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonOS;

/**
 * Adopts the {@link SlotSignature} annotation to {@link Builtin} annotation, so that the builtin
 * functions machinery can be seamlessly reused for slots.
 */
class Slot2Builtin implements Builtin {
    private final BuiltinSlotWrapperSignature signature;
    private final SlotSignature annotation;
    private final String name;

    public Slot2Builtin(SlotSignature annotation, String name, BuiltinSlotWrapperSignature signature) {
        if (signature != null) {
            assert annotation == null : "Slot " + name + " does not support custom signature. The @SlotSignature annotation is not allowed.";
        } else {
            assert annotation != null : "Slot " + name + " must provide custom signature. Add @SlotSignature annotation.";
        }
        this.annotation = annotation;
        this.name = name;
        this.signature = signature;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String doc() {
        return "";
    }

    @Override
    public PythonOS os() {
        return PythonOS.PLATFORM_ANY;
    }

    @Override
    public PythonBuiltinClassType constructsClass() {
        return PythonBuiltinClassType.nil;
    }

    @Override
    public PythonBuiltinClassType[] base() {
        return new PythonBuiltinClassType[0];
    }

    @Override
    public int minNumOfPositionalArgs() {
        return signature != null ? signature.minNumOfPositionalArgs() : annotation.minNumOfPositionalArgs();
    }

    @Override
    public int maxNumOfPositionalArgs() {
        return -1;
    }

    @Override
    public int numOfPositionalOnlyArgs() {
        return -1;
    }

    @Override
    public boolean isGetter() {
        return false;
    }

    @Override
    public boolean isSetter() {
        return false;
    }

    @Override
    public boolean allowsDelete() {
        return false;
    }

    @Override
    public boolean takesVarArgs() {
        return annotation != null && annotation.takesVarArgs();
    }

    @Override
    public boolean varArgsMarker() {
        return false;
    }

    @Override
    public boolean takesVarKeywordArgs() {
        return false;
    }

    @Override
    public String[] parameterNames() {
        return signature != null ? signature.parameterNames() : annotation.parameterNames();
    }

    @Override
    public String[] keywordOnlyNames() {
        return new String[0];
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isClassmethod() {
        return false;
    }

    @Override
    public boolean isStaticmethod() {
        return false;
    }

    @Override
    public boolean needsFrame() {
        return annotation != null && annotation.needsFrame();
    }

    @Override
    public boolean alwaysNeedsCallerFrame() {
        return annotation != null && annotation.alwaysNeedsCallerFrame();
    }

    @Override
    public boolean declaresExplicitSelf() {
        return false;
    }

    @Override
    public boolean reverseOperation() {
        return false;
    }

    @Override
    public String raiseErrorName() {
        return annotation != null ? annotation.raiseErrorName() : "";
    }

    @Override
    public boolean forceSplitDirectCalls() {
        return false;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Builtin.class;
    }

    @Override
    public boolean autoRegister() {
        return false;
    }
}
