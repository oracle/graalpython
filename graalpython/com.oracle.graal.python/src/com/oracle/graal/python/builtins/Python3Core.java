/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.nodes.BuiltinNames.FOREIGN;
import static com.oracle.graal.python.nodes.BuiltinNames.MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.OBJECT;
import static com.oracle.graal.python.nodes.BuiltinNames.TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS_PATCHES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.ArrayModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AstModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AtexitModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CollectionsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ErrnoModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FaulthandlerModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FunctoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GcModuleBuiltins;
import com.oracle.graal.python.builtins.modules.IOModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins;
import com.oracle.graal.python.builtins.modules.InteropModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.JavaModuleBuiltins;
import com.oracle.graal.python.builtins.modules.LocaleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.RandomModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SREModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SignalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.StringModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins;
import com.oracle.graal.python.builtins.modules.UnicodeDataModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bool.BoolBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.code.CodeBuiltins;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictItemsIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictKeysIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictValuesIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictViewBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltins;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.TruffleObjectBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptorTypeBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.ForeignIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.PZipBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.SentinelIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.BufferBuiltins;
import com.oracle.graal.python.builtins.objects.method.AbstractMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.random.RandomBuiltins;
import com.oracle.graal.python.builtins.objects.range.RangeBuiltins;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.reversed.ReversedBuiltins;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * The core is intended to the immutable part of the interpreter, including most modules and most
 * types.
 *
 * TODO: It is a work-in-progress to separate out the things that cannot be shared. Until then, each
 * {@link PythonContext} has its own core.
 */
public final class Python3Core implements PythonCore {
    // Order matters!
    private static final String[] CORE_FILES = new String[]{
                    "_descriptor",
                    "object",
                    "sys",
                    "dict",
                    "_mappingproxy",
                    "str",
                    "type",
                    "_imp",
                    "function",
                    "_functools",
                    "method",
                    "code",
                    "_warnings",
                    "_frozen_importlib_external",
                    "_frozen_importlib",
                    "posix",
                    "classes",
                    "_weakref",
                    "_io",
                    "set",
                    "itertools",
                    "base_exception",
                    "python_cext",
                    "_sre",
                    "_collections",
                    "memoryview",
                    "list",
                    "_codecs",
                    "bytes",
                    "bytearray",
                    "float",
                    "time",
                    "unicodedata",
                    "_locale",
    };

    private final PythonBuiltins[] BUILTINS = new PythonBuiltins[]{
                    new BuiltinConstructors(),
                    new BuiltinFunctions(),
                    new InteropModuleBuiltins(),
                    new ObjectBuiltins(),
                    new CellBuiltins(),
                    new BoolBuiltins(),
                    new FloatBuiltins(),
                    new BytesBuiltins(),
                    new ComplexBuiltins(),
                    new ByteArrayBuiltins(),
                    new TypeBuiltins(),
                    new IntBuiltins(),
                    new TruffleObjectBuiltins(),
                    new ListBuiltins(),
                    new DictBuiltins(),
                    new DictViewBuiltins(),
                    new DictValuesBuiltins(),
                    new DictKeysIteratorBuiltins(),
                    new DictValuesIteratorBuiltins(),
                    new DictItemsIteratorBuiltins(),
                    new RangeBuiltins(),
                    new SliceBuiltins(),
                    new TupleBuiltins(),
                    new StringBuiltins(),
                    new SetBuiltins(),
                    new FrozenSetBuiltins(),
                    new IteratorBuiltins(),
                    new ReversedBuiltins(),
                    new PZipBuiltins(),
                    new EnumerateBuiltins(),
                    new SentinelIteratorBuiltins(),
                    new ForeignIteratorBuiltins(),
                    new GeneratorBuiltins(),
                    new AbstractFunctionBuiltins(),
                    new FunctionBuiltins(),
                    new BuiltinFunctionBuiltins(),
                    new AbstractMethodBuiltins(),
                    new MethodBuiltins(),
                    new BuiltinMethodBuiltins(),
                    new CodeBuiltins(),
                    new FrameBuiltins(),
                    new MappingproxyBuiltins(),
                    new GetSetDescriptorTypeBuiltins(),
                    new BaseExceptionBuiltins(),
                    new PosixModuleBuiltins(),
                    new ImpModuleBuiltins(),
                    new ArrayModuleBuiltins(),
                    new ArrayBuiltins(),
                    new TimeModuleBuiltins(),
                    new MathModuleBuiltins(),
                    new MarshalModuleBuiltins(),
                    new RandomModuleBuiltins(),
                    new RandomBuiltins(),
                    new TruffleCextBuiltins(),
                    new WeakRefModuleBuiltins(),
                    new ReferenceTypeBuiltins(),
                    new IOModuleBuiltins(),
                    new StringModuleBuiltins(),
                    new ItertoolsModuleBuiltins(),
                    new FunctoolsModuleBuiltins(),
                    new ErrnoModuleBuiltins(),
                    new CodecsModuleBuiltins(),
                    new CollectionsModuleBuiltins(),
                    new JavaModuleBuiltins(),
                    new SREModuleBuiltins(),
                    new AstModuleBuiltins(),
                    new SignalModuleBuiltins(),
                    new TracebackBuiltins(),
                    new GcModuleBuiltins(),
                    new AtexitModuleBuiltins(),
                    new FaulthandlerModuleBuiltins(),
                    new UnicodeDataModuleBuiltins(),
                    new LocaleModuleBuiltins(),
                    new SysModuleBuiltins(),
                    new BufferBuiltins(),
    };

