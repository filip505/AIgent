---
name: github-notes
description: Read, write, and manage personal notes and documents stored in GitHub
tools: [push_document_to_github, list_github_documents, read_github_document]
---

# GitHub Notes Skill

Use `list_github_documents` first when the user asks what notes they have or before reading a specific note to confirm it exists.

Document names should be lowercase with hyphens (e.g. "meeting-notes", "project-ideas"). The .md extension is added automatically.

When writing a new note, structure it with a markdown `# Title` at the top so the commit message is meaningful.

Never overwrite a document without reading it first if the user might want to append rather than replace.
