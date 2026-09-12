/**
 * Modelos de domínio para contas, autenticação e endereços públicos.
 * Épico CONTA (histórias CONTA-01..10) e épico BTC (BTC-01..05).
 */

/** Informações da conta autenticada. */
export interface AccountInfo {
  id: string;
  email: string;
  address?: string;
  petName?: string;
}

/** Resposta da verificação do magic-link. */
export interface VerifyResponse {
  email: string;
  /** true quando o e-mail ainda não possui conta cadastrada. */
  isNewUser: boolean;
}

/** Payload de registro (cadastro inicial após primeiro acesso). */
export interface RegisterPayload {
  address: string;
  petName: string;
}

/** Dados públicos de um endereço Bitcoin. */
export interface PublicAddressInfo {
  address: string;
  petName?: string;
  petState?: 'ALIMENTADO' | 'PENSANDO' | 'CHATEADO' | 'FAMINTO' | 'CRITICO' | 'HIBERNANDO';
  createdAt?: string;
}
