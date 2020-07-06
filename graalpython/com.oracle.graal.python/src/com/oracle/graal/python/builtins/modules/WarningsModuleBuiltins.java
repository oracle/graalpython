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
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "_warnings")
public class WarningsModuleBuiltins extends PythonBuiltins {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(WarningsModuleBuiltins.class);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WarningsModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, "_warnings provides basic warning filtering support.\n" +
                        "It is a helper module to speed up interpreter start-up.");
        builtinConstants.put("_defaultaction", "ignore");
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
                            createFilter(core, PythonBuiltinClassType.DeprecationWarning, "ignore"),
                            createFilter(core, PythonBuiltinClassType.PendingDeprecationWarning, "ignore"),
                            createFilter(core, PythonBuiltinClassType.ImportWarning, "ignore"),
                            createFilter(core, PythonBuiltinClassType.ResourceWarning, "ignore") });
    }

    private static final PTuple createFilter(PythonCore core, PythonBuiltinClassType cat, String id, Object mod) {
        return core.factory().createTuple(new Object[] { id, PNone.NONE, cat, mod, 0 });
    }

    private static final PTuple createFilter(PythonCore core, PythonBuiltinClassType cat, String id) {
        return core.factory().createTuple(new Object[] { id, PNone.NONE, cat, PNone.NONE, 0 });
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

    @Builtin(name = "warn_explicit", parameterNames = { //
                        "mod", "message", "category", "filename", "lineno", //
                        "module", "registry", "module_globals", "source" }, //
                    declaresExplicitSelf = true, minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    static abstract class WarnExplicitNode extends PythonBuiltinNode {
        @Child CastToJavaStringNode castStr;
        @Child HashingCollectionNodes.GetDictStorageNode getDictStorageNode;
        @Child HashingStorageLibrary hashingLibrary;
        @Child PythonObjectLibrary objectLibrary;
        @Child CallUnaryMethodNode callNode;
        @Child BuiltinFunctions.IsInstanceNode isInstanceNode;
        @Child BuiltinConstructors.StrNode strNode;
        @CompilationFinal ConditionProfile gotFrame;

        @Specialization
        Object warn(VirtualFrame frame, PythonModule mod, Object message, Object category, Object filename, Object wLineno, Object module, Object registry, Object moduleGlobals, Object source) {
            Object sourceLine;
            int lineno = getObjectLibrary().asSizeWithFrame(lineno, getGotFrameProfile(), frame);
            if (!(moduleGlobals instanceof PNone)) {
                if (!(moduleGlobals instanceof PDict)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "module_globals", "dict", moduleGlobals);
                }
                sourceLine = getSourceLine(frame, (PDict) moduleGlobals, lineno);
            }

            return warnExplicit(frame, category, message, filename, lineno, module, registry, sourceLine, source);
        }

        private Object warnExplicit(VirtualFrame frame, Object category, Object message, Object filename, int lineno, Object module, Object registry, Object sourceLine, Object source) {
            if (!(registry instanceof PNone)) {
                if (!(registry instanceof PDict)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "'registry'", "dict or None", registry);
                }
            }

            if (module == PNone.NO_VALUE) {
                module = normalizeModule(filename);
            }

            String text;
            if (getIsInstanceNode().execute(frame, message, PythonBuiltinClassType.Warning)) {
                text = getStrNode().executeWith(frame, registry, sourceLine, source, text)
            } else {
            }

            return null;
        }

        private BuiltinConstructors.StrNode getStrNode() {
            if (strNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                strNode = insert(BuiltinConstructorsFactory.StrNodeFactory.create(null));
            }
            return strNode;
        }

        private BuiltinFunctions.IsInstanceNode getIsInstanceNode() {
            if (isInstanceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isInstanceNode = insert(BuiltinFunctions.IsInstanceNode.create());
            }
            return isInstanceNode;
        }

        private Object getSourceLine(VirtualFrame frame, PDict moduleGlobals, int lineno) {
            HashingStorage dictStorage = getDictStorageNode().execute(moduleGlobals);
            Object loader = getHashingStorageLibrary().getItemWithFrame(dictStorage, SpecialAttributeNames.__LOADER__, getGotFrameProfile(), frame);
            Object moduleName = getHashingStorageLibrary().getItemWithFrame(dictStorage, SpecialAttributeNames.__NAME__, getGotFrameProfile(), frame);
            Object getSource = getObjectLibrary().lookupAttribute(loader, "get_source");
            Object source = getCallNode().executeObject(frame, getSource, moduleName);
            if (source instanceof PNone) {
                return null;
            }
            String[] sourceLines;
            try {
                sourceLines = castStr.execute(source).split("\n");
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "get_source result", "str", source);
            }
            try {
                return sourceLines[lineno - 1];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw raise(PythonBuiltinClassType.IndexError, ErrorMessages.LIST_INDEX_OUT_OF_RANGE);
            }
        }

        private CallUnaryMethodNode getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallUnaryMethodNode.create());
            }
            return callNode;
        }

        private PythonObjectLibrary getObjectLibrary() {
            if (objectLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectLibrary = insert(PythonObjectLibrary.getFactory().createDispatched(3));
            }
            return objectLibrary;
        }

        private ConditionProfile getGotFrameProfile() {
            if (gotFrame == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                gotFrame = ConditionProfile.create();
            }
            return gotFrame;
        }

        private HashingStorageLibrary getHashingStorageLibrary() {
            if (hashingLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashingLibrary = insert(HashingStorageLibrary.getFactory().createDispatched(3));
            }
            return hashingLibrary;
        }

        private GetDictStorageNode getDictStorageNode() {
            if (getDictStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDictStorageNode = insert(HashingCollectionNodes.GetDictStorageNode.create());
            }
            return getDictStorageNode;
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
