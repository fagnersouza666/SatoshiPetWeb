# Receita exclusiva dos testes: mesma release MinIO, compilada do fonte oficial.
# O digest fixa o compilador; o SHA fixa o código e também é conferido após o fetch.
FROM golang:1.24.6-bookworm@sha256:ab1d1823abb55a9504d2e3e003b75b36dbeb1cbcc4c92593d85a84ee46becc6c AS build

ENV CGO_ENABLED=0 GOTOOLCHAIN=local
WORKDIR /src

RUN git init . \
    && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth=1 origin 07c3a429bfed433e49018cb0f78a52145d4bedeb \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "07c3a429bfed433e49018cb0f78a52145d4bedeb"

# Camadas separadas permitem reaproveitar download/compilação no cache do Docker.
RUN go mod download && go mod verify \
    && git diff --exit-code -- go.mod go.sum
RUN ldflags="$(MINIO_RELEASE=RELEASE go run buildscripts/gen-ldflags.go)" \
    && go build -mod=readonly -trimpath -ldflags="$ldflags" \
    -o /out/minio .

# Equivalente ao Dockerfile.scratch do upstream, com entrypoint explícito para os testes.
FROM scratch
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="07c3a429bfed433e49018cb0f78a52145d4bedeb" \
      org.opencontainers.image.version="RELEASE.2025-09-07T16-13-09Z"
COPY --from=build /out/minio /minio
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /src/LICENSE /licenses/LICENSE
COPY --from=build /src/CREDITS /licenses/CREDITS
EXPOSE 9000
ENTRYPOINT ["/minio"]
CMD ["server", "/data"]
