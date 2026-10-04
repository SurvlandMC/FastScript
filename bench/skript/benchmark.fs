# FastScript benchmark equivalent. Same algorithms as bench/scripts/Arithmetic.fs.
# Run with /fastscript bench (or BenchRunner outside the server).

function fib(n) : number:
    if n < 2:
        return n
    let a = 0
    let b = 1
    let i = 2
    while i <= n:
        let next = a + b
        a = b
        b = next
        i = i + 1
    return b

function sum-range(limit) : number:
    let total = 0
    loop limit times:
        total = total + loop-index
    return total

function countdown(from) : text:
    let result = ""
    let i = from
    while i > 0:
        result = result + i + ","
        i = i - 1
    return result
