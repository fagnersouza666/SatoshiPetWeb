package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import br.com.satoshipet.api.pet.Pet;
import br.com.satoshipet.api.pet.PetPublicSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Recurso público de informações sobre endereços Bitcoin.
 *
 * <p>Endpoint aberto (sem autenticação) que retorna apenas dados públicos
 * de um endereço monitorado (CA-009, PRD §8).</p>
 *
 * <p>Nunca expõe accountId, petId, bindingId, email ou qualquer campo da
 * denylist de {@code redaction-policy.json}.</p>
 */
@Path("/api/v1/public/addresses")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PublicAddressResource {

    private static final Logger LOG = Logger.getLogger(PublicAddressResource.class);

    /** Número máximo de transações no histórico retornado. */
    private static final int MAX_HISTORY_SIZE = 20;

    /** Template do URL do block explorer público. */
    private static final String EXPLORER_MAINNET  = "https://mempool.space/address/%s";
    private static final String EXPLORER_TESTNET  = "https://mempool.space/testnet/address/%s";
    private static final String EXPLORER_REGTEST  = "https://mempool.space/signet/address/%s";

    private final BitcoinAddressValidator validator;

    @Inject
    public PublicAddressResource(BitcoinAddressValidator validator) {
        this.validator = validator;
    }

    /**
     * Retorna informações públicas de um endereço Bitcoin monitorado.
     *
     * <p>Resposta inclui saldo, histórico paginado, QR data, explorer e o
     * bloco compartilhado do pet (CA-009: sem petId, accountId ou e-mail).</p>
     *
     * @param address endereço Bitcoin (qualquer capitalização aceita para bech32)
     * @return {@link PublicAddressResponse} com dados públicos, ou 404 se não monitorado
     */
    @GET
    @Path("/{address}")
    public Response getAddress(@PathParam("address") String address) {
        if (address == null || address.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Endereço inválido\"}")
                    .build();
        }

        // Canonicaliza o endereço (bech32 → lowercase)
        String canonical = validator.canonicalize(address.trim());

        // Valida o formato do endereço
        if (!validator.isValid(canonical)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Endereço Bitcoin inválido\"}")
                    .build();
        }

        String network = validator.detectNetwork(canonical);

        // Busca o endereço monitorado
        Address managed = Address.findByNetworkAndCanonical(network, canonical)
                .orElseThrow(() -> new NotFoundException(
                        "Endereço não monitorado: " + canonical
                ));

        LOG.debugf("Consulta pública de endereço=%s", canonical);

        return Response.ok(buildResponse(managed, canonical, network)).build();
    }

    // -------------------------------------------------------------------------
    // Construção da resposta (somente campos públicos — CA-009)
    // -------------------------------------------------------------------------

    private PublicAddressResponse buildResponse(Address managed, String canonical, String network) {
        // Busca transações do banco de dados (dados locais, sem chamada externa)
        List<BitcoinTransaction> txs = BitcoinTransaction.list(
                "address = ?1 ORDER BY observedAt DESC",
                managed
        );

        // Calcula saldos a partir dos recebimentos lógicos persistidos
        List<LogicalReceipt> receipts = LogicalReceipt.findByAddress(managed);
        long confirmedSats = receipts.stream().mapToLong(r -> r.confirmedSats).sum();
        long pendingSats   = receipts.stream().mapToLong(r -> r.pendingSats).sum();

        // Mapeia histórico recente (somente campos públicos)
        List<PublicAddressResponse.TransactionSummary> history = txs.stream()
                .limit(MAX_HISTORY_SIZE)
                .map(tx -> new PublicAddressResponse.TransactionSummary(
                        tx.txid,
                        tx.status.name(),
                        tx.amountSats,
                        tx.observedAt,
                        tx.confirmedAt,
                        tx.blockHeight
                ))
                .toList();

        PetPublicSnapshot petSnapshot = Pet.findByAddress(managed)
                .map(pet -> PetPublicSnapshot.from(pet, pendingSats))
                .orElseGet(PetPublicSnapshot::empty);

        return PublicAddressResponse.of(
                canonical,
                network,
                confirmedSats,
                pendingSats,
                txs.size(),
                history,
                buildQrData(canonical),
                buildExplorerUrl(canonical, network),
                petSnapshot
        );
    }

    private String buildQrData(String canonical) {
        return "bitcoin:" + canonical;
    }

    private String buildExplorerUrl(String canonical, String network) {
        return switch (network) {
            case "testnet" -> String.format(EXPLORER_TESTNET, canonical);
            case "regtest" -> String.format(EXPLORER_REGTEST, canonical);
            default        -> String.format(EXPLORER_MAINNET, canonical);
        };
    }
}
