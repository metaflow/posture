# -*- coding: utf-8 -*-
"""
Notifications
-------------

Example showing how to add notifications to a characteristic and handle the responses.

Updated on 2019-07-03 by hbldh <henrik.blidh@gmail.com>

"""

import logging
import asyncio
import platform

from bleak import BleakClient
from bleak import _logger as logger


CHARACTERISTIC_UUID = (
    "6A806050-B5A3-F393-E0A9-E50E24DCCA9E"
)  # <--- Change to the characteristic you want to enable notifications from.


def notification_handler(sender, data):
    """Simple notification handler which prints the data received."""
    print("{0}: {1}".format(sender, data))


async def run(address, loop, debug=False):
    if debug:
        import sys

        # loop.set_debug(True)
        l = logging.getLogger("asyncio")
        l.setLevel(logging.DEBUG)
        h = logging.StreamHandler(sys.stdout)
        h.setLevel(logging.DEBUG)
        l.addHandler(h)
        logger.addHandler(h)

    async with BleakClient(address, loop=loop) as client:
        x = await client.is_connected()
        logger.info("Connected: {0}".format(x))

        await client.start_notify(CHARACTERISTIC_UUID, notification_handler)
        await asyncio.sleep(5.0, loop=loop)
        await client.stop_notify(CHARACTERISTIC_UUID)


if __name__ == "__main__":
    import os

    os.environ["PYTHONASYNCIODEBUG"] = str(1)
    address = (
        "24:71:89:cc:09:05"  # <--- Change to your device's address here if you are using Windows or Linux
        if platform.system() != "Darwin"
        else "7137C3CC-8B3F-4621-A698-118852E4705F"  # <--- Change to your device's address here if you are using macOS
    )
    loop = asyncio.get_event_loop()
    loop.run_until_complete(run(address, loop, True))
