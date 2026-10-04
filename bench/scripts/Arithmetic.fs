# Exercises the code paths the JIT specialises: arithmetic in a loop, text
# concatenation, conditionals, typed returns and the loop-index variable.

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

function echo-number(value) : number:
    return value

function sum-count(limit) : number:
    let iterations = 0
    loop limit times:
        iterations = iterations + 1
    return iterations

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
