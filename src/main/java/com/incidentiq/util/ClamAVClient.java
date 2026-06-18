package com.incidentiq.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class ClamAVClient {

    @Value("${clamav.host:localhost}")
    private String host;

    @Value("${clamav.port:3310}")
    private int port;

    public String ping() throws Exception {
        try (Socket s = new Socket(host, port);
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {
            
            out.write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
        }
    }

    public String scan(byte[] data) throws Exception {
        try (Socket s = new Socket(host, port);
             OutputStream out = new BufferedOutputStream(s.getOutputStream());
             InputStream in = s.getInputStream()) {
            
            s.setSoTimeout(10000); // 10s timeout
            
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            
            int offset = 0;
            int chunkSize = 2048;
            while (offset < data.length) {
                int len = Math.min(chunkSize, data.length - offset);
                byte[] chunkHeader = ByteBuffer.allocate(4).putInt(len).array();
                out.write(chunkHeader);
                out.write(data, offset, len);
                offset += len;
            }
            
            out.write(new byte[]{0, 0, 0, 0}); // Zero length chunk to terminate
            out.flush();

            String response = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
            return response;
        }
    }
}
