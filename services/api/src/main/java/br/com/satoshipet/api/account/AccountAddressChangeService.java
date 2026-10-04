package br.com.satoshipet.api.account;

import br.com.satoshipet.api.btc.BitcoinAddressValidator;
import br.com.satoshipet.api.isolation.AccountConfigurationWipeService;
import br.com.satoshipet.api.pet.Pet;
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
    private final AccountConfigurationWipeService wipePort;

    public AccountAddressChangeService(
            BitcoinAddressValidator addressValidator,
            AccountConfigurationWipeService wipePort
    ) {
        this.addressValidator = addressValidator;
        this.wipePort = wipePort;
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
        return change(account, newAddress, null, now);
    }

    @Transactional
    public Address change(Account account, String newAddress, String petName, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(newAddress, "newAddress");
        Objects.requireNonNull(now, "now");
        AccountMutationLock.acquire();
        Account currentAccount = Account.findById(account.id);
        if (currentAccount == null) {
            throw new AddressChangeException("account_missing", "Conta não encontrada.");
        }
        Account.getEntityManager().flush();
        Account.getEntityManager().refresh(currentAccount);

        // Verifica janela de 72h
        if (currentAccount.addressChangeDeadline == null || !now.isBefore(currentAccount.addressChangeDeadline)) {
            throw new AddressChangeException("window_expired",
                    "O prazo para troca de endereço expirou (72h após o registro).");
        }

        // Valida novo endereço
        if (!addressValidator.isValidMainnet(newAddress)) {
            throw new AddressChangeException("invalid_address",
                    "Endereço Bitcoin inválido para mainnet.");
        }
        String canonical = addressValidator.canonicalize(newAddress);

        // Reutiliza endereço de vínculo anterior ou busca/cria globalmente
        Optional<AccountAddressBinding> priorBinding =
                AccountAddressBinding.findByAccountAndCanonical(currentAccount, canonical);

        Address destination = priorBinding.map(b -> b.address)
                .or(() -> Address.findByNetworkAndCanonical("mainnet", canonical))
                .orElseGet(() -> {
                    Address created = Address.create(canonical, "mainnet", now);
                    created.persist();
                    return created;
                });

        Optional<AccountAddressBinding> active = AccountAddressBinding.findActivePrimary(currentAccount);
        if (active.isPresent() && active.get().address.id.equals(destination.id)) return destination;
        Optional<Pet> previousPet = active.flatMap(b -> Pet.findByAddress(b.address));
        Optional<Pet> destinationPet = Pet.findByAddress(destination);
        java.util.stream.Stream.concat(previousPet.stream(), destinationPet.stream())
                .map(p -> p.id).distinct().sorted().forEach(Pet::lockForUpdate);
        String name = petName == null ? previousPet.map(p -> p.name).orElse("Satoshi") : petName.trim();
        if (destinationPet.isEmpty() && (name.isBlank() || name.length() > 100)) {
            throw new AddressChangeException("invalid_pet_name", "Nome do pet deve ter entre 1 e 100 caracteres.");
        }
        active.ifPresent(b -> b.unbind(now));
        wipePort.wipe(currentAccount.id);
        AccountAddressBinding.getEntityManager().flush();

        priorBinding.ifPresentOrElse(
                existing -> existing.rebindAsPrimary(now),
                () -> AccountAddressBinding.create(currentAccount, destination, true, now).persist()
        );
        if (destinationPet.isEmpty()) Pet.create(destination, currentAccount, name, now).persist();

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
