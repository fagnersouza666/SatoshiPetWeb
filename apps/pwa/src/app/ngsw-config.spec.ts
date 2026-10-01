import ngswConfig from '../../ngsw-config.json';

describe('ngsw-config', () => {
  it('cacheia sprites aprovados com performance', () => {
    const artworkGroup = ngswConfig.dataGroups.find((group) => group.name === 'pet-artwork');
    expect(artworkGroup).toBeDefined();
    expect(artworkGroup?.cacheConfig.strategy).toBe('performance');
    expect(artworkGroup?.urls).toContain('/api/v1/public/addresses/*/artwork/**');
  });
});
