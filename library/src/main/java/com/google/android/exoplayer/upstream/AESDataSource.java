package com.google.android.exoplayer.upstream;

import android.net.Uri;

import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.android.exoplayer.util.Util;

public final class AESDataSource implements DataSource {
  private Cipher cipher;
  private DataSource underlyingDataSource;
  private String userAgent;

  private CipherInputStream cipherInputStream;

  // XXX: reuse the upstream/cache stuff ?
  // this is not just an optimisation. Some servers use single usage tokens
  // so we must make sure we never request the same key twice
  static final HashMap<String, byte[]> keyCache = new HashMap<String, byte[]>();

  public AESDataSource(String userAgent, DataSource underlyingDataSource){
    this.underlyingDataSource = underlyingDataSource;
    this.userAgent = userAgent;
  }


  @Override
  public long open(DataSpec dataSpec) throws IOException {
    DataSpec underlyingDataSpec = null;

    if (dataSpec.uri.getScheme().equals("aes")) {
      try {
        cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
      } catch (NoSuchAlgorithmException e) {
        throw new IOException("Specified decryption algorithm not supported", e);
      } catch (NoSuchPaddingException e) {
        throw new IOException("Specified padding scheme is not available", e);
      }

      String keyUrl = dataSpec.uri.getQueryParameter("keyUrl");
      String dataUrl = dataSpec.uri.getQueryParameter("dataUrl");
      Uri keyUri = Uri.parse(keyUrl);
      if (keyUri.isRelative()) {
        String absolute = Util.makeAbsoluteUrl(dataUrl, keyUrl);
        keyUri = Uri.parse(absolute);
      }
      DataSource keyDataSource;

      if (keyUri.getScheme().equals("file")) {
        keyDataSource = new FileDataSource();
      } else {
        keyDataSource = new HttpDataSource(userAgent, null);
      }
      byte key[];
      synchronized (keyCache) {
        key = keyCache.get(keyUri.toString());
        if (key == null) {
          DataSpec keyDataSpec = new DataSpec(keyUri, 0, DataSpec.LENGTH_UNBOUNDED, null);
          keyDataSource.open(keyDataSpec);
          key = new byte[16];
          int bytesRead = 0;
          while (bytesRead < 16) {
            int ret = keyDataSource.read(key, bytesRead, 16 - bytesRead);
            if (ret <= 0) {
              throw new IOException("Cannot read key from " + keyUri);
            }
            bytesRead += ret;
          }
          keyDataSource.close();
          keyCache.put(keyUri.toString(), key);
        }
      }
      String ivHexa = dataSpec.uri.getQueryParameter("iv");
      byte iv[] = Util.hexToBin(ivHexa);
      Key cipherKey = new SecretKeySpec(key, "AES");
      AlgorithmParameterSpec cipherIV = new IvParameterSpec(iv);

      try {
        cipher.init(Cipher.DECRYPT_MODE, cipherKey, cipherIV);
      } catch (InvalidKeyException e) {
          throw new IOException("Given key was not accepted", e);
      } catch (InvalidAlgorithmParameterException e) {
          throw new IOException("Given algorithm parameters was not accepted", e);
      }

      underlyingDataSpec = new DataSpec(Uri.parse(dataUrl), dataSpec.absoluteStreamPosition, dataSpec.length, dataSpec.key);
      DataSourceInputStream is = new DataSourceInputStream(underlyingDataSource, underlyingDataSpec);
      cipherInputStream = new CipherInputStream(is, cipher);

      return DataSpec.LENGTH_UNBOUNDED;

    } else {
      return underlyingDataSource.open(dataSpec);
    }
  }

  @Override
  public void close() throws IOException {
    underlyingDataSource.close();
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (cipherInputStream != null) {
      int ret = cipherInputStream.read(buffer, offset, readLength);
      if (ret == -1) {
        return -1;
      }

      return ret;
    } else {
      return underlyingDataSource.read(buffer, offset, readLength);
    }
  }
}
