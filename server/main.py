import glob
import logging
import os
import shutil
import threading
import time
import uuid
from typing import Optional

import yt_dlp
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("videodl")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
TMP_DIR = os.path.join(BASE_DIR, "tmp")
CACHE_DIR = os.path.join(BASE_DIR, "cache")
os.makedirs(TMP_DIR, exist_ok=True)
os.makedirs(CACHE_DIR, exist_ok=True)

AUDIO_FORMATS = ["mp3", "wav", "flac", "m4a", "opus"]

app = FastAPI(title="Video Downloader API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class DownloadRequest(BaseModel):
    url: str
    format_id: Optional[str] = None   # formato de video a usar
    audio_format: Optional[str] = None  # mp3, wav, flac, m4a, opus


class Job:
    def __init__(self, job_id, request):
        self.id = job_id
        self.url = request.url
        self.format_id = request.format_id
        self.audio_format = request.audio_format
        self.status = "queued"        # queued | downloading | converting | done | error
        self.progress = 0             # 0-100
        self.filename = None
        self.filesize = 0
        self.error = None
        self._thread = None

    def to_dict(self, include_filename=False):
        d = {
            "id": self.id,
            "status": self.status,
            "progress": round(self.progress, 1),
        }
        if self.error:
            d["error"] = self.error
        if include_filename and self.filename:
            d["filename"] = self.filename
            d["filesize"] = self.filesize
        return d


JOBS: dict[str, Job] = {}


def _find_result_files(job_id: str):
    return sorted(
        glob.glob(os.path.join(TMP_DIR, job_id) + ".*"),
        key=os.path.getmtime,
        reverse=True,
    )


def _build_format_strategies(job: Job) -> list[str]:
    """Estrategias de formato, en orden de preferencia, para reintentar si una falla."""
    if job.audio_format:
        # para audio prefiere bestaudio, y si el extractor solo da video/muxed, cae a best
        return ["bestaudio/best", "best"]
    if job.format_id:
        return [f"{job.format_id}+bestaudio/best", f"{job.format_id}", "bestvideo+bestaudio/best", "best"]
    return ["bestvideo+bestaudio/best", "best"]


def _build_opts(job: Job, format_string: str) -> dict:
    opts = {
        "outtmpl": os.path.join(TMP_DIR, job.id) + ".%(ext)s",
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "retries": 3,
        "socket_timeout": 30,
        "nocheckcertificate": True,
        "format": format_string,
    }

    if job.audio_format:
        codec = job.audio_format
        pp = {"key": "FFmpegExtractAudio", "preferredcodec": codec}
        if codec in ("mp3", "m4a"):
            pp["preferredquality"] = "320"
        opts["postprocessors"] = [pp]
    else:
        opts["merge_output_format"] = "mp4"

    return opts


def _find_final_file(job_id: str, audio_format: Optional[str]):
    files = _find_result_files(job_id)
    candidates = [p for p in files if not p.endswith(".part") and os.path.getsize(p) > 0]
    if not candidates:
        return None
    if audio_format:
        for ext in (audio_format, "mp4", "m4a", "webm"):
            for p in candidates:
                if p.endswith("." + ext):
                    return p
        return candidates[0]
    return max(candidates, key=os.path.getsize)


def _run_job(job: Job):
    def progress_hook(d):
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate")
            downloaded = d.get("downloaded_bytes") or 0
            if total:
                job.progress = min(93, downloaded / total * 93)
            job.status = "downloading"
        elif d.get("status") == "finished":
            job.status = "converting"
            job.progress = 94

    try:
        job.status = "downloading"
        strategies = _build_format_strategies(job)
        last_error = None
        for attempt, fmt in enumerate(strategies):
            if job.status in ("done", "error") and attempt > 0:
                break
            try:
                for leftover in glob.glob(os.path.join(TMP_DIR, job.id) + ".*"):
                    try:
                        os.remove(leftover)
                    except OSError:
                        pass
                opts = _build_opts(job, fmt)
                opts["progress_hooks"] = [progress_hook]
                with yt_dlp.YoutubeDL(opts) as ydl:
                    ydl.download([job.url])
                last_error = None
                break
            except Exception as e:  # noqa: BLE001
                last_error = e
                log.warning("Estrategia de formato %r falló: %s", fmt, e)

        if last_error is not None:
            raise last_error

        job.status = "converting"
        job.progress = 97

        final = _find_final_file(job.id, job.audio_format)
        if not final:
            raise RuntimeError("No se encontró el archivo descargado/contenido esperado.")

        dest = os.path.join(CACHE_DIR, os.path.basename(final))
        try:
            os.replace(final, dest)
        except OSError:
            shutil.copy2(final, dest)
            os.remove(final)

        for leftover in glob.glob(os.path.join(TMP_DIR, job.id) + ".*"):
            try:
                if leftover != dest and os.path.isfile(leftover):
                    os.remove(leftover)
            except OSError:
                pass

        job.filename = os.path.basename(dest)
        job.filesize = os.path.getsize(dest)
        job.status = "done"
        job.progress = 100
    except Exception as e:
        log.exception("Job %s falló", job.id)
        job.status = "error"
        job.error = str(e)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/api/formats")
def get_formats(url: str):
    if not url:
        raise HTTPException(status_code=400, detail="Falta la URL")
    try:
        opts = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "socket_timeout": 30,
            "nocheckcertificate": True,
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
    except Exception as e:
        log.exception("Error extrayendo formatos")
        raise HTTPException(status_code=422, detail=f"No se pudo analizar la URL: {e}")

    if not info:
        raise HTTPException(status_code=422, detail="No se encontró contenido en la URL")

    formats = info.get("formats") or []

    # videos progresivos (con audio incluido) y por calidades DASH
    best_by_quality = {}
    for f in formats:
        vcodec = f.get("vcodec") or "none"
        if vcodec == "none":
            continue
        height = f.get("height") or 0
        ext = f.get("ext") or "mp4"
        if ext not in ("mp4", "webm", "mkv"):
            continue
        score = height * 10
        if f.get("acodec") and f["acodec"] != "none":
            score += 5  # preferir progresivos
        key = height
        if key not in best_by_quality or score > best_by_quality[key]["_score"]:
            best_by_quality[key] = {
                "format_id": f["format_id"],
                "ext": ext,
                "height": height,
                "_score": score,
            }

    video_formats = []
    for h in sorted(best_by_quality.keys(), reverse=True):
        if not best_by_quality[h]["height"]:
            continue
        video_formats.append(
            {
                "format_id": best_by_quality[h]["format_id"],
                "ext": best_by_quality[h]["ext"],
                "height": best_by_quality[h]["height"],
                "label": f"{best_by_quality[h]['height']}p",
            }
        )

    result = {
        "title": info.get("title") or "Video",
        "uploader": info.get("uploader"),
        "thumbnail": info.get("thumbnail"),
        "duration": info.get("duration"),
        "url": url,
        "video_formats": video_formats[:12],
        "audio_formats": AUDIO_FORMATS,
    }
    return result


@app.post("/api/download", status_code=202)
def start_download(request: DownloadRequest):
    if not request.url:
        raise HTTPException(status_code=400, detail="Falta la URL")
    if not request.format_id and not request.audio_format:
        raise HTTPException(status_code=400, detail="Elige formato de video o de audio")

    job_id = uuid.uuid4().hex[:10]
    job = Job(job_id, request)
    JOBS[job_id] = job
    job._thread = threading.Thread(target=_run_job, args=(job,), daemon=True)
    job._thread.start()
    return {"job_id": job_id, "status": job.status}


@app.get("/api/job/{job_id}")
def job_status(job_id: str):
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Trabajo no encontrado")
    return job.to_dict(include_filename=(job.status == "done"))


@app.get("/api/file/{job_id}")
def get_file(job_id: str):
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Trabajo no encontrado")
    if job.status != "done" or not job.filename:
        raise HTTPException(status_code=409, detail="La descarga no ha terminado")
    path = os.path.join(CACHE_DIR, job.filename)
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="Archivo no encontrado")
    return FileResponse(path, filename=job.filename)


@app.delete("/api/job/{job_id}")
def delete_job(job_id: str):
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Trabajo no encontrado")
    if job.filename:
        for p in glob.glob(os.path.join(CACHE_DIR, job.filename)):
            try:
                os.remove(p)
            except OSError:
                pass
    JOBS.pop(job_id, None)
    return {"status": "deleted"}


# limpieza periódica de trabajos viejos
def _janitor():
    while True:
        time.sleep(600)
        for jid, job in list(JOBS.items()):
            age = time.time() - getattr(job, "_ts", time.time())
            if age > 3600:  # 1 hora
                for p in glob.glob(os.path.join(CACHE_DIR, job.filename or "")):
                    try:
                        os.remove(p)
                    except OSError:
                        pass
                JOBS.pop(jid, None)


threading.Thread(target=_janitor, daemon=True).start()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)