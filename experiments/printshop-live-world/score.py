"""Score the five live-editor print-shop challenges using exact decimal arithmetic."""
from decimal import Decimal, ROUND_HALF_UP


def challenge(attempt):
    if attempt not in range(1, 6):
        raise ValueError("attempt must be 1 through 5")
    offset = attempt - 1
    return [
        (80 + offset, False, False, 40),
        (240 + 2 * offset, True, True, 40),
        (720 + 3 * offset, False, True, 40),
        (90 + offset, True, True, 40),
        (720 + 3 * offset, True, True, 75),
    ]


def total(quantity, rush, member, cap):
    production = (min(quantity, 100) * 2
                  + min(max(quantity - 100, 0), 400) * Decimal("1.60")
                  + max(quantity - 500, 0) * Decimal("1.30"))
    charges = production * (Decimal("1.20") if rush else 1)
    discount = min(charges * Decimal("0.10"), cap) if member else 0
    subtotal = charges - discount + 25
    delivered = subtotal + (12 if subtotal < 250 else 0)
    return (delivered * Decimal("1.08")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


if __name__ == "__main__":
    for attempt in range(1, 6):
        print(attempt, *(total(*order) for order in challenge(attempt)))
