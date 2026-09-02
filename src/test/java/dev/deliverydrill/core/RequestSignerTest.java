package dev.deliverydrill.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSignerTest {
    @Test void signsSha256AsLowercaseHex() {
        assertThat(RequestSigner.sign("hello".getBytes(), "secret", "sha256", "hex"))
                .isEqualTo("88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b");
    }
    @Test void supportsBase64AndSha1() {
        assertThat(RequestSigner.sign("hello".getBytes(), "secret", "sha1", "base64")).isNotBlank();
    }
}
