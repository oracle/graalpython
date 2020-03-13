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

import static com.oracle.graal.python.nodes.BuiltinNames.GLOBALS;
import static com.oracle.graal.python.nodes.BuiltinNames.LOCALS;
import static com.oracle.graal.python.nodes.BuiltinNames.__IMPORT__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;

public abstract class AbstractImportNode extends StatementNode {
    @CompilationFinal private ContextReference<PythonContext> contextRef;
    @Child PythonObjectFactory objectFactory;

    @Child private CallNode callNode;
    @Child private GetDictNode getDictNode;
    @CompilationFinal private Boolean emulateJython;

    public AbstractImportNode() {
        super();
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
        return importModule(frame, name, PNone.NONE, new String[0], 0);
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

    protected Object importModule(VirtualFrame frame, String name, Object globals, String[] fromList, int level) {
        // Look up built-in modules supported by GraalPython
        if (!getContext().getCore().isInitialized()) {
            PythonModule builtinModule = getContext().getCore().lookupBuiltinModule(name);
            if (builtinModule != null) {
                return builtinModule;
            }
        }
        if (emulateJython()) {
            if (fromList.length > 0) {
                getContext().pushCurrentImport(name + "." + fromList[0]);
            } else {
                getContext().pushCurrentImport(name);
            }
        }
        try {
            return __import__(frame, name, globals, fromList, level);
        } finally {
            if (emulateJython()) {
                getContext().popCurrentImport();
            }
        }
    }

    Object __import__(VirtualFrame frame, String name, Object globals, String[] fromList, int level) {
        PMethod builtinImport = (PMethod) getContext().getCore().lookupBuiltinModule(BuiltinNames.BUILTINS).getAttribute(__IMPORT__);
        assert fromList != null;
        assert globals != null;
        return getCallNode().execute(frame, builtinImport, new Object[]{name}, new PKeyword[]{
                        new PKeyword(GLOBALS, getGetDictNode().execute(globals)),
                        new PKeyword(LOCALS, PNone.NONE), // the locals argument is ignored so it
                                                          // can always be None
                        new PKeyword("fromlist", factory().createTuple(fromList)),
                        new PKeyword("level", level)
        });
    }

    protected boolean emulateJython() {
        if (emulateJython == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            emulateJython = getContext().isJythonEmulated();
        }
        return emulateJython;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.CallTag.class || super.hasTag(tag);
    }
}
