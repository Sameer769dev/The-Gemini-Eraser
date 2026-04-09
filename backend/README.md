---
title: Gemini Eraser AI Backend
emoji: ✦
colorFrom: purple
colorTo: cyan
sdk: docker
app_port: 7860
pinned: true
license: mit
short_description: Production AI inpainting API using LaMa for watermark removal
---

# ✦ Gemini Eraser — AI Inpainting Backend

A production-grade REST API that uses the **LaMa (Large Mask Inpainting)** neural network to remove watermarks, objects, and unwanted elements from images — with Photoshop-quality results and zero artifacts.

## 🚀 API Usage

### `POST /inpaint`

Send a multipart form with two files:

| Field | Type | Description |
|-------|------|-------------|
| `image` | `image/png` or `image/jpeg` | The original photo |
| `mask` | `image/png` | Black & white mask — **white pixels = area to erase** |

**Returns:** `image/png` — The inpainted result.

### `GET /health`

Returns `{"status": "healthy"}` when the model is loaded and ready.

### `GET /docs`

Interactive Swagger UI to test the API directly in your browser.

## 🛠️ Example (curl)

```bash
curl -X POST "https://your-username-gemini-eraser-ai.hf.space/inpaint" \
  -F "image=@photo.png" \
  -F "mask=@mask.png" \
  --output result.png
```

## 🏗️ Architecture

- **Framework**: FastAPI + Uvicorn
- **AI Model**: LaMa (Large Mask Inpainting) via ONNX Runtime
- **Image Processing**: Pillow + OpenCV (headless)
- **Container**: Python 3.11-slim Docker image
