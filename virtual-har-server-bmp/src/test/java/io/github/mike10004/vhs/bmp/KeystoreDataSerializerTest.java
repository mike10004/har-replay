package io.github.mike10004.vhs.bmp;

import com.google.common.io.BaseEncoding;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class KeystoreDataSerializerTest {

    private Random random = new Random(KeystoreDataSerializerTest.class.getName().hashCode());

    @Test
    public void serialize() throws Exception {
        KeystoreData keystoreData = create();
        String serializedForm = KeystoreDataSerializer.getDefault().serialize(keystoreData);
        KeystoreData deserialized = KeystoreDataSerializer.getDefault().deserialize(serializedForm);
        assertTrue("inverse+inverse", KeystoreDataSerializer.isEqual(keystoreData, deserialized));
    }

    private KeystoreData create() {
        KeystoreType keystoreType = KeystoreType.PKCS12;
        byte[] bytes = new byte[2048];
        random.nextBytes(bytes);
        byte[] passwordBytes = new byte[32];
        random.nextBytes(passwordBytes);
        char[] password = BaseEncoding.base16().encode(passwordBytes).toCharArray();
        String privateKeyAlias = "myKey";
        KeystoreData keystoreData = new KeystoreData(keystoreType, bytes, privateKeyAlias, password);
        return keystoreData;
    }

}