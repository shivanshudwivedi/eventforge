import http from "k6/http";
import { check } from "k6";

const targetRate = Number(__ENV.RATE || 20000);
const batchSize = Number(__ENV.BATCH_SIZE || 200);
const duration = __ENV.DURATION || "60s";
const apiUrl = __ENV.EVENTFORGE_URL || "http://localhost:8080";
const apiKey = __ENV.EVENTFORGE_API_KEY || "local-development-key";

export const options = {
  scenarios: {
    event_ingestion: {
      executor: "constant-arrival-rate",
      rate: Math.ceil(targetRate / batchSize),
      timeUnit: "1s",
      duration,
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000"],
    checks: ["rate>0.99"],
  },
};

function randomId() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (token) => {
    const random = Math.floor(Math.random() * 16);
    const value = token === "x" ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

export default function () {
  const events = Array.from({ length: batchSize }, (_, index) => {
    const eventId = randomId();
    return {
      eventId,
      eventType: "order.created",
      schemaVersion: 2,
      idempotencyKey: `load-${eventId}`,
      payload: {
        aggregateId: `order-${eventId}`,
        customerId: `customer-${index}`,
        amount: 42.5,
        currency: "USD",
      },
      metadata: { source: "k6" },
    };
  });

  const response = http.post(
    `${apiUrl}/api/v1/events/batch`,
    JSON.stringify(events),
    {
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": apiKey,
      },
    },
  );
  check(response, { "batch accepted": (result) => result.status === 202 });
}
