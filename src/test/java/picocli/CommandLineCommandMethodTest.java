/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import picocli.CommandLineTest.CompactFields;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.IParseResultHandler;
import static picocli.CommandLine.MissingParameterException;
import static picocli.CommandLine.Model;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;
import static picocli.CommandLine.UnmatchedArgumentException;
import static picocli.CommandLineTest.verifyCompact;
import static picocli.HelpTestUtil.setTraceLevel;

/**
 * Tests for {@code @Command} methods.
 */
public class CommandLineCommandMethodTest {
    @Rule
    public final ProvideSystemProperty ansiOFF = new ProvideSystemProperty("picocli.ansi", "false");

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Before public void setUp() { System.clearProperty("picocli.trace"); }
    @After public void tearDown() { System.clearProperty("picocli.trace"); }

    static class MethodAppBase {
        @Command(name="run-0")
        public void run0() {}
    }

    @Command(name="method")
    static class MethodApp extends MethodAppBase {

        @Command(name="run-1")
        int run1(int a) {
            return a;
        }

        @Command(name="run-2")
        int run2(int a, @Option(names="-b", required=true) int b) {
            return a*b;
        }
    }
	@SuppressWarnings("deprecation")
    @Test
    public void testAnnotateMethod_noArg() throws Exception {
        setTraceLevel("OFF");
        Method m = CommandLine.getCommandMethods(MethodApp.class, "run0").get(0);
        CommandLine cmd1 = new CommandLine(m);
        assertEquals("run-0", cmd1.getCommandName());
        assertEquals(Arrays.asList(), cmd1.getCommandSpec().args());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        cmd1.parseWithHandler(((IParseResultHandler) null), new PrintStream(baos), new String[]{"--y"});
        assertEquals(Arrays.asList("--y"), cmd1.getUnmatchedArguments());

        // test execute
        Object ret = CommandLine.invoke(m.getName(), MethodApp.class, new PrintStream(new ByteArrayOutputStream()));
        assertNull("return value", ret);

        setTraceLevel("WARN");
    }
    @Test
    public void testAnnotateMethod_unannotatedPositional() throws Exception {
        Method m = CommandLine.getCommandMethods(MethodApp.class, "run1").get(0);

        // test required
        try {
            CommandLine.populateCommand(m);
            fail("Missing required field should have thrown exception");
        } catch (MissingParameterException ex) {
            assertEquals("Missing required parameter: <arg0>", ex.getMessage());
        }

        // test execute
        Object ret = CommandLine.invoke(m.getName(), MethodApp.class, new PrintStream(new ByteArrayOutputStream()), "42");
        assertEquals("return value", 42, ((Number)ret).intValue());
    }

    @Command
    static class UnannotatedPositional {
        @Command
        public void x(int a, int b, int c, int[] x, String[] y) {}
    }

    @Test
    public void testAnnotateMethod_unannotatedPositional_indexByParameterOrder() throws Exception {
        Method m = CommandLine.getCommandMethods(UnannotatedPositional.class, "x").get(0);
        CommandLine cmd = new CommandLine(m);
        Model.CommandSpec spec = cmd.getCommandSpec();
        List<Model.PositionalParamSpec> positionals = spec.positionalParameters();
        String[] labels = { "<arg0>", "<arg1>", "<arg2>", "<arg3>", "<arg4>"};
        assertEquals(positionals.size(), labels.length);

        for (int i = 0; i < positionals.size(); i++) {
            Model.PositionalParamSpec positional = positionals.get(i);
            assertEquals(positional.paramLabel() + " at index " + i, CommandLine.Range.valueOf(i + ""), positional.index());
            assertEquals(labels[i], positional.paramLabel());
        }
    }

    @Command
    static class PositionalsMixedWithOptions {
        @Command
        public void mixed(int a, @Option(names = "-b") int b, @Option(names = "-c") String c, int[] x, String[] y) {}
    }

    @Test
    public void testAnnotateMethod_unannotatedPositionalMixedWithOptions_indexByParameterOrder() throws Exception {
        Method m = CommandLine.getCommandMethods(PositionalsMixedWithOptions.class, "mixed").get(0);
        CommandLine cmd = new CommandLine(m);
        Model.CommandSpec spec = cmd.getCommandSpec();
        List<Model.PositionalParamSpec> positionals = spec.positionalParameters();
        String[] labels = { "<arg0>", "<arg3>", "<arg4>"};
        assertEquals(positionals.size(), labels.length);

        for (int i = 0; i < positionals.size(); i++) {
            Model.PositionalParamSpec positional = positionals.get(i);
            assertEquals(positional.paramLabel() + " at index " + i, CommandLine.Range.valueOf(i + ""), positional.index());
            assertEquals(labels[i], positional.paramLabel());
        }

        assertEquals(2, spec.options().size());
        assertEquals(int.class, spec.findOption("-b").type());
        assertEquals(String.class, spec.findOption("-c").type());
    }

