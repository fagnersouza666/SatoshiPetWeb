import ngswConfig from '../../ngsw-config.json';

describe('ngsw-config', () => {
  it('cacheia sprites aprovados com cacheFirst', () => {
    const artworkGroup = ngswConfig.dataGroups.find((group) => group.name === 'pet-artwork');
    expect(artworkGroup).toBeDefined();
    expect(artworkGroup?.cacheConfig.strategy).toBe('cacheFirst');
    expect(artworkGroup?.urls).toContain('/api/v1/public/addresses/*/artwork/**');
  });
});
