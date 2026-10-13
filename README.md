# Authorization service - Tokenz interview assignment

This repository provides a faithful representation of how a backend service at Tokenz is structured.
While its structure is deliberately simplified, we ask you to treat it as actual production code
for the purpose of any changes you make to the service.

## AI use

The assignment assumes that you may use AI in order to develop a solution.
Regardless of whether AI was used for this assignment or not we ask you to have
complete understanding of both the design and the technical aspects of the codebase.

## The task

### Background

The service acts as a proxy for a payment service provider by accepting authorization requests, forwarding
them to the payment service provider and persisting (intermediate) authorization results.

Due to the agreement with the payment service provider, there is a rate limit on how many requests a proxy
can send to the payment service provider API. If the rate limit is exceeded, the payment service provider
API responds with a failure even if the request would have otherwise succeeded.

Additionally, it was identified that the payment service provider API is inherently unstable, and would 
often respond with a failure.

### Goals

While the service is already able to process authorization requests, it needs to be changed to accomplish
the goals listed below. Additionally, please include a README file explaining your reasoning and any
additional assumptions you made while working on the assignment.

1. Regardless of the payment service provider's RPS limitations, the proxy service needs to be able to accept
requests and serve responses as long as the service itself is able to handle the load.
2. Authorization requests to the proxy service API need to be served within "reasonable time" of the 
underlying payment service provider processing the request.
   1. The "reasonable time" metric is undefined and has to be determined while solving the assignment.
3. Payment service provider API errors cannot result in authorization failures in the proxy service unless
the authorization as a whole is taking more than 10 seconds.

### Assumptions

1. For the purposes of this assignment payment service provider API errors can be considered transient - 
the service would eventually succeed after a certain number of retries.
2. Maximum loads handled by both services are expected to be "sane" - no specific limits are provided,
but nothing that would require specific "high load" solutions is expected.

## Running

Requirements: a JDK 23 or newer and Docker. `./mill` downloads everything else.

```bash
docker compose up proxy           # the payment service provider, on :8090
./mill app.run                    # the service, on :9999
./mill __.test                    # all tests
./mill __.reformat                # format
```

## Proxy service APIs

`POST /authorizations` takes an amount and card details and:

1. Stores a new authorization as `pending`.
2. Asks the payment provider to authorize it.
3. Stores the outcome — `authorized`, `rejected` (the provider declined the card), or `failed`
   (the provider could not process the request).
4. Returns the authorization.

```bash
curl --location 'localhost:9999/authorizations' \
--data '{
    "amount": {
        "value": 1999,
        "currency": "EUR"
    },
    "card": {
        "number": "4242424242424242",
        "expiryMonth": 12,
        "expiryYear": 2030,
        "cvc": "737",
        "holderName": "Jane Doe"
    }
}'
```

`GET /authorizations/{id}` returns a stored authorization.

```bash
curl --location 'localhost:9999/authorizations/17facfb0-cba7-4871-857a-8dd063dfdfeb'
```

## Payment service provider APIs

Only the provider `acme` exists. Base URL: `http://localhost:8090`. Any other provider id gets
`404 {"error":"unknown_provider"}` and is not rate-limited.

`POST /providers/{provider}/authorize` 
- `200 { "reference": "<reference>", "outcome": "authorized"}` for authorization successes.
- `200 { "reference": "<reference>", "outcome": "declined", "declineCode": "card_declined" }` for authorization failures.
- `400 { "error": "bad_request", "message": "error details" }` for invalid input (including malformed JSON).
- `404 { "error": "unknown_provider" }`
- `429 { "error": "rate_limited" }`
- `500 { "error": "error_code" }` for internal failures.

```bash
curl --location 'localhost:8090/providers/acme/authorize' \
--data '{
    "reference": "193c133f-0d13-4e4e-b611-4aed77290f9d",
    "amount": {
        "value": 1999,
        "currency": "EUR"
    },
    "card": {
        "number": "4111111111111111",
        "expiryMonth": 12,
        "expiryYear": 2030,
        "cvc": "737",
        "holderName": "Taro Tokenz"
    }
}'
```

`POST /providers/{provider}/authorize/batch` 
- `200 { "results": [ { "reference": "<reference>", "outcome": "authorized"}, { "reference": "<reference>", "outcome": "declined", "declineCode": "card_declined" }, ... ] }` for authorization results (same as individual authorizations).
- `400 { "error": "bad_request", "message": "error details" }` for invalid input (including malformed JSON).
- `404 { "error": "unknown_provider" }`
- `429 { "error": "rate_limited" }`
- `500 { "error": "error_code" }` for internal failures.

```bash
curl --location 'localhost:8090/providers/acme/authorize/batch' \
--data '{
    "items": [
        {
            "reference": "193c133f-0d13-4e4e-b611-4aed77290f9d",
            "amount": {
                "value": 1999,
                "currency": "EUR"
            },
            "card": {
                "number": "4111111111111111",
                "expiryMonth": 12,
                "expiryYear": 2030,
                "cvc": "737",
                "holderName": "Taro Tokenz"
            }
        }
    ]
}'
```

### Test cards

For testing purposes authorization results depend on the card number:

| Card number ends in | Outcome |
|---|---|
| `0002` | declined, `card_declined` |
| `9995` | declined, `insufficient_funds` |
| anything else | authorized |

### Failure behavior

RPS limit and error rate for the payment service provider are configured in `docker-compose.yml` through
`RPS_LIMIT` and `FAILURE_ONE_IN` environment variables respectively.

You are welcome to modify these values and experiment with them.