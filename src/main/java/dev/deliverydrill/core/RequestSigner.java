package dev.deliverydrill.core;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RequestSigner {
    private RequestSigner() { }

    public static String sign(byte[] body, String secret, String algorithm, String encoding) {
        if (secret == null) throw new IllegalArgumentException("Signing is configured but no secret was supplied");
        String macAlgorithm = switch (algorithm == null ? "sha256" : algorithm.toLowerCase()) {
            case "sha1", "hmac-sha1" -> "HmacSHA1";
            case "sha256", "hmac-sha256" -> "HmacSHA256";
            default -> throw new IllegalArgumentException("Unsupported HMAC algorithm: " + algorithm);
        };
        try {
            Mac mac = Mac.getInstance(macAlgorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
            byte[] result = mac.doFinal(body);
            return "base64".equalsIgnoreCase(encoding) ? Base64.getEncoder().encodeToString(result) : hex(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to calculate HMAC", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}

