package com.airbnb.paris.processor;

import com.squareup.javapoet.ClassName;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

class ResourceProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Trees trees;

    void init(ProcessingEnvironment processingEnv) {
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        try {
            trees = Trees.instance(processingEnv);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Returns the {@link Id} that is used as an annotation value of the given {@link Element}
     */
    Id getId(Class<? extends Annotation> annotation, Element element, int value) {
        Map<Integer, Id> results = getResults(annotation, element);
        if (results.containsKey(value)) {
            return results.get(value);
        } else {
            return new Id(value);
        }
    }

    private static AnnotationMirror getMirror(Element element,
                                              Class<? extends Annotation> annotation) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotation.getCanonicalName())) {
                return annotationMirror;
            }
        }
        return null;
    }

    private Map<Integer, Id> getResults(Class<? extends Annotation> annotation, Element element) {
        AnnotationScanner scanner = new AnnotationScanner();
        JCTree tree = (JCTree) trees.getTree(element, getMirror(element, annotation));
        if (tree != null) { // tree can be null if the references are compiled types and not source
            tree.accept(scanner);
        }
        return scanner.results();
    }

    private class AnnotationScanner extends TreeScanner {

        private final Map<Integer, Id> results = new HashMap<>();

        @Override public void visitSelect(JCTree.JCFieldAccess jcFieldAccess) {
            Symbol symbol = jcFieldAccess.sym;
            if (symbol != null
                    && symbol.getEnclosingElement() != null
                    && symbol.getEnclosingElement().getEnclosingElement() != null
                    && symbol.getEnclosingElement().getEnclosingElement().enclClass() != null) {
                parseResourceSymbol((Symbol.VarSymbol) symbol);
            }
        }

        private void parseResourceSymbol(Symbol.VarSymbol symbol) {
            // eg io.sweers.barber.R
            String rClass = symbol.getEnclosingElement().getEnclosingElement().enclClass().className();
            // eg styleable
            String rTypeClass = symbol.getEnclosingElement().getSimpleName().toString();
            // eg BarberView_stripeColor
            String resourceName = symbol.getSimpleName().toString();

            Object value = symbol.getConstantValue();
            if (!(value instanceof Integer)) {
                return;
            }

            Id id = new Id((int) value, getClassName(rClass, rTypeClass), resourceName);
            results.put(id.value, id);
        }

        Map<Integer, Id> results() {
            return results;
        }
    }

    private ClassName getClassName(String rClass, String rTypeClass) {
        Element rClassElement;
        try {
            rClassElement = elementUtils.getTypeElement(rClass);
        } catch (MirroredTypeException mte) {
            rClassElement = typeUtils.asElement(mte.getTypeMirror());
        }

        String rClassPackageName =
                elementUtils.getPackageOf(rClassElement).getQualifiedName().toString();
        return ClassName.get(rClassPackageName, "R", rTypeClass);
    }
}
