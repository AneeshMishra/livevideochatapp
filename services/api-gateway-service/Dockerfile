FROM golang:1.22-alpine AS builder
WORKDIR /app

ENV GONOSUMDB=*
ENV GONOSUMCHECK=*
ENV GOFLAGS=-mod=mod

COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -trimpath -ldflags="-s -w" -o api-gateway-service ./cmd/server

FROM alpine:3.20
RUN apk --no-cache add ca-certificates tzdata
WORKDIR /app
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup
COPY --from=builder /app/api-gateway-service .
USER appuser
EXPOSE 8000
ENTRYPOINT ["./api-gateway-service"]
