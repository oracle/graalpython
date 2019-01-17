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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.util.ArrayList;
import java.util.List;

public abstract class PNodeWithContext extends Node {
    @Child private PythonObjectFactory factory;
    @Child private WriteAttributeToObjectNode writeCause;
    @Child private LookupAttributeInMRONode getNewFuncNode;
    @Child private CallVarargsMethodNode callNode;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    protected final PythonObjectFactory factory() {
        if (factory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            factory = insert(PythonObjectFactory.create());
        }
        return factory;
    }

    public final PythonCore getCore() {
        return getContext().getCore();
    }

    public final NodeFactory getNodeFactory() {
        return getContext().getLanguage().getNodeFactory();
    }

    public final PException raise(PBaseException exc) {
        throw PException.fromObject(exc, this);
    }

    public PException raise(LazyPythonClass exceptionType) {
        throw raise(factory().createBaseException(exceptionType));
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        assert format != null;
        PBaseException baseException = factory().createBaseException(type, format, arguments);
        if (writeCause == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeCause = insert(WriteAttributeToObjectNode.create());
        }
        writeCause.execute(baseException, SpecialAttributeNames.__CAUSE__, cause);
        throw raise(baseException);
    }

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        assert format != null;
        throw raise(factory().createBaseException(type, format, arguments));
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        throw raise(type, getMessage(e));
    }

    @TruffleBoundary
    private static final String getMessage(Exception e) {
        return e.getMessage();
    }

    public final PException raiseIndexError() {
        return raise(PythonErrorType.IndexError, "cannot fit 'int' into an index-sized integer");
    }

    public final PException raiseOSError(VirtualFrame frame, int errno) {
        return raiseOSError(frame, errno, null, null, null);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror) {
        return raiseOSError(frame, oserror.getNumber(), oserror.getMessage(), null, null);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename) {
        return raiseOSError(frame, oserror.getNumber(), oserror.getMessage(), filename, null);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename, String filename2) {
        return raiseOSError(frame, oserror.getNumber(), oserror.getMessage(), filename, filename2);
    }

    public final PException raiseOSError(VirtualFrame frame, int errno, String errorstr, String filename, String filename2) {
        if (getNewFuncNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getNewFuncNode = insert(LookupAttributeInMRONode.create("__new__"));
        }
        Object newFunc = getNewFuncNode.execute(PythonBuiltinClassType.OSError);
        if (newFunc != PNone.NO_VALUE) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallVarargsMethodNode.create());
            }
            Object[] args = createArgumentsForOSError(errno, errorstr, filename, filename2);
            PBaseException error = (PBaseException) callNode.execute(frame, newFunc, args, new PKeyword[]{});
            return raise(error);
        }
        return raise(factory().createBaseException(PythonBuiltinClassType.OSError));
    }

    private Object[] createArgumentsForOSError(int errno, String errorstr, String filename, String filename2) {
        List<Object> result = new ArrayList<>();
        result.add(getBuiltinPythonClass(PythonBuiltinClassType.OSError));
        result.add(errno);
        if (errorstr != null && !errorstr.isEmpty()) {
            result.add(errorstr);
        }
        if (filename != null && !filename.isEmpty()) {
            result.add(filename);
            if (filename2 != null && !filename2.isEmpty()) {
                result.add(PNone.NONE); // instead winerror
                result.add(filename2);
            }
        }
        return result.toArray();
    }

    public final PythonClass getPythonClass(LazyPythonClass lazyClass, ConditionProfile profile) {
        if (profile.profile(lazyClass instanceof PythonClass)) {
            return (PythonClass) lazyClass;
        } else {
            return getCore().lookupType((PythonBuiltinClassType) lazyClass);
        }
    }

    public final PythonClass getBuiltinPythonClass(PythonBuiltinClassType type) {
        return getCore().lookupType(type);
    }

    public final PythonContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = PythonLanguage.getContextRef();
        }
        return contextRef.get();
    }

    protected Assumption singleContextAssumption() {
        CompilerAsserts.neverPartOfCompilation("the singleContextAssumption should only be retrieved in the interpreter");
        PythonLanguage language = null;
        RootNode rootNode = getRootNode();
        if (rootNode != null) {
            language = rootNode.getLanguage(PythonLanguage.class);
        } else {
            throw new IllegalStateException("a python node was executed without being adopted!");
        }
        if (language == null) {
            language = PythonLanguage.getCurrent();
        }
        return language.singleContextAssumption;
    }
}
