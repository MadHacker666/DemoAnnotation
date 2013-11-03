package com.example.annotation;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes(value = {HashedAnnotationProcessor.ANNOTATION_TYPE})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({HashedAnnotationProcessor.ENABLE_OPTIONS_NAME})
public class HashedAnnotationProcessor extends AbstractProcessor {
    public static final String ANNOTATION_TYPE = "com.example.annotation.Hashed";
    public static final String ENABLE_OPTIONS_NAME = "Hashed";
    private static final String SUPPORT_FIELD_TYPE = "String";
    private JavacProcessingEnvironment javacProcessingEnv;
    private TreeMaker maker;

    private boolean enable = true;

    @Override
    public void init(ProcessingEnvironment procEnv) {
        super.init(procEnv);
        this.javacProcessingEnv = (JavacProcessingEnvironment) procEnv;
        this.maker = TreeMaker.instance(javacProcessingEnv.getContext());
        java.util.Map<java.lang.String,java.lang.String> opt = javacProcessingEnv.getOptions();
        if (opt.containsKey(ENABLE_OPTIONS_NAME) && opt.get(ENABLE_OPTIONS_NAME).equals("disable")){
            enable = false;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!enable){
            javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Annotation Hashed is disable");
            return false;
        }
        if ( annotations == null || annotations.isEmpty()) {
            return false;
        }
        //Получаем вспомогательные инструменты.
        JavacElements utils = javacProcessingEnv.getElementUtils();
        for (TypeElement annotation : annotations)
        {
            //убеждаемся, что аннотация наша (не уверен, что сюда могут попасть не наши, но...)
            if (ANNOTATION_TYPE.equals(annotation.asType().toString())){
                // Выбираем все элементы, у которых стоит наша аннотация
                final Set<? extends Element> fields = roundEnv.getElementsAnnotatedWith(annotation);
                for (final Element field : fields) {
                    //Получаем саму аннотацию, чтоб достать из неё способ хеширования.
                    Hashed hashed = field.getAnnotation(Hashed.class);
                    //Мы выбрали все элементы с нашей аннотацией. Откуда тут нулл?
                    if (hashed == null){
                        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                "Bad annotate field: System error - annotation is null",
                                field);
                        continue;
                    }
                    //преобразовываем аннотированный элемент в дерево
                    JCTree blockNode = utils.getTree(field);
                    // Нам нужны только описания полей
                    if (blockNode instanceof JCTree.JCVariableDecl) {
                        JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) blockNode;
                        if (!SUPPORT_FIELD_TYPE.equals(var.vartype.toString())){
                            javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    String.format("Unsupported field type \"%s\" for hashed. Supported only String.", var.vartype.toString()),
                                    field);
                            continue;
                        }
                        JCTree.JCExpression initializer = var.getInitializer();
                        //Проверка отсечёт поля с инициализацией в конструкторе, а так же конструкции вида:
                        // "" + 1
                        // new String("new string")
                        if ((initializer != null) && (var.getInitializer() instanceof JCTree.JCLiteral)){
                            //Берём строку инициализации.
                            JCTree.JCLiteral lit = (JCTree.JCLiteral) var.getInitializer();
                            //получаем строку
                            String value = lit.getValue().toString();
                            //Шифруем по заданному методу:
                            try {
                                MessageDigest md = MessageDigest.getInstance(hashed.method());
                                //Для однообразия на разных платформах задаём локаль.
                                md.update(value.getBytes("UTF-8"));
                                byte[] hash = md.digest();
                                StringBuilder str = new StringBuilder(hash.length * 2);
                                for (byte val : hash) {
                                    str.append(String.format("%02X", val & 0xFF));
                                }
                                value = str.toString();
                                lit = maker.Literal(value);
                                var.init = lit;
                            } catch (NoSuchAlgorithmException e) {
                                javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                        String.format("Unsupported digest method %s", hashed.method()),
                                        field);
                            } catch (UnsupportedEncodingException e) {
                                javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                        "Bad environment. Required to support UTF-8",
                                        field);
                            }
                        }else{
                            javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    "Bad annotation: supported only literal string. Example: \"Good string\" ",
                                    field);
                        }

                    }else{
                        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                "Bad annotate. Supported only class variable field.",
                                field);
                    }
                }

            }
        }
        return true;
    }


}
