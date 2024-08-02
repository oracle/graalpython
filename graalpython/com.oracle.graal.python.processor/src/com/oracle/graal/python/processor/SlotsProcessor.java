/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.processor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.Slots;
import com.oracle.graal.python.processor.CodeWriter.Block;

public class SlotsProcessor extends AbstractProcessor {
    private static final boolean LOGGING = false;

    public record TpSlotData(Slot slot, TypeElement enclosingType, TypeElement slotNodeType) {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Slot.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        try {
            doProcess(roundEnv);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (ProcessingError ex) {
            processingEnv.getMessager().printMessage(Kind.ERROR, ex.getMessage(), ex.getElement());
        }
        return true;
    }

    private void doProcess(RoundEnvironment roundEnv) throws IOException, ProcessingError {
        log("Running the SlotsProcessor");
        var enclosingTypes = collectEnclosingTypes(roundEnv);
        validate(enclosingTypes);
        writeCode(enclosingTypes);
    }

    private void validate(HashMap<TypeElement, Set<TpSlotData>> enclosingTypes) throws ProcessingError {
        var typeCache = new TypeCache(processingEnv);
        for (Entry<TypeElement, Set<TpSlotData>> enclosingType : enclosingTypes.entrySet()) {
            for (TpSlotData slot : enclosingType.getValue()) {
                if (slot.slot.isComplex()) {
                    if (!SlotsMapping.supportsComplex(slot.slot().value())) {
                        throw error(slot.slotNodeType, "Slot does not support complex builtins. The support can be added.");
                    }
                } else {
                    if (!SlotsMapping.supportsSimple(slot.slot().value())) {
                        throw error(slot.slotNodeType, "Slot does not support simple builtins. Did you forget 'isComplex = true'? Otherwise, the support can be added.");
                    }
                }
                String baseName = SlotsMapping.getSlotNodeBaseClass(slot.slot);
                TypeMirror expectedBase = typeCache.get(baseName);
                if (!processingEnv.getTypeUtils().isSubtype(slot.slotNodeType.asType(), expectedBase)) {
                    throw error(slot.slotNodeType, "Slot does not inherit from expected base class '%s'", baseName);
                }
            }
        }
    }

    @SuppressWarnings("try")
    private void writeCode(HashMap<TypeElement, Set<TpSlotData>> enclosingTypes) throws IOException {
        for (Entry<TypeElement, Set<TpSlotData>> enclosingType : enclosingTypes.entrySet()) {
            String pkgName = getPackage(enclosingType.getKey());
            String className = enclosingType.getKey().getSimpleName() + "SlotsGen";
            String sourceFile = pkgName + "." + className;
            log("Generating file '%s'", sourceFile);

            JavaFileObject file = processingEnv.getFiler().createSourceFile(sourceFile);
            try (CodeWriter w = new CodeWriter(file.openWriter())) {
                w.writeLn("// CheckStyle: start generated");
                w.writeLn("// Auto generated by %s at %s", getClass().getName(), LocalDateTime.now());
                w.writeLn("package %s;", pkgName);
                w.writeLn();
                writeImports(w);
                w.writeLn();
                w.writeLn("public class %s {", className);
                try (Block i = w.newIndent()) {
                    for (TpSlotData slot : enclosingType.getValue()) {
                        writeSlot(w, slot);
                    }
                    writeSlotsStaticField(w, enclosingType.getValue());
                }
                w.writeLn("}");
            }
        }
    }

    private void writeImports(CodeWriter w) throws IOException {
        log("Writing imports...");
        TreeSet<String> imports = new TreeSet<>();
        imports.add("com.oracle.graal.python.builtins.objects.type.slots.*");
        imports.add("com.oracle.graal.python.builtins.objects.type.*");
        for (String pkg : imports) {
            w.writeLn("import %s;", pkg);
        }
    }