    @Test
    public void testAnnotateMethod_annotated() throws Exception {
        Method m = CommandLine.getCommandMethods(MethodApp.class, "run2").get(0);

        // test required
        try {
            CommandLine.populateCommand(m, "0");
            fail("Missing required option should have thrown exception");
        } catch (MissingParameterException ex) {
            assertEquals("Missing required option '-b=<arg1>'", ex.getMessage());
        }

        // test execute
        Object ret = CommandLine.invoke(m.getName(), MethodApp.class, new PrintStream(new ByteArrayOutputStream()), "13", "-b", "-1");
        assertEquals("return value", -13, ((Number)ret).intValue());
    }

    @Test
    public void testCommandMethodsFromSuperclassAddedToSubcommands() throws Exception {

        CommandLine cmd = new CommandLine(MethodApp.class);
        assertEquals("method", cmd.getCommandName());
        assertEquals(3, cmd.getSubcommands().size());
        assertEquals(0, cmd.getSubcommands().get("run-0").getCommandSpec().args().size());
        assertEquals(1, cmd.getSubcommands().get("run-1").getCommandSpec().args().size());
        assertEquals(2, cmd.getSubcommands().get("run-2").getCommandSpec().args().size());

        //CommandLine.usage(cmd.getSubcommands().get("run-2"), System.out);
    }

    /** @see CompactFields */
    private static class CompactFieldsMethod {
        @Command
        public CompactFields run(
            @Option(names = "-v", paramLabel="<verbose>" /* useless, but required for Assert.equals() */) boolean verbose,
            @Option(names = "-r", paramLabel="<recursive>" /* useless, but required for Assert.equals() */) boolean recursive,
            @Option(names = "-o", paramLabel="<outputFile>" /* required only for Assert.equals() */) File outputFile,
            @Parameters(paramLabel="<inputFiles>" /* required only for Assert.equals() */) File[] inputFiles)
        {
            CompactFields ret = new CommandLineTest.CompactFields();
            ret.verbose = verbose;
            ret.recursive = recursive;
            ret.outputFile = outputFile;
            ret.inputFiles = inputFiles;
            return ret;
        }
    }
    @Test
    public void testAnnotateMethod_matchesAnnotatedClass() throws Exception {
        setTraceLevel("OFF");
        CommandLine classCmd = new CommandLine(new CompactFields());
        Method m = CompactFieldsMethod.class.getDeclaredMethod("run", new Class<?>[] {boolean.class, boolean.class, File.class, File[].class});
        CommandLine methodCmd = new CommandLine(m);
        assertEquals("run", methodCmd.getCommandName());
        assertEquals("argument count", classCmd.getCommandSpec().args().size(), methodCmd.getCommandSpec().args().size());
        for (int i = 0;  i < classCmd.getCommandSpec().args().size(); i++) {
            Model.ArgSpec classArg = classCmd.getCommandSpec().args().get(i);
            Model.ArgSpec methodArg = methodCmd.getCommandSpec().args().get(i);
            assertEquals("arg #" + i, classArg, methodArg);
        }
        setTraceLevel("WARN");
    }
    /** replicate {@link CommandLineTest#testCompactFieldsAnyOrder()} but using
     * {@link CompactFieldsMethod#run(boolean, boolean, File, File[])}
     * as source of the {@link Command} annotation. */
    @Test
    public void testCompactFieldsAnyOrder_method() throws Exception {
        final Method m = CompactFieldsMethod.class.getDeclaredMethod("run", new Class<?>[] {boolean.class, boolean.class, File.class, File[].class});
        String[] tests = {
                "-rvoout",
                "-vroout",
                "-vro=out",
                "-rv p1 p2",
                "p1 p2",
                "-voout p1 p2",
                "-voout -r p1 p2",
                "-r -v -oout p1 p2",
                "-rv -o out p1 p2",
                "-oout -r -v p1 p2",
                "-rvo out p1 p2",
        };
        for (String test : tests) {
            // parse
            CompactFields compact = CommandLine.populateCommand(new CompactFields(), test.split(" "));
            List<CommandLine> result = new CommandLine(m).parse(test.split(" "));

            // extract arg values
            assertEquals(1, result.size());
            Object[] methodArgValues = result.get(0).getCommandSpec().argValues();
            assertNotNull(methodArgValues);

            // verify parsing had the same result
            verifyCompact(compact, (Boolean)methodArgValues[0], (Boolean)methodArgValues[1], methodArgValues[2] == null ? null : String.valueOf(methodArgValues[2]), (File[])methodArgValues[3]);

            // verify method is callable (args have the correct/assignable type)
            CompactFields methodCompact = (CompactFields) m.invoke(new CompactFieldsMethod(), methodArgValues); // should not throw

            // verify passed args are the same
            assertNotNull(methodCompact);
            assertEquals(compact.verbose, methodCompact.verbose);
            assertEquals(compact.recursive, methodCompact.recursive);
            assertEquals(compact.outputFile, methodCompact.outputFile);
            assertArrayEquals(compact.inputFiles, methodCompact.inputFiles);
        }
        try {
            CommandLine.populateCommand(m, "-oout -r -vp1 p2".split(" "));
            fail("should fail: -v does not take an argument");
        } catch (UnmatchedArgumentException ex) {
            assertEquals("Unknown option: -p1", ex.getMessage());
        }
    }

