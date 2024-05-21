# 베이스 이미지로 python:3.9을 사용합니다.
FROM python:3.9

# 작업 디렉토리 설정
WORKDIR /app

# 필요한 파일을 복사합니다.
COPY . .

# 필요한 패키지를 설치합니다.
RUN pip install --upgrade pip
RUN pip install flask numpy Pillow tensorflow

# Flask 애플리케이션 실행
EXPOSE 5000
CMD ["python", "app.py"]
