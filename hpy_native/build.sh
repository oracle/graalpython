#/bin/sh

clang -I ../graalpython/include -I ~/Source/jdk/Contents/Home/include -I ~/Source/jdk/Contents/Home/include/darwin -O3 -g -shared hpy_native.c -o hpy_native.so; nm -g hpy_native.so
