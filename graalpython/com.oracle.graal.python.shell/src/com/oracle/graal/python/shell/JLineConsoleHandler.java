/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import org.graalvm.shadowed.org.jline.reader.Candidate;
import org.graalvm.shadowed.org.jline.reader.Completer;
import org.graalvm.shadowed.org.jline.reader.EndOfFileException;
import org.graalvm.shadowed.org.jline.reader.History;
import org.graalvm.shadowed.org.jline.reader.LineReader;
import org.graalvm.shadowed.org.jline.reader.LineReaderBuilder;
import org.graalvm.shadowed.org.jline.reader.ParsedLine;
import org.graalvm.shadowed.org.jline.reader.UserInterruptException;
import org.graalvm.shadowed.org.jline.terminal.Terminal;
import org.graalvm.shadowed.org.jline.terminal.TerminalBuilder;

public class JLineConsoleHandler extends ConsoleHandler {
    private final boolean noPrompt;
    private final Terminal terminal;
    private LineReader reader;
    private History history;
    private String prompt;

    public JLineConsoleHandler(InputStream inStream, OutputStream outStream, boolean noPrompt) {
        this.noPrompt = noPrompt;
        try {
            this.terminal = TerminalBuilder.builder().jna(false).streams(inStream, outStream).system(true).signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException ex) {
            throw new RuntimeException("unexpected error opening console reader", ex);
        }
    }

