/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.benchmarks.interop;

import static com.oracle.graal.python.benchmarks.interop.BenchRunner.get;
import static com.oracle.graal.python.benchmarks.interop.BenchRunner.set;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.isNone;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.BUFSIZE;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.I_DEVA;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.I_DEVB;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.I_HANDLERA;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.I_HANDLERB;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.K_WORK;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.getField;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.getIntField;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.getTaskList;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.setField;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.taskWorkArea;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.trace;
import static com.oracle.graal.python.benchmarks.interop.PyRichards.tracing;

import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

public class PyRichards extends BenchRunner {

    @Param({"200"}) public int arg1;

    public static final boolean tracing = false;
    public static final int TASKTABSIZE = 10;
    public static int layout = 0;

    private Value classes;
    private Value PacketClass;
    private Value DeviceTaskRecClass;
    private Value IdleTaskRecClass;
    private Value HandlerTaskRecClass;
    private Value WorkerTaskRecClass;

    public static final int I_IDLE = 1;
    public static final int I_WORK = 2;
    public static final int I_HANDLERA = 3;
    public static final int I_HANDLERB = 4;
    public static final int I_DEVA = 5;
    public static final int I_DEVB = 6;

    // Packet types
    public static final int K_DEV = 1000;
    public static final int K_WORK = 1001;

    // Packet
    public static final int BUFSIZE = 4;

    public static Value taskWorkArea;
    public static Value None;

    @Setup
    public void setup() {
        System.out.println("### setup ...");
        PyRichards.None = this.context.eval("python", "None");
        this.classes = this.context.eval("python", //
                        "BUFSIZE = " + BUFSIZE + "\n" +
                                        "I_HANDLERA = " + I_HANDLERA + "\n" +
                                        "\n" +
                                        "class Packet(object):\n" +
                                        "    def __init__(self,l,i,k):\n" +
                                        "        self.link = l\n" +
                                        "        self.ident = i\n" +
                                        "        self.kind = k\n" +
                                        "        self.datum = 0\n" +
                                        "        self.data = [0] * BUFSIZE\n" +
                                        "\n" +
                                        "    def append_to(self,lst):\n" +
                                        "        self.link = None\n" +
                                        "        if lst is None:\n" +
                                        "            return self\n" +
                                        "        else:\n" +
                                        "            p = lst\n" +
                                        "            next = p.link\n" +
                                        "            while next is not None:\n" +
                                        "                p = next\n" +
                                        "                next = p.link\n" +
                                        "            p.link = self\n" +
                                        "            return lst\n" +
                                        "\n" +
                                        "\n" +
                                        "# Task Records\n" +
                                        "class TaskRec(object):\n" +
                                        "    pass\n" +
                                        "\n" +
                                        "\n" +
                                        "class DeviceTaskRec(TaskRec):\n" +
                                        "    def __init__(self):\n" +
                                        "        self.pending = None\n" +
                                        "\n" +
                                        "\n" +
                                        "class IdleTaskRec(TaskRec):\n" +
                                        "    def __init__(self):\n" +
                                        "        self.control = 1\n" +
                                        "        self.count = 10000\n" +
                                        "\n" +
                                        "\n" +
                                        "class HandlerTaskRec(TaskRec):\n" +
                                        "    def __init__(self):\n" +
                                        "        self.work_in = None\n" +
                                        "        self.device_in = None\n" +
                                        "\n" +
                                        "    def workInAdd(self,p):\n" +
                                        "        self.work_in = p.append_to(self.work_in)\n" +
                                        "        return self.work_in\n" +
                                        "\n" +
                                        "    def deviceInAdd(self,p):\n" +
                                        "        self.device_in = p.append_to(self.device_in)\n" +
                                        "        return self.device_in\n" +
                                        "\n" +
                                        "\n" +
                                        "class WorkerTaskRec(TaskRec):\n" +
                                        "    def __init__(self):\n" +
                                        "        self.destination = I_HANDLERA\n" +
                                        "        self.count = 0\n" +
                                        "\n" +
                                        "TASKTABSIZE = " + TASKTABSIZE + "\n" +
                                        "\n" +
                                        "class TaskWorkArea(object):\n" +
                                        "    def __init__(self):\n" +
                                        "        self.taskTab = [None] * TASKTABSIZE\n" +
                                        "\n" +
                                        "        self.taskList = None\n" +
                                        "\n" +
                                        "        self.holdCount = 0\n" +
                                        "        self.qpktCount = 0\n" +
                                        "\n" +
                                        "def get_class(c):\n" +
                                        "    if c == 1:\n" +
                                        "        return Packet\n" +
                                        "    if c == 2:\n" +
                                        "        return DeviceTaskRec\n" +
                                        "    if c == 3:\n" +
                                        "        return IdleTaskRec\n" +
                                        "    if c == 4:\n" +
                                        "        return HandlerTaskRec\n" +
                                        "    if c == 5:\n" +
                                        "        return WorkerTaskRec\n" +
                                        "    if c == 6:\n" +
                                        "        return TaskWorkArea\n" +
                                        "get_class");
        this.PacketClass = classes.execute(1);
        this.DeviceTaskRecClass = classes.execute(2);
        this.IdleTaskRecClass = classes.execute(3);
        this.HandlerTaskRecClass = classes.execute(4);
        this.WorkerTaskRecClass = classes.execute(5);
        PyRichards.taskWorkArea = classes.execute(6).newInstance();
    }

