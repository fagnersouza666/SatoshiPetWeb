package br.com.satoshipet.api.isolation;

import br.com.satoshipet.api.account.Account;
import br.com.satoshipet.api.account.AccountMutationLock;
import br.com.satoshipet.api.pet.Pet;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exclusão transacional dos dados privados existentes, preservando fatos públicos e criaturas. */
@ApplicationScoped
public class DatabaseAccountPrivateDataWipePort implements AccountPrivateDataWipePort {
    @Inject EntityManager entityManager;

    @Override
    @Transactional
    public void wipe(UUID accountId) {
        AccountMutationLock.acquire();
        AccountDeletionTombstone tombstone = AccountDeletionTombstone.findById(accountId);
        if (tombstone == null) {
            tombstone = new AccountDeletionTombstone();
            tombstone.accountId = accountId;
            tombstone.deletedAt = Instant.now();
            tombstone.persist();
        }
        deletePrivateData(accountId);
    }

    /** O diário de exclusões importado é aplicado antes de a API aceitar tráfego. */
    @Transactional
    void restorePrivacy(@Observes StartupEvent event) {
        AccountMutationLock.acquire();
        List<AccountDeletionTombstone> deletions = AccountDeletionTombstone.listAll();
        for (AccountDeletionTombstone deletion : deletions) deletePrivateData(deletion.accountId);
    }

    private void deletePrivateData(UUID accountId) {
        Account account = Account.findById(accountId);
        if (account == null) return;
        // Mesma ordem global → Pet adotada pela troca; arte usa Pet → PetArtwork.
        List<Pet> pets = Pet.list("creatorAccount.id = ?1 OR foodSourceAccount.id = ?1 "
                + "OR address.id IN (SELECT b.address.id FROM AccountAddressBinding b WHERE b.account.id = ?1) "
                + "ORDER BY id", accountId);
        for (Pet pet : pets) Pet.lockForUpdate(pet.id);
        entityManager.createQuery("DELETE FROM MagicLinkToken WHERE email = :email")
                .setParameter("email", account.email).executeUpdate();
        entityManager.createQuery("DELETE FROM OutboxEvent WHERE aggregateType = 'Account' AND aggregateId = :id")
                .setParameter("id", accountId.toString()).executeUpdate();
        // Cascatas eliminam sessões, códigos/challenges, vínculos e cursor privado.
        // FKs SET NULL apagam a associação pessoal em pets e porções imutáveis.
        Account.delete("id", accountId);
        entityManager.flush();
    }
}
