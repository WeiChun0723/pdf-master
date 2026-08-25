# PDF Master

PDF Master is a Kotlin and Spring Boot REST API for common PDF operations. It can merge files, apply text watermarks, extract PDF text into a Word document, and render PDF pages as PNG images.

## Features

- Upload a PDF and return its basic file information.
- Combine multiple PDFs in the supplied order.
- Add configurable text, font size, and rotation watermarks.
- Convert PDF text to a DOCX document.
- Render PDF pages as PNG images and return them as a compressed archive.
- Explore the API through an OpenAPI specification and Swagger UI.
- Package and deploy the service with Docker or Kubernetes.

## Technology stack

| Area | Technology |
| --- | --- |
| Language | Kotlin 2.3.10 on Java 25 |
| Framework | Spring Boot 4.0.3, Spring Web MVC, and Spring Security |
| Build | Gradle 9.3.1 wrapper |
| PDF processing | iText 5.5.13.4 and Apache PDFBox 3.0.6 |
| Word documents | Apache POI 5.5.1 |
| API documentation | Springdoc OpenAPI 2.8.6 |
| Quality and tests | ktlint and JUnit 5 |
| Operations | Spring Boot Actuator, Docker, and Kubernetes |

## Requirements

- Java Development Kit 25 for local development.
- Docker for container builds.
- `kubectl` and a Kubernetes cluster for deployment.

The Gradle wrapper is included, so a system-wide Gradle installation is not required.

## Run locally

Set the public Supabase Auth configuration, then start the application from the repository root:

```bash
cd backend
export SUPABASE_AUTH_URL=https://your-project.supabase.co
export SUPABASE_AUTH_PUBLISHABLE_KEY=sb_publishable_your_key
./gradlew bootRun
```

On Windows PowerShell:

```powershell
cd backend
$env:SUPABASE_AUTH_URL = "https://your-project.supabase.co"
$env:SUPABASE_AUTH_PUBLISHABLE_KEY = "sb_publishable_your_key"
.\gradlew.bat bootRun
```

The service starts on `http://localhost:8080` by default.

## Build, test, and format

Run these commands from `backend/`:

```bash
./gradlew build
./gradlew test
./gradlew ktlintCheck
./gradlew ktlintFormat
```

The application JAR is written to `backend/build/libs/`.

## API

Supabase handles Google login and issues JWTs. Spring Security validates each JWT's signature, issuer, expiry, and `authenticated` audience before allowing access to `/api/**`. All PDF endpoints use `multipart/form-data` under `/api/pdf`.

| Method | Endpoint | Parameters | Result |
| --- | --- | --- | --- |
| `POST` | `/api/pdf/upload` | `files`: one PDF | Upload confirmation text |
| `POST` | `/api/pdf/combine` | `files`: multiple PDFs | `combined.pdf` |
| `POST` | `/api/pdf/add-watermark` | `file`, `watermarkText`, `fontSize`, `rotation` | `watermarked.pdf` |
| `POST` | `/api/pdf/convert` | `file`, `fileType`: `DOCX` or `PNG` | `word.docx` or `images.gz` |

DOCX conversion extracts text only and does not preserve the source layout. PNG conversion renders each page at 300 DPI and returns the images in a compressed archive.

Once the application is running:

- OpenAPI specification: `http://localhost:8080/openapi`
- Authenticated Swagger UI: `http://localhost:8080/docs/index.html`
- Actuator endpoints: `http://localhost:8080/actuator`

## Docker

Pull and run the prebuilt image from Docker Hub:

```bash
docker pull weichunlai/pdf-master:latest
docker run --rm -p 8080:8080 \
  -e SUPABASE_AUTH_URL=https://your-project.supabase.co \
  -e SUPABASE_AUTH_PUBLISHABLE_KEY=sb_publishable_your_key \
  weichunlai/pdf-master:latest
```

The API is then available at `http://localhost:8080`, with Swagger UI at `http://localhost:8080/swagger-ui/index.html`. Press `Ctrl+C` to stop and remove the container.

To build the image from source instead, run these commands from `backend/`:

```bash
docker build -t pdf-master .
docker run --rm -p 8080:8080 pdf-master
```

The multi-stage image builds the application with Java 25 and runs it as an unprivileged user on a Java 25 JRE.

## Kubernetes

The repository includes a Deployment, ClusterIP Service, and Ingress definition. Apply them from `backend/`:

```bash
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml
```

The Deployment currently uses the `weichunlai/pdf-master:latest` image, and the Ingress expects the host name `minikube`. Update those values for your registry and environment before deployment.

## Project structure

```text
.
├── backend/
│   ├── src/main/kotlin/       # Spring Boot application and PDF API
│   ├── src/main/resources/    # Application configuration
│   ├── src/test/kotlin/       # Application tests
│   ├── Dockerfile             # Multi-stage container build
│   └── *.yaml                 # Kubernetes manifests
└── myke.yml                   # Repository task discovery
```