    public Value Packet(Value l, int i, int k) {
        return PacketClass.newInstance(l, i, k);
    }

    public Value IdleTaskRec() {
        return IdleTaskRecClass.newInstance();
    }

    public Value WorkerTaskRec() {
        return WorkerTaskRecClass.newInstance();
    }

    public Value HandlerTaskRec() {
        return HandlerTaskRecClass.newInstance();
    }

    public Value DeviceTaskRec() {
        return DeviceTaskRecClass.newInstance();
    }

    public static int getHoldCount() {
        return getIntField(taskWorkArea, "holdCount");
    }

    public static void setHoldCount(int i) {
        setField(taskWorkArea, "holdCount", i);
    }

    public static int getQpktCount() {
        return getIntField(taskWorkArea, "qpktCount");
    }

    public static void setQpktCount(int i) {
        setField(taskWorkArea, "qpktCount", i);
    }

    @Benchmark
    public void richards3(Blackhole bh) {
        for (int i = 0; i < arg1; i++) {
            setHoldCount(0);
            setQpktCount(0);

            bh.consume(new IdleTask(I_IDLE, 1, PyRichards.None, new TaskState().running(), IdleTaskRec()));

            Value wkq = Packet(None, 0, K_WORK);
            wkq = Packet(wkq, 0, K_WORK);
            bh.consume(new WorkTask(I_WORK, 1000, wkq, new TaskState().waitingWithPacket(), WorkerTaskRec()));

            wkq = Packet(None, I_DEVA, K_DEV);
            wkq = Packet(wkq, I_DEVA, K_DEV);
            wkq = Packet(wkq, I_DEVA, K_DEV);
            bh.consume(new HandlerTask(I_HANDLERA, 2000, wkq, new TaskState().waitingWithPacket(), HandlerTaskRec()));

            wkq = Packet(None, I_DEVB, K_DEV);
            wkq = Packet(wkq, I_DEVB, K_DEV);
            wkq = Packet(wkq, I_DEVB, K_DEV);
            bh.consume(new HandlerTask(I_HANDLERB, 3000, wkq, new TaskState().waitingWithPacket(), HandlerTaskRec()));

            wkq = None;
            bh.consume(new DeviceTask(I_DEVA, 4000, wkq, new TaskState().waiting(), DeviceTaskRec()));
            bh.consume(new DeviceTask(I_DEVB, 5000, wkq, new TaskState().waiting(), DeviceTaskRec()));

            schedule();

            if (getHoldCount() == 9297 && getQpktCount() == 23246) {
                continue;
            } else {
                return;
            }
        }
    }

    public static void trace(int a) {
        layout -= 1;
        if (layout <= 0) {
            System.out.println();
            layout = 50;
        }
        System.out.print(a + " ");
    }

