/** Estados emocionais públicos do pet (CA-009). */
export type PetEmotionalState =
  'ALIMENTADO' | 'PENSANDO' | 'CHATEADO' | 'FAMINTO' | 'CRITICO' | 'HIBERNANDO';

/** Poses do atlas 4×4 (ART-03). */
export type SpritePoseName =
  | 'idle'
  | 'look'
  | 'smile'
  | 'feeding'
  | 'celebration'
  | 'thinking'
  | 'upset'
  | 'hungry'
  | 'critical'
  | 'hibernation'
  | 'sleep'
  | 'birth'
  | 'return'
  | 'reduced_motion';

/** Bloco de arte no snapshot autenticado (ART-05/06). */
export interface ArtworkInfo {
  generationStatus: string;
  canApprove?: boolean;
  canRegenerate?: boolean;
  previewUrls?: Record<string, string>;
  approvedVersion?: number;
  atlasUrl?: string;
  currentAttemptNo?: number;
}

/** Snapshot autenticado do pet (GET /api/v1/account/pet). */
export interface AccountPetSnapshot {
  petName?: string;
  presentation?: 'EGG' | 'CREATURE';
  petState?: PetEmotionalState;
  reserveHours?: string;
  awaitingReference?: boolean;
  pendingMovesEgg?: boolean;
  operationalLabel?: string | null;
  artworkVersion?: string;
  atlasUrl?: string;
  artwork?: ArtworkInfo;
}

/** Payload público de PET_ARTWORK_READY. */
export interface PetArtworkReadyEvent {
  address: string;
  eventType: 'PET_ARTWORK_READY';
  occurredAt: string;
  presentation: 'EGG' | 'CREATURE';
  artworkVersion: number;
  atlasUrl: string;
}

export const SPRITE_FRAME_PX = 32;
export const SPRITE_ATLAS_COLUMNS = 4;

/** Índice da pose no atlas (ordem ART-03). */
export function spritePoseIndex(pose: SpritePoseName): number {
  const order: SpritePoseName[] = [
    'idle',
    'look',
    'smile',
    'feeding',
    'celebration',
    'thinking',
    'upset',
    'hungry',
    'critical',
    'hibernation',
    'sleep',
    'birth',
    'return',
    'reduced_motion',
  ];
  return order.indexOf(pose);
}

/** Mapeia estado emocional → pose do atlas. */
export function poseForPetState(state: PetEmotionalState | undefined): SpritePoseName {
  switch (state) {
    case 'PENSANDO':
      return 'thinking';
    case 'CHATEADO':
      return 'upset';
    case 'FAMINTO':
      return 'hungry';
    case 'CRITICO':
      return 'critical';
    case 'HIBERNANDO':
      return 'hibernation';
    case 'ALIMENTADO':
    default:
      return 'idle';
  }
}
