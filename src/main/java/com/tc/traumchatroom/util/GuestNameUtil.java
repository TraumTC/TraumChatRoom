package com.tc.traumchatroom.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class GuestNameUtil {

    public String generateGuestName(HttpServletRequest request) {
            String userAgent = request.getHeader("User-Agent");
            String remoteAddr = request.getRemoteAddr();

            if (userAgent == null) {
                userAgent = "unknown";
            }
            String rawInput = userAgent + "|" + remoteAddr;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(rawInput.getBytes(StandardCharsets.UTF_8));

                String hexString = bytesToHex(hash);

                String shortHash = hexString.substring(0, 7);

                return "游客_" + shortHash;
            } catch (NoSuchAlgorithmException e) {
                long randomId = Math.abs(rawInput.hashCode()) % 10000000;
                return "游客_" + String.format("%07d", randomId);
            }
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }
}

