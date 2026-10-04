# Apresentação da arte na PWA

Referências: ART-05/06, CA-037/038, CC-12.

O painel privado exibe o atlas de prévia como criatura quando a geração está em
`AWAITING_APPROVAL` e a API fornece `previewUrls.atlas`. Essa apresentação é
somente da revisão privada; a URL da prévia usa `ngsw-bypass=true` inclusive
quando uma instalação ainda executa o SW da versão anterior. O painel não altera
`presentation` do pet nem publica a arte.
Sem prévia autorizada, o painel segue a apresentação recebida da API. Um pet
aprovado que voltou ao ovo continua em ovo mesmo que mantenha um atlas permanente.

A página pública continua exibindo ovo até a API informar `CREATURE` e disponibilizar
arte aprovada. Eventos públicos e snapshots invalidam a leitura HTTP, permitindo
acompanhar mudanças de energia, saldo e apresentação sem recarregar a página.
