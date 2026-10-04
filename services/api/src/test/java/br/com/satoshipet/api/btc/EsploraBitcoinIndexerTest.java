package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.btc.esplora.*;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class EsploraBitcoinIndexerTest {
    static final String TXID = "ab".repeat(32), INPUT_TXID = "cd".repeat(32), ADDRESS = "1AbCdEf";

    @Test void mapeiaInputsEStatusDoPayloadRealSemInventarRbf() {
        EsploraBitcoinIndexer adapter = adapter((method, args) -> tx(ADDRESS));
        var found = adapter.getTransaction(ADDRESS, TXID);
        assertEquals(BitcoinIndexerPort.LookupState.FOUND, found.state());
        assertEquals(List.of(new BitcoinIndexerPort.InputInfo(INPUT_TXID, 2)), found.transaction().inputs());
        assertEquals(BitcoinTransaction.Status.CONFIRMED, found.transaction().status());
        assertEquals(500, found.transaction().amountSats());
    }

    @Test void enderecoBase58NaoIgnoraMaiusculas() {
        EsploraBitcoinIndexer adapter = adapter((method, args) -> List.of(tx(ADDRESS.toLowerCase())));
        assertEquals(0, adapter.getMempool(ADDRESS).getFirst().amountSats());
    }

    @Test void consultaIndividualDistingue404DeIndisponibilidade() {
        assertEquals(BitcoinIndexerPort.LookupState.MISSING,
                adapter((m,a) -> { throw new NotFoundException(); }).getTransaction(ADDRESS, TXID).state());
        assertEquals(BitcoinIndexerPort.LookupState.UNAVAILABLE,
                adapter((m,a) -> { throw new IllegalStateException("rede indisponível"); })
                        .getTransaction(ADDRESS, TXID).state());
    }

    @Test void outspendRetornaTxidSomenteQuandoProvedorConfirmaGasto() {
        var input = new BitcoinIndexerPort.InputInfo(INPUT_TXID, 2);
        var spent = adapter((m,a) -> {
            assertEquals("getOutspend", m); assertArrayEquals(new Object[] {INPUT_TXID, 2}, a);
            return new EsploraOutspend(true, TXID, 0, new EsploraTxStatus(false, null, null, null));
        }).getOutspend(input);
        assertTrue(spent.available()); assertEquals(TXID, spent.spendingTxid());
        assertNull(adapter((m,a) -> new EsploraOutspend(false, null, null, null)).getOutspend(input).spendingTxid());
        assertFalse(adapter((m,a) -> { throw new NotFoundException(); }).getOutspend(input).available());
    }

    @Test void paginaComErroNaoEhPaginaVaziaDisponivel() {
        var page = adapter((m,a) -> { throw new IllegalStateException("timeout"); }).getTransactionPage(ADDRESS, null, 25);
        assertFalse(page.available()); assertTrue(page.transactions().isEmpty());
    }

    private EsploraBitcoinIndexer adapter(BiFunction<String,Object[],Object> handler) {
        EsploraBitcoinIndexer result = new EsploraBitcoinIndexer();
        result.esplora = (EsploraClient) Proxy.newProxyInstance(EsploraClient.class.getClassLoader(),
                new Class<?>[] {EsploraClient.class}, (proxy, method, args) -> handler.apply(method.getName(), args));
        return result;
    }
    private EsploraTx tx(String address) {
        return new EsploraTx(TXID, 2, 0,
                List.of(new EsploraTxInput(INPUT_TXID, 2, null, 0xfffffffdL)),
                List.of(new EsploraTxOutput("script", "", "p2pkh", address, 500)),
                200, 800, 100, new EsploraTxStatus(true, 100, "ef".repeat(32), 1000L));
    }
}
