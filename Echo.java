/** enable preview to get access to loom/StructuredExecutor **/
//JAVA_OPTIONS --enable-preview
//JAVAC_OPTIONS --enable-preview --source 19
//JAVA 19

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import java.util.concurrent.StructuredExecutor;

public class Echo {

	private final int echoWaitMs;

	public Echo(int echoWaitMs) {
		this.echoWaitMs = echoWaitMs;
	}

	/**
	 * @param args
	 * 	1: threading strategy: "pooled" or "virtual" (required)
	 * 	2: time to wait before echoing in ms (optional - defaults to {@link Configuration#DEFAULT_ECHO_WAIT_MS DEFAULT_MESSAGE_COUNT}
	 * 	3: number of threads for the pooled sender (optional - defaults to {@link Configuration#DEFAULT_THREAD_COUNT DEFAULT_THREAD_COUNT}
	 */
	public static void main(String[] args) throws IOException {
		var config = Configuration.parse(args);
		var echo = new Echo(config.echoWaitMs());
		Server server = switch(config.threading()) {
			case POOLED -> new PooledServer(echo::echo, config.threadCount());
			case VIRTUAL -> new VirtualThreadServer(echo::echo);
		};

		System.out.println("Server up - start listening on 8080...");
		server.listen();
	}

	private void echo(Socket socket) {
		try (socket;
				var receiver = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				var sender = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()))
		) {
			var message = receiver.readLine();
			System.out.printf("Echoed '%s'.%n", message);
			Thread.sleep(echoWaitMs);
			sender.println(message);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		} catch (InterruptedException ex) {
			// if the thread was interrupted during `sleep`,
			// the socket and streams will be closed without replying
			Thread.currentThread().interrupt();
		}
	}

	private record Configuration(Threading threading, int echoWaitMs, int threadCount) {

		static final int DEFAULT_ECHO_WAIT_MS = 100;
		static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

		static Configuration parse(String[] args) {
			if (args.length == 0)
				throw new IllegalArgumentException("Please specify the implementation.");
			var implementation = Threading.valueOf(args[0].toUpperCase());
			var echoWaitMs = args.length == 2 ? Integer.parseInt(args[1]) : DEFAULT_ECHO_WAIT_MS;
			var threadCount = args.length ==3 ? Integer.parseInt(args[2]) : DEFAULT_THREAD_COUNT;
			return new Configuration(implementation, echoWaitMs, threadCount);
		}

	}

	private enum Threading {
		POOLED,
		VIRTUAL
	}

    static class PooledServer implements Server {

        private final Consumer<Socket> echo;
        private final ExecutorService pool;

        PooledServer(Consumer<Socket> echo, int threadCount) {
            this.echo = requireNonNull(echo);
            this.pool = Executors.newFixedThreadPool(threadCount);
        }

        @Override
        public void listen() throws IOException {
            ServerSocket server = new ServerSocket(8080);
            try (pool) {
                while (true) {
                    Socket socket = server.accept();
                    pool.submit(() -> echo.accept(socket));
                }
            }
        }

    }

	static interface Server {

		void listen() throws IOException;

	}

	static class VirtualThreadServer implements Server {

		private final Consumer<Socket> echo;

		VirtualThreadServer(Consumer<Socket> echo) {
			this.echo = requireNonNull(echo);
		}

		@Override
		public void listen() throws IOException {
			ServerSocket server = new ServerSocket(8080);
			try (var executor = StructuredExecutor.open()) {
				while (true) {
					Socket socket = server.accept();
					executor.execute(() -> echo.accept(socket));
				}
			}
		}

	}
}
