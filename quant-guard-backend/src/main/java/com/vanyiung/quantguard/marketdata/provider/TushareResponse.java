package com.vanyiung.quantguard.marketdata.provider;

import com.fasterxml.jackson.databind.JsonNode;

record TushareResponse(JsonNode fields, JsonNode items) {
}
