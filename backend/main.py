import os
import io
import time
import logging
import asyncio
from contextlib import asynccontextmanager
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, JSONResponse
from PIL import Image
from simple_lama_inpainting import SimpleLama
from ultralytics import FastSAM
import numpy as np
import uvicorn

# ─────────────────────────────────────────────
# Logging
# ─────────────────────────────────────────────
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────
# Global model cache + thread pool
# ─────────────────────────────────────────────
lama_model: SimpleLama | None = None
fastsam_model: FastSAM | None = None

# LaMa is CPU-bound. A dedicated thread pool keeps it OFF the async event loop
# so FastAPI can still accept new requests while inference is running.
_executor = ThreadPoolExecutor(max_workers=2)

# ─────────────────────────────────────────────
# Maximum edge-length before server downscales.
# Smaller = faster inference. LaMa is fully
# resolution-agnostic so quality is retained.
# ─────────────────────────────────────────────
FREE_MAX_PX    = 720   # free users  — fast (was unlimited, now capped)
PREMIUM_MAX_PX = 1280  # premium     — high quality but still bounded

# ─────────────────────────────────────────────
# Startup: pre-load both models once
# ─────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    global lama_model, fastsam_model
    logger.info("Loading FastSAM model…")
    try:
        fastsam_model = FastSAM("FastSAM-s.pt")
    except Exception as e:
        logger.error(f"FastSAM load failed: {e}")

    logger.info("Loading LaMa model…")
    lama_model = SimpleLama()
    logger.info("✅ Models ready.")
    yield
    logger.info("Shutting down.")

