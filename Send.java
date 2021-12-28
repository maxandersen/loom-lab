/** enable preview to get access to loom/StructuredExecutor **/
//JAVA_OPTIONS --enable-preview
//JAVAC_OPTIONS --enable-preview --source 19
//JAVA 19

import java.io.BufferedReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import java.util.concurrent.StructuredExecutor;

/**
 * Sends a bunch of messages localhost:8080.
 */
public class Send {

	/**
	 * @param args
	 * 	1: threading strategy: "pooled" or "virtual" (required)
	 * 	2: number of messages (optional - defaults to {@link Configuration#DEFAULT_MESSAGE_COUNT DEFAULT_MESSAGE_COUNT}
	 * 	3: number of threads for the pooled sender (optional - defaults to {@link Configuration#DEFAULT_THREAD_COUNT DEFAULT_THREAD_COUNT}
	 */
	public static void main(String[] args) throws InterruptedException {
		var config = Configuration.parse(args);
		Sender sender = switch(config.threading()) {
			case POOLED -> new PooledSender(
					Send::sendMessageAndWaitForReply,
					config.messageCount(),
					config.threadCount());
			case VIRTUAL -> new VirtualThreadSender(
					Send::sendMessageAndWaitForReply,
					config.messageCount());
		};

		System.out.printf("Sender up - start sending %d messages...%n", config.messageCount());
		var startTime = System.currentTimeMillis();
		sender.sendMessages("fOo bAR");
		var elapsedTime = System.currentTimeMillis() - startTime;
		System.out.printf("Done in %dms%n(NOTE: This measurement is very unreliable for various reasons! E.g. `println` itself.)%n", elapsedTime);
	}

	private static void sendMessageAndWaitForReply(String message) {
		System.out.printf("Sending: '%s'%n", message);
		try (var socket = new Socket("localhost", 8080);
			 var receiver = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			 var sender = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()))
		) {
			sender.println(message);
			sender.flush();
			var reply = receiver.readLine();
			System.out.printf("Received: '%s'.%n", reply);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private record Configuration(Threading threading, int messageCount, int threadCount) {

		static final int DEFAULT_MESSAGE_COUNT = 100;
		static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

		static Configuration parse(String[] args) {
			if (args.length == 0)
				throw new IllegalArgumentException("Please specify the implementation.");
			var implementation = Threading.valueOf(args[0].toUpperCase());
			var messageCount = args.length == 2 ? Integer.parseInt(args[1]) : DEFAULT_MESSAGE_COUNT;
			var threadCount = args.length ==3 ? Integer.parseInt(args[2]) : DEFAULT_THREAD_COUNT;
			return new Configuration(implementation, messageCount, threadCount);
		}

	}

	private enum Threading {
		POOLED,
		VIRTUAL
	}

    /**
     * Uses a simple thread pool to send messages
     * to a destination defined by the specified {@code sender} (see constructor).
     */
    static class PooledSender implements Sender {

        private final Consumer<String> sender;
        private final ExecutorService pool;
        private final int messageCount;

        public PooledSender(Consumer<String> sender, int messageCount, int threadCount) {
            this.sender = requireNonNull(sender);
            this.messageCount = messageCount;
            this.pool = Executors.newFixedThreadPool(threadCount);
        }

        @Override
        public void sendMessages(String messageRoot) throws UncheckedIOException, InterruptedException {
            try (pool) {
                IntStream.range(0, messageCount)
                        .forEach(counter -> {
                            String message = messageRoot + " " + counter;
                            Runnable send = () -> sender.accept(message);
                            CompletableFuture.runAsync(send, pool);
                        });
            }
        }

    }

	/**
	 * Sends a bunch of messages - where and how exactly depends on the implementation.
	 */
	static interface Sender {

		void sendMessages(String messageRoot) throws UncheckedIOException, InterruptedException;

	}

	/**
	 * Uses Loom's {@link StructuredExecutor} to spawn virtual threads that send messages
	 * to a destination defined by the specified {@code sender} (see constructor).
	 */
	static class VirtualThreadSender implements Sender {

		private final Consumer<String> sender;
		private final int messageCount;

		public VirtualThreadSender(Consumer<String> sender, int messageCount) {
			this.sender = requireNonNull(sender);
			this.messageCount = messageCount;
		}

		@Override
		public void sendMessages(String messageRoot) throws UncheckedIOException, InterruptedException {
			try (var executor = StructuredExecutor.open()) {
				var handler = new StructuredExecutor.ShutdownOnFailure();
				IntStream.range(0, messageCount)
						.forEach(counter -> {
							String message = messageRoot + " " + counter;
							Runnable send = () -> sender.accept(message);
							executor.execute(send);
						});

				executor.join();
				handler.throwIfFailed();
			} catch (ExecutionException ex) {
				if (ex.getCause() instanceof RuntimeException runtimeException)
					throw runtimeException;
				else
					throw new RuntimeException(ex.getCause());
			}
		}

	}
}
