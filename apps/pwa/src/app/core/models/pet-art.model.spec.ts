import { poseForPetState, spritePoseIndex } from './pet-art.model';

describe('pet-art.model', () => {
  it('mapeia estados emocionais para poses', () => {
    expect(poseForPetState('PENSANDO')).toBe('thinking');
    expect(poseForPetState('CRITICO')).toBe('critical');
    expect(poseForPetState(undefined)).toBe('idle');
  });

  it('ordena poses do atlas', () => {
    expect(spritePoseIndex('reduced_motion')).toBe(13);
    expect(spritePoseIndex('idle')).toBe(0);
  });
});
