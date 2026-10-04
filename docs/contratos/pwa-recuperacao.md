# Recuperação de acesso na PWA

Referências: CC-02, CA-006. A API documenta os endpoints no contrato de conta.

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
