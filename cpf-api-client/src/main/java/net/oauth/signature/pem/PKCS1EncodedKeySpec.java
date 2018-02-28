/*
 * Copyright © 2008-2016, Province of British Columbia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oauth.signature.pem;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.RSAPrivateCrtKeySpec;

/**
 * PKCS#1 encoded private key is commonly used with OpenSSL. It provides CRT
 * parameters so the private key operation can be much faster than using
 * exponent/modulus alone, which is the case for PKCS#8 encoded key.
 * <p/>
 * Unfortunately, JCE doesn't have an API to decode the DER. This class takes
 * DER buffer and decoded into CRT key.
 *
 * @author zhang
 */
@SuppressWarnings("javadoc")
public class PKCS1EncodedKeySpec {

  private RSAPrivateCrtKeySpec keySpec;

  /**
   * Construct a new PKCS#1 keyspec from DER encoded buffer
   *
   * @param keyBytes DER encoded octet stream
   * @throws IOException
   */
  public PKCS1EncodedKeySpec(final byte[] keyBytes) throws IOException {
    decode(keyBytes);
  }

  /**
   * Decode PKCS#1 encoded private key into RSAPrivateCrtKeySpec.
   * <p/>
   * The ASN.1 syntax for the private key with CRT is
   *
   * <pre>
   * --
   * -- Representation of RSA private key with information for the CRT algorithm.
   * --
   * RSAPrivateKey ::= SEQUENCE {
   *   version           Version,
   *   modulus           BIG_INTEGER,  -- n
   *   publicExponent    BIG_INTEGER,  -- e
   *   privateExponent   BIG_INTEGER,  -- d
   *   prime1            BIG_INTEGER,  -- p
   *   prime2            BIG_INTEGER,  -- q
   *   exponent1         BIG_INTEGER,  -- d mod (p-1)
   *   exponent2         BIG_INTEGER,  -- d mod (q-1)
   *   coefficient       BIG_INTEGER,  -- (inverse of q) mod p
   *   otherPrimeInfos   OtherPrimeInfos OPTIONAL
   * }
   * </pre>
   *
   * @param keyBytes PKCS#1 encoded key
   * @throws IOException
   */

  private void decode(final byte[] keyBytes) throws IOException {

    DerParser parser = new DerParser(keyBytes);

    final Asn1Object sequence = parser.read();
    if (sequence.getType() != DerParser.SEQUENCE) {
      throw new IOException("Invalid DER: not a sequence"); //$NON-NLS-1$
    }

    // Parse inside the sequence
    parser = sequence.getParser();

    parser.read(); // Skip version
    final BigInteger modulus = parser.read().getInteger();
    final BigInteger publicExp = parser.read().getInteger();
    final BigInteger privateExp = parser.read().getInteger();
    final BigInteger prime1 = parser.read().getInteger();
    final BigInteger prime2 = parser.read().getInteger();
    final BigInteger exp1 = parser.read().getInteger();
    final BigInteger exp2 = parser.read().getInteger();
    final BigInteger crtCoef = parser.read().getInteger();

    this.keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1,
      exp2, crtCoef);
  }

  /**
   * Get the key spec that JCE understands.
   *
   * @return CRT keyspec defined by JCE
   */
  public RSAPrivateCrtKeySpec getKeySpec() {
    return this.keySpec;
  }
}
