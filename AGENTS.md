# AGENTS.md

Proyecto Java (Spring Boot) de backend. Rama principal: `main`.

## Comandos

Todos los comandos usan el Maven Wrapper (`./mvnw`):

```bash
./mvnw compile                 # compilar
./mvnw test                    # todos los tests
./mvnw test -Dtest=ClassName   # un solo test
./mvnw spring-boot:run         # arrancar la app
```

## Requisitos y gotchas

- **Java 21 con preview**: el pom usa `--enable-preview` (compilador y plugin de Spring Boot). No elimines esos flags; el código puede depender de features en preview.
- **Docker obligatorio para tests de integración**: los tests con base de datos usan Testcontainers (PostgreSQL efímero). Sin Docker activo, fallan.
- Base de datos real: PostgreSQL (`jdbc:postgresql://localhost:5432/saas_db`), migraciones con Flyway.

## Arquitectura

Hexagonal: `core/` (domain, ports in/out, application) y `infrastructure/` (adapters in/out, web, security, config). La lógica de dominio vive en `core/`, la infraestructura en `infrastructure/`. Punto de entrada: `com.turnospro.TurnosProApplication`.

## Convención de commits, tags y código

- **Idiomas**: el código (comentarios, `@DisplayName`, mensajes de excepciones) se escribe en **inglés**; los mensajes de commit, en **español**.
- **Commits**: Conventional Commits (`feat:`, `chore:`, `fix:`, `refactor:`), subject de una línea, sin cuerpo.
- **Tags**: cada feature/publicación se cierra con un tag **anotado** `post-N` (numeración correlativa). El mensaje del tag replica el subject: `post-N : <descripción>`.
- Los commits de mantenimiento (`chore:`/`fix:`/`refactor:`) **no** llevan tag.
- El próximo número de post libre es `11`; actualizar el número libre tras cada release etiquetado.

Ignorar: `target/`, `.idea`, binarios y configuración oculta.
