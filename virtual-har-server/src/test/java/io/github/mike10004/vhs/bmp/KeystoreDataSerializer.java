package io.github.mike10004.vhs.bmp;

import com.google.common.io.BaseEncoding;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.builder.EqualsBuilder;

import static com.google.common.base.Preconditions.checkArgument;

public interface KeystoreDataSerializer {

    String serialize(KeystoreData keystoreData);
    KeystoreData deserialize(String serializedForm);

    static KeystoreDataSerializer getDefault() {
        return new KeystoreDataSerializer() {

            private final String KEY_KEYSTORE_TYPE = "keystoreType";
            private final String KEY_KEYSTORE_BYTES = "keystoreBytes";
            private final String KEY_KEYSTORE_PASSWORD = "keystorePassword";
            private final String KEY_PRIVATE_KEY_ALIAS = "privateKeyAlias";
            private final BaseEncoding keystoreBytesEncoding = BaseEncoding.base64();

            public String serialize(KeystoreData keystoreData) {
                JsonObject object = new JsonObject();
                object.addProperty(KEY_PRIVATE_KEY_ALIAS, keystoreData.privateKeyAlias);
                object.addProperty(KEY_KEYSTORE_BYTES, keystoreBytesEncoding.encode(keystoreData.keystoreBytes));
                String pw = "";
                if (keystoreData.keystorePassword != null) {
                    pw = String.copyValueOf(keystoreData.keystorePassword);
                }
                object.addProperty(KEY_KEYSTORE_PASSWORD, pw);
                object.addProperty(KEY_KEYSTORE_TYPE, keystoreData.keystoreType.name());
                return new GsonBuilder().setPrettyPrinting().create().toJson(object);
            }

            public KeystoreData deserialize(String serializedForm) {
                JsonObject object = new JsonParser().parse(serializedForm).getAsJsonObject();
                checkArgument(object.has(KEY_KEYSTORE_PASSWORD), KEY_KEYSTORE_PASSWORD);
                checkArgument(object.has(KEY_KEYSTORE_BYTES), KEY_KEYSTORE_BYTES);
                checkArgument(object.has(KEY_KEYSTORE_TYPE), KEY_KEYSTORE_TYPE);
                checkArgument(object.has(KEY_PRIVATE_KEY_ALIAS), KEY_PRIVATE_KEY_ALIAS);
                KeystoreType keystoreType = KeystoreType.valueOf(object.get(KEY_KEYSTORE_TYPE).getAsString());
                char[] keystorePassword = object.get(KEY_KEYSTORE_PASSWORD).getAsString().toCharArray();
                String keystoreBytesBase64 = object.get(KEY_KEYSTORE_BYTES).getAsString();
                byte[] keystoreBytes = keystoreBytesEncoding.decode(keystoreBytesBase64);
                String privateKeyAlias = object.get(KEY_PRIVATE_KEY_ALIAS).getAsString();
                return new KeystoreData(keystoreType, keystoreBytes, privateKeyAlias, keystorePassword);
            }
        };
    }

    static boolean isEqual(KeystoreData a, KeystoreData b) {
        return new EqualsBuilder()
                .append(a.keystoreBytes, b.keystoreBytes)
                .append(a.keystorePassword, b.keystorePassword)
                .append(a.keystoreType, b.keystoreType)
                .append(a.privateKeyAlias, b.privateKeyAlias)
                .isEquals();
    }
}
