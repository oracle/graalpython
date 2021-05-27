/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyFrameGetBuiltins;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetDictNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNodeFactory.ImportNameNodeGen;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

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

    public static Object importModule(String name) {
        return importModule(name, PythonUtils.EMPTY_STRING_ARRAY);
    }

    @TruffleBoundary
    public static Object importModule(String name, String[] fromList) {
        Object builtinImport = PyFrameGetBuiltins.getUncached().execute().getAttribute(__IMPORT__);
        if (builtinImport == PNone.NO_VALUE) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, IMPORT_NOT_FOUND);
        }
        assert builtinImport instanceof PMethod || builtinImport instanceof PFunction;
        return CallNode.getUncached().execute(builtinImport, name, PNone.NONE, PNone.NONE, PythonObjectFactory.getUncached().createTuple(fromList), 0);
    }

    @Child ImportName importNameNode;

    protected Object importModule(VirtualFrame frame, String name, Object globals, String[] fromList, int level) {
        // Look up built-in modules supported by GraalPython
        PythonContext context = getContext();
        Python3Core core = getContext().getCore();
        if (!core.isInitialized()) {
            PythonModule builtinModule = context.getCore().lookupBuiltinModule(name);
            if (builtinModule != null) {
                return builtinModule;
            }
        }
        if (importNameNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            importNameNode = insert(ImportNameNodeGen.create());
        }
        if (emulateJython()) {
            if (fromList.length > 0) {
                context.pushCurrentImport(PString.cat(name, ".", fromList[0]));
            } else {
                context.pushCurrentImport(name);
            }
        }
        try {
            return importNameNode.execute(frame, context, core.getBuiltins(), name, globals, fromList, level);
        } finally {
            if (emulateJython()) {
                context.popCurrentImport();
            }
        }
    }

    /**
     * Equivalent to CPython's import_name. We also pass the builtins module in, because we ignore what it's set to in the frame and globals.
     */
    abstract static class ImportName extends Node {
        protected abstract Object execute(VirtualFrame frame, PythonContext context, PythonModule builtins, String name, Object globals, String[] fromList, int level);

        @Specialization(limit = "1")
        static Object importName(VirtualFrame frame, PythonContext context, PythonModule builtins, String name, Object globals, String[] fromList, int level,
                        @CachedLibrary("builtins") DynamicObjectLibrary builtinsDylib,
                        @Cached PConstructAndRaiseNode raiseNode,
                        @Cached CallNode importCallNode,
                        @Cached GetDictNode getDictNode,
                        @Cached PythonObjectFactory factory,
                        @Cached ImportModuleLevelObject importModuleLevel) {
            Object importFunc = builtinsDylib.getOrDefault(builtins, __IMPORT__, null);
            if (importFunc == null) {
                throw raiseNode.raiseImportError(frame, IMPORT_NOT_FOUND);
            }
            if (context.importFunc() != importFunc) {
                Object globalsArg;
                if (globals instanceof PNone) {
                    globalsArg = globals;
                } else {
                    globalsArg = getDictNode.execute(globals);
                }
                return importCallNode.execute(frame, importFunc, name, globalsArg, PNone.NONE, factory.createTuple(fromList), level);
            }
            return importModuleLevel.execute(frame, context, name, globals, fromList, level);
        }
    }

    /**
     * Equivalent of PyImport_ImportModuleLevelObject
     */
    abstract static class ImportModuleLevelObject extends Node {
        protected abstract Object execute(VirtualFrame frame, PythonContext context, String name, Object globals, String[] fromList, int level);

        @SuppressWarnings("unused")
        @Specialization(guards = "level < 0")
        Object levelLtZero(VirtualFrame frame, PythonContext context, String name, Object globals, String[] fromList, int level) {
            throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "level must be >= 0");
        }

        @Specialization(guards = {"level == 0", "fromList.length == 0"})
        static Object levelZeroNoFromlist(VirtualFrame frame, PythonContext context, String name, @SuppressWarnings("unused") Object globals, @SuppressWarnings("unused") String[] fromList, @SuppressWarnings("unused") int level,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyDictGetItem getModuleNode,
                        @Cached EnsureInitializedNode ensureInitialized,
                        @Cached FindAndLoad findAndLoad) {
            final String absName = name;
            if (name.length() == 0) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, "Empty module name");
            }
            PDict sysModules = context.getSysModules();
            assert sysModules != null : "sysModules is null?!";
            Object mod = getModuleNode.execute(frame, sysModules, absName); // import_get_module
            if (mod != null && mod != PNone.NONE) {
                ensureInitialized.execute(frame, context, mod, absName);
            } else {
                mod = findAndLoad.execute(frame, context, absName);
            }
            int dotIndex = name.indexOf('.');
            if (dotIndex == -1) {
                return mod;
            }
            String front = name.substring(0, dotIndex);
            // recursion up number of dots in the name
            return levelZeroNoFromlist(frame, context, front, null, null, 0,
                            raiseNode, // raiseNode only needed if front.length() == 0 at this point
                            getModuleNode, // used multiple times to get the 'front' module
                            ensureInitialized,  // used multiple times on the 'front' module
                            findAndLoad); // used multiple times, but always to call the exact same function
        }

        @Specialization(guards = "level >= 0", replaces = "levelZeroNoFromlist")
        static Object genericImport(VirtualFrame frame, PythonContext context, String name, Object globals, String[] fromList, int level,
                        @Cached ResolveName resolveName,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyDictGetItem getModuleNode,
                        @Cached EnsureInitializedNode ensureInitialized,
                        @Cached PyObjectLookupAttr getPathNode,
                        @Cached PyObjectCallMethodObjArgs callHandleFromlist,
                        @Cached PythonObjectFactory factory,
                        @Cached FindAndLoad findAndLoad) {
            String absName;
            if (level > 0) {
                absName = resolveName.execute(frame, name, globals, level);
            } else {
                if (name.length() == 0) {
                    throw raiseNode.raise(PythonBuiltinClassType.ValueError, "Empty module name");
                }
                absName = name;
            }
            PDict sysModules = context.getSysModules();
            Object mod = getModuleNode.execute(frame, sysModules, absName); // import_get_module
            if (mod != null && mod != PNone.NONE) {
                ensureInitialized.execute(frame, context, mod, absName);
            } else {
                mod = findAndLoad.execute(frame, context, absName);
            }
            if (fromList.length == 0) {
                int nameLength = name.length();
                if (level == 0 || nameLength > 0) {
                    int dotIndex = name.indexOf('.');
                    if (dotIndex == -1) {
                        return mod;
                    }
                    if (level == 0) {
                        String front = name.substring(0, dotIndex);
                        // recursion up number of dots in the name
                        return levelZeroNoFromlist(frame, context, front, null, null, 0,
                                        raiseNode, // raiseNode only needed if front.length() == 0 at this point
                                        getModuleNode, // used multiple times to get the 'front' module
                                        ensureInitialized,  // used multiple times on the 'front' module
                                        findAndLoad); // used multiple times, but always to call the exact same function
                    } else {
                        int cutoff = nameLength - dotIndex;
                        String toReturn = absName.substring(0, absName.length() - cutoff);
                        Object finalModule = getModuleNode.execute(frame, sysModules, toReturn); // import_get_module
                        if (finalModule == null) {
                            throw raiseNode.raise(PythonBuiltinClassType.KeyError, "'%s' not in sys.modules as expected", toReturn);
                        }
                        return finalModule;
                    }
                } else {
                    return mod;
                }
            } else {
                Object path = getPathNode.execute(frame, mod, SpecialAttributeNames.__PATH__);
                if (path != PNone.NO_VALUE) {
                    return callHandleFromlist.execute(frame, context.getImportlib(), "_handle_fromlist",
                                    mod,
                                    factory.createTuple(fromList),
                                    context.importFunc());
                } else {
                    return mod;
                }
            }
        }
    }

    /**
     * Equivalent of CPython's import_ensure_initialized
     */
    static abstract class EnsureInitializedNode extends Node {
        protected abstract void execute(VirtualFrame frame, PythonContext context, Object mod, String name);

        protected static GetFixedAttributeNode getSpecNode() {
            return GetFixedAttributeNode.create(SpecialAttributeNames.__SPEC__);
        }

        protected static GetFixedAttributeNode getInitializingNode() {
            return GetFixedAttributeNode.create("_initializing");
        }

        protected static GetFixedAttributeNode getLockUnlockModuleNode() {
            return GetFixedAttributeNode.create("_lock_unlock_module");
        }

        @Specialization
        static void ensureInitialized(VirtualFrame frame, PythonContext context, Object mod, String name,
                        @Cached PyObjectGetAttr getSpecNode,
                        @Cached PyObjectGetAttr getInitNode,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PyObjectCallMethodObjArgs callLockUnlock) {
            boolean isInitializing = false;
            try {
                Object spec = getSpecNode.execute(frame, mod, SpecialAttributeNames.__SPEC__);
                Object initializing = getInitNode.execute(frame, spec, "_initializing");
                isInitializing = isTrue.execute(frame, initializing);
            } catch (PException e) {
                // _PyModuleSpec_IsInitializing clears any error that happens during getting the __spec__ or _initializing attributes
                return;
            }
            if (isInitializing) {
                callLockUnlock.execute(frame, context.getImportlib(), "_lock_unlock_module", name); // blocks until done
            }
        }
    }

    /**
     * Equivalent of resolve_name in CPython's import.c
     */
    abstract static class ResolveName extends Node {
        private static final byte PKG_IS_HERE = 0b1;
        private static final byte PKG_IS_NULL = 0b01;
        private static final byte SPEC_IS_STH = 0b001;
        private static final byte NO_SPEC_PKG = 0b0001;
        @CompilationFinal private byte branchStates = 0;

        abstract String execute(VirtualFrame frame, String name, Object globals, int level);

        @Specialization
        String resolveName(VirtualFrame frame, String name, Object globals, int level,
                        @Cached GetDictNode getDictNode,
                        @Cached PyDictGetItem getPackageOrNameNode,
                        @Cached PyDictGetItem getSpecNode,
                        @Cached CastToJavaStringNode castPackageNode) {
            PDict globalsDict = getDictNode.execute(globals);
            Object pkg = getPackageOrNameNode.execute(frame, globalsDict, SpecialAttributeNames.__PACKAGE__);
            Object spec = getSpecNode.execute(frame, globalsDict, SpecialAttributeNames.__SPEC__);
            String pkgString;
            if (pkg == PNone.NONE) {
                pkg = null;
            }
            if (pkg != null) {
                if ((branchStates & PKG_IS_HERE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates |= PKG_IS_HERE;
                }
                try {
                    pkgString = castPackageNode.execute(pkg);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "package must be a string");
                }
                if (spec != null && spec != PNone.NONE) {
                    if ((branchStates & SPEC_IS_STH) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates |= SPEC_IS_STH;
                    }
                    // TODO
                    // parent = _PyObject_GetAttrId(spec, &PyId_parent);
                    // equal = PyObject_RichCompareBool(package, parent, Py_EQ);
                    // if (equal == 0) { PyErr_WarnEx(PyExc_ImportWarning, "__package__ != __spec__.parent", 1) }
                }
            } else if (spec != null && spec != PNone.NONE) {
                if ((branchStates & PKG_IS_NULL) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates |= PKG_IS_NULL;
                }
                // TODO
                // pkg = _PyObject_GetAttrId(spec, &PyId_parent);
                try {
                    pkgString = castPackageNode.execute(pkg);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "__spec__.parent must be a string");
                }
            } else {
                if ((branchStates & NO_SPEC_PKG) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates |= NO_SPEC_PKG;
                }
                // TODO
                // PyErr_WarnEx(PyExc_ImportWarning,
                // "can't resolve package from __spec__ or __package__, "
                // "falling back on __name__ and __path__", 1)

                // (tfel): we use the byte field to cut off this branch unless needed, and for
                // footprint when use the same node for __package__, __name__, and __path__ lookup
                pkg = getPackageOrNameNode.execute(frame, globalsDict, SpecialAttributeNames.__NAME__);
                if (pkg == null) {
                    PRaiseNode.raiseUncached(this, PythonBuiltinClassType.KeyError, "'__name__' not in globals");
                }
                try {
                    pkgString = castPackageNode.execute(pkg);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "__name__ must be a string");
                }
                Object path = getPackageOrNameNode.execute(frame, globalsDict, SpecialAttributeNames.__PATH__);
                if (path == null) {
                    int dotIdx = pkgString.indexOf('.');
                    if (dotIdx == -1) {
                        throw noParentError(frame);
                    }
                    pkgString = pkgString.substring(0, dotIdx);
                }
            }

            int lastDotIdx = pkgString.length();
            if (lastDotIdx == 0) {
                throw noParentError(frame);
            }

            for (int levelUp = 1; levelUp < level; levelUp += 1) {
                lastDotIdx = pkgString.lastIndexOf('.', lastDotIdx);
                if (lastDotIdx == -1) {
                    throw PConstructAndRaiseNode.getUncached().raiseImportError(frame, "attempted relative import beyond top-level package");
                }
            }

            String base = pkgString.substring(0, lastDotIdx);
            if (name.length() == 0) {
                return base;
            }

            String absName = base + "." + name;
            return absName;
        }

        private static final RuntimeException noParentError(VirtualFrame frame) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(frame, "attempted relative import with no known parent package");
        }
    }

    /**
     * Equivalent of import_find_and_load
     */
    abstract static class FindAndLoad extends Node {
        protected abstract Object execute(VirtualFrame frame, PythonContext context, String absName);

        @Specialization
        static Object findAndLoad(VirtualFrame frame, PythonContext context, String absName,
                        // TODO: (tfel) audit and import timing
                        // @Cached ReadAttributeFromDynamicObjectNode readPath,
                        // @Cached ReadAttributeFromDynamicObjectNode readMetaPath,
                        // @Cached ReadAttributeFromDynamicObjectNode readPathHooks,
                        // @Cached AuditNode audit,
                        @Cached PyObjectCallMethodObjArgs callFindAndLoad) {
            // TODO: (tfel) audit and import timing
            // PythonModule sys = context.getSysModule();
            // Object sysPath = readPath.execute(sys, "path");
            // Object sysMetaPath = readPath.execute(sys, "meta_path");
            // Object sysPathHooks = readPath.execute(sys, "path_hooks");
            // audit.execute("import", new Object[] {
            //                     absName,
            //                     PNone.NONE,
            //                     sysPath == PNone.NO_VALUE ? PNone.NONE : sysPath,
            //                     sysMetaPath == PNone.NO_VALUE ? PNone.NONE : sysMetaPath,
            //                     sysPathHooks == PNone.NO_VALUE ? PNone.NONE : sysPathHooks);
            return callFindAndLoad.execute(frame, context.getImportlib(), "_find_and_load", absName, context.importFunc());
        }
    }

    protected boolean emulateJython() {
        return getPythonLanguage().getEngineOption(PythonOptions.EmulateJython);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.CallTag.class || super.hasTag(tag);
    }
}
