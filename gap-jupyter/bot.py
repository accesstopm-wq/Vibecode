import os
import requests
import yt_dlp

from telegram import Update
from telegram.ext import Application, MessageHandler, ContextTypes, filters

TOKEN = os.environ["TELEGRAM_BOT_TOKEN"]


def download_tiktok(url):
    response = requests.post(
        "https://www.tikwm.com/api/",
        data={"url": url, "hd": 1},
        timeout=30,
    )
    response.raise_for_status()
    data = response.json()

    if data.get("code") != 0:
        raise Exception(data.get("msg", "TikTok download failed"))

    video_url = data["data"].get("hdplay") or data["data"].get("play")
    if not video_url:
        raise Exception("TikTok video URL not found")

    video = requests.get(video_url, timeout=60)
    video.raise_for_status()

    filename = "/tmp/tiktok.mp4"
    with open(filename, "wb") as f:
        f.write(video.content)

    return filename


def download_instagram(url):
    options = {
        "format": "best[ext=mp4]/best",
        "outtmpl": "/tmp/instagram.%(ext)s",
        "merge_output_format": "mp4",
        "quiet": True,
        "noplaylist": True,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = ydl.prepare_filename(info)

    if not os.path.exists(filename):
        mp4 = os.path.splitext(filename)[0] + ".mp4"
        if os.path.exists(mp4):
            filename = mp4

    return filename


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    url = update.message.text.strip()

    if "tiktok.com/" in url:
        downloader = download_tiktok
    elif "instagram.com/reel/" in url:
        downloader = download_instagram
    else:
        return

    await update.message.reply_text("Качаю...")

    try:
        filename = downloader(url)

        with open(filename, "rb") as video:
            await update.message.reply_video(video=video, caption=url)

        os.remove(filename)

    except Exception as e:
        await update.message.reply_text(f"Помилка: {e}")


app = Application.builder().token(TOKEN).build()
app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))

print("Бот запущений")
app.run_polling()