    // not using EnumMap, HashMap, etc. to allow this to fold away during partial evaluation
    @CompilationFinal(dimensions = 1) private final PythonBuiltinClass[] builtinTypes = new PythonBuiltinClass[PythonBuiltinClassType.values().length];

    private final Map<String, PythonModule> builtinModules = new HashMap<>();
    @CompilationFinal private PythonModule builtinsModule;

    @CompilationFinal private PythonBuiltinClass typeClass;
    @CompilationFinal private PythonBuiltinClass objectClass;
    @CompilationFinal private PythonBuiltinClass moduleClass;
    @CompilationFinal private PythonBuiltinClass foreignClass;

    @CompilationFinal(dimensions = 1) private PythonClass[] errorClasses;

    private final PythonLanguage language;
    private final PythonParser parser;

    @CompilationFinal private boolean initialized;

    // used in case PythonOptions.SharedCore is false
    @CompilationFinal private PythonContext singletonContext;

    // only applicable while running initialization code (stored in context afterwards)
    private PException currentException;

    private final PythonObjectFactory factory = PythonObjectFactory.create();

    public Python3Core(PythonLanguage language, PythonParser parser) {
        this.language = language;
        this.parser = parser;
    }

    @Override
    public PythonLanguage getLanguage() {
        return language;
    }

    @Override
    public void setSingletonContext(PythonContext context) {
        assert !PythonOptions.getOption(context, PythonOptions.SharedCore);
        this.singletonContext = context;
    }

    @Override
    public boolean hasSingletonContext() {
        return singletonContext != null;
    }

    @Override
    public PythonContext getContext() {
        if (singletonContext != null) {
            return singletonContext;
        } else {
            return language.getContextReference().get();
        }
    }

    @Override
    public PythonParser getParser() {
        return parser;
    }

