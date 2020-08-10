package io.github.mosadie.awayfromauctioncache;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.exceptions.APIThrottleException;
import net.hypixel.api.exceptions.HypixelAPIException;
import net.hypixel.api.reply.KeyReply;
import net.hypixel.api.reply.skyblock.SkyBlockAuctionsReply;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {

    public static HypixelAPI hypixelAPI;
    public static HttpClient httpClient = HttpClientBuilder.create().build();
    public static Gson GSON = new Gson();

    public static void main(String[] args) {
        // get/check key from args or env
        String hypixelApiKey = null;
        if (args.length > 0) {
            System.out.println("Getting Hypixel API key from program arguments.");
            hypixelApiKey = args[0];
        } else if (System.getenv("HYPIXEL_API_KEY") != null) {
            System.out.println("Getting Hypixel API key from environment variable.");
            hypixelApiKey = System.getenv("HYPIXEL_API_KEY");
        }

        if (hypixelApiKey == null) {
            System.out.println("Hypixel API key could not be loaded, exiting without changes.");
            System.exit(1);
        }

        UUID hypixelApiKeyUUID = null;
        try {
            hypixelApiKeyUUID = UUID.fromString(hypixelApiKey);
        } catch (IllegalArgumentException e) {
            System.out.println("Hypixel API key not in correct format, exiting without changes.");
            System.exit(2);
        }

        System.out.println("Loaded Hypixel API key.");

        // Create Hypixel API obj
        hypixelAPI = new HypixelAPI(hypixelApiKeyUUID);

        // Test Key
        System.out.println("Testing Hypixel API key.");
        try {
            KeyReply keyReply = hypixelAPI.getKey().get(1, TimeUnit.MINUTES);
            if (keyReply == null || keyReply.isThrottle() || !keyReply.isSuccess()) {
                System.out.println("Key endpoint test failed, exiting without changes.");
                int code = 4;
                if (keyReply != null) {
                    if (keyReply.isThrottle() && !keyReply.isSuccess()) code = 5;
                    else if (keyReply.isThrottle()) code = 6;
                    else if (!keyReply.isSuccess()) code = 7;
                }
                System.exit(code);
            }
        } catch (Exception e) {
            System.out.println("An exception occurred Key endpoint test, exiting without changes.");
            e.printStackTrace();
            System.exit(8);
        }

        System.out.println("Hypixel API key test passed.");
        System.out.println();

        generateUsernameCache();
    }

    public static void generateUsernameCache() {
        System.out.println("Begin generating username cache.");

        Set<UUID> uuidSet = new HashSet<>();

        // Grab all Auctions and extract unique UUIDs to set
        try {
            System.out.println("Fetching first page of Skyblock Auctions.");
            SkyBlockAuctionsReply reply = hypixelAPI.getSkyBlockAuctions(0).get(1, TimeUnit.MINUTES);
            System.out.println("Fetch complete, processing auctions.");
            processAuctionsArray(uuidSet, reply.getAuctions());
            System.out.println("First page processing complete, fetching and processing " + reply.getTotalPages() + " total pages.");

            for (int i = 1; i < reply.getTotalPages(); i++) {
                try {
                    System.out.println("Fetching page " + (i+1) + " of Skyblock Auctions.");
                    SkyBlockAuctionsReply pagedReply = hypixelAPI.getSkyBlockAuctions(i).get(1, TimeUnit.MINUTES);
                    System.out.println("Fetch complete, processing auctions.");
                    processAuctionsArray(uuidSet, pagedReply.getAuctions());
                    System.out.println("Processing complete.");
                } catch (APIThrottleException e) {
                    System.out.println("The Hypixel API key has been throttled, exiting without changes.");
                    System.exit(9);
                } catch (HypixelAPIException e) {
                    System.out.println("An exception occurred in the Hypixel API getting page " + i + ", exiting without changes.");
                    e.printStackTrace();
                    System.exit(10);
                } catch (InterruptedException e) {
                    System.out.println("An InterruptedException occurred getting page " + i + ", exiting without changes.");
                    e.printStackTrace();
                    System.exit(11);
                } catch (ExecutionException e) {
                    System.out.println("An ExecutionException occurred getting page " + i + ", exiting without changes.");
                    e.printStackTrace();
                    System.exit(12);
                } catch (TimeoutException e) {
                    System.out.println("An TimeoutException occurred getting page " + i + ", exiting without changes.");
                    e.printStackTrace();
                    System.exit(13);
                }
            }
        }  catch (APIThrottleException e) {
            System.out.println("The Hypixel API key has been throttled, exiting without changes.");
            System.exit(14);
        } catch (HypixelAPIException e) {
            System.out.println("An exception occurred in the Hypixel API getting page 0, exiting without changes.");
            e.printStackTrace();
            System.exit(15);
        } catch (InterruptedException e) {
            System.out.println("An InterruptedException occurred getting page 0, exiting without changes.");
            e.printStackTrace();
            System.exit(16);
        } catch (ExecutionException e) {
            System.out.println("An ExecutionException occurred getting page 0, exiting without changes.");
            e.printStackTrace();
            System.exit(17);
        } catch (TimeoutException e) {
            System.out.println("An TimeoutException occurred getting page 0, exiting without changes.");
            e.printStackTrace();
            System.exit(18);
        }

        System.out.println("Finished fetching and processing all auctions.");

        // Iterate through set and get as many names as possible, warn about failed names
        System.out.println("Begin username resolution for " + uuidSet.size() +" UUIDs.");
        Map<UUID, String> nameMap = new HashMap<>();

        int finishedCount = 0; //This is just for display, do not trust.

        for(UUID uuid : uuidSet) {
            try {
                String url = "https://api.ashcon.app/mojang/v2/user/" + uuid.toString();
                HttpResponse httpResponse = httpClient.execute(new HttpGet(url));
                if (httpResponse.getEntity() == null)
                    throw new NullPointerException("Profile response entity was null!");
                String content = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
                AshconResponse response = GSON.fromJson(content, AshconResponse.class);
                if (response.error != null) {
                    throw new IOException("Error occurred getting username/uuid: " + response.error + " Details: " + response.reason);
                }
                finishedCount++;
                if (finishedCount % 100 == 0)
                    System.out.println("Fetching usernames " + (finishedCount) + " of " + uuidSet.size() + " (" + percentify(((double)finishedCount)/uuidSet.size()) + "%)");
                nameMap.put(uuid, response.username);
            } catch (IOException | NullPointerException e) {
                System.out.println("[WARNING] Failed to get username for UUID " + uuid.toString() + ", skipping UUID");
                e.printStackTrace();
            }
        }

        System.out.println("Finished resolving usernames");

        // Save as json to docs/usernames.json
        System.out.println("Begin saving cache to file.");
        try {
            File usernameCache = new File("./docs/usernames.json");
            boolean wasDeleted = usernameCache.delete();
            if (wasDeleted) {
                System.out.println("Previous cache file successfully deleted.");
            }
            FileWriter writer = new FileWriter(usernameCache);
            Type typeToken = new TypeToken<Map<UUID, String>>() {}.getType();
            System.out.println("Begin writing cache to file");
            GSON.toJson(nameMap, typeToken, writer);
            writer.close();
            System.out.println("Cache has been written to file.");
        } catch (IOException e) {
            System.out.println("An IOException occurred saving cache to file, exiting without changes.");
            e.printStackTrace();
            System.exit(19);
        }

        System.out.println("Finished saving cache to file.");
        System.out.println("Finished generating username cache.");
    }

    public static String addHyphens(String uuid) {
        return uuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"); // Shamelessly stolen from StackOverflow
    }

    public static double percentify(double percent) {
        percent *= 1000;
        percent = Math.floor(percent);
        percent /= 10.0f;
        return percent;
    }

    public static void processAuctionsArray(Set<UUID> uuidSet, JsonArray auctions) {
        if (auctions != null) {
            for (JsonElement auctionElement : auctions) {
                JsonObject auctionObject = auctionElement.getAsJsonObject();
                try {
                    UUID ownerUUID = UUID.fromString(addHyphens(auctionObject.get("auctioneer").getAsString()));
                    uuidSet.add(ownerUUID);
                } catch (IllegalArgumentException e) {
                    System.out.println("Failed to convert auction owner JSON to UUID, exiting without changes. Auction owner JSON: " + auctionObject.get("auctioneer").getAsString());
                    System.exit(20);
                }

                JsonArray bidsArray = auctionObject.getAsJsonArray("bids");
                for (JsonElement bidElement : bidsArray) {
                    JsonObject bidObject = bidElement.getAsJsonObject();
                    try {
                        UUID bidUUID = UUID.fromString(addHyphens(bidObject.get("bidder").getAsString()));
                        uuidSet.add(bidUUID);
                    } catch (IllegalArgumentException e) {
                        System.out.println("Failed to convert auction bidder JSON to UUID, exiting without changes. Auction bidder JSON: " + bidObject.get("bidder").getAsString());
                        System.exit(21);
                    }
                }
            }
        }
    }

    private class AshconResponse {
        private String uuid;
        private String username;

        private int code;
        private String error;
        private String reason;
    }
}
