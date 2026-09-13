# Content-Security-Policy da PWA

## Objetivo

O Satoshi Pet Web entrega a política `Content-Security-Policy` nas respostas
da PWA. A mesma política é configurada no Caddy, no nginx da imagem da PWA e
no filtro de respostas da API. O Caddy é a borda autoritativa em staging e
produção; nginx e API mantêm a proteção quando acessados diretamente.

## Política vigente

```text
default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; media-src 'self'; connect-src 'self' wss:; worker-src 'self'; manifest-src 'self'; object-src 'none'; frame-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
```

| Diretiva | Permissão | Motivo |
| --- | --- | --- |
| `default-src` | `'self'` | Fecha recursos não listados para a origem da PWA. |
| `script-src` | `'self'` | Permite apenas bundles compilados; não habilita scripts inline ou `unsafe-eval`. |
| `style-src` | `'self' 'unsafe-inline'` | Compatível com estilos de componentes Angular injetados em `<style>`. |
| `img-src` | `'self' data: https:` | Inclui ícones, sprites, imagens embutidas e URLs HTTPS assinadas do object storage. |
| `font-src` / `media-src` | `'self'` | Fontes e sons vêm dos assets versionados da PWA. |
| `connect-src` | `'self' wss:` | A API usa o proxy same-origin e eventos em tempo real usam WebSocket seguro. |
| `worker-src` | `'self'` | Permite o `ngsw-worker.js` do Angular; workers externos não são aceitos. |
| `manifest-src` | `'self'` | Restringe o manifesto instalável à PWA publicada. |
| `object-src` / `frame-src` | `'none'` | Desabilita plugins e conteúdo incorporado não necessários. |
| `frame-ancestors` | `'none'` | Impede que a PWA seja embutida por outro site. |
| `base-uri` / `form-action` | `'self'` | Evita alteração da base de navegação e envio de formulários para terceiros. |

Os provedores de mercado, clima, e-mail, IA e Bitcoin são acessados pelo
servidor. Por isso, não devem ser adicionados ao `connect-src` da PWA. Se um
recurso novo precisar de uma origem no navegador, adicione o host HTTPS exato
à diretiva necessária e atualize as configurações e este documento; não
use curingas amplos ou `unsafe-eval`.

## Verificação

```bash
npm run test:csp
docker run --rm -v "$PWD/infra/caddy/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile
docker run --rm -v "$PWD/infra/caddy/Caddyfile.prod:/etc/caddy/Caddyfile:ro" caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile
docker run --rm -v "$PWD/apps/pwa/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.29-alpine nginx -t
```

Com a stack local em execução, confirme o header na borda:

```bash
curl -kI https://localhost/ | grep -i content-security-policy
```

O header deve estar presente no HTML, no `ngsw-worker.js` e nos assets. A
permissão para geolocalização, câmera, microfone e pagamento é controlada
separadamente por `Permissions-Policy`.
