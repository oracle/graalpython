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

import java.io.IOException;
import java.util.IllegalFormatException;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;

@CoreFunctions(defineModule = "_warnings")
public class WarningsModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey FILTERS_VERSION = new HiddenKey("filters_version");
    private static final HiddenKey FILTERS = new HiddenKey("filters");
    private static final HiddenKey DEFAULTACTION = new HiddenKey("_defaultaction");
    private static final HiddenKey ONCEREGISTRY = new HiddenKey("_onceregistry");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WarningsModuleBuiltinsFactory.getFactories();
    }

    // _Warnings_InitState done via initialize and postInitialize
    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, "_warnings provides basic warning filtering support.\n" +
                        "It is a helper module to speed up interpreter start-up.");
        builtinConstants.put("_defaultaction", "default");
        builtinConstants.put("_onceregistry", PythonObjectFactory.getUncached().createDict());
        builtinConstants.put("filters", initFilters());
        super.initialize(core);
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule warningsModule = core.lookupBuiltinModule("_warnings");
        // we need to copy these, since they must still be available even if the user `del`s the
        // attrs
        warningsModule.setAttribute(FILTERS, warningsModule.getAttribute("filters"));
        warningsModule.setAttribute(DEFAULTACTION, warningsModule.getAttribute("_defaultaction"));
        warningsModule.setAttribute(ONCEREGISTRY, warningsModule.getAttribute("_onceregistry"));
        DynamicObjectLibrary.getUncached().putLong(warningsModule, FILTERS_VERSION, 0);
    }

    private static PTuple createFilter(PythonBuiltinClassType cat, String id, Object mod) {
        return PythonObjectFactory.getUncached().createTuple(new Object[]{id, PNone.NONE, cat, mod, 0});
    }

    // init_filters
    private static PList initFilters() {
        return PythonObjectFactory.getUncached().createList(new Object[]{
                        createFilter(PythonBuiltinClassType.DeprecationWarning, "default", "__main__"),
                        createFilter(PythonBuiltinClassType.DeprecationWarning, "ignore", PNone.NONE),
                        createFilter(PythonBuiltinClassType.PendingDeprecationWarning, "ignore", PNone.NONE),
                        createFilter(PythonBuiltinClassType.ImportWarning, "ignore", PNone.NONE),
                        createFilter(PythonBuiltinClassType.ResourceWarning, "ignore", PNone.NONE)});
    }

    static final class WarningsModuleNode extends Node implements IndirectCallNode {
        private static final String WARNINGS = "warnings";

        @CompilationFinal ContextReference<PythonContext> contextRef;
        @Child DynamicObjectLibrary warningsModuleLib;
        @Child CastToJavaStringNode castStr;
        @Child PRaiseNode raiseNode;
        @Child PythonObjectLibrary pylib;
        @Child PythonObjectFactory factory;
        @Child IsSubtypeNode isSubtype;
        @Child GetDictNode getDictNode;
        @Child ReadCallerFrameNode readCallerNode;

        static WarningsModuleNode create() {
            return new WarningsModuleNode();
        }

        private static Object tryImport() {
            return AbstractImportNode.importModule(WARNINGS);
        }

        private PythonContext getContext() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef.get();
        }

        private DynamicObjectLibrary getWarnLib() {
            if (warningsModuleLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                warningsModuleLib = insert(DynamicObjectLibrary.getFactory().createDispatched(1));
            }
            return warningsModuleLib;
        }

        private PythonObjectLibrary getPyLib() {
            if (pylib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // used for: sys and warnings modules, modules and registry dicts, a regex,
                // filters tuple, boolean results, a category type, line and filters-version
                // int-like objects
                reportPolymorphicSpecialize();
                pylib = insert(PythonObjectLibrary.getFactory().createDispatched(7));
            }
            return pylib;
        }

        private CastToJavaStringNode getCastStr() {
            if (castStr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                castStr = insert(CastToJavaStringNode.create());
            }
            return castStr;
        }

        private PRaiseNode getRaise() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        private PythonObjectFactory getFactory() {
            if (factory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                factory = insert(PythonObjectFactory.create());
            }
            return factory;
        }

        private IsSubtypeNode getIsSubtype() {
            if (isSubtype == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                isSubtype = insert(IsSubtypeNode.create());
            }
            return isSubtype;
        }

        private PDict getSysDict() {
            if (getDictNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                getDictNode = insert(GetDictNode.create());
            }
            return (PDict) getDictNode.execute(getContext().getCore().lookupBuiltinModule("sys"));
        }

        private PDict getGlobalsDict(Object globals) {
            if (globals instanceof PDict) {
                return (PDict) globals;
            }
            if (getDictNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                getDictNode = insert(GetDictNode.create());
            }
            return (PDict) getDictNode.execute(globals);
        }

        private PFrame getCallerFrame(VirtualFrame frame, int stackLevel) {
            if (readCallerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportPolymorphicSpecialize();
                readCallerNode = insert(ReadCallerFrameNode.create());
            }
            return readCallerNode.executeWith(frame, stackLevel);
        }

        // _Warnings_GetState split up

        /**
         * May be on the fast path, depending on the passed library.
         */
        private static long getStateFiltersVersion(PythonModule warningsModule, DynamicObjectLibrary dylib) {
            try {
                return dylib.getLongOrDefault(warningsModule, FILTERS_VERSION, 0);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        /**
         * On fast path.
         */
        private Object getStateFilters(PythonModule warningsModule) {
            return getWarnLib().getOrDefault(warningsModule, FILTERS, null);
        }

        /**
         * On slow path.
         */
        private static Object getStateOnceRegistry(PythonModule warningsModule) {
            return DynamicObjectLibrary.getUncached().getOrDefault(warningsModule, ONCEREGISTRY, null);
        }

        /**
         * On fast path.
         */
        private Object getStateDefaultAction(PythonModule warningsModule) {
            return getWarnLib().getOrDefault(warningsModule, DEFAULTACTION, null);
        }

        /**
         * On fast path.
         */
        private boolean checkMatched(VirtualFrame frame, Object obj, Object arg) {
            if (obj == PNone.NONE) {
                return true;
            }
            try {
                String objStr = getCastStr().execute(obj);
                try {
                    return PString.equals(objStr, getCastStr().execute(arg));
                } catch (CannotCastException e) {
                    // Python calls PyUnicode_Compare directly, which raises this error
                    throw getRaise().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANT_COMPARE, obj, arg);
                }
            } catch (CannotCastException e) {
                Object result = getPyLib().lookupAndCallRegularMethod(obj, frame, "match", arg);
                return getPyLib().isTrue(result);
            }
        }

        /**
         * On fast path. Never tries to import the warnings module.
         */
        private Object getWarningsAttr(VirtualFrame frame, String attr) {
            return getWarningsAttr(frame, attr, false, getPyLib(), getContext());
        }

        /**
         * Slow path. Sometimes may try to import the warnings module.
         */
        private static Object getWarningsAttr(String attr, boolean tryImport) {
            return getWarningsAttr(null, attr, tryImport, PythonObjectLibrary.getUncached(), PythonLanguage.getContext());
        }

        /**
         * Used on both fast and slow path.
         */
        private static Object getWarningsAttr(VirtualFrame frame, String attr, boolean tryImport,
                        PythonObjectLibrary polib, PythonContext context) {
            Object warningsModule = null;
            if (tryImport) {
                try {
                    warningsModule = tryImport();
                } catch (PException e) {
                    e.expect(PythonBuiltinClassType.ImportError, IsBuiltinClassProfile.getUncached());
                    return null;
                }
            } else {
                Object sys = context.getCore().lookupBuiltinModule("sys");
                Object modules = polib.lookupAttribute(sys, frame, "modules");
                try {
                    warningsModule = polib.lookupAndCallSpecialMethod(modules, frame, "get", WARNINGS, PNone.NONE);
                } catch (PException e) {
                    return null;
                }
                if (warningsModule == PNone.NONE) {
                    return null;
                }
            }
            Object result = polib.lookupAttribute(warningsModule, frame, attr);
            if (result == PNone.NO_VALUE) {
                return null;
            } else {
                return result;
            }
        }

        /**
         * On slow path.
         */
        private static PDict getOnceRegistry(PythonModule module) {
            Object registry = getWarningsAttr("onceregistry", false);
            if (registry == null) {
                registry = getStateOnceRegistry(module);
            }
            if (!(registry instanceof PDict)) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, "warnings.onceregistry must be a dict, not %p", registry);
            }
            return (PDict) registry;
        }

        /**
         * On fast path.
         */
        private String getDefaultAction(VirtualFrame frame, PythonModule module) {
            Object defaultAction = getWarningsAttr(frame, "defaultaction");
            if (defaultAction == null) {
                defaultAction = getStateDefaultAction(module);
            }
            try {
                return getCastStr().execute(defaultAction);
            } catch (CannotCastException e) {
                throw getRaise().raise(PythonBuiltinClassType.TypeError, "warnings.defaultaction must be a string, not %p", defaultAction);
            }
        }

        /**
         * On fast path.
         */
        private String getFilter(VirtualFrame frame, PythonModule _warnings, Object category, Object text, int lineno, Object module, Object[] item) {
            Object filters = getWarningsAttr(frame, "filters");
            if (filters != null) {
                getWarnLib().put(_warnings, FILTERS, filters);
            } else {
                filters = getStateFilters(_warnings);
            }
            if (filters == null || !(filters instanceof PList)) {
                throw getRaise().raise(PythonBuiltinClassType.ValueError, "warnings.filters must be a list");
            }
            for (int i = 0; i < getPyLib().length(filters); i++) {
                Object tmpItem = getPyLib().lookupAndCallSpecialMethod(filters, frame, SpecialMethodNames.__GETITEM__, i);
                if (!(tmpItem instanceof PTuple) || getPyLib().length(tmpItem) != 5) {
                    throw getRaise().raise(PythonBuiltinClassType.ValueError, "warnings.filters item %d isn't a 5-tuple", i);
                }

                Object actionObj = getPyLib().lookupAndCallSpecialMethod(tmpItem, frame, SpecialMethodNames.__GETITEM__, 0);
                String action;
                try {
                    action = getCastStr().execute(actionObj);
                } catch (CannotCastException e) {
                    // CPython does this check after the other __getitem__ calls, but we know it's a
                    // tuple so...
                    throw getRaise().raise(PythonBuiltinClassType.TypeError, "action must be a string, not %p", actionObj);
                }
                Object msg = getPyLib().lookupAndCallSpecialMethod(tmpItem, frame, SpecialMethodNames.__GETITEM__, 1);
                Object cat = getPyLib().lookupAndCallSpecialMethod(tmpItem, frame, SpecialMethodNames.__GETITEM__, 2);
                Object mod = getPyLib().lookupAndCallSpecialMethod(tmpItem, frame, SpecialMethodNames.__GETITEM__, 3);
                Object lnObj = getPyLib().lookupAndCallSpecialMethod(tmpItem, frame, SpecialMethodNames.__GETITEM__, 4);

                boolean goodMsg = checkMatched(frame, msg, text);
                boolean goodMod = checkMatched(frame, mod, module);
                boolean isSubclass = getIsSubtype().execute(category, cat);
                int ln = getPyLib().asSize(lnObj);
                if (goodMsg && isSubclass && goodMod && (ln == 0 || lineno == ln)) {
                    // if we're ignoring warnings, the first action will match all and the loop
                    // count would always be 1, so let's report here and hope that Graal will unroll
                    // it
                    com.oracle.truffle.api.nodes.LoopNode.reportLoopCount(this, i + 1);
                    item[0] = tmpItem;
                    return action;
                }
            }

            String action = getDefaultAction(frame, _warnings);
            item[0] = PNone.NONE;
            return action;
        }

        /**
         * The variant of alreadyWarned that should not set and that must be on the fast path.
         */
        private boolean alreadyWarnedShouldNotSet(VirtualFrame frame, PythonModule _warnings, Object registry, Object key) {
            return alreadyWarned(frame, _warnings, registry, key, false, getPyLib(), getWarnLib());
        }

        /**
         * The variant of alreadyWarned that should set and that's on the slow path where the
         * warnings will be printed.
         */
        private static boolean alreadyWarnedShouldSet(PythonModule _warnings, Object registry, Object key) {
            return alreadyWarned(null, _warnings, registry, key, true, PythonObjectLibrary.getUncached(), DynamicObjectLibrary.getUncached());
        }

        /**
         * Used on both fast and slow path.
         */
        private static boolean alreadyWarned(VirtualFrame frame, PythonModule _warnings, Object registry, Object key, boolean shouldSet, PythonObjectLibrary polib, DynamicObjectLibrary warnLib) {
            Object versionObj = polib.lookupAndCallSpecialMethod(registry, frame, "get", "version", PNone.NONE);
            long stateFiltersVersion = getStateFiltersVersion(_warnings, warnLib);
            if (versionObj == PNone.NONE || !polib.equals(stateFiltersVersion, versionObj, polib)) {
                polib.lookupAndCallSpecialMethod(registry, frame, "clear");
                polib.lookupAndCallSpecialMethod(registry, frame, SpecialMethodNames.__SETITEM__, "version", stateFiltersVersion);
            } else {
                Object alreadyWarned = polib.lookupAndCallSpecialMethod(registry, frame, "get", key, PNone.NONE);
                if (alreadyWarned != PNone.NONE) {
                    return polib.isTrue(alreadyWarned);
                }
            }
            if (shouldSet) {
                polib.lookupAndCallSpecialMethod(registry, frame, SpecialMethodNames.__SETITEM__, key, true);
            }
            return false;
        }

        /**
         * On the fast path, but must be behind a boundary for the String operations.
         */
        @TruffleBoundary
        private static String normalizeModule(String filename) {
            int length = filename.length();
            if (length == 0) {
                return "<unknown>";
            }
            if (filename.endsWith(".py")) {
                return filename.substring(0, length - 3);
            } else {
                return filename;
            }
        }

        @TruffleBoundary
        private static boolean updateRegistry(PythonModule _warnings, Object registry, Object text, Object category, boolean addZero) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PTuple altKey;
            if (addZero) {
                altKey = factory.createTuple(new Object[]{text, category, 0});
            } else {
                altKey = factory.createTuple(new Object[]{text, category});
            }
            return alreadyWarnedShouldSet(_warnings, registry, altKey);
        }

        @TruffleBoundary
        private static void showWarning(Object filename, int lineno, Object text, Object category, Object sourceline) {
            PythonObjectLibrary polib = PythonObjectLibrary.getUncached();
            Object name;
            if (category instanceof PythonBuiltinClassType) {
                name = ((PythonBuiltinClassType) category).getName();
            } else {
                name = polib.lookupAttribute(category, null, SpecialAttributeNames.__NAME__);
            }
            Object sys = PythonLanguage.getCore().lookupBuiltinModule("sys");
            Object stderr;
            try {
                stderr = polib.lookupAttribute(sys, null, "stderr");
            } catch (PException e) {
                try {
                    PythonLanguage.getContext().getEnv().err().write("lost sys.stderr\n".getBytes());
                } catch (IOException ioe) {
                    // nothing more we can do
                }
                throw e;
            }

            // tfel: I've inlined PyFile_WriteObject, which just calls the "write" method and
            // decides if we should use "repr" or "str" - in this case its always "str" for objects

            // Print "filename:lineno: category: text\n"
            polib.lookupAndCallRegularMethod(stderr, null, "write", polib.lookupAndCallRegularMethod(filename, null, SpecialMethodNames.__STR__));
            polib.lookupAndCallRegularMethod(stderr, null, "write", String.format(":%d:", lineno));
            polib.lookupAndCallRegularMethod(stderr, null, "write", polib.lookupAndCallRegularMethod(name, null, SpecialMethodNames.__STR__));
            polib.lookupAndCallRegularMethod(stderr, null, "write", ": ");
            polib.lookupAndCallRegularMethod(stderr, null, "write", polib.lookupAndCallRegularMethod(text, null, SpecialMethodNames.__STR__));
            polib.lookupAndCallRegularMethod(stderr, null, "write", "\n");

            // Print " source_line\n"
            if (sourceline != null) {
                // CPython goes through the trouble of getting a substring of sourceline with
                // leading whitespace removed, but then ignores the substring and prints the full
                // sourceline anyway...
                polib.lookupAndCallRegularMethod(stderr, null, "write", polib.lookupAndCallRegularMethod(sourceline, null, SpecialMethodNames.__STR__));
                polib.lookupAndCallRegularMethod(stderr, null, "write", "\n");
            } else {
                // TODO: _Py_DisplaySourceLine(f_stderr, filename, lineno, indent 2);
            }
        }

        @TruffleBoundary
        private static void callShowWarning(Object category, Object text, Object message,
                        Object filename, int lineno, Object sourceline, Object sourceIn) {
            PythonObjectLibrary polib = PythonObjectLibrary.getUncached();
            PRaiseNode raise = PRaiseNode.getUncached();

            Object showFn = getWarningsAttr("_showwarnmsg", sourceIn != null);
            if (showFn == null) {
                showWarning(filename, lineno, text, category, sourceline);
                return;
            }

            if (!polib.isCallable(showFn)) {
                throw raise.raise(PythonBuiltinClassType.TypeError, "warnings._showwarnmsg() must be set to a callable");
            }

            Object warnmsgCls = getWarningsAttr("WarningMessage", false);
            if (warnmsgCls == null) {
                throw raise.raise(PythonBuiltinClassType.RuntimeError, "unable to get warnings.WarningMessage");
            }
            Object source = sourceIn == null ? PNone.NONE : sourceIn;

            assert message != null && category != null && filename != null && source != null;
            assert message != PNone.NO_VALUE && category != PNone.NO_VALUE && filename != PNone.NO_VALUE && source != PNone.NO_VALUE;
            Object msg = polib.callObject(warnmsgCls, null, message, category, filename, lineno, PNone.NONE, PNone.NONE, source);
            polib.callObject(showFn, null, msg);
        }

        /**
         * This is the main part that checks if the warning will be printed at all. We shouldn't put
         * it behind a boundary, and we should try to ensure it compiles away nicely when warnings
         * are set to ignore. On the fast path.
         */
        private void warnExplicit(VirtualFrame frame, PythonModule warnings,
                        Object categoryIn, Object messageIn, String filename, int lineno, Object moduleIn,
                        Object registry, PDict globals /* see comment in method */, Object source) {
            // CPython passes the sourceline directly here where we pass the globals argument. If
            // it's not null, and we need the source line eventually, we will get it on the slow
            // path.
            Object module = moduleIn;
            Object category = categoryIn;
            Object message = messageIn;

            if (module == PNone.NONE) {
                return;
            }

            if (registry != null && registry != PNone.NONE && !(registry instanceof PDict)) {
                throw getRaise().raise(PythonBuiltinClassType.TypeError, "'registry' must be a dict or None");
            }

            if (module == null) {
                module = normalizeModule(filename);
            }

            // Python code uses PyObject_IsInstance but on the built-in Warning class, so we know
            // what __instancecheck__ does
            Object text;
            if (getIsSubtype().execute(frame, getPyLib().getLazyPythonClass(message), PythonBuiltinClassType.Warning)) {
                text = getPyLib().lookupAndCallRegularMethod(message, frame, SpecialMethodNames.__STR__);
                category = getPyLib().getLazyPythonClass(message);
            } else {
                text = message;
                message = getPyLib().callObject(category, frame, message);
            }

            Object key = getFactory().createTuple(new Object[]{text, category, lineno});
            if (registry != null && registry != PNone.NONE) {
                if (alreadyWarnedShouldNotSet(frame, warnings, registry, key)) {
                    return;
                }
            }
            // TODO: branch profile?

            Object[] item = new Object[1];
            String action = getFilter(frame, warnings, category, text, lineno, module, item);

            // CPython first checks for the "error" case, but since we want to optimize for ignored
            // warnings, we swap those checks
            if (PString.equals("ignore", action)) {
                return;
            }

            // the rest of this function is behind a TruffleBoundary, since we don't care so much
            // about performance when warnings are enabled.
            Object state = IndirectCallContext.enter(frame, getContext(), this);
            try {
                warnExplicitPart2(this, warnings, filename, lineno, registry, globals, source, category, message, text, key, item, action);
            } finally {
                IndirectCallContext.exit(frame, getContext(), state);
            }
        }

        @TruffleBoundary
        private static void warnExplicitPart2(Node node, PythonModule warnings, Object filename, int lineno, Object registry, PDict globals, Object source, Object category, Object message,
                        Object text,
                        Object key, Object[] item, String action) {
            PythonObjectLibrary polib = PythonObjectLibrary.getUncached();

            if (PString.equals("error", action)) {
                if (!(message instanceof PBaseException)) {
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.SystemError, "exception %s not a BaseException subclass",
                                    polib.lookupAndCallRegularMethod(message, null, SpecialMethodNames.__REPR__));
                } else {
                    throw PRaiseNode.raise(node, (PBaseException) message, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.getCurrent()));
                }
            }

            if (!PString.equals("always", action)) {
                if (registry != null && registry != PNone.NONE) {
                    polib.lookupAndCallSpecialMethod(registry, null, SpecialMethodNames.__SETITEM__, key, true);
                }

                boolean alreadyWarned = false;
                if (PString.equals("once", action)) {
                    if (registry == null || registry == PNone.NONE) {
                        Object currentRegistry = getOnceRegistry(warnings);
                        alreadyWarned = updateRegistry(warnings, currentRegistry, text, category, false);
                    } else {
                        alreadyWarned = updateRegistry(warnings, registry, text, category, false);
                    }
                } else if (PString.equals("module", action)) {
                    if (registry != null && registry != PNone.NONE) {
                        alreadyWarned = updateRegistry(warnings, registry, text, category, false);
                    }
                } else if (!PString.equals("default", action)) {
                    PRaiseNode.getUncached().raise(PythonBuiltinClassType.RuntimeError, "Unrecognized action (%s) in warnings.filters:\n %s", action,
                                    polib.lookupAndCallRegularMethod(item, null, SpecialMethodNames.__REPR__));
                }

                if (alreadyWarned) {
                    return;
                }
            }

            // CPython does this part eagerly in warn_explicit before ever getting here, but we try
            // to delay it
            String sourceline = null;
            if (globals != null) {
                sourceline = getSourceLine(globals, lineno);
            }

            callShowWarning(category, text, message, filename, lineno, sourceline, source);
        }

        /**
         * Used from doWarn. On the fast path.
         */
        private void setupContext(VirtualFrame frame, int stackLevel, String[] filename, int[] lineno, String[] module, Object[] registry) {
            PFrame f = getCallerFrame(frame, stackLevel - 1); // the stack level for the
                                                              // intrinsified version is off-by-one
                                                              // compared to the Python version
            Object globals;
            if (f == null) {
                globals = getSysDict();
                filename[0] = "sys";
                lineno[0] = 1;
            } else {
                globals = getGlobalsDict(f.getGlobals());
                if (globals == null) {
                    globals = getSysDict();
                }
                lineno[0] = f.getLine();
                RootCallTarget ct = f.getTarget();
                if (ct != null) {
                    filename[0] = PCode.extractFileName(ct.getRootNode());
                } else {
                    filename[0] = "<unknown source>";
                }
            }

            PythonObjectLibrary polib = getPyLib();
            registry[0] = polib.lookupAndCallSpecialMethod(globals, frame, "get", "__warningregistry__", PNone.NONE);
            if (registry[0] == PNone.NONE) {
                registry[0] = getFactory().createDict();
                polib.lookupAndCallSpecialMethod(globals, frame, SpecialMethodNames.__SETITEM__, "__warningregistry__", registry[0]);
            }
            Object moduleObj = polib.lookupAndCallSpecialMethod(globals, frame, "get", "__name__", PNone.NONE);
            if (moduleObj == PNone.NONE) {
                module[0] = null;
            } else {
                try {
                    module[0] = getCastStr().execute(moduleObj);
                } catch (CannotCastException e) {
                    module[0] = "<string>";
                }
            }
        }

        /**
         * Used from the "warn" function. On the fast path.
         */
        private Object getCategory(VirtualFrame frame, Object message, Object category) {
            Object messageType = getPyLib().getLazyPythonClass(message);
            if (getIsSubtype().execute(frame, messageType, PythonBuiltinClassType.Warning)) {
                return messageType;
            } else if (category == null || category == PNone.NONE) {
                return PythonBuiltinClassType.UserWarning;
            } else if (!getPyLib().isLazyPythonClass(category) || !getIsSubtype().execute(frame, category, PythonBuiltinClassType.Warning)) {
                throw getRaise().raise(PythonBuiltinClassType.TypeError, "category must be a Warning subclass, not '%P'", category);
            } else {
                return category;
            }
        }

        /**
         * Entry point for module functions. On the fast path.
         */
        private void doWarn(VirtualFrame frame, PythonModule warnings,
                        Object message, Object category, int stackLevel, Object source) {
            String[] filename = new String[1];
            int[] lineno = new int[1];
            String[] module = new String[1];
            Object[] registry = new Object[1];
            setupContext(frame, stackLevel, filename, lineno, module, registry);
            warnExplicit(frame, warnings, category, message, filename[0], lineno[0], module[0], registry[0], null, source);
        }

        /**
         * Slow path.
         */
        @TruffleBoundary
        private static String getSourceLine(PDict globals, int lineno) {
            PythonObjectLibrary polib = PythonObjectLibrary.getUncached();
            Object loader = polib.lookupAndCallSpecialMethod(globals, null, "get", "__loader__", PNone.NONE);
            if (loader == PNone.NONE) {
                return null;
            }
            Object moduleName = polib.lookupAndCallSpecialMethod(globals, null, "get", "__name__", PNone.NONE);
            if (moduleName == PNone.NONE) {
                return null;
            }
            Object source;
            try {
                source = polib.lookupAndCallRegularMethod(loader, null, "get_source", moduleName);
            } catch (PException e) {
                return null;
            }
            if (source == PNone.NONE) {
                return null;
            }
            String src;
            try {
                src = CastToJavaStringNode.getUncached().execute(source);
            } catch (CannotCastException e) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_S_NOT_P, "str", source);
            }
            String[] lines = src.split("\n");
            if (lines.length >= lineno) {
                return lines[lineno - 1];
            } else {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }

        private final Assumption passFrame = Truffle.getRuntime().createAssumption();
        private final Assumption passExc = Truffle.getRuntime().createAssumption();

        public Assumption needNotPassFrameAssumption() {
            return passFrame;
        }

        public Assumption needNotPassExceptionAssumption() {
            return passExc;
        }
    }

    @ReportPolymorphism
    @NodeInfo(shortName = "warnings_warn_impl", description = "implements warnings_warn_impl and the clinic wrapper")
    @Builtin(name = "warn", minNumOfPositionalArgs = 2, parameterNames = {"$mod", "message", "category", "stacklevel", "source"}, declaresExplicitSelf = true, alwaysNeedsCallerFrame = true)
    @GenerateNodeFactory
    static abstract class WarnBuiltinNode extends PythonBuiltinNode {
        @Specialization(limit = "2")
        Object doWarn(VirtualFrame frame, PythonModule mod, Object msg, Object cat, Object lvl, Object src,
                        @Cached IsSubtypeNode isFloatSubtype,
                        @Cached WarningsModuleNode moduleFunctionsNode,
                        @CachedLibrary("lvl") PythonObjectLibrary lvlLib) {
            // argument processing like clinic
            Object message = msg;
            Object category = cat;
            if (category == PNone.NO_VALUE) {
                category = PNone.NONE;
            }
            int stacklevel = 1;
            if (lvl != PNone.NO_VALUE) {
                if (isFloatSubtype.execute(frame, lvlLib.getLazyPythonClass(lvl), PythonBuiltinClassType.PFloat)) {
                    throw raise(PythonBuiltinClassType.TypeError, "integer argument expected, got float");
                }
                stacklevel = lvlLib.asSize(lvl);
            }
            Object source = src;
            if (source == PNone.NO_VALUE) {
                source = PNone.NONE;
            }
            // warnings_warn_impl
            category = moduleFunctionsNode.getCategory(frame, message, category);
            moduleFunctionsNode.doWarn(frame, mod, message, category, stacklevel, source);
            return PNone.NONE;
        }
    }

    @ReportPolymorphism
    @NodeInfo(shortName = "warnings_warn_explicit")
    @Builtin(name = "warn_explicit", minNumOfPositionalArgs = 5, parameterNames = {"$mod", "message", "category", "filename", "lineno", "module", "registry", "module_globals",
                    "source"}, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class WarnExplicitBuiltinNode extends PythonBuiltinNode {
        @Specialization
        Object doWarn(VirtualFrame frame, PythonModule mod, Object message, Object category, Object flname,
                        Object ln, Object module, Object registry, Object globals, Object source,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CastToJavaIntLossyNode castLong,
                        @Cached WarningsModuleNode moduleFunctionsNode) {
            String filename;
            try {
                filename = castStr.execute(flname);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "warn_explicit()", 3, "str", flname);
            }
            int lineno;
            try {
                lineno = castLong.execute(ln);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.INTEGER_EXPECTED_GOT_FLOAT);
            }
            PDict globalsDict;
            if (globals instanceof PNone) {
                globalsDict = null;
            } else if (globals instanceof PDict) {
                globalsDict = (PDict) globals;
            } else {
                throw raise(PythonBuiltinClassType.TypeError, "module_globals must be a dict, not '%p'", globals);
            }
            // CPython calls get_source_line here. But since that's potentially slow, maybe we can
            // get away with doing that lazily
            moduleFunctionsNode.warnExplicit(frame, mod, category, message, filename, lineno,
                            module == PNone.NO_VALUE ? null : module,
                            registry == PNone.NO_VALUE ? null : registry,
                            globalsDict,
                            source == PNone.NO_VALUE ? null : source);
            return PNone.NONE;
        }
    }

    @Builtin(name = "_filters_mutated", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class FiltersMutated extends PythonBuiltinNode {
        @Specialization(limit = "1")
        static PNone mutate(PythonModule self,
                        @CachedLibrary("self") DynamicObjectLibrary dylib) {
            long version = 0;
            try {
                version = dylib.getLongOrDefault(self, FILTERS_VERSION, 0);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.shouldNotReachHere();
            }
            dylib.putLong(self, FILTERS_VERSION, version + 1);
            return PNone.NONE;
        }
    }

    /**
     * Our replacement for PyErr_WarnFormat, warn_unicode and related functions.
     */
    public abstract static class WarnNode extends Node {
        private static final ErrorMessageFormatter formatter = new ErrorMessageFormatter();
        private static final WarnNode UNCACHED = new WarnNodeUncached();

        public static WarnNode create() {
            return new WarnNodeCached();
        }

        public static WarnNode getUncached() {
            return UNCACHED;
        }

        public final void warnUnicode(Frame frame, Object category, String message, int stackLevel, Object source) {
            execute(frame, source, category == null ? PythonBuiltinClassType.RuntimeWarning : category, message, stackLevel);
        }

        public final void warnFormat(Frame frame, Object source, Object category, int stackLevel, String message, Object... formatArgs) {
            execute(frame, source, category == null ? PythonBuiltinClassType.RuntimeWarning : category, message, stackLevel, formatArgs);
        }

        public final void resourceWarning(Frame frame, Object source, int stackLevel, String message, Object... formatArgs) {
            execute(frame, source, PythonBuiltinClassType.ResourceWarning, message, stackLevel, formatArgs);
        }

        public final void warnEx(Frame frame, Object category, String message, int stackLevel) {
            execute(frame, null, category == null ? PythonBuiltinClassType.RuntimeWarning : category, message, stackLevel);
        }

        public final void warn(Frame frame, Object category, String message) {
            warnEx(frame, category, message, 1);
        }

        protected abstract void execute(Frame frame, Object source, Object category, String format, int stackLevel, Object... formatArgs);

        private static final class WarnNodeCached extends WarnNode {
            @CompilationFinal ContextReference<PythonContext> ctxRef;
            @Child PythonObjectLibrary lib;
            @Child WarningsModuleNode moduleFunctionsNode;

            @Override
            protected void execute(Frame frame, Object source, Object category, String format, int stackLevel, Object... formatArgs) {
                assert frame instanceof VirtualFrame;
                if (ctxRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ctxRef = lookupContextReference(PythonLanguage.class);
                }
                PythonModule _warnings = ctxRef.get().getCore().lookupBuiltinModule("_warnings");
                if (lib == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lib = insert(PythonObjectLibrary.getFactory().createDispatched(3));
                }
                String message = formatMessage(format, formatArgs);
                if (moduleFunctionsNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    moduleFunctionsNode = insert(WarningsModuleNode.create());
                }
                moduleFunctionsNode.doWarn((VirtualFrame) frame, _warnings, message, category, stackLevel, source);
            }

            /*
             * Unfortunately, this has do be done eagerly for now, because of the way that the
             * action filters filter by message text. So we cannot easily wait until we find "ah,
             * this warning will be ignored" and then format behind the TruffleBoundary at the end
             * of warnExplicit, since matching the filters needs the text. We could very carefully
             * delay this formatting if e.g. there's a catch-all ignore filter in the filters list,
             * but that's a bit involved and might not be worth it.
             */
            @TruffleBoundary
            private String formatMessage(String format, Object... formatArgs) {
                String message;
                try {
                    message = formatter.format(lib, format, formatArgs);
                } catch (IllegalFormatException e) {
                    throw CompilerDirectives.shouldNotReachHere("error while formatting \"" + format + "\"", e);
                }
                return message;
            }

        }

        private static final class WarnNodeUncached extends WarnNode {
            @Override
            public boolean isAdoptable() {
                return false;
            }

            @Override
            protected void execute(Frame frame, Object source, Object category, String format, int stackLevel, Object... formatArgs) {
                PythonModule _warnings = lookupContextReference(PythonLanguage.class).get().getCore().lookupBuiltinModule("_warnings");
                Object warn = DynamicObjectLibrary.getUncached().getOrDefault(_warnings, "warn", PNone.NONE);
                PythonObjectLibrary lib = PythonObjectLibrary.getUncached();
                String message;
                try {
                    message = formatter.format(lib, format, formatArgs);
                } catch (IllegalFormatException e) {
                    throw CompilerDirectives.shouldNotReachHere("error while formatting \"" + format + "\"", e);
                }
                lib.callObject(warn, null, message, category, stackLevel, source);
            }

        }
    }
}
