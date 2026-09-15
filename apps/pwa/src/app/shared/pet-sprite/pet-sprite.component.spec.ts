import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PetSpriteComponent } from './pet-sprite.component';

describe('PetSpriteComponent', () => {
  let fixture: ComponentFixture<PetSpriteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PetSpriteComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PetSpriteComponent);
  });

  it('renderiza ovo quando apresentação é EGG', () => {
    fixture.componentRef.setInput('presentation', 'EGG');
    fixture.componentRef.setInput('petName', 'Pixel');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img[src="/assets/egg.svg"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('canvas')).toBeNull();
  });

  it('reserva canvas para criatura com atlas', () => {
    fixture.componentRef.setInput('presentation', 'CREATURE');
    fixture.componentRef.setInput('atlasUrl', '/api/v1/public/addresses/x/artwork/1/atlas.png');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('canvas')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });
});
