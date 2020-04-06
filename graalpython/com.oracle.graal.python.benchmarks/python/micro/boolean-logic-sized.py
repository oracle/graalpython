# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# micro benchmark: method call polymorphic inspired by richards

iteration = 50000


class Task(object):
    def __init__(self, p, w, h):
        self.packet_pending = p
        self.task_waiting = w
        self.task_holding = h
        self.link = None

    def is_task_holding_or_waiting(self):
        return self.task_holding or (not self.packet_pending and self.task_waiting)


def is_task_holding_or_waiting(task_holding, packet_pending, task_waiting):
    return task_holding or (not packet_pending and task_waiting)


TASK_LIST = [Task(False, False, True),
             Task(False, True, False),
             Task(True, True, False),
             Task(True, False, True)]


def setup_task_queue():
    prev = None
    for t in TASK_LIST:
        t.link = prev
        prev = t
    return t


TASK_QUEUE = setup_task_queue()


def do_stuff():
    total = 0
    for i in range(iteration):
        t = TASK_QUEUE
        while t is not None:
            if (t.is_task_holding_or_waiting()):
                total += 1
            t = t.link

    return total


def no_object_do_stuff():
    p = True
    w = False
    h = True
    total = 0
    for i in range(iteration):
        h = is_task_holding_or_waiting(h, p, w)
        if (is_task_holding_or_waiting(h, p, w)):
            total += 1

    return total


def measure(num):
    for i in range(num):
        result = do_stuff()

    print(result)


def __benchmark__(num=1000):
    measure(num)
