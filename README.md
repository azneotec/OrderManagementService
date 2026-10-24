# OrderManagementService

Order service for the scaler-project workspace. It turns a checked-out cart into an order, collects payment through PaymentService, emails the customer, and lets customers view their order history and follow each delivery. MySQL is the source of truth.

Spring Boot 3.3.3, Java 17, registered in Eureka as `order-management-service` (port 8004, a second instance can use 9004).

## How it fits in

| Needs | For |
|---|---|
| `user-auth-service` | validates the bearer token on every request (`POST /auth/validateToken`) |
| `product-catalog-service` | prices every order line again from the catalog (`GET /products/{id}`) |
| MySQL | `order_management_service` database; Flyway creates the tables |
| Kafka | `payment-requested` / `payment-result` with PaymentService, `order-notification` to NotificationService |
| `cart-service` | calls `POST /orders` when a customer confirms checkout (`POST /cart/checkout/confirm`) |

The services reached over HTTP are found by their Eureka names, so the endpoints only work with Eureka running. UserAuthService is reached over HTTP only: token validation is the one thing OMS needs from it, the same way every other service does it.

## Order lifecycle

```
POST /orders ──► COD? ──yes──► CONFIRMED ─► PROCESSING ─► SHIPPED ─► OUT_FOR_DELIVERY ─► DELIVERED
                  │                │            │
                  no               └────────────┴──► CANCELLED   (admin, until the order ships)
                  ▼
           PENDING_PAYMENT ──payment-result PAID───► CONFIRMED
                  │
                  ├──payment-result FAILED──► PAYMENT_FAILED
                  └──admin──► CANCELLED
```

- **Cash on delivery** is confirmed immediately (`paymentStatus: PAY_ON_DELIVERY`).
- **Online payment** (`CARD`, `UPI`, `NET_BANKING`) starts as `PENDING_PAYMENT`. OMS asks PaymentService for a payment link over Kafka; the link shows up as `paymentLink` on the order (and in an email) a moment later, and the order is confirmed when PaymentService reports it paid.
- Every status change is written to the order's tracking timeline.

## API

All endpoints need `Authorization: Bearer <token>` from `POST /auth/login` on user-auth-service. Customers (`ROLE_USER`) only ever see their own orders; the user comes from the token. Someone else's order looks like a missing one (404).

| Endpoint | Role | Description |
|---|---|---|
| `POST /orders` | `ROLE_USER` | Place an order. 201 with the confirmation. |
| `GET /orders?page=0&size=10` | `ROLE_USER` | Order history, newest first (`size` is capped at 50). |
| `GET /orders/{orderNumber}` | `ROLE_USER` | One order in full. |
| `GET /orders/{orderNumber}/tracking` | `ROLE_USER` | Current status and the timeline, oldest first. |
| `PUT /orders/{orderNumber}/status` | `ROLE_ADMIN` | Move any order to its next status: `{"status": "SHIPPED", "note": "Bluedart 123"}`. |

### Placing an order

Customers normally go through CartService (`POST /cart/checkout/confirm`), which calls this endpoint with their own token. It can be called directly too:

```bash
curl -X POST localhost:8004/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "items": [{"productId": 1, "quantity": 2}, {"productId": 2, "quantity": 1}],
        "deliveryAddress": {
          "fullName": "Alice", "phoneNumber": "+15551234567",
          "addressLine1": "1 Main St", "city": "Pune", "state": "MH",
          "postalCode": "411001", "country": "IN"
        },
        "paymentMethod": "UPI",
        "expectedTotal": 242.20
      }'
```

Items carry only a product and a quantity (1 to 99, each product at most once, up to 50 lines). **Prices are never taken from the caller**: OMS looks every product up in the catalog and stores title, image and price on the order as they were at that moment. `expectedTotal` is optional; when given and different from the catalog total, the order is refused with 409, so a customer is never charged an amount they were not shown. `addressLine2` is optional.

```json
{
  "orderNumber": "ORD-20261024-WAUH6NJZ",
  "status": "PENDING_PAYMENT",
  "paymentMethod": "UPI",
  "paymentStatus": "PENDING",
  "currency": "INR",
  "items": [{"productId": 1, "title": "Fjallraven - Foldsack No. 1 Backpack, Fits 15 Laptops", "imageUrl": "...", "unitPrice": 109.95, "quantity": 2, "lineTotal": 219.90}],
  "totalQuantity": 3,
  "totalAmount": 242.20,
  "deliveryAddress": {"fullName": "Alice", "...": "..."},
  "createdAt": "2026-10-24T04:33:49.127056Z",
  "updatedAt": "2026-10-24T04:33:49.127056Z"
}
```

The order number is the public identifier. For an online payment, poll `GET /orders/{orderNumber}` for `paymentLink`.

### Tracking

```json
{
  "orderNumber": "ORD-20261024-WAUH6NJZ",
  "status": "SHIPPED",
  "events": [
    {"status": "CONFIRMED", "note": "Order placed, pay on delivery", "timestamp": "2026-10-24T04:33:49.110712Z"},
    {"status": "PROCESSING", "timestamp": "2026-10-25T08:00:00Z"},
    {"status": "SHIPPED", "note": "Bluedart 123", "timestamp": "2026-10-25T16:20:00Z"}
  ]
}
```

