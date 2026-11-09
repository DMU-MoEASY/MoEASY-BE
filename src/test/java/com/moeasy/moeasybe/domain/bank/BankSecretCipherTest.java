package com.moeasy.moeasybe.domain.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moeasy.moeasybe.domain.bank.config.BankProperties;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.infra.BankSecretCipher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

class BankSecretCipherTest {
    @Test
    @DisplayName("암호문은 매번 달라지며 같은 컨텍스트에서만 복호화할 수 있다")
    void encrypt_authenticatedEncryption_rejectsWrongContextAndTampering() {
        // given
        BankProperties settings = new BankProperties();
        settings.setEncryptionKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        BankSecretCipher cipher = new BankSecretCipher(settings);
        // when
        String first = cipher.encrypt("local-only-secret", "credential:api");
        String second = cipher.encrypt("local-only-secret", "credential:api");
        // then
        assertThat(first).isNotEqualTo(second).doesNotContain("local-only-secret");
        assertThat(cipher.decrypt(first, "credential:api")).isEqualTo("local-only-secret");
        assertThrows(BankException.class, () -> cipher.decrypt(first, "other:api"));
        byte[] changed = Base64.getDecoder().decode(first);
        changed[changed.length - 1] ^= 1;
        assertThrows(
                BankException.class,
                () ->
                        cipher.decrypt(
                                Base64.getEncoder().encodeToString(changed), "credential:api"));
    }

    @Test
    @DisplayName("암호화 키가 없거나 256비트가 아니면 은행 비밀정보를 저장할 수 없다")
    void encrypt_invalidKey_failsClosed() {
        // given
        BankSecretCipher cipher = new BankSecretCipher(new BankProperties());
        // when
        BankException error =
                assertThrows(
                        BankException.class, () -> cipher.encrypt("local-only-secret", "context"));
        // then
        assertThat(error.getCode().getCode()).isEqualTo("BANK503_1");
    }
}
