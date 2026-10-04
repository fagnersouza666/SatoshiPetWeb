package br.com.satoshipet.api.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;

/** Serializa criação/vínculos/exclusão e recuperação; nunca abrange chamadas a provedores. */
@Entity
@Table(name = "account_mutation_locks")
public class AccountMutationLock extends PanacheEntityBase {
    @Id @Column(name = "lock_name")
    public String name;

    public static void acquire() {
        if (findById("accounts", LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalStateException("Trava persistida de contas indisponível");
        }
    }
}
