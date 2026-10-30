FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY image2playlist ./image2playlist
ENV TESSERACT_CMD=/usr/bin/tesseract
ENV TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata
EXPOSE 8000
CMD ["uvicorn", "image2playlist.main:app", "--host", "0.0.0.0", "--port", "8000"]