    @Override
    public void setupReader(BooleanSupplier shouldRecord,
                    IntSupplier getSize,
                    Consumer<String> addItem,
                    IntFunction<String> getItem,
                    BiConsumer<Integer, String> setItem,
                    IntConsumer removeItem,
                    Runnable clear,
                    Function<String, List<String>> completer) {
        history = new HistoryImpl(shouldRecord, getSize, addItem, getItem, setItem, removeItem, clear);

        LineReaderBuilder builder = LineReaderBuilder.builder();
        builder = builder.terminal(terminal).history(history);
        if (completer != null) {
            builder.completer(new Completer() {
                @Override
                public void complete(LineReader r, ParsedLine pl, List<Candidate> candidates) {
                    String line = pl.line();
                    if (line != null) {
                        List<String> l = completer.apply(line);
                        for (String value : l) {
                            candidates.add(new Candidate(value, value, null, null, null, null, false));
                        }
                    }
                }
            });
        }
        reader = builder.build();
        reader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true);
        reader.setVariable(LineReader.COMMENT_BEGIN, "#");
    }

    @Override
    public String readLine(boolean showPrompt) {
        try {
            return reader.readLine(showPrompt ? prompt : "");
        } catch (UserInterruptException e) {
            throw e;
        } catch (EndOfFileException e) {
            return null;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setPrompt(String prompt) {
        this.prompt = noPrompt ? "" : prompt != null ? prompt : "";
    }

    @Override
    public int getTerminalHeight() {
        return terminal.getHeight();
    }

    @Override
    public int getTerminalWidth() {
        return terminal.getWidth();
    }

    private static class HistoryImpl implements History {
        private final BooleanSupplier shouldRecord;
        private final IntSupplier getSize;
        private final Consumer<String> addItem;
        private final IntFunction<String> getItem;
        private final BiConsumer<Integer, String> setItem;
        private final IntConsumer removeItem;
        private final Runnable clear;

        private int index;

        public HistoryImpl(BooleanSupplier shouldRecord, IntSupplier getSize, Consumer<String> addItem, IntFunction<String> getItem, BiConsumer<Integer, String> setItem, IntConsumer removeItem,
                        Runnable clear) {
            this.shouldRecord = shouldRecord;
            this.getSize = getSize;
            this.addItem = addItem;
            this.getItem = getItem;
            this.setItem = setItem;
            this.removeItem = removeItem;
            this.clear = clear;
            index = getSize.getAsInt();
        }

        @Override
        public int size() {
            return getSize.getAsInt();
        }

        @Override
        public void resetIndex() {
            int size = size();
            index = index > size ? size : index;
        }

        @Override
        public int first() {
            return 0;
        }

        @Override
        public int last() {
            return size() - 1;
        }

        @Override
        public boolean previous() {
            if (index <= 0) {
                return false;
            } else {
                index--;
                return true;
            }
        }

        @Override
        public boolean next() {
            if (index >= size()) {
                return false;
            } else {
                index++;
                return true;
            }
        }

        @Override
        public boolean moveTo(int idx) {
            if (idx >= 0 && idx < size()) {
                this.index = idx;
                return true;
            }
            return false;
        }

        @Override
        public boolean moveToLast() {
            int lastEntry = size() - 1;
            if (lastEntry >= 0 && lastEntry != index) {
                index = lastEntry;
                return true;
            }
            return false;
        }

        @Override
        public void moveToEnd() {
            index = size();
        }

        @Override
        public boolean moveToFirst() {
            if (size() > 0 && index != 0) {
                index = 0;
                return true;
            }
            return false;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public String get(int idx) {
            return getItem.apply(idx);
        }

        @Override
        public String current() {
            if (index < 0 || index >= size()) {
                return "";
            }
            return getItem.apply(index);
        }

        @Override
        public void add(String string) {
            if (shouldRecord.getAsBoolean()) {
                addItem.accept(string);
                index = size();
            }
        }

        @Override
        public void add(Instant instnt, String string) {
            add(string);
        }

        private void add(int idx, String val) {
            setItem.accept(idx, val);
        }

        @Override
        public void purge() throws IOException {
            clear.run();
        }

        @Override
        public ListIterator<History.Entry> iterator(int i) {
            return new HistoryIterator(i);
        }

        private class HistoryIterator implements ListIterator<History.Entry> {
            private int iterIndex;

            public HistoryIterator(int idx) {
                this.iterIndex = idx;
            }

            @Override
            public boolean hasNext() {
                int size = HistoryImpl.this.size();
                return size > 0 && iterIndex < size;
            }

            @Override
            public int nextIndex() {
                return iterIndex;
            }

            @Override
            public History.Entry next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                HistoryEntry e = new HistoryEntry(iterIndex++);
                return e;
            }

            @Override
            public boolean hasPrevious() {
                int size = HistoryImpl.this.size();
                return size > 0 && iterIndex > 0;
            }

            @Override
            public int previousIndex() {
                return iterIndex - 1;
            }

            @Override
            public History.Entry previous() {
                if (!hasPrevious()) {
                    throw new NoSuchElementException();
                }
                HistoryEntry e = new HistoryEntry(--iterIndex);
                return e;
            }

            @Override
            public void remove() {
                removeItem.accept(iterIndex);
                while (iterIndex > HistoryImpl.this.size()) {
                    iterIndex--;
                }
            }

            @Override
            public void set(History.Entry e) {
                HistoryImpl.this.add(iterIndex, e.line());
            }

            @Override
            public void add(History.Entry e) {
                HistoryImpl.this.add(size(), e.line());
            }
        }

        class HistoryEntry implements Entry {
            private final int entryIndex;

            public HistoryEntry(int idx) {
                this.entryIndex = idx;
            }

            @Override
            public int index() {
                return entryIndex;
            }

            @Override
            public Instant time() {
                return Instant.ofEpochMilli(0);
            }

            @Override
            public String line() {
                return HistoryImpl.this.get(entryIndex);
            }

            @Override
            public String toString() {
                return "<HistoryEntry: " + entryIndex + " " + HistoryImpl.this.get(entryIndex) + " >";
            }
        }

        @Override
        public void attach(LineReader reader) {

        }

        @Override
        public void load() throws IOException {

        }

        @Override
        public void save() throws IOException {

        }

        @Override
        public void write(Path path, boolean bln) throws IOException {

        }

        @Override
        public void append(Path path, boolean bln) throws IOException {

        }

        @Override
        public void read(Path path, boolean bln) throws IOException {

        }
    }
}