An admin moves an order one step at a time (`CONFIRMED` to `PROCESSING` to `SHIPPED` to `OUT_FOR_DELIVERY` to `DELIVERED`) and may cancel it until it ships. Payment outcomes (`CONFIRMED`, `PAYMENT_FAILED` from `PENDING_PAYMENT`) only ever come from PaymentService. Anything else is a 409. The first admin is described in user-auth-service's README.

### Errors

Every error has the same shape as user-auth-service:

```json
{"status": 409, "error": "Conflict", "message": "Order ORD-... cannot move from CONFIRMED to DELIVERED", "timestamp": "..."}
```

Validation failures add `fieldErrors` (field name to message).

| Status | When |
|---|---|
| 400 | invalid body, quantity outside 1 to 99, unknown payment method or status, the same product twice |
| 401 | missing or invalid token |
| 403 | valid token without the role the endpoint needs |
| 404 | no such order for the caller |
| 409 | a product is no longer in the catalog, `expectedTotal` differs from the catalog total, a status move that is not allowed, an order changed at the same moment (retry) |
| 503 | user-auth-service or product-catalog-service unreachable |

## Kafka

| Topic | Direction | Key | Message |
|---|---|---|---|
| `payment-requested` | OMS to PaymentService | order number | `{orderNumber, amountMinor, currency, customerName, email, phoneNumber}`; `amountMinor` is in paise for INR |
| `payment-result` | PaymentService to OMS | order number | `{orderNumber, status, paymentLink?, message?}` with `status` one of `LINK_CREATED`, `PAID`, `FAILED` |
| `order-notification` | OMS to NotificationService | order number | `{to, from, subject, body}`, the same email message user-auth-service publishes on `signup` |

- **Emails** go out when an order is confirmed (items, total, address, payment), when its payment link is ready, when payment fails, and when it ships, is out for delivery, is delivered or is cancelled.
- A `payment-result` is applied idempotently: a repeated `PAID` changes nothing, `FAILED` never undoes a paid order, `PAID` for a failed order confirms it (the customer did pay), and a result for an unknown or cash-on-delivery order is logged and dropped. A message that is not valid JSON is logged and dropped rather than retried.
- If a `PAID` arrives for an order that was cancelled meanwhile, the payment is recorded and an error is logged: **refunds are not implemented**, someone has to refund it by hand.
- Publishing is best-effort and happens after the database commit. If Kafka is down when an online order is placed, the order is saved but its `payment-requested` message is lost and it stays `PENDING_PAYMENT` (an admin can cancel it). There is no outbox or expiry job yet.
- The topics are created by the monorepo's root `docker-compose.yml` (or this directory's). `payment-requested` and `payment-result` have 3 partitions so two OMS instances share the work.

## Running it

The easiest way is the stack in the parent directory, which starts MySQL, MongoDB, Redis, Kafka, Eureka, UserAuthService, ProductCatalogService, CartService and two OrderManagementService instances:

```bash
cd ..            # scaler-project/
make up          # make status shows what runs; make down stops it
make payment RAZORPAY_KEY_ID=... RAZORPAY_KEY_SECRET=... RAZORPAY_WEBHOOK_SECRET=...   # PaymentService, started on request
make notification GMAIL_PASSWORD=...                                                    # NotificationService, started on request
```

Cash-on-delivery orders and the whole order history/tracking flow work without PaymentService and NotificationService; online orders then wait in `PENDING_PAYMENT` and no emails are sent.

OrderManagementService alone (MySQL on 3307 and Kafka on 9092 from this directory's `docker-compose.yml`, app on :8004):

```bash
cp .env.example .env
make up          # make down stops MySQL and Kafka
```

A standalone run has Eureka switched off, so it starts but cannot validate tokens or price products. Use it for working on the app; use the root stack to call the endpoints. The standalone compose binds the same host ports as the root one (3307, 9092, 8090), so run only one of them.

### Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8004` | HTTP port |
| `DB_URL` | `jdbc:mysql://localhost:3307/order_management_service?createDatabaseIfNotExist=true` | MySQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `password` | MySQL login |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers (set it as a real environment variable, it is not read from `.env`) |
| `EUREKA_CLIENT_ENABLED` | `true` | register with and read from Eureka (`http://localhost:8761/eureka/`) |

`make run` and `./mvnw spring-boot:run` also read a gitignored `.env` (template `.env.example`, unquoted values). Topic names, the notification sender and the client timeouts are `orders.*` properties in `application.properties`.

## Tests

```bash
./mvnw test                                              # unit + web-layer tests; no database needed
./mvnw test -DexcludedGroups=none -Dgroups=integration   # Flyway schema, JPA mapping and the order lifecycle against MySQL on :3307
```

The default suite mocks the catalog, the auth service and Kafka. The integration tests use their own `order_management_service_test` database on the compose MySQL (`make infra-up` first).

## Known limitations

- **No refunds.** A cancelled paid order, or a payment that lands after cancellation, is recorded and logged as an error; the refund is manual.
- **No outbox or expiry.** If Kafka is down when an online order is placed, its payment request is lost and it stays `PENDING_PAYMENT` until an admin cancels it.
- **Currency is fixed to `INR`**, because the catalog has no currency of its own.
- **Double submit.** Two identical confirm requests sent at the same moment can create two orders; there is no idempotency key yet.
- **Status changes are admin-only.** There is no carrier integration and no customer-side cancel.
