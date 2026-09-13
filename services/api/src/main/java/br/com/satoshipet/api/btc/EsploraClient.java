package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.btc.esplora.EsploraAddressStats;
import br.com.satoshipet.api.btc.esplora.EsploraTx;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Cliente REST para a API pública Blockstream Esplora.
 *
 * <p>URL base configurável via {@code quarkus.rest-client.esplora.url}
 * (padrão: {@code https://blockstream.info/api}).</p>
 */
@RegisterRestClient(configKey = "esplora")
@Produces(MediaType.APPLICATION_JSON)
public interface EsploraClient {

    /** Retorna estatísticas de saldo de um endereço. */
    @GET
    @Path("/address/{address}")
    EsploraAddressStats getAddress(@PathParam("address") String address);

    /** Retorna as transações mais recentes (mempool + confirmadas) de um endereço. */
    @GET
    @Path("/address/{address}/txs")
    List<EsploraTx> getTransactions(@PathParam("address") String address);

    /** Retorna transações pendentes na mempool para o endereço. */
    @GET
    @Path("/address/{address}/txs/mempool")
    List<EsploraTx> getMempoolTransactions(@PathParam("address") String address);

    /** Retorna transações on-chain confirmadas após {@code lastSeenTxid}. */
    @GET
    @Path("/address/{address}/txs/chain/{lastSeenTxid}")
    List<EsploraTx> getChainTransactions(
            @PathParam("address") String address,
            @PathParam("lastSeenTxid") String lastSeenTxid
    );

    /** Retorna as 25 transações confirmadas mais recentes do endereço. */
    @GET
    @Path("/address/{address}/txs/chain")
    List<EsploraTx> getChainTransactionsFromStart(@PathParam("address") String address);
}