    @Override
    public void bootstrap() {
        initializeTypes();
        populateBuiltins();
        publishBuiltinModules();

        builtinsModule = builtinModules.get("builtins");
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void initialize() {
        String coreHome = PythonCore.getCoreHomeOrFail();
        loadFile("builtins", coreHome);
        findKnownExceptionTypes();
        for (String s : CORE_FILES) {
            loadFile(s, coreHome);
        }
        exportCInterface(getContext());
        currentException = null;
        postInitialize();
        initialized = true;
    }

    @Override
    public void postInitialize() {
        if (!getLanguage().isNativeBuildTime()) {
            initialized = false;
            loadFile(__BUILTINS_PATCHES__, PythonCore.getCoreHomeOrFail());

            PythonModule os = lookupBuiltinModule("posix");
            // reuse existing dict
            Object environAttr = os.getAttribute("environ");
            if (environAttr instanceof PDict) {
                ((PDict) environAttr).setDictStorage(createEnvironDict().getDictStorage());
            }

            initialized = true;
        }
    }

    public Object duplicate(Map<Object, Object> replacements, Object value) {
        Object replacement = replacements.get(value);
        if (replacement != null) {
            return replacement;
        }
        if (value instanceof String || value instanceof PNone || value instanceof PNotImplemented || value instanceof Boolean || value instanceof Integer || value instanceof PInt ||
                        value instanceof Double) {
            return value;
        } else if (value instanceof PFunction) {
            PFunction function = (PFunction) value;
            PythonModule globals = (PythonModule) function.getGlobals();
            PFunction newFunction = function.copyWithGlobals((PythonObject) duplicate(replacements, globals));
            for (String attr : function.getAttributeNames()) {
                newFunction.setAttribute(attr, duplicate(replacements, function.getAttribute(attr)));
            }
            return newFunction;
        } else if (value instanceof PBuiltinFunction) {
            assert ((PythonObject) value).getAttributeNames().isEmpty();
            return value;
        } else if (value instanceof PythonModule) {
            PythonModule module = (PythonModule) value;
            PythonModule newModule = factory().createPythonModule(module.getModuleName());
            replacements.put(module, newModule);
            for (String attr : module.getAttributeNames()) {
                newModule.setAttribute(attr, duplicate(replacements, module.getAttribute(attr)));
            }
            return newModule;
        } else if (value instanceof PythonClass) {
            // TODO: all classes in core should be PythonBuiltinClass
            return value;
        } else if (value instanceof PythonBuiltinClass) {
            return value;
        } else if (value instanceof PDict) {
            PDict dict = (PDict) value;
            PDict newDict = factory().createDict();
            replacements.put(dict, newDict);
            for (DictEntry attr : dict.entries()) {
                newDict.setItem(duplicate(replacements, attr.getKey()), duplicate(replacements, attr.getValue()));
            }
            return newDict;
        } else if (value instanceof PTuple) {
            PTuple tuple = (PTuple) value;
            assert tuple.getAttributeNames().isEmpty();
            Object[] contents = new Object[tuple.len()];
            PTuple newTuple = factory.createTuple(contents);
            replacements.put(tuple, newTuple);
            for (int i = 0; i < tuple.len(); i++) {
                contents[i] = duplicate(replacements, tuple.getItem(i));
            }
            return newTuple;
        } else if (value instanceof PList) {
            PList list = (PList) value;
            assert list.getAttributeNames().isEmpty();
            PList newList = factory().createList();
            replacements.put(list, newList);
            for (int i = 0; i < list.len(); i++) {
                newList.append(duplicate(replacements, list.getItem(i)));
            }
            return newList;
        }
        assert value.getClass() == PythonObject.class;
        // TODO: not sure what to do about these
        return value;
    }

    public PythonModule createSysModule(PythonContext context) {
        Map<Object, Object> replacements = new HashMap<>();
        if (context.getOptions().get(PythonOptions.SharedCore)) {
            for (PythonModule module : builtinModules.values()) {
                duplicate(replacements, module);
            }
        } else {
            for (PythonModule module : builtinModules.values()) {
                replacements.put(module, module);
            }
        }
        PythonModule sys = (PythonModule) replacements.get(builtinModules.get("sys"));
        String[] args = context.getEnv().getApplicationArguments();
        sys.setAttribute("argv", factory().createList(Arrays.copyOf(args, args.length, Object[].class)));
        String prefix = PythonCore.getSysPrefix(context.getEnv());
        for (String name : SysModuleBuiltins.SYS_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, prefix);
        }
        initializeSysPath(context, sys, args);
        return sys;
    }

    private void initializeSysPath(PythonContext context, PythonModule sys, String[] args) {
        Env env = context.getEnv();
        Object[] path = new Object[]{
                        getScriptPath(env, args),
                        PythonCore.getStdlibHome(env),
                        PythonCore.getCoreHome(env) + PythonCore.FILE_SEPARATOR + "modules"};
        PList sysPaths = factory().createList(path);
        sys.setAttribute("path", sysPaths);
        // sysPaths.append(getPythonLibraryExtrasPath());
        String pythonPath = System.getenv("PYTHONPATH");
        if (pythonPath != null) {
            for (String s : pythonPath.split(PythonCore.PATH_SEPARATOR)) {
                sysPaths.append(s);
            }
        }
    }

    private static String getScriptPath(Env env, String[] args) {
        String scriptPath;
        if (args.length > 0) {
            String argv0 = args[0];
            if (argv0 != null && !argv0.startsWith("-")) {
                TruffleFile scriptFile = env.getTruffleFile(argv0);
                try {
                    scriptPath = scriptFile.getAbsoluteFile().getParent().getPath();
                } catch (SecurityException e) {
                    scriptPath = scriptFile.getParent().getPath();
                }
                if (scriptPath == null) {
                    scriptPath = ".";
                }
            } else {
                scriptPath = "";
            }
        } else {
            scriptPath = "";
        }
        return scriptPath;
    }

    @TruffleBoundary
    public PythonModule lookupBuiltinModule(String name) {
        return builtinModules.get(name);
    }

    public PythonBuiltinClass lookupType(PythonBuiltinClassType type) {
        return builtinTypes[type.ordinal()];
    }

