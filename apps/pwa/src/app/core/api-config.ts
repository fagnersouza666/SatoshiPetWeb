import { InjectionToken } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * Token de injeção para a URL base da API.
 * Usar este token em vez de importar o environment diretamente nos serviços,
 * facilitando testes e troca de ambiente.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  factory: () => environment.apiUrl,
});