    void schedule() {
        Task t = getTaskList();
        while (t != null) {
            if (tracing) {
                System.out.println("tcb =" + t.ident);
            }

            if (t.isTaskHoldingOrWaiting()) {
                t = (Task) t.link;
            } else {
                if (tracing) {
                    trace('0' + t.ident);
                }
                t = (Task) t.runTask();
            }
        }
    }

    public static Task getTaskList() {
        Value t = getField(taskWorkArea, "taskList");
        return isNone(t) ? null : t.asHostObject();
    }

    public static void setField(Value clazz, String member, Object val) {
        clazz.putMember(member, val);
    }

    public static Value getField(Value clazz, String member) {
        return clazz.getMember(member);
    }

    public static int getIntField(Value clazz, String member) {
        return clazz.getMember(member).asInt();
    }

    public static boolean isNone(Value v) {
        if (v == null) {
            return true;
        }
        return v.isNull();
    }
}

class TaskState {
    boolean packet_pending;
    boolean task_waiting;
    boolean task_holding;

    public TaskState() {
        this.packet_pending = true;
        this.task_waiting = false;
        this.task_holding = false;
    }

    TaskState packetPending() {
        this.packet_pending = true;
        this.task_waiting = false;
        this.task_holding = false;
        return this;
    }

    TaskState waiting() {
        this.packet_pending = false;
        this.task_waiting = true;
        this.task_holding = false;
        return this;
    }

    TaskState running() {
        this.packet_pending = false;
        this.task_waiting = false;
        this.task_holding = false;
        return this;
    }

    TaskState waitingWithPacket() {
        this.packet_pending = true;
        this.task_waiting = true;
        this.task_holding = false;
        return this;
    }

    boolean isPacketPending() {
        return this.packet_pending;
    }

    boolean isTaskWaiting() {
        return this.task_waiting;
    }

    boolean isTaskHolding() {
        return this.task_holding;
    }

    boolean isTaskHoldingOrWaiting() {
        return this.task_holding || (!this.packet_pending && this.task_waiting);
    }

    boolean isWaitingWithPacket() {
        return this.packet_pending && this.task_waiting && !this.task_holding;
    }

}

abstract class Task extends TaskState {
    public int ident;
    public int priority;
    public Value input; // Packet
    public Value handle; // TaskRec
    public TaskState link;

    public Task(int i, int p, Value w, TaskState initialState, Value r) {
        this.link = getTaskList();
        this.ident = i;
        this.priority = p;
        this.input = w;

        this.packet_pending = initialState.isPacketPending();
        this.task_waiting = initialState.isTaskWaiting();
        this.task_holding = initialState.isTaskHolding();

        this.handle = r;

        setField(taskWorkArea, "taskList", this);
        set(getField(taskWorkArea, "taskTab"), i, this);
    }

    public abstract TaskState fn(Value pkt, Value r);

    public TaskState addPacket(Value p, Task old) {
        if (isNone(this.input)) {
            this.input = p;
            this.packet_pending = true;
            if (this.priority > old.priority) {
                return this;
            }
        } else {
            p.invokeMember("append_to", this.input);
        }
        return old;
    }

    public TaskState runTask() {
        Value msg = PyRichards.None;
        if (this.isWaitingWithPacket()) {
            msg = this.input;
            this.input = getField(msg, "link");
            if (isNone(this.input)) {
                this.running();
            } else {
                this.packetPending();
            }
        }
        return this.fn(msg, this.handle);
    }

    public TaskState waitTask() {
        this.task_waiting = true;
        return this;
    }

    public TaskState hold() {
        setField(taskWorkArea, "holdCount", getIntField(taskWorkArea, "holdCount") + 1);
        this.task_holding = true;
        return this.link;
    }

    public TaskState release(int i) {
        Task t = this.findtcb(i);
        t.task_holding = false;
        if (t.priority > this.priority) {
            return t;
        } else {
            return this;
        }
    }

