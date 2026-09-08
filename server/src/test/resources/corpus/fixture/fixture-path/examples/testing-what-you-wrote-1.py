import unittest

def total_price(items):
    return sum(item["price"] for item in items)

class TotalPriceTests(unittest.TestCase):
    def setUp(self):
        self.items = [{"price": 5}, {"price": 7}]

    def test_sums_all_prices(self):
        self.assertEqual(total_price(self.items), 12)
