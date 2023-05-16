package com.oracle.graal.python.compiler.operation;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.operations.POperationRootNode;
import com.oracle.graal.python.nodes.operations.POperationRootNodeGen;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.scope.Scope;
import com.oracle.graal.python.pegparser.scope.Scope.DefUse;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.BoolOpTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.ModTy.Interactive;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.PatternTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public class OperationCompiler extends BaseSSTreeVisitor<Void> {
    private final PythonLanguage language;
    private final PythonContext context;
    private final POperationRootNodeGen.Builder b;
    private final Source source;

    private ScopeEnvironment scopeEnvironment;
    private final Map<String, OperationLocal> locals = new HashMap<>();

    private final Map<SSTNode, OperationRootNode> builtNodes = new HashMap<>();

    private OperationLabel breakLabel;
    private OperationLabel continueLabel;
    private PythonObjectSlowPathFactory factory;

    private static <T> int len(T[] arr) {
        return arr == null ? 0 : arr.length;
    }

    private static Signature makeSignature(ArgumentsTy args) {
        if (args == null) {
            return Signature.EMPTY;
        }

        int posArgCount = len(args.args) + len(args.posOnlyArgs);

        String[] parameterNames = new String[posArgCount];
        int idx = 0;
        for (int i = 0; i < len(args.args); i++) {
            parameterNames[idx++] = args.args[i].arg;
        }

        for (int i = 0; i < len(args.posOnlyArgs); i++) {
            parameterNames[idx++] = args.posOnlyArgs[i].arg;
        }

        String[] kwOnlyNames = new String[len(args.kwOnlyArgs)];
        for (int i = 0; i < len(args.kwOnlyArgs); i++) {
            kwOnlyNames[i] = args.kwOnlyArgs[i].arg;
        }

        int varArgsIndex = args.varArg != null ? posArgCount : -1;
        return new Signature(len(args.posOnlyArgs),
                        args.kwArg != null,
                        varArgsIndex,
                        len(args.posOnlyArgs) > 0,
                        PythonUtils.toTruffleStringArrayUncached(parameterNames),
                        PythonUtils.toTruffleStringArrayUncached(kwOnlyNames));
    }

    private static final String COMPREHENSION_ARGUMENT_NAME = ".0";
    private static final Signature COMPREHENSION_SIGNATURE = new Signature(false, 0, false, PythonUtils.tsArray(COMPREHENSION_ARGUMENT_NAME), null);

    private class RootOperationCompiler extends BaseSSTreeVisitor<OperationRootNode> {
        private final POperationRootNodeGen.Builder b = OperationCompiler.this.b;

        @Override
        public OperationRootNode visit(ModTy.Module node) {
            b.beginRoot(language);
            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            visitModuleBody(node.body);

            endNode(node);
            b.endSource();

            POperationRootNode result = b.endRoot();
            result.setName("<module>");
            return result;
        }

        @Override
        public OperationRootNode visit(ModTy.Expression node) {
            b.beginRoot(language);
            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            b.beginReturn();
            OperationCompiler.this.visitNode(node.body);
            b.endReturn();

            endNode(node);
            b.endSource();
            POperationRootNode result = b.endRoot();
            result.setName("<module>");
            return result;
        }

        @Override
        public OperationRootNode visit(ModTy.Interactive node) {
            b.beginRoot(language);
            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            visitModuleBody(node.body);

            endNode(node);
            b.endSource();
            POperationRootNode result = b.endRoot();
            result.setName("<interactive>");
            return result;
        }

        private void visitModuleBody(StmtTy[] body) {
            if (body != null) {
                for (int i = 0; i < body.length; i++) {
                    StmtTy bodyNode = body[i];
                    if (i == body.length - 1) {
                        if (bodyNode instanceof StmtTy.Expr) {
                            b.beginReturn();
                            ((StmtTy.Expr) bodyNode).value.accept(OperationCompiler.this);
                            b.endReturn();
                        } else {
                            bodyNode.accept(OperationCompiler.this);

                            b.beginReturn();
                            b.emitLoadConstant(PNone.NONE);
                            b.endReturn();
                        }
                    } else {
                        bodyNode.accept(OperationCompiler.this);
                    }
                }
            } else {
                b.beginReturn();
                b.emitLoadConstant(PNone.NONE);
                b.endReturn();
            }
        }

        private void beginGenerator(SSTNode node) {
            b.beginRoot(language);
            b.beginSource(source);
            beginNode(node);
        }

        private void endGenerator(SSTNode node, String name, Signature signature) {
            endNode(node);
            b.endSource();
            POperationRootNode genRoot = b.endRoot();
            genRoot.setName(name);
            genRoot.setSignature(signature);

            b.beginReturn();
            b.beginMakeGenerator();
            b.emitLoadConstant(toTruffleStringUncached(name));
            b.emitLoadConstant(toTruffleStringUncached(createQualName(currentScope)));
            b.emitLoadConstant(genRoot);
            b.endMakeGenerator();
            b.endReturn();
        }

        @Override
        public OperationRootNode visit(StmtTy.FunctionDef node) {
            b.beginRoot(language);

            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            boolean isGenerator = currentScope.isGenerator();
            if (isGenerator) {
                beginGenerator(node);
            }

            copyArguments(node.args);
            copyCells();

            OperationCompiler.this.visitSequence(node.body);

            b.beginReturn();
            b.emitLoadConstant(PNone.NONE);
            b.endReturn();

            if (isGenerator) {
                endGenerator(node, node.name, makeSignature(node.args));
            }

            endNode(node);
            b.endSource();

            POperationRootNode result = b.endRoot();
            result.setName(node.name);
            result.setSignature(makeSignature(node.args));
            return result;
        }

        @Override
        public OperationRootNode visit(StmtTy.AsyncFunctionDef node) {
            // XXX: THIS IS NOT CORRECT SINCE WE DON'T HAVE YIELD
            b.beginRoot(language);

            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            copyArguments(node.args);
            copyCells();

            OperationCompiler.this.visitSequence(node.body);

            b.beginReturn();
            b.emitLoadConstant(PNone.NONE);
            b.endReturn();

            endNode(node);
            b.endSource();

            POperationRootNode result = b.endRoot();
            result.setName(node.name);
            result.setSignature(makeSignature(node.args));
            return result;
        }

        private String createQualName(Scope s) {
            Scope cur = s;
            ArrayList<String> names = new ArrayList<>();
            while (cur != null) {
                names.add(0, cur.getName());
                cur = scopeEnvironment.lookupParent(cur);
            }

            return String.join(".", names.toArray(new String[0]));
        }

        @Override
        public OperationRootNode visit(StmtTy.ClassDef node) {
            b.beginRoot(language);

            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            beginStoreLocal("__module__");
            emitReadLocal("__name__");
            endStoreLocal("__module__");

            beginStoreLocal("__qualname__");
            b.emitLoadConstant(toTruffleStringUncached(createQualName(currentScope)));
            endStoreLocal("__qualname__");

            OperationCompiler.this.visitSequence(node.body);

            if (currentScope.needsClassClosure()) {
                beginStoreLocal("__classcell__");
                b.emitLoadLocal(locals.get("__class__"));
                endStoreLocal("__classcell__");

                b.beginReturn();
                b.emitLoadLocal(locals.get("__class__"));
                b.endReturn();
            } else {
                b.beginReturn();
                b.emitLoadConstant(PNone.NONE);
                b.endReturn();
            }

            endNode(node);
            b.endSource();

            POperationRootNode result = b.endRoot();
            result.setName(node.name);
            result.setSignature(Signature.EMPTY);
            return result;
        }

        @Override
        public OperationRootNode visit(ExprTy.Lambda node) {
            b.beginRoot(language);
            b.beginSource(source);
            beginNode(node);

            initializeScope(node);

            boolean isGenerator = currentScope.isGenerator();
            if (isGenerator) {
                beginGenerator(node);
            }

            copyArguments(node.args);
            copyCells();

            if (isGenerator) {
                node.body.accept(OperationCompiler.this);

                b.beginReturn();
                b.emitLoadConstant(PNone.NONE);
                b.endReturn();

                endGenerator(node, "<lambda>", makeSignature(node.args));
            } else {
                b.beginReturn();
                node.body.accept(OperationCompiler.this);
                b.endReturn();
            }

            endNode(node);
            b.endSource();

            POperationRootNode result = b.endRoot();
            result.setName("<lambda>");
            result.setSignature(makeSignature(node.args));
            return result;
        }

        private void beginComprehension(ComprehensionTy comp, int index) {
            OperationLocal localIter = b.createLocal();
            OperationLocal localValue = b.createLocal();

            b.beginStoreLocal(localIter);
            b.beginGetIter();
            if (index == 0) {
                b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);
            } else {
                comp.iter.accept(OperationCompiler.this);
            }
            b.endGetIter();
            b.endStoreLocal();

            b.beginWhile();

            b.beginForIterate(localValue);
            b.emitLoadLocal(localIter);
            b.endForIterate();

            b.beginBlock();

            comp.target.accept(new StoreVisitor(() -> b.emitLoadLocal(localValue)));

            if (comp.ifs != null) {
                for (int i = 0; i < comp.ifs.length; i++) {
                    b.beginIfThen();
                    visitCondition(comp.ifs[i]);
                    b.beginBlock();
                }
            }
        }

        private void endComprehension(ComprehensionTy comp) {
            for (int i = 0; i < len(comp.ifs); i++) {
                b.endBlock();
                b.endIfThen();
            }

            b.endBlock();
            b.endWhile();
        }

        private OperationLocal doComprehension1(SSTNode node) {
            b.beginRoot(language);

            b.beginSource(source);
            beginNode(node);

            initializeScope(node);
            copyCells();

            OperationLocal listLocal = b.createLocal();

            b.beginStoreLocal(listLocal);
            return listLocal;
        }

        private void doComprehension2(ComprehensionTy[] generators) {
            b.endStoreLocal();

            for (int i = 0; i < generators.length; i++) {
                beginComprehension(generators[i], i);
            }
        }

        private OperationRootNode doComprehension3(SSTNode node, ComprehensionTy[] generators, OperationLocal listLocal, String name) {
            for (int i = generators.length - 1; i >= 0; i--) {
                endComprehension(generators[i]);
            }

            b.beginReturn();
            b.emitLoadLocal(listLocal);
            b.endReturn();

            endNode(node);
            b.endSource();

            POperationRootNode result = b.endRoot();
            result.setName(name);
            result.setSignature(COMPREHENSION_SIGNATURE);

            return result;
        }

        @Override
        public OperationRootNode visit(ExprTy.ListComp node) {

            OperationLocal local = doComprehension1(node);

            b.emitMakeEmptyList();

            doComprehension2(node.generators);

            b.beginListAppend();
            b.emitLoadLocal(local);
            node.element.accept(OperationCompiler.this);
            b.endListAppend();

            return doComprehension3(node, node.generators, local, "<listcomp>");
        }

        @Override
        public OperationRootNode visit(ExprTy.DictComp node) {
            OperationLocal local = doComprehension1(node);

            b.emitMakeEmptyDict();

            doComprehension2(node.generators);

            b.beginSetDictItem();
            node.key.accept(OperationCompiler.this);
            node.value.accept(OperationCompiler.this);
            b.emitLoadLocal(local);
            b.endSetDictItem();

            return doComprehension3(node, node.generators, local, "<dictcomp>");
        }

        @Override
        public OperationRootNode visit(ExprTy.SetComp node) {
            OperationLocal local = doComprehension1(node);

            b.emitMakeEmptySet();

            doComprehension2(node.generators);

            b.beginSetAdd();
            b.emitLoadLocal(local);
            node.element.accept(OperationCompiler.this);
            b.endSetAdd();

            return doComprehension3(node, node.generators, local, "<setcomp>");
        }

        @Override
        public OperationRootNode visit(ExprTy.GeneratorExp node) {
            // XXX: THIS IS NOT CORRECT, I JUST DON'T HAVE YIELD YET
            OperationLocal local = doComprehension1(node);
            b.emitMakeEmptyList();
            doComprehension2(node.generators);
            b.beginListAppend();
            b.emitLoadLocal(local);
            node.element.accept(OperationCompiler.this);
            b.endListAppend();
            return doComprehension3(node, node.generators, local, "<generator>");
        }

        private void copyArguments(ArgumentsTy args) {
            if (args == null) {
                return;
            }

            int argIdx = PArguments.USER_ARGUMENTS_OFFSET;
            // TODO default handling
            if (args.posOnlyArgs != null) {
                for (int i = 0; i < args.posOnlyArgs.length; i++) {
                    OperationLocal local = b.createLocal();

                    b.beginStoreLocal(local);
                    b.emitLoadArgument(argIdx++);
                    b.endStoreLocal();

                    locals.put(args.posOnlyArgs[i].arg, local);
                }
            }

            if (args.args != null) {
                for (int i = 0; i < args.args.length; i++) {
                    OperationLocal local = b.createLocal();

                    b.beginStoreLocal(local);
                    b.emitLoadArgument(argIdx++);
                    b.endStoreLocal();

                    locals.put(args.args[i].arg, local);
                }
            }

            if (args.kwOnlyArgs != null) {
                for (int i = 0; i < args.kwOnlyArgs.length; i++) {
                    OperationLocal local = b.createLocal();

                    b.beginStoreLocal(local);
                    b.emitLoadArgument(argIdx++);
                    b.endStoreLocal();

                    locals.put(args.kwOnlyArgs[i].arg, local);
                }
            }

            if (args.varArg != null) {
                OperationLocal local = b.createLocal();

                b.beginStoreLocal(local);
                b.emitLoadVariableArguments();
                b.endStoreLocal();

                locals.put(args.varArg.arg, local);
            }

            if (args.kwArg != null) {
                OperationLocal local = b.createLocal();

                b.beginStoreLocal(local);
                b.emitLoadKeywordArguments();
                b.endStoreLocal();

                locals.put(args.kwArg.arg, local);
            }
        }

        private void copyCells() {
            for (String cellvar : currentScope.getSymbolsByType(EnumSet.of(DefUse.Cell), 0).keySet()) {
                OperationLocal local = b.createLocal();
                b.beginStoreLocal(local);
                b.beginCreateCell();
                if (currentScope.getUseOfName(cellvar).contains(DefUse.DefParam)) {
                    b.emitLoadLocal(locals.get(cellvar));
                } else {
                    b.emitLoadConstant(null);
                }
                b.endCreateCell();
                b.endStoreLocal();

                locals.put(cellvar, local);
            }

            int start = 0;
            Set<Map.Entry<String, Integer>> freeVars = currentScope.getSymbolsByType(EnumSet.of(DefUse.Free), start).entrySet();

            if (!freeVars.isEmpty()) {
                OperationLocal[] freeLocals = new OperationLocal[freeVars.size()];
                for (int i = 0; i < freeLocals.length; i++) {
                    freeLocals[i] = b.createLocal();
                }

                b.emitLoadClosure(freeLocals);

                for (Map.Entry<String, Integer> freeVar : freeVars) {
                    locals.put(freeVar.getKey(), freeLocals[freeVar.getValue()]);
                }
            }
        }

    }

    private final RootOperationCompiler rootCompiler;
    private Scope currentScope;
    private ErrorCallback errorCallback;

    public OperationCompiler(PythonLanguage language, PythonContext context, POperationRootNodeGen.Builder b, Source source) {
        this.language = language;
        this.context = context;
        this.b = b;
        this.source = source;

        this.errorCallback = new RaisePythonExceptionErrorCallback(source, false);

        rootCompiler = new RootOperationCompiler();
        factory = context.factory();
    }

    public static OperationRootNode compile(PythonLanguage lang, PythonContext context, ModTy mod, Source source) {
        try {
            var nodes = POperationRootNodeGen.create(OperationConfig.WITH_SOURCE, b -> {
                OperationCompiler compiler = new OperationCompiler(lang, context, b, source);
                compiler.compile(mod);
            });
            return nodes.getNodes().get(nodes.getNodes().size() - 1);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    private void compile(ModTy mod) {
        // TODO: Properly parse futures
        scopeEnvironment = ScopeEnvironment.analyze(mod, errorCallback, EnumSet.noneOf(FutureFeature.class));
        // System.out.println(scopeEnvironment.toString());

        FunctionFinder finder = new FunctionFinder();
        mod.accept(finder);

        for (SSTNode node : finder.getRootNodes()) {
            // System.out.printf("building %s%n", node);
            OperationRootNode opNode = node.accept(rootCompiler);
            builtNodes.put(node, opNode);
            // System.err.println(opNode.dump());
            locals.clear();
        }
    }

    // -------------- sources --------------

    private void beginNode(SSTNode node) {
        SourceRange sourceRange = node.getSourceRange();
        int startOffset = source.getLineStartOffset(sourceRange.startLine) + sourceRange.startColumn;
        int endOffset = source.getLineStartOffset(sourceRange.endLine) + sourceRange.endColumn;
        int length = endOffset - startOffset;
        if (length == 0) {
            startOffset = 0;
        }
        b.beginSourceSection(startOffset, length);
        b.beginBlock();
    }

    @SuppressWarnings("unused")
    private void endNode(SSTNode node) {
        b.endBlock();
        b.endSourceSection();
    }

    // -------------- locals --------------

    public void initializeScope(SSTNode node) {
        locals.clear();

        Scope scope = scopeEnvironment.lookupScope(node);

        this.currentScope = scope;

        // System.err.println(currentScope);

        for (String local : scope.getSymbolsByType(EnumSet.allOf(DefUse.class), 0).keySet()) {
            emitNameOperation(local, NameOperation.Init);
        }

        if (scope.needsClassClosure()) {
            emitNameCellOperation("__class__", NameOperation.Init);
        }
    }

    private String mangle(String name) {
        return ScopeEnvironment.mangle(getClassName(currentScope), name);
    }

    private enum NameOperation {
        Init,
        Read,
        BeginWrite,
        EndWrite,
        Delete
    }

    private void emitNameCellOperation(String name, NameOperation op) {
        OperationLocal local = locals.get(name);
        switch (op) {
            case Init:
                assert local == null : "local already created";
                local = b.createLocal();
                locals.put(name, local);

                b.beginStoreLocal(local);
                b.beginCreateCell();
                b.emitLoadConstant(null);
                b.endCreateCell();
                b.endStoreLocal();
                break;
            case Read:
                b.beginLoadCell();
                b.emitLoadLocal(local);
                b.endLoadCell();
                break;
            case Delete:
                b.beginClearCell();
                b.emitLoadLocal(local);
                b.endClearCell();
                break;
            case BeginWrite:
                b.beginStoreCell();
                b.emitLoadLocal(local);
                break;
            case EndWrite:
                b.endStoreCell();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameFastOperation(String name, NameOperation op) {
        OperationLocal local = locals.get(name);
        switch (op) {
            case Init:
                assert local == null : "local already created";
                local = b.createLocal();
                locals.put(name, local);
                break;
            case Read:
                b.emitLoadLocal(local);
                break;
            case Delete:
                b.beginStoreLocal(local);
                b.emitLoadConstant(null);
                b.endStoreLocal();
                break;
            case BeginWrite:
                if (local == null) {
                    throw new NullPointerException("local " + name + " not defined");
                }
                b.beginStoreLocal(local);
                break;
            case EndWrite:
                b.endStoreLocal();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameGlobalOperation(String name, NameOperation op) {
        assert locals.get(name) == null;
        TruffleString tsName = toTruffleStringUncached(name);
        switch (op) {
            case Init:
                break;
            case Read:
                b.beginReadGlobal();
                b.emitLoadConstant(tsName);
                b.endReadGlobal();
                break;
            case Delete:
                b.beginDeleteGlobal();
                b.emitLoadConstant(tsName);
                b.endDeleteGlobal();
                break;
            case BeginWrite:
                b.beginWriteGlobal();
                break;
            case EndWrite:
                b.emitLoadConstant(tsName);
                b.endWriteGlobal();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private void emitNameSlowOperation(String name, NameOperation op) {
        assert locals.get(name) == null;
        TruffleString tsName = toTruffleStringUncached(name);
        switch (op) {
            case Init:
                break;
            case Read:
                b.beginReadName();
                b.emitLoadConstant(tsName);
                b.endReadName();
                break;
            case Delete:
                b.beginDeleteName();
                b.emitLoadConstant(tsName);
                b.endDeleteName();
                break;
            case BeginWrite:
                b.beginWriteName();
                break;
            case EndWrite:
                b.emitLoadConstant(tsName);
                b.endWriteName();
                break;
            default:
                throw new UnsupportedOperationException("unknown value: " + op);
        }
    }

    private String getClassName(Scope s) {
        Scope cur = s;
        while (cur != null) {
            if (cur.isClass()) {
                return cur.getName();
            }
            cur = scopeEnvironment.lookupParent(cur);
        }

        return null;
    }

    private void emitNameOperation(String name, NameOperation op) {
        String mangled = mangle(name);
        EnumSet<DefUse> uses = currentScope.getUseOfName(name);

        if (uses != null) {
            if (uses.contains(DefUse.Free) || uses.contains(DefUse.Cell)) {
                emitNameCellOperation(mangled, op);
                return;
            } else if (uses.contains(DefUse.Local)) {
                if (currentScope.isFunction()) {
                    emitNameFastOperation(mangled, op);
                    return;
                }
            } else if (uses.contains(DefUse.GlobalImplicit)) {
                if (currentScope.isFunction()) {
                    emitNameGlobalOperation(mangled, op);
                    return;
                }
            } else if (uses.contains(DefUse.GlobalExplicit)) {
                emitNameGlobalOperation(mangled, op);
                return;
            }
        }
        emitNameSlowOperation(mangled, op);
    }

    private void emitReadLocal(String name) {
        emitNameOperation(name, NameOperation.Read);
    }

    private void beginStoreLocal(String name) {
        emitNameOperation(name, NameOperation.BeginWrite);
    }

    private void endStoreLocal(String name) {
        emitNameOperation(name, NameOperation.EndWrite);
    }

    // --------------------- visitor ---------------------------

    @Override
    public Void visit(AliasTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(ArgTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(ArgumentsTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(ComprehensionTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(ExprTy.Attribute node) {
        beginNode(node);

        b.beginGetAttribute();
        node.value.accept(this);
        b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
        b.endGetAttribute();

        endNode(node);

        return null;
    }

    @Override
    public Void visit(ExprTy.Await node) {
        // XXX: THIS IS NOT CORRECT
        node.value.accept(this);
        return null;
    }

    @Override
    public Void visit(ExprTy.BinOp node) {
        beginNode(node);
        switch (node.op) {
            case Add:
                b.beginAdd();
                node.left.accept(this);
                node.right.accept(this);
                b.endAdd();
                break;
            case BitAnd:
                b.beginBitAnd();
                node.left.accept(this);
                node.right.accept(this);
                b.endBitAnd();
                break;
            case BitOr:
                b.beginBitOr();
                node.left.accept(this);
                node.right.accept(this);
                b.endBitOr();
                break;
            case BitXor:
                b.beginBitXor();
                node.left.accept(this);
                node.right.accept(this);
                b.endBitXor();
                break;
            case Div:
                b.beginTrueDiv();
                node.left.accept(this);
                node.right.accept(this);
                b.endTrueDiv();
                break;
            case FloorDiv:
                b.beginFloorDiv();
                node.left.accept(this);
                node.right.accept(this);
                b.endFloorDiv();
                break;
            case LShift:
                b.beginLShift();
                node.left.accept(this);
                node.right.accept(this);
                b.endLShift();
                break;
            case MatMult:
                b.beginMatMul();
                node.left.accept(this);
                node.right.accept(this);
                b.endMatMul();
                break;
            case Mod:
                b.beginMod();
                node.left.accept(this);
                node.right.accept(this);
                b.endMod();
                break;
            case Mult:
                b.beginMul();
                node.left.accept(this);
                node.right.accept(this);
                b.endMul();
                break;
            case Pow:
                b.beginPow();
                node.left.accept(this);
                node.right.accept(this);
                b.endPow();
                break;
            case RShift:
                b.beginRShift();
                node.left.accept(this);
                node.right.accept(this);
                b.endRShift();
                break;
            case Sub:
                b.beginSub();
                node.left.accept(this);
                node.right.accept(this);
                b.endSub();
                break;
            default:
                throw new UnsupportedOperationException("" + node.getClass());
        }

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.BoolOp node) {
        beginNode(node);

        if (node.op == BoolOpTy.And) {
            b.beginBoolAnd();
        } else {
            b.beginBoolOr();
        }

        visitSequence(node.values);

        if (node.op == BoolOpTy.And) {
            b.endBoolAnd();
        } else {
            b.endBoolOr();
        }

        endNode(node);
        return null;
    }

    private static boolean anyIsStarred(SSTNode[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] instanceof ExprTy.Starred) {
                return true;
            }
        }

        return false;
    }

    private static final int NUM_ARGS_VARIADIC = -1;
    private static final int NUM_ARGS_MIN_FIXED = 0;
    private static final int NUM_ARGS_MAX_FIXED = 4;

    private void emitCall(ExprTy func, ExprTy[] args, KeywordTy[] keywords) {
        int numArgs = anyIsStarred(args) || len(keywords) > 0 ? NUM_ARGS_VARIADIC : len(args);
        boolean useVariadic = numArgs < NUM_ARGS_MIN_FIXED || numArgs > NUM_ARGS_MAX_FIXED;

        // @formatter:off
        switch (numArgs) {
            case 0:  b.beginCallNilaryMethod();     break;
            case 1:  b.beginCallUnaryMethod();      break;
            case 2:  b.beginCallBinaryMethod();     break;
            case 3:  b.beginCallTernaryMethod();    break;
            case 4:  b.beginCallQuaternaryMethod(); break;
            default: b.beginCallVarargsMethod();    break;
        }
        // @formatter:on

        func.accept(this);

        if (useVariadic) {
            emitUnstar(args);
            emitKeywords(keywords);
        } else {
            assert len(args) == numArgs;
            assert len(keywords) == 0;
            visitSequence(args);
        }

        // @formatter:off
        switch (numArgs) {
            case 0:  b.endCallNilaryMethod();     break;
            case 1:  b.endCallUnaryMethod();      break;
            case 2:  b.endCallBinaryMethod();     break;
            case 3:  b.endCallTernaryMethod();    break;
            case 4:  b.endCallQuaternaryMethod(); break;
            default: b.endCallVarargsMethod();    break;
        }
        // @formatter:on
    }

    @Override
    public Void visit(ExprTy.Call node) {
        beginNode(node);

        // XXX: handle super() calls specially since we don't have named slots yet
        if (node.func instanceof ExprTy.Name && ((ExprTy.Name) node.func).id.equals("super") && len(node.args) == 0 && len(node.keywords) == 0) {
            b.beginCallBinaryMethod();

            b.beginReadName();
            b.emitLoadConstant(StringLiterals.T_SUPER);
            b.endReadName();

            emitReadLocal("__class__");

            b.emitLoadArgument(PArguments.USER_ARGUMENTS_OFFSET);

            b.endCallBinaryMethod();
        } else {
            emitCall(node.func, node.args, node.keywords);
        }

        endNode(node);
        return null;
    }

    private void beginComparison(CmpOpTy op) {
        switch (op) {
            case Eq:
                b.beginEq();
                break;
            case NotEq:
                b.beginNe();
                break;
            case Lt:
                b.beginLt();
                break;
            case LtE:
                b.beginLe();
                break;
            case Gt:
                b.beginGt();
                break;
            case GtE:
                b.beginGe();
                break;
            case Is:
                b.beginIs();
                break;
            case IsNot:
                b.beginNot();
                b.beginIs();
                break;
            case In:
                b.beginContains();
                break;
            case NotIn:
                b.beginNot();
                b.beginContains();
                break;
            default:
                throw new UnsupportedOperationException("" + op);
        }
    }

    private void endComparison(CmpOpTy op) {
        switch (op) {
            case Eq:
                b.endEq();
                break;
            case NotEq:
                b.endNe();
                break;
            case Lt:
                b.endLt();
                break;
            case LtE:
                b.endLe();
                break;
            case Gt:
                b.endGt();
                break;
            case GtE:
                b.endGe();
                break;
            case Is:
                b.endIs();
                break;
            case IsNot:
                b.endIs();
                b.endNot();
                break;
            case In:
                b.endContains();
                break;
            case NotIn:
                b.endContains();
                b.endNot();
                break;
            default:
                throw new UnsupportedOperationException("" + op);
        }
    }

    @Override
    public Void visit(ExprTy.Compare node) {
        beginNode(node);

        b.beginBoolAnd();

        OperationLocal tmp = b.createLocal();

        for (int i = 0; i < node.comparators.length; i++) {
            beginComparison(node.ops[i]);

            if (i == 0) {
                node.left.accept(this);
            } else {
                b.emitLoadLocal(tmp);
            }

            if (i != node.comparators.length - 1) {
                b.beginTeeLocal(tmp);
            }
            node.comparators[i].accept(this);
            if (i != node.comparators.length - 1) {
                b.endTeeLocal();
            }

            endComparison(node.ops[i]);
        }

        b.endBoolAnd();

        endNode(node);
        return null;
    }

    private boolean isFullConstant(ConstantValue value) {
        switch (value.kind) {
            case TUPLE:
                for (ConstantValue cv : value.getTupleElements()) {
                    if (!isFullConstant(cv)) {
                        return false;
                    }
                }
                return true;
            case FROZENSET:
                return false;
            default:
                return true;
        }
    }

    private Object createConstantObject(ConstantValue value) {
        switch (value.kind) {
            case NONE:
                return (PNone.NONE);
            case ELLIPSIS:
                return (PEllipsis.INSTANCE);
            case COMPLEX: {
                double[] parts = value.getComplex();
                return (factory.createComplex(parts[0], parts[1]));
            }
            case BIGINTEGER:
                return (factory.createInt(value.getBigInteger()));
            case BYTES:
                return (factory.createBytes(value.getBytes()));
            case BOOLEAN:
                return (value.getBoolean());
            case DOUBLE:
                return (value.getDouble());
            case LONG:
                return (value.getLong());
            case RAW:
                return (value.getRaw(TruffleString.class));
            case TUPLE:
                ConstantValue[] cvs = value.getTupleElements();
                Object[] objs = new Object[cvs.length];
                for (int i = 0; i < cvs.length; i++) {
                    objs[i] = createConstantObject(cvs[i]);
                }
                return factory.createTuple(objs);
            default:
                throw new UnsupportedOperationException("not supported: " + value.kind);
        }
    }

    private void createConstant(ConstantValue value) {
        switch (value.kind) {
            case FROZENSET:
                b.beginMakeFrozenSet();
                for (ConstantValue cv : value.getFrozensetElements()) {
                    createConstant(cv);
                }
                b.endMakeFrozenSet();
                break;
            case TUPLE:
                if (isFullConstant(value)) {
                    b.emitLoadConstant(createConstantObject(value));
                } else {
                    b.beginMakeTuple();
                    b.beginMakeVariadic();
                    for (ConstantValue cv : value.getFrozensetElements()) {
                        createConstant(cv);
                    }
                    b.endMakeVariadic();
                    b.endMakeTuple();
                }
                break;
            default:
                b.emitLoadConstant(createConstantObject(value));
                break;
        }
    }

    @Override
    public Void visit(ExprTy.Constant node) {
        beginNode(node);
        createConstant(node.value);
        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Dict node) {
        beginNode(node);

        b.beginMakeDict();
        for (int i = 0; i < node.keys.length; i++) {
            if (node.keys[i] == null) {
                b.emitLoadConstant(PNone.NO_VALUE);
            } else {
                node.keys[i].accept(this);
            }
            node.values[i].accept(this);
        }
        b.endMakeDict();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.DictComp node) {
        beginNode(node);

        b.beginCallUnaryMethod();
        emitMakeFunction(node, "<dictcomp>", COMPREHENSION_ARGS, createCodeObject(node));
        node.generators[0].iter.accept(this);
        b.endCallUnaryMethod();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.FormattedValue node) {
        b.beginFormatValue();

        // @formatter:off
        switch (node.conversion) {
            case 97:  b.beginFormatAscii(); break;
            case 114: b.beginFormatRepr(); break;
            case 115: b.beginFormatStr(); break;
            case -1:  break;
            default: throw new UnsupportedOperationException("unknown conversion: " + node.conversion);
        }
        // @formatter:on

        node.value.accept(this);

        // @formatter:off
        switch (node.conversion) {
            case 97:  b.endFormatAscii(); break;
            case 114: b.endFormatRepr(); break;
            case 115: b.endFormatStr(); break;
            case -1:  break;
            default: throw new UnsupportedOperationException("unknown conversion: " + node.conversion);
        }
        // @formatter:on

        if (node.formatSpec != null) {
            node.formatSpec.accept(this);
        } else {
            b.emitLoadConstant(StringLiterals.T_EMPTY_STRING);
        }

        b.endFormatValue();

        return null;
    }

    @Override
    public Void visit(ExprTy.GeneratorExp node) {
        // XXX: THIS IS NOT CORRECT, I JUST DON'T HAVE YIELD YET
        beginNode(node);

        b.beginCallUnaryMethod();
        emitMakeFunction(node, "<generator>", COMPREHENSION_ARGS, createCodeObject(node));
        node.generators[0].iter.accept(this);
        b.endCallUnaryMethod();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.IfExp node) {
        beginNode(node);
        b.beginConditional();

        visitCondition(node.test);

        node.body.accept(this);

        node.orElse.accept(this);

        b.endConditional();
        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.JoinedStr node) {
        beginNode(node);

        if (node.values.length == 1) {
            node.values[0].accept(this);
        } else {
            b.beginBuildString();
            visitSequence(node.values);
            b.endBuildString();
        }

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Lambda node) {
        beginNode(node);
        emitMakeFunction(node, "<lambda>", node.args, createCodeObject(node));
        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.List node) {
        beginNode(node);

        b.beginMakeList();
        emitUnstar(node.elements);
        b.endMakeList();

        endNode(node);
        return null;
    }

    private static final ArgumentsTy COMPREHENSION_ARGS = new ArgumentsTy(new ArgTy[]{new ArgTy(COMPREHENSION_ARGUMENT_NAME, null, null, null)}, null, null, null, null, null, null, null);

    @Override
    public Void visit(ExprTy.ListComp node) {
        beginNode(node);

        b.beginCallUnaryMethod();
        emitMakeFunction(node, "<listcomp>", COMPREHENSION_ARGS, createCodeObject(node));
        node.generators[0].iter.accept(this);
        b.endCallUnaryMethod();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Name node) {
        beginNode(node);
        emitReadLocal(node.id);
        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.NamedExpr node) {
        beginNode(node);

        // save expr result to "tmp"
        OperationLocal tmp = b.createLocal();
        b.beginStoreLocal(tmp);
        node.value.accept(this);
        b.endStoreLocal();

        node.target.accept(new StoreVisitor(() -> {
            b.emitLoadLocal(tmp);
        }));

        b.emitLoadLocal(tmp);
        endNode(node);
        return null;
    }

    /**
     * Converts a sequence of expressions of which some may be started into just an Object[]
     *
     * @param args the sequence of expressions
     */
    private void emitUnstar(ExprTy[] args) {
        if (len(args) == 0) {
            b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
        } else if (anyIsStarred(args)) {
            b.beginUnstar();
            boolean inVariadic = false;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof ExprTy.Starred) {
                    if (inVariadic) {
                        b.endMakeVariadic();
                        inVariadic = false;
                    }

                    b.beginIterToArray();
                    ((ExprTy.Starred) args[i]).value.accept(this);
                    b.endIterToArray();
                } else {
                    if (!inVariadic) {
                        b.beginMakeVariadic();
                        inVariadic = true;
                    }

                    args[i].accept(this);
                }
            }

            if (inVariadic) {
                b.endMakeVariadic();
            }

            b.endUnstar();
        } else {
            b.beginMakeVariadic();
            visitSequence(args);
            b.endMakeVariadic();
        }
    }

    @Override
    public Void visit(ExprTy.Set node) {
        beginNode(node);

        b.beginMakeSet();
        emitUnstar(node.elements);
        b.endMakeSet();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.SetComp node) {
        beginNode(node);

        b.beginCallUnaryMethod();
        emitMakeFunction(node, "<setcomp>", COMPREHENSION_ARGS, createCodeObject(node));
        node.generators[0].iter.accept(this);
        b.endCallUnaryMethod();

        endNode(node);
        return null;
    }

    private void visitNoneable(ExprTy node) {
        if (node == null) {
            b.emitLoadConstant(PNone.NONE);
        } else {
            node.accept(this);
        }
    }

    @Override
    public Void visit(ExprTy.Slice node) {
        beginNode(node);

        b.beginMakeSlice();

        visitNoneable(node.lower);
        visitNoneable(node.upper);
        visitNoneable(node.step);

        b.endMakeSlice();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Starred node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(ExprTy.Subscript node) {
        beginNode(node);

        b.beginGetItem();
        node.value.accept(this);
        node.slice.accept(this);
        b.endGetItem();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Tuple node) {
        beginNode(node);

        b.beginMakeTuple();
        emitUnstar(node.elements);
        b.endMakeTuple();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.UnaryOp node) {
        beginNode(node);

        switch (node.op) {
            case UAdd:
                b.beginPos();
                node.operand.accept(this);
                b.endPos();
                break;
            case Invert:
                b.beginInvert();
                node.operand.accept(this);
                b.endInvert();
                break;
            case USub:
                b.beginNeg();
                node.operand.accept(this);
                b.endNeg();
                break;
            case Not:
                b.beginNot();
                node.operand.accept(this);
                b.endNot();
                break;
            default:
                throw new UnsupportedOperationException("" + node.getClass());
        }

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.Yield node) {
        beginNode(node);

        b.beginYield();
        if (node.value != null) {
            node.value.accept(this);
        } else {
            b.emitLoadConstant(PNone.NONE);
        }
        b.endYield();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(ExprTy.YieldFrom node) {
        // XXX: THIS IS NOT CORRECT
        beginNode(node);

        b.beginYield();
        node.value.accept(this);
        b.endYield();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(KeywordTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.AnnAssign node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.Assert node) {
        if (!context.getOption(PythonOptions.PythonOptimizeFlag)) {
            // todo: if this can be changed at runtime, we need to compile in a check
            beginNode(node);
            b.beginIfThen();

            b.beginNot();
            node.test.accept(this);
            b.endNot();

            b.beginAssertFailed();
            if (node.msg == null) {
                b.emitLoadConstant(PNone.NO_VALUE);
            } else {
                node.msg.accept(this);
            }
            b.endAssertFailed();

            b.endIfThen();
            endNode(node);
        }
        return null;
    }

    // --------------------- assign ------------------------

    private class StoreVisitor extends BaseSSTreeVisitor<Void> {
        private final POperationRootNodeGen.Builder b = OperationCompiler.this.b;
        private final Runnable generateValue;

        StoreVisitor(Runnable generateValue) {
            this.generateValue = generateValue;
        }

        @Override
        public Void visit(ExprTy.Name node) {
            beginNode(node);
            beginStoreLocal(node.id);
            generateValue.run();
            endStoreLocal(node.id);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Attribute node) {
            beginNode(node);
            b.beginSetAttribute();
            generateValue.run();
            node.value.accept(OperationCompiler.this);
            b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
            b.endSetAttribute();
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            beginNode(node);
            b.beginSetItem();
            generateValue.run();
            node.value.accept(OperationCompiler.this);
            node.slice.accept(OperationCompiler.this);
            b.endSetItem();
            endNode(node);
            return null;
        }

        private void visitIterableAssign(ExprTy[] nodes) {
            b.beginBlock();

            // TODO if it is a variable directly, use that variable instead of going to a temp
            OperationLocal[] targets = new OperationLocal[nodes.length];

            for (int i = 0; i < targets.length; i++) {
                targets[i] = b.createLocal();
            }

            int indexOfStarred = -1;
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] instanceof ExprTy.Starred) {
                    indexOfStarred = i;
                    break;
                }
            }

            if (indexOfStarred == -1) {
                b.beginUnpackIterable(targets);
            } else {
                b.beginUnpackIterableStarred(targets);
            }

            generateValue.run();

            if (indexOfStarred == -1) {
                b.endUnpackIterable();
            } else {
                b.emitLoadConstant(indexOfStarred);
                b.endUnpackIterableStarred();
            }

            for (int i = 0; i < nodes.length; i++) {
                final int index = i;

                ExprTy target = nodes[i];
                if (nodes[i] instanceof ExprTy.Starred) {
                    target = ((ExprTy.Starred) target).value;
                }

                target.accept(new StoreVisitor(() -> {
                    b.emitLoadLocal(targets[index]);
                }));
            }

            b.endBlock();
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            beginNode(node);
            visitIterableAssign(node.elements);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            beginNode(node);
            visitIterableAssign(node.elements);
            endNode(node);
            return null;
        }
    }

    private class AugStoreVisitor extends BaseSSTreeVisitor<Void> {
        private final POperationRootNodeGen.Builder b = OperationCompiler.this.b;
        private final Runnable generateValue;
        private final OperatorTy op;

        AugStoreVisitor(OperatorTy op, Runnable generateValue) {
            this.op = op;
            this.generateValue = generateValue;
        }

        private void beginAugAssign() {
            switch (op) {
                case Add:
                    b.beginIAdd();
                    break;
                case Sub:
                    b.beginISub();
                    break;
                case Mult:
                    b.beginIMult();
                    break;
                case FloorDiv:
                    b.beginIFloorDiv();
                    break;
                case BitAnd:
                    b.beginIAnd();
                    break;
                case BitOr:
                    b.beginIOr();
                    break;
                case BitXor:
                    b.beginIXor();
                    break;
                case RShift:
                    b.beginIRShift();
                    break;
                case LShift:
                    b.beginILShift();
                    break;
                case Div:
                    b.beginITrueDiv();
                    break;
                case Mod:
                    b.beginIMod();
                    break;
                case MatMult:
                    b.beginIMatMul();
                    break;
                case Pow:
                    b.beginIPow();
                    break;
                default:
                    throw new UnsupportedOperationException("aug ass: " + op);
            }
        }

        private void endAugAssign() {
            switch (op) {
                case Add:
                    b.endIAdd();
                    break;
                case Sub:
                    b.endISub();
                    break;
                case Mult:
                    b.endIMult();
                    break;
                case FloorDiv:
                    b.endIFloorDiv();
                    break;
                case BitAnd:
                    b.endIAnd();
                    break;
                case BitOr:
                    b.endIOr();
                    break;
                case BitXor:
                    b.endIXor();
                    break;
                case RShift:
                    b.endIRShift();
                    break;
                case LShift:
                    b.endILShift();
                    break;
                case Div:
                    b.endITrueDiv();
                    break;
                case Mod:
                    b.endIMod();
                    break;
                case MatMult:
                    b.endIMatMul();
                    break;
                case Pow:
                    b.endIPow();
                    break;
                default:
                    throw new UnsupportedOperationException("aug ass: " + op);
            }
        }

        @Override
        public Void visit(ExprTy.Name node) {
            beginNode(node);

            beginStoreLocal(node.id);
            beginAugAssign();
            emitReadLocal(node.id);
            generateValue.run();
            endAugAssign();
            endStoreLocal(node.id);

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Attribute node) {
            beginNode(node);
            b.beginBlock();
            // {
            OperationLocal value = b.createLocal();
            OperationLocal target = b.createLocal();

            b.beginStoreLocal(value);
            generateValue.run();
            b.endStoreLocal();

            b.beginStoreLocal(target);
            node.value.accept(OperationCompiler.this);
            b.endStoreLocal();

            b.beginSetAttribute();
            beginAugAssign();

            b.beginGetAttribute();
            b.emitLoadLocal(target);
            b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
            b.endGetAttribute();

            b.emitLoadLocal(value);

            endAugAssign();

            b.emitLoadLocal(target);

            b.emitLoadConstant(toTruffleStringUncached(mangle(node.attr)));
            b.endSetAttribute();
            // }
            b.endBlock();
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Subscript node) {
            beginNode(node);
            b.beginBlock();
            // {
            OperationLocal value = b.createLocal();
            OperationLocal target = b.createLocal();
            OperationLocal slice = b.createLocal();

            b.beginStoreLocal(value);
            generateValue.run();
            b.endStoreLocal();

            b.beginStoreLocal(target);
            node.value.accept(OperationCompiler.this);
            b.endStoreLocal();

            b.beginStoreLocal(slice);
            node.slice.accept(OperationCompiler.this);
            b.endStoreLocal();

            b.beginSetItem();
            beginAugAssign();

            b.beginGetItem();
            b.emitLoadLocal(target);
            b.emitLoadLocal(slice);
            b.endGetItem();

            b.emitLoadLocal(value);

            endAugAssign();

            b.emitLoadLocal(target);
            b.emitLoadLocal(slice);
            b.endSetItem();
            // }
            b.endBlock();
            endNode(node);
            return null;
        }
    }

    @Override
    public Void visit(StmtTy.Assign node) {
        if (node.targets.length == 1) {
            node.targets[0].accept(new StoreVisitor(() -> {
                node.value.accept(this);
            }));
        } else {
            OperationLocal tmp = b.createLocal();
            b.beginStoreLocal(tmp);
            node.value.accept(this);
            b.endStoreLocal();

            for (ExprTy target : node.targets) {
                target.accept(new StoreVisitor(() -> {
                    b.emitLoadLocal(tmp);
                }));
            }
        }
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncFor node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.AsyncWith node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.AugAssign node) {
        node.target.accept(new AugStoreVisitor(node.op, () -> {
            node.value.accept(this);
        }));

        return null;
    }

    private void emitKeywords(KeywordTy[] kws) {
        if (len(kws) == 0) {
            b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
        } else {
            ArrayList<TruffleString> keys = new ArrayList<>();
            ArrayList<ExprTy> values = new ArrayList<>();

            boolean hasSplat = false;

            for (int i = 0; i < kws.length; i++) {
                if (kws[i].arg == null) {
                    if (!hasSplat) {
                        b.beginUnstarKw();
                        hasSplat = true;
                    }

                    if (!keys.isEmpty()) {
                        emitKeywordsSimple(keys, values);
                    }

                    b.beginSplatKeywords();
                    kws[i].value.accept(this);
                    b.endSplatKeywords();

                } else {
                    keys.add(toTruffleStringUncached(kws[i].arg));
                    values.add(kws[i].value);
                }
            }

            if (!keys.isEmpty()) {
                emitKeywordsSimple(keys, values);
            }

            if (hasSplat) {
                b.endUnstarKw();
            }
        }
    }

    private void emitKeywordsSimple(ArrayList<TruffleString> keys, ArrayList<ExprTy> values) {
        b.beginMakeKeywords();
        b.emitLoadConstant(keys.toArray(new TruffleString[0]));
        visitSequence(values.toArray(new ExprTy[0]));
        b.endMakeKeywords();

        keys.clear();
        values.clear();
    }

    @Override
    public Void visit(StmtTy.ClassDef node) {
        beginNode(node);

        PCode code = createCodeObject(node);

        beginStoreLocal(node.name);

        b.beginCallVarargsMethod();

        b.emitBuildClass();

        b.beginMakeVariadic();
        // {

        b.beginMakeFunction();
        b.emitLoadConstant(toTruffleStringUncached(node.name));
        b.emitLoadConstant(code);
        b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
        b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
        b.emitLoadConstant(null);
        b.endMakeFunction();

        b.emitLoadConstant(toTruffleStringUncached(node.name));

        visitSequence(node.bases);

        // }
        b.endMakeVariadic();

        emitKeywords(node.keywords);

        b.endCallVarargsMethod();

        endStoreLocal(node.name);

        endNode(node);

        return null;
    }

    private class DeleteVisitor extends BaseSSTreeVisitor<Void> {

        @Override
        public Void visit(ExprTy.Subscript node) {
            beginNode(node);

            b.beginDeleteItem();
            node.value.accept(OperationCompiler.this);
            node.slice.accept(OperationCompiler.this);
            b.endDeleteItem();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Attribute node) {
            beginNode(node);

            b.beginDeleteAttribute();
            node.value.accept(OperationCompiler.this);
            b.emitLoadConstant(toTruffleStringUncached(node.attr));
            b.endDeleteAttribute();

            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Name node) {
            beginNode(node);
            emitNameOperation(node.id, NameOperation.Delete);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.Tuple node) {
            beginNode(node);
            visitSequence(node.elements);
            endNode(node);
            return null;
        }

        @Override
        public Void visit(ExprTy.List node) {
            beginNode(node);
            visitSequence(node.elements);
            endNode(node);
            return null;
        }
    }

    @Override
    public Void visit(StmtTy.Delete node) {
        new DeleteVisitor().visitSequence(node.targets);
        return null;
    }

    @Override
    public Void visit(StmtTy.Expr node) {
        beginNode(node);
        node.value.accept(this);
        endNode(node);

        return null;
    }

    @Override
    public Void visit(StmtTy.For node) {
        // iter = GetIter(<<iter>>); value;
        // while (ForIterate(iter, &value)) {
        // store value
        // <<body>>
        // }
        beginNode(node);

        OperationLocal iter = b.createLocal();

        b.beginStoreLocal(iter);
        b.beginGetIter();
        node.iter.accept(this);
        b.endGetIter();
        b.endStoreLocal();

        OperationLabel oldBreakLabel = breakLabel;
        OperationLabel oldContinueLabel = continueLabel;

        continueLabel = b.createLabel();
        breakLabel = b.createLabel();

        b.emitLabel(continueLabel);

        b.beginWhile();
        // {
        OperationLocal value = b.createLocal();

        b.beginForIterate(value);
        b.emitLoadLocal(iter);
        b.endForIterate();

        b.beginBlock();
        // {
        node.target.accept(new StoreVisitor(() -> {
            b.emitLoadLocal(value);
        }));

        visitSequence(node.body);
        // }
        b.endBlock();
        // }
        b.endWhile();

        visitSequence(node.orElse);

        b.emitLabel(breakLabel);

        breakLabel = oldBreakLabel;
        continueLabel = oldContinueLabel;

        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.FunctionDef node) {
        beginNode(node);

        beginStoreLocal(node.name);

        int numDeco = len(node.decoratorList);
        for (int i = 0; i < numDeco; i++) {
            b.beginCallUnaryMethod();
            node.decoratorList[i].accept(this);
        }

        emitMakeFunction(node, node.name, node.args, createCodeObject(node));

        for (int i = 0; i < numDeco; i++) {
            b.endCallUnaryMethod();
        }

        endStoreLocal(node.name);

        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.AsyncFunctionDef node) {
        beginNode(node);

        beginStoreLocal(node.name);

        int numDeco = len(node.decoratorList);
        for (int i = 0; i < numDeco; i++) {
            b.beginCallUnaryMethod();
            node.decoratorList[i].accept(this);
        }

        emitMakeFunction(node, node.name, node.args, createCodeObject(node));

        for (int i = 0; i < numDeco; i++) {
            b.endCallUnaryMethod();
        }

        endStoreLocal(node.name);

        endNode(node);
        return null;
    }

    private void emitMakeFunction(SSTNode node, String name, ArgumentsTy args, PCode code) {
        b.beginMakeFunction();
        b.emitLoadConstant(toTruffleStringUncached(name));
        b.emitLoadConstant(code);

        if (args == null || len(args.defaults) == 0) {
            b.emitLoadConstant(PythonUtils.EMPTY_OBJECT_ARRAY);
        } else {
            b.beginMakeVariadic();
            for (int i = 0; i < args.defaults.length; i++) {
                args.defaults[i].accept(this);
            }
            b.endMakeVariadic();
        }

        if (args == null || len(args.kwDefaults) == 0) {
            b.emitLoadConstant(PKeyword.EMPTY_KEYWORDS);
        } else {
            ArgTy[] kwOnlyArgs = args.kwOnlyArgs;
            TruffleString[] res = new TruffleString[kwOnlyArgs.length];
            for (int i = 0; i < kwOnlyArgs.length; i++) {
                res[i] = toTruffleStringUncached(kwOnlyArgs[i].arg);
            }

            b.beginMakeKeywords();
            b.emitLoadConstant(res);
            for (int i = 0; i < args.kwDefaults.length; i++) {
                if (args.kwDefaults[i] != null) {
                    args.kwDefaults[i].accept(this);
                } else {
                    b.emitLoadConstant(PNone.NO_VALUE);
                }
            }
            b.endMakeKeywords();
        }

        Scope target = scopeEnvironment.lookupScope(node);

        Set<Map.Entry<String, Integer>> freeVars = target.getSymbolsByType(EnumSet.of(DefUse.Free), 0).entrySet();
        if (freeVars.isEmpty()) {
            b.emitLoadConstant(null);
        } else {
            String[] vars = new String[freeVars.size()];
            for (Map.Entry<String, Integer> fv : freeVars) {
                vars[fv.getValue()] = fv.getKey();
            }

            b.beginMakeCellArray();
            for (int i = 0; i < vars.length; i++) {
                b.emitLoadLocal(locals.get(vars[i]));
            }
            b.endMakeCellArray();
        }

        b.endMakeFunction();
    }

    private PCode createCodeObject(SSTNode node) {
        OperationRootNode opNode = builtNodes.get(node);
        if (opNode == null) {
            throw new AssertionError();
        }

        return factory.createCode(
                        ((RootNode) opNode).getCallTarget(),
                        0, node.getSourceRange().startLine, new byte[0],
                        toTruffleStringUncached(source.getPath()));
    }

    @Override
    public Void visit(StmtTy.Global node) {
        return null;
    }

    private void visitStatements(StmtTy[] stmts) {
        b.beginBlock();
        if (stmts != null) {
            for (StmtTy stmt : stmts) {
                stmt.accept(this);
            }
        }
        b.endBlock();
    }

    private void visitCondition(ExprTy node) {
        boolean needsYes = true;
        if (node instanceof ExprTy.UnaryOp && ((ExprTy.UnaryOp) node).op == UnaryOpTy.Not) {
            needsYes = false;
        }

        if (needsYes) {
            b.beginYes();
        }

        node.accept(this);

        if (needsYes) {
            b.endYes();
        }
    }

    @Override
    public Void visit(StmtTy.If node) {
        beginNode(node);

        if (node.orElse == null || node.orElse.length == 0) {
            b.beginIfThen();

            visitCondition(node.test);

            visitStatements(node.body);

            b.endIfThen();
        } else {
            b.beginIfThenElse();

            visitCondition(node.test);

            visitStatements(node.body);

            visitStatements(node.orElse);

            b.endIfThenElse();
        }

        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.Import node) {
        beginNode(node);

        for (AliasTy name : node.names) {
            if (name.asName == null) {
                // import a.b.c
                // --> a = (Import "a.b.c" [] 0)

                // import a
                // --> a = (Import "a" [] 0)

                String resName = name.name.contains(".")
                                ? name.name.substring(0, name.name.indexOf('.'))
                                : name.name;

                beginStoreLocal(resName);

                b.beginImport();
                b.emitLoadConstant(toTruffleStringUncached(name.name));
                b.emitLoadConstant(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
                b.emitLoadConstant(0);
                b.endImport();

                endStoreLocal(resName);
            } else {
                // import a.b.c as x
                // --> x = (ImportFrom (ImportFrom (Import "a" [] 0) "b") "c")
                // import a as x
                // --> x = (Import "a" [] 0)

                String[] parts = name.name.split("\\.");

                beginStoreLocal(name.asName);

                for (int i = parts.length - 1; i >= 0; i--) {
                    if (i != 0) {
                        b.beginImportFrom();
                    } else {
                        b.beginImport();
                        b.emitLoadConstant(toTruffleStringUncached(parts[i]));
                        b.emitLoadConstant(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
                        b.emitLoadConstant(0);
                        b.endImport();
                    }
                }

                for (int i = 1; i < parts.length; i++) {
                    b.emitLoadConstant(toTruffleStringUncached(parts[i]));
                    b.endImportFrom();
                }

                endStoreLocal(name.asName);
            }
        }

        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.ImportFrom node) {
        beginNode(node);

        TruffleString tsModuleName = toTruffleStringUncached(node.module == null ? "" : node.module);

        if (node.names[0].name.equals("*")) {
            b.beginImportStar();
            b.emitLoadConstant(tsModuleName);
            b.emitLoadConstant(node.level);
            b.endImportStar();
        } else {
            b.beginBlock();

            OperationLocal module = b.createLocal();

            TruffleString[] fromList = new TruffleString[node.names.length];
            for (int i = 0; i < fromList.length; i++) {
                fromList[i] = toTruffleStringUncached(node.names[i].name);
            }

            b.beginStoreLocal(module);
            b.beginImport();
            b.emitLoadConstant(tsModuleName);
            b.emitLoadConstant(fromList);
            b.emitLoadConstant(node.level);
            b.endImport();
            b.endStoreLocal();

            for (AliasTy name : node.names) {
                String asName = name.asName == null ? name.name : name.asName;
                beginStoreLocal(asName);

                b.beginImportFrom();
                b.emitLoadLocal(module);
                b.emitLoadConstant(toTruffleStringUncached(name.name));
                b.endImportFrom();

                endStoreLocal(asName);
            }

            b.endBlock();
        }

        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.Match node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(MatchCaseTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchAs node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchClass node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchMapping node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchOr node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchSequence node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchSingleton node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchStar node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(PatternTy.MatchValue node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.Nonlocal node) {
        return null;
    }

    @Override
    public Void visit(StmtTy.Raise node) {
        beginNode(node);
        b.beginRaise();

        if (node.exc != null) {
            node.exc.accept(this);
        } else {
            b.emitLoadConstant(PNone.NO_VALUE);
        }

        if (node.cause != null) {
            node.cause.accept(this);
        } else {
            b.emitLoadConstant(PNone.NO_VALUE);
        }

        b.emitLoadConstant(true);

        b.endRaise();
        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.Return node) {
        beginNode(node);

        b.beginReturn();
        if (node.value != null) {
            node.value.accept(this);
        } else {
            b.emitLoadConstant(PNone.NONE);
        }
        b.endReturn();

        endNode(node);
        return null;
    }

    @Override
    public Void visit(StmtTy.Try node) {
        beginNode(node);
// if (len(node.finalBody) != 0) {
// b.beginFinallyTry();
//
// b.beginBlock();
// visitSequence(node.finalBody);
// b.endBlock();
//
// b.beginBlock();
// }

        // try {
        // << body >>
        // catch ex {
        // if (CheckHandler(ex)) {
        // << handler 1 >>
        // goto afterElse
        // }
        // ...
        // Throw(ex)
        // }
        // << else >>
        // afterElse:

        OperationLocal ex = b.createLocal();
        OperationLabel afterElse = b.createLabel();

        b.beginTryCatch(ex);

        b.beginBlock();
        visitSequence(node.body);
        b.endBlock();

        b.beginBlock();

        if (node.handlers != null) {
            for (ExceptHandlerTy h : node.handlers) {
                ExceptHandlerTy.ExceptHandler handler = (ExceptHandlerTy.ExceptHandler) h;
                if (handler.type != null) {

                    b.beginIfThen();

                    b.beginExceptMatch();
                    b.emitLoadLocal(ex);
                    handler.type.accept(this);
                    b.endExceptMatch();
                }

                b.beginBlock(); // {

                OperationLocal exState = b.createLocal();

// b.beginFinallyTry();
//
// b.beginRestoreExceptionState();
// b.emitLoadLocal(exState);
// b.endRestoreExceptionState();

                b.beginBlock(); // {

                b.beginStoreLocal(exState);
                b.emitSaveExceptionState();
                b.endStoreLocal();

                b.beginSetExceptionState();
                b.emitLoadLocal(ex);
                b.endSetExceptionState();

                if (handler.name != null) {
                    beginStoreLocal(handler.name);
                    b.beginUnwrapException();
                    b.emitLoadLocal(ex);
                    b.endUnwrapException();
                    endStoreLocal(handler.name);
                }

                visitSequence(handler.body);
                b.emitBranch(afterElse);
                b.endBlock(); // }

// b.endFinallyTry();

                b.endBlock(); // }

                if (handler.type != null) {
                    b.endIfThen();
                }
            }
        }

        b.beginThrow();
        b.emitLoadLocal(ex);
        b.endThrow();

        b.endBlock();

        b.endTryCatch();

        visitSequence(node.orElse);

        b.emitLabel(afterElse);

// if (len(node.finalBody) != 0) {
// b.endBlock();
// b.endFinallyTry();
// }
        endNode(node);

        return null;
    }

    @Override
    public Void visit(ExceptHandlerTy.ExceptHandler node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.While node) {
        beginNode(node);

        OperationLabel oldBreakLabel = breakLabel;
        OperationLabel oldContinueLabel = continueLabel;

        breakLabel = b.createLabel();
        continueLabel = b.createLabel();

        b.emitLabel(continueLabel);
        b.beginWhile();
        // {

        visitCondition(node.test);

        visitStatements(node.body);
        // }
        b.endWhile();

        visitStatements(node.orElse);

        b.emitLabel(breakLabel);

        breakLabel = oldBreakLabel;
        continueLabel = oldContinueLabel;

        endNode(node);
        return null;
    }

    private void visitWithRecurse(WithItemTy[] items, int index, StmtTy[] body) {
        if (index == items.length) {
            visitSequence(body);
            return;
        }

        WithItemTy item = items[index];

        beginNode(item);

        OperationLocal mgr = b.createLocal();
        b.beginStoreLocal(mgr);
        item.contextExpr.accept(this);
        b.endStoreLocal();

        OperationLocal exit = b.createLocal();
        OperationLocal value = b.createLocal();
        OperationLocal ex = b.createLocal();

        b.beginContextManagerEnter(exit, value);
        b.emitLoadLocal(mgr);
        b.endContextManagerEnter();

// b.beginFinallyTryNoExcept();
//
// b.beginCallQuaternaryMethod();
// b.emitLoadLocal(exit);
// b.emitLoadLocal(mgr);
// b.emitLoadConstant(PNone.NONE);
// b.emitLoadConstant(PNone.NONE);
// b.emitLoadConstant(PNone.NONE);
// b.endCallQuaternaryMethod();

        b.beginTryCatch(ex);

        b.beginBlock();

        if (item.optionalVars != null) {
            item.optionalVars.accept(new StoreVisitor(() -> b.emitLoadLocal(value)));
        }

        visitWithRecurse(items, index + 1, body);

        b.endBlock();

        b.beginIfThen();

        b.beginNot();
        b.beginContextManagerExit();
        b.emitLoadLocal(exit);
        b.emitLoadLocal(mgr);
        b.emitLoadLocal(ex);
        b.endContextManagerExit();
        b.endNot();

        b.beginRaise();
        b.emitLoadConstant(PNone.NO_VALUE);
        b.emitLoadConstant(PNone.NO_VALUE);
        b.emitLoadConstant(true);
        b.endRaise();

        b.endIfThen();

        b.endTryCatch();

// b.endFinallyTryNoExcept();

        endNode(item);
    }

    @Override
    public Void visit(StmtTy.With node) {
        beginNode(node);
        visitWithRecurse(node.items, 0, node.body);
        endNode(node);
        return null;
    }

    @Override
    public Void visit(WithItemTy node) {
        throw new UnsupportedOperationException("" + node.getClass());
    }

    @Override
    public Void visit(StmtTy.Break aThis) {
        beginNode(aThis);

        assert breakLabel != null;
        b.emitBranch(breakLabel);

        endNode(aThis);
        return null;
    }

    @Override
    public Void visit(StmtTy.Continue aThis) {
        beginNode(aThis);

        assert continueLabel != null;
        b.emitBranch(continueLabel);

        endNode(aThis);
        return null;
    }

    @Override
    public Void visit(StmtTy.Pass aThis) {
        return null;
    }

}