    @SuppressWarnings("try")
    private void writeSlot(CodeWriter w, TpSlotData slot) throws IOException {
        log("Writing slot node %s", slot.slotNodeType);
        String slotImplName = getSlotImplName(slot.slot.value());
        String genericArg = "";
        genericArg = String.format("<%s>", slot.slotNodeType.getQualifiedName());

        w.writeLn("private static final class %s extends %s%s {", slotImplName, SlotsMapping.getSlotBaseClass(slot.slot), genericArg);
        try (Block i1 = w.newIndent()) {

            // Static field holding the singleton and private ctor:
            w.writeLn("public static final %s INSTANCE = new %s();", slotImplName, slotImplName);
            w.writeLn();
            w.writeLn("private %s() {", slotImplName);
            try (Block i2 = w.newIndent()) {
                w.startLn().//
                                write("super(").//
                                write(getNodeFactory(slot, slot.slotNodeType()) + ".getInstance()").//
                                endLn(");");

            }
            w.writeLn("}");
            w.writeLn();

            if (!slot.slot.isComplex()) {
                w.writeLn("public %s {", SlotsMapping.getUncachedExecuteSignature(slot.slot.value()));
                try (Block i2 = w.newIndent()) {
                    w.writeLn("return %s.getUncached().%s;", getNodeFactory(slot, slot.slotNodeType), SlotsMapping.getUncachedExecuteCall(slot.slot.value()));
                }
                w.writeLn("}");
            }
        }
        w.writeLn("}");
    }

    @SuppressWarnings("try")
    private static void writeSlotsStaticField(CodeWriter w, Set<TpSlotData> slots) throws IOException {
        w.writeLn("static final TpSlots SLOTS = TpSlots.newBuilder()");
        try (Block i3 = w.newIndent()) {
            String defs = slots.stream().//
                            map(s -> String.format(".set(TpSlots.TpSlotMeta.%s, %s.INSTANCE)", //
                                            s.slot.value().name().toUpperCase(Locale.ROOT), //
                                            getSlotImplName(s.slot.value()))).//
                            collect(Collectors.joining("\n"));
            w.writeLn("%s.", defs);
            w.writeLn("build();");
        }
    }

    private HashMap<TypeElement, Set<TpSlotData>> collectEnclosingTypes(RoundEnvironment roundEnv) throws ProcessingError {
        HashMap<TypeElement, Set<TpSlotData>> enclosingTypes = new HashMap<>();
        HashSet<Element> elements = new HashSet<>(roundEnv.getElementsAnnotatedWithAny(Set.of(Slot.class, Slots.class)));
        for (Element e : elements) {
            log("Checking type '%s'", e);
            if (e.getKind() != ElementKind.CLASS) {
                throw error(e, "@%s annotation is applicable only to classes.", Slot.class.getSimpleName());
            }
            TypeElement type = (TypeElement) e;
            if (type.getEnclosingElement() == null) {
                throw error(e, "@%s annotation supports only inner classes at moment.", Slot.class.getSimpleName());
            }

            TypeElement enclosingType = (TypeElement) type.getEnclosingElement();
            for (Slot slotAnnotation : e.getAnnotationsByType(Slot.class)) {
                if (!slotAnnotation.isComplex()) {
                    verifySimpleNode(type);
                }
                var tpSlotDataSet = enclosingTypes.computeIfAbsent(enclosingType, k -> new HashSet<>());
                tpSlotDataSet.add(new TpSlotData(slotAnnotation, enclosingType, type));
            }
        }
        return enclosingTypes;
    }

    private static void verifySimpleNode(TypeElement type) throws ProcessingError {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement executable) {
                if (executable.getAnnotationMirrors().stream().anyMatch(x -> x.getAnnotationType().asElement().getSimpleName().contentEquals("Specialization"))) {
                    for (VariableElement param : executable.getParameters()) {
                        if (param.asType() instanceof DeclaredType declType) {
                            if (declType.asElement().getSimpleName().contentEquals("VirtualFrame")) {
                                throw new ProcessingError(type, "Slot node has isComplex=false (the default), but seems to have methods that take VirtualFrame");
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getNodeFactory(TpSlotData slot, TypeElement node) {
        return slot.enclosingType.getQualifiedName() + "Factory." + node.getSimpleName() + "Factory";
    }

    private static ProcessingError error(Element element, String fmt, Object... args) throws ProcessingError {
        throw new ProcessingError(element, fmt, args);
    }

    private void log(String fmt, Object... args) {
        if (LOGGING) {
            String msg = "SlotsProcessor: " + String.format(fmt, args);
            processingEnv.getMessager().printMessage(Kind.NOTE, msg);
        }
    }

    private static String getSlotImplName(SlotKind s) {
        return s.name() + "_Impl";
    }

    private static String getPackage(TypeElement type) {
        return getPackage(type.getQualifiedName().toString());
    }

    private static String getPackage(String qname) {
        int idx = qname.lastIndexOf('.');
        assert idx > 0 : qname;
        return qname.substring(0, idx);
    }
}
