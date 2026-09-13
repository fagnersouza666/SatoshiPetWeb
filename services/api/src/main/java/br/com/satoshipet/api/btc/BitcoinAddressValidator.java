package br.com.satoshipet.api.btc;

import jakarta.enterprise.context.ApplicationScoped;
import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.jboss.logging.Logger;

/**
 * Validação e canonicalização de endereços Bitcoin.
 *
 * <p>Suporta mainnet P2PKH (1…), P2SH (3…), SegWit bech32 (bc1q…) e Taproot (bc1p…).</p>
 */
@ApplicationScoped
public class BitcoinAddressValidator {

    private static final Logger LOG = Logger.getLogger(BitcoinAddressValidator.class);

    public boolean isValid(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        return tryParse(address.trim(), BitcoinNetwork.MAINNET)
                || tryParse(address.trim(), BitcoinNetwork.TESTNET)
                || tryParse(address.trim(), BitcoinNetwork.REGTEST);
    }

    public boolean isValidMainnet(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        return tryParse(address.trim(), BitcoinNetwork.MAINNET);
    }

    public String canonicalize(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        String trimmed = address.trim();
        for (BitcoinNetwork network : new BitcoinNetwork[]{
                BitcoinNetwork.MAINNET, BitcoinNetwork.TESTNET, BitcoinNetwork.REGTEST
        }) {
            try {
                Address parsed = Address.fromString(toParams(network), normalizeForNetwork(trimmed, network));
                if (parsed.network() == network) {
                    return parsed.toString();
                }
            } catch (AddressFormatException ignored) {
                // tenta próxima rede
            }
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("bc1") || lower.startsWith("tb1") || lower.startsWith("bcrt1")) {
            return lower;
        }
        return trimmed;
    }

    public String detectNetwork(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            return "unknown";
        }
        String addr = canonical.trim();
        if (tryParse(addr, BitcoinNetwork.MAINNET)) {
            return "mainnet";
        }
        if (tryParse(addr, BitcoinNetwork.TESTNET)) {
            return "testnet";
        }
        if (tryParse(addr, BitcoinNetwork.REGTEST)) {
            return "regtest";
        }
        return "unknown";
    }

    private boolean tryParse(String address, BitcoinNetwork network) {
        try {
            Address parsed = Address.fromString(toParams(network), normalizeForNetwork(address, network));
            return parsed.network() == network;
        } catch (AddressFormatException e) {
            LOG.debugf("Endereço inválido para rede %s: %s", network.id(), e.getMessage());
            return false;
        }
    }

    private String normalizeForNetwork(String address, BitcoinNetwork network) {
        String trimmed = address.trim();
        String lower = trimmed.toLowerCase();
        String hrp = network.segwitAddressHrp();
        if (hrp != null && lower.startsWith(hrp)) {
            return lower;
        }
        return trimmed;
    }

    private NetworkParameters toParams(BitcoinNetwork network) {
        return switch (network) {
            case MAINNET -> MainNetParams.get();
            case TESTNET -> TestNet3Params.get();
            case REGTEST -> RegTestParams.get();
            default -> throw new IllegalArgumentException("Rede não suportada: " + network);
        };
    }
}
