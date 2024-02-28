/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.module.ModuleBuiltins.T__INITIALIZING;
import static com.oracle.graal.python.nodes.BuiltinNames.T___IMPORT__;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTEMPTED_RELATIVE_IMPORT_BEYOND_TOPLEVEL;
import static com.oracle.graal.python.nodes.ErrorMessages.IMPORT_NOT_FOUND;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.graal.python.util.PythonUtils.tsbCapacity;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyFrameGetBuiltins;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetDictFromGlobalsNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNodeFactory.ImportNameNodeGen;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
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
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class AbstractImportNode extends PNodeWithContext {

    @CompilationFinal(dimensions = 1) public static final TruffleString[] T_IMPORT_ALL = tsArray("*");
    public static final TruffleString T__FIND_AND_LOAD = tsLiteral("_find_and_load");

    public static PythonModule importModule(TruffleString name) {
        return importModule(name, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
    }

    public static PythonModule importModule(TruffleString name, TruffleString[] fromList) {
        return importModule(name, PythonObjectFactory.getUncached().createTuple(fromList), 0);
    }

    @TruffleBoundary
    public static PythonModule importModule(TruffleString name, Object[] fromList, Object level) {
        return importModule(name, PythonObjectFactory.getUncached().createTuple(fromList), level);
    }

    @TruffleBoundary
    public static PythonModule importModule(TruffleString name, Object fromList, Object level) {
        Object builtinImport = PyFrameGetBuiltins.executeUncached().getAttribute(T___IMPORT__);
        if (builtinImport == PNone.NO_VALUE) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, IMPORT_NOT_FOUND);
        }
        assert builtinImport instanceof PMethod || builtinImport instanceof PFunction;
        Object module = CallNode.getUncached().execute(builtinImport, name, PNone.NONE, PNone.NONE, fromList, level);
        if (module instanceof PythonModule pythonModule) {
            return pythonModule;
        }
        transferToInterpreter();
        throw shouldNotReachHere("__import__ returned " + module.getClass() + " instead of PythonModule");
    }

    @TruffleBoundary
    public static Object importModule(PythonContext context, TruffleString name, TruffleString[] fromList, int level) {
        return ImportNameNodeGen.getUncached().execute(null, context, PyFrameGetBuiltins.execute(context), name, PNone.NONE, fromList, level);
    }

    protected final Object importModule(VirtualFrame frame, TruffleString name, Object globals, TruffleString[] fromList, int level, ImportName importNameNode) {
        // Look up built-in modules supported by GraalPython
        PythonContext context = getContext();
        if (!context.isInitialized()) {
            PythonModule builtinModule = context.lookupBuiltinModule(name);
            if (builtinModule != null) {
                return builtinModule;
            }
        }
        if (emulateJython()) {
            if (fromList.length > 0) {
                context.pushCurrentImport(StringUtils.cat(name, T_DOT, fromList[0]));
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
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 29
    public abstract static class ImportName extends Node {
        public abstract Object execute(Frame frame, PythonContext context, PythonModule builtins, TruffleString name, Object globals, TruffleString[] fromList, int level);

        @Specialization(limit = "1")
        static Object importName(VirtualFrame frame, PythonContext context, PythonModule builtins, TruffleString name, Object globals, TruffleString[] fromList, int level,
                        @CachedLibrary("builtins") DynamicObjectLibrary builtinsDylib,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile importFuncProfile,
                        @Cached PConstructAndRaiseNode.Lazy raiseNode,
                        @Cached CallNode importCallNode,
                        @Cached GetDictFromGlobalsNode getDictNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PyImportImportModuleLevelObject importModuleLevel) {
            Object importFunc = builtinsDylib.getOrDefault(builtins, T___IMPORT__, null);
            if (importFunc == null) {
                throw raiseNode.get(inliningTarget).raiseImportError(frame, IMPORT_NOT_FOUND);
            }
            if (importFuncProfile.profile(inliningTarget, context.importFunc() != importFunc)) {
                Object globalsArg;
                if (globals instanceof PNone) {
                    globalsArg = globals;
                } else {
                    globalsArg = getDictNode.execute(inliningTarget, globals);
                }
                return importCallNode.execute(frame, importFunc, name, globalsArg, PNone.NONE, factory.createTuple(fromList), level);
            }
            return importModuleLevel.execute(frame, context, name, globals, fromList, level);
        }

        public static ImportName getUncached() {
            return ImportNameNodeGen.getUncached();
        }
    }

    /**
     * Equivalent of PyImport_ImportModuleLevelObject
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 68 -> 51
    public abstract static class PyImportImportModuleLevelObject extends Node {
        public static final TruffleString T__HANDLE_FROMLIST = tsLiteral("_handle_fromlist");

        public abstract Object execute(Frame frame, PythonContext context, TruffleString name, Object globals, TruffleString[] fromList, int level);

        @SuppressWarnings("unused")
        @Specialization(guards = "level < 0")
        Object levelLtZero(VirtualFrame frame, PythonContext context, TruffleString name, Object globals, TruffleString[] fromList, int level) {
            throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, ErrorMessages.LEVEL_MUST_BE_AT_LEAST_ZERO);
        }

        protected static boolean containsDot(TruffleString name, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.IndexOfCodePointNode indexOfCodePointNode) {
            return indexOfCodePointNode.execute(name, '.', 0, codePointLengthNode.execute(name, TS_ENCODING), TS_ENCODING) >= 0;
        }

        @Specialization(guards = {"level == 0", "fromList.length == 0", "!containsDot(name, codePointLengthNode, indexOfCodePointNode)"}, limit = "1")
        public static Object levelZeroNoFromlist(VirtualFrame frame, PythonContext context, TruffleString name, @SuppressWarnings("unused") Object globals,
                        @SuppressWarnings("unused") TruffleString[] fromList,
                        @SuppressWarnings("unused") int level,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Exclusive @Cached PyDictGetItem getModuleNode,
                        @Exclusive @Cached EnsureInitializedNode ensureInitialized,
                        @Exclusive @Cached FindAndLoad findAndLoad,
                        @Exclusive @Cached @SuppressWarnings("unused") TruffleString.CodePointLengthNode codePointLengthNode,
                        @Exclusive @Cached @SuppressWarnings("unused") TruffleString.IndexOfCodePointNode indexOfCodePointNode) {
            final TruffleString absName = name;
            if (name.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.EMPTY_MOD_NAME);
            }
            PDict sysModules = context.getSysModules();
            Object mod = getModuleNode.execute(frame, inliningTarget, sysModules, absName); // import_get_module
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
            private TruffleString front;

            private ModuleFront(TruffleString front) {
                this.front = front;
            }
        }

        @Specialization(guards = "level >= 0", replaces = "levelZeroNoFromlist")
        static Object genericImport(VirtualFrame frame, PythonContext context, TruffleString name, Object globals, TruffleString[] fromList, int level,
                        @Bind("this") Node inliningTarget,
                        @Cached ResolveName resolveName,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Exclusive @Cached PyDictGetItem getModuleNode,
                        @Exclusive @Cached EnsureInitializedNode ensureInitialized,
                        @Cached PyObjectLookupAttr getPathNode,
                        @Cached PyObjectCallMethodObjArgs callHandleFromlist,
                        @Cached PythonObjectFactory factory,
                        @Exclusive @Cached FindAndLoad findAndLoad,
                        @Cached InlinedConditionProfile recursiveCase,
                        @Exclusive @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Exclusive @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            TruffleString absName;
            if (level > 0) {
                absName = resolveName.execute(frame, name, globals, level);
            } else {
                if (name.isEmpty()) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.EMPTY_MOD_NAME);
                }
                absName = name;
            }
            PDict sysModules = context.getSysModules();
            Object mod = getModuleNode.execute(frame, inliningTarget, sysModules, absName); // import_get_module
            if (mod != null && mod != PNone.NONE) {
                ensureInitialized.execute(frame, context, mod, absName);
            } else {
                mod = findAndLoad.execute(frame, context, absName);
            }
            if (fromList.length == 0) {
                int nameLength = codePointLengthNode.execute(name, TS_ENCODING);
                if (level == 0 || nameLength > 0) {
                    int dotIndex = indexOfCodePointNode.execute(name, '.', 0, nameLength, TS_ENCODING);
                    if (dotIndex < 0) {
                        return mod;
                    }
                    if (level == 0) {
                        Object front = new ModuleFront(substringNode.execute(name, 0, dotIndex, TS_ENCODING, true));
                        // cpython recurses, we have transformed the recursion into a loop
                        do {
                            // we omit a few arguments in the recursion, because that makes things
                            // simpler.
                            // globals are null, fromlist is empty, level is 0
                            front = genericImportRecursion(frame, inliningTarget, context, (ModuleFront) front,
                                            raiseNode, // raiseNode only needed if front.length() ==
                                                       // 0 at this point
                                            getModuleNode, // used multiple times to get the 'front'
                                                           // module
                                            ensureInitialized,  // used multiple times on the
                                                                // 'front' module
                                            findAndLoad,  // used multiple times, but always to call
                                                          // the exact same function
                                            codePointLengthNode, indexOfCodePointNode, substringNode);
                        } while (recursiveCase.profile(inliningTarget, front instanceof ModuleFront));
                        return front;
                    } else {
                        int cutoff = nameLength - dotIndex;
                        TruffleString toReturn = substringNode.execute(absName, 0, codePointLengthNode.execute(absName, TS_ENCODING) - cutoff, TS_ENCODING, true);
                        Object finalModule = getModuleNode.execute(frame, inliningTarget, sysModules, toReturn); // import_get_module
                        if (finalModule == null) {
                            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.KeyError, ErrorMessages.S_NOT_IN_SYS_MODS, toReturn);
                        }
                        return finalModule;
                    }
                } else {
                    return mod;
                }
            } else {
                Object path = getPathNode.execute(frame, inliningTarget, mod, SpecialAttributeNames.T___PATH__);
                if (path != PNone.NO_VALUE) {
                    return callHandleFromlist.execute(frame, inliningTarget, context.getImportlib(), T__HANDLE_FROMLIST,
                                    mod,
                                    factory.createTuple(fromList),
                                    context.importFunc());
                } else {
                    return mod;
                }
            }
        }

        static Object genericImportRecursion(VirtualFrame frame, Node inliningTarget, PythonContext context, ModuleFront front,
                        PRaiseNode.Lazy raiseNode,
                        PyDictGetItem getModuleNode,
                        EnsureInitializedNode ensureInitialized,
                        FindAndLoad findAndLoad,
                        TruffleString.CodePointLengthNode codePointLengthNode,
                        TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        TruffleString.SubstringNode substringNode) {
            TruffleString absName = front.front;
            if (absName.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.EMPTY_MOD_NAME);
            }
            PDict sysModules = context.getSysModules();
            Object mod = getModuleNode.execute(frame, inliningTarget, sysModules, absName); // import_get_module
            if (mod != null && mod != PNone.NONE) {
                ensureInitialized.execute(frame, context, mod, absName);
            } else {
                mod = findAndLoad.execute(frame, context, absName);
            }
            // fromList.length == 0
            // level == 0
            int dotIndex = indexOfCodePointNode.execute(absName, '.', 0, codePointLengthNode.execute(absName, TS_ENCODING), TS_ENCODING);
            if (dotIndex < 0) {
                return mod;
            }
            // level == 0
            front.front = substringNode.execute(absName, 0, dotIndex, TS_ENCODING, true);
            return front;
        }
    }

    /**
     * Equivalent of CPython's PyModuleSpec_IsInitializing, but for convenience it takes the module,
     * not the spec.
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 124 -> 105
    public abstract static class PyModuleIsInitializing extends Node {
        public abstract boolean execute(Frame frame, Object mod);

        @Specialization
        static boolean isInitializing(VirtualFrame frame, Object mod,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasSpec,
                        @Cached PyObjectLookupAttr getSpecNode,
                        @Cached PyObjectLookupAttr getInitNode,
                        // CPython uses PyObject_GetAttr, but ignores the exception here
                        @Cached PyObjectIsTrueNode isTrue) {
            try {
                Object spec = getSpecNode.execute(frame, inliningTarget, mod, SpecialAttributeNames.T___SPEC__);
                if (hasSpec.profile(inliningTarget, spec != PNone.NO_VALUE)) {
                    Object initializing = getInitNode.execute(frame, inliningTarget, spec, T__INITIALIZING);
                    return isTrue.execute(frame, inliningTarget, initializing);
                } else {
                    return false;
                }
            } catch (PException e) {
                // _PyModuleSpec_IsInitializing clears any error that happens during getting the
                // __spec__ or _initializing attributes
                return false;
            }
        }

        public static PyModuleIsInitializing getUncached() {
            return AbstractImportNodeFactory.PyModuleIsInitializingNodeGen.getUncached();
        }
    }

    /**
     * Equivalent of CPython's import_ensure_initialized
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 88 -> 72
    abstract static class EnsureInitializedNode extends Node {

        public static final TruffleString T_LOCK_UNLOCK_MODULE = tsLiteral("_lock_unlock_module");

        protected abstract void execute(Frame frame, PythonContext context, Object mod, TruffleString name);

        @Specialization
        static void ensureInitialized(VirtualFrame frame, PythonContext context, Object mod, TruffleString name,
                        @Bind("this") Node inliningTarget,
                        @Cached PyModuleIsInitializing isInitializing,
                        @Cached PyObjectCallMethodObjArgs callLockUnlock) {
            if (isInitializing.execute(frame, mod)) {
                callLockUnlock.execute(frame, inliningTarget, context.getImportlib(), T_LOCK_UNLOCK_MODULE, name);
                // blocks until done
            }
        }
    }

    /**
     * Equivalent of resolve_name in CPython's import.c
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 124 -> 106
    abstract static class ResolveName extends Node {
        private static final byte PKG_IS_HERE = 0b1;
        private static final byte PKG_IS_NULL = 0b01;
        private static final byte SPEC_IS_STH = 0b001;
        private static final byte NO_SPEC_PKG = 0b0001;
        private static final byte CANNOT_CAST = 0b00001;
        private static final byte GOT_NO_NAME = 0b000001;
        public static final TruffleString T_PARENT = tsLiteral("parent");

        protected static byte[] uncachedByte() {
            return new byte[]{Byte.MIN_VALUE};
        }

        @NeverDefault
        protected static byte[] singleByte() {
            return new byte[1];
        }

        abstract TruffleString execute(Frame frame, TruffleString name, Object globals, int level);

        @Specialization
        TruffleString resolveName(VirtualFrame frame, TruffleString name, Object globals, int level,
                        @Bind("this") Node inliningTarget,
                        @Cached GetDictFromGlobalsNode getDictNode,
                        @Cached PyDictGetItem getPackageOrNameNode,
                        @Cached PyDictGetItem getSpecNode,
                        @Cached PyObjectGetAttr getParent,
                        @Cached CastToTruffleStringNode castPackageNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.LastIndexOfCodePointNode lastIndexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached(value = "singleByte()", uncached = "uncachedByte()", dimensions = 1) byte[] branchStates) {
            PDict globalsDict = getDictNode.execute(inliningTarget, globals);
            Object pkg = getPackageOrNameNode.execute(frame, inliningTarget, globalsDict, SpecialAttributeNames.T___PACKAGE__);
            Object spec = getSpecNode.execute(frame, inliningTarget, globalsDict, SpecialAttributeNames.T___SPEC__);
            TruffleString pkgString;
            if (pkg == PNone.NONE) {
                pkg = null;
            }
            if (pkg != null) {
                if ((branchStates[0] & PKG_IS_HERE) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates[0] |= PKG_IS_HERE;
                }
                try {
                    pkgString = castPackageNode.execute(inliningTarget, pkg);
                } catch (CannotCastException e) {
                    if ((branchStates[0] & CANNOT_CAST) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates[0] |= CANNOT_CAST;
                    }
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, ErrorMessages.PACKAGE_MUST_BE_A_STRING);
                }
                if (spec != null && spec != PNone.NONE) {
                    if ((branchStates[0] & SPEC_IS_STH) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates[0] |= SPEC_IS_STH;
                    }
                    // TODO: emit warning
                    // Object parent = getParent.execute(frame, spec, "parent");
                    // equal = PyObject_RichCompareBool(package, parent, Py_EQ);
                    // if (equal == 0) { PyErr_WarnEx(PyExc_ImportWarning, "__package__ !=
                    // __spec__.parent", 1) }
                }
            } else if (spec != null && spec != PNone.NONE) {
                if ((branchStates[0] & PKG_IS_NULL) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates[0] |= PKG_IS_NULL;
                }
                pkg = getParent.execute(frame, inliningTarget, spec, T_PARENT);
                try {
                    pkgString = castPackageNode.execute(inliningTarget, pkg);
                } catch (CannotCastException e) {
                    if ((branchStates[0] & CANNOT_CAST) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates[0] |= CANNOT_CAST;
                    }
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, ErrorMessages.SPEC_PARENT_MUST_BE_A_STRING);
                }
            } else {
                if ((branchStates[0] & NO_SPEC_PKG) == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    branchStates[0] |= NO_SPEC_PKG;
                }
                // TODO: emit warning
                // PyErr_WarnEx(PyExc_ImportWarning,
                // "can't resolve package from __spec__ or __package__, "
                // "falling back on __name__ and __path__", 1)

                // (tfel): we use the byte field to cut off this branch unless needed, and for
                // footprint when use the same node for __package__, __name__, and __path__ lookup
                pkg = getPackageOrNameNode.execute(frame, inliningTarget, globalsDict, SpecialAttributeNames.T___NAME__);
                if (pkg == null) {
                    if ((branchStates[0] & GOT_NO_NAME) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates[0] |= GOT_NO_NAME;
                    }
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.KeyError, ErrorMessages.NAME_NOT_IN_GLOBALS);
                }
                try {
                    pkgString = castPackageNode.execute(inliningTarget, pkg);
                } catch (CannotCastException e) {
                    if ((branchStates[0] & CANNOT_CAST) == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        branchStates[0] |= CANNOT_CAST;
                    }
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, ErrorMessages.NAME_MUST_BE_A_STRING);
                }
                Object path = getPackageOrNameNode.execute(frame, inliningTarget, globalsDict, SpecialAttributeNames.T___PATH__);
                if (path == null) {
                    int dotIdx = indexOfCodePointNode.execute(pkgString, '.', 0, codePointLengthNode.execute(pkgString, TS_ENCODING), TS_ENCODING);
                    if (dotIdx < 0) {
                        throw noParentError(frame);
                    }
                    pkgString = substringNode.execute(pkgString, 0, dotIdx, TS_ENCODING, true);
                }
            }

            int lastDotIdx = codePointLengthNode.execute(pkgString, TS_ENCODING);
            if (lastDotIdx == 0) {
                throw noParentError(frame);
            }

            for (int levelUp = 1; levelUp < level; levelUp += 1) {
                lastDotIdx = lastIndexOfCodePointNode.execute(pkgString, '.', lastDotIdx, 0, TS_ENCODING);
                if (lastDotIdx < 0) {
                    throw PConstructAndRaiseNode.getUncached().raiseImportError(frame, ATTEMPTED_RELATIVE_IMPORT_BEYOND_TOPLEVEL);
                }
            }

            TruffleString base = substringNode.execute(pkgString, 0, lastDotIdx, TS_ENCODING, true);
            if (name.isEmpty()) {
                return base;
            }

            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, base.byteLength(TS_ENCODING) + tsbCapacity(1) + name.byteLength(TS_ENCODING));
            appendStringNode.execute(sb, base);
            appendStringNode.execute(sb, T_DOT);
            appendStringNode.execute(sb, name);
            return toStringNode.execute(sb);
        }

        private static RuntimeException noParentError(VirtualFrame frame) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(frame, ATTEMPTED_RELATIVE_IMPORT_BEYOND_TOPLEVEL);
        }
    }

    /**
     * Equivalent of import_find_and_load
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 84 -> 68
    abstract static class FindAndLoad extends Node {
        protected abstract Object execute(Frame frame, PythonContext context, TruffleString absName);

        @Specialization
        static Object findAndLoad(VirtualFrame frame, PythonContext context, TruffleString absName,
                        // TODO: (tfel) audit and import timing
                        // @Cached ReadAttributeFromDynamicObjectNode readPath,
                        // @Cached ReadAttributeFromDynamicObjectNode readMetaPath,
                        // @Cached ReadAttributeFromDynamicObjectNode readPathHooks,
                        // @Cached AuditNode audit,
                        @Bind("this") Node inliningTarget,
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
            return callFindAndLoad.execute(frame, inliningTarget, context.getImportlib(), T__FIND_AND_LOAD, absName, context.importFunc());
        }
    }

    protected boolean emulateJython() {
        return PythonLanguage.get(this).getEngineOption(PythonOptions.EmulateJython);
    }
}
