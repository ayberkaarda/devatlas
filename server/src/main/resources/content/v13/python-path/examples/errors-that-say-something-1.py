"""One root per subsystem, and exceptions that carry the facts."""


class OrderError(Exception):
    """Anything this module refuses to do."""


class OrderNotFound(OrderError):
    def __init__(self, order_id: str) -> None:
        super().__init__(f"no order with id {order_id!r}")
        self.order_id = order_id


class InvalidQuantity(OrderError):
    def __init__(self, field: str, value: int, limit: int) -> None:
        super().__init__(f"{field} must be between 1 and {limit}, got {value}")
        self.field = field
        self.value = value
        self.limit = limit


ORDERS = {"A-1": 3}


def quantity_of(order_id: str) -> int:
    if order_id not in ORDERS:
        raise OrderNotFound(order_id)
    return ORDERS[order_id]


def set_quantity(order_id: str, value: int, limit: int = 10) -> None:
    if order_id not in ORDERS:
        raise OrderNotFound(order_id)
    if not 1 <= value <= limit:
        raise InvalidQuantity("quantity", value, limit)
    ORDERS[order_id] = value


def main() -> None:
    print("hierarchy:", [cls.__name__ for cls in InvalidQuantity.__mro__])
    print("known order:", quantity_of("A-1"))

    for order_id, value in (("A-1", 4), ("A-1", 99), ("Z-9", 1)):
        try:
            set_quantity(order_id, value)
            print("set", order_id, "to", quantity_of(order_id))
        except InvalidQuantity as exc:
            print("rejected:", exc, "| field:", exc.field, "| limit:", exc.limit)
        except OrderNotFound as exc:
            print("missing:", exc, "| id:", exc.order_id)

    try:
        quantity_of("Q-0")
    except OrderError as exc:
        print("one catch covers the subsystem:", type(exc).__name__, "|", exc)


if __name__ == "__main__":
    main()