    public TaskState qpkt(Value pkt) {
        Task t = this.findtcb(getIntField(pkt, "ident"));
        setField(taskWorkArea, "qpktCount", getIntField(taskWorkArea, "qpktCount") + 1);
        setField(pkt, "link", PyRichards.None);
        setField(pkt, "ident", this.ident);
        return t.addPacket(pkt, this);
    }

    public Task findtcb(int id) {
        Value t = get(getField(taskWorkArea, "taskTab"), id);
        if (isNone(t)) {
            throw new IllegalStateException(String.format("Bad task id %d", id));
        }
        return t.asHostObject();
    }
}

class DeviceTask extends Task {
    public DeviceTask(int i, int p, Value w, TaskState s, Value r) {
        super(i, p, w, s, r);
    }

    @Override
    public TaskState fn(Value pkt, Value r) {
        Value d = r; // DeviceTaskRec
        // assert isinstance(d, DeviceTaskRec)
        if (isNone(pkt)) {
            Value pkt2 = getField(d, "pending");
            if (isNone(pkt2)) {
                return this.waitTask();
            } else {
                setField(d, "pending", PyRichards.None);
                return this.qpkt(pkt2);
            }
        } else {
            setField(d, "pending", pkt);
            if (tracing) {
                trace(getIntField(pkt, "datum"));
            }
            return this.hold();
        }
    }
}

class HandlerTask extends Task {
    public HandlerTask(int i, int p, Value w, TaskState s, Value r) {
        super(i, p, w, s, r);
    }

    @Override
    public TaskState fn(Value pkt, Value r) {
        Value h = r;
        // assert isinstance(h, HandlerTaskRec)
        if (!isNone(pkt)) {
            if (getIntField(pkt, "kind") == K_WORK) {
                h.invokeMember("workInAdd", pkt);
            } else {
                h.invokeMember("deviceInAdd", pkt);
            }
        }
        Value work = getField(h, "work_in");
        if (isNone(work)) {
            return this.waitTask();
        }
        int count = getIntField(work, "datum");
        if (count >= BUFSIZE) {
            setField(h, "work_in", getField(work, "link"));
            return this.qpkt(work);
        }

        Value dev = getField(h, "device_in");
        if (isNone(dev)) {
            return this.waitTask();
        }

        setField(h, "device_in", getField(dev, "link"));
        setField(dev, "datum", get(getField(work, "data"), count));
        setField(work, "datum", count + 1);
        return this.qpkt(dev);
    }
}

class IdleTask extends Task {
    @SuppressWarnings("unused")
    public IdleTask(int i, int p, Value w, TaskState s, Value r) {
        super(i, 0, PyRichards.None, s, r);
    }

    @Override
    public TaskState fn(Value pkt, Value r) {
        Value i = r;
        // assert isinstance(i, IdleTaskRec);
        int count = getIntField(i, "count") - 1;
        setField(i, "count", count);
        if (count == 0) {
            return this.hold();
        } else if ((getIntField(i, "control") & 1) == 0) {
            setField(i, "control", getIntField(i, "control") / 2);
            return this.release(I_DEVA);
        } else {
            setField(i, "control", getIntField(i, "control") / 2 ^ 0xd008);
            return this.release(I_DEVB);
        }
    }
}

class WorkTask extends Task {
    public WorkTask(int i, int p, Value w, TaskState s, Value r) {
        super(i, p, w, s, r);
    }

    @Override
    public TaskState fn(Value pkt, Value r) {
        Value w = r;
        // assert isinstance(w, WorkerTaskRec);
        if (isNone(pkt)) {
            return this.waitTask();
        }
        int dest = I_HANDLERA;
        if (getIntField(w, "destination") == I_HANDLERA) {
            dest = I_HANDLERB;
        }

        setField(w, "destination", dest);
        setField(pkt, "ident", dest);
        setField(pkt, "datum", 0);

        for (int i = 0; i < BUFSIZE; i++) {
            int count = getIntField(w, "count") + 1;
            setField(w, "count", count);
            if (count > 26) {
                setField(w, "count", 1);
            }
            // A = ord('A')
            set(getField(pkt, "data"), i, 'A' + getIntField(w, "count") - 1);
        }
        return this.qpkt(pkt);
    }

}