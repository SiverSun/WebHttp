package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Server sever = new Server(9999);
        sever.start();
    }
}
