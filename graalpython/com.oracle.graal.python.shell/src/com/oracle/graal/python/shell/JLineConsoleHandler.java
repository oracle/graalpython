/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import jline.console.ConsoleReader;
import jline.console.UserInterruptException;
import jline.console.completer.CandidateListCompletionHandler;
import jline.console.completer.CompletionHandler;
import jline.console.history.MemoryHistory;
import org.graalvm.polyglot.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
        } catch (IOException ex) {
            // TODO throw proper exception type
            throw new RuntimeException("unexpected error opening console reader", ex);
        }
    }

    @Override
    public void setContext(Context context) {
        CompletionHandler completionHandler = console.getCompletionHandler();
        if (completionHandler instanceof CandidateListCompletionHandler) {
            ((CandidateListCompletionHandler) completionHandler).setPrintSpaceAfterFullCompletion(false);
        }
    }

    @Override
    public String readLine() {
        try {
            console.getTerminal().init();
            return console.readLine();
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
}
