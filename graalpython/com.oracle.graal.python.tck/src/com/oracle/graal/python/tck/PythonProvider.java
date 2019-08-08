/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.tck;

import static org.graalvm.polyglot.tck.TypeDescriptor.ANY;
import static org.graalvm.polyglot.tck.TypeDescriptor.ARRAY;
import static org.graalvm.polyglot.tck.TypeDescriptor.BOOLEAN;
import static org.graalvm.polyglot.tck.TypeDescriptor.EXECUTABLE;
import static org.graalvm.polyglot.tck.TypeDescriptor.HOST_OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.NATIVE_POINTER;
import static org.graalvm.polyglot.tck.TypeDescriptor.NULL;
import static org.graalvm.polyglot.tck.TypeDescriptor.NUMBER;
import static org.graalvm.polyglot.tck.TypeDescriptor.OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.STRING;
import static org.graalvm.polyglot.tck.TypeDescriptor.INSTANTIABLE;
import static org.graalvm.polyglot.tck.TypeDescriptor.array;
import static org.graalvm.polyglot.tck.TypeDescriptor.executable;
import static org.graalvm.polyglot.tck.TypeDescriptor.intersection;
import static org.graalvm.polyglot.tck.TypeDescriptor.union;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;

public class PythonProvider implements LanguageProvider {

    private static final String ID = "python";

    // in Python, boolean inherits from integer
    private static final TypeDescriptor PNUMBER = TypeDescriptor.union(NUMBER, BOOLEAN);
    private static final TypeDescriptor NUMBER_OBJECT = TypeDescriptor.union(NUMBER, BOOLEAN, OBJECT, array(ANY));
    private static final TypeDescriptor PSEQUENCE_OBJECT = TypeDescriptor.union(array(ANY), STRING);

    // Python types are just objects
    private static final TypeDescriptor PYTHON_TYPE = TypeDescriptor.union(OBJECT, INSTANTIABLE);

    public String getId() {
        return ID;
    }

    public Value createIdentityFunction(Context context) {
        return context.eval(ID, "lambda x: x");
    }

