package com.zewbby.smartticket.service;

public interface AudienceIdentityService {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String hash(String plainText);

    String mask(String plainText);
}
