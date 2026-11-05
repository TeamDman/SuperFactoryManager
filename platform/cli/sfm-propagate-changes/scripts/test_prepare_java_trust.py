import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("prepare-java-trust.py")
SPEC = importlib.util.spec_from_file_location("prepare_java_trust", SCRIPT)
TRUST = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TRUST)


@unittest.skipUnless(shutil.which("keytool"), "keytool is required")
class PrepareJavaTrustTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temporary.name)
        cls.keytool = Path(shutil.which("keytool"))
        cls.source = cls.root / "source"
        cls.certs = []
        for alias in ("retained", "denied", "proxy"):
            identity = cls.root / (alias + ".p12")
            cert = cls.root / (alias + ".pem")
            cls.run_keytool("-genkeypair", "-alias", alias, "-dname", "CN=" + alias,
                            "-keyalg", "EC", "-validity", "1", "-keystore", str(identity),
                            "-storepass", "changeit", "-keypass", "changeit")
            cls.run_keytool("-exportcert", "-rfc", "-alias", alias, "-keystore", str(identity),
                            "-storepass", "changeit", "-file", str(cert))
            cls.certs.append(cert)
            if alias != "proxy":
                cls.run_keytool("-importcert", "-noprompt", "-alias", alias, "-file", str(cert),
                                "-keystore", str(cls.source), "-storepass", "changeit")
        cls.denied = TRUST.certificate_fingerprint(cls.certs[1].read_bytes())
        cls.proxy = TRUST.certificate_fingerprint(cls.certs[2].read_bytes())

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    @classmethod
    def run_keytool(cls, *args):
        subprocess.run([str(cls.keytool), *args], check=True, capture_output=True)

    def setUp(self):
        self.output = self.root / self.id().split(".")[-1]

    def test_preserves_source_and_other_anchors_and_adds_verified_ca(self):
        source_hash = TRUST.digest(self.source)
        receipt = TRUST.prepare(self.source, self.keytool, self.output, {self.denied},
                                self.certs[2], self.proxy)
        actual = TRUST.entries(self.output / "cacerts", self.keytool)
        self.assertEqual(set(actual), {"retained", TRUST.ENVIRONMENT_ALIAS})
        self.assertEqual(actual[TRUST.ENVIRONMENT_ALIAS], self.proxy)
        self.assertEqual(receipt["removed_aliases"], {"denied": self.denied})
        self.assertEqual(TRUST.digest(self.source), source_hash)
        self.assertEqual(receipt, json.loads((self.output / "receipt.json").read_text()))

    def test_rejects_wrong_or_denied_extra_ca_before_creating_output(self):
        with self.assertRaisesRegex(ValueError, "fingerprint mismatch"):
            TRUST.prepare(self.source, self.keytool, self.output, {self.denied},
                          self.certs[2], "00" * 32)
        self.assertFalse(self.output.exists())
        with self.assertRaisesRegex(ValueError, "denied list"):
            TRUST.prepare(self.source, self.keytool, self.output, {self.proxy},
                          self.certs[2], self.proxy)
        self.assertFalse(self.output.exists())

    def test_refuses_existing_output_without_modifying_it(self):
        self.output.mkdir()
        marker = self.output / "marker"
        marker.write_text("preserve")
        with self.assertRaises(FileExistsError):
            TRUST.prepare(self.source, self.keytool, self.output, {self.denied}, None, None)
        self.assertEqual(marker.read_text(), "preserve")

    def test_refuses_to_filter_every_source_anchor(self):
        denied = set(TRUST.entries(self.source, self.keytool).values())
        with self.assertRaisesRegex(ValueError, "every source trust anchor"):
            TRUST.prepare(self.source, self.keytool, self.output, denied, None, None)
        self.assertFalse(self.output.exists())

    def test_failed_filter_never_publishes_a_final_store(self):
        original = TRUST.keytool_command

        def fail_deletion(keytool, *args):
            if "-delete" in args:
                raise ValueError("simulated keytool failure")
            return original(keytool, *args)

        with patch.object(TRUST, "keytool_command", side_effect=fail_deletion):
            with self.assertRaisesRegex(ValueError, "simulated keytool failure"):
                TRUST.prepare(self.source, self.keytool, self.output, {self.denied}, None, None)
        self.assertFalse((self.output / "cacerts").exists())
        self.assertFalse((self.output / "receipt.json").exists())

    @unittest.skipUnless(shutil.which("java"), "java is required")
    def test_filtered_store_accepts_retained_cert_and_rejects_denied_cert(self):
        TRUST.prepare(self.source, self.keytool, self.output, {self.denied}, None, None)
        probe = self.root / "TrustHandshake.java"
        probe.write_text('''
import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import javax.net.ssl.*;
class TrustHandshake {
  public static void main(String[] args) throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (var input = new FileInputStream(args[0])) { keys.load(input, "changeit".toCharArray()); }
    var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(keys, "changeit".toCharArray());
    var serverContext = SSLContext.getInstance("TLS");
    serverContext.init(km.getKeyManagers(), null, null);
    try (var listener = (SSLServerSocket) serverContext.getServerSocketFactory()
        .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var server = new Thread(() -> { try (var s = (SSLSocket) listener.accept()) {
        s.startHandshake();
      } catch (Exception ignored) {} });
      server.setDaemon(true); server.start();
      boolean accepted;
      try (var s = (SSLSocket) SSLSocketFactory.getDefault()
          .createSocket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
        s.startHandshake(); accepted = true;
      } catch (SSLHandshakeException rejected) { accepted = false; }
      server.join(5000);
      if (accepted != Boolean.parseBoolean(args[1])) throw new AssertionError("unexpected TLS result");
      System.out.println(accepted ? "accepted" : "rejected");
    }
  }
}
''')
        env = os.environ.copy()
        for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
            env.pop(name, None)
        for alias, expected in (("retained", "true"), ("denied", "false")):
            with self.subTest(alias=alias):
                result = subprocess.run([
                    shutil.which("java"),
                    "-Djavax.net.ssl.trustStore=" + str(self.output / "cacerts"),
                    "-Djavax.net.ssl.trustStorePassword=changeit", str(probe),
                    str(self.root / (alias + ".p12")), expected,
                ], env=env, capture_output=True, text=True, timeout=20, check=True)
                self.assertEqual(result.stdout.strip(), "accepted" if expected == "true" else "rejected")


if __name__ == "__main__":
    unittest.main()
