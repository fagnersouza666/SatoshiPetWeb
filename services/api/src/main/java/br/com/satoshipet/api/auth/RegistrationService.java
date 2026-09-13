package br.com.satoshipet.api.auth;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountAddressBinding;
import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.account.MagicLinkToken;
import br.com.satoshipet.api.account.MagicLinkTokenService;
import br.com.satoshipet.api.account.SessionService;
import br.com.satoshipet.api.btc.BitcoinAddressValidator;
import br.com.satoshipet.api.pet.Pet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Orquestra o registro de uma nova conta após verificação do magic link.
 *
 * <p>Cria atomicamente: Account, Address, AccountAddressBinding e Pet.
 * Define {@code addressChangeDeadline = now + 72h} conforme CA-004.</p>
 */
@ApplicationScoped
public class RegistrationService {

    private static final Logger LOG = Logger.getLogger(RegistrationService.class);

    /** Janela de troca de endereço após o primeiro vínculo (CA-004). */
    static final Duration ADDRESS_CHANGE_WINDOW = Duration.ofHours(72);

    private final MagicLinkTokenService tokenService;
    private final SessionService sessionService;
    private final BitcoinAddressValidator addressValidator;

    public RegistrationService(
            MagicLinkTokenService tokenService,
            SessionService sessionService,
            BitcoinAddressValidator addressValidator
    ) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.addressValidator = addressValidator;
    }

    /**
     * Registra uma nova conta, validando o token de magic link.
     *
     * @param rawToken      token bruto do magic link (consumido aqui)
     * @param bitcoinAddress endereço Bitcoin mainnet
     * @param petName        nome do pet
     * @param timezone       fuso horário IANA (padrão: "America/Sao_Paulo")
     * @param userAgent      agente do cliente
     * @param ip             IP de origem
     * @param now            instante de criação
     * @return resultado do registro com tokens de sessão
     * @throws RegistrationException se o token for inválido, endereço for inválido ou conta já existir
     */
    @Transactional
    public SessionService.SessionCreation register(
            String rawToken,
            String bitcoinAddress,
            String petName,
            String timezone,
            String userAgent,
            String ip,
            Instant now
    ) {
        // 1. Consome o token de magic link
        Optional<MagicLinkToken> consumed = tokenService.consume(rawToken, now);
        if (consumed.isEmpty()) {
            throw new RegistrationException("token_invalid", "Token de verificação inválido ou expirado.");
        }

        String email = consumed.get().getEmail();

        // 2. Verifica se conta já existe (evita duplicidade)
        if (Account.findByEmail(email) != null) {
            throw new RegistrationException("account_exists", "Já existe uma conta para este e-mail.");
        }

        // 3. Valida endereço Bitcoin
        if (!addressValidator.isValidMainnet(bitcoinAddress)) {
            throw new RegistrationException("invalid_address", "Endereço Bitcoin inválido para mainnet.");
        }
        String canonical = addressValidator.canonicalize(bitcoinAddress);

        // 4. Valida nome do pet
        String trimmedName = petName != null ? petName.trim() : "";
        if (trimmedName.isEmpty() || trimmedName.length() > 100) {
            throw new RegistrationException("invalid_pet_name", "Nome do pet deve ter entre 1 e 100 caracteres.");
        }

        // 5. Valida fuso horário
        String tz = timezone != null && !timezone.isBlank() ? timezone : "America/Sao_Paulo";
        try {
            java.time.ZoneId.of(tz);
        } catch (java.time.zone.ZoneRulesException e) {
            throw new RegistrationException("invalid_timezone", "Fuso horário inválido: " + tz);
        }

        // 6. Cria as entidades atomicamente
        Account account = Account.create(email, tz, "pt-BR", now);
        account.addressChangeDeadline = now.plus(ADDRESS_CHANGE_WINDOW);
        account.persist();

        Address address = Address.findByCanonical(canonical)
                .orElseGet(() -> {
                    Address a = Address.create(canonical, now);
                    a.persist();
                    return a;
                });

        // Verifica se o endereço já tem vínculo ativo (outra conta pode acompanhar o mesmo)
        AccountAddressBinding binding = AccountAddressBinding.create(account, address, true, now);
        binding.persist();

        Pet pet = Pet.findByAddress(address).orElseGet(() -> {
            Pet created = Pet.create(address, account, trimmedName, now);
            created.persist();
            return created;
        });

        LOG.infof("Conta registrada: account=%s address=%s pet=%s", account.id, address.id, pet.id);

        // 7. Cria sessão
        return sessionService.create(account, now, userAgent, ip);
    }

    /** Exceção de domínio para falhas de registro. */
    public static class RegistrationException extends RuntimeException {
        private final String code;

        public RegistrationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
