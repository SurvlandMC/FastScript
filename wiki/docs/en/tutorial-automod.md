# Tutorial 4: anti-spam and auto-moderation

An honest disclaimer first: scripts **cannot see chat text** (there is no
builtin for message contents), so word filters are impossible. But anything
based on time, player and frequency works: cooldowns, limits, punishments.

## 3-second chat cooldown

`time()` returns world time in ticks (20 ticks = 1 second).
Logic: if less than 60 ticks passed since the last message — cancel it.

```fs
on chat:
    let now = time()
    if #last-chat is null:
        #last-chat = now
        stop
    if now - #last-chat < 60:
        message "Slow down! Wait 3 seconds."
        cancel-event
        stop
    #last-chat = now
```

Line by line:

- First message: `#last-chat` is still `null` — remember the time and exit
  (`stop` ends the handler, the message goes through).
- Spam: `cancel-event` cancels the Bukkit event — nobody sees the message.
- `stop` after `cancel-event` isn't strictly required (no code follows),
  but it's a useful habit: the handler shouldn't "fall through" past a cancel.

## Punishment after three strikes

```fs
on chat:
    let now = time()
    if #last-chat is null:
        #last-chat = now
        stop
    if now - #last-chat < 60:
        cancel-event
        if #warns is null:
            #warns = 1
        else:
            #warns = #warns + 1
        if #warns >= 3:
            kick "Spamming. Come back later."
            #warns = 0
        else:
            message "Warning " + #warns + "/3. Slow down!"
        stop
    #last-chat = now
    #warns = 0
```

Patterns here:

- A `#warns` counter with `null` initialization (see [Tutorial 2](tutorial-economy.md)).
- Resetting `#warns = 0` on a "good" message.
- `kick` acts on the event player.

## A delayed greeting? No — and here's why

FAQ: "how do I `message` 5 seconds after join?"
Answer: **you can't in the current version** — the scheduler
(`Host.runLater`) is not exposed in the DSL. There are no workarounds;
it's a known limitation (see [Operations](ops.md)).
If delays are critical, you need a Java plugin or another FastScript version.
