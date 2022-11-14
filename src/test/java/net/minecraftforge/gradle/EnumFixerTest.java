package net.minecraftforge.gradle;

import com.google.common.io.Resources;
import net.minecraftforge.gradle.extrastuff.FFPatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class EnumFixerTest {
    private static final String INPUT = "TestClass";
    private static final String EXPECTED = "TestClassOut";

    @Test
    public void test() throws IOException {
        String input = readResource(INPUT);
        //String expected = readResource(EXPECTED);

        input = FFPatcher.processFile(INPUT + ".java", input, true);

        // check LineByLine...
        String[] expected = readResource(EXPECTED).split("\r\n|\r|\n");
        String[] actual = input.split("\r\n|\r|\n");

        Assertions.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            //System.out.println("EXPECTED >>"+expected[i]);
            //System.out.println("ACTUAL   >>"+actual[i]);
            Assertions.assertEquals(expected[i], actual[i]);
        }
    }

    private String readResource(String name) throws IOException {
        return Resources.toString(Resources.getResource(name), StandardCharsets.UTF_8);
    }
}
