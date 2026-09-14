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
package com.oracle.graal.python.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.nodes.Node;

public class UnavailableFrameLocationTest {

    @Test
    public void unavailableLocals() {
        checkFrame(false, false);
    }

    @Test
    public void unavailableGeneratorLocals() {
        checkFrame(true, false);
    }

    @Test
    public void preserveCapturedLocals() {
        checkFrame(false, true);
    }

    private static void checkFrame(boolean generator, boolean captureFirst) {
        try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
            Value checkLocals = context.eval("python", """
                            def check_locals(frame, captured):
                                proxy = frame.f_locals
                                if captured:
                                    assert proxy['local_value'] == 42
                                else:
                                    assert len(proxy) == 0
                                    assert list(proxy) == []
                                    assert proxy.copy() == {}
                                    assert 'local_value' not in proxy
                                proxy['extra'] = 123
                                assert proxy['extra'] == 123
                                assert 'extra' in proxy
                                assert proxy.copy()['extra'] == 123
                                del proxy['extra']
                                assert 'extra' not in proxy
                            check_locals
                            """);
            context.getBindings("python").putMember("capture", (ProxyExecutable) args -> {
                // This allows us to manually trigger stack walk and process the result using MaterializeFrameNode
                // passing various combinations of arguments to it

                ReadFrameNode.StackWalkResult result = ReadFrameNode.getFrame(null, null, FrameAccess.MATERIALIZE,
                                ReadFrameNode.AllPythonFramesSelector.INSTANCE, 0, CallerFlags.ALL_FRAME_FLAGS);
                assertNotNull(result);
                MaterializeFrameNode materialize = MaterializeFrameNode.getUncached();
                assertThrows(AssertionError.class, () -> materialize.execute(null, false, false, result.frame()));
                BytecodeFrame captured = null;
                if (captureFirst) {
                    captured = materialize.execute(result.callNode(), true, true, result.frame()).getBytecodeFrame();
                    assertNotNull(captured);
                }
                Node unavailable = PythonLanguage.get(null).unavailableSafepointLocation;
                PFrame pyFrame = materialize.execute(unavailable, true, true, result.frame());
                assertNull(pyFrame.getBytecodeNode());
                assertEquals(-1, pyFrame.getBci());
                assertEquals(-1, pyFrame.getLine());
                assertSame(captured, pyFrame.getBytecodeFrame());
                assertFalse(pyFrame.syncsLocals());
                assertFalse(pyFrame.outdatedCallerFlags(CallerFlags.NEEDS_MATERIALIZED_LOCALS));
                assertNotNull(GetFrameLocalsNode.executeUncached(pyFrame, true));
                checkLocals.execute(context.asValue(pyFrame), captureFirst);

                // A later observation with a valid location must resume normal locals capture.
                assertSame(pyFrame, materialize.execute(result.callNode(), true, true, result.frame()));
                assertNotNull(pyFrame.getBytecodeNode());
                assertNotNull(pyFrame.getBytecodeFrame());
                assertTrue(pyFrame.hasMaterializedFrame() || pyFrame.syncsLocals());
                return null;
            });
            context.eval("python", generator ? """
                            def target():
                                local_value = 42
                                capture()
                                yield local_value
                            assert next(target()) == 42
                            """ : """
                            def target():
                                local_value = 42
                                capture()
                            target()
                            """);
        }
    }
}
