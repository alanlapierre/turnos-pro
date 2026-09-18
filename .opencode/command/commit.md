---
description: Commitea y pushea todos los cambios pendientes, agrupados por tipo (feat, fix, refactor, chore, docs), con tag anotado para cada feat, siguiendo las convenciones del repo.
agent: build
---

# Commit

Commitea y pushea todos los cambios pendientes del repo respetando las convenciones de commits, tags y push del proyecto. No mezcles tipos de cambio en un mismo commit.

## 1. Inspección

Ejecuta `git status --short`, `git diff --stat`, `git diff --cached --stat`, `git log --oneline -15` y `git tag --list 'post-*'`.

- Si el árbol está limpio (no hay nada staged ni unstaged pendiente), informa "No hay nada que commitear" y termina.
- Si aparece cualquier artefacto que no deba ir al repo (`target/`, `.idea/`, binarios, config oculta, archivos locales), no lo commitees: propón añadirlo al `.gitignore` o sácalo del commit y avisa al usuario.

## 2. Agrupación por tipo

Clasifica todos los cambios (staged y unstaged) en grupos según el tipo de commit. Cada archivo va en un único grupo; si encaja en varios, elige el tipo que mejor describa el cambio:

- `chore:` — mantenimiento: build, dependencias, Docker, k8s, Maven, CI, configuración de infraestructura, etc.
- `docs:` — cambios solo de documentación.
- `refactor:` — refactor sin cambio de comportamiento observable.
- `fix:` — corrección de errores.
- `feat:` — nueva funcionalidad/publicación. Su subject incluye el número de post: `feat: post N - <descripción>`, con `N` = siguiente post libre = (mayor `post-N` existente en los tags) + 1.

## 3. Commits

Crea un commit por cada grupo, en orden lógico, confirmando cada uno con el usuario antes de ejecutarlo:

1. Preséntale el plan: los grupos detectados, los archivos de cada uno y el subject propuesto, y pídele confirmación o edición del subject (puedes usar la herramienta `question` si tienes varias alternativas).
2. `git add` los archivos del grupo.
3. Executa `git commit -m "<subject>"` con un subject en **español**, estilo Conventional Commit (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`), de **una línea, sin cuerpo** y sin emojis. Respeta los hooks del repo si los hay.
4. Repite hasta commitear todos los grupos.

## 4. Tags

- Cada commit `feat:` se cierra con un **tag anotado** del mismo número: `git tag -a post-N -m "post-N : <descripción>"`. El mensaje del tag replica el subject del commit (`post-N : ` seguido de la descripción).
- Los commits `fix:`, `refactor:`, `chore:` y `docs:` **no** llevan tag.

## 5. Push

Cuando todos los commits (y sus tags, si los hay) estén creados, haz push de todo:

`git push --follow-tags origin main`