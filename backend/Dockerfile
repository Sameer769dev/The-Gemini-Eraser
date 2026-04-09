FROM python:3.11-slim

# ─── System dependencies ───────────────────────────────────────────────────────
# libglib2.0-0 is required by opencv-python-headless on Linux
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    git \
    && rm -rf /var/lib/apt/lists/*

# ─── Hugging Face Spaces requires all files owned by user 1000 ────────────────
RUN useradd -m -u 1000 appuser

WORKDIR /app

# Copy and install Python dependencies first (layer caching optimization)
COPY --chown=appuser:appuser requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY --chown=appuser:appuser . .

# ─── Pre-download the LaMa model weights into the image ───────────────────────
# This bakes the ~200MB model into the Docker image so it cold-starts instantly
# on every Hugging Face Space restart without re-downloading.
RUN python -c "from simple_lama_inpainting import SimpleLama; print('Model download complete:', SimpleLama())"

# Switch to non-root user (required by Hugging Face Spaces)
USER 1000

# Hugging Face Spaces expects port 7860
ENV PORT=7860
EXPOSE 7860

# Start the server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "7860", "--workers", "1"]
