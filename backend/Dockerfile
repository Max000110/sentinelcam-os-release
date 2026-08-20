# Build stage - compile native extensions
FROM python:3.11-slim AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt email-validator

# Runtime stage - clean, minimal image
FROM python:3.11-slim AS runtime

WORKDIR /app

# Copy pre-built Python packages from builder
COPY --from=builder /install /usr/local

# Create non-root user
RUN useradd -m -r appuser && \
    mkdir -p /app/uploads/recordings /app/uploads/snapshots && \
    chown -R appuser:appuser /app

COPY . .

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/api/v1/health')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips=*"]
