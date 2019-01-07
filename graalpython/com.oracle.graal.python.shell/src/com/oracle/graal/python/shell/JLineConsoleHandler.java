/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import org.graalvm.polyglot.Context;

import jline.console.ConsoleReader;
import jline.console.UserInterruptException;
import jline.console.completer.CandidateListCompletionHandler;
import jline.console.completer.Completer;
import jline.console.completer.CompletionHandler;
import jline.console.history.History;
import jline.console.history.MemoryHistory;

public class JLineConsoleHandler extends ConsoleHandler {
    private final ConsoleReader console;
    private final MemoryHistory history;
    private final boolean noPrompt;

    public JLineConsoleHandler(InputStream inStream, OutputStream outStream, boolean noPrompt) {
        this.noPrompt = noPrompt;
        try {
            console = new ConsoleReader(inStream, outStream);
            history = new MemoryHistory();
            console.setHistory(history);
            console.setHandleUserInterrupt(true);
            console.setExpandEvents(false);
            console.setCommentBegin("#");
        } catch (IOException ex) {
            // TODO throw proper exception type
            throw new RuntimeException("unexpected error opening console reader", ex);
        }
    }

    @Override
    public void addCompleter(Function<String, List<String>> completer) {
        console.addCompleter(new Completer() {
            public int complete(String buffer, int cursor, List<CharSequence> candidates) {
                if (buffer != null) {
                    candidates.addAll(completer.apply(buffer));
                }
                return candidates.isEmpty() ? -1 : 0;
            }
        });

    }

    @Override
    public void setHistory(BooleanSupplier shouldRecord, IntSupplier getSize, Consumer<String> addItem, IntFunction<String> getItem, BiConsumer<Integer, String> setItem, IntConsumer removeItem,
                    Runnable clear) {
        console.setHistory(new History() {
            private int pos = getSize.getAsInt();

            public int size() {
                return getSize.getAsInt();
            }

            public void set(int arg0, CharSequence arg1) {
                setItem.accept(arg0, arg1.toString());
            }

            public void replace(CharSequence arg0) {
                if (pos < 0 || pos >= size()) {
                    return;
                }
                setItem.accept(pos, arg0.toString());
            }

            public CharSequence removeLast() {
                int t = size() - 1;
                String s = getItem.apply(t);
                removeItem.accept(t);
                return s;
            }

            public CharSequence removeFirst() {
                int t = size() - 1;
                String s = getItem.apply(t);
                removeItem.accept(0);
                return s;
            }

            public CharSequence remove(int arg0) {
                int t = size() - 1;
                String s = getItem.apply(t);
                removeItem.accept(arg0);
                return s;
            }

            public boolean previous() {
                if (pos >= 0) {
                    pos--;
                    return true;
                } else {
                    return false;
                }
            }

            public boolean next() {
                if (pos < size()) {
                    pos++;
                    return true;
                } else {
                    return false;
                }
            }

            public boolean moveToLast() {
                pos = size();
                return true;
            }

            public boolean moveToFirst() {
                pos = 0;
                return true;
            }

            public void moveToEnd() {
                moveToLast();
            }

            public boolean moveTo(int arg0) {
                pos = arg0;
                int size = size();
                if (pos < 0 || pos >= size) {
                    pos = pos % size;
                    return false;
                }
                return true;
            }

            public Iterator<Entry> iterator() {
                // TODO Auto-generated method stub
                return null;
            }

            public boolean isEmpty() {
                return size() == 0;
            }

            public int index() {
                return pos;
            }

            public CharSequence get(int arg0) {
                return getItem.apply(arg0);
            }

            public ListIterator<Entry> entries(int arg0) {
                // TODO Auto-generated method stub
                return null;
            }

            public ListIterator<Entry> entries() {
                // TODO Auto-generated method stub
                return null;
            }

            public CharSequence current() {
                if (pos < 0 || pos >= size()) {
                    return "";
                }
                return getItem.apply(pos);
            }

            public void clear() {
                clear.run();
            }

            public void add(CharSequence arg0) {
                if (shouldRecord.getAsBoolean()) {
                    addItem.accept(arg0.toString());
                    pos = size();
                }
            }
        });
    }

    @Override
    public void setContext(Context context) {
        CompletionHandler completionHandler = console.getCompletionHandler();
        if (completionHandler instanceof CandidateListCompletionHandler) {
            ((CandidateListCompletionHandler) completionHandler).setPrintSpaceAfterFullCompletion(false);
        }
    }

    @Override
    public String readLine(boolean showPrompt) {
        try {
            console.getTerminal().init();
            return console.readLine(showPrompt ? console.getPrompt() : "");
        } catch (UserInterruptException e) {
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setPrompt(String prompt) {
        console.setPrompt(noPrompt ? "" : prompt != null ? prompt : "");
    }

    public void clearHistory() {
        history.clear();
    }

    public void addToHistory(String input) {
        history.add(input);
    }

    public String[] getHistory() {
        String[] result = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            result[i] = history.get(i).toString();
        }
        return result;
    }

    @Override
    public int getTerminalHeight() {
        return console.getTerminal().getHeight();
    }

    @Override
    public int getTerminalWidth() {
        return console.getTerminal().getWidth();
    }

}
