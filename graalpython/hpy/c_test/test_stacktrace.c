// Smoke test for the create_stacktrace function

#include <stdarg.h>
#include "acutest.h" // https://github.com/mity/acutest
#include "hpy/debug/src/debug_internal.h"

void test_create_stacktrace(void)
{
    char *trace;
    create_stacktrace(&trace, 16);
    if (trace != NULL) {
        printf("\n\nTRACE:\n%s\n\n", trace);
        free(trace);
    }
}

#define MYTEST(X) { #X, X }

TEST_LIST = {
        MYTEST(test_create_stacktrace),
        { NULL, NULL }
};