/* MIT License
 *
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "debug_internal.h"

// TODO: we need to make DHQueue thread-safe if we want to use the same
// context in multiple threads
void DHQueue_init(DHQueue *q) {
    q->head = NULL;
    q->tail = NULL;
    q->size = 0;
}

void DHQueue_append(DHQueue *q, DHQueueNode *h) {
    if (q->head == NULL) {
        h->prev = NULL;
        h->next = NULL;
        q->head = h;
        q->tail = h;
    } else {
        h->next = NULL;
        h->prev = q->tail;
        q->tail->next = h;
        q->tail = h;
    }
    q->size++;
}

DHQueueNode *DHQueue_popfront(DHQueue *q)
{
    assert(q->size > 0);
    assert(q->head != NULL);
    DHQueueNode *head = q->head;
    if (q->size == 1) {
        q->head = NULL;
        q->tail = NULL;
        q->size = 0;
    }
    else {
        q->head = head->next;
        q->head->prev = NULL;
        q->size--;
    }
    // the following is not strictly necessary, but it makes thing much easier
    // to debug in case of bugs
    head->next = NULL;
    head->prev = NULL;
    return head;
}

void DHQueue_remove(DHQueue *q, DHQueueNode *h)
{
#ifndef NDEBUG
    // if we are debugging, let's check that h is effectively in the queue
    DHQueueNode *it = q->head;
    bool found = false;
    while(it != NULL) {
        if (it == h) {
            found = true;
            break;
        }
        it = it->next;
    }
    assert(found);
#endif
    if (q->size == 1) {
        q->head = NULL;
        q->tail = NULL;
    } else if (h == q->head) {
        assert(h->prev == NULL);
        q->head = h->next;
        q->head->prev = NULL;
    } else if (h == q->tail) {
        assert(h->next == NULL);
        q->tail = h->prev;
        q->tail->next = NULL;
    }
    else {
        h->prev->next = h->next;
        h->next->prev = h->prev;
    }
    q->size--;
    h->next = NULL;
    h->prev = NULL;
}


#ifndef NDEBUG
static void linked_item_sanity_check(DHQueueNode *h)
{
    if (h == NULL)
        return;
    if (h->next != NULL)
        assert(h->next->prev == h);
    if (h->prev != NULL)
        assert(h->prev->next == h);
}
#endif

void DHQueue_sanity_check(DHQueue *q)
{
#ifndef NDEBUG
    if (q->head == NULL || q->tail == NULL) {
        assert(q->head == NULL);
        assert(q->tail == NULL);
        assert(q->size == 0);
    }
    else {
        assert(q->head->prev == NULL);
        assert(q->tail->next == NULL);
        assert(q->size > 0);
        DHQueueNode *h = q->head;
        HPy_ssize_t size = 0;
        while(h != NULL) {
            linked_item_sanity_check(h);
            if (h->next == NULL)
                assert(h == q->tail);
            h = h->next;
            size++;
        }
        assert(q->size == size);
    }
#endif
}
