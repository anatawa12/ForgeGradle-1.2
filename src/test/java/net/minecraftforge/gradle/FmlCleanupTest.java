package net.minecraftforge.gradle;

import com.google.common.io.ByteStreams;
import net.minecraftforge.gradle.extrastuff.FmlCleanup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

public class FmlCleanupTest {
    private static final String INPUT = "AnonymousTest";
    private static final String EXPECTED = "AnonymousTestOut";

    @Test
    public void tstAnonymousClassRenaming() throws IOException {
        String input = readResource(INPUT);

        input = FmlCleanup.renameClass(input);

        String[] expected = readResource(EXPECTED).split("\r\n|\r|\n");
        String[] actual = input.split("\r\n|\r|\n");

        //Assertions.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            System.out.println("EXPECTED >>" + expected[i]);
            System.out.println("ACTUAL   >>" + actual[i]);
            Assertions.assertEquals(expected[i], actual[i]);
        }
    }

    private String readResource(String name) throws IOException {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(name);
        return new String(ByteStreams.toByteArray(stream));
    }
}
