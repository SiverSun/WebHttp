package org.example;

import com.sun.net.httpserver.Headers;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Request implements com.sun.net.httpserver.Request {
    private final RequestLine requestLine;
    private final List<String> headers;
    private String body;
    private List<NameValuePair> queryParams;

    public Request(RequestLine requestLine, List<String> headers) {
        this.requestLine = requestLine;
        this.headers = headers;
    }

    public Request(RequestLine requestLine, List<String> headers, String body) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
    }

    public RequestLine getRequestLine() {
        return requestLine;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getQueryParam(String name) {
        return queryParams.stream()
                .filter(o -> o.getName().startsWith(name))
                .map(NameValuePair::getValue)
                .collect(Collectors.toList());
    }

    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(List<NameValuePair> queryParams) {
        this.queryParams = queryParams;
    }

    @Override
    public String toString() {
        return "Request{" +
                "requestLine=" + requestLine +
                ", headers='" + headers + '\'' +
                ", body='" + body + '\'' +
                '}';
    }

    @Override
    public URI getRequestURI() {
        return null;
    }

    @Override
    public String getRequestMethod() {
        return null;
    }

    @Override
    public Headers getRequestHeaders() {
        return null;
    }
}
