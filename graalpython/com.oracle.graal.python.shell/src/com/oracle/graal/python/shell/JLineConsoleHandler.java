/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import org.graalvm.polyglot.Value;
import org.graalvm.shadowed.org.jline.keymap.KeyMap;
import org.graalvm.shadowed.org.jline.reader.Binding;
import org.graalvm.shadowed.org.jline.reader.Candidate;
import org.graalvm.shadowed.org.jline.reader.EndOfFileException;
import org.graalvm.shadowed.org.jline.reader.History;
import org.graalvm.shadowed.org.jline.reader.LineReader;
import org.graalvm.shadowed.org.jline.reader.LineReaderBuilder;
import org.graalvm.shadowed.org.jline.reader.Macro;
import org.graalvm.shadowed.org.jline.reader.UserInterruptException;
import org.graalvm.shadowed.org.jline.reader.impl.DefaultParser;
import org.graalvm.shadowed.org.jline.terminal.Terminal;
import org.graalvm.shadowed.org.jline.terminal.TerminalBuilder;

public class JLineConsoleHandler extends ConsoleHandler {
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private LineReader reader;
    private final LinkedList<String> lineBuffer = new LinkedList<>();

    JLineConsoleHandler(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    // Called via interop from readline module initialization
    @SuppressWarnings("unused")
    public void initializeReadline(Value readlineModule) {
        Terminal terminal;
        try {
            terminal = TerminalBuilder.builder().jna(false).streams(inputStream, outputStream).system(true).signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException ex) {
            throw new RuntimeException("unexpected error opening console reader", ex);
        }
        final Value getCompleter = readlineModule.getMember("get_completer");
        final Value shouldRecord = readlineModule.getMember("get_auto_history");
        final Value addHistory = readlineModule.getMember("add_history");
        final Value getHistoryItem = readlineModule.getMember("get_history_item");
        final Value setHistoryItem = readlineModule.getMember("replace_history_item");
        final Value deleteHistoryItem = readlineModule.getMember("remove_history_item");
        final Value clearHistory = readlineModule.getMember("clear_history");
        final Value getHistorySize = readlineModule.getMember("get_current_history_length");

        LineReaderBuilder builder = LineReaderBuilder.builder().terminal(terminal);
        builder.history(new HistoryImpl(
                        () -> shouldRecord.execute().asBoolean(),
                        () -> getHistorySize.execute().asInt(),
                        addHistory::execute,
                        (pos) -> getHistoryItem.execute(pos).asString(),
                        setHistoryItem::execute,
                        deleteHistoryItem::execute,
                        clearHistory::execute));
        builder.completer((r, pl, candidates) -> {
            String word = pl.word();
            if (word != null) {
                Value completer = getCompleter.execute();
                if (completer.canExecute()) {
                    int i = 0;
                    while (true) {
                        Value completerResult = completer.execute(word, i++);
                        if (completerResult.isString()) {
                            String completion = completerResult.asString();
                            candidates.add(new Candidate(completion, completion, null, null, null, null, false));
                        } else {
                            break;
                        }
                    }
                }
            }
        });

        builder.parser(new DefaultParser() {
            @Override
            public boolean isDelimiterChar(CharSequence buffer, int pos) {
                // Never count a last character of a char sequence as delimiter. The REPL completer
                // implemented by `rlcompleter.py` adds a trailing whitespace to keywords,
                // e.g. 'raise '. The default DefaultParser implementation always escaped this
                // whitespace leading to wrong completions like 'raise\ '.
                if (pos == buffer.length() - 1) {
                    return false;
                }
                return Character.isWhitespace(buffer.charAt(pos));
            }
        });

        reader = builder.build();
        reader.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true);
        reader.option(LineReader.Option.INSERT_TAB, true);
        reader.setVariable(LineReader.COMMENT_BEGIN, "#");

        // numpad bindings
        KeyMap<Binding> binding = reader.getKeyMaps().get(LineReader.MAIN);
        binding.bind(new Macro(KeyMap.translate("0")), KeyMap.translate("^[Op"));
        binding.bind(new Macro(KeyMap.translate(".")), KeyMap.translate("^[On"));
        binding.bind(new Macro(KeyMap.translate("^M")), KeyMap.translate("^[OM"));
        binding.bind(new Macro(KeyMap.translate("1")), KeyMap.translate("^[Oq"));
        binding.bind(new Macro(KeyMap.translate("2")), KeyMap.translate("^[Or"));
        binding.bind(new Macro(KeyMap.translate("3")), KeyMap.translate("^[Os"));
        binding.bind(new Macro(KeyMap.translate("4")), KeyMap.translate("^[Ot"));
        binding.bind(new Macro(KeyMap.translate("5")), KeyMap.translate("^[Ou"));
        binding.bind(new Macro(KeyMap.translate("6")), KeyMap.translate("^[Ov"));
        binding.bind(new Macro(KeyMap.translate("7")), KeyMap.translate("^[Ow"));
        binding.bind(new Macro(KeyMap.translate("8")), KeyMap.translate("^[Ox"));
        binding.bind(new Macro(KeyMap.translate("9")), KeyMap.translate("^[Oy"));
        binding.bind(new Macro(KeyMap.translate("+")), KeyMap.translate("^[Ol"));
        binding.bind(new Macro(KeyMap.translate("-")), KeyMap.translate("^[OS"));
        binding.bind(new Macro(KeyMap.translate("*")), KeyMap.translate("^[OR"));
        binding.bind(new Macro(KeyMap.translate("/")), KeyMap.translate("^[OQ"));
    }

    @Override
    public String readLine(String prompt) {
        if (lineBuffer.isEmpty()) {
            try {
                String lines = reader.readLine(prompt);
                Collections.addAll(lineBuffer, lines.split("\n"));
            } catch (UserInterruptException e) {
                throw e;
            } catch (EndOfFileException e) {
                return null;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return lineBuffer.poll();
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
        public void purge() {
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
                return new HistoryEntry(iterIndex++);
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
                return new HistoryEntry(--iterIndex);
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
        public void load() {

        }

        @Override
        public void save() {

        }

        @Override
        public void write(Path path, boolean bln) {

        }

        @Override
        public void append(Path path, boolean bln) {

        }

        @Override
        public void read(Path path, boolean bln) {

        }
    }
}
