import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnChanges,
  SimpleChanges,
  ViewChild,
  inject,
  input,
  signal,
} from '@angular/core';
import {
  PetEmotionalState,
  SPRITE_ATLAS_COLUMNS,
  SPRITE_FRAME_PX,
  SpritePoseName,
  poseForPetState,
  spritePoseIndex,
} from '../../core/models/pet-art.model';

/**
 * Renderiza ovo estático ou sprite recortado do atlas aprovado (ART-06, PWA-09).
 * Respeita prefers-reduced-motion mapeando para a pose REDUCED_MOTION.
 */
@Component({
  selector: 'app-pet-sprite',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="pet-sprite" [attr.aria-label]="ariaLabel()">
      @if (presentation() === 'EGG' || !atlasUrl()) {
        <img
          class="pet-sprite__egg"
          src="/assets/egg.svg"
          width="128"
          height="128"
          alt=""
          aria-hidden="true"
        />
      } @else {
        <canvas
          #canvas
          class="pet-sprite__canvas"
          width="128"
          height="128"
          aria-hidden="true"
        ></canvas>
      }
      <figcaption class="visually-hidden">{{ ariaLabel() }}</figcaption>
    </figure>
  `,
  styles: [
    `
      .pet-sprite {
        margin: 0;
        display: flex;
        justify-content: center;
      }

      .pet-sprite__egg,
      .pet-sprite__canvas {
        image-rendering: pixelated;
        image-rendering: crisp-edges;
        width: 8rem;
        height: 8rem;
      }

      .visually-hidden {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
      }
    `,
  ],
})
export class PetSpriteComponent implements OnChanges {
  private readonly destroyRef = inject(DestroyRef);

  readonly presentation = input<'EGG' | 'CREATURE' | undefined>('EGG');
  readonly petState = input<PetEmotionalState | undefined>(undefined);
  readonly atlasUrl = input<string | undefined>(undefined);
  readonly petName = input<string | undefined>(undefined);

  protected readonly ariaLabel = signal('Pet em ovo');

  @ViewChild('canvas') private canvasRef?: ElementRef<HTMLCanvasElement>;

  private atlasImage: HTMLImageElement | null = null;
  private reducedMotion = false;
  private mediaQuery: MediaQueryList | null = null;

  constructor() {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reducedMotion = this.mediaQuery.matches;
      const listener = () => {
        this.reducedMotion = this.mediaQuery?.matches ?? false;
        this.renderSprite();
      };
      this.mediaQuery.addEventListener('change', listener);
      this.destroyRef.onDestroy(() => this.mediaQuery?.removeEventListener('change', listener));
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.updateAriaLabel();
    if (changes['atlasUrl'] || changes['presentation'] || changes['petState']) {
      void this.loadAndRender();
    }
  }

  private updateAriaLabel(): void {
    const name = this.petName() ?? 'Pet';
    if (this.presentation() === 'EGG' || !this.atlasUrl()) {
      this.ariaLabel.set(`${name} em ovo`);
      return;
    }
    const state = this.petState();
    this.ariaLabel.set(state ? `${name}, estado ${state}` : `${name} como criatura`);
  }

  private async loadAndRender(): Promise<void> {
    const url = this.atlasUrl();
    if (this.presentation() !== 'CREATURE' || !url) {
      this.atlasImage = null;
      return;
    }

    const image = new Image();
    image.decoding = 'async';
    image.src = url;
    if (typeof image.decode === 'function') {
      await image.decode().catch(() => undefined);
    } else {
      await new Promise<void>((resolve) => {
        image.onload = () => resolve();
        image.onerror = () => resolve();
      });
    }
    if (image.naturalWidth === 0) {
      return;
    }
    this.atlasImage = image;
    this.renderSprite();
  }

  private renderSprite(): void {
    const canvas = this.canvasRef?.nativeElement;
    const atlas = this.atlasImage;
    if (!canvas || !atlas) {
      return;
    }

    const pose = this.resolvePose();
    const index = spritePoseIndex(pose);
    const col = index % SPRITE_ATLAS_COLUMNS;
    const row = Math.floor(index / SPRITE_ATLAS_COLUMNS);
    const sx = col * SPRITE_FRAME_PX;
    const sy = row * SPRITE_FRAME_PX;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(
      atlas,
      sx,
      sy,
      SPRITE_FRAME_PX,
      SPRITE_FRAME_PX,
      0,
      0,
      canvas.width,
      canvas.height,
    );
  }

  private resolvePose(): SpritePoseName {
    if (this.reducedMotion) {
      return 'reduced_motion';
    }
    return poseForPetState(this.petState());
  }
}
