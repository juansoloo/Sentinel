package Proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketUtils {
    static byte[] copyExactCapture(InputStream in, OutputStream out, int byteCount) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream(byteCount);
        byte[] buffer = new byte[8192];
        int remaining = byteCount;

        while(remaining > 0) {
            int read = in.read(buffer, 0, Math.min(buffer.length, remaining));

            if (read == -1) {
                throw new IOException("client disconnected before full body request was read.");
            }

            out.write(buffer, 0, read);
            captured.write(buffer,0,read);

            remaining -= read;
        }

        return captured.toByteArray();
    }

    static void copyTunnel(InputStream input, OutputStream output) throws IOException{
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            output.flush();
        }
    }

    public static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
