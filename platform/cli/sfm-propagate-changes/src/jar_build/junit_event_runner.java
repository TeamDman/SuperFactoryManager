package dev.teamdman.sfm.toolchain;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

public final class SfmJUnitRunner {
    private static final String PREFIX = "SFM_JUNIT";
    private static final InheritableThreadLocal<TestInfo> CURRENT_TEST = new InheritableThreadLocal<TestInfo>();

    private SfmJUnitRunner() {
    }

    public static void main(String[] args) {
        ProtocolEmitter emitter = new ProtocolEmitter(System.out);
        try {
            Options options = Options.parse(args);
            CapturingOutputStream stdoutCapture = new CapturingOutputStream(emitter, "stdout");
            CapturingOutputStream stderrCapture = new CapturingOutputStream(emitter, "stderr");
            System.setOut(new PrintStream(stdoutCapture, false, "UTF-8"));
            System.setErr(new PrintStream(stderrCapture, false, "UTF-8"));

            Launcher launcher = LauncherFactory.create();
            LauncherDiscoveryRequest request = buildRequest(options);
            if ("list".equals(options.mode)) {
                TestPlan plan = launcher.discover(request);
                int count = emitTests(emitter, options, plan);
                emitter.emit("list_summary", Integer.toString(count));
                System.exit(0);
            }

            SummaryGeneratingListener summary = new SummaryGeneratingListener();
            StructuredListener listener = new StructuredListener(emitter, options, stdoutCapture, stderrCapture);
            launcher.execute(request, listener, summary);
            stdoutCapture.flushAll();
            stderrCapture.flushAll();
            TestExecutionSummary result = summary.getSummary();
            emitter.emit(
                    "summary",
                    Long.toString(result.getTestsFoundCount()),
                    Long.toString(result.getTestsStartedCount()),
                    Long.toString(result.getTestsSucceededCount()),
                    Long.toString(result.getTestsFailedCount()),
                    Long.toString(result.getTestsSkippedCount()),
                    Long.toString(result.getTestsAbortedCount()),
                    Long.toString(result.getContainersFoundCount()),
                    Long.toString(result.getContainersFailedCount())
            );
            if (result.getTestsFoundCount() == 0) {
                System.exit(4);
            }
            System.exit(result.getTestsFailedCount() == 0 ? 0 : 1);
        } catch (Throwable error) {
            emitter.emit("runner_error", error.toString(), stackTrace(error));
            System.exit(2);
        }
    }

