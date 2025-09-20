/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.nodes;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.MemMoveNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.MemMoveNodeGen;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

public class MemMoveNodeTests {

    private GilNode.UncachedAcquire gil;

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("mac"));
    }

    @Before
    public void setUp() {
        PythonTests.enterContext(Map.of("python.IsolateNativeModules", "true"), new String[0]);
        this.gil = GilNode.uncachedAcquire();
        CApiContext.ensureCapiWasLoaded("internal");
    }

    @After
    public void tearDown() {
        this.gil.close();
        PythonTests.closeContext();
    }

    @Test
    public void doArrayBasedSpecialization() {
        final var storage = new IntSequenceStorage(new int[]{1, 2, 2, 3, 0});
        new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                var memMoveNode = MemMoveNodeGen.getUncached();
                memMoveNode.execute(this, storage, 3, 2, 2);
                return PNone.NO_VALUE;
            }
        }.getCallTarget().call();

        var expectedArr = new int[]{1, 2, 2, 2, 3};
        Assert.assertArrayEquals(expectedArr, storage.getInternalIntArray());
    }

    @Test
    public void doOtherSpecialization() {
        final NativeSequenceStorage storage = StorageToNativeNode.executeUncached(new Object[]{1, 2, 2, 3, 0}, 5);

        new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                MemMoveNode.executeUncached(storage, 3, 2, 2);
                return PNone.NO_VALUE;
            }

        }.getCallTarget().call();

        Assert.assertEquals(1L, ((Number) GetItemScalarNode.executeUncached(storage, 0)).longValue());
        Assert.assertEquals(2L, ((Number) GetItemScalarNode.executeUncached(storage, 1)).longValue());
        Assert.assertEquals(2L, ((Number) GetItemScalarNode.executeUncached(storage, 2)).longValue());
        Assert.assertEquals(2L, ((Number) GetItemScalarNode.executeUncached(storage, 3)).longValue());
        Assert.assertEquals(3L, ((Number) GetItemScalarNode.executeUncached(storage, 4)).longValue());
    }
}
