package com.oracle.graal.python.test.nodes.storage;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemSliceNode;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GetItemSliceNodeTests {

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    @Test
    public void intGetSlice() {
        var storage = new RootNode(null) {
            @Child @SuppressWarnings("FieldMayBeFinal") private GetItemSliceNode getItemSliceNode = GetItemSliceNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                var intStorage = new IntSequenceStorage(new int[]{1, 2, 3, 4, 5, 6});
                return getItemSliceNode.execute(intStorage, 1, 4, 1, 3);
            }
        }.getCallTarget().call();

        assertEquals(IntSequenceStorage.class, storage.getClass());
        var intStorage = (IntSequenceStorage) storage;
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, intStorage.getInternalIntArray()[i]);
        }
    }

    @Test
    public void longGetSlice() {
        var storage = new RootNode(null) {
            @Child @SuppressWarnings("FieldMayBeFinal") private GetItemSliceNode getItemSliceNode = GetItemSliceNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                var longStorage = new LongSequenceStorage(new long[]{1, 2, 3, 4, 5, 6});
                return getItemSliceNode.execute(longStorage, 1, 4, 1, 3);
            }
        }.getCallTarget().call();

        assertEquals(LongSequenceStorage.class, storage.getClass());
        var longStorage = (LongSequenceStorage) storage;
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, longStorage.getInternalLongArray()[i]);
        }
    }

    @Test
    public void doubleGetSlice() {
        var storage = new RootNode(null) {
            @Child @SuppressWarnings("FieldMayBeFinal") private GetItemSliceNode getItemSliceNode = GetItemSliceNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                var doubleStorage = new DoubleSequenceStorage(new double[]{1, 2, 3, 4, 5, 6});
                return getItemSliceNode.execute(doubleStorage, 1, 4, 1, 3);
            }
        }.getCallTarget().call();

        assertEquals(DoubleSequenceStorage.class, storage.getClass());
        var doubleStorage = (DoubleSequenceStorage) storage;
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, doubleStorage.getInternalDoubleArray()[i], 0);
        }
    }

    @Test
    public void byteGetSlice() {
        var storage = new RootNode(null) {
            @Child @SuppressWarnings("FieldMayBeFinal") private GetItemSliceNode getItemSliceNode = GetItemSliceNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                var byteStorage = new ByteSequenceStorage(new byte[]{1, 2, 3, 4, 5, 6});
                return getItemSliceNode.execute(byteStorage, 1, 4, 1, 3);
            }
        }.getCallTarget().call();

        assertEquals(ByteSequenceStorage.class, storage.getClass());
        var byteStorage = (ByteSequenceStorage) storage;
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, byteStorage.getInternalByteArray()[i]);
        }
    }

    @Test
    public void objectGetSlice() {
        var storage = new RootNode(null) {
            @Child @SuppressWarnings("FieldMayBeFinal") private GetItemSliceNode getItemSliceNode = GetItemSliceNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                var objectStorage = new ObjectSequenceStorage(new Object[]{1, 2, 3, 4, 5, 6});
                return getItemSliceNode.execute(objectStorage, 1, 4, 1, 3);
            }
        }.getCallTarget().call();

        assertEquals(ObjectSequenceStorage.class, storage.getClass());
        var objectStorage = (ObjectSequenceStorage) storage;
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, objectStorage.getInternalArray()[i]);
        }
    }

    @Test
    public void boolGetSlice() {
        var storage = new RootNode(null) {
            @Child @SuppressWarnings("FieldMayBeFinal") private GetItemSliceNode getItemSliceNode = GetItemSliceNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                var boolStorage = new BoolSequenceStorage(new boolean[]{true, false, false, true, true, false});
                return getItemSliceNode.execute(boolStorage, 1, 4, 1, 3);
            }
        }.getCallTarget().call();

        assertEquals(BoolSequenceStorage.class, storage.getClass());
        var boolArray = ((BoolSequenceStorage) storage).getInternalBoolArray();
        assertEquals(3, boolArray.length);
        assertFalse(boolArray[0]);
        assertFalse(boolArray[1]);
        assertTrue(boolArray[2]);
    }
}
