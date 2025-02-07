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
package net.oauth;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.oauth.signature.OAuthSignatureMethod;

@SuppressWarnings("javadoc")
public class SimpleOAuthValidator implements OAuthValidator {

  private static class UsedNonce implements Comparable<UsedNonce> {
    private final String sortKey;

    UsedNonce(final long timestamp, final String... nonceEtc) {
      final StringBuilder key = new StringBuilder(String.format("%20d", Long.valueOf(timestamp)));
      // The blank padding ensures that timestamps are compared as numbers.
      for (final String etc : nonceEtc) {
        key.append("&").append(etc == null ? " " : OAuth.percentEncode(etc));
        // A null value is different from "" or any other String.
      }
      this.sortKey = key.toString();
    }

    @Override
    public int compareTo(final UsedNonce that) {
      return that == null ? 1 : this.sortKey.compareTo(that.sortKey);
    }

    @Override
    public boolean equals(final Object that) {
      if (that == null) {
        return false;
      }
      if (that == this) {
        return true;
      }
      if (that.getClass() != getClass()) {
        return false;
      }
      return this.sortKey.equals(((UsedNonce)that).sortKey);
    }

    long getTimestamp() {
      int end = this.sortKey.indexOf("&");
      if (end < 0) {
        end = this.sortKey.length();
      }
      return Long.parseLong(this.sortKey.substring(0, end).trim());
    }

    @Override
    public int hashCode() {
      return this.sortKey.hashCode();
    }

    @Override
    public String toString() {
      return this.sortKey;
    }
  }

  public static final long DEFAULT_MAX_TIMESTAMP_AGE = 5 * 60 * 1000L;

  public static final long DEFAULT_TIMESTAMP_WINDOW = DEFAULT_MAX_TIMESTAMP_AGE;

  public static final Set<String> SINGLE_PARAMETERS = constructSingleParameters();

  private static Set<String> constructSingleParameters() {
    final Set<String> s = new HashSet<>();
    for (final String p : new String[] {
      OAuth.OAUTH_CONSUMER_KEY, OAuth.OAUTH_TOKEN, OAuth.OAUTH_TOKEN_SECRET, OAuth.OAUTH_CALLBACK,
      OAuth.OAUTH_SIGNATURE_METHOD, OAuth.OAUTH_SIGNATURE, OAuth.OAUTH_TIMESTAMP, OAuth.OAUTH_NONCE,
      OAuth.OAUTH_VERSION
    }) {
      s.add(p);
    }
    return Collections.unmodifiableSet(s);
  }

  protected final long maxTimestampAgeMsec;

  protected final double maxVersion;

  protected final double minVersion = 1.0;

  private final Set<UsedNonce> usedNonces = new TreeSet<>();

  public SimpleOAuthValidator() {
    this(DEFAULT_TIMESTAMP_WINDOW, Double.parseDouble(OAuth.VERSION_1_0));
  }

  public SimpleOAuthValidator(final long maxTimestampAgeMsec, final double maxVersion) {
    this.maxTimestampAgeMsec = maxTimestampAgeMsec;
    this.maxVersion = maxVersion;
  }

  protected void checkSingleParameters(final OAuthMessage message)
    throws IOException, OAuthException {
    // Check for repeated oauth_ parameters:
    boolean repeated = false;
    final Map<String, Collection<String>> nameToValues = new HashMap<>();
    for (final Map.Entry<String, String> parameter : message.getParameters()) {
      final String name = parameter.getKey();
      if (SINGLE_PARAMETERS.contains(name)) {
        Collection<String> values = nameToValues.get(name);
        if (values == null) {
          values = new ArrayList<>();
          nameToValues.put(name, values);
        } else {
          repeated = true;
        }
        values.add(parameter.getValue());
      }
    }
    if (repeated) {
      final Collection<OAuth.Parameter> rejected = new ArrayList<>();
      for (final Map.Entry<String, Collection<String>> p : nameToValues.entrySet()) {
        final String name = p.getKey();
        final Collection<String> values = p.getValue();
        if (values.size() > 1) {
          for (final String value : values) {
            rejected.add(new OAuth.Parameter(name, value));
          }
        }
      }
      final OAuthProblemException problem = new OAuthProblemException(
        OAuth.Problems.PARAMETER_REJECTED);
      problem.setParameter(OAuth.Problems.OAUTH_PARAMETERS_REJECTED, OAuth.formEncode(rejected));
      throw problem;
    }
  }

