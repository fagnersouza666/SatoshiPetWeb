package br.com.satoshipet.api.account;

import br.com.satoshipet.api.btc.BitcoinAddressValidator;
import br.com.satoshipet.api.isolation.AccountPrivateDataWipePort;
import br.com.satoshipet.api.isolation.PushSubscriptionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Orquestra a troca de endereço Bitcoin de uma conta dentro da janela permitida.
 *
 * <p>Regras (CA-004, PRD §4.3):
 * <ul>
 *   <li>Só é possível dentro das 72h a partir da criação da conta
 *       ({@code addressChangeDeadline}).</li>
 *   <li>Desfaz o vínculo atual, notifica portas de isolamento e cria o novo vínculo.</li>
 *   <li>O prazo {@code addressChangeDeadline} não é alterado.</li>
 *   <li>O pet permanece vinculado ao endereço atual; um pet novo será criado
 *       para o novo endereço se não existir (gerenciado pelo épico PET).</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
public class AccountAddressChangeService {

    private static final Logger LOG = Logger.getLogger(AccountAddressChangeService.class);

    private final BitcoinAddressValidator addressValidator;
    private final AccountPrivateDataWipePort wipePort;
    private final PushSubscriptionPort pushPort;

    public AccountAddressChangeService(
            BitcoinAddressValidator addressValidator,
            AccountPrivateDataWipePort wipePort,
            PushSubscriptionPort pushPort
    ) {
        this.addressValidator = addressValidator;
        this.wipePort = wipePort;
        this.pushPort = pushPort;
    }

    /**
     * Troca o endereço primário da conta.
     *
     * @param account    conta autenticada
     * @param newAddress novo endereço Bitcoin (será canonicalizado)
     * @param now        instante da operação
     * @throws AddressChangeException se fora da janela ou endereço inválido
     */
    @Transactional
    public Address change(Account account, String newAddress, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(newAddress, "newAddress");
        Objects.requireNonNull(now, "now");

        // Verifica janela de 72h
        if (account.addressChangeDeadline == null || !now.isBefore(account.addressChangeDeadline)) {
            throw new AddressChangeException("window_expired",
                    "O prazo para troca de endereço expirou (72h após o registro).");
        }

        // Valida novo endereço
        if (!addressValidator.isValidMainnet(newAddress)) {
            throw new AddressChangeException("invalid_address",
                    "Endereço Bitcoin inválido para mainnet.");
        }
        String canonical = addressValidator.canonicalize(newAddress);

        // Desfaz vínculo atual
        AccountAddressBinding.findActivePrimary(account).ifPresent(b -> {
            b.unbind(now);
            // Notifica portas de isolamento (dados privados vinculados ao endereço antigo)
            wipePort.wipe(account.id);
        });
        AccountAddressBinding.getEntityManager().flush();

        // Reutiliza endereço de vínculo anterior ou busca/cria globalmente
        Optional<AccountAddressBinding> priorBinding =
                AccountAddressBinding.findByAccountAndCanonical(account, canonical);

        Address destination = priorBinding.map(b -> b.address)
                .or(() -> Address.findByNetworkAndCanonical("mainnet", canonical))
                .orElseGet(() -> {
                    Address created = Address.create(canonical, "mainnet", now);
                    created.persist();
                    return created;
                });

        priorBinding.ifPresentOrElse(
                existing -> existing.rebindAsPrimary(now),
                () -> AccountAddressBinding.create(account, destination, true, now).persist()
        );

        LOG.infof("Troca de endereço: account=%s → address=%s canonical=%s",
                account.id, destination.id, canonical);

        return destination;
    }

    /** Exceção de domínio para falhas de troca de endereço. */
    public static class AddressChangeException extends RuntimeException {
        private final String code;

        public AddressChangeException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
