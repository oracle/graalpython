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
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(defineModule = "_warnings")
public class WarningsModuleBuiltins extends PythonBuiltins {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(WarningsModuleBuiltins.class);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WarningsModuleBuiltinsFactory.getFactories();
    }

    private static final HiddenKey FILTERS_MUTATED = new HiddenKey("filtersMutated");
    private static final HiddenKey PYTHON_WARN = new HiddenKey("warn");
    private static final HiddenKey PYTHON_WARN_EXPLICIT = new HiddenKey("warn_explicit");
    private static final HiddenKey PYTHON_WARNINGS = new HiddenKey("warnings");

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, "_warnings provides basic warning filtering support.\n" +
                        "It is a helper module to speed up interpreter start-up.");
        builtinConstants.put("filters", core.factory().createList());
        builtinConstants.put("_defaultaction", "ignore");
        builtinConstants.put("_onceregistry", core.factory().createDict());
        super.initialize(core);
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule weakrefModule = core.lookupBuiltinModule("_warnings");
        if (core.getContext().getOption(PythonOptions.WarnOptions).equals("ignore")) {
            weakrefModule.setAttribute(FILTERS_MUTATED, -1);
        } else {
            weakrefModule.setAttribute(FILTERS_MUTATED, 0);
        }
    }

    protected static boolean ignoreWarnings(PythonModule mod, ReadAttributeFromDynamicObjectNode readState) {
        return ((int) readState.execute(mod.getStorage(), FILTERS_MUTATED)) < 0;
    }

    protected static Object getOrDefault(Object obj) {
        return getOrDefault(obj, PNone.NONE);
    }

    protected static Object getOrDefault(Object obj, Object def) {
        if (obj == PNone.NO_VALUE) {
            return def;
        } else {
            return obj;
        }
    }

    @Builtin(name = "_register_python_warnings", minNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    static abstract class RegisterNode extends PythonBuiltinNode {
        @Specialization
        static Object mutate(PythonModule mod,
                        Object warnMethod,
                        Object warnExplicitMethod,
                        PythonModule warningsModule,
                        @Cached WriteAttributeToDynamicObjectNode writeNode) {
            DynamicObject storage = mod.getStorage();
            writeNode.execute(storage, PYTHON_WARN, warnMethod);
            writeNode.execute(storage, PYTHON_WARN_EXPLICIT, warnExplicitMethod);
            writeNode.execute(storage, PYTHON_WARNINGS, warningsModule);
            return PNone.NONE;
        }
    }

    @Builtin(name = "_filters_mutated", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    static abstract class FiltersMutated extends PythonBuiltinNode {
        @Specialization
        static Object mutate(PythonModule mod,
                        @Cached ReadAttributeFromDynamicObjectNode readWarningsNode,
                        @Cached WriteAttributeToObjectNode writeWarningsFiltersVersion,
                        @Cached ReadAttributeFromDynamicObjectNode readNode,
                        @Cached WriteAttributeToDynamicObjectNode writeNode) {
            int version = (int) readNode.execute(mod.getStorage(), FILTERS_MUTATED);
            writeNode.execute(mod.getStorage(), FILTERS_MUTATED, version + 1);
            Object warnMod = readWarningsNode.execute(mod.getStorage(),PYTHON_WARNINGS);
            if (warnMod != PNone.NO_VALUE) {
                writeWarningsFiltersVersion.execute(warnMod, "_filters_version", version);
            }
            return version;
        }
    }

    @TruffleBoundary
    private static final String formatWarning(Object message, Object category, Object filename, Object lineno) {
        InteropLibrary lib = InteropLibrary.getUncached();
        StringBuilder sb = new StringBuilder();
        try {
            if (filename != null) {
                sb.append(lib.asString(lib.toDisplayString(filename))).append(":");
            }
            if (lineno != null) {
                sb.append(lib.asString(lib.toDisplayString(lineno))).append(": ");
            }
            if (category != null && !(category instanceof PNone)) {
                sb.append(lib.asString(lib.toDisplayString(category))).append(":");
            } else {
                sb.append("UserWarning: ");
            }
            sb.append(lib.asString(lib.toDisplayString(message)));
            return sb.toString();
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Builtin(name = "warn_explicit", parameterNames = {"mod", "message", "category", "filename", "lineno", "module", "registry", "module_globals", "source"},
                    declaresExplicitSelf = true, minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    static abstract class WarnExplicitNode extends PythonBuiltinNode {
        protected static ExpressionNode getImportNode() {
            return new ImportNode("warnings").asExpression();
        }

        @Specialization
        Object warn(VirtualFrame frame, PythonModule mod, Object message, Object category, Object filename, Object lineno, Object module, Object registry, Object moduleGlobals, Object source,
                        @Cached BranchProfile noLogger,
                        @Cached ReadAttributeFromDynamicObjectNode readState,
                        @Cached ReadAttributeFromDynamicObjectNode readWarn,
                        @Cached CallNode callNode) {
            if (ignoreWarnings(mod, readState)) {
                return PNone.NONE;
            }
            Object method = readWarn.execute(mod.getStorage(), PYTHON_WARN_EXPLICIT);
            if (method == PNone.NO_VALUE) {
                noLogger.enter();
                LOGGER.warning(() -> formatWarning(message, category, filename, lineno));
                return PNone.NONE;
            }
            return callNode.execute(frame, method, message, category, filename, lineno, getOrDefault(module), getOrDefault(registry), getOrDefault(moduleGlobals), getOrDefault(source));
        }
    }

    /**
     * Can be used as equivalent for PyErr_WarnFormat.
     */
    @Builtin(name = "warn", parameterNames = {"mod", "message", "category", "stacklevel", "source"}, declaresExplicitSelf = true, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public static abstract class WarnNode extends PythonBuiltinNode {
        private final BranchProfile noLogger = BranchProfile.create();
        @CompilationFinal private Assumption singleContextAssumption;
        @CompilationFinal private PythonModule builtinMod;
        @CompilationFinal private ErrorMessageFormatter formatter;

        @Child private ReadAttributeFromDynamicObjectNode readWarn;
        @Child private ReadAttributeFromDynamicObjectNode readState;
        @Child private CallNode callNode;

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
            if (ignoreWarnings(warningsMod, getReadState())) {
                return PNone.NONE;
            }
            if (formatter == null) { // acts as branch profile
                formatter = new ErrorMessageFormatter();
            }
            return warn(frame, warningsMod, formatter.format(PythonObjectLibrary.getUncached(), message, arguments), category, 1, PNone.NONE);
        }

        @Specialization
        Object warn(VirtualFrame frame, PythonModule mod, Object message, Object wCategory, Object wStacklevel, Object source) {
            if (ignoreWarnings(mod, getReadState())) {
                return PNone.NONE;
            }
            Object method = getReadWarn().execute(mod.getStorage(), PYTHON_WARN);
            if (method == PNone.NO_VALUE) {
                noLogger.enter();
                LOGGER.warning(() -> formatWarning(message, wCategory, null, null));
                return PNone.NONE;
            }
            return getCallNode().execute(frame, method, message, getOrDefault(wCategory), getOrDefault(wStacklevel, 1), getOrDefault(source));
        }

        private Assumption getSingleContextAssumption() {
            if (singleContextAssumption == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                singleContextAssumption = singleContextAssumption();
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

        private ReadAttributeFromDynamicObjectNode getReadState() {
            if (readState == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readState = insert(ReadAttributeFromDynamicObjectNode.create());
            }
            return readState;
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
