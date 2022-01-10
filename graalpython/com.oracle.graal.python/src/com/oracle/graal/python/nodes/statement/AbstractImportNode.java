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
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
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
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetDictFromGlobalsNode;
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
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class AbstractImportNode extends StatementNode {

    @CompilationFinal(dimensions = 1) public static final String[] IMPORT_ALL = new String[]{"*"};
    
    @Child ImportName importNameNode;

    public AbstractImportNode() {
        super();
    }

    private PythonLanguage getPythonLanguage() {
        return PythonLanguage.get(this);
    }

    protected final Object importModule(VirtualFrame frame, String name) {
        return importModule(frame, name, PNone.NONE, PythonUtils.EMPTY_STRING_ARRAY, 0);
    }

    public static final Object importModule(String name) {
        return importModule(name, PythonUtils.EMPTY_STRING_ARRAY);
    }

    @TruffleBoundary
    public static final Object importModule(String name, String[] fromList) {
        Object builtinImport = PyFrameGetBuiltins.getUncached().execute().getAttribute(__IMPORT__);
        if (builtinImport == PNone.NO_VALUE) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, IMPORT_NOT_FOUND);
        }
        assert builtinImport instanceof PMethod || builtinImport instanceof PFunction;
        return CallNode.getUncached().execute(builtinImport, name, PNone.NONE, PNone.NONE, PythonObjectFactory.getUncached().createTuple(fromList), 0);
    }

    protected final Object importModule(VirtualFrame frame, String name, Object globals, String[] fromList, int level) {
        // Look up built-in modules supported by GraalPython
        PythonContext context = getContext();
        if (!context.isInitialized()) {
            PythonModule builtinModule = context.lookupBuiltinModule(name);
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
            return importNameNode.execute(frame, context, context.getBuiltins(), name, globals, fromList, level);
        } finally {
            if (emulateJython()) {
                context.popCurrentImport();
            }
        }
    }

    /**
     * Equivalent to CPython's import_name. We also pass the builtins module in, because we ignore
     * what it's set to in the frame and globals.
     */
    abstract static class ImportName extends Node {
        protected abstract Object execute(VirtualFrame frame, PythonContext context, PythonModule builtins, String name, Object globals, String[] fromList, int level);

        @Specialization(limit = "1")
        static Object importName(VirtualFrame frame, PythonContext context, PythonModule builtins, String name, Object globals, String[] fromList, int level,
                        @CachedLibrary("builtins") DynamicObjectLibrary builtinsDylib,
                        @Cached PConstructAndRaiseNode raiseNode,
                        @Cached CallNode importCallNode,
                        @Cached GetDictFromGlobalsNode getDictNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PyImportImportModuleLevelObject importModuleLevel) {
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
    public abstract static class PyImportImportModuleLevelObject extends Node {
        public abstract Object execute(VirtualFrame frame, PythonContext context, String name, Object globals, String[] fromList, int level);

        @SuppressWarnings("unused")
        @Specialization(guards = "level < 0")
        Object levelLtZero(VirtualFrame frame, PythonContext context, String name, Object globals, String[] fromList, int level) {
            throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "level must be >= 0");
        }

        protected static final int indexOfDot(String name) {
            return name.indexOf('.');
        }

        @Specialization(guards = {"level == 0", "fromList.length == 0", "dotIndex < 0"})
        public static Object levelZeroNoFromlist(VirtualFrame frame, PythonContext context, String name, @SuppressWarnings("unused") Object globals, @SuppressWarnings("unused") String[] fromList,
                        @SuppressWarnings("unused") int level,
                        @SuppressWarnings("unused") @Bind("indexOfDot(name)") int dotIndex,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyDictGetItem getModuleNode,
                        @Cached EnsureInitializedNode ensureInitialized,
                        @Cached FindAndLoad findAndLoad) {
            final String absName = name;
            if (name.length() == 0) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, "Empty module name");
            }
            PDict sysModules = context.getSysModules();
            Object mod = getModuleNode.execute(frame, sysModules, absName); // import_get_module
            if (mod != null && mod != PNone.NONE) {
                ensureInitialized.execute(frame, context, mod, absName);
            } else {
                mod = findAndLoad.execute(frame, context, absName);
            }
            // Here CPython has a check for fromlist being empty, the level being 0, and the index
            // of the first dot. All these are guards in this specialization, so we can just
            // return.
            return mod;
        }

        @ValueType
        private static final class ModuleFront {
            private String front;

            private ModuleFront(String front) {
                this.front = front;
            }
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
                        @Cached FindAndLoad findAndLoad,
                        @Cached ConditionProfile recursiveCase) {
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
                        Object front = new ModuleFront(name.substring(0, dotIndex));
                        // cpython recurses, we have transformed the recursion into a loop
                        do {
                            // we omit a few arguments in the recursion, because that makes things
                            // simpler.
                            // globals are null, fromlist is empty, level is 0
                            front = genericImportRecursion(frame, context, (ModuleFront) front,
                                            raiseNode, // raiseNode only needed if front.length() ==
                                                       // 0 at this point
                                            getModuleNode, // used multiple times to get the 'front'
                                                           // module
                                            ensureInitialized,  // used multiple times on the
                                                                // 'front' module
                                            findAndLoad); // used multiple times, but always to call
                                                          // the exact same function
                        } while (recursiveCase.profile(front instanceof ModuleFront));
                        return front;
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

        static final Object genericImportRecursion(VirtualFrame frame, PythonContext context, ModuleFront front,
                        PRaiseNode raiseNode,
                        PyDictGetItem getModuleNode,
                        EnsureInitializedNode ensureInitialized,
                        FindAndLoad findAndLoad) {
            String absName = front.front;
            if (absName.length() == 0) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, "Empty module name");
            }
            PDict sysModules = context.getSysModules();
            Object mod = getModuleNode.execute(frame, sysModules, absName); // import_get_module
            if (mod != null && mod != PNone.NONE) {
                ensureInitialized.execute(frame, context, mod, absName);
            } else {
                mod = findAndLoad.execute(frame, context, absName);
            }
            // fromList.length == 0
            // level == 0
            int dotIndex = absName.indexOf('.');
            if (dotIndex == -1) {
                return mod;
            }
            // level == 0
            front.front = absName.substring(0, dotIndex);
            return front;
        }
    }

    /**
     * Equivalent of CPython's PyModuleSpec_IsInitializing, but for convenience it takes the module,
     * not the spec.
     */
    abstract static class PyModuleIsInitializing extends Node {
        abstract boolean execute(VirtualFrame frame, Object mod);

        @Specialization
        static boolean isInitializing(VirtualFrame frame, Object mod,
                        @Cached ConditionProfile hasSpec,
                        @Cached PyObjectLookupAttr getSpecNode,
                        @Cached PyObjectLookupAttr getInitNode,
                        // CPython uses PyObject_GetAttr, but ignores the exception here
                        @Cached PyObjectIsTrueNode isTrue) {
            try {
                Object spec = getSpecNode.execute(frame, mod, SpecialAttributeNames.__SPEC__);
                if (hasSpec.profile(spec != PNone.NO_VALUE)) {
                    Object initializing = getInitNode.execute(frame, spec, "_initializing");
                    return isTrue.execute(frame, initializing);
                } else {
                    return false;
                }
            } catch (PException e) {
                // _PyModuleSpec_IsInitializing clears any error that happens during getting the
                // __spec__ or _initializing attributes
                return false;
            }
        }
    }

    /**
     * Equivalent of CPython's import_ensure_initialized
     */
    abstract static class EnsureInitializedNode extends Node {
        protected abstract void execute(VirtualFrame frame, PythonContext context, Object mod, String name);

        @Specialization
        static void ensureInitialized(VirtualFrame frame, PythonContext context, Object mod, String name,
                        @Cached PyModuleIsInitializing isInitializing,
                        @Cached PyObjectCallMethodObjArgs callLockUnlock) {
            if (isInitializing.execute(frame, mod)) {
                callLockUnlock.execute(frame, context.getImportlib(), "_lock_unlock_module", name); // blocks
                                                                                                    // until
                                                                                                    // done
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
        private static final byte CANNOT_CAST = 0b00001;
        private static final byte GOT_NO_NAME = 0b000001;
        @CompilationFinal private byte branchStates = 0;

        abstract String execute(VirtualFrame frame, String name, Object globals, int level);

        @Specialization
        String resolveName(VirtualFrame frame, String name, Object globals, int level,
                        @Cached GetDictFromGlobalsNode getDictNode,
                        @Cached PyDictGetItem getPackageOrNameNode,
                        @Cached PyDictGetItem getSpecNode,
                        @Cached PyObjectGetAttr getParent,
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
                    if ((branchStates & CANNOT_CAST) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates |= CANNOT_CAST;
                    }
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "package must be a string");
                }
                if (spec != null && spec != PNone.NONE) {
                    if ((branchStates & SPEC_IS_STH) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates |= SPEC_IS_STH;
                    }
                    // TODO: emit warning
                    // Object parent = getParent.execute(frame, spec, "parent");
                    // equal = PyObject_RichCompareBool(package, parent, Py_EQ);
                    // if (equal == 0) { PyErr_WarnEx(PyExc_ImportWarning, "__package__ !=
                    // __spec__.parent", 1) }
                }
            } else if (spec != null && spec != PNone.NONE) {
                if ((branchStates & PKG_IS_NULL) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates |= PKG_IS_NULL;
                }
                pkg = getParent.execute(frame, spec, "parent");
                try {
                    pkgString = castPackageNode.execute(pkg);
                } catch (CannotCastException e) {
                    if ((branchStates & CANNOT_CAST) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates |= CANNOT_CAST;
                    }
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, "__spec__.parent must be a string");
                }
            } else {
                if ((branchStates & NO_SPEC_PKG) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates |= NO_SPEC_PKG;
                }
                // TODO: emit warning
                // PyErr_WarnEx(PyExc_ImportWarning,
                // "can't resolve package from __spec__ or __package__, "
                // "falling back on __name__ and __path__", 1)

                // (tfel): we use the byte field to cut off this branch unless needed, and for
                // footprint when use the same node for __package__, __name__, and __path__ lookup
                pkg = getPackageOrNameNode.execute(frame, globalsDict, SpecialAttributeNames.__NAME__);
                if (pkg == null) {
                    if ((branchStates & GOT_NO_NAME) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates |= GOT_NO_NAME;
                    }
                    PRaiseNode.raiseUncached(this, PythonBuiltinClassType.KeyError, "'__name__' not in globals");
                }
                try {
                    pkgString = castPackageNode.execute(pkg);
                } catch (CannotCastException e) {
                    if ((branchStates & CANNOT_CAST) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates |= CANNOT_CAST;
                    }
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
                lastDotIdx = pkgString.lastIndexOf('.', lastDotIdx - 1);
                if (lastDotIdx == -1) {
                    throw PConstructAndRaiseNode.getUncached().raiseImportError(frame, "attempted relative import beyond top-level package");
                }
            }

            String base = pkgString.substring(0, lastDotIdx);
            if (name.length() == 0) {
                return base;
            }

            String absName = PString.cat(base, ".", name);
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
            // absName,
            // PNone.NONE,
            // sysPath == PNone.NO_VALUE ? PNone.NONE : sysPath,
            // sysMetaPath == PNone.NO_VALUE ? PNone.NONE : sysMetaPath,
            // sysPathHooks == PNone.NO_VALUE ? PNone.NONE : sysPathHooks);
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
