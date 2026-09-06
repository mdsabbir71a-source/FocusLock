import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.immutable.value.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.File;
import java.util.*;

/** Reproducible, instruction-aware hotfix against the released v1.0.22 DEX.
 * Uses Android command-line tools' dexlib2; preserves unrelated classes.
 */
public final class TimerSaveHotfix {
    private static final String MAIN = "Lcom/focuslock/app/MainActivity;";
    private static final String CONTEXT = "Landroid/content/Context;";
    private static final String REST = "Lcom/focuslock/app/ProtectionRestarter;";
    private static final String HANDLER = "Landroid/os/Handler;";
    private static final String REFRESH = "Lcom/focuslock/app/MainActivity$15;";
    private static int haptics, prompts, starters, versions;

    private static ImmutableMethodReference ref(String cls, String name, String ret, String... params) {
        return new ImmutableMethodReference(cls, name, List.of(params), ret);
    }

    private static void call(MethodImplementationBuilder b, Opcode op, MethodReference ref, int... regs) {
        int[] r = new int[5];
        System.arraycopy(regs, 0, r, 0, regs.length);
        b.addInstruction(new BuilderInstruction35c(op, regs.length, r[0], r[1], r[2], r[3], r[4], ref));
    }

    private static MethodImplementation safeStarter() {
        // v4 is this; v0..v3 are locals. The existing refresh callback is retained.
        MethodImplementationBuilder b = new MethodImplementationBuilder(5);
        b.addLabel("try");
        call(b, Opcode.INVOKE_STATIC, ref(REST, "ensureMonitorRunning", "V", CONTEXT), 4);
        b.addLabel("end");
        b.addInstruction(new BuilderInstruction10t(Opcode.GOTO, b.getLabel("refresh")));
        b.addLabel("catch");
        b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_EXCEPTION, 0));
        call(b, Opcode.INVOKE_VIRTUAL, ref("Ljava/lang/Object;", "getClass", "Ljava/lang/Class;"), 0);
        b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        call(b, Opcode.INVOKE_VIRTUAL, ref("Ljava/lang/Class;", "getSimpleName", "Ljava/lang/String;"), 0);
        b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        b.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING, 1, new ImmutableStringReference("monitor_start_deferred")));
        call(b, Opcode.INVOKE_STATIC, ref("Lcom/focuslock/app/DiagnosticStore;", "record", "V", CONTEXT, "Ljava/lang/String;", "Ljava/lang/String;"), 4, 1, 0);
        b.addLabel("refresh");
        b.addInstruction(new BuilderInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference(HANDLER)));
        call(b, Opcode.INVOKE_DIRECT, ref(HANDLER, "<init>", "V"), 0);
        b.addInstruction(new BuilderInstruction21c(Opcode.NEW_INSTANCE, 1, new ImmutableTypeReference(REFRESH)));
        call(b, Opcode.INVOKE_DIRECT, ref(REFRESH, "<init>", "V", MAIN), 1, 4);
        b.addInstruction(new BuilderInstruction21s(Opcode.CONST_WIDE_16, 2, 1200));
        call(b, Opcode.INVOKE_VIRTUAL, ref(HANDLER, "postDelayed", "Z", "Ljava/lang/Runnable;", "J"), 0, 1, 2, 3);
        b.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        b.addCatch("Ljava/lang/RuntimeException;", b.getLabel("try"), b.getLabel("end"), b.getLabel("catch"));
        return b.getMethodImplementation();
    }

    private static Method patchMethod(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) return m;
        if (m.getDefiningClass().equals(MAIN) && m.getName().equals("startSavedMonitoring")) {
            starters++;
            impl = safeStarter();
        } else {
            boolean changed = false;
            List<Instruction> insns = new ArrayList<>();
            for (Instruction i : impl.getInstructions()) {
                if (i.getOpcode() == Opcode.SGET && i instanceof ReferenceInstruction ri
                        && ri.getReference() instanceof FieldReference f
                        && f.getDefiningClass().equals("Landroid/view/HapticFeedbackConstants;")
                        && Set.of("CONFIRM", "REJECT").contains(f.getName())) {
                    // Same instruction width; no new platform field is resolved.
                    insns.add(new ImmutableInstruction21s(Opcode.CONST_16, ((OneRegisterInstruction)i).getRegisterA(), 3));
                    haptics++;
                    changed = true;
                } else if (m.getDefiningClass().equals(MAIN) && m.getName().equals("startCommitment")
                        && i instanceof ReferenceInstruction ri && ri.getReference() instanceof MethodReference r
                        && r.getName().equals("requestPermissions")) {
                    for (int j = 0; j < i.getCodeUnits(); j++) insns.add(new ImmutableInstruction10x(Opcode.NOP));
                    prompts++;
                    changed = true;
                } else if (i.getOpcode() == Opcode.CONST_STRING && i instanceof ReferenceInstruction ri
                        && ri.getReference() instanceof StringReference s && s.getString().endsWith("1.0.21")) {
                    insns.add(new ImmutableInstruction21c(Opcode.CONST_STRING, ((OneRegisterInstruction)i).getRegisterA(), new ImmutableStringReference(s.getString().replace("1.0.21", "1.0.23"))));
                    versions++;
                    changed = true;
                } else if (m.getDefiningClass().equals("Lcom/focuslock/app/RemoteConfigStore;")
                        && i.getOpcode() == Opcode.CONST_16 && ((NarrowLiteralInstruction)i).getNarrowLiteral() == 121) {
                    insns.add(new ImmutableInstruction21s(Opcode.CONST_16, ((OneRegisterInstruction)i).getRegisterA(), 123));
                    changed = true;
                } else insns.add(i);
            }
            if (!changed) return m;
            impl = new ImmutableMethodImplementation(impl.getRegisterCount(), insns, impl.getTryBlocks(), impl.getDebugItems());
        }
        return new ImmutableMethod(m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
                m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), impl);
    }

    public static void main(String[] args) throws Exception {
        DexFile input = DexFileFactory.loadDexFile(new File(args[0]), Opcodes.forApi(26));
        List<ClassDef> classes = new ArrayList<>();
        for (ClassDef c : input.getClasses()) {
            List<Method> methods = new ArrayList<>();
            for (Method m : c.getMethods()) methods.add(patchMethod(m));
            List<Field> fields = new ArrayList<>();
            for (Field f : c.getFields()) {
                if (c.getType().equals("Lcom/focuslock/app/BuildConfig;") && f.getName().equals("VERSION_CODE")) {
                    fields.add(new ImmutableField(f.getDefiningClass(), f.getName(), f.getType(), f.getAccessFlags(), new ImmutableIntEncodedValue(123), f.getAnnotations(), f.getHiddenApiRestrictions()));
                } else if (c.getType().equals("Lcom/focuslock/app/BuildConfig;") && f.getName().equals("VERSION_NAME")) {
                    fields.add(new ImmutableField(f.getDefiningClass(), f.getName(), f.getType(), f.getAccessFlags(), new ImmutableStringEncodedValue("1.0.23"), f.getAnnotations(), f.getHiddenApiRestrictions()));
                } else fields.add(f);
            }
            classes.add(new ImmutableClassDef(c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                    c.getSourceFile(), c.getAnnotations(), fields, methods));
        }
        if (haptics != 3 || prompts != 1 || starters != 1) throw new IllegalStateException("Unexpected base APK: " + haptics + "/" + prompts + "/" + starters);
        DexPool.writeTo(args[1], new ImmutableDexFile(input.getOpcodes(), classes));
        System.out.printf("Patched %d haptic calls, %d save-time permission request, %d monitor starter; %d version strings.%n", haptics, prompts, starters, versions);
    }
}
