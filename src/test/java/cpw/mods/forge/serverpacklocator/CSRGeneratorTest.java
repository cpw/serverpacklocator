package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.cert.CertificateManager;
import org.junit.jupiter.api.Test;
import sun.security.pkcs10.PKCS10;

import java.nio.file.Paths;
import java.security.KeyPair;

public class CSRGeneratorTest {
    private KeyPair kp;
    private PKCS10 csr;

    @Test
    void testCSR() {
        CertificateManager.generateKeyPair(kp->this.kp = kp);
        CertificateManager.generateCSR(()->"hello world", ()->this.kp, csr->this.csr = csr);
        CertificateManager.writeCSR(()->this.csr, Paths.get("testout.csr"));
    }
}
