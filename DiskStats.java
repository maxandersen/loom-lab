/** enable preview to get access to loom/StructuredExecutor **/
//JAVA_OPTIONS --enable-preview
//JAVAC_OPTIONS --enable-preview --source 19
//JAVA 19

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.StructuredExecutor;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
public class DiskStats {

	/**
	 * @param args:
	 * 		0: threading strategy: "single" or "virtual" (required)
	 * 	    1: path to analyze (required)
	 */



public static void main(String[] args) throws InterruptedException {
		var config = Configuration.parse(args);
		Analyzer analyzer = switch (config.threading()) {
			case SINGLE -> new SingleThreadAnalyzer();
			case VIRTUAL -> new VirtualThreadsAnalyzer();
		};

		System.out.printf("Analyzing '%s'...%n", config.root());
		long startTime = System.currentTimeMillis();
		var stats = analyzer.analyzeFolder(config.root());
		long elapsedTime = System.currentTimeMillis() - startTime;

		System.out.println(stats);
		System.out.printf("Done in %dms%n(NOTE: This measurement is very unreliable for various reasons, e.g. disk caching.)%n", elapsedTime);
		System.out.println(analyzer.analyzerStats());
	}

	private record Configuration(Threading threading, Path root) {

		static Configuration parse(String[] args) {
			if (args.length < 2)
				throw new IllegalArgumentException("Please specify the implementation and path.");
			var implementation = Threading.valueOf(args[0].toUpperCase());
			var root = Path.of(args[1]);
			return new Configuration(implementation, root);
		}

	}

	private enum Threading {
		SINGLE,
		VIRTUAL
	}

	static record FileStats(Path path, long size) implements Stats {

		public FileStats {
			requireNonNull(path);
		}

	}

	static record FolderStats(Path path, long size, List<Stats> children) implements Stats {

		public FolderStats {
			requireNonNull(path);
			children = List.copyOf(children);
		}

		@Override
		public String toString() {
			return "FolderStats: path='%s', size=%d}".formatted(path, size);
		}

	}

	static interface Analyzer {

		FolderStats analyzeFolder(Path folder) throws UncheckedIOException, InterruptedException;

		default String analyzerStats() {
			return "";
		}

	}

	/**
	 * Uses a single thread to gather statistics (hence: sequential).
	 */
	static class SingleThreadAnalyzer implements Analyzer {

		@Override
		public FolderStats analyzeFolder(Path folder) throws UncheckedIOException {
			try (var content = Files.list(folder)) {
				var children = content
						.filter(not(Files::isSymbolicLink))
						.<Stats>map(path -> Files.isDirectory(path)
								? analyzeFolder(path)
								: analyzeFile(path))
						.toList();
				long totalSize = children.stream().mapToLong(Stats::size).sum();
				return new FolderStats(folder, totalSize, children);
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		private static FileStats analyzeFile(Path file) throws UncheckedIOException {
			try {
				return new FileStats(file, Files.size(file));
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

	public sealed static interface Stats permits FileStats, FolderStats {

		Path path();

		long size();

		default List<Stats> children() {
			return List.of();
		}

	}

	/**
	 * Uses a new virtual thread for each file and folder to analyze (i.e. easily hundreds of thousands or millions).
	 */
	static class VirtualThreadsAnalyzer implements Analyzer {

		private static final LongAdder VIRTUAL_THREAD_COUNT = new LongAdder();

		@Override
		public FolderStats analyzeFolder(Path folder) throws UncheckedIOException, InterruptedException {
			try (var executor = StructuredExecutor.open();
					var content = Files.list(folder)) {

				var handler = new StructuredExecutor.ShutdownOnFailure();
				List<Future<Stats>> childrenTasks = content
						.filter(not(Files::isSymbolicLink))
						.<Callable<Stats>>map(path -> Files.isDirectory(path)
								? () -> analyzeFolder(path)
								: () -> analyzeFile(path))
						.map(action -> executor.fork(action, handler))
						.toList();

				VIRTUAL_THREAD_COUNT.add(childrenTasks.size());
				executor.join();
				handler.throwIfFailed();

				var children = childrenTasks.stream()
						.map(Future::resultNow)
						.toList();
				long totalSize = children.stream().mapToLong(Stats::size).sum();
				return new FolderStats(folder, totalSize, children);
			} catch (ExecutionException ex) {
				if (ex.getCause() instanceof RuntimeException runtimeException)
					throw runtimeException;
				else
					throw new RuntimeException(ex);
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		private static FileStats analyzeFile(Path file) throws UncheckedIOException {
			try {
				return new FileStats(file, Files.size(file));
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		@Override
		public String analyzerStats() {
			return "Number of created virtual threads: " + VIRTUAL_THREAD_COUNT.sum();
		}

	}
}
