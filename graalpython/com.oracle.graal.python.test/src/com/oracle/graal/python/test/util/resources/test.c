// gcc -shared -Wl,soname=test test.c -o test.so
#include "stdio.h"

int magic() {
    return printf("%d\n", 42);
}
