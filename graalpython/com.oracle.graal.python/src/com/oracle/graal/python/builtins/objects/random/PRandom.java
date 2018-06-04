package com.oracle.graal.python.builtins.objects.random;

import java.util.Random;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;

public class PRandom extends PythonBuiltinObject {
    private Random javaRandom = new Random();

    public PRandom(PythonClass cls) {
        super(cls);
    }

    public void setSeed(long seed) {
        javaRandom.setSeed(seed);
    }

    public long nextLong() {
        return javaRandom.nextLong();
    }

    public double nextDouble() {
        return javaRandom.nextDouble();
    }

    public Random getJavaRandom() {
        return javaRandom;
    }

    public void setJavaRandom(Random random) {
        javaRandom = random;
    }
}
