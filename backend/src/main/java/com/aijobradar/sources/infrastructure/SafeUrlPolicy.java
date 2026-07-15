package com.aijobradar.sources.infrastructure;

import com.aijobradar.sources.application.UnsafeSourceException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SafeUrlPolicy {
  public URI requirePublicHttpUrl(String value) {
    URI uri = metadataUrl(value);
    try {
      for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
        if (isBlocked(address))
          throw new UnsafeSourceException("UNSAFE_URL", "Source host is not public");
      }
      return uri;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("DNS_FAILURE", "Source host could not be resolved");
    }
  }

  public URI metadataUrl(String value) {
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!(scheme.equals("http") || scheme.equals("https"))
          || uri.getHost() == null
          || uri.getUserInfo() != null)
        throw new UnsafeSourceException("UNSAFE_URL", "Only HTTP(S) public URLs are allowed");
      return uri;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw new UnsafeSourceException("UNSAFE_URL", "URL is invalid");
    }
  }

  boolean isBlocked(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return true;
    if (address instanceof Inet6Address) {
      byte first = address.getAddress()[0];
      return (first & 0xFE) == 0xFC;
    }
    return false;
  }
}
