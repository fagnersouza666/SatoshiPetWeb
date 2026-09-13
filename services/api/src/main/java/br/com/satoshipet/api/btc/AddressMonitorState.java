package br.com.satoshipet.api.btc;

import br.com.satoshipet.api.account.Address;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado do monitor de um endereço no indexador Bitcoin.
 * Armazena o cursor da última consulta para implementar a reconexão com
 * snapshot + cursor sem repetir transações já processadas.
 */
@Entity
@Table(name = "address_monitor_state")
public class AddressMonitorState extends PanacheEntityBase {

    /**
     * Mesmo id do Address associado (OneToOne compartilhando PK).
     * Preenchido automaticamente pelo JPA via @MapsId — não atribuir manualmente.
     */
    @Id
    public UUID addressId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "address_id", nullable = false, updatable = false)
    public Address address;

    /**
     * Txid da última transação observada. Nulo quando o endereço ainda não
     * foi varrido pelo indexador.
     */
    @Column(name = "last_seen_txid", length = 64)
    public String lastSeenTxid;

    /** Momento da última verificação ao indexador. */
    @Column(name = "last_checked_at", nullable = false)
    public Instant lastCheckedAt;

    /**
     * Cursor opaco retornado pelo indexador para paginação de histórico.
     * Vazio antes da primeira varredura.
     */
    @Column(name = "cursor", columnDefinition = "TEXT")
    public String cursor;

    protected AddressMonitorState() {
        // Construtor exigido pelo Hibernate ORM.
    }

    /** Cria o estado inicial do monitor para um endereço recém-registrado. */
    public static AddressMonitorState init(Address address, Instant now) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(now, "now");

        AddressMonitorState state = new AddressMonitorState();
        state.addressId = address.id;
        state.address = address;
        state.lastCheckedAt = now;
        return state;
    }

    /** Atualiza cursor e último txid observado. */
    public void advance(String lastSeenTxid, String cursor, Instant now) {
        this.lastSeenTxid = lastSeenTxid;
        this.cursor = cursor;
        this.lastCheckedAt = now;
    }

    /** Busca o estado do monitor pelo endereço. */
    public static Optional<AddressMonitorState> findByAddress(Address address) {
        return findByIdOptional(address.id);
    }
}