    private static LauncherDiscoveryRequest buildRequest(final Options options) {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClasspathRoots(Collections.singleton(options.classpathRoot)));
        if (options.filter != null && options.filter.trim().length() > 0) {
            final String needle = options.filter.toLowerCase();
            builder.filters(new PostDiscoveryFilter() {
                @Override
                public FilterResult apply(TestDescriptor descriptor) {
                    TestSource source = descriptor.getSource().orElse(null);
                    if (!descriptor.isTest() && !(source instanceof MethodSource)) {
                        return FilterResult.included("container");
                    }
                    String legacy = legacyName(source, descriptor.getDisplayName());
                    String haystack = (descriptor.getDisplayName()
                            + " "
                            + descriptor.getUniqueId()
                            + " "
                            + legacy).toLowerCase();
                    if (haystack.contains(needle)) {
                        return FilterResult.included("matched " + options.filter);
                    }
                    return FilterResult.excluded("did not match " + options.filter);
                }
            });
        }
        return builder.build();
    }

    private static int emitTests(ProtocolEmitter emitter, Options options, TestPlan plan) {
        int[] count = new int[]{0};
        for (TestIdentifier root : plan.getRoots()) {
            emitTestChildren(emitter, options, plan, root, count);
        }
        return count[0];
    }

    private static void emitTestChildren(
            ProtocolEmitter emitter,
            Options options,
            TestPlan plan,
            TestIdentifier parent,
            int[] count
    ) {
        for (TestIdentifier child : plan.getChildren(parent)) {
            if (child.isTest()) {
                TestInfo info = TestInfo.from(child, options);
                emitter.emit("test", info.uniqueId, info.displayName, info.legacyName, info.sourcePath);
                count[0]++;
            }
            emitTestChildren(emitter, options, plan, child, count);
        }
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String sourcePath(TestSource source, Path sourceRoot) {
        if (source instanceof MethodSource) {
            return classToSourcePath(((MethodSource) source).getClassName(), sourceRoot);
        }
        if (source instanceof ClassSource) {
            return classToSourcePath(((ClassSource) source).getClassName(), sourceRoot);
        }
        return "";
    }

    private static String classToSourcePath(String className, Path sourceRoot) {
        int nestedClassIndex = className.indexOf('$');
        if (nestedClassIndex >= 0) {
            className = className.substring(0, nestedClassIndex);
        }
        return sourceRoot
                .resolve(className.replace('.', File.separatorChar) + ".java")
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private static String legacyName(TestSource source, String fallback) {
        if (source instanceof MethodSource) {
            MethodSource method = (MethodSource) source;
            return simpleClassName(method.getClassName()) + "::" + method.getMethodName();
        }
        if (source instanceof ClassSource) {
            return simpleClassName(((ClassSource) source).getClassName());
        }
        return fallback;
    }

    private static String simpleClassName(String className) {
        int packageIndex = className.lastIndexOf('.');
        String simple = packageIndex >= 0 ? className.substring(packageIndex + 1) : className;
        int nestedClassIndex = simple.indexOf('$');
        return nestedClassIndex >= 0 ? simple.substring(0, nestedClassIndex) : simple;
    }

    private static final class Options {
        final String mode;
        final Path classpathRoot;
        final Path sourceRoot;
        final String filter;

        private Options(String mode, Path classpathRoot, Path sourceRoot, String filter) {
            this.mode = mode;
            this.classpathRoot = classpathRoot;
            this.sourceRoot = sourceRoot;
            this.filter = filter;
        }

        static Options parse(String[] args) {
            String mode = "run";
            Path classpathRoot = null;
            Path sourceRoot = null;
            String filter = null;
            ArrayDeque<String> queue = new ArrayDeque<String>();
            Collections.addAll(queue, args);
            while (!queue.isEmpty()) {
                String arg = queue.removeFirst();
                if ("--mode".equals(arg)) {
                    mode = requiredValue(queue, arg);
                } else if ("--classpath-root".equals(arg)) {
                    classpathRoot = Paths.get(requiredValue(queue, arg));
                } else if ("--source-root".equals(arg)) {
                    sourceRoot = Paths.get(requiredValue(queue, arg));
                } else if ("--filter".equals(arg)) {
                    filter = requiredValue(queue, arg);
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (classpathRoot == null) {
                throw new IllegalArgumentException("--classpath-root is required");
            }
            if (sourceRoot == null) {
                throw new IllegalArgumentException("--source-root is required");
            }
            if (!"run".equals(mode) && !"list".equals(mode)) {
                throw new IllegalArgumentException("Unsupported --mode: " + mode);
            }
            return new Options(mode, classpathRoot, sourceRoot, filter);
        }

        private static String requiredValue(ArrayDeque<String> queue, String arg) {
            if (queue.isEmpty()) {
                throw new IllegalArgumentException(arg + " requires a value");
            }
            return queue.removeFirst();
        }
    }

    private static final class TestInfo {
        final String uniqueId;
        final String displayName;
        final String legacyName;
        final String sourcePath;

        private TestInfo(String uniqueId, String displayName, String legacyName, String sourcePath) {
            this.uniqueId = uniqueId;
            this.displayName = displayName;
            this.legacyName = legacyName;
            this.sourcePath = sourcePath;
        }

        static TestInfo from(TestIdentifier identifier, Options options) {
            TestSource source = identifier.getSource().orElse(null);
            return new TestInfo(
                    identifier.getUniqueId(),
                    identifier.getDisplayName(),
                    legacyName(source, identifier.getDisplayName()),
                    sourcePath(source, options.sourceRoot)
            );
        }
    }

    private static final class StructuredListener implements TestExecutionListener {
        private final ProtocolEmitter emitter;
        private final Options options;
        private final CapturingOutputStream stdoutCapture;
        private final CapturingOutputStream stderrCapture;

        private StructuredListener(
                ProtocolEmitter emitter,
                Options options,
                CapturingOutputStream stdoutCapture,
                CapturingOutputStream stderrCapture
        ) {
            this.emitter = emitter;
            this.options = options;
            this.stdoutCapture = stdoutCapture;
            this.stderrCapture = stderrCapture;
        }

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            if (!testIdentifier.isTest()) {
                return;
            }
            TestInfo info = TestInfo.from(testIdentifier, options);
            CURRENT_TEST.set(info);
            emitter.emit("started", info.uniqueId, info.displayName, info.legacyName, info.sourcePath);
        }

        @Override
        public void executionSkipped(TestIdentifier testIdentifier, String reason) {
            if (!testIdentifier.isTest()) {
                return;
            }
            TestInfo info = TestInfo.from(testIdentifier, options);
            emitter.emit("skipped", info.uniqueId, info.displayName, info.legacyName, info.sourcePath, reason);
        }

        @Override
        public void executionFinished(
                TestIdentifier testIdentifier,
                TestExecutionResult testExecutionResult
        ) {
            if (!testIdentifier.isTest()) {
                return;
            }
            TestInfo info = TestInfo.from(testIdentifier, options);
            CURRENT_TEST.set(info);
            stdoutCapture.flushAll();
            stderrCapture.flushAll();
            Optional<Throwable> throwable = testExecutionResult.getThrowable();
            String throwableType = "";
            String throwableMessage = "";
            String throwableStackTrace = "";
            if (throwable.isPresent()) {
                Throwable error = throwable.get();
                throwableType = error.getClass().getName();
                throwableMessage = error.getMessage() == null ? "" : error.getMessage();
                throwableStackTrace = stackTrace(error);
            }
            emitter.emit(
                    "finished",
                    info.uniqueId,
                    info.displayName,
                    info.legacyName,
                    info.sourcePath,
                    testExecutionResult.getStatus().name(),
                    throwableType,
                    throwableMessage,
                    throwableStackTrace
            );
            CURRENT_TEST.remove();
        }
    }

    private static final class CapturingOutputStream extends OutputStream {
        private final ProtocolEmitter emitter;
        private final String stream;
        private final Map<Long, ByteArrayOutputStream> buffers = new HashMap<Long, ByteArrayOutputStream>();

        private CapturingOutputStream(ProtocolEmitter emitter, String stream) {
            this.emitter = emitter;
            this.stream = stream;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            if (value == '\n') {
                flushThread(Thread.currentThread().getId());
                return;
            }
            if (value == '\r') {
                return;
            }
            bufferFor(Thread.currentThread().getId()).write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(bytes[i]);
            }
        }

        @Override
        public synchronized void flush() {
        }

        synchronized void flushAll() {
            List<Long> threadIds = new ArrayList<Long>(buffers.keySet());
            for (Long threadId : threadIds) {
                flushThread(threadId.longValue());
            }
        }

        private ByteArrayOutputStream bufferFor(long threadId) {
            ByteArrayOutputStream buffer = buffers.get(threadId);
            if (buffer == null) {
                buffer = new ByteArrayOutputStream();
                buffers.put(threadId, buffer);
            }
            return buffer;
        }

        private void flushThread(long threadId) {
            ByteArrayOutputStream buffer = buffers.get(threadId);
            if (buffer == null || buffer.size() == 0) {
                return;
            }
            String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            buffer.reset();
            TestInfo info = CURRENT_TEST.get();
            if (info == null) {
                info = new TestInfo("", "", "", "");
            }
            emitter.emit(
                    "output",
                    stream,
                    info.uniqueId,
                    info.displayName,
                    info.legacyName,
                    info.sourcePath,
                    line
            );
        }
    }

    private static final class ProtocolEmitter {
        private final PrintStream output;

        private ProtocolEmitter(PrintStream output) {
            this.output = output;
        }

        synchronized void emit(String event, String... fields) {
            output.print(PREFIX);
            output.print('\t');
            output.print(event);
            for (String field : fields) {
                output.print('\t');
                output.print(encode(field == null ? "" : field));
            }
            output.println();
            output.flush();
        }

        private static String encode(String value) {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
