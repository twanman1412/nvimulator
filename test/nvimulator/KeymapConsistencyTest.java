package nvimulator;

import static nvimulator.core.Fixture.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every vi-* action bound in the keymap or the ex table must exist in actions.xml. */
public class KeymapConsistencyTest {
    static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new AssertionError("cannot read " + path + " (run from the repo root)", e);
        }
    }

    static Set<String> all(String text, String regex) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static Set<String> definedActions() {
        return all(read("actions.xml"), "ACTION NAME=\"([^\"]+)\"");
    }

    static Set<String> boundActions() {
        return all(read("Nvimulator_keys.props"), "^nvimulator\\.keys\\.[a-z-]+\\.([a-z0-9-]+?)(?:\\.\\d+)?=");
    }

    static Set<String> exBoundActions() {
        return all(read("src/nvimulator/ex/ExInterpreter.java"), "bind\\(\"[^\"]*\", \"([^\"]+)\"\\)");
    }

    public static void testEveryBindingHasAnAction() {
        Set<String> defined = definedActions();
        for (String a : boundActions())
            if (a.startsWith("vi-")) assertTrue("unbound keymap action " + a, defined.contains(a));
    }

    public static void testEveryExBindingHasAnAction() {
        Set<String> defined = definedActions();
        for (String a : exBoundActions())
            if (a.startsWith("vi-")) assertTrue("unbound ex action " + a, defined.contains(a));
    }
}
