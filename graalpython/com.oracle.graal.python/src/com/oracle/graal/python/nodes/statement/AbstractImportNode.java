/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.BuiltinNames.__IMPORT__;
import static com.oracle.graal.python.nodes.ErrorMessages.IMPORT_NOT_FOUND;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;

public abstract class AbstractImportNode extends StatementNode {
    @Child PythonObjectFactory objectFactory;
    @Child private PythonObjectLibrary pythonLibrary;

    @Child private CallNode callNode;
    @Child private GetDictNode getDictNode;
    @Child private PRaiseNode raiseNode;
    @Child private PConstructAndRaiseNode constructAndRaiseNode;

    @CompilationFinal private LanguageReference<PythonLanguage> languageRef;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    public AbstractImportNode() {
        super();
    }

    private PythonLanguage getPythonLanguage() {
        if (languageRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            languageRef = lookupLanguageReference(PythonLanguage.class);
        }
        return languageRef.get();
    }

    private ContextReference<PythonContext> getContextRef() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef;
    }

    protected PythonContext getContext() {
        return getContextRef().get();
    }

    protected PythonObjectFactory factory() {
        if (objectFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            objectFactory = insert(PythonObjectFactory.create());
        }
        return objectFactory;
    }

    protected Object importModule(VirtualFrame frame, String name) {
        return importModule(frame, name, PNone.NONE, PythonUtils.EMPTY_STRING_ARRAY, 0);
    }

    protected PythonObjectLibrary ensurePythonLibrary() {
        if (pythonLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pythonLibrary = insert(PythonObjectLibrary.getFactory().createDispatched(PythonOptions.getCallSiteInlineCacheMaxDepth()));
        }
        return pythonLibrary;
    }

    private PRaiseNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    protected PException raiseTypeError(String format, Object... args) {
        throw raise(PythonBuiltinClassType.TypeError, format, args);
    }

    protected PException raise(PythonBuiltinClassType type, String format, Object... args) {
        throw ensureRaiseNode().raise(type, format, args);
    }

    private PConstructAndRaiseNode ensureConstructAndRaiseNode() {
        if (constructAndRaiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            constructAndRaiseNode = insert(PConstructAndRaiseNode.create());
        }
        return constructAndRaiseNode;
    }

    protected PException raiseImportError(Frame frame, Object name, Object path, String format, Object... formatArgs) {
        throw ensureConstructAndRaiseNode().raiseImportError(frame, name, path, format, formatArgs);
    }

    CallNode getCallNode() {
        if (callNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callNode = insert(CallNode.create());
        }
        return callNode;
    }

    private GetDictNode getGetDictNode() {
        if (getDictNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getDictNode = insert(GetDictNode.create());
        }
        return getDictNode;
    }

    @TruffleBoundary
    public static Object importModule(String name) {
        PythonContext ctx = PythonLanguage.getContext();
        CallNode callNode = CallNode.getUncached();
        GetDictNode getDictNode = GetDictNode.getUncached();
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        PConstructAndRaiseNode raiseNode = PConstructAndRaiseNode.getUncached();
        return __import__(null, raiseNode, ctx, name, PNone.NONE, PythonUtils.EMPTY_STRING_ARRAY, 0, callNode, getDictNode, factory);
    }

    protected Object importModule(VirtualFrame frame, String name, Object globals, String[] fromList, int level) {
        // Look up built-in modules supported by GraalPython
        PythonContext context = getContext();
        if (!context.getCore().isInitialized()) {
            PythonModule builtinModule = context.getCore().lookupBuiltinModule(name);
            if (builtinModule != null) {
                return builtinModule;
            }
        }
        if (emulateJython()) {
            if (fromList.length > 0) {
                context.pushCurrentImport(PString.cat(name, ".", fromList[0]));
            } else {
                context.pushCurrentImport(name);
            }
        }
        try {
            return __import__(frame, ensureConstructAndRaiseNode(), context, name, globals, fromList, level, getCallNode(), getGetDictNode(), factory());
        } finally {
            if (emulateJython()) {
                context.popCurrentImport();
            }
        }
    }

    private static Object __import__(VirtualFrame frame, PConstructAndRaiseNode raiseNode, PythonContext ctx, String name, Object globals, String[] fromList, int level, CallNode callNode,
                    GetDictNode getDictNode,
                    PythonObjectFactory factory) {
        Object builtinImport = ctx.getCore().lookupBuiltinModule(BuiltinNames.BUILTINS).getAttribute(__IMPORT__);
        if (builtinImport == PNone.NO_VALUE) {
            throw raiseNode.raiseImportError(frame, IMPORT_NOT_FOUND);
        }
        assert builtinImport instanceof PMethod || builtinImport instanceof PFunction;
        assert fromList != null;
        assert globals != null;
        // the locals argument is ignored so it can always be None
        return callNode.execute(frame, builtinImport, new Object[]{name,
                        getDictNode.execute(globals), PNone.NONE, factory.createTuple(fromList), level},
                        PKeyword.EMPTY_KEYWORDS);
    }

    protected boolean emulateJython() {
        return getPythonLanguage().getEngineOption(PythonOptions.EmulateJython);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.CallTag.class || super.hasTag(tag);
    }
}
