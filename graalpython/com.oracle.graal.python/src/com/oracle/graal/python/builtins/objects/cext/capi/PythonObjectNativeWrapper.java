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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
 * correct shape of the corresponding native type {@code struct _object}.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonObjectNativeWrapper extends PythonAbstractObjectNativeWrapper {

    public PythonObjectNativeWrapper(PythonAbstractObject object) {
        super(object);
    }

    public static PythonAbstractObjectNativeWrapper wrap(PythonAbstractObject obj, Node inliningTarget, InlinedConditionProfile noWrapperProfile) {
        // important: native wrappers are cached
        PythonAbstractObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
        if (noWrapperProfile.profile(inliningTarget, nativeWrapper == null)) {
            nativeWrapper = new PythonObjectNativeWrapper(obj);
            obj.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonUtils.formatJString("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
    }

    @ExportMessage
    boolean isNull() {
        return getDelegate() == PNone.NO_VALUE;
    }

    @ExportMessage
    boolean isPointer() {
        return getDelegate() == PNone.NO_VALUE || isNative();
    }

    @ExportMessage
    long asPointer() {
        return getDelegate() == PNone.NO_VALUE ? 0L : getNativePointer();
    }

    @ExportMessage
    void toNative(
                    @Bind("$node") Node inliningTarget,
                    @Cached CApiTransitions.FirstToNativeNode firstToNativeNode) {
        if (getDelegate() != PNone.NO_VALUE && !isNative()) {
            /*
             * If the wrapped object is a special singleton (e.g. None, True, False, ...) then it
             * should be immortal.
             */
            boolean immortal = CApiGuards.isSpecialSingleton(getDelegate());
            assert !immortal || (getDelegate() instanceof PythonAbstractObject po && PythonContext.get(inliningTarget).getSingletonNativeWrapper(po) == this);
            setNativePointer(firstToNativeNode.execute(inliningTarget, this, immortal));
        }
    }
}
