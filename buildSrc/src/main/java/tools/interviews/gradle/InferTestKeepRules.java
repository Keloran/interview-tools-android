package tools.interviews.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import javax.inject.Inject;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Preserve the shared entry points actually referenced by instrumentation code and its libraries. */
public abstract class InferTestKeepRules extends DefaultTask {
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<RegularFile> getApplicationJars();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<Directory> getApplicationDirectories();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<RegularFile> getTestJars();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<Directory> getTestDirectories();
    @Classpath public abstract ConfigurableFileCollection getTracerClasspath();
    @Classpath public abstract ConfigurableFileCollection getBootClasspath();
    @OutputFile public abstract RegularFileProperty getOutputFile();
    @Inject protected abstract ExecOperations getExecOperations();

    @TaskAction public void infer() throws IOException {
        File targets = new File(getTemporaryDir(), "application.jar");
        Set<String> shared = merge(getApplicationJars().get(), getApplicationDirectories().get(), targets, Set.of());
        File sources = new File(getTemporaryDir(), "instrumentation.jar");
        // Classes shared with the app belong on the target side, not in both archives.
        merge(getTestJars().get(), getTestDirectories().get(), sources, shared);
        File output = getOutputFile().get().getAsFile();
        output.getParentFile().mkdirs();
        List<String> args = new ArrayList<>(List.of("--keep-rules", "--source", sources.getPath(),
            "--target", targets.getPath(), "--output", output.getPath(),
            "--map-diagnostics:MissingDefinitionsDiagnostic", "error", "warning"));
        for (File library : getBootClasspath()) { args.add("--lib"); args.add(library.getPath()); }
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(getTracerClasspath());
            spec.getMainClass().set("com.android.tools.r8.tracereferences.TraceReferences");
            spec.jvmArgs("-Xmx1g");
            spec.args(args);
        }).assertNormalExitValue();
    }

    private static Set<String> merge(List<RegularFile> jars, List<Directory> dirs,
                                     File output, Set<String> excluded) throws IOException {
        Set<String> written = new HashSet<>();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(output))) {
            for (RegularFile jar : jars) {
                try (JarFile input = new JarFile(jar.getAsFile())) {
                    var entries = input.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (accept(entry.getName(), written, excluded)) {
                            out.putNextEntry(new JarEntry(entry.getName()));
                            try (InputStream bytes = input.getInputStream(entry)) { bytes.transferTo(out); }
                            out.closeEntry();
                        }
                    }
                }
            }
            for (Directory directory : dirs) {
                Path root = directory.getAsFile().toPath();
                if (!Files.exists(root)) continue;
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                        String name = root.relativize(path).toString().replace(File.separatorChar, '/');
                        if (accept(name, written, excluded)) {
                            out.putNextEntry(new JarEntry(name));
                            Files.copy(path, out);
                            out.closeEntry();
                        }
                    }
                }
            }
        }
        return written;
    }
    private static boolean accept(String name, Set<String> written, Set<String> excluded) {
        return name.endsWith(".class") && !excluded.contains(name) && written.add(name);
    }
}