    @TruffleBoundary
    public PythonBuiltinClass lookupType(Class<? extends Object> clazz) {
        return lookupType(PythonBuiltinClassType.fromClass(clazz));
    }

    @TruffleBoundary
    public String[] builtinModuleNames() {
        return builtinModules.keySet().toArray(new String[0]);
    }

    public PythonModule getBuiltins() {
        return builtinsModule;
    }

    public PythonBuiltinClass getTypeClass() {
        return typeClass;
    }

    public PythonBuiltinClass getForeignClass() {
        return foreignClass;
    }

    public PythonBuiltinClass getObjectClass() {
        return objectClass;
    }

    public PythonBuiltinClass getModuleClass() {
        return moduleClass;
    }

    public PythonClass getErrorClass(PythonErrorType type) {
        return errorClasses[type.ordinal()];
    }

    @Override
    public PException raise(PBaseException exception, Node node) {
        PException pException = new PException(exception, node);
        exception.setException(pException);
        throw pException;
    }

    @Override
    public PException raise(PythonErrorType type, Node node, String format, Object... args) {
        PBaseException instance;
        PythonClass exceptionType = getErrorClass(type);
        if (format != null) {
            instance = factory.createBaseException(exceptionType, format, args);
        } else {
            instance = factory.createBaseException(exceptionType, factory.createEmptyTuple());
        }
        throw raise(instance, node);
    }

    @Override
    public PException raise(PythonErrorType type, String format, Object... args) {
        return raise(type, null, format, args);
    }

    @Override
    public PException raise(PythonErrorType type) {
        throw raise(factory.createBaseException(getErrorClass(type)), null);
    }

    @Override
    public PException raise(PythonClass exceptionType, Node node) {
        throw raise(factory.createBaseException(exceptionType), node);
    }

    public PException raise(PythonErrorType type, Node node) {
        throw raise(factory.createBaseException(getErrorClass(type)), node);
    }

    public void setCurrentException(PException e) {
        assert !initialized;
        currentException = e;
    }

    public PException getCurrentException() {
        assert !initialized;
        return currentException;
    }

    private void publishBuiltinModules() {
        PythonModule sysModule = builtinModules.get("sys");
        PDict sysModules = (PDict) sysModule.getAttribute("modules");
        for (Entry<String, PythonModule> entry : builtinModules.entrySet()) {
            sysModules.setItem(entry.getKey(), entry.getValue());
        }
    }

    public void exportCInterface(PythonContext context) {
        Env env = context.getEnv();
        if (env != null) {
            env.exportSymbol("python_cext", builtinModules.get("python_cext"));
            env.exportSymbol("python_builtins", builtinsModule);

            // export all exception classes for the C API
            for (PythonErrorType errorType : PythonErrorType.VALUES) {
                PythonClass errorClass = getErrorClass(errorType);
                env.exportSymbol("python_" + errorClass.getName(), errorClass);
            }
        }
    }

