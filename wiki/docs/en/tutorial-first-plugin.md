# Tutorial 1: your first script in 10 minutes

This tutorial takes you from an empty server to a working heal and kit.
Every step is verifiable immediately — no server restart needed.

## Step 0. What you should already have

The plugin is built (`build/FastScript-1.0.0.jar`), a Paper 1.21.8+ server
is running, the jar is in `plugins/`, and the console shows:

```text
FastScript enabled: 1 script(s), 1 trigger(s), 1 script command(s)
```

Scripts live in `plugins/FastScript/scripts/`.

## Step 1. Greet joiners

Create `plugins/FastScript/scripts/hello.fs`:

```fs
on player join:
    message "Welcome to the server!"
```

In console or in game:

```text
/fastscript reload
```

Join with a second account (or ask a friend) — the greeting arrives.
A bare `message` always goes to **the event player**.

## Step 2. A command with an argument

Append to the same file:

```fs
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target
```

Line by line:

- `command heal(target : text)` — a `/heal <text>` command; the last text
  argument is greedy (captures the rest of the line).
- `permission fastscript.heal` — without the permission Bukkit won't run it.
- `player(target)` — finds an online player, returns the object or `null`.
- `is null` is a synonym of `== null`.
- `stop` exits the handler early.

Now **be sure** to add the command to the plugin's `plugin.yml`:

```yaml
commands:
  heal:
    description: Declared by a script
    usage: /heal <player>
```

Without it Bukkit won't bind the command, and the log will say:

```text
script command '/heal' is not declared in plugin.yml
```

Reload (`/fastscript reload`) and check: `/heal Steve`, `/heal Nobody123`.

## Step 3. A kit with a cooldown in a `#` variable

```fs
command kit:
    if #kit-ready == false:
        message "Your kit is still cooling down."
        stop
    give "DIAMOND" 3
    #kit-ready = false
    message "Kit granted."
```

`#kit-ready` is a variable **scoped to the player** (keyed by UUID).
Every player has their own value; other players' cooldowns are untouched.
The first time it equals `null`, and `null == false` is `false`,
so newcomers get the kit. And don't forget `kit:` in `plugin.yml`.

## What's next

- [Tutorial 2: economy and shop](tutorial-economy.md) — counters,
  `null` initialization, variable arithmetic.
- [Tutorial 3: warps](tutorial-warps.md) — storing coordinates as text,
  `split`, indexes, `teleport`.
- [Tutorial 4: anti-spam](tutorial-automod.md) — `time()`, `cancel-event`.
