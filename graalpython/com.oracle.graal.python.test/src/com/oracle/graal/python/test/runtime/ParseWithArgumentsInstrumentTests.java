/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.runtime;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.Test;

import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;

public class ParseWithArgumentsInstrumentTests {
    private static final String TEST_INSTRUMENT_ID = "parse-with-arguments-instrument-tests";

    @SuppressWarnings("unchecked")
    @Test
    public void instrumentParseWithArgumentsDuringLanguageContextInitializationExecutes() {
        try (Engine engine = Engine.create()) {
            Instrument instrument = engine.getInstruments().get(TEST_INSTRUMENT_ID);
            Function<org.graalvm.polyglot.Source, Void> registrar = instrument.lookup(Function.class);
            registrar.apply(org.graalvm.polyglot.Source.create("python", "x + 1"));
            try (Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
                assertEquals(42, context.eval("python", "42").asInt());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void parsedModuleCodeReexecutionReturnsFunction() {
        try (Engine engine = Engine.create();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
            context.eval("python", """
                            import sys
                            parsed_module_codes = []
                            def capture_module_code(frame, event, arg):
                                # Capture the synthetic module while it defines execute(x). By the
                                # time the supplied source runs inside execute, this frame has returned.
                                if event == 'call' and frame.f_code.co_filename == 'parse-with-arguments.py' and frame.f_code.co_name == '<module>':
                                    parsed_module_codes.append(frame.f_code)
                            sys.setprofile(capture_module_code)
                            """);
            Function<org.graalvm.polyglot.Source, Void> registrar = engine.getInstruments().get(TEST_INSTRUMENT_ID).lookup(Function.class);
            context.enter();
            try {
                // The polyglot entry point still runs the module and then calls execute(41), returning 42.
                registrar.apply(org.graalvm.polyglot.Source.newBuilder("python", "x + 1", "parse-with-arguments.py").buildLiteral());
            } finally {
                context.leave();
            }
            assertEquals(42, context.eval("python", """
                            sys.setprofile(None)
                            assert len(parsed_module_codes) == 1
                            module_code = parsed_module_codes[0]
                            # Previously this code object stored RootNodeWithArguments: its target
                            # included both module execution and the subsequent call to execute(x).
                            # eval() actually crashed with ClassCastException because Python's call
                            # machinery expects a PRootNode, which that wrapper is not.
                            # Storing the module root changes the code object's executable target:
                            # re-execution now only defines and returns execute, without calling it.
                            function = eval(module_code, {})
                            assert callable(function)
                            assert function.__code__ in module_code.co_consts
                            assert module_code.co_argcount == 0
                            assert function.__code__.co_argcount == 1
                            assert function.__code__.co_varnames[0] == 'x'
                            # co_code was empty for the wrapper; it now exposes the module bytecode.
                            assert module_code.co_code
                            function(41)
                            """).asInt());
        }
    }

    @TruffleInstrument.Registration(id = TEST_INSTRUMENT_ID, services = Function.class)
    public static final class ParseWithArgumentsInitializationInstrument extends TruffleInstrument {
        private final ContextLocal<boolean[]> parsed = locals.createContextLocal((ctx) -> new boolean[1]);

        @Override
        protected void onCreate(Env env) {
            Function<org.graalvm.polyglot.Source, Void> registerSource = (polyglotSource) -> {
                com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder(
                                polyglotSource.getLanguage(), polyglotSource.getCharacters(), polyglotSource.getName()).build();
                env.getInstrumenter().attachContextsListener(new ContextsListener() {
                    @Override
                    public void onContextCreated(TruffleContext context) {
                    }

                    @Override
                    public void onLanguageContextCreate(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextInitialize(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                        if (!"python".equals(language.getId())) {
                            return;
                        }
                        boolean[] seen = parsed.get(context);
                        if (seen[0]) {
                            return;
                        }
                        seen[0] = true;
                        try {
                            assertEquals(42, env.parse(source, "x").call(41));
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    }

                    @Override
                    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onContextClosed(TruffleContext context) {
                    }
                }, true);
                return null;
            };
            env.registerService(registerSource);
        }
    }
}
