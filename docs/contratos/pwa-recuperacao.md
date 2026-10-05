# Recuperação de acesso na PWA

Referências: CC-02, CA-006. A API documenta os endpoints no contrato de conta.

Na página autenticada **Minha conta**, a seção **Código de recuperação** permite
emitir o primeiro código ou substituir o anterior com uma ação explícita.
`POST /v1/account/recovery/code` usa cookie, CSRF e `no-store`; não é chamado por
carregamentos ou leituras. A emissão fica desabilitada offline.

O resultado `{code}` aparece somente em memória e pode ser copiado ou baixado
por comando do usuário. A tela orienta guardar o segredo fora do aplicativo e
informa que cada emissão invalida o código anterior. Logout, troca de identidade
e saída da tela removem o segredo; respostas pendentes de outra sessão são
descartadas. Não há armazenamento automático em storage, cache ou logs. Ao
repetir a emissão, a tela remove o código anterior inclusive se a resposta falhar,
pois o servidor pode já ter concluído a substituição.

1. Em `/recuperar`, informar código de recuperação e novo e-mail. A PWA envia
   `POST /v1/account/recovery/email` com `{code,email}`; aguarda verificação.
2. O link dedicado chega a `/recuperar?token=...`. O token é removido da URL e
   mantido somente em memória. Informar novamente o código de recuperação,
   inclusive quando o link é aberto em outra aba.
3. Enviar `POST /v1/account/recovery/reset` com `{code,token}`. O servidor verifica
   ambos, substitui o e-mail, revoga sessões e emite novo cookie, CSRF e
   `recoveryCode`. Nenhum segredo é persistido em storage pelo cliente.
4. O novo código é exibido antes de navegar. O usuário guarda o código e seleciona
   **Guardei meu novo código; abrir conta**. Só então a PWA consulta `/account/me`
   e abre a conta. Se essa consulta falhar, o código continua disponível e a
   repetição não consome novamente os segredos antigos.

Erros de rede não são tratados como código inválido. Cliques repetidos durante
requisição não reenviam a operação. Dados privados e identidade não têm fallback
no SW; recuperação não depende de cache de conta anterior.