    private void initializeTypes() {
        // Make prebuilt classes known
        typeClass = new PythonBuiltinClass(null, TYPE, null);
        objectClass = new PythonBuiltinClass(typeClass, OBJECT, null);
        moduleClass = new PythonBuiltinClass(typeClass, MODULE, objectClass);
        foreignClass = new PythonBuiltinClass(typeClass, FOREIGN, objectClass);
        typeClass.unsafeSetSuperClass(objectClass);
        // Prepare core classes that are required all for core setup
        addType(PythonClass.class, getTypeClass());
        addType(PythonObject.class, getObjectClass());
        addType(PythonModule.class, getModuleClass());
        addType(TruffleObject.class, getForeignClass());
        // n.b.: the builtin modules and classes and their constructors are initialized first here,
        // so we have the mapping from java classes to python classes and builtin names to modules
        // available.
        for (PythonBuiltins builtin : BUILTINS) {
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                createModule(annotation.defineModule());
            }
            builtin.initializeClasses(this);
            for (Entry<PythonBuiltinClass, Entry<Class<?>[], Boolean>> entry : builtin.getBuiltinClasses().entrySet()) {
                PythonBuiltinClass pythonClass = entry.getKey();
                for (Class<?> klass : entry.getValue().getKey()) {
                    addType(klass, pythonClass);
                }
            }
        }
    }

    private void populateBuiltins() {
        for (PythonBuiltins builtin : BUILTINS) {
            builtin.initialize(this);
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                addBuiltinsTo(builtinModules.get(annotation.defineModule()), builtin);
            }
            for (Class<?> klass : annotation.extendClasses()) {
                addBuiltinsTo(lookupType(klass), builtin);
            }
        }

        // core machinery
        createModule("_descriptor");
        createModule("_warnings");
        PythonModule bootstrapExternal = createModule("importlib._bootstrap_external");
        bootstrapExternal.setAttribute(__PACKAGE__, "importlib");
        builtinModules.put("_frozen_importlib_external", bootstrapExternal);
        PythonModule bootstrap = createModule("importlib._bootstrap");
        bootstrap.setAttribute(__PACKAGE__, "importlib");
        builtinModules.put("_frozen_importlib", bootstrap);
    }

    private void addType(Class<? extends Object> clazz, PythonBuiltinClass typ) {
        builtinTypes[PythonBuiltinClassType.fromClass(clazz).ordinal()] = typ;
    }

    private PythonModule createModule(String name) {
        PythonModule mod = builtinModules.get(name);
        if (mod == null) {
            mod = factory().createPythonModule(name);
            builtinModules.put(name, mod);
        }
        return mod;
    }

    private void addBuiltinsTo(PythonObject obj, PythonBuiltins builtins) {
        Map<String, Object> builtinConstants = builtins.getBuiltinConstants();
        for (Map.Entry<String, Object> entry : builtinConstants.entrySet()) {
            String constantName = entry.getKey();
            obj.setAttribute(constantName, entry.getValue());
        }

        Map<String, BoundBuiltinCallable<?>> builtinFunctions = builtins.getBuiltinFunctions();
        for (Entry<String, BoundBuiltinCallable<?>> entry : builtinFunctions.entrySet()) {
            String methodName = entry.getKey();
            Object value;
            if (obj instanceof PythonModule) {
                value = factory.createBuiltinMethod(obj, (PBuiltinFunction) entry.getValue());
            } else {
                value = entry.getValue().boundToObject(obj, factory());
            }
            obj.setAttribute(methodName, value);
        }

        Map<PythonBuiltinClass, Entry<Class<?>[], Boolean>> builtinClasses = builtins.getBuiltinClasses();
        for (Entry<PythonBuiltinClass, Entry<Class<?>[], Boolean>> entry : builtinClasses.entrySet()) {
            boolean isPublic = entry.getValue().getValue();
            if (isPublic) {
                PythonBuiltinClass pythonClass = entry.getKey();
                obj.setAttribute(pythonClass.getName(), pythonClass);
            }
        }
    }

    public Source getCoreSource(String basename) {
        return getSource(basename, PythonCore.getCoreHomeOrFail());
    }

    @TruffleBoundary
    private static Source getSource(String basename, String prefix) {
        URL url = null;
        try {
            url = new URL(prefix);
        } catch (MalformedURLException e) {
            // pass
        }
        String suffix = FILE_SEPARATOR + basename + ".py";
        PythonContext ctxt = PythonLanguage.getContext();
        if (url != null) {
            // This path is hit when we load the core library e.g. from a Jar file
            try {
                return PythonLanguage.newSource(ctxt, new URL(url + suffix), basename);
            } catch (IOException e) {
                throw new RuntimeException("Could not read core library from " + url);
            }
        } else {
            Env env = ctxt.getEnv();
            TruffleFile file = env.getTruffleFile(prefix + suffix);
            try {
                return PythonLanguage.newSource(ctxt, file, basename);
            } catch (SecurityException | IOException t) {
                throw new RuntimeException("Could not read core library from " + file);
            }
        }
    }

    private void loadFile(String s, String prefix) {
        RootNode parsedModule = (RootNode) getParser().parse(ParserMode.File, this, getSource(s, prefix), null);
        PythonModule mod = lookupBuiltinModule(s);
        if (mod == null) {
            // use an anonymous module for the side-effects
            mod = factory().createPythonModule("__anonymous__");
        }
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(parsedModule);
        callTarget.call(PArguments.withGlobals(mod));
    }

    private void findKnownExceptionTypes() {
        errorClasses = new PythonClass[PythonErrorType.VALUES.length];
        for (PythonErrorType type : PythonErrorType.VALUES) {
            errorClasses[type.ordinal()] = (PythonClass) builtinsModule.getAttribute(type.name());
        }
    }

    @TruffleBoundary
    public PDict createEnvironDict() {
        Map<String, String> getenv = System.getenv();
        PDict environ = factory.createDict();
        for (Entry<String, String> entry : getenv.entrySet()) {
            environ.setItem(factory.createBytes(entry.getKey().getBytes()), factory.createBytes(entry.getValue().getBytes()));
        }
        return environ;
    }

    public PythonObjectFactory factory() {
        return factory;
    }
}
