package previewstripper;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PreviewStripper {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: PreviewStripper <inputDir> <outputDir>");
            System.exit(1);
        }
        Path inDir = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);

        Files.walk(inDir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(p -> {
                    try {
                        byte[] original = Files.readAllBytes(p);
                        ClassReader cr = new ClassReader(original);
                        ClassWriter cw = new ClassWriter(0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
                                // Wrap to inspect annotations
                                return new MethodVisitor(Opcodes.ASM9, mv) {
                                    boolean isPreview = false;
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                        if ("Landroidx/compose/ui/tooling/preview/Preview;".equals(descriptor)) {
                                            isPreview = true;
                                        }
                                        return super.visitAnnotation(descriptor, visible);
                                    }
                                    @Override
                                    public void visitCode() {
                                        if (isPreview) {
                                            // Skip emitting this method completely
                                            // by replacing its code with an empty stub and then immediately returning
                                            super.visitCode();
                                            super.visitInsn(Opcodes.RETURN);
                                            super.visitMaxs(0, 0);
                                            super.visitEnd();
                                        } else {
                                            super.visitCode();
                                        }
                                    }
                                };
                            }
                        };
                        cr.accept(cv, 0);
                        // write out to mirrored path under outDir
                        Path rel = inDir.relativize(p);
                        Path target = outDir.resolve(rel);
                        Files.createDirectories(target.getParent());
                        Files.write(target, cw.toByteArray());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

        System.out.println("PreviewStripper: done");
    }
}