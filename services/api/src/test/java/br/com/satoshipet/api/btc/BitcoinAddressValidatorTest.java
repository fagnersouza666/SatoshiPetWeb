package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.support.bitcoin.BitcoinTestAddresses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitcoinAddressValidatorTest {

    private final BitcoinAddressValidator validator = new BitcoinAddressValidator();

    @ParameterizedTest(name = "válido: {0}")
    @ValueSource(strings = {
            "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
    })
    void enderecoValido(String address) {
        assertTrue(validator.isValid(address), "Esperado válido: " + address);
    }

    @Test
    void mainnetP2PKHValido() {
        assertTrue(validator.isValid(BitcoinTestAddresses.MAINNET_P2PKH));
    }

    @Test
    void mainnetP2SHValido() {
        assertTrue(validator.isValid("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"));
    }

    @Test
    void mainnetBech32Valido() {
        assertTrue(validator.isValid(BitcoinTestAddresses.MAINNET_BECH32));
    }

    @Test
    void mainnetBech32UppercaseValido() {
        assertTrue(validator.isValid(BitcoinTestAddresses.MAINNET_BECH32.toUpperCase()));
    }

    @Test
    void regtestBech32Valido() {
        assertTrue(validator.isValid(BitcoinTestAddresses.REGTEST_BECH32));
    }

    @Test
    void testnetBech32Valido() {
        assertTrue(validator.isValid(BitcoinTestAddresses.TESTNET_BECH32));
    }

    @Test
    void nullInvalido() {
        assertFalse(validator.isValid(null));
    }

    @Test
    void brancosInvalidos() {
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("   "));
    }

    @Test
    void enderecoAleatorioinvalido() {
        assertFalse(validator.isValid("not-a-bitcoin-address"));
    }

    @Test
    void base58ComChecksumeErrado() {
        assertFalse(validator.isValid("1A1zP1eP5QGefi2DMPTfTL5SLmv7Divfxx"));
    }

    @Test
    void bech32ComCaracteresInvalidos() {
        assertFalse(validator.isValid("bc1qillegalcharb1lhc0"));
    }

    @Test
    void bech32UppercaseVirarMinuscula() {
        String canonical = validator.canonicalize(BitcoinTestAddresses.MAINNET_BECH32.toUpperCase());
        assertEquals(BitcoinTestAddresses.MAINNET_BECH32, canonical);
    }

    @Test
    void bech32JaMinusculaNaoAltera() {
        assertEquals(BitcoinTestAddresses.MAINNET_BECH32,
                validator.canonicalize(BitcoinTestAddresses.MAINNET_BECH32));
    }

    @Test
    void legacyP2PKHNaoAltera() {
        assertEquals(BitcoinTestAddresses.MAINNET_P2PKH,
                validator.canonicalize(BitcoinTestAddresses.MAINNET_P2PKH));
    }

    @Test
    void canonicalizarNullRetornaNull() {
        assertEquals(null, validator.canonicalize(null));
    }

    @Test
    void regtestBech32Canonicaliza() {
        String upper = BitcoinTestAddresses.REGTEST_BECH32.toUpperCase();
        assertEquals(BitcoinTestAddresses.REGTEST_BECH32, validator.canonicalize(upper));
    }

    @Test
    void detectaMainnetP2PKH() {
        assertEquals("mainnet", validator.detectNetwork(BitcoinTestAddresses.MAINNET_P2PKH));
    }

    @Test
    void detectaMainnetP2SH() {
        assertEquals("mainnet", validator.detectNetwork("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"));
    }

    @Test
    void detectaMainnetBech32() {
        assertEquals("mainnet", validator.detectNetwork(BitcoinTestAddresses.MAINNET_BECH32));
    }

    @Test
    void detectaTestnet() {
        assertEquals("testnet", validator.detectNetwork(BitcoinTestAddresses.TESTNET_BECH32));
    }

    @Test
    void detectaRegtest() {
        assertEquals("regtest", validator.detectNetwork(BitcoinTestAddresses.REGTEST_BECH32));
    }

    @Test
    void detectaUnknownParaNulo() {
        assertEquals("unknown", validator.detectNetwork(null));
    }

    @Test
    void mainnetValidaMainnetBech32() {
        assertTrue(validator.isValidMainnet(BitcoinTestAddresses.MAINNET_BECH32));
    }

    @Test
    void mainnetRejeita_regtestBech32() {
        assertFalse(validator.isValidMainnet(BitcoinTestAddresses.REGTEST_BECH32));
    }
}
