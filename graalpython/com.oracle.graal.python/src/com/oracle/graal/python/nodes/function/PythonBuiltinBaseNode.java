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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithGlobalState;
import com.oracle.graal.python.nodes.PNodeWithGlobalState.NodeContextManager;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseOSErrorNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.PassCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic({PGuards.class, PythonOptions.class, SpecialMethodNames.class, SpecialAttributeNames.class, BuiltinNames.class})
public abstract class PythonBuiltinBaseNode extends PNodeWithContext {
    @Child private PythonObjectFactory objectFactory;
    @Child private PRaiseNode raiseNode;
    @Child private PRaiseOSErrorNode raiseOSNode;
    @Child private PassCaughtExceptionNode passExceptionNode;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    protected final PythonObjectFactory factory() {
        if (objectFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isAdoptable()) {
                objectFactory = insert(PythonObjectFactory.create());
            } else {
                objectFactory = getCore().factory();
            }
        }
        return objectFactory;
    }

    private final PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isAdoptable()) {
                raiseNode = insert(PRaiseNode.create());
            } else {
                raiseNode = PRaiseNode.getUncached();
            }
        }
        return raiseNode;
    }

    private final PRaiseOSErrorNode getRaiseOSNode() {
        if (raiseOSNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseOSNode = insert(PRaiseOSErrorNode.create());
        }
        return raiseOSNode;
    }

    public final PythonCore getCore() {
        return getContext().getCore();
    }

    public final PythonAbstractClass getPythonClass(LazyPythonClass lazyClass, ConditionProfile profile) {
        if (profile.profile(lazyClass instanceof PythonBuiltinClassType)) {
            return getCore().lookupType((PythonBuiltinClassType) lazyClass);
        } else {
            return (PythonAbstractClass) lazyClass;
        }
    }

    public final PythonBuiltinClass getBuiltinPythonClass(PythonBuiltinClassType type) {
        return getCore().lookupType(type);
    }

    protected final ContextReference<PythonContext> getContextRef() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef;
    }

    public final PythonContext getContext() {
        return getContextRef().get();
    }

    protected final PException passException(VirtualFrame frame) {
        if (passExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            passExceptionNode = insert(PassCaughtExceptionNode.create());
        }
        return passExceptionNode.execute(frame);
    }

    protected final NodeContextManager withGlobalState(VirtualFrame frame) {
        return PNodeWithGlobalState.transferToContext(getContextRef(), frame, this);
    }

    protected final NodeContextManager withGlobalState(PNodeWithGlobalState node, VirtualFrame frame) {
        return node.withGlobalState(getContextRef(), frame);
    }

    public final PException raise(PBaseException exc) {
        return getRaiseNode().raise(exc);
    }

    public PException raise(LazyPythonClass exceptionType) {
        return getRaiseNode().raise(exceptionType);
    }

    public final PException raiseIndexError() {
        return getRaiseNode().raiseIndexError();
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        return getRaiseNode().raise(type, cause, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        return getRaiseNode().raise(type, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        return getRaiseNode().raise(type, e);
    }

    public final PException raiseOSError(VirtualFrame frame, int num) {
        return getRaiseOSNode().raiseOSError(frame, num);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, Exception e) {
        return getRaiseOSNode().raiseOSError(frame, oserror, e);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename) {
        return getRaiseOSNode().raiseOSError(frame, oserror, filename);
    }
}
