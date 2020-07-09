/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = "_warnings")
public class WarningsModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WarningsModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, "_warnings provides basic warning filtering support.\n" +
                        "It is a helper module to speed up interpreter start-up.");
        builtinConstants.put("_defaultaction", "default");
        builtinConstants.put("_onceregistry", core.factory().createDict());
        super.initialize(core);
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule weakrefModule = core.lookupBuiltinModule("_warnings");
        weakrefModule.setAttribute("filters", initFilters(core));
    }

    private static PList initFilters(PythonCore core) {
        return core.factory().createList(new Object[] {
                            createFilter(core, PythonBuiltinClassType.DeprecationWarning, "default", "__main__"),
                            createFilter(core, PythonBuiltinClassType.DeprecationWarning, "ignore", PNone.NONE),
                            createFilter(core, PythonBuiltinClassType.PendingDeprecationWarning, "ignore", PNone.NONE),
                            createFilter(core, PythonBuiltinClassType.ImportWarning, "ignore", PNone.NONE),
                            createFilter(core, PythonBuiltinClassType.ResourceWarning, "ignore", PNone.NONE) });
    }

    private static final PTuple createFilter(PythonCore core, PythonBuiltinClassType cat, String id, Object mod) {
        return core.factory().createTuple(new Object[] { id, PNone.NONE, cat, mod, 0 });
    }

    @Builtin(name = "_filters_mutated", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    static abstract class FiltersMutated extends PythonBuiltinNode {
        @Specialization
        static long mutate(@CachedContext(PythonLanguage.class) PythonContext ctx) {
            return ctx.increaseWarningsFiltersVersion();
        }
    }

    @Builtin(name = "_filters_version", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    static abstract class FiltersVersion extends PythonBuiltinNode {
        @Specialization
        static long get(@CachedContext(PythonLanguage.class) PythonContext ctx) {
            return ctx.getWarningsFiltersVersion();
        }
    }

    /**
     * Equivalent for PyErr_WarnFormat.
     */
    public static abstract class WarnNode extends Node {
        @CompilationFinal private Assumption singleContextAssumption;
        @CompilationFinal private PythonModule builtinMod;
        @CompilationFinal private ErrorMessageFormatter formatter;
        @CompilationFinal private ContextReference<PythonContext> contextRef;

        @Child private ReadAttributeFromDynamicObjectNode readWarn;
        @Child private CallNode callNode;
        @Child private PythonObjectLibrary lib;

        // an optimization to avoid formatting the message string if we know that warnings are disabled
        public final Object execute(VirtualFrame frame, Object category, String message, Object... arguments) {
            PythonModule warningsMod;
            if (getSingleContextAssumption().isValid()) {
                if (builtinMod == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    builtinMod = getContext().getCore().lookupBuiltinModule("_warnings");
                }
                warningsMod = builtinMod;
            } else {
                warningsMod = getContext().getCore().lookupBuiltinModule("_warnings");
            }
            Object method = getReadWarn().execute(warningsMod.getStorage(), "warn");
            return getCallNode().execute(frame, method, getFormatter().format(getObjectLibrary(), message, arguments), category, 1, PNone.NONE);
        }

        private PythonObjectLibrary getObjectLibrary() {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonObjectLibrary.getFactory().createDispatched(3));
            }
            return lib;
        }

        private ErrorMessageFormatter getFormatter() {
            if (formatter == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                formatter = new ErrorMessageFormatter();
            }
            return formatter;
        }

        private PythonContext getContext() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef.get();
        }

        private Assumption getSingleContextAssumption() {
            if (singleContextAssumption == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                singleContextAssumption = lookupLanguageReference(PythonLanguage.class).get().singleContextAssumption;
            }
            return singleContextAssumption;
        }

        private ReadAttributeFromDynamicObjectNode getReadWarn() {
            if (readWarn == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readWarn = insert(ReadAttributeFromDynamicObjectNode.create());
            }
            return readWarn;
        }

        private CallNode getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallNode.create());
            }
            return callNode;
        }
    }
}
