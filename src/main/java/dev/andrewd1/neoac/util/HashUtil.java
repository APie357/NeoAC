package dev.andrewd1.neoac.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class HashUtil {
    public static byte[] hash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (file.isDirectory()) {
                for (File child : Objects.requireNonNull(file.listFiles())) {
                    digest.update(hash(child));
                }
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] byteArray = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(byteArray)) != -1) {
                        digest.update(byteArray, 0, bytesRead);
                    }
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String bytesToHex(byte[] hash) {
        StringBuilder sb = new StringBuilder();
        if (hash != null) {
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
        }
        return sb.toString();
    }
}
