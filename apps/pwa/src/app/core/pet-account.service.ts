import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { AccountPetSnapshot, ArtworkInfo } from './models/pet-art.model';

/**
 * Operações autenticadas do pet e da arte (ART-05/06).
 * Não dispara regeneração no carregamento — apenas leitura e ações explícitas.
 */
@Injectable({ providedIn: 'root' })
export class PetAccountService {
  private readonly api = inject(ApiClientService);

  async loadSnapshot(): Promise<AccountPetSnapshot> {
    return firstValueFrom(this.api.get<AccountPetSnapshot>('/v1/account/pet'));
  }

  async approveArtwork(): Promise<ArtworkInfo> {
    return firstValueFrom(this.api.post<ArtworkInfo>('/v1/account/pet/artwork/approve', {}));
  }

  async regenerateArtwork(): Promise<ArtworkInfo> {
    return firstValueFrom(this.api.post<ArtworkInfo>('/v1/account/pet/artwork/regenerate', {}));
  }
}
