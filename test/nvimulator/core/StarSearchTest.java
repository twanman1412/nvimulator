package nvimulator.core;

import static nvimulator.core.Fixture.*;

public class StarSearchTest {
    public static void testStar() { Fixture f = of("|foo bar foo").keys("*"); eq("foo bar |foo", f.render()); eq("\\b\\Qfoo\\E\\b", f.lastSearch()); check("foo bar |foo", "#", "|foo bar foo"); check("|  foo bar foo", "*", "  foo bar |foo"); eq("\\Qfoo\\E", of("|foo").keys("g*").lastSearch()); checkBeep("|  ", "*"); }
    public static void testCountAndMidWord() { check("|a x a y a", "2*", "a x a y |a"); check("a x a y |a", "#", "a x |a y a"); check("fo|o bar foo", "*", "foo bar |foo"); }
}
