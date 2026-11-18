package vimulator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/** Minimal dependency-free test runner: runs every public static void test*() in TESTS. */
public class TestRunner {
    static final Class<?>[] TESTS = {
        vimulator.core.TextModelTest.class,
        vimulator.core.BasicMotionTest.class,
        vimulator.core.KeyTokensTest.class,
        vimulator.core.WordMotionTest.class,
        vimulator.core.FindMotionTest.class,
        vimulator.core.OperatorTest.class,
        vimulator.core.TextObjectTest.class,
        vimulator.core.TransformTest.class,
        vimulator.core.VisualTest.class,
        vimulator.core.ScrollTest.class,
        vimulator.core.RepeatTest.class,
        vimulator.core.StarSearchTest.class,
        vimulator.core.InsertEditsTest.class,
    };

    public static void main(String[] args) throws Exception {
        int passed = 0, failed = 0;
        String filter = args.length > 0 ? args[0] : null;
        for (Class<?> c : TESTS) {
            Method[] methods = c.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method m : methods) {
                if (!m.getName().startsWith("test") || !Modifier.isStatic(m.getModifiers())
                        || !Modifier.isPublic(m.getModifiers()) || m.getParameterCount() != 0)
                    continue;
                String name = c.getSimpleName() + "." + m.getName();
                if (filter != null && !name.contains(filter)) continue;
                try {
                    m.invoke(null);
                    passed++;
                } catch (InvocationTargetException e) {
                    failed++;
                    Throwable t = e.getCause();
                    System.out.println("FAIL " + name + ": " + t);
                    if (!(t instanceof AssertionError)) {
                        for (StackTraceElement el : t.getStackTrace()) {
                            if (el.getClassName().startsWith("vimulator"))
                                System.out.println("    at " + el);
                        }
                    }
                }
            }
        }
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
