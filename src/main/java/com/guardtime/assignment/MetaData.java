package com.guardtime.assignment;

/** A class representing a POJO of a single metadata content of a datafile in the manifest file. */
public class MetaData {

  private String uri;
  private String hashAlgorithm;
  private String hash;

  MetaData(String uri, String hashAlgorithm, String hash) {
    this.uri = uri;
    this.hashAlgorithm = hashAlgorithm;
    this.hash = hash;
  }

  String getUri() {
    return uri;
  }

  String getHashAlgorithm() {
    return hashAlgorithm;
  }

  String getHash() {
    return hash;
  }
}