  protected long currentTimeMsec() {
    return System.currentTimeMillis();
  }

  public Date releaseGarbage() {
    return removeOldNonces(currentTimeMsec());
  }

  private Date removeOldNonces(final long currentTimeMsec) {
    UsedNonce next = null;
    final UsedNonce min = new UsedNonce((currentTimeMsec - this.maxTimestampAgeMsec + 500) / 1000L);
    synchronized (this.usedNonces) {
      // Because usedNonces is a TreeSet, its iterator produces
      // elements from oldest to newest (their natural order).
      for (final Iterator<UsedNonce> iter = this.usedNonces.iterator(); iter.hasNext();) {
        final UsedNonce used = iter.next();
        if (min.compareTo(used) <= 0) {
          next = used;
          break; // all the rest are also new enough
        }
        iter.remove(); // too old
      }
    }
    if (next == null) {
      return null;
    }
    return new Date(next.getTimestamp() * 1000L + this.maxTimestampAgeMsec + 500);
  }

  @Override
  public void validateMessage(final OAuthMessage message, final OAuthAccessor accessor)
    throws OAuthException, IOException, URISyntaxException {
    checkSingleParameters(message);
    validateVersion(message);
    validateTimestampAndNonce(message);
    validateSignature(message, accessor);
  }

  protected Date validateNonce(final OAuthMessage message, final long timestamp,
    final long currentTimeMsec) throws IOException, OAuthProblemException {
    final UsedNonce nonce = new UsedNonce(timestamp, message.getParameter(OAuth.OAUTH_NONCE),
      message.getConsumerKey(), message.getToken());
    boolean valid = false;
    synchronized (this.usedNonces) {
      valid = this.usedNonces.add(nonce);
    }
    if (!valid) {
      throw new OAuthProblemException(OAuth.Problems.NONCE_USED);
    }
    return removeOldNonces(currentTimeMsec);
  }

  protected void validateSignature(final OAuthMessage message, final OAuthAccessor accessor)
    throws OAuthException, IOException, URISyntaxException {
    message.requireParameters(OAuth.OAUTH_CONSUMER_KEY, OAuth.OAUTH_SIGNATURE_METHOD,
      OAuth.OAUTH_SIGNATURE);
    OAuthSignatureMethod.newSigner(message, accessor).validate(message);
  }

  protected void validateTimestamp(final OAuthMessage message, final long timestamp,
    final long currentTimeMsec) throws IOException, OAuthProblemException {
    final long min = (currentTimeMsec - this.maxTimestampAgeMsec + 500) / 1000L;
    final long max = (currentTimeMsec + this.maxTimestampAgeMsec + 500) / 1000L;
    if (timestamp < min || max < timestamp) {
      final OAuthProblemException problem = new OAuthProblemException(
        OAuth.Problems.TIMESTAMP_REFUSED);
      problem.setParameter(OAuth.Problems.OAUTH_ACCEPTABLE_TIMESTAMPS, min + "-" + max);
      throw problem;
    }
  }

  protected void validateTimestampAndNonce(final OAuthMessage message)
    throws IOException, OAuthProblemException {
    message.requireParameters(OAuth.OAUTH_TIMESTAMP, OAuth.OAUTH_NONCE);
    final long timestamp = Long.parseLong(message.getParameter(OAuth.OAUTH_TIMESTAMP));
    final long now = currentTimeMsec();
    validateTimestamp(message, timestamp, now);
    validateNonce(message, timestamp, now);
  }

  protected void validateVersion(final OAuthMessage message) throws OAuthException, IOException {
    final String versionString = message.getParameter(OAuth.OAUTH_VERSION);
    if (versionString != null) {
      final double version = Double.parseDouble(versionString);
      if (version < this.minVersion || this.maxVersion < version) {
        final OAuthProblemException problem = new OAuthProblemException(
          OAuth.Problems.VERSION_REJECTED);
        problem.setParameter(OAuth.Problems.OAUTH_ACCEPTABLE_VERSIONS,
          this.minVersion + "-" + this.maxVersion);
        throw problem;
      }
    }
  }
}
