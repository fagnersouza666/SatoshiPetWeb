package br.com.satoshipet.api.events;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainEventTypeTest {

    private static final Set<String> EXPECTED_CATALOG = Set.of(
            "BITCOIN_TRANSACTION_OBSERVED",
            "BITCOIN_TRANSACTION_CONFIRMED",
            "BITCOIN_TRANSACTION_REPLACED",
            "BITCOIN_TRANSACTION_DROPPED",
            "BITCOIN_CHAIN_REORG",
            "BITCOIN_BALANCE_RECONCILED",
            "PET_FEEDING_APPLIED",
            "PET_FEEDING_REVISED",
            "PET_FEEDING_INVALIDATED",
            "PET_ARTWORK_READY",
            "PET_BORN",
            "PET_RETURNED_TO_EGG",
            "PET_REAPPEARED",
            "PET_STATE_CHANGED",
            "DCA_RECOMMENDATION_GENERATED",
            "DCA_RECOMMENDATION_EXPIRED",
            "PURCHASE_REPORTED",
            "PURCHASE_CORRECTED",
            "PURCHASE_DELETED",
            "LOCATION_CHANGE_SCHEDULED",
            "LOCATION_CHANGE_APPLIED"
    );

    @Test
    void catalogoContemSomenteOsEventosDoPrd() {
        assertEquals(EXPECTED_CATALOG, DomainEventType.catalog());
        assertEquals(EXPECTED_CATALOG.size(), DomainEventType.values().length);
    }

    @Test
    void valorEConversaoMantemNomeCanonico() {
        for (DomainEventType type : DomainEventType.values()) {
            assertEquals(type, DomainEventType.fromValue(type.value()));
            assertEquals(type.name(), type.value());
        }
    }

    @Test
    void rejeitaEventoForaDoCatalogo() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainEventType.fromValue("EVENTO_NAO_CATALOGADO"));
    }
}
