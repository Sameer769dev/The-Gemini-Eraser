import os
import io
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, JSONResponse
from PIL import Image
from simple_lama_inpainting import SimpleLama
import uvicorn

# ─────────────────────────────────────────────
# Logging
# ─────────────────────────────────────────────
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────
# Lazy-load the model once on startup (lifespan)
# ─────────────────────────────────────────────
lama_model: SimpleLama | None = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global lama_model
    logger.info("Loading LaMa model...")
    lama_model = SimpleLama()
    logger.info("LaMa model loaded and ready!")
    yield
    logger.info("Shutting down.")

# ─────────────────────────────────────────────
# App
# ─────────────────────────────────────────────
app = FastAPI(
    title="Gemini Eraser AI Backend",
    description="Production-grade AI inpainting powered by LaMa.",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS — allow all origins so the Android app can always reach this endpoint
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# ─────────────────────────────────────────────
# Routes
# ─────────────────────────────────────────────

@app.get("/", tags=["Health"])
async def root():
    """Quick health check — confirms the server is running."""
    return JSONResponse({"status": "ok", "model": "lama", "ready": lama_model is not None})

@app.get("/health", tags=["Health"])
async def health():
    if lama_model is None:
        raise HTTPException(status_code=503, detail="Model not yet loaded")
    return JSONResponse({"status": "healthy"})

@app.post("/inpaint", tags=["Inpaint"])
async def inpaint(
    image: UploadFile = File(..., description="Original image (PNG/JPG)"),
    mask: UploadFile = File(..., description="B&W mask: white = area to erase"),
):
    """
    Receives a source image and a binary mask.
    Returns the inpainted PNG with the masked region generatively filled.
    """
    if lama_model is None:
        raise HTTPException(status_code=503, detail="Model not yet loaded, please retry in a moment")

    # Validate MIME types
    for upload, name in [(image, "image"), (mask, "mask")]:
        if upload.content_type not in ("image/png", "image/jpeg", "image/jpg", "application/octet-stream"):
            raise HTTPException(status_code=415, detail=f"Unsupported file type for '{name}': {upload.content_type}")

    try:
        img_data  = await image.read()
        mask_data = await mask.read()

        src  = Image.open(io.BytesIO(img_data)).convert("RGB")
        msk  = Image.open(io.BytesIO(mask_data)).convert("L")

        # Ensure mask dimensions match image
        if src.size != msk.size:
            msk = msk.resize(src.size, Image.NEAREST)

        logger.info(f"Processing image {src.size} with mask {msk.size} ...")
        result = lama_model(src, msk)
        logger.info("Inpainting complete.")

        output = io.BytesIO()
        result.save(output, format="PNG", optimize=True)
        return Response(
            content=output.getvalue(),
            media_type="image/png",
            headers={"X-Model": "lama"},
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Inpainting failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Inpainting error: {str(e)}")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 7860))
    uvicorn.run("main:app", host="0.0.0.0", port=port, log_level="info")
