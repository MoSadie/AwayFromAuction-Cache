# AwayFromAuction-Cache

[![Username Cache Workflow Status](https://github.com/MoSadie/AwayFromAuction-Cache/workflows/Generate%20and%20Commit%20Cache/badge.svg)](https://github.com/MoSadie/AwayFromAuction-Cache/actions)

Automatically generates and hosts the [AwayFromAuction username cache](docs/usernames.json).

There are two main parts to this repo, [the cache generation code](src/main/java/io/github/mosadie/awayfromauctioncache/Main.java), and [the cache itself](docs/usernames.json).

The cache generation code is ran every half-hour [via GitHub Action](.github/workflows/generateCache.yml) and auto-commits the updated cache file.
The cache file has a Minecraft UUID to username mapping for every auction owner and bidder in the Hypixel Skyblock Auction House.

The intention is to use this as a "starting" cache for projects like [AwayFromAuction](https://github.com/MoSadie/AwayFromAuction) to reduce the username look up requests the client needs to do in order to have a "complete" cache.
