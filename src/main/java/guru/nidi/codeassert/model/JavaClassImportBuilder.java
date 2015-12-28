/*
 * Copyright (C) 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.codeassert.model;

import guru.nidi.codeassert.PackageCollector;
import guru.nidi.codeassert.model.ClassFileParser.AttributeInfo;
import guru.nidi.codeassert.model.ClassFileParser.Constant;
import guru.nidi.codeassert.model.ClassFileParser.FieldOrMethodInfo;

import java.io.IOException;

class JavaClassImportBuilder {
    private static final String ATTR_ANNOTATIONS = "RuntimeVisibleAnnotations";
    private static final String ATTR_SIGNATURE = "Signature";
    private static final String ATTR_SOURCE = "SourceFile";

    private final PackageCollector collector;
    private final JavaClass jClass;
    private final Constant[] constantPool;

    public JavaClassImportBuilder(JavaClass jClass, PackageCollector collector, Constant[] constantPool) {
        this.jClass = jClass;
        this.collector = collector;
        this.constantPool = constantPool;
    }

    public void addClassName(String className) {
        jClass.setPackageName(getPackageName(className));
    }

    public void addSuperClass(String className) {
        addImport(getPackageName(className));
    }

    public void addInterfaces(String[] interfaceNames) {
        for (final String interfaceName : interfaceNames) {
            addImport(getPackageName(interfaceName));
        }
    }

    private Constant getConstantPoolEntry(int entryIndex) throws IOException {
        if (entryIndex < 0 || entryIndex >= constantPool.length) {
            throw new IOException("Illegal constant pool index : " + entryIndex);
        }
        return constantPool[entryIndex];
    }

    public void addClassConstantReferences() throws IOException {
        for (int j = 1; j < constantPool.length; j++) {
            if (constantPool[j].tag == ClassFileParser.CONSTANT_CLASS) {
                final String name = toUTF8(constantPool[j].nameIndex);
                addImport(getPackageName(name));
            }

            if (constantPool[j].tag == ClassFileParser.CONSTANT_DOUBLE || constantPool[j].tag == ClassFileParser.CONSTANT_LONG) {
                j++;
            }
        }
    }

    public void addMethodRefs(FieldOrMethodInfo[] methods) throws IOException {
        addMethodAnnotationRefs(methods);
        addMethodSignatureRefs(methods);
        addMethodTypes(methods);
    }

    private void addMethodTypes(FieldOrMethodInfo[] methods) throws IOException {
        for (final FieldOrMethodInfo method : methods) {
            final String descriptor = toUTF8(method.descriptorIndex);
            final String[] types = descriptorToTypes(descriptor);
            for (final String type : types) {
                if (type.length() > 0) {
                    addImport(getPackageName(type));
                }
            }
        }
    }

    private void addMethodSignatureRefs(FieldOrMethodInfo[] methods) throws IOException {
        for (final FieldOrMethodInfo info : methods) {
            if (info.signature != null) {
                final String name = toUTF8(u2(info.signature.value, 0));
                for (final String pack : SignatureParser.parseMethodSignature(name).getPackages()) {
                    addImport(pack);
                }
            }
        }
    }

    private void addMethodAnnotationRefs(FieldOrMethodInfo[] methods) throws IOException {
        for (int j = 1; j < methods.length; j++) {
            if (methods[j].runtimeVisibleAnnotations != null) {
                addAnnotationReferences(methods[j].runtimeVisibleAnnotations);
            }
        }
    }

    public void addFieldRefs(FieldOrMethodInfo[] fields) throws IOException {
        addFieldAnnotationRefs(fields);
        addFieldSignatureRefs(fields);
        addFieldTypes(fields);
    }

    private void addFieldSignatureRefs(FieldOrMethodInfo[] fields) throws IOException {
        for (final FieldOrMethodInfo info : fields) {
            if (info.signature != null) {
                final String name = toUTF8(u2(info.signature.value, 0));
                for (final String pack : SignatureParser.parseFieldSignature(name).getPackages()) {
                    addImport(pack);
                }
            }
        }
    }

    private void addFieldAnnotationRefs(FieldOrMethodInfo[] fields) throws IOException {
        for (int j = 1; j < fields.length; j++) {
            if (fields[j].runtimeVisibleAnnotations != null) {
                addAnnotationReferences(fields[j].runtimeVisibleAnnotations);
            }
        }
    }

    private void addFieldTypes(FieldOrMethodInfo[] fields) throws IOException {
        for (final FieldOrMethodInfo field : fields) {
            final String descriptor = toUTF8(field.descriptorIndex);
            final String[] types = descriptorToTypes(descriptor);
            for (final String type : types) {
                addImport(getPackageName(type));
            }
        }
    }

    public void addAttributeRefs(AttributeInfo[] attributes) throws IOException {
        addAttributeAnnotationRefs(attributes);
        addAttributeSignatureRefs(attributes);
    }

    private void addAttributeSignatureRefs(AttributeInfo[] attributes) throws IOException {
        for (final AttributeInfo attr : attributes) {
            if (attr.name.equals(ATTR_SIGNATURE)) {
                final String name = toUTF8(u2(attr.value, 0));
                for (final String pack : SignatureParser.parseClassSignature(name).getPackages()) {
                    addImport(pack);
                }
            }
        }
    }

    private void addAttributeAnnotationRefs(AttributeInfo[] attributes) throws IOException {
        for (int j = 1; j < attributes.length; j++) {
            if (ATTR_ANNOTATIONS.equals(attributes[j].name)) {
                addAnnotationReferences(attributes[j]);
            }
        }
    }

    private void addAnnotationReferences(AttributeInfo annotation) throws IOException {
        // JVM Spec 4.8.15
        final byte[] data = annotation.value;
        final int numAnnotations = u2(data, 0);
        final int annotationIndex = 2;
        addAnnotationReferences(data, annotationIndex, numAnnotations);
    }

    private int addAnnotationReferences(byte[] data, int index, int numAnnotations) throws IOException {
        int visitedAnnotations = 0;
        int i = index;
        while (visitedAnnotations < numAnnotations) {
            final int typeIndex = u2(data, i);
            final int numElementValuePairs = u2(data, i += 2);
            addImport(getPackageName(toUTF8(typeIndex).substring(1)));
            int visitedElementValuePairs = 0;
            i += 2;
            while (visitedElementValuePairs < numElementValuePairs) {
                i = addAnnotationElementValueReferences(data, i + 2);
                visitedElementValuePairs++;
            }
            visitedAnnotations++;
        }
        return i;
    }

    private int addAnnotationElementValueReferences(byte[] data, int i) throws IOException {
        final byte tag = data[i];
        switch (tag) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':
                return i + 3;
            case 'e':
                final int enumTypeIndex = u2(data, i + 1);
                addImport(getPackageName(toUTF8(enumTypeIndex).substring(1)));
                return i + 5;
            case 'c':
                final int classInfoIndex = u2(data, i + 1);
                addImport(getPackageName(toUTF8(classInfoIndex).substring(1)));
                return i + 3;
            case '@':
                return addAnnotationReferences(data, i + 1, 1);
            case '[':
                final int numValues = u2(data, i + 1);
                int k = i + 3;
                for (int j = 0; j < numValues; j++) {
                    k = addAnnotationElementValueReferences(data, k);
                }
                return k;
            default:
                throw new RuntimeException("Unknown tag '" + tag + "'");
        }
    }

    private int u2(byte[] data, int index) {
        return (data[index] << 8 & 0xFF00) | (data[index + 1] & 0xFF);
    }

    private String toUTF8(int entryIndex) throws IOException {
        final Constant entry = getConstantPoolEntry(entryIndex);
        if (entry.tag == ClassFileParser.CONSTANT_UTF8) {
            return (String) entry.value;
        }

        throw new IOException("Constant pool entry is not a UTF8 type: " + entryIndex);
    }

    private void addImport(String importPackage) {
        if (importPackage != null && collector.accept(importPackage)) {
            jClass.addImport(new JavaPackage(importPackage));
        }
    }

    private String slashesToDots(String s) {
        return s.replace('/', '.');
    }

    private String getPackageName(String s) {
        final String typed;
        if (s.length() > 0 && s.charAt(0) == '[') {
            final String types[] = descriptorToTypes(s);
            if (types.length == 0) {
                return null; // primitives
            }
            typed = types[0];
        } else {
            typed = s;
        }

        final String dotted = slashesToDots(typed);
        final int pos = dotted.lastIndexOf(".");
        if (pos > 0) {
            return dotted.substring(0, pos);
        }

        return "Default";
    }

    private String[] descriptorToTypes(String descriptor) {
        int typesCount = 0;
        for (int i = 0; i < descriptor.length(); i++) {
            if (descriptor.charAt(i) == ';') {
                typesCount++;
            }
        }

        final String types[] = new String[typesCount];

        int typeIndex = 0;
        for (int index = 0; index < descriptor.length(); index++) {
            final int startIndex = descriptor.indexOf(ClassFileParser.CLASS_DESCRIPTOR, index);
            if (startIndex < 0) {
                break;
            }
            index = descriptor.indexOf(';', startIndex + 1);
            types[typeIndex++] = descriptor.substring(startIndex + 1, index);
        }

        return types;
    }
}
