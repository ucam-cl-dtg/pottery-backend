/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.cam.cl.dtg.teaching.pottery.ssh;

import com.google.inject.Inject;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.io.Charsets;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;

public class SshManager {

  private static final String PUBLIC_KEY = "sshPublicKey";
  private static final String PRIVATE_KEY = "sshPrivateKey";

  private final Database database;
  private String publicKey;

  @Inject
  public SshManager(Database database) {
    this.database = database;
  }

  public void init() {
    KeyPair keyPair = makeKeyPair(database);

    this.publicKey = openSslEncode(keyPair.getPublic());
    String privateKey = pemEncode(keyPair.getPrivate());

    SshSessionFactory.setInstance(
        new JschConfigSessionFactory() {
          @Override
          protected void configure(OpenSshConfig.Host hc, Session session) {
            // do nothing
          }

          @Override
          protected JSch createDefaultJSch(FS fs) throws JSchException {
            JSch defaultJSch = super.createDefaultJSch(fs);
            defaultJSch.removeAllIdentity();
            defaultJSch.addIdentity(
                "Pottery", privateKey.getBytes(Charsets.US_ASCII), null, new byte[0]);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            JSch.setConfig(config);
            return defaultJSch;
          }
        });
  }

  private String pemEncode(PrivateKey privateKey) {
    try {
      PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(privateKey.getEncoded());
      ASN1Primitive primitive = privateKeyInfo.parsePrivateKey().toASN1Primitive();
      PemObject pemObject = new PemObject("RSA PRIVATE KEY", primitive.getEncoded());
      StringWriter stringWriter = new StringWriter();
      try (PemWriter pemWriter = new PemWriter(stringWriter)) {
        pemWriter.writeObject(pemObject);
      }
      return stringWriter.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String openSslEncode(PublicKey key) {
    try {
      return PublicKeyEntry.appendPublicKeyEntry(new StringBuilder(Byte.MAX_VALUE), key).toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static KeyPair makeKeyPair(Database database) {

    try (TransactionQueryRunner q = database.getQueryRunner()) {
      Optional<String> publicKey = database.lookupConfigValue(PUBLIC_KEY, q);
      Optional<String> privateKey = database.lookupConfigValue(PRIVATE_KEY, q);

      if (!publicKey.isPresent() || !privateKey.isPresent()) {
        KeyPairGenerator generator = SecurityUtils.getKeyPairGenerator("RSA");
        generator.initialize(2048);
        KeyPair kp = generator.generateKeyPair();
        Base64.Encoder encoder = Base64.getEncoder();
        String privateKeyBase64 = encoder.encodeToString(kp.getPrivate().getEncoded());
        String publicKeyBase64 = encoder.encodeToString(kp.getPublic().getEncoded());
        database.storeConfigValue(PUBLIC_KEY, publicKeyBase64, q);
        database.storeConfigValue(PRIVATE_KEY, privateKeyBase64, q);
        q.commit();
        return kp;
      } else {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        Base64.Decoder decoder = Base64.getDecoder();
        return new KeyPair(
            factory.generatePublic(new X509EncodedKeySpec(decoder.decode(publicKey.get()))),
            factory.generatePrivate(new PKCS8EncodedKeySpec(decoder.decode(privateKey.get()))));
      }
    } catch (SQLException | GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public String getPublicKey() {
    if (publicKey == null) {
      throw new RuntimeException("SshManager is not initialised yet");
    }
    return publicKey;
  }
}
