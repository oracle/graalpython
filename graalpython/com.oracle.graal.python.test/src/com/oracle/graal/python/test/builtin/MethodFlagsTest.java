package com.oracle.graal.python.test.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.strings.TruffleString;

public class MethodFlagsTest {
    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    private static void assertMethodConsistentWithFlag(PythonBuiltinClassType clazz, long flags, TruffleString methodName) {
        Object method = LookupAttributeInMRONode.Dynamic.getUncached().execute(clazz, methodName);
        if (method instanceof PDecoratedMethod) {
            // Ignore classmethods
            method = PNone.NO_VALUE;
        }
        if ((clazz.getMethodsFlags() & flags) != 0) {
            assertNotEquals(
                            String.format("%s has no method %s even though it sets corresponding method flags;", clazz.name(), methodName),
                            PNone.NO_VALUE, method);
        } else {
            assertEquals(
                            String.format("%s has method %s but it doesn't set the corresponding method flag;", clazz.name(), methodName),
                            PNone.NO_VALUE, method);
        }
    }

    @Test
    public void testMethodFlagsConsistency() {
        for (PythonBuiltinClassType clazz : PythonBuiltinClassType.VALUES) {
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_ADD | MethodsFlags.SQ_CONCAT, SpecialMethodNames.T___ADD__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_SUBTRACT, SpecialMethodNames.T___SUB__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_MULTIPLY | MethodsFlags.SQ_REPEAT, SpecialMethodNames.T___MUL__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_REMAINDER, SpecialMethodNames.T___MOD__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_DIVMOD, SpecialMethodNames.T___DIVMOD__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_POWER, SpecialMethodNames.T___POW__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_NEGATIVE, SpecialMethodNames.T___NEG__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_POSITIVE, SpecialMethodNames.T___POS__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_ABSOLUTE, SpecialMethodNames.T___ABS__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_BOOL, SpecialMethodNames.T___BOOL__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INVERT, SpecialMethodNames.T___INVERT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_LSHIFT, SpecialMethodNames.T___LSHIFT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_RSHIFT, SpecialMethodNames.T___RSHIFT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_AND, SpecialMethodNames.T___AND__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_XOR, SpecialMethodNames.T___XOR__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_OR, SpecialMethodNames.T___OR__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INT, SpecialMethodNames.T___INT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_FLOAT, SpecialMethodNames.T___FLOAT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_ADD | MethodsFlags.SQ_INPLACE_CONCAT, SpecialMethodNames.T___IADD__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_SUBTRACT, SpecialMethodNames.T___ISUB__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_MULTIPLY | MethodsFlags.SQ_INPLACE_REPEAT, SpecialMethodNames.T___IMUL__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_REMAINDER, SpecialMethodNames.T___IMOD__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_POWER, SpecialMethodNames.T___IPOW__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_LSHIFT, SpecialMethodNames.T___ILSHIFT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_RSHIFT, SpecialMethodNames.T___IRSHIFT__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_AND, SpecialMethodNames.T___IAND__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_XOR, SpecialMethodNames.T___IXOR__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_OR, SpecialMethodNames.T___IOR__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_FLOOR_DIVIDE, SpecialMethodNames.T___FLOORDIV__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_TRUE_DIVIDE, SpecialMethodNames.T___TRUEDIV__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_FLOOR_DIVIDE, SpecialMethodNames.T___IFLOORDIV__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_TRUE_DIVIDE, SpecialMethodNames.T___ITRUEDIV__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INDEX, SpecialMethodNames.T___INDEX__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_MATRIX_MULTIPLY, SpecialMethodNames.T___MATMUL__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.NB_INPLACE_MATRIX_MULTIPLY, SpecialMethodNames.T___IMATMUL__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.SQ_LENGTH | MethodsFlags.MP_LENGTH, SpecialMethodNames.T___LEN__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.SQ_ITEM | MethodsFlags.MP_SUBSCRIPT, SpecialMethodNames.T___GETITEM__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.SQ_ASS_ITEM | MethodsFlags.MP_ASS_SUBSCRIPT, SpecialMethodNames.T___SETITEM__);
            assertMethodConsistentWithFlag(clazz, MethodsFlags.SQ_CONTAINS, SpecialMethodNames.T___CONTAINS__);
        }
    }
}
