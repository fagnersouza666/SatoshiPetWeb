package br.com.satoshipet.api.isolation;

import java.util.UUID;

/**
 * Porta para gerenciamento de assinaturas Web Push (VAPID).
 *
 * <p>Chaves VAPID e endpoints ficam somente no servidor (invariante de segurança).
 * Implementação real a ser fornecida pelo épico PUSH.</p>
 */
public interface PushSubscriptionPort {

    /**
     * Registra ou atualiza a assinatura Web Push de uma conta.
     *
     * @param accountId    conta que está se registrando
     * @param endpoint     URL de entrega do serviço de push
     * @param p256dhKey    chave pública do cliente (Base64Url)
     * @param authSecret   segredo de autenticação (Base64Url)
     */
    void subscribe(UUID accountId, String endpoint, String p256dhKey, String authSecret);

    /**
     * Remove a assinatura associada ao endpoint informado.
     * Idempotente: não lança erro se a assinatura não existir.
     *
     * @param accountId conta proprietária
     * @param endpoint  URL da assinatura a remover
     */
    void unsubscribe(UUID accountId, String endpoint);

    /**
     * Envia uma notificação push para todas as assinaturas ativas de uma conta.
     *
     * @param accountId identificador da conta destinatária
     * @param payload   conteúdo JSON da notificação (sem dados privados)
     */
    void send(UUID accountId, String payload);
}
