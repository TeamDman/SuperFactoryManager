import java.net.HttpURLConnection;
import java.net.URI;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Diagnostic only: inspect default JVM trust and perform a verified HTTPS request. */
class JavaTrustProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("denied SHA256 CSV, expected CA SHA256, HTTPS URL required");
        Set<String> denied = Arrays.stream(args[0].split(",")).map(String::toUpperCase).collect(Collectors.toSet());
        String expected = args[1].toUpperCase();
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init((KeyStore) null);
        int count = 0;
        boolean found = false;
        for (var manager : managers.getTrustManagers()) {
            if (!(manager instanceof X509TrustManager trust)) continue;
            for (X509Certificate cert : trust.getAcceptedIssuers()) {
                String sha = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
                if (denied.contains(sha)) throw new IllegalStateException("denied root present: " + sha);
                if (sha.equals(expected)) found = true;
                count++;
            }
        }
        if (!found || count == 0) throw new IllegalStateException("expected CA absent or trust store empty");
        URI uri = URI.create(args[2]);
        if (!"https".equals(uri.getScheme())) throw new IllegalArgumentException("HTTPS URL required");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        int status = connection.getResponseCode();
        connection.disconnect();
        if (status != 200) throw new IllegalStateException("HTTPS returned " + status);
        System.out.println("anchors=" + count + " denied=0 expected_ca=present https=200");
    }
}
