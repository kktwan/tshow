# TSHOW (공연 · 축제 · 전시 일정)

공연·축제·전시 일정을 모아 날짜·지역·분위기로 찾아 주는 서비스. (가칭, 설계 단계)
설계 원칙과 진행 순서는 [CLAUDE.md](CLAUDE.md)를 본다.

## 로컬 실행

1. 인프라 기동 (tcine과 포트가 겹치지 않는다: DB `5434`, Qdrant `6335/6336`)
   ```bash
   docker compose up -d db qdrant
   ```
2. `.env.example`을 복사해 `.env`를 만들고 인증키를 채운다 (`.env`는 커밋하지 않는다)
3. 빌드·실행
   ```bash
   ./gradlew.bat build
   ./gradlew.bat bootRun
   ```
   헬스체크: `http://localhost:8080/actuator/health`

## 기술 스택

Spring Boot 4.1 (Java 21), PostgreSQL 17, Qdrant, Thymeleaf. 벡터 색인·AI 추천(OpenAI 임베딩, Gemini)은 데이터 모델 확정 후 추가한다.
