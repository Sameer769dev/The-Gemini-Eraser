import os
import io
import time
import logging
import asyncio
from contextlib import asynccontextmanager
from concurrent.futures import ThreadPoolExecutor

import cv2
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, JSONResponse
from PIL import Image, ImageFilter
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

# FSRCNN DNN super-resolution models
# Scale 2 → used for "High" PRO tier (~1-2s on CPU)
# Scale 4 → used for "Max" PRO tier (~3-5s on CPU)
superres_x2: cv2.dnn_superres.DnnSuperResImpl | None = None
superres_x4: cv2.dnn_superres.DnnSuperResImpl | None = None

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
# FSRCNN model paths (baked into Docker image)
# ─────────────────────────────────────────────
FSRCNN_X2_PATH = os.path.join(os.path.dirname(__file__), "FSRCNN_x2.pb")
FSRCNN_X4_PATH = os.path.join(os.path.dirname(__file__), "FSRCNN_x4.pb")

# Maximum dimension to feed into FSRCNN before upscaling.
# Keeping input ≤ this ensures fast CPU inference (1-3s).
# After upscaling x2, output max-edge = UPSCALE_INPUT_MAX * 2
UPSCALE_INPUT_MAX = 720


def _load_superres() -> None:
    """Load FSRCNN super-res models. Called once at startup."""
    global superres_x2, superres_x4

    for scale, path, var_name in [(2, FSRCNN_X2_PATH, "superres_x2"), (4, FSRCNN_X4_PATH, "superres_x4")]:
        if not os.path.exists(path):
            logger.warning(f"FSRCNN x{scale} model not found at {path} — upscaling at x{scale} disabled.")
            continue
        try:
            sr = cv2.dnn_superres.DnnSuperResImpl_create()
            sr.readModel(path)
            sr.setModel("fsrcnn", scale)
            if scale == 2:
                globals()["superres_x2"] = sr
            else:
                globals()["superres_x4"] = sr
            logger.info(f"✅ FSRCNN x{scale} loaded.")
        except Exception as e:
            logger.error(f"Failed to load FSRCNN x{scale}: {e}")


# ─────────────────────────────────────────────
# Startup: pre-load all models once
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

    logger.info("Loading FSRCNN upscaling models…")
    _load_superres()

    logger.info("✅ All models ready.")
    yield
    logger.info("Shutting down.")

# ─────────────────────────────────────────────
# App
# ─────────────────────────────────────────────
app = FastAPI(
    title="Gemini Eraser AI Backend",
    description="High-performance AI inpainting + upscaling powered by LaMa & FSRCNN.",
    version="3.0.0",
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


def _run_superres(src: Image.Image, scale: int) -> Image.Image:
    """
    Run FSRCNN AI super-resolution on a PIL image.

    Strategy:
      1. Downscale input so max-edge ≤ UPSCALE_INPUT_MAX px (ensures fast CPU inference)
      2. Convert PIL → OpenCV BGR array
      3. Run FSRCNN upscale (scale×)
      4. Convert back to PIL RGB
      5. Apply subtle UnsharpMask for extra crispness
    """
    sr = superres_x2 if scale == 2 else superres_x4
    if sr is None:
        # Fallback to high-quality LANCZOS + sharpening if model unavailable
        logger.warning(f"FSRCNN x{scale} not available, falling back to LANCZOS+sharpen")
        w, h = src.size
        upscaled = src.resize((w * scale, h * scale), Image.LANCZOS)
        return upscaled.filter(ImageFilter.UnsharpMask(radius=1.5, percent=150, threshold=2))

    # 1. Fit input to manageable size
    src_fit = _fit(src, UPSCALE_INPUT_MAX)
    logger.info(f"  FSRCNN input size: {src_fit.size} → x{scale}")

    # 2. PIL (RGB) → OpenCV (BGR)
    img_np = np.array(src_fit.convert("RGB"))
    img_bgr = cv2.cvtColor(img_np, cv2.COLOR_RGB2BGR)

    # 3. AI upscale
    upscaled_bgr = sr.upsample(img_bgr)

    # 4. OpenCV BGR → PIL RGB
    upscaled_rgb = cv2.cvtColor(upscaled_bgr, cv2.COLOR_BGR2RGB)
    result_pil = Image.fromarray(upscaled_rgb)

    # 5. Post-process: subtle sharpening to enhance perceived detail
    # UnsharpMask: radius=1.2 (tight halos), percent=140 (moderate boost), threshold=3 (don't sharpen noise)
    result_pil = result_pil.filter(ImageFilter.UnsharpMask(radius=1.2, percent=140, threshold=3))

    logger.info(f"  FSRCNN output size: {result_pil.size}")
    return result_pil


# ─────────────────────────────────────────────
# Routes
# ─────────────────────────────────────────────

@app.get("/", tags=["Health"])
async def root():
    return JSONResponse({
        "status": "ok",
        "model": "lama+fsrcnn",
        "ready": lama_model is not None,
        "upscale_x2": superres_x2 is not None,
        "upscale_x4": superres_x4 is not None,
    })


@app.get("/health", tags=["Health"])
async def health():
    if lama_model is None or fastsam_model is None:
        raise HTTPException(status_code=503, detail="Models not yet loaded")
    return JSONResponse({
        "status": "healthy",
        "upscale_x2": superres_x2 is not None,
        "upscale_x4": superres_x4 is not None,
    })


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


@app.post("/upscale", tags=["Upscale"])
async def upscale_image(
    image: UploadFile = File(...),
    scale: str = Form("2"),
    premium: str = Form("false"),
):
    """
    AI Super-Resolution using FSRCNN — PRO feature only.

    - scale=2 → 2× upscale (720p → 1440p) — "High" PRO tier, ~1-3s CPU
    - scale=4 → 4× upscale (720p → 2880p) — "Max"  PRO tier, ~4-8s CPU

    Returns a lossless PNG with sharp, AI-enhanced detail.
    The FSRCNN model synthesizes realistic texture rather than blurring like LANCZOS.
    """
    is_premium = premium.lower() in ("true", "1", "yes")

    if not is_premium:
        raise HTTPException(
            status_code=403,
            detail="AI upscaling is a PRO feature. Upgrade to access it."
        )

    if image.content_type not in ("image/png", "image/jpeg", "image/jpg", "application/octet-stream"):
        raise HTTPException(status_code=415, detail="Unsupported image format")

    scale_int = int(scale) if scale in ("2", "4") else 2

    t0 = time.perf_counter()
    try:
        img_data = await image.read()
        src = Image.open(io.BytesIO(img_data)).convert("RGB")

        logger.info(f"Upscaling {src.size} × x{scale_int} (premium={is_premium})")

        # Run super-res in thread pool (CPU-bound)
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            _executor, _run_superres, src, scale_int
        )

        # Return lossless PNG — upscaled premium image deserves no compression loss
        output_bytes = _encode_png_fast(result)

        elapsed = time.perf_counter() - t0
        logger.info(
            f"Upscaling done in {elapsed:.2f}s | "
            f"{src.size} → {result.size} | {len(output_bytes)//1024}KB"
        )

        return Response(
            content=output_bytes,
            media_type="image/png",
            headers={
                "X-Model": "fsrcnn",
                "X-Scale": str(scale_int),
                "X-Time": f"{elapsed:.2f}",
                "X-Output-Width": str(result.width),
                "X-Output-Height": str(result.height),
            },
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Upscaling failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Upscaling error: {str(e)}")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 7860))
    uvicorn.run("main:app", host="0.0.0.0", port=port, log_level="info")
