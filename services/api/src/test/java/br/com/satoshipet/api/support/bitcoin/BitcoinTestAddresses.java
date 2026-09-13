package br.com.satoshipet.api.support.bitcoin;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

/**
 * Endereços Bitcoin válidos e determinísticos para testes.
 */
public final class BitcoinTestAddresses {

    /** Endereço mainnet P2WPKH gerado deterministicamente via bitcoinj. */
    public static final String MAINNET_BECH32 = Address.fromKey(
            MainNetParams.get(),
            ECKey.fromPrivate(hexToBytes("04".repeat(32))),
            ScriptType.P2WPKH
    ).toString();

    /** Endereço regtest gerado deterministicamente via bitcoinj. */
    public static final String REGTEST_BECH32 = Address.fromKey(
            RegTestParams.get(),
            ECKey.fromPrivate(hexToBytes("01".repeat(32))),
            ScriptType.P2WPKH
    ).toString();

    /** Endereço testnet gerado deterministicamente via bitcoinj. */
    public static final String TESTNET_BECH32 = Address.fromKey(
            TestNet3Params.get(),
            ECKey.fromPrivate(hexToBytes("02".repeat(32))),
            ScriptType.P2WPKH
    ).toString();

    public static final String MAINNET_P2PKH = Address.fromKey(
            MainNetParams.get(),
            ECKey.fromPrivate(hexToBytes("03".repeat(32))),
            ScriptType.P2PKH
    ).toString();

    /** Endereço mainnet válido reservado para cenários “não monitorado” (404). */
    public static final String MAINNET_UNMONITORED = Address.fromKey(
            MainNetParams.get(),
            ECKey.fromPrivate(hexToBytes("05".repeat(32))),
            ScriptType.P2WPKH
    ).toString();

    /** Endereço mainnet com pet pré-existente (contas compartilhando endereço). */
    public static final String MAINNET_WITH_PET = Address.fromKey(
            MainNetParams.get(),
            ECKey.fromPrivate(hexToBytes("06".repeat(32))),
            ScriptType.P2WPKH
    ).toString();

    private BitcoinTestAddresses() {
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
