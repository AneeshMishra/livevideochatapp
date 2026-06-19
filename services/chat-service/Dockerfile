FROM golang:1.22-alpine AS builder
WORKDIR /app

# Allow downloading modules without a pre-committed go.sum (run `go mod tidy` locally for production)
ENV GONOSUMDB=*
ENV GONOSUMCHECK=*
ENV GOFLAGS=-mod=mod

COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -trimpath -ldflags="-s -w" -o chat-service ./cmd/server

FROM alpine:3.20
RUN apk --no-cache add ca-certificates tzdata
WORKDIR /app
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup
COPY --from=builder /app/chat-service .
USER appuser
EXPOSE 8086
ENTRYPOINT ["./chat-service"]
