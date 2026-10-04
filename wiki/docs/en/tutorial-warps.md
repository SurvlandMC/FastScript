# Tutorial 3: warps without maps

The DSL has no map literals, so warp coordinates are stored **as text**
`"world,x,y,z"` and parsed with `split` and indexes. The technique is
universal: anything encodable as a string.

## A home point

```fs
command sethome:
    let me = sender()
    $home = me.world + "," + me.x + "," + me.y + "," + me.z
    message "Home set at " + $home + "."

command home:
    if $home is null:
        message "No home set. Use /sethome first."
        stop
    let parts = split($home, ",")
    if length(parts) < 4:
        message "Home data is broken."
        stop
    teleport(parts[1], parts[2], parts[3])
    message "Teleported home."
```

Line by line:

- `sender()` returns the player object; `.world`/`.x`/`.y`/`.z` are
  its properties. Concatenating with a number yields text automatically.
- `split($home, ",")` returns `["world", "100", "64", "200"]`.
- `parts[1]` indexes the list (0-based). `parts[0]` is the world (not needed
  here — `teleport` moves within the player's current world).
- `teleport` takes numbers; `"100"` texts auto-convert to numbers
  (`text → number` is allowed for arguments).
- `teleport` acts on the event player — in a command, that's the sender.

!!! warning "Length check"
    If `$home` is malformed (`"world,100"`), `parts[2]` returns `null`
    and `teleport` with `null` fails. For your own files writing
    coordinates carefully is enough; for others' — check `length(parts)`.

## Several warps

```fs
command setwarp(name : text):
    let me = sender()
    $warp = me.world + "," + me.x + "," + me.y + "," + me.z
    message "Warp '" + name + "' saved (demo: single slot)."

command warp(name : text):
    if $warp is null:
        message "No warp saved."
        stop
    let parts = split($warp, ",")
    teleport(parts[1], parts[2], parts[3])
    message "Warped to '" + name + "'."
```

An honest note: this is **one slot** (`$warp`), not a warp dictionary —
there are no named slots in the language because there are no map literals.
For 2–3 fixed points, use `$warp-home`, `$warp-spawn`, `$warp-shop`.
