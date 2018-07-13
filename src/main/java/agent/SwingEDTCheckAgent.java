package agent;

import static java.awt.EventQueue.*;
import static java.lang.Thread.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Original source: StackOverflow: 
 * http://stackoverflow.com/questions/17760204/how-to-check-a-swing-application-for-correct-use-of-the-edt-event-dispatch-thre
 * By user: ruediste http://stackoverflow.com/users/1290557/ruediste
 * CC by-sa 3.0 http://creativecommons.org/licenses/by-sa/3.0
 */

/**
 * A java agent which transforms the Swing Component classes in such a way that
 * a stack trace will be dumped or an exception will be thrown when they are
 * accessed from a wrong thread.
 * 
 * To use it, add
 * 
 * <pre>
 * ${workspace_loc:mespas/tool/util/swingEDTCheck}/swingEDTCheck.jar
 * </pre>
 * 
 * to the VM arguments of a run configuration. This will cause the stack traces
 * to be dumped.
 * 
 * Use
 * 
 * <pre>
 * ${workspace_loc:mespas/tool/util/swingEDTCheck}/swingEDTCheck.jar=throw
 * </pre>
 * 
 * to throw exceptions.
 * 
 */
public class SwingEDTCheckAgent {

	public static void premain(String args, Instrumentation inst) {
		boolean throwing = false;
		if ("throw".equals(args)) {
			throwing = true;
		}
		System.out.println("SwingEDTCheckAgent running XXX");
		inst.addTransformer(new Transformer(throwing));
	}

	private static class Transformer implements ClassFileTransformer {

		private final boolean throwing;

		public Transformer(boolean throwing) {
			this.throwing = throwing;
		}

		@Override
		public byte[] transform(ClassLoader loader, String className,
				Class classBeingRedefined, ProtectionDomain protectionDomain,
				byte[] classfileBuffer) throws IllegalClassFormatException {
			// Process all classes in javax.swing package which names start with
			// J
			if (className.startsWith("javax/swing/J")) {
				try {
					System.out.println("Instrumentation of " + className);
					ClassReader cr = new ClassReader(classfileBuffer);
					// ClassWriter cw = new ClassWriter(cr,
					// ClassWriter.COMPUTE_FRAMES);
					ClassWriter cw = new ClassWriter(cr,
							ClassWriter.COMPUTE_FRAMES);
					ClassVisitor cv = new EdtCheckerClassAdapter(cw, throwing);
					cr.accept(cv, 0);
					return cw.toByteArray();
				} catch (Throwable e) {
					e.printStackTrace();
					// throw e;
				}
			}
			return classfileBuffer;

		}
	}

	private static class EdtCheckerClassAdapter extends ClassVisitor {

		private boolean throwing;

		public EdtCheckerClassAdapter(ClassVisitor classVisitor,
				boolean throwing) {
			super(Opcodes.ASM5, classVisitor);
			try {
				this.throwing = throwing;
				// System.out
				// .println("EdtCheckerClassAdapter for " + classVisitor);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public MethodVisitor visitMethod(final int access, final String name,
				final String desc, final String signature,
				final String[] exceptions) {
			MethodVisitor mv = cv.visitMethod(access, name, desc, signature,
					exceptions);
			// System.out.println("Checking " + name);
			// if ("getHeight".equals(name) || "getWidth".equals(name))
			// return mv;
			if (name.startsWith("set") || name.startsWith("get")
					|| name.startsWith("is")) {
				return new EdtCheckerMethodAdapter(mv, throwing);
			} else {
				return mv;
			}
		}
	}

	private static class EdtCheckerMethodAdapter extends MethodVisitor {

		private final boolean throwing;

		public EdtCheckerMethodAdapter(MethodVisitor methodVisitor,
				boolean throwing) {
			super(Opcodes.ASM5, methodVisitor);
			this.throwing = throwing;
		}

		// @Override
		// public void visitCode() {
		// mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/awt/EventQueue",
		// "isDispatchThread", "()Z");
		// Label l1 = new Label();
		// mv.visitJumpInsn(Opcodes.IFNE, l1);
		// Label l2 = new Label();
		// mv.visitLabel(l2);
		//
		// if (throwing) {
		// // more Aggressive: throw exception
		// mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
		// mv.visitInsn(Opcodes.DUP);
		// mv.visitLdcInsn("Swing Component called from outside the EDT");
		// mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
		// "java/lang/RuntimeException", "<init>",
		// "(Ljava/lang/String;)V");
		// mv.visitInsn(Opcodes.ATHROW);
		//
		// } else {
		// // this just dumps the Stack Trace
		// mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread",
		// "dumpStack", "()V");
		// }
		// mv.visitLabel(l1);
		// }

		@Override
		public void visitCode() {
			// agent.SwingEDTCheckAgent.checkThreadViolations();
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "agent/SwingEDTCheckAgent",
					"checkThreadViolations", "()V");
		}

		private static void checkThreadViolations1() {
			if (!isDispatchThread()) {
				Thread.dumpStack();
			}
		}

	}

	public static void checkThreadViolations() {
		if (!isDispatchThread()) {
			boolean repaint = false;
			boolean fromSwing = false;
			boolean imageUpdate = false;
			StackTraceElement[] stackTrace = currentThread().getStackTrace();
			for (StackTraceElement st : stackTrace) {
				if (repaint && st.getClassName().startsWith("javax.swing.")) {
					fromSwing = true;
				}
				if (repaint && "imageUpdate".equals(st.getMethodName())) {
					imageUpdate = true;
				}
				if ("repaint".equals(st.getMethodName())) {
					repaint = true;
					fromSwing = false;
				}
			}
			if (imageUpdate) {
				// assuming it is
				// java.awt.image.ImageObserver.imageUpdate(...)
				// image was asynchronously updated, that's ok
				return;
			}
			if (repaint && !fromSwing) {
				// no problems here, since repaint() is thread safe
				return;
			}
			// if (true) {
			// throw new RuntimeException(
			// "Swing Component called from outside the EDT");
			// } else {
			// Thread.dumpStack();
			// }
			Thread.dumpStack();
		}
	}
}
