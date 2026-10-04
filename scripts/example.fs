# FastScript example scripts.
# Everything here is compiled to JVM bytecode when the plugin loads.

# A command with a permission and one text argument.
command heal(target : text) permission fastscript.heal:
    let victim = player(target)
    if victim is null:
        message "Player '" + target + "' is not online."
        stop
    message "Healed " + target

# Welcome message with a per-player visit counter.
on player join:
    if #visits is null:
        #visits = 0
    #visits = #visits + 1
    message "Welcome! This is visit number " + #visits

# A per-player cooldown kept in a player-scoped variable.
command kit:
    if #kit-ready == false:
        message "Your kit is still cooling down."
        stop
    give "DIAMOND" 3
    #kit-ready = false
    message "Kit granted."

# A loop-heavy calculation; the benchmark uses the same shape.
function triangle(n) : number:
    let total = 0
    loop n times:
        total = total + loop-index
    return total

# Global state shared by every script in this directory.
$server-started = 0

on server start:
    $server-started = 1
    log "FastScript routines are ready."
