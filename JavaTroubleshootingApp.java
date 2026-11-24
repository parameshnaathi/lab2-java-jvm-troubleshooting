import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class JavaTroubleshootingApp {

    private static final List<byte[]> MEMORY = new ArrayList<>();

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    private static volatile double cpuSink = 0;

    public static void main(String[] args) throws Exception {

        HttpServer server =
                HttpServer.create(new InetSocketAddress("127.0.0.1", 8081), 0);

        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/health", exchange -> {
            send(exchange, 200, "UP");
        });

        server.createContext("/info", exchange -> {

            long pid = ProcessHandle.current().pid();

            String response =
                    "Java Troubleshooting Lab\n" +
                    "PID: " + pid + "\n" +
                    "Threads: " + Thread.activeCount() + "\n" +
                    "Status: Running\n";

            send(exchange, 200, response);
        });

        server.createContext("/slow", exchange -> {

            System.out.println("Slow request started");

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            send(exchange, 200, "Slow request completed");
        });

        server.createContext("/cpu", exchange -> {

            System.out.println("CPU test started");

            long end = System.currentTimeMillis() + 15000;

            while (System.currentTimeMillis() < end) {
                cpuSink += Math.sqrt(System.nanoTime());
            }

            send(exchange, 200, "CPU test completed");
        });

        server.createContext("/memory", exchange -> {

            try {

                byte[] block = new byte[10 * 1024 * 1024];

                MEMORY.add(block);

                send(
                        exchange,
                        200,
                        "Allocated 10 MB. Blocks retained: " + MEMORY.size()
                );

            } catch (OutOfMemoryError error) {

                error.printStackTrace();

                send(exchange, 500, "OutOfMemoryError");
            }
        });

        server.createContext("/memory-clear", exchange -> {

            MEMORY.clear();

            System.gc();

            send(exchange, 200, "Memory references cleared");
        });

        server.createContext("/oom", exchange -> {

            try {

                byte[] huge = new byte[256 * 1024 * 1024];

                send(exchange, 200,
                        "Unexpected allocation success: " + huge.length);

            } catch (OutOfMemoryError error) {

                error.printStackTrace();

                send(exchange, 500,
                        "Intentional OutOfMemoryError generated");
            }
        });

        server.createContext("/deadlock", exchange -> {

            Thread t1 = new Thread(() -> {

                synchronized (LOCK_A) {

                    sleep(500);

                    synchronized (LOCK_B) {
                        System.out.println("Thread 1 acquired both locks");
                    }
                }

            }, "deadlock-thread-1");

            Thread t2 = new Thread(() -> {

                synchronized (LOCK_B) {

                    sleep(500);

                    synchronized (LOCK_A) {
                        System.out.println("Thread 2 acquired both locks");
                    }
                }

            }, "deadlock-thread-2");

            t1.setDaemon(true);
            t2.setDaemon(true);

            t1.start();
            t2.start();

            send(exchange, 200, "Deadlock scenario started");
        });

        server.start();

        System.out.println(
                "Java Troubleshooting App listening on 127.0.0.1:8081"
        );
    }

    private static void sleep(long milliseconds) {

        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String response
    ) throws IOException {

        byte[] body =
                response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(status, body.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}