    static class CommandMethod1 {
        @Command(mixinStandardHelpOptions = true, version = "1.2.3")
        public int times(@Option(names = "-l", defaultValue = "2") int left,
                         @Option(names = "-r", defaultValue = "3") int right) {
            return left * right;
        }
    }

    @Test
    public void testCommandMethodDefaults() {
        Object timesResultBothDefault = CommandLine.invoke("times", CommandMethod1.class);
        assertEquals("both default", 6, ((Integer) timesResultBothDefault).intValue());

        Object timesResultLeftDefault = CommandLine.invoke("times", CommandMethod1.class, "-r", "8");
        assertEquals("right default", 16, ((Integer) timesResultLeftDefault).intValue());

        Object timesResultRightDefault = CommandLine.invoke("times", CommandMethod1.class, "-l", "8");
        assertEquals("left default", 24, ((Integer) timesResultRightDefault).intValue());

        Object timesResultNoDefault = CommandLine.invoke("times", CommandMethod1.class, "-r", "4", "-l", "5");
        assertEquals("no default", 20, ((Integer) timesResultNoDefault).intValue());
    }

    @Test
    public void testCommandMethodMixinHelp() {
        CommandLine.invoke("times", CommandMethod1.class, "-h");
        String expected = String.format("" +
                "Usage: times [-hV] [-l=<arg0>] [-r=<arg1>]%n" +
                "  -h, --help      Show this help message and exit.%n" +
                "  -l= <arg0>%n" +
                "  -r= <arg1>%n" +
                "  -V, --version   Print version information and exit.%n" +
                "");
        assertEquals(expected, systemOutRule.getLog());
    }

    @Test
    public void testCommandMethodMixinVersion() {
        CommandLine.invoke("times", CommandMethod1.class, "--version");
        String expected = String.format("1.2.3%n");
        assertEquals(expected, systemOutRule.getLog());
    }

    static class UnAnnotatedClassWithoutAnnotatedFields {
        @Command public void cmd1(@Option(names = "-x") int x, File f) { }
        @Command public void cmd2(@Option(names = "-x") int x, File f) { }
    }

    @Test
    public void testMethodCommandsAreNotSubcommandsOfNonAnnotatedClass() {
        try {
            new CommandLine(new UnAnnotatedClassWithoutAnnotatedFields());
            fail("expected exception");
        } catch (CommandLine.InitializationException ex) {
            assertEquals("picocli.CommandLineCommandMethodTest$UnAnnotatedClassWithoutAnnotatedFields " +
                            "is not a command: it has no @Command, @Option, " +
                            "@Parameters or @Unmatched annotations", ex.getMessage());
        }
    }

    static class UnAnnotatedClassWithAnnotatedField {
        @Option(names = "-y") int y;

        @Command public void cmd1(@Option(names = "-x") int x, File f) { }
        @Command public void cmd2(@Option(names = "-x") int x, File f) { }
    }

    @Test
    public void testMethodCommandsAreNotSubcommandsOfNonAnnotatedClassWithAnnotatedFields() {
        CommandLine cmd = new CommandLine(new UnAnnotatedClassWithAnnotatedField());
        assertNotNull(cmd.getCommandSpec().findOption('y'));

        assertTrue(cmd.getSubcommands().isEmpty());
        assertNull(cmd.getCommandSpec().findOption('x'));
    }

    @Command
    static class AnnotatedClassWithoutAnnotatedFields {
        @Command public void cmd1(@Option(names = "-x") int x, File f) { }
        @Command public void cmd2(@Option(names = "-x") int x, File f) { }
    }

