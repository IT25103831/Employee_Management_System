package lk.sliit.ems.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PasswordUtil {
    private static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordUtil() {}
    public static String hash(String password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return ITERATIONS + ":" + HexFormat.of().formatHex(salt) + ":" + HexFormat.of().formatHex(derive(password.toCharArray(), salt, ITERATIONS));
    }
    public static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split(":");
            byte[] salt = HexFormat.of().parseHex(p[1]);
            return MessageDigest.isEqual(HexFormat.of().parseHex(p[2]), derive(password.toCharArray(), salt, Integer.parseInt(p[0])));
        } catch (RuntimeException e) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password, salt, iterations, 256)).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Unable to hash password", e); }
    }
}
