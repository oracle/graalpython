/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectConstArray;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectReturn;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethFastcallRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethFastcallWithKeywordsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethKeywordsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethMethodRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethNoargsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethORoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethVarargsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nfi2.Nfi;
import com.oracle.graal.python.nfi2.NfiDowncallSignature;
import com.oracle.graal.python.nfi2.NfiType;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

public enum MethodDescriptorWrapper implements NativeCExtSymbol {
    // METH_FASTCALL
    FASTCALL(PyObjectReturn, PyObject, PyObjectConstArray, Py_ssize_t),

    // METH_FASTCALL | METH_KEYWORDS
    FASTCALL_WITH_KEYWORDS(PyObjectReturn, PyObject, PyObjectConstArray, Py_ssize_t, PyObject),

    // METH_VARARGS | METH_KEYWORDS
    KEYWORDS(PyObjectReturn, PyObject, PyObject, PyObject),

    // METH_VARARGS
    VARARGS(PyObjectReturn, PyObject, PyObject),

    // METH_NOARGS
    NOARGS(PyObjectReturn, PyObject, PyObject),

    // METH_O
    O(PyObjectReturn, PyObject, PyObject),

    // METH_FASTCALL | METH_KEYWORDS | METH_METHOD:
    METHOD(PyObjectReturn, PyObject, PyTypeObject, PyObjectConstArray, Py_ssize_t, PyObject);

    public final ArgDescriptor returnValue;
    public final ArgDescriptor[] arguments;
    public final NfiDowncallSignature signature;

    MethodDescriptorWrapper(ArgDescriptor returnValue, ArgDescriptor... arguments) {
        this.returnValue = returnValue;
        this.arguments = arguments;
        NfiType[] nfiTypes = new NfiType[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            nfiTypes[i] = arguments[i].getNFI2Type();
        }
        this.signature = Nfi.createDowncallSignature(returnValue.getNFI2Type(), nfiTypes);
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public TruffleString getTsName() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public NfiDowncallSignature getSignature() {
        return signature;
    }

    private static final TruffleLogger LOGGER = CApiContext.getLogger(MethodDescriptorWrapper.class);

    @TruffleBoundary
    static RootCallTarget getOrCreateCallTarget(PythonLanguage language, MethodDescriptorWrapper sig, TruffleString name, boolean isStatic) {
        Class<? extends PRootNode> nodeKlass;
        Function<PythonLanguage, RootNode> rootNodeFunction = switch (sig) {
            case KEYWORDS -> {
                nodeKlass = MethKeywordsRoot.class;
                yield l -> new MethKeywordsRoot(l, name, isStatic, sig);
            }
            case VARARGS -> {
                nodeKlass = MethVarargsRoot.class;
                yield (l -> new MethVarargsRoot(l, name, isStatic, sig));
            }
            case NOARGS -> {
                nodeKlass = MethNoargsRoot.class;
                yield (l -> new MethNoargsRoot(l, name, isStatic, sig));
            }
            case O -> {
                nodeKlass = MethORoot.class;
                yield (l -> new MethORoot(l, name, isStatic, sig));
            }
            case FASTCALL -> {
                nodeKlass = MethFastcallRoot.class;
                yield (l -> new MethFastcallRoot(l, name, isStatic, sig));
            }
            case FASTCALL_WITH_KEYWORDS -> {
                nodeKlass = MethFastcallWithKeywordsRoot.class;
                yield (l -> new MethFastcallWithKeywordsRoot(l, name, isStatic, sig));
            }
            case METHOD -> {
                nodeKlass = MethMethodRoot.class;
                yield (l -> new MethMethodRoot(l, name, isStatic, sig));
            }
        };
        return language.createCachedExternalFunWrapperCallTarget(rootNodeFunction, nodeKlass, sig, name, true, isStatic);
    }

    /**
     * Similar to {@code PyDescr_NewMethod}, creates a built-in function for a specific signature.
     * This built-in function also does appropriate argument and result conversion and calls the
     * provided callable.
     *
     * @param language The Python language object.
     * @param name The name of the method.
     * @param callable A reference denoting executable code. Currently, there are two
     *            representations for that: a native function pointer or a {@link RootCallTarget}
     * @param enclosingType The type the function belongs to (needed for checking of {@code self}).
     * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot wrapper.
     */
    @TruffleBoundary
    public static PBuiltinFunction createWrapperFunction(PythonLanguage language, TruffleString name, long callable, Object enclosingType, int flags) {
        LOGGER.finer(() -> PythonUtils.formatJString("MethodDescriptorWrapper.createWrapperFunction(%s, %s)", name, callable));
        MethodDescriptorWrapper methodDescriptorWrapper = fromMethodFlags(flags);
        if (methodDescriptorWrapper == null) {
            return null;
        }

        RootCallTarget callTarget = getOrCreateCallTarget(language, methodDescriptorWrapper, name, CExtContext.isMethStatic(flags));

        NfiType[] nfiTypes = new NfiType[methodDescriptorWrapper.arguments.length];
        for (int i = 0; i < nfiTypes.length; i++) {
            nfiTypes[i] = methodDescriptorWrapper.arguments[i].getNFI2Type();
        }
        PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(CExtCommonNodes.ensureExecutable(callable, methodDescriptorWrapper));

        // generate default values for positional args (if necessary)
        Object[] defaults = PBuiltinFunction.generateDefaults(0);

        Object type = enclosingType == PNone.NO_VALUE ? null : enclosingType;
        return PFactory.createBuiltinFunction(language, name, type, defaults, kwDefaults, flags, callTarget);
    }

    /**
     * See {@code PyDescr_NewMethod}
     *
     * @param flags The method flags {@link CExtContext#METH_VARARGS} and others.
     * @return
     */
    static MethodDescriptorWrapper fromMethodFlags(int flags) {
        if (CExtContext.isMethVarargs(flags)) {
            return VARARGS;
        }
        if (CExtContext.isMethVarargsWithKeywords(flags)) {
            return KEYWORDS;
        }
        if (CExtContext.isMethFastcall(flags)) {
            return FASTCALL;
        }
        if (CExtContext.isMethFastcallWithKeywords(flags)) {
            return FASTCALL_WITH_KEYWORDS;
        }
        if (CExtContext.isMethNoArgs(flags)) {
            return NOARGS;
        }
        if (CExtContext.isMethO(flags)) {
            return O;
        }
        if (CExtContext.isMethMethod(flags)) {
            return METHOD;
        }
        throw CompilerDirectives.shouldNotReachHere("illegal method flags");
    }
}
