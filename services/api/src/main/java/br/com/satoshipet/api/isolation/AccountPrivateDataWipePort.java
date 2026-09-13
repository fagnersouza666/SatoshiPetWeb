package br.com.satoshipet.api.isolation;

import java.util.UUID;

/**
 * Porta para exclusão de dados privados de uma conta.
 *
 * <p>Garante que dados pessoais (e-mail, histórico de sessões, sugestões DCA)
 * possam ser removidos em atendimento à LGPD (CA-070) sem afetar o histórico
 * público de recebimentos on-chain.</p>
 *
 * <p>Implementação real a ser fornecida pelo épico CONTA.</p>
 */
public interface AccountPrivateDataWipePort {

    /**
     * Apaga todos os dados privados associados à conta.
     * Operação irreversível; deve ser idempotente.
     *
     * @param accountId identificador da conta a ser apagada
     */
    void wipe(UUID accountId);
}