    @Test
    public void testMethodCommandsAreSubcommandsOfAnnotatedClass() {
        CommandLine cmd = new CommandLine(new AnnotatedClassWithoutAnnotatedFields());
        assertNull(cmd.getCommandSpec().findOption('x'));

        assertEquals(2, cmd.getSubcommands().size());
        assertEquals(set("cmd1", "cmd2"), cmd.getSubcommands().keySet());

        String expected = String.format("" +
                "Usage: <main class> [COMMAND]%n" +
                "Commands:%n" +
                "  cmd1%n" +
                "  cmd2%n");
        assertEquals(expected, cmd.getUsageMessage());
    }

    @Command(addMethodSubcommands = false)
    static class SwitchedOff {
        @Command public void cmd1(@Option(names = "-x") int x, File f) { }
        @Command public void cmd2(@Option(names = "-x") int x, File f) { }
    }

    @Test
    public void testMethodCommandsAreNotAddedAsSubcommandsIfAnnotationSaysSo() {
        CommandLine cmd = new CommandLine(new SwitchedOff());

        assertEquals(0, cmd.getSubcommands().size());

        String expected = String.format("" +
                "Usage: <main class>%n");
        assertEquals(expected, cmd.getUsageMessage());
    }

    /** Exemple from the documentation. */
    static class Cat {
        public static void main(String[] args) {
            CommandLine.invoke("cat", Cat.class, args);
        }

        @Command(description = "Concatenate FILE(s) to standard output.",
                mixinStandardHelpOptions = true, version = "3.6.0")
        void cat(@Option(names = {"-E", "--show-ends"}) boolean showEnds,
                 @Option(names = {"-n", "--number"}) boolean number,
                 @Option(names = {"-T", "--show-tabs"}) boolean showTabs,
                 @Option(names = {"-v", "--show-nonprinting"}) boolean showNonPrinting,
                 @Parameters(paramLabel = "FILE") File[] files) {
            // process files
        }
    }
    @Test
    public void testCatUsageHelpMessage() {
        CommandLine cmd = new CommandLine(CommandLine.getCommandMethods(Cat.class, "cat").get(0));
        String expected = String.format("" +
                "Usage: cat [-EhnTvV] [FILE...]%n" +
                "Concatenate FILE(s) to standard output.%n" +
                "      [FILE...]%n" +
                "  -E, --show-ends%n" +
                "  -h, --help               Show this help message and exit.%n" +
                "  -n, --number%n" +
                "  -T, --show-tabs%n" +
                "  -v, --show-nonprinting%n" +
                "  -V, --version            Print version information and exit.%n");
        assertEquals(expected, cmd.getUsageMessage());
    }

    @Command(name = "git", mixinStandardHelpOptions = true, version = "picocli-3.6.0",
            description = "Version control system.")
    static class Git {
        @Option(names = "--git-dir", description = "Set the path to the repository")
        File path;

        @Command(description = "Clone a repository into a new directory")
        void clone(@Option(names = {"-l", "--local"}) boolean local,
                   @Option(names = "-q", description = "Operate quietly.") boolean quiet,
                   @Option(names = "-v", description = "Run verbosely.") boolean verbose,
                   @Option(names = {"-b", "--branch"}) String branch,
                   @Parameters(paramLabel = "<repository>") String repo) {
            // ... implement business logic
        }

        @Command(description = "Record changes to the repository")
        void commit(@Option(names = {"-m", "--message"}) String commitMessage,
                    @Option(names = "--squash", paramLabel = "<commit>") String squash,
                    @Parameters(paramLabel = "<file>") File[] files) {
            // ... implement business logic
        }

        @Command(description = "Update remote refs along with associated objects")
        void push(@Option(names = {"-f", "--force"}) boolean force,
                  @Option(names = "--tags") boolean tags,
                  @Parameters(paramLabel = "<repository>") String repo) {
            // ... implement business logic
        }
    }
    @Test
    public void testGitsageHelpMessage() {
        CommandLine cmd = new CommandLine(new Git());
        String expected = String.format("" +
                "Usage: git [-hV] [--git-dir=<path>] [COMMAND]%n" +
                "Version control system.%n" +
                "      --git-dir=<path>   Set the path to the repository%n" +
                "  -h, --help             Show this help message and exit.%n" +
                "  -V, --version          Print version information and exit.%n" +
                "Commands:%n" +
                "  clone   Clone a repository into a new directory%n" +
                "  commit  Record changes to the repository%n" +
                "  push    Update remote refs along with associated objects%n");
        assertEquals(expected, cmd.getUsageMessage());
    }

    private static Set<String> set(String... elements) {
        return new HashSet<String>(Arrays.asList(elements));
    }
}