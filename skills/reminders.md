---
name: reminders
description: Schedule and manage reminders, alerts, and time-based notifications
tools: [schedule_reminder, list_reminders, get_current_time]
---

# Reminders Skill

Always call `get_current_time` first before scheduling a reminder so you know what "today", "tonight", "tomorrow", or relative times like "in 2 hours" actually mean.

When the user gives a vague time like "tonight" assume 20:00. For "morning" assume 08:00.

After scheduling, confirm with the exact date and time so the user can verify it's correct.
