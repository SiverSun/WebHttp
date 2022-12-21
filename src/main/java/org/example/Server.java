package org.example;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final ExecutorService threadPool = Executors.newFixedThreadPool(64);

    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();

    public void listen(int port) {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                final Socket socket = serverSocket.accept();
                threadPool.submit(new SocketHandler(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        Map<String, Handler> map = new ConcurrentHashMap<>();
        if (handlers.containsKey(method)) {
            map = handlers.get(method);
        }
        map.put(path, handler);
        handlers.put(method, map);
    }

    private class SocketHandler implements Runnable {

        private final Socket socket;

        private final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
        private final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};


        public SocketHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    final BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                    final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())
            ) {
                // лимит на request line + заголовки
                final int limit = 4096;

                in.mark(limit);
                final byte[] buffer = new byte[limit];
                final int read = in.read(buffer);

                RequestLine requestLine = getRequestLine(buffer, read);
                if (requestLine == null) {
                    badRequest(out);
                    return;
                }

                List<String> headers = getHeaders(buffer, read, in);
                if (headers == null) {
                    badRequest(out);
                    return;
                }

                Request request = new Request(requestLine, headers);
                request.setBody(getBody(request, in));
                request.setQueryParams(getQueryParams(requestLine.getPath()));

                runHandler(request, out);
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }

        private RequestLine getRequestLine(byte[] buffer, int read) {
            // ищем request line
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                return null;
            }

            // читаем request line
            final String[] parts = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (parts.length != 3) {
                return null;
            }

            // проверяем, валидный ли путь
            if (!parts[1].startsWith("/")) {
                return null;
            }

            // получили request line
            return new RequestLine(parts[0], parts[1], parts[2]);
        }

        private List<String> getHeaders(byte[] buffer, int read, BufferedInputStream in) throws IOException {
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);

            // ищем заголовки
            final int headersStart = requestLineEnd + requestLineDelimiter.length;
            final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                return null;
            }

            // отматываем на начало буфера
            in.reset();

            // пропускаем requestLine
            in.skip(headersStart);

            // получили заголовки
            final byte[] headersBytes = in.readNBytes(headersEnd - headersStart);
            return Arrays.asList(new String(headersBytes).split("\r\n"));
        }

        private String getBody(Request request, BufferedInputStream in) throws IOException {
            // для GET тело МОЖЕТ быть, но общепринято его игнорировать
            if (!request.getRequestLine().getMethod().equals("GET")) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final Optional<String> contentLength = extractHeader(request.getHeaders(), "Content-Length");
                if (contentLength.isPresent()) {
                    final int length = Integer.parseInt(contentLength.get());
                    final byte[] bodyBytes = in.readNBytes(length);
                    return new String(bodyBytes);
                }
            }
            return null;
        }

        private List<NameValuePair> getQueryParams(String path) throws URISyntaxException {
            final URI uri = new URI(path);
            return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        }

        private void runHandler(Request request, BufferedOutputStream out) throws IOException {
            Handler handler = handlers.get(request.getRequestLine().getMethod())
                    .get(request.getRequestLine().getPath());

            if (handler != null) {
                handler.handle(request, out);
            } else {
                notFound(out);
            }
        }

        private Optional<String> extractHeader(List<String> headers, String header) {
            return headers.stream()
                    .filter(o -> o.startsWith(header))
                    .map(o -> o.substring(o.indexOf(" ")))
                    .map(String::trim)
                    .findFirst();
        }

        private void badRequest(BufferedOutputStream out) throws IOException {
            out.write((
                    "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        }

        public void notFound(BufferedOutputStream out) throws IOException {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        }

        // from google guava with modifications
        private int indexOf(byte[] array, byte[] target, int start, int max) {
            outer:
            for (int i = start; i < max - target.length + 1; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i + j] != target[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

}