    private static void addValueSnippet(Context context, List<Snippet> snippets, String id, TypeDescriptor returnType, String code) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).build());
    }

    private static void addExpressionSnippet(Context context, List<Snippet> snippets, String id, String code, TypeDescriptor returnType, TypeDescriptor... parameterTypes) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).parameterTypes(parameterTypes).build());
    }

    private static void addExpressionSnippet(Context context, List<Snippet> snippets, String id, String code, TypeDescriptor returnType, ResultVerifier rv, TypeDescriptor... parameterTypes) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).resultVerifier(rv).parameterTypes(parameterTypes).build());
    }

    private static void addStatementSnippet(Context context, List<Snippet> snippets, String id, String code, TypeDescriptor returnType, TypeDescriptor... parameterTypes) {
        snippets.add(Snippet.newBuilder(id, context.eval(ID, code), returnType).parameterTypes(parameterTypes).build());
    }

    public Collection<? extends Snippet> createValueConstructors(Context context) {
        List<Snippet> snippets = new ArrayList<>();
        final TypeDescriptor noType = intersection();
        final TypeDescriptor allTypes = intersection(noType, NULL, BOOLEAN, NUMBER, STRING, HOST_OBJECT, NATIVE_POINTER, OBJECT, ARRAY, EXECUTABLE);
        // @formatter:off
        addValueSnippet(context, snippets, "BooleanType:True",  BOOLEAN,    "lambda: True");
        addValueSnippet(context, snippets, "BooleanType:False", BOOLEAN,    "lambda: False");
        addValueSnippet(context, snippets, "NoneType",          NULL,       "lambda: None");
        addValueSnippet(context, snippets, "IntType",           NUMBER,     "lambda: 1");
        addValueSnippet(context, snippets, "FloatType",         NUMBER,     "lambda: 1.1");
        addValueSnippet(context, snippets, "ComplexType",       OBJECT,     "lambda: 1.0j");
        addValueSnippet(context, snippets, "StringType",        STRING,     "lambda: 'spam'");
        addValueSnippet(context, snippets, "TupleType:Empty",   array(allTypes),                     "lambda: ()");
        addValueSnippet(context, snippets, "TupleType:Number",  array(NUMBER),                  "lambda: (1, 2.1)");
        addValueSnippet(context, snippets, "TupleType:String",  array(STRING),                  "lambda: ('foo', 'bar')");
        addValueSnippet(context, snippets, "TupleType:Mixed",   array(union(NUMBER, STRING)),   "lambda: ('foo', 1)");
        addValueSnippet(context, snippets, "ListType:Empty",    array(allTypes),                     "lambda: []");
        addValueSnippet(context, snippets, "ListType:Number",   array(NUMBER),                  "lambda: [1, 2.1]");
        addValueSnippet(context, snippets, "ListType:String",   array(STRING),                  "lambda: ['foo', 'bar']");
        addValueSnippet(context, snippets, "ListType:Mixed",    array(union(NUMBER, STRING)),   "lambda: ['foo', 1]");
        addValueSnippet(context, snippets, "DictType:Empty",    OBJECT,     "lambda: {}");
        addValueSnippet(context, snippets, "DictType:KeyString",   OBJECT,     "lambda: {'Bacon':1, 'Ham': 0}");
        addValueSnippet(context, snippets, "DictType:KeyNumber",   OBJECT,     "lambda: {1: 'Bacon', 0: 'Ham'}");

        // TODO remove '*args' from following value constructors once this is fixed in Truffle TCK
        addValueSnippet(context, snippets, "LambdaType:Id",     intersection(OBJECT, executable(ANY, ANY)),     "lambda: lambda x, *args: x");
        addValueSnippet(context, snippets, "LambdaType:+1",     intersection(OBJECT, executable(NUMBER, NUMBER)),     "lambda: lambda x, *args: x + 1");

        // @formatter:on
        return snippets;
    }

    public Collection<? extends Snippet> createExpressions(Context context) {
        List<Snippet> snippets = new ArrayList<>();

        // @formatter:off

        // addition
        addExpressionSnippet(context, snippets, "+",     "lambda x, y: x + y",  NUMBER_OBJECT, PNUMBER, PNUMBER);
        addExpressionSnippet(context, snippets, "+",     "lambda x, y: x + y",  array(ANY), PNoListCoercionVerifier.INSTANCE, array(ANY), array(ANY));

        // substraction
        addExpressionSnippet(context, snippets, "-",     "lambda x, y: x - y",  NUMBER_OBJECT, PNUMBER, PNUMBER);

        // multiplication
        addExpressionSnippet(context, snippets, "*",     "lambda x, y: x * y",  NUMBER_OBJECT, PNoArrayVerifier.INSTANCE, PNUMBER, PNUMBER);
        addExpressionSnippet(context, snippets, "*",     "lambda x, y: x * y",  PSEQUENCE_OBJECT, PSequenceMultiplicationVerifier.INSTANCE, PNUMBER, PSEQUENCE_OBJECT);
        addExpressionSnippet(context, snippets, "*",     "lambda x, y: x * y",  PSEQUENCE_OBJECT, PSequenceMultiplicationVerifier.INSTANCE, PSEQUENCE_OBJECT, PNUMBER);

        // division
        addExpressionSnippet(context, snippets, "/",     "lambda x, y: x / y",  NUMBER_OBJECT, PDivByZeroVerifier.INSTANCE, PNUMBER, PNUMBER);

        // comparison
        addExpressionSnippet(context, snippets, ">",     "lambda x, y: x > y",  BOOLEAN, PNUMBER, PNUMBER);
        addExpressionSnippet(context, snippets, ">=",     "lambda x, y: x > y",  BOOLEAN, PNUMBER, PNUMBER);
        addExpressionSnippet(context, snippets, "<",     "lambda x, y: x > y",  BOOLEAN, PNUMBER, PNUMBER);
        addExpressionSnippet(context, snippets, "<=",     "lambda x, y: x > y",  BOOLEAN, PNUMBER, PNUMBER);

        // dictionaries
        addExpressionSnippet(context, snippets, "dict", "lambda: { 'x': 4 }", OBJECT);
        addExpressionSnippet(context, snippets, "dict", "lambda: { 'a': 1, 'b': 2, 'c': 3 }", OBJECT, new PDictMemberVerifier(arr("get", "keys", "update"), arr("a", "b", "c")));

        // @formatter:on
        return snippets;
    }

    public Collection<? extends Snippet> createStatements(Context context) {
        List<Snippet> snippets = new ArrayList<>();

        // @formatter:off

        // simple statements
        addStatementSnippet(context, snippets, "assert", "def gen_assert(v):\n" +
                                                         "    assert v or not v\n\n" +
                                                         "gen_assert", NULL, ANY);
        addStatementSnippet(context, snippets, "pass", "def gen_pass():\n" +
                                                         "    pass\n\n" +
                                                         "gen_pass", NULL);
        addStatementSnippet(context, snippets, "return", "def gen_return(x):\n" +
                                                         "    return x\n\n" +
                                                         "gen_return", ANY, ANY);
        addStatementSnippet(context, snippets, "yield", "def gen_yield0(last):\n" +
                                                      "    def gen(last):\n" +
                                                      "        yield True\n" +
                                                      "        yield False\n" +
                                                      "        yield last\n" +
                                                      "        return None\n" +
                                                      "    obj = gen(last)\n" +
                                                      "    return [next(gen(last)), next(obj), next(obj), next(obj)]\n" +
                                                      "gen_yield0", array(ANY), ANY);

        // compound statements

        // if
        addStatementSnippet(context, snippets, "if", "lambda p: True if p else False\n\n", BOOLEAN, ANY);

        // for
        addStatementSnippet(context, snippets, "for", "def gen_for(l):\n" +
                                                      "    for x in l:\n" +
                                                      "        return x\n\n" +
                                                      "gen_for", ANY, PSEQUENCE_OBJECT);

        addStatementSnippet(context, snippets, "for", "def gen_for(l):\n" +
                                                      "    first = None\n" +
                                                      "    for x in l:\n" +
                                                      "        first = x\n" +
                                                      "        break\n" +
                                                      "    return first\n\n" +
                                                      "gen_for", ANY, PSEQUENCE_OBJECT);

        // while
        addStatementSnippet(context, snippets, "while", "def gen_while(l):\n" +
                                                      "    cnt = 0\n" +
                                                      "    while l:\n" +
                                                      "        cnt = cnt + 1\n" +
                                                      "        break\n" +
                                                      "    return cnt\n\n" +
                                                      "gen_while", NUMBER, ANY);

        // try
        addStatementSnippet(context, snippets, "try-finally", "def gen_tryfinally():\n" +
                                                      "    try:\n" +
                                                      "        raise BaseException()\n" +
                                                      "        return None\n" +
                                                      "    finally:\n" +
                                                      "        return True\n" +
                                                      "gen_tryfinally", BOOLEAN);
        addStatementSnippet(context, snippets, "try-except", "def gen_tryexcept():\n" +
                                                      "    class CustomException(BaseException):\n" +
                                                      "        def __init__():\n" +
                                                      "            super('custom error message')\n" +
                                                      "    try:\n" +
                                                      "        raise CustomException()\n" +
                                                      "        return None\n" +
                                                      "    except BaseException as e:\n" +
                                                      "        return str(e) == 'custom error message'\n" +
                                                      "gen_tryexcept", BOOLEAN);
        addStatementSnippet(context, snippets, "try-except-else", "def gen_tryexceptelse0():\n" +
                                                      "    e = None\n" +
                                                      "    try:\n" +
                                                      "        raise BaseException()\n" +
                                                      "        e = None\n" +
                                                      "    except BaseException as ex:\n" +
                                                      "        e = True\n" +
                                                      "    else:\n" +
                                                      "        e = None\n" +
                                                      "    return e\n" +
                                                      "gen_tryexceptelse0", BOOLEAN);
        addStatementSnippet(context, snippets, "try-except-else", "def gen_tryexceptelse1():\n" +
                                                      "    e = None\n" +
                                                      "    try:\n" +
                                                      "        e = None\n" +
                                                      "    except BaseException as ex:\n" +
                                                      "        e = None\n" +
                                                      "    else:\n" +
                                                      "        e = True\n" +
                                                      "    return e\n" +
                                                      "gen_tryexceptelse1", BOOLEAN);

        // class definition
        addStatementSnippet(context, snippets, "class", "class Custom0:\n" +
                                                      "    def __init__(self, val):\n" +
                                                      "        self.val = val\n" +
                                                      "Custom0", PYTHON_TYPE, ANY);
        addStatementSnippet(context, snippets, "class", "class Custom1:\n" +
                                                      "    def __call__(self, val):\n" +
                                                      "        return val\n" +
                                                      "Custom1()", ANY, ANY);

        // @formatter:on

        return snippets;
    }

    public Collection<? extends Snippet> createScripts(Context context) {
        List<Snippet> snippets = new ArrayList<>();

        snippets.add(loadScript(context, "resources/sieve.py", array(NUMBER)));
        snippets.add(loadScript(context, "resources/euler31.py", NUMBER));
        snippets.add(loadScript(context, "resources/mandelbrot3.py", NULL));

        return snippets;
    }

    public Collection<? extends Source> createInvalidSyntaxScripts(Context context) {
        try {
            return Arrays.asList(createSource("resources/invalid_syntax0.py"),
                            createSource("resources/invalid_syntax1.py"));
        } catch (IOException e) {
            throw new AssertionError("IOException while creating a test script.", e);
        }
    }

    private static Snippet loadScript(
                    Context context,
                    String resourceName,
                    TypeDescriptor resultType) {
        try {
            Source src = createSource(resourceName);
            return Snippet.newBuilder(src.getName(), context.eval(src), resultType).build();
        } catch (IOException ioe) {
            throw new AssertionError("IOException while creating a test script.", ioe);
        }
    }

    private static Source createSource(String resourceName) throws IOException {
        int slashIndex = resourceName.lastIndexOf('/');
        String scriptName = slashIndex >= 0 ? resourceName.substring(slashIndex + 1) : resourceName;
        Reader in = new InputStreamReader(PythonProvider.class.getResourceAsStream(resourceName), "UTF-8");
        try {
            return Source.newBuilder(ID, in, scriptName).build();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private abstract static class PResultVerifier implements ResultVerifier {
    }

    private static class PSequenceMultiplicationVerifier extends PResultVerifier {

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            // Just restrict 'number' to integer value space
            if (isSequence(par0) && isNumber(par1)) {
                if (!hasMemoryError(snippetRun)) {
                    if (isInteger(par1) || isNegativeNumber(par1)) {
                        ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
                    } else {
                        if (snippetRun.getException() == null) {
                            throw new AssertionError("<sequence> * <non-integer> should give an error.");
                        }
                    }
                }
            } else if (isNumber(par0) && isSequence(par1)) {
                if (!hasMemoryError(snippetRun)) {
                    if (isInteger(par0) || isNegativeNumber(par0)) {
                        ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
                    } else {
                        if (snippetRun.getException() == null) {
                            throw new AssertionError("<non-integer> * <sequence> should give an error.");
                        }
                    }
                }
            } else if (isNumber(par0) && isMapping(par1) || isNumber(par1) && isMapping(par0)) {
                if (snippetRun.getException() != null) {
                    throw new AssertionError("Multipliation with mapping should give an error.");
                }
            } else if (isSequence(par0) && isSequence(par1)) {
                if (snippetRun.getException() == null) {
                    throw new AssertionError("<sequence> * <sequence> should give an error.");
                } else {
                    throw snippetRun.getException();
                }
            } else if (!(isNumber(par0) && isScalarVector(par1) || isScalarVector(par0) && isNumber(par1))) {
                ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
            }
        }

        protected static boolean isScalarVector(Value val) {
            return isNumber(val) && val.hasArrayElements() && val.getArraySize() == 1 && !isMapping(val);
        }

        protected static boolean isNumber(Value par0) {
            return par0.isNumber() || par0.isBoolean();
        }

        protected static boolean isNegativeNumber(Value par0) {
            return par0.isNumber() && par0.fitsInLong() && par0.asLong() < 0L;
        }

        protected static boolean isSequence(Value par0) {
            return !isNumber(par0) && (par0.isString() || (par0.hasArrayElements() && !isMapping(par0)));
        }

        protected static boolean isMapping(Value par0) {
            return par0.hasMembers() && par0.getMetaObject().toString().contains("dict");
        }

        private static boolean hasMemoryError(SnippetRun snippetRun) {
            PolyglotException exception = snippetRun.getException();
            if (exception != null && exception.isGuestException()) {
                return "MemoryError".equals(exception.getMessage()) || exception.getMessage().contains("OverflowError");
            }
            return false;
        }

        private static boolean isInteger(Value par0) {
            return (par0.isNumber() && par0.fitsInInt() || par0.isBoolean());
        }

        private static final PSequenceMultiplicationVerifier INSTANCE = new PSequenceMultiplicationVerifier();

    }

    /**
     * Only accepts exact matches of types.
     */
    private static class PNoListCoercionVerifier extends PResultVerifier {

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            // If both parameter values are lists, then ignore if they have different types. E.g.
            // ignore '(1,2) + [3,4]'.
            if (par0.hasArrayElements() && par1.hasArrayElements()) {
                if (par0.getMetaObject() == par1.getMetaObject()) {
                    ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
                }
            } else {
                ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
            }
        }

        private static final PNoListCoercionVerifier INSTANCE = new PNoListCoercionVerifier();
    }

    /**
     * Foreign objects may be array-ish and boxed (e.g. if they have just one element). In this
     * case, we still treat them as arrays.
     */
    private static class PNoArrayVerifier extends PResultVerifier {

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            if (isNumber(par0) && isNumber(par1)) {
                if (!(par0.hasArrayElements() || par1.hasArrayElements())) {
                    ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
                }
            } else {
                ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
            }
        }

        private static boolean isNumber(Value val) {
            return val.isNumber() || val.isBoolean();
        }

        private static final PNoArrayVerifier INSTANCE = new PNoArrayVerifier();
    }

    /**
     * Only accepts exact matches of types.
     */
    private static class PDivByZeroVerifier extends PResultVerifier {

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            List<? extends Value> parameters = snippetRun.getParameters();
            assert parameters.size() == 2;

            Value par0 = parameters.get(0);
            Value par1 = parameters.get(1);

            // If anumber/Boolean should be divided, ignore if divisor is Boolean false
            if (!par0.isNumber() && !par0.isBoolean() || !par1.isBoolean() || par1.asBoolean()) {
                ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
            }
        }

        private static final PDivByZeroVerifier INSTANCE = new PDivByZeroVerifier();
    }

    /**
     * Only accepts exact matches of types.
     */
    private static class PDictMemberVerifier extends PResultVerifier {

        private final String[] expectedMembers;
        private final String[] unexpectedMembers;

        public PDictMemberVerifier(String[] expectedMembers, String[] unexpectedMembers) {
            this.expectedMembers = expectedMembers;
            this.unexpectedMembers = unexpectedMembers;
        }

        public void accept(SnippetRun snippetRun) throws PolyglotException {
            ResultVerifier.getDefaultResultVerifier().accept(snippetRun);

            Value result = snippetRun.getResult();
            if (result.hasMembers()) {
                for (String expectedMember : expectedMembers) {
                    if (!result.hasMember(expectedMember)) {
                        throw new AssertionError("Expected member missing: " + expectedMember);
                    }
                }
                for (String unexpectedMember : unexpectedMembers) {
                    if (result.hasMember(unexpectedMember)) {
                        throw new AssertionError("Unexpected member present: " + unexpectedMember);
                    }
                }
            }
        }
    }

    private static String[] arr(String... strings) {
        return strings;
    }

}
