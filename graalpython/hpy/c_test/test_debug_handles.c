#include <stdarg.h>
#include "acutest.h" // https://github.com/mity/acutest
#include "hpy/debug/src/debug_internal.h"

static void check_DHQueue(DHQueue *q, HPy_ssize_t size, ...)
{
    va_list argp;
    va_start(argp, size);
    DHQueue_sanity_check(q);
    TEST_CHECK(q->size == size);
    DHQueueNode *h = q->head;
    while(h != NULL) {
        DHQueueNode *expected = va_arg(argp, DHQueueNode*);
        TEST_CHECK(h == expected);
        h = h->next;
    }
    va_end(argp);
}

void test_DHQueue_init(void)
{
    DHQueue q;
    DHQueue_init(&q);
    TEST_CHECK(q.head == NULL);
    TEST_CHECK(q.tail == NULL);
    TEST_CHECK(q.size == 0);
    DHQueue_sanity_check(&q);
}

void test_DHQueue_append(void)
{
    DHQueue q;
    DHQueueNode h1;
    DHQueueNode h2;
    DHQueueNode h3;
    DHQueue_init(&q);
    DHQueue_append(&q, &h1);
    DHQueue_append(&q, &h2);
    DHQueue_append(&q, &h3);
    check_DHQueue(&q, 3, &h1, &h2, &h3);
}

void test_DHQueue_popfront(void)
{
    DHQueue q;
    DHQueueNode h1;
    DHQueueNode h2;
    DHQueueNode h3;
    DHQueue_init(&q);
    DHQueue_append(&q, &h1);
    DHQueue_append(&q, &h2);
    DHQueue_append(&q, &h3);

    TEST_CHECK(DHQueue_popfront(&q) == &h1);
    check_DHQueue(&q, 2, &h2, &h3);

    TEST_CHECK(DHQueue_popfront(&q) == &h2);
    check_DHQueue(&q, 1, &h3);

    TEST_CHECK(DHQueue_popfront(&q) == &h3);
    check_DHQueue(&q, 0);
}


void test_DHQueue_remove(void)
{
    DHQueue q;
    DHQueueNode h1;
    DHQueueNode h2;
    DHQueueNode h3;
    DHQueueNode h4;
    DHQueue_init(&q);
    DHQueue_append(&q, &h1);
    DHQueue_append(&q, &h2);
    DHQueue_append(&q, &h3);
    DHQueue_append(&q, &h4);

    DHQueue_remove(&q, &h3); // try to remove something in the middle
    check_DHQueue(&q, 3, &h1, &h2, &h4);

    DHQueue_remove(&q, &h1); // try to remove the head
    check_DHQueue(&q, 2, &h2, &h4);

    DHQueue_remove(&q, &h4); // try to remove the tail
    check_DHQueue(&q, 1, &h2);

    DHQueue_remove(&q, &h2); // try to remove the only element
    check_DHQueue(&q, 0);
}

#define MYTEST(X) { #X, X }

TEST_LIST = {
    MYTEST(test_DHQueue_init),
    MYTEST(test_DHQueue_append),
    MYTEST(test_DHQueue_popfront),
    MYTEST(test_DHQueue_remove),
    { NULL, NULL }
};
