package br.com.satoshipet.api.isolation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Limpa configuração do vínculo anterior sem excluir a conta ou suas credenciais (CC-03). */
@ApplicationScoped
public class AccountConfigurationWipeService {
    @Inject EntityManager entityManager;

    @Transactional
    public void wipe(UUID accountId) {
        entityManager.createQuery("DELETE FROM PresentationCursor WHERE account.id = :id")
                .setParameter("id", accountId).executeUpdate();
    }
}
