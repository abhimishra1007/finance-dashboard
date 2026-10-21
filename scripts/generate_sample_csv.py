#!/usr/bin/env python3
"""Generates a synthetic bank CSV for trying out the dashboard.

The data is made up: a paycheck every two weeks, rent, utilities, a handful of
subscriptions, and random day-to-day spending with realistic-looking bank
descriptions. Usage:

    python3 scripts/generate_sample_csv.py --months 12 > sample.csv
"""
import argparse
import csv
import random
import sys
from datetime import date, timedelta

SUBSCRIPTIONS = [
    ("NETFLIX.COM 866-579-7172 CA", 15.49, 3),
    ("Spotify USA", 11.99, 7),
    ("APPLE.COM/BILL 866-712-7753 CA", 2.99, 12),
    ("GITHUB, INC. GITHUB.COM CA", 4.00, 18),
    ("PLANET FITNESS 3301 SALT LAKE CTY UT", 24.99, 21),
]

DAILY = [
    ("POS PURCHASE SQ *BLUE COPPER {n} SALT LAKE CTY UT", (4, 9)),
    ("STARBUCKS STORE {n} SALT LAKE CTY UT", (3, 8)),
    ("TST* CAFE RIO {n} MIDVALE UT", (9, 16)),
    ("CHIPOTLE {n} SALT LAKE CTY UT", (10, 15)),
    ("DOORDASH*{w} SAN FRANCISCO CA", (18, 40)),
    ("SMITHS #{n} SANDY UT", (25, 120)),
    ("TRADER JOE S #{n} SALT LAKE CTY UT", (20, 80)),
    ("WINCO FOODS #{n} MIDVALE UT", (30, 110)),
    ("MAVERIK #{n} SANDY UT", (25, 55)),
    ("UBER *TRIP HELP.UBER.COM CA", (9, 28)),
    ("AMZN Mktp US*{w}", (8, 90)),
    ("TARGET 000{n} SANDY UT", (12, 75)),
    ("MEGAPLEX THEATRES {n} SANDY UT", (11, 30)),
    ("STEAMGAMES.COM 425-889-9642 WA", (5, 60)),
    ("WALGREENS #{n} SALT LAKE CTY UT", (6, 35)),
]


def ref():
    return "".join(random.choice("ABCDEFGHJKLMNPQRSTUVWXYZ0123456789") for _ in range(9))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--months", type=int, default=12)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    random.seed(args.seed)

    end = date(2026, 9, 30)
    start = end - timedelta(days=round(args.months * 30.4))
    rows = []

    d = start
    while d <= end:
        if (d - start).days % 14 == 4:
            rows.append((d, "ACME ENGINEERING PAYROLL PPD ID 4410298", 1850.00))
        if d.day == 1:
            rows.append((d, "SUGARHOUSE APTS RENT ACH", -950.00))
            rows.append((d, "ONLINE BANKING TRANSFER TO SAVINGS 7731", -150.00))
        if d.day == 6:
            rows.append((d, "ROCKY MOUNTAIN POWER", -round(random.uniform(38, 72), 2)))
        if d.day == 11:
            rows.append((d, "XFINITY MOBILE AUTOPAY", -45.00))
        for desc, amount, day in SUBSCRIPTIONS:
            if d.day == day:
                rows.append((d, desc, -amount))
        purchases_today = random.choices([0, 1, 2, 3, 4, 5], weights=[1, 3, 4, 3, 2, 1])[0]
        for _ in range(purchases_today):
            template, (lo, hi) = random.choice(DAILY)
            desc = template.format(n=random.randint(100, 9999), w=ref())
            rows.append((d, desc, -round(random.uniform(lo, hi), 2)))
        d += timedelta(days=1)

    w = csv.writer(sys.stdout)
    w.writerow(["Date", "Description", "Amount"])
    for d, desc, amt in rows:
        w.writerow([d.isoformat(), desc, f"{amt:.2f}"])
    print(f"{len(rows)} rows", file=sys.stderr)


if __name__ == "__main__":
    main()
