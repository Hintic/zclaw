package com.zxx.zclaw.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class CliIndentTest {

    @Test
    void prefixesEachLine() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        CliIndent.printlnIndented(ps, "a\nb\n");
        ps.flush();
        assertEquals("  a\n  b\n  \n", baos.toString());
    }
}