# ─────────────────────────────────────────────
# App
# ─────────────────────────────────────────────
app = FastAPI(
    title="Gemini Eraser AI Backend",
    description="High-performance AI inpainting powered by LaMa.",
    version="2.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# ─────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────

def _fit(img: Image.Image, max_px: int) -> Image.Image:
    """Downscale so the longest edge ≤ max_px. Upscaling is never done."""
    w, h = img.size
    if max(w, h) <= max_px:
        return img
    ratio = max_px / max(w, h)
    # Ensure even dimensions for codec compatibility
    nw = max(2, round(w * ratio) & ~1)
    nh = max(2, round(h * ratio) & ~1)
    return img.resize((nw, nh), Image.LANCZOS)


def _encode_jpeg(img: Image.Image, quality: int = 88) -> bytes:
    """Encode PIL image to JPEG bytes quickly (no PNG overhead)."""
    buf = io.BytesIO()
    img.convert("RGB").save(buf, format="JPEG", quality=quality, optimize=False, subsampling=2)
    return buf.getvalue()


def _encode_png_fast(img: Image.Image) -> bytes:
    """Encode with compress_level=1 (fastest, ~10× smaller than raw)."""
    buf = io.BytesIO()
    img.save(buf, format="PNG", compress_level=1, optimize=False)
    return buf.getvalue()


def _run_lama(src: Image.Image, msk: Image.Image) -> Image.Image:
    """Synchronous LaMa call — runs inside the thread pool."""
    return lama_model(src, msk)


def _run_fastsam(src: Image.Image, px: int, py: int) -> Image.Image | None:
    """Synchronous FastSAM call — runs inside the thread pool."""
    results = fastsam_model(
        src,
        imgsz=512,           # ↓ from 640 → faster inference
        conf=0.3,
        iou=0.7,
        points=[[px, py]],
        labels=[1],
        retina_masks=False,  # skip high-res mask upsampling
    )
    if not results or results[0].masks is None:
        return None
    mask_array = results[0].masks.data[0].cpu().numpy()
    mask_img = Image.fromarray((mask_array * 255).astype(np.uint8), mode="L")
    if mask_img.size != src.size:
        mask_img = mask_img.resize(src.size, Image.NEAREST)
    return mask_img


# ─────────────────────────────────────────────
# Routes
# ─────────────────────────────────────────────

@app.get("/", tags=["Health"])
async def root():
    return JSONResponse({"status": "ok", "model": "lama", "ready": lama_model is not None})


@app.get("/health", tags=["Health"])
async def health():
    if lama_model is None or fastsam_model is None:
        raise HTTPException(status_code=503, detail="Models not yet loaded")
    return JSONResponse({"status": "healthy"})


@app.post("/segment", tags=["Segmentation"])
async def segment_image(
    image: UploadFile = File(...),
    normX: float = Form(0.5),
    normY: float = Form(0.5),
):
    """
    Point-tap segmentation via FastSAM.
    Returns a grayscale PNG mask (white = selected object).
    """
    if fastsam_model is None:
        raise HTTPException(status_code=503, detail="FastSAM not loaded")

    if image.content_type not in ("image/png", "image/jpeg", "image/jpg", "application/octet-stream"):
        raise HTTPException(status_code=415, detail="Unsupported image format")

    t0 = time.perf_counter()
    try:
        img_data = await image.read()
        src = Image.open(io.BytesIO(img_data)).convert("RGB")

        # Segmentation never needs full-res; 512 is plenty for FastSAM
        src_small = _fit(src, 512)
        px = int(normX * src_small.width)
        py = int(normY * src_small.height)

        logger.info(f"Segmenting @ ({px},{py}) on {src_small.size}")

        loop = asyncio.get_event_loop()
        mask_img = await loop.run_in_executor(_executor, _run_fastsam, src_small, px, py)

        if mask_img is None:
            # Nothing detected — return all-black mask
            mask_img = Image.new("L", src.size, 0)
        elif mask_img.size != src.size:
            # Scale mask back to original size
            mask_img = mask_img.resize(src.size, Image.NEAREST)

        # Fast PNG for masks (binary, compresses instantly)
        output_bytes = _encode_png_fast(mask_img)

        logger.info(f"Segmentation done in {time.perf_counter()-t0:.2f}s, {len(output_bytes)//1024}KB")
        return Response(content=output_bytes, media_type="image/png", headers={"X-Model": "fastsam"})

    except Exception as e:
        logger.error(f"Segmentation failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Segmentation error: {str(e)}")


@app.post("/inpaint", tags=["Inpaint"])
async def inpaint(
    image: UploadFile = File(...),
    mask:  UploadFile = File(...),
    premium: str = Form("false"),
):
    """
    Generative object removal with LaMa.
    premium=true → up to 1280px, JPEG 92 response.
    premium=false → capped at 720px, JPEG 85 response.
    """
    if lama_model is None:
        raise HTTPException(status_code=503, detail="Model not loaded, retry in a moment")

    for upload, name in [(image, "image"), (mask, "mask")]:
        if upload.content_type not in ("image/png", "image/jpeg", "image/jpg", "application/octet-stream"):
            raise HTTPException(status_code=415, detail=f"Unsupported type for '{name}'")

    is_premium = premium.lower() in ("true", "1", "yes")
    max_px     = PREMIUM_MAX_PX if is_premium else FREE_MAX_PX
    resp_q     = 92 if is_premium else 85

    t0 = time.perf_counter()
    try:
        img_data, mask_data = await asyncio.gather(image.read(), mask.read())

        src = Image.open(io.BytesIO(img_data)).convert("RGB")
        msk = Image.open(io.BytesIO(mask_data)).convert("L")

        # ── Server-side downscale ──────────────────────────────────────────────
        orig_size = src.size
        src = _fit(src, max_px)
        if msk.size != src.size:
            msk = msk.resize(src.size, Image.NEAREST)

        logger.info(f"Inpainting {orig_size}→{src.size} (premium={is_premium})")

        # ── Run LaMa off the async event loop ─────────────────────────────────
        loop   = asyncio.get_event_loop()
        result = await loop.run_in_executor(_executor, _run_lama, src, msk)

        # ── Encode response as JPEG — ~5–10× smaller than PNG, visually lossless
        output_bytes = _encode_jpeg(result, quality=resp_q)

        elapsed = time.perf_counter() - t0
        logger.info(f"Inpainting done in {elapsed:.2f}s, {len(output_bytes)//1024}KB")
        return Response(
            content=output_bytes,
            media_type="image/jpeg",
            headers={"X-Model": "lama", "X-Time": f"{elapsed:.2f}"},
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Inpainting failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Inpainting error: {str(e)}")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 7860))
    uvicorn.run("main:app", host="0.0.0.0", port=port, log_level="info